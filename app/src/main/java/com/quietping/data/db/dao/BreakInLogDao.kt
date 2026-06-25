package com.quietping.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quietping.data.db.entities.BreakInLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the [BreakInLogEntity] privacy audit trail.
 */
@Dao
interface BreakInLogDao {

    @Query("SELECT * FROM break_in_log ORDER BY attempted_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BreakInLogEntity>>

    @Insert
    suspend fun insert(entry: BreakInLogEntity): Long

    @Query("DELETE FROM break_in_log")
    suspend fun clear()
}
