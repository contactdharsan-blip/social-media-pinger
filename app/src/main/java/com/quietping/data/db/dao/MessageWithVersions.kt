package com.quietping.data.db.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.quietping.data.db.entities.MessageEntity
import com.quietping.data.db.entities.MessageVersionEntity

/**
 * Query container pairing a [MessageEntity] with its full, ordered version history.
 * Room populates [versions] via the message_id relation. The mapper picks the body
 * of [MessageEntity.currentVersionId] (falling back to the highest version) as the
 * domain message's current body.
 */
data class MessageWithVersions(
    @Embedded
    val message: MessageEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "message_id"
    )
    val versions: List<MessageVersionEntity>
)
