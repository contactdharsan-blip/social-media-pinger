package com.quietping.capture

import com.google.common.truth.Truth.assertThat
import com.quietping.domain.alerts.AlertDispatcher
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.MatchResult
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.Rule
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType
import com.quietping.domain.model.RawEvent
import com.quietping.domain.parser.MessageParser
import com.quietping.domain.parser.WhatsAppParser
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.repo.RuleRepository
import com.quietping.domain.rules.RuleEngineImpl
import com.quietping.domain.vault.VaultManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

/**
 * End-to-end regression test for the conversationId=0 bug.
 *
 * Drives the *real* [CapturePipeline] (with a real [WhatsAppParser],
 * [VaultManagerImpl] and [RuleEngineImpl]) from a WhatsApp group @-mention
 * [RawEvent.NotificationPosted] whose parsed messages carry `conversationId = 0`
 * (exactly what every parser emits). No conversation row is pre-seeded — the
 * pipeline must resolve/create one before ingest, otherwise the FK child insert
 * would fail and rule evaluation would never run.
 *
 * This is the gap the old unit tests never covered: they all seeded a
 * conversation (id = 10L) and called VaultManager.ingest directly, so they could
 * not catch the missing resolveConversationId wiring at the pipeline boundary.
 *
 * The pipeline is driven on [Dispatchers.Unconfined] so its internal consumer
 * coroutine processes each submitted event inline (the fakes return immediate
 * `flowOf`/values, so there is no real suspension); a single [yield] after
 * [CapturePipeline.submit] drains any trailing continuation before asserting.
 * This keeps the test dependency-free (no kotlinx-coroutines-test required).
 *
 * Asserts:
 *  (a) a conversation row AND a message row now exist in the fake store, with the
 *      message stamped to the resolved conversation id (not 0);
 *  (b) with an enabled KEYWORD rule, [AlertDispatcher.fire] was invoked, and the
 *      fired match carries the real (non-zero) stored message id;
 *  (c) a disabled rule still persists the message but never fires.
 */
class CapturePipelineIntegrationTest {

    /**
     * In-memory [MessageRepository] that mimics Room's FK + insert semantics
     * WITHOUT Android/Room. A message may only be stored once its conversation
     * parent exists; inserting a message for an unknown conversation id throws —
     * exactly the FK violation the production bug produced under
     * `PRAGMA foreign_keys = ON`. This is what makes the test adversarial: if the
     * pipeline failed to resolve the conversation before ingest, [upsert] would
     * blow up instead of silently passing.
     */
    private class FakeMessageRepository : MessageRepository {
        val conversationStore = mutableListOf<Conversation>()
        val messageStore = mutableListOf<Message>()
        private var nextMessageId = 1L
        private var nextConversationId = 1L

        override fun conversations(): Flow<List<Conversation>> =
            flowOf(conversationStore.toList())

        override fun thread(conversationId: Long): Flow<List<Message>> =
            flowOf(
                messageStore.filter { it.conversationId == conversationId }
                    .sortedBy { it.postedAt }
            )

        override fun deleted(): Flow<List<Message>> =
            flowOf(messageStore.filter { it.status == MessageStatus.DELETED })

        override fun edited(): Flow<List<Message>> =
            flowOf(messageStore.filter { it.status == MessageStatus.EDITED })

        override suspend fun upsert(message: Message): Long {
            // Enforce the foreign key the way Room would: the parent conversation
            // row must already exist. A conversationId of 0 (or any unresolved id)
            // is the bug we are guarding against.
            require(message.conversationId > 0L) {
                "FK violation: message.conversationId=${message.conversationId} is not a resolved row"
            }
            require(conversationStore.any { it.id == message.conversationId }) {
                "FK violation: no conversation row ${message.conversationId} for message"
            }
            if (message.id == 0L) {
                val withId = message.copy(id = nextMessageId++)
                messageStore.add(withId)
                return withId.id
            }
            val idx = messageStore.indexOfFirst { it.id == message.id }
            if (idx >= 0) messageStore[idx] = message else messageStore.add(message)
            return message.id
        }

