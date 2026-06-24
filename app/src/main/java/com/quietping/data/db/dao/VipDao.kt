package com.quietping.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quietping.data.db.entities.VipContactEntity
import com.quietping.domain.model.AppPackage
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [VipContactEntity]. Insert ignores duplicates (the (app, handle) unique
 * index guards against double-starring the same contact).
 */
@Dao
interface VipDao {

    @Query("SELECT * FROM vip_contacts ORDER BY handle COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<VipContactEntity>>

    @Query("SELECT * FROM vip_contacts WHERE app_package = :app ORDER BY handle COLLATE NOCASE ASC")
    suspend fun forApp(app: AppPackage): List<VipContactEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(vip: VipContactEntity): Long

    @Query("DELETE FROM vip_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
