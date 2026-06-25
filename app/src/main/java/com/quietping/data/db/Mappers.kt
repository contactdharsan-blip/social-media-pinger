package com.quietping.data.db

import com.quietping.data.db.dao.MessageWithVersions
import com.quietping.data.db.entities.ConversationEntity
import com.quietping.data.db.entities.MatchLogEntity
import com.quietping.data.db.entities.MessageEntity
import com.quietping.data.db.entities.MessageVersionEntity
import com.quietping.data.db.entities.RuleEntity
import com.quietping.data.db.entities.VipContactEntity
import com.quietping.domain.model.Conversation
import com.quietping.domain.model.MatchLog
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageVersion
import com.quietping.domain.model.Rule
import com.quietping.domain.model.VipContact

/**
 * Pure entity <-> domain mapping. Kept side-effect free so it is trivially unit
 * testable. The message mappers reconcile the append-only version table with the
 * domain [Message.currentBody] / [Message.versions] view.
 */

// ---- Conversation ----

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    appPackage = appPackage,
    conversationKey = conversationKey,
    displayName = displayName,
    isGroup = isGroup,
    watched = watched
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    appPackage = appPackage,
    conversationKey = conversationKey,
    displayName = displayName,
    isGroup = isGroup,
    watched = watched
)

// ---- Message version ----

fun MessageVersionEntity.toDomain(): MessageVersion = MessageVersion(
    id = id,
    messageId = messageId,
    versionNo = versionNo,
    body = body,
    capturedAt = capturedAt
)

// ---- Message (with assembled version history) ----

/**
 * Assemble a domain [Message] from its row + ordered version history. [currentBody]
 * is resolved from [MessageEntity.currentVersionId] when present, otherwise from the
 * highest-numbered version, otherwise empty (a message with no versions yet).
 */
fun MessageWithVersions.toDomain(): Message {
    val ordered = versions.sortedBy { it.versionNo }
    val currentBody = currentBodyFor(message, ordered)
    return Message(
        id = message.id,
        conversationId = message.conversationId,
        sender = message.sender,
        postedAt = message.postedAt,
        capturedAt = message.capturedAt,
        source = message.source,
        status = message.status,
        currentBody = currentBody,
        versions = ordered.map { it.toDomain() }
    )
}

private fun currentBodyFor(message: MessageEntity, ordered: List<MessageVersionEntity>): String {
    if (ordered.isEmpty()) return ""
    val byCurrentId = message.currentVersionId?.let { id -> ordered.firstOrNull { it.id == id } }
    return (byCurrentId ?: ordered.last()).body
}

// ---- Rule ----

fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    appPackage = appPackage,
    type = type,
    pattern = pattern,
    soundPreset = soundPreset,
    dndOverride = dndOverride,
    enabled = enabled,
    alertStyle = alertStyle,
    action = action,
    windowStartMin = windowStartMin,
    windowEndMin = windowEndMin
)

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    appPackage = appPackage,
    type = type,
    pattern = pattern,
    soundPreset = soundPreset,
    dndOverride = dndOverride,
    enabled = enabled,
    alertStyle = alertStyle,
    action = action,
    windowStartMin = windowStartMin,
    windowEndMin = windowEndMin
)

// ---- VIP contact ----

fun VipContactEntity.toDomain(): VipContact = VipContact(
    id = id,
    handle = handle,
    appPackage = appPackage
)

fun VipContact.toEntity(): VipContactEntity = VipContactEntity(
    id = id,
    handle = handle,
    appPackage = appPackage
)

// ---- Match log ----

fun MatchLogEntity.toDomain(): MatchLog = MatchLog(
    id = id,
    messageId = messageId,
    ruleId = ruleId,
    firedAt = firedAt,
    channelId = channelId
)

fun MatchLog.toEntity(): MatchLogEntity = MatchLogEntity(
    id = id,
    messageId = messageId,
    ruleId = ruleId,
    firedAt = firedAt,
    channelId = channelId
)
