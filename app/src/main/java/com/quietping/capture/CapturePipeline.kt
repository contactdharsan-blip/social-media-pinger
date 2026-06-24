package com.quietping.capture

import android.util.Log
import com.quietping.domain.alerts.AlertDispatcher
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Message
import com.quietping.domain.model.RawEvent
import com.quietping.domain.parser.MessageParser
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.repo.RuleRepository
import com.quietping.domain.rules.RuleEngine
import com.quietping.domain.vault.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * The single ingestion pipeline for every capture source.
 *
 * Capture services (notification listener, SMS observer, accessibility) never do
 * real work on their binder/callback thread: they normalize their input into a
 * [RawEvent] and hand it to [submit], which offloads onto an internal coroutine
 * [Channel] consumed on a background dispatcher.
 *
 * Routing:
 *  - [RawEvent.NotificationPosted]      -> matching [MessageParser] -> for each
 *                                          [Message]: [VaultManager.ingest] then
 *                                          [RuleEngine.evaluate] -> [AlertDispatcher.fire].
 *  - [RawEvent.AccessibilityCaptured]   -> synthesized into a notification-shaped
 *                                          event and routed through the same path.
 *  - [RawEvent.NotificationRemoved]     -> deletion heuristic -> [VaultManager.markDeleted].
 *  - [RawEvent.SmsChanged]              -> SMS diff (new rows ingested + matched,
 *                                          removed rows flagged deleted).
 *
 * The pipeline owns its own [CoroutineScope] so it survives individual service
 * binds/unbinds and processes events serially in arrival order.
 */
@Singleton
class CapturePipeline @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards MessageParser>,
    private val ruleEngine: RuleEngine,
    private val vaultManager: VaultManager,
    private val alertDispatcher: AlertDispatcher,
    private val ruleRepository: RuleRepository,
    private val messageRepository: MessageRepository,
    dispatcher: CoroutineContext = kotlinx.coroutines.Dispatchers.IO,
) {

    /**
     * Unbounded so a callback thread (which must return fast) is never suspended
     * by back-pressure when offering an event.
     */
    private val events = Channel<RawEvent>(capacity = Channel.UNLIMITED)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launch {
            for (event in events) {
                runCatching { route(event) }
                    .onFailure { Log.w(TAG, "Failed to process $event", it) }
            }
        }
    }

    /**
     * Enqueue [event] for background processing. Suspends only momentarily on an
     * unbounded channel (effectively never); callers on a binder thread should use
     * [offer] instead to stay strictly non-blocking.
     */
    suspend fun submit(event: RawEvent) {
        events.send(event)
    }

    /**
     * Non-suspending enqueue for binder/callback threads. Never blocks; the channel
     * is unbounded so the offer always succeeds while the pipeline is alive.
     */
    fun offer(event: RawEvent) {
        events.trySend(event)
    }

    private suspend fun route(event: RawEvent) {
        when (event) {
            is RawEvent.NotificationPosted -> handleNotification(event)
            is RawEvent.AccessibilityCaptured -> handleAccessibility(event)
            is RawEvent.NotificationRemoved -> handleNotificationRemoved(event)
            is RawEvent.SmsChanged -> handleSmsChanged(event)
        }
    }

    private suspend fun handleNotification(event: RawEvent.NotificationPosted) {
        val parser = parsers.firstOrNull { it.canParse(event) } ?: return
        val messages = parser.parse(event)
        if (messages.isEmpty()) return
        for (message in messages) {
            ingestAndMatch(message)
        }
    }

    /**
     * Accessibility captures arrive as plain visible text rather than a structured
     * notification. We adapt them into a [RawEvent.NotificationPosted] so the same
     * per-app [MessageParser] handles the quirks, then mark the resulting messages
     * as ACCESSIBILITY-sourced.
     */
    private suspend fun handleAccessibility(event: RawEvent.AccessibilityCaptured) {
        val adapted = RawEvent.NotificationPosted(
            packageName = event.packageName,
            key = "a11y:${event.packageName}:${event.postedAt}",
            title = event.title,
            text = event.text,
            bigText = event.text,
            subText = null,
            messages = emptyList(),
            postedAt = event.postedAt,
        )
        val parser = parsers.firstOrNull { it.canParse(adapted) } ?: return
        val messages = parser.parse(adapted)
        if (messages.isEmpty()) return
        for (message in messages) {
            ingestAndMatch(
                message.copy(source = com.quietping.domain.model.CaptureSource.ACCESSIBILITY),
            )
        }
    }

    /**
     * Removal of a chat-app notification is a deletion *heuristic* only. We forward
     * the conversation context to the [VaultManager], which decides (together with
     * revoked-message sentinels handled in the parser) whether to surface it as a
     * recovered/deleted message. The notification [RawEvent.NotificationRemoved.key]
     * doubles as the conversation key for chat apps where no finer id is available.
     */
    private suspend fun handleNotificationRemoved(event: RawEvent.NotificationRemoved) {
        val app = AppPackage.fromPackageId(event.packageName) ?: return
        vaultManager.markDeleted(
            conversationKey = event.key,
            appPackage = app,
            body = null,
        )
    }

    /**
     * Diff the SMS/MMS provider against the local cache. New/changed rows are
     * ingested and matched; rows present in the cache but missing from the provider
     * are flagged deleted. The actual provider read lives in [SmsObserver.readNewSms]
     * (it owns the [android.content.Context]); the pipeline only orchestrates the
     * domain side, so a [RawEvent.SmsChanged] with no pre-read payload is a no-op
     * here and the messages are submitted directly by the observer as
     * [RawEvent.NotificationPosted]-equivalent ingests via [ingestSms].
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun handleSmsChanged(event: RawEvent.SmsChanged) {
        // The observer reads provider rows on its own thread (it holds the Context)
        // and pushes resulting Messages through [ingestSms]; the bare change signal
        // carries no body, so there is nothing to diff here without the read result.
    }

    /**
     * Entry point used by [SmsObserver] for already-read SMS [Message]s: archive +
     * evaluate rules exactly like a parsed notification.
     */
    suspend fun ingestSms(message: Message) {
        ingestAndMatch(message)
    }

    /**
     * Mark an SMS conversation's message as deleted (provider diff found it gone).
     */
    suspend fun markSmsDeleted(conversationKey: String, body: String?) {
        vaultManager.markDeleted(conversationKey, AppPackage.SMS, body)
    }

    private suspend fun ingestAndMatch(message: Message) {
        // 1. Always archive into the encrypted Vault first (capture-everything).
        vaultManager.ingest(message)

        // 2. Evaluate enabled rules for this message's app and fire any matches.
        val app = appPackageFor(message) ?: return
        val rules = ruleRepository.rulesFor(app).first().filter { it.enabled }
        if (rules.isEmpty()) return

        val matches = ruleEngine.evaluate(message, rules)
        for (match in matches) {
            alertDispatcher.fire(match)
        }
    }

    /**
     * Resolve the [AppPackage] for a [Message]. The domain [Message] references its
     * conversation by id; we look the conversation up through the repository to read
     * its app. Falls back to null (skip rule matching) when the conversation is not
     * yet visible.
     */
    private suspend fun appPackageFor(message: Message): AppPackage? {
        val conversations = messageRepository.conversations().first()
        return conversations.firstOrNull { it.id == message.conversationId }?.appPackage
    }

    private companion object {
        const val TAG = "CapturePipeline"
    }
}
