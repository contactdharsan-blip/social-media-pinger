package com.quietping.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietping.domain.model.AppPackage

/**
 * Room row for a logical conversation. The pair ([appPackage], [conversationKey])
 * is unique so capture/parsing can dedupe captured messages into a single thread.
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["app_package", "conversation_key"], unique = true)
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "app_package")
    val appPackage: AppPackage,

    @ColumnInfo(name = "conversation_key")
    val conversationKey: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "is_group")
    val isGroup: Boolean
)
