package com.quietping.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.quietping.data.db.entities.MessageEntity
import com.quietping.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [MessageEntity]. Read queries are [Transaction]-wrapped and return
 * [MessageWithVersions] so each emitted message carries its ordered version
 * history. Write helpers are intentionally granular; the repository composes them
 * inside a single transaction to perform dedupe + version-append upserts.
 */
@Dao
interface MessageDao {

    // ---- Reactive reads (Vault) ----

    @Transaction
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY posted_at ASC, id ASC")
    fun observeThread(conversationId: Long): Flow<List<MessageWithVersions>>

    @Transaction
    @Query("SELECT * FROM messages WHERE status = :status ORDER BY captured_at DESC, id DESC")
    fun observeByStatus(status: MessageStatus): Flow<List<MessageWithVersions>>

    // ---- Point lookups (capture / dedupe) ----

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun findWithVersions(id: Long): MessageWithVersions?

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MessageEntity?

    /**
     * The most recent message in [conversationId] from [sender] whose latest body
     * equals [body]. Used to detect a re-observed (duplicate) message so it is not
     * stored twice.
     */
    @Query(
        """
        SELECT m.* FROM messages AS m
        INNER JOIN message_versions AS v ON v.id = m.current_version_id
        WHERE m.conversation_id = :conversationId
          AND m.sender = :sender
          AND v.body = :body
        ORDER BY m.captured_at DESC, m.id DESC
        LIMIT 1
        """
    )
    suspend fun findLatestMatching(conversationId: Long, sender: String, body: String): MessageEntity?

    /**
     * The most recent message in [conversationId] from [sender] regardless of body.
     * Used as the edit candidate when a changed body arrives for the same sender.
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId AND sender = :sender
        ORDER BY captured_at DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun findLatestFromSender(conversationId: Long, sender: String): MessageEntity?

    // ---- Writes ----

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET current_version_id = :versionId WHERE id = :messageId")
    suspend fun setCurrentVersion(messageId: Long, versionId: Long)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun setStatus(messageId: Long, status: MessageStatus)

    // ---- Retention purge ----

    @Query("DELETE FROM messages WHERE captured_at < :epochMillis")
    suspend fun deleteOlderThan(epochMillis: Long): Int
}
