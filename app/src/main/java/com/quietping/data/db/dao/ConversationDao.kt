package com.quietping.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quietping.data.db.entities.ConversationEntity
import com.quietping.domain.model.AppPackage
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ConversationEntity]. [observeAll] orders conversations by the timestamp
 * of their most recently captured message (most-recent first), so an idle
 * conversation with no messages sorts last.
 */
@Dao
interface ConversationDao {

    @Query(
        """
        SELECT c.* FROM conversations AS c
        LEFT JOIN (
            SELECT conversation_id, MAX(captured_at) AS last_at
            FROM messages
            GROUP BY conversation_id
        ) AS m ON m.conversation_id = c.id
        ORDER BY COALESCE(m.last_at, 0) DESC, c.id DESC
        """
    )
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ConversationEntity?

    /**
     * Look up the conversation row by its stable per-app identity. Used to dedupe
     * an incoming message into an existing thread before inserting a new one.
     */
    @Query(
        "SELECT * FROM conversations WHERE app_package = :appPackage AND conversation_key = :conversationKey LIMIT 1"
    )
    suspend fun findByKey(appPackage: AppPackage, conversationKey: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET display_name = :displayName, is_group = :isGroup WHERE id = :id")
    suspend fun updateMeta(id: Long, displayName: String, isGroup: Boolean)

    /** Toggle the per-group alert watch flag. Leaves all other columns untouched. */
    @Query("UPDATE conversations SET watched = :watched WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean)
}
