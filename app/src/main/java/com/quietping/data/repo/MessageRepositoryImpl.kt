package com.quietping.data.repo

import androidx.room.withTransaction
import com.quietping.data.db.AppDatabase
import com.quietping.data.db.dao.ConversationDao
import com.quietping.data.db.dao.MessageDao
import com.quietping.data.db.dao.MessageVersionDao
import com.quietping.data.db.toDomain
import com.quietping.data.db.entities.ConversationEntity
import com.quietping.data.db.entities.MessageEntity
import com.quietping.data.db.entities.MessageVersionEntity
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.repo.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Room-backed [MessageRepository] implementing the Vault store.
 *
 * [upsert] performs dedupe + append-only version history in a single transaction:
 *  - re-observing an identical (conversation, sender, body) message is a no-op;
 *  - a changed body from the same sender within [EDIT_WINDOW_MS] appends a new
 *    version and flags the message EDITED;
 *  - anything else inserts a fresh message with version 1.
 *
 * [resolveConversationId] (part of the [MessageRepository] contract) and the local
 * [upsertConversation] helper let the capture/vault layer create a conversation
 * before ingesting its first message (its foreign-key parent).
 */
class MessageRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val versionDao: MessageVersionDao
) : MessageRepository {

    // ---- Reactive reads ----

    override fun conversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun thread(conversationId: Long): Flow<List<Message>> =
        messageDao.observeThread(conversationId).map { rows -> rows.map { it.toDomain() } }

    override fun deleted(): Flow<List<Message>> =
        messageDao.observeByStatus(MessageStatus.DELETED).map { rows -> rows.map { it.toDomain() } }

    override fun edited(): Flow<List<Message>> =
        messageDao.observeByStatus(MessageStatus.EDITED).map { rows -> rows.map { it.toDomain() } }

    // ---- Upsert (dedupe + version append) ----

    override suspend fun upsert(message: Message): Long = db.withTransaction {
        val incomingBody = message.currentBody

        // Exact duplicate already stored — nothing to do.
        messageDao.findLatestMatching(message.conversationId, message.sender, incomingBody)?.let { dup ->
            return@withTransaction dup.id
        }

        // Same sender, recent, different body => treat as an edit of the latest message.
        val editCandidate = messageDao.findLatestFromSender(message.conversationId, message.sender)
        if (editCandidate != null && (message.capturedAt - editCandidate.capturedAt) in 0..EDIT_WINDOW_MS) {
            appendVersion(editCandidate.id, incomingBody, message.capturedAt)
            messageDao.setStatus(editCandidate.id, MessageStatus.EDITED)
            return@withTransaction editCandidate.id
        }

        // Fresh message: insert row, then its first version, then point at it.
        val messageId = messageDao.insert(
            MessageEntity(
                id = 0L,
                conversationId = message.conversationId,
                sender = message.sender,
                postedAt = message.postedAt,
                capturedAt = message.capturedAt,
                source = message.source,
                status = if (message.status == MessageStatus.EDITED) MessageStatus.ACTIVE else message.status,
                currentVersionId = null
            )
        )
        appendVersion(messageId, incomingBody, message.capturedAt)
        messageId
    }

    /** Insert the next version for [messageId] and make it the current body. */
    private suspend fun appendVersion(messageId: Long, body: String, capturedAt: Long) {
        val versionNo = versionDao.nextVersionNo(messageId)
        val versionId = versionDao.insert(
            MessageVersionEntity(
                id = 0L,
                messageId = messageId,
                versionNo = versionNo,
                body = body,
                capturedAt = capturedAt
            )
        )
        messageDao.setCurrentVersion(messageId, versionId)
    }

    // ---- Retention purge ----

    override suspend fun purgeOlderThan(epochMillis: Long) {
        messageDao.deleteOlderThan(epochMillis)
    }

    override suspend fun conversationIdFor(appPackage: AppPackage, conversationKey: String): Long? =
        conversationDao.findByKey(appPackage, conversationKey)?.id

    override suspend fun conversationById(id: Long): Conversation? =
        conversationDao.findById(id)?.toDomain()

    override suspend fun setWatched(id: Long, watched: Boolean) =
        conversationDao.setWatched(id, watched)

    override suspend fun purgeOtpOlderThan(epochMillis: Long): Int = db.withTransaction {
        val otpIds = messageDao
            .bodiesBySourceOlderThan(com.quietping.domain.model.CaptureSource.SMS, epochMillis)
            .filter { com.quietping.domain.parser.PatternCatalog.isOtp(it.body) }
            .map { it.id }
        if (otpIds.isEmpty()) 0 else messageDao.deleteByIds(otpIds)
    }

    // ---- Conversation resolution (beyond the interface) ----

    /**
     * Find or create the conversation identified by ([appPackage], [conversationKey]),
     * refreshing its display name / group flag, and return its row id. Idempotent.
     */
    override suspend fun resolveConversationId(
        appPackage: AppPackage,
        conversationKey: String,
        displayName: String,
        isGroup: Boolean
    ): Long = db.withTransaction {
        val existing = conversationDao.findByKey(appPackage, conversationKey)
        if (existing != null) {
            if (existing.displayName != displayName || existing.isGroup != isGroup) {
                conversationDao.updateMeta(existing.id, displayName, isGroup)
            }
            existing.id
        } else {
            val inserted = conversationDao.insert(
                ConversationEntity(
                    id = 0L,
                    appPackage = appPackage,
                    conversationKey = conversationKey,
                    displayName = displayName,
                    isGroup = isGroup
                )
            )
            // IGNORE conflict returns -1 on a race; re-read to get the winner's id.
            if (inserted == -1L) {
                conversationDao.findByKey(appPackage, conversationKey)?.id
                    ?: error("Conversation upsert failed for $appPackage/$conversationKey")
            } else {
                inserted
            }
        }
    }

    /** Find or create the [conversation] (by app+key) and return its row id. */
    suspend fun upsertConversation(conversation: Conversation): Long = resolveConversationId(
        appPackage = conversation.appPackage,
        conversationKey = conversation.conversationKey,
        displayName = conversation.displayName,
        isGroup = conversation.isGroup
    )

    /**
     * Flag the most recent matching message in a conversation as DELETED ("Recovered"),
     * returning the affected message id, or null if no match was found. When [body] is
     * null the latest message from any sender in the conversation is targeted.
     */
    suspend fun markDeleted(conversationId: Long, sender: String?, body: String?): Long? = db.withTransaction {
        val target = when {
            body != null && sender != null ->
                messageDao.findLatestMatching(conversationId, sender, body)
            sender != null ->
                messageDao.findLatestFromSender(conversationId, sender)
            else -> null
        } ?: return@withTransaction null
        messageDao.setStatus(target.id, MessageStatus.DELETED)
        target.id
    }

    companion object {
        /** Max gap between an original and a changed body to be treated as an edit (10 min). */
        const val EDIT_WINDOW_MS: Long = 10 * 60 * 1000L
    }
}
