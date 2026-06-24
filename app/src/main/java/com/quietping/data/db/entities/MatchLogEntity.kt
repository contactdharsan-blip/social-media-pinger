package com.quietping.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row recording a fired alert for Home's match feed. References the message
 * and rule that produced it; both FKs cascade-delete so audit rows never dangle
 * after a retention purge or rule deletion.
 */
@Entity(
    tableName = "match_log",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["rule_id"]),
        Index(value = ["fired_at"])
    ]
)
data class MatchLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "rule_id")
    val ruleId: Long,

    @ColumnInfo(name = "fired_at")
    val firedAt: Long,

    @ColumnInfo(name = "channel_id")
    val channelId: String
)
