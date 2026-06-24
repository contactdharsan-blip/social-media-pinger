package com.quietping.domain.vault

import com.google.common.truth.Truth.assertThat
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.repo.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VaultManagerImplTest {

    /**
     * In-memory [MessageRepository] that performs upsert-by-id (id 0 ⇒ insert with
     * a generated id) — enough to validate VaultManager's dedupe/version/delete
     * decisions without Room.
     */
    private class FakeMessageRepository(
        seedConversations: List<Conversation> = emptyList(),
        seedMessages: List<Message> = emptyList()
    ) : MessageRepository {
        val conversationStore = seedConversations.toMutableList()
        val messageStore = seedMessages.toMutableList()
        private var nextId = (seedMessages.maxOfOrNull { it.id } ?: 0L) + 1

        override fun conversations(): Flow<List<Conversation>> = flowOf(conversationStore.toList())

        override fun thread(conversationId: Long): Flow<List<Message>> =
            flowOf(messageStore.filter { it.conversationId == conversationId }
                .sortedBy { it.postedAt })

        override fun deleted(): Flow<List<Message>> =
            flowOf(messageStore.filter { it.status == MessageStatus.DELETED })

        override fun edited(): Flow<List<Message>> =
            flowOf(messageStore.filter { it.status == MessageStatus.EDITED })

        override suspend fun upsert(message: Message): Long {
            if (message.id == 0L) {
                val withId = message.copy(id = nextId++)
                messageStore.add(withId)
                return withId.id
            }
            val idx = messageStore.indexOfFirst { it.id == message.id }
            if (idx >= 0) messageStore[idx] = message else messageStore.add(message)
            return message.id
        }

        override suspend fun purgeOlderThan(epochMillis: Long) {
            messageStore.removeAll { it.capturedAt < epochMillis }
        }
    }

    private val conversation = Conversation(
        id = 10L,
        appPackage = AppPackage.WHATSAPP,
        conversationKey = "conv-1",
        displayName = "Alice",
        isGroup = false
    )

    private fun msg(
        id: Long = 0L,
        body: String,
        sender: String = "Alice",
        postedAt: Long = 1_000L,
        capturedAt: Long = 1_000L,
        status: MessageStatus = MessageStatus.ACTIVE
    ) = Message(
        id = id,
        conversationId = 10L,
        sender = sender,
        postedAt = postedAt,
        capturedAt = capturedAt,
        source = CaptureSource.NOTIFICATION,
        status = status,
        currentBody = body
    )

    @Test
    fun `ingest inserts a new message`() = runTest {
        val repo = FakeMessageRepository(seedConversations = listOf(conversation))
        val vault = VaultManagerImpl(repo)
        vault.ingest(msg(body = "hello"))
        assertThat(repo.messageStore).hasSize(1)
        assertThat(repo.messageStore.first().currentBody).isEqualTo("hello")
    }

    @Test
    fun `ingest deduplicates identical re-delivery`() = runTest {
        val seed = msg(id = 1L, body = "hello")
        val repo = FakeMessageRepository(listOf(conversation), listOf(seed))
        val vault = VaultManagerImpl(repo)
        // Same edit-key (sender+postedAt) and same body ⇒ duplicate, no new row.
        vault.ingest(msg(body = "hello"))
        assertThat(repo.messageStore).hasSize(1)
    }

    @Test
    fun `ingest appends a version and marks EDITED on body change`() = runTest {
        val seed = msg(id = 1L, body = "hello", capturedAt = 1_000L)
        val repo = FakeMessageRepository(listOf(conversation), listOf(seed))
        val vault = VaultManagerImpl(repo)
        // Same edit-key, different body, within window ⇒ EDITED + version appended.
        vault.ingest(msg(body = "hello (edited)", capturedAt = 2_000L))

        assertThat(repo.messageStore).hasSize(1)
        val updated = repo.messageStore.first { it.id == 1L }
        assertThat(updated.status).isEqualTo(MessageStatus.EDITED)
        assertThat(updated.currentBody).isEqualTo("hello (edited)")
        assertThat(updated.versions).hasSize(1)
        assertThat(updated.versions.first().body).isEqualTo("hello (edited)")
        assertThat(updated.versions.first().versionNo).isEqualTo(2)
    }

    @Test
    fun `ingest treats out-of-window change as a new message`() = runTest {
        val seed = msg(id = 1L, body = "hello", capturedAt = 1_000L)
        val repo = FakeMessageRepository(listOf(conversation), listOf(seed))
        val vault = VaultManagerImpl(repo)
        val twoDaysLater = 1_000L + 2L * 24 * 60 * 60 * 1000
        vault.ingest(msg(body = "totally different", capturedAt = twoDaysLater))
        assertThat(repo.messageStore).hasSize(2)
    }

    @Test
    fun `ingest delegates to store when conversation unresolved`() = runTest {
        // No conversation seeded ⇒ cannot derive stable key ⇒ delegate (insert).
        val repo = FakeMessageRepository()
        val vault = VaultManagerImpl(repo)
        vault.ingest(msg(body = "orphan"))
        assertThat(repo.messageStore).hasSize(1)
    }

    @Test
    fun `markDeleted flags matching cached message`() = runTest {
        val seed = msg(id = 1L, body = "secret plan")
        val repo = FakeMessageRepository(listOf(conversation), listOf(seed))
        val vault = VaultManagerImpl(repo)
        vault.markDeleted("conv-1", AppPackage.WHATSAPP, body = "secret plan")
        assertThat(repo.messageStore.first { it.id == 1L }.status).isEqualTo(MessageStatus.DELETED)
    }

    @Test
    fun `markDeleted with null body flags most recent message`() = runTest {
        val older = msg(id = 1L, body = "first", postedAt = 1_000L)
        val newer = msg(id = 2L, body = "second", postedAt = 2_000L)
        val repo = FakeMessageRepository(listOf(conversation), listOf(older, newer))
        val vault = VaultManagerImpl(repo)
        vault.markDeleted("conv-1", AppPackage.WHATSAPP, body = null)
        assertThat(repo.messageStore.first { it.id == 2L }.status).isEqualTo(MessageStatus.DELETED)
        assertThat(repo.messageStore.first { it.id == 1L }.status).isEqualTo(MessageStatus.ACTIVE)
    }

    @Test
    fun `markDeleted is a no-op for unknown conversation`() = runTest {
        val seed = msg(id = 1L, body = "x")
        val repo = FakeMessageRepository(listOf(conversation), listOf(seed))
        val vault = VaultManagerImpl(repo)
        vault.markDeleted("does-not-exist", AppPackage.WHATSAPP, body = null)
        assertThat(repo.messageStore.first { it.id == 1L }.status).isEqualTo(MessageStatus.ACTIVE)
    }
}
