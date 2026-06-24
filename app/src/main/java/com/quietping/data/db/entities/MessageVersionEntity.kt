package com.quietping.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for one append-only revision of a message body. [versionNo] starts at 1
 * and increments per observed edit, yielding free edit history. Deleting the parent
 * [MessageEntity] cascades to its versions.
 */
@Entity(
    tableName = "message_versions",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["message_id", "version_no"], unique = true)
    ]
)
data class MessageVersionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "version_no")
    val versionNo: Int,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long
)
