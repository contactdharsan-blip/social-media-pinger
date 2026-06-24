package com.quietping.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quietping.data.db.entities.MatchLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [MatchLogEntity], powering Home's reactive match feed.
 */
@Dao
interface MatchDao {

    @Query("SELECT * FROM match_log ORDER BY fired_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MatchLogEntity>>

    @Insert
    suspend fun insert(match: MatchLogEntity): Long
}
