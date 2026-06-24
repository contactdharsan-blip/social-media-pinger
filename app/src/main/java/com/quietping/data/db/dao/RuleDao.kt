package com.quietping.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.quietping.data.db.entities.RuleEntity
import com.quietping.domain.model.AppPackage
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [RuleEntity]. Supports the full rule list, the per-app filtered list, and
 * an enabled-only query the RuleEngine can use to load active rules cheaply.
 */
@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY id ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE app_package = :app ORDER BY id ASC")
    fun observeForApp(app: AppPackage): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY id ASC")
    fun observeEnabled(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 AND app_package = :app ORDER BY id ASC")
    suspend fun enabledForApp(app: AppPackage): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RuleEntity): Long

    @Update
    suspend fun update(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
