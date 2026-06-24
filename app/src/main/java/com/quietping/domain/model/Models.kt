package com.quietping.domain.model

/**
 * A logical conversation (1:1 or group) within a specific app. [conversationKey]
 * is the stable per-app identifier used to dedupe messages into a thread.
 */
data class Conversation(
    val id: Long,
    val appPackage: AppPackage,
    val conversationKey: String,
    val displayName: String,
    val isGroup: Boolean
)

/**
 * One captured revision of a [Message] body. Append-only: a new version is added
 * whenever the same message id is observed with changed text, giving free edit
 * history. [versionNo] starts at 1 and increments per edit.
 */
data class MessageVersion(
    val id: Long,
    val messageId: Long,
    val versionNo: Int,
    val body: String,
    val capturedAt: Long
)

/**
 * A captured message. [currentBody] mirrors the latest [MessageVersion]; [versions]
 * holds the ordered history (v1..vN) when loaded for the Vault thread.
 *
 * @param postedAt   when the source app reports the message was posted (best effort).
 * @param capturedAt when QuietPing first observed it (monotonic, always present).
 */
data class Message(
    val id: Long,
    val conversationId: Long,
    val sender: String,
    val postedAt: Long,
    val capturedAt: Long,
    val source: CaptureSource,
    val status: MessageStatus,
    val currentBody: String,
    val versions: List<MessageVersion> = emptyList()
)

/**
 * A user-defined alert condition for a given app. When [enabled], the RuleEngine
 * evaluates incoming messages of [appPackage] against [type] using [pattern]
 * (keyword/regex/handle depending on type). On match, AlertDispatcher fires using
 * [soundPreset], optionally bypassing DND via [dndOverride].
 */
data class Rule(
    val id: Long,
    val appPackage: AppPackage,
    val type: TriggerType,
    val pattern: String,
    val soundPreset: SoundPreset,
    val dndOverride: Boolean,
    val enabled: Boolean
)

/**
 * A starred contact for [appPackage]. Messages whose sender matches [handle]
 * fire a VIP_CONTACT rule regardless of content.
 */
data class VipContact(
    val id: Long,
    val handle: String,
    val appPackage: AppPackage
)

/**
 * Audit record of a fired alert: the [messageId] that matched [ruleId], when it
 * [firedAt], and which notification [channelId] delivered it. Powers Home's feed.
 */
data class MatchLog(
    val id: Long,
    val messageId: Long,
    val ruleId: Long,
    val firedAt: Long,
    val channelId: String
)

/**
 * Output of the RuleEngine: the [rule] that matched the [message]. Carried to the
 * AlertDispatcher to drive channel selection, sound, and DND behavior.
 */
data class MatchResult(
    val rule: Rule,
    val message: Message
)