        override suspend fun resolveConversationId(
            appPackage: AppPackage,
            conversationKey: String,
            displayName: String,
            isGroup: Boolean
        ): Long {
            val existing = conversationStore.firstOrNull {
                it.appPackage == appPackage && it.conversationKey == conversationKey
            }
            if (existing != null) return existing.id
            val created = Conversation(
                id = nextConversationId++,
                appPackage = appPackage,
                conversationKey = conversationKey,
                displayName = displayName,
                isGroup = isGroup
            )
            conversationStore.add(created)
            return created.id
        }

        override suspend fun purgeOlderThan(epochMillis: Long) {
            messageStore.removeAll { it.capturedAt < epochMillis }
        }

        override suspend fun conversationIdFor(appPackage: AppPackage, conversationKey: String): Long? =
            conversationStore.firstOrNull {
                it.appPackage == appPackage && it.conversationKey == conversationKey
            }?.id

        override suspend fun conversationById(id: Long): Conversation? =
            conversationStore.firstOrNull { it.id == id }

        override suspend fun setWatched(id: Long, watched: Boolean) {
            val idx = conversationStore.indexOfFirst { it.id == id }
            if (idx >= 0) conversationStore[idx] = conversationStore[idx].copy(watched = watched)
        }

        override suspend fun purgeOtpOlderThan(epochMillis: Long): Int = 0
    }

    /** Rule store scoped per app, used by the pipeline's `ingestAndMatch`. */
    private class FakeRuleRepository(private val all: List<Rule>) : RuleRepository {
        override fun rules(): Flow<List<Rule>> = flowOf(all)
        override fun rulesFor(app: AppPackage): Flow<List<Rule>> =
            flowOf(all.filter { it.appPackage == app })
        override suspend fun upsert(rule: Rule): Long = rule.id
        override suspend fun delete(id: Long) = Unit
    }

    /** Capturing [AlertDispatcher] — records every fired match. */
    private class FakeAlertDispatcher : AlertDispatcher {
        val fired = mutableListOf<MatchResult>()
        val cancelled = mutableListOf<Long>()
        override fun ensureChannels() = Unit
        override fun fire(match: MatchResult) {
            fired += match
        }
        override fun cancelReminders(conversationId: Long) {
            cancelled += conversationId
        }
    }

    /**
     * A WhatsApp group @-mention notification. MessagingStyle carries two distinct
     * senders (so isGroup is true) and the body holds the mention keyword. The
     * StatusBarNotification key is the only conversation identifier available —
     * the pipeline must turn it into a real conversation row.
     */
    private fun whatsappGroupMention() = RawEvent.NotificationPosted(
        packageName = AppPackage.WHATSAPP.packageId,
        key = "0|com.whatsapp|1|group-weekend|10",
        title = "Weekend Trip",
        text = null,
        bigText = null,
        subText = "Weekend Trip",
        messages = listOf(
            "Bob" to "are we still on?",
            "Carol" to "@Dharsan can you confirm the booking"
        ),
        postedAt = 1_700_000_000_000L,
        messageTimes = listOf(1_700_000_000_000L, 1_700_000_000_001L)
    )

    private val keywordRule = Rule(
        id = 7L,
        appPackage = AppPackage.WHATSAPP,
        type = TriggerType.KEYWORD,
        pattern = "booking",
        soundPreset = SoundPreset.DROPLET,
        dndOverride = false,
        enabled = true
    )

    private fun pipeline(
        messageRepo: FakeMessageRepository,
        ruleRepo: FakeRuleRepository,
        dispatcher: FakeAlertDispatcher
    ) = CapturePipeline(
        parsers = setOf<MessageParser>(WhatsAppParser()),
        ruleEngine = RuleEngineImpl(),
        vaultManager = VaultManagerImpl(messageRepo),
        alertDispatcher = dispatcher,
        ruleRepository = ruleRepo,
        messageRepository = messageRepo,
        dispatcher = Dispatchers.Unconfined
    )

