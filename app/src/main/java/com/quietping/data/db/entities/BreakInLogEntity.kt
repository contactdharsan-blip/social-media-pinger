package com.quietping.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row recording a failed app-unlock attempt (privacy "break-in" log, AppLock /
 * Keepsafe parity). Standalone — no foreign keys; purely an append-only audit the
 * user can review. We log only a timestamp and a coarse [reason]; never any captured
 * content and (deliberately) no camera/intruder image — that would invite Play
 * camera-permission scrutiny.
 */
@Entity(
    tableName = "break_in_log",
    indices = [Index(value = ["attempted_at"])]
)
data class BreakInLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "attempted_at")
    val attemptedAt: Long,

    @ColumnInfo(name = "reason")
    val reason: String
)
