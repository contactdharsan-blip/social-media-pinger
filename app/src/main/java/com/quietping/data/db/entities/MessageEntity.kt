package com.quietping.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.MessageStatus

/**
 * Room row for a captured message. The full body history lives in
 * [MessageVersionEntity]; [currentVersionId] softly points at the latest version
 * row (no hard FK, to avoid an insert ordering cycle — a message is inserted before
 * its first version exists). [status] drives Vault presentation (edited/recovered).
 *
 * Deleting the parent [ConversationEntity] cascades to its messages.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["status"]),
        Index(value = ["captured_at"]),
        Index(value = ["current_version_id"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "posted_at")
    val postedAt: Long,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,

    @ColumnInfo(name = "source")
    val source: CaptureSource,

    @ColumnInfo(name = "status")
    val status: MessageStatus,

    /** Soft reference to the latest [MessageVersionEntity.id]; null until the first version is written. */
    @ColumnInfo(name = "current_version_id")
    val currentVersionId: Long? = null
)