    @Test
    fun `group mention with conversationId 0 resolves a conversation and persists a message`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val dispatcher = FakeAlertDispatcher()
            // No rules: isolate the persistence half of the trace (steps 1-2).
            val pipeline = pipeline(messageRepo, FakeRuleRepository(emptyList()), dispatcher)

            // Precondition: nothing seeded. This is the case the old tests skipped.
            assertThat(messageRepo.conversationStore).isEmpty()
            assertThat(messageRepo.messageStore).isEmpty()

            pipeline.submit(whatsappGroupMention())
            yield()

            // (a) a conversation row was find-or-created from the notification key,
            //     and a message row now references it (no FK violation thrown).
            assertThat(messageRepo.conversationStore).hasSize(1)
            val conversation = messageRepo.conversationStore.single()
            assertThat(conversation.appPackage).isEqualTo(AppPackage.WHATSAPP)
            assertThat(conversation.isGroup).isTrue()

            assertThat(messageRepo.messageStore).isNotEmpty()
            // Every stored message is stamped to the resolved id, never 0.
            assertThat(messageRepo.messageStore.map { it.conversationId }.toSet())
                .containsExactly(conversation.id)
            assertThat(conversation.id).isGreaterThan(0L)
            assertThat(messageRepo.messageStore.map { it.id }.all { it > 0L }).isTrue()
        }

    @Test
    fun `enabled keyword rule on a conversationId 0 group mention fires the alert with a real message id`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val dispatcher = FakeAlertDispatcher()
            val pipeline = pipeline(messageRepo, FakeRuleRepository(listOf(keywordRule)), dispatcher)

            pipeline.submit(whatsappGroupMention())
            yield()

            // (b) the keyword "booking" matched -> AlertDispatcher.fire was called.
            assertThat(dispatcher.fired).isNotEmpty()
            val match = dispatcher.fired.first()
            assertThat(match.rule.id).isEqualTo(keywordRule.id)
            // The fired match carries the real stored row id, not 0 — this is what
            // AlertDispatcher stamps into MatchLog.messageId.
            assertThat(match.message.id).isGreaterThan(0L)
            assertThat(messageRepo.messageStore.map { it.id })
                .contains(match.message.id)
        }

    /**
     * A "delete for everyone" message recovered by the Face 2 Xposed hook and delivered through
     * the UID-authenticated [com.quietping.capture.DeepCaptureProvider], which offers a
     * [RawEvent.DeepHookRecovered] to the pipeline. Carries `conversationId = 0` semantics like
     * every other source (only the conversationKey identifies the thread). Body holds the keyword.
     */
    private fun deepHookRecovered() = RawEvent.DeepHookRecovered(
        packageName = AppPackage.WHATSAPP.packageId,
        conversationKey = "wa-hook:weekend-trip",
        sender = "Carol",
        body = "@Dharsan can you confirm the booking",
        postedAt = 1_700_000_000_002L
    )

    @Test
    fun `deep-hook recovered message resolves a conversation and persists with DEEP_HOOK source`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val dispatcher = FakeAlertDispatcher()
            val pipeline = pipeline(messageRepo, FakeRuleRepository(emptyList()), dispatcher)

            assertThat(messageRepo.conversationStore).isEmpty()

            pipeline.submit(deepHookRecovered())
            yield()

            // A conversation row was find-or-created from the hook's key, and the recovered
            // message was archived against it (no FK violation) with the DEEP_HOOK source.
            assertThat(messageRepo.conversationStore).hasSize(1)
            val conversation = messageRepo.conversationStore.single()
            assertThat(conversation.appPackage).isEqualTo(AppPackage.WHATSAPP)

            assertThat(messageRepo.messageStore).hasSize(1)
            val stored = messageRepo.messageStore.single()
            assertThat(stored.conversationId).isEqualTo(conversation.id)
            assertThat(stored.id).isGreaterThan(0L)
            assertThat(stored.source).isEqualTo(CaptureSource.DEEP_HOOK)
            assertThat(stored.currentBody).isEqualTo("@Dharsan can you confirm the booking")
        }

    @Test
    fun `deep-hook recovered message still evaluates rules and fires on a keyword match`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val dispatcher = FakeAlertDispatcher()
            val pipeline = pipeline(messageRepo, FakeRuleRepository(listOf(keywordRule)), dispatcher)

            pipeline.submit(deepHookRecovered())
            yield()

            // Recovered content flows the same path as any capture: the "booking" keyword fires.
            assertThat(dispatcher.fired).isNotEmpty()
            val match = dispatcher.fired.first()
            assertThat(match.rule.id).isEqualTo(keywordRule.id)
            assertThat(match.message.id).isGreaterThan(0L)
        }

    @Test
    fun `disabled rule on a conversationId 0 group mention persists but never fires`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val dispatcher = FakeAlertDispatcher()
            val pipeline = pipeline(
                messageRepo,
                FakeRuleRepository(listOf(keywordRule.copy(enabled = false))),
                dispatcher
            )

            pipeline.submit(whatsappGroupMention())
            yield()

            // Still captured (capture-everything), but a disabled rule never fires.
            assertThat(messageRepo.messageStore).isNotEmpty()
            assertThat(dispatcher.fired).isEmpty()
        }

    /** Seed [event]'s conversation under its resolved (app, key) with a [watched] flag. */
    private fun FakeMessageRepository.seedGroup(event: RawEvent.NotificationPosted, watched: Boolean) {
        conversationStore += Conversation(
            id = 1L,
            appPackage = AppPackage.WHATSAPP,
            conversationKey = event.key,
            displayName = "Weekend Trip",
            isGroup = true,
            watched = watched
        )
    }

    @Test
    fun `muted group archives the message but never evaluates rules`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val dispatcher = FakeAlertDispatcher()
            // The "booking" keyword WOULD match — only the mute gate stops the alert.
            val pipeline = pipeline(messageRepo, FakeRuleRepository(listOf(keywordRule)), dispatcher)

            val event = whatsappGroupMention()
            messageRepo.seedGroup(event, watched = false)

            pipeline.submit(event)
            yield()

            // Capture-everything still holds: the message is archived to the Vault ...
            assertThat(messageRepo.messageStore).isNotEmpty()
            // ... but the per-group mute gate skipped rule evaluation, so no ping fired.
            assertThat(dispatcher.fired).isEmpty()
        }

    @Test
    fun `watched group still fires — the mute gate is group-scoped, not a blanket block`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val dispatcher = FakeAlertDispatcher()
            val pipeline = pipeline(messageRepo, FakeRuleRepository(listOf(keywordRule)), dispatcher)

            val event = whatsappGroupMention()
            messageRepo.seedGroup(event, watched = true)

            pipeline.submit(event)
            yield()

            // A watched group evaluates rules normally — the keyword fires.
            assertThat(dispatcher.fired).isNotEmpty()
            assertThat(dispatcher.fired.first().rule.id).isEqualTo(keywordRule.id)
        }

    @Test
    fun `setWatched toggles the flag and new groups default to watched`() =
        runBlocking {
            val messageRepo = FakeMessageRepository()
            val id = messageRepo.resolveConversationId(
                appPackage = AppPackage.WHATSAPP,
                conversationKey = "k-1",
                displayName = "Trip",
                isGroup = true
            )

            // Default for a freshly-detected group: watched (opt-out model).
            assertThat(messageRepo.conversationById(id)?.watched).isTrue()

            messageRepo.setWatched(id, false)
            assertThat(messageRepo.conversationById(id)?.watched).isFalse()

            messageRepo.setWatched(id, true)
            assertThat(messageRepo.conversationById(id)?.watched).isTrue()
        }
}
