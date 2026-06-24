package com.quietping.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quietping.data.db.entities.MessageVersionEntity

/**
 * DAO for [MessageVersionEntity]. Versions are append-only; [nextVersionNo]
 * computes the next sequential number for a message so edit history stays ordered.
 */
@Dao
interface MessageVersionDao {

    @Insert
    suspend fun insert(version: MessageVersionEntity): Long

    @Query("SELECT * FROM message_versions WHERE message_id = :messageId ORDER BY version_no ASC")
    suspend fun versionsFor(messageId: Long): List<MessageVersionEntity>

    /** The next version number for [messageId] (max existing + 1, or 1 if none). */
    @Query("SELECT COALESCE(MAX(version_no), 0) + 1 FROM message_versions WHERE message_id = :messageId")
    suspend fun nextVersionNo(messageId: Long): Int

    @Query("SELECT body FROM message_versions WHERE id = :versionId LIMIT 1")
    suspend fun bodyOf(versionId: Long): String?
}
