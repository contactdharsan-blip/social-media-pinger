package com.quietping.domain.model

/**
 * A logical conversation (1:1 or group) within a specific app. [conversationKey]
 * is the stable per-app identifier used to dedupe messages into a thread.
 *
 * @param watched whether incoming messages in this conversation are evaluated for
 *   alerts. Only meaningful for groups ([isGroup] = true): a muted group
 *   (`watched = false`) is still archived to the Vault but never reaches the
 *   RuleEngine. New conversations default to watched; 1:1 chats ignore the flag.
 */
data class Conversation(
    val id: Long,
    val appPackage: AppPackage,
    val conversationKey: String,
    val displayName: String,
    val isGroup: Boolean,
    val watched: Boolean = true
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
 * How an alert escalates when its rule fires (competitor parity — BuzzKill "Remind
 * me" / "Alarm", Missed-Notifications-Reminder).
 *  - [STANDARD]   one IMPORTANCE_HIGH heads-up (today's behavior).
 *  - [PERSISTENT] re-ping on an interval until the source notification is read
 *                 (removed), bounded by a max repeat count.
 *  - [CRITICAL]   full-screen intent + ALARM-stream channel that pierces DND.
 */
enum class AlertStyle { STANDARD, PERSISTENT, CRITICAL }

/**
 * What the engine does when a rule matches.
 *  - [ALERT]    fire the alert (default).
 *  - [SUPPRESS] block — archive the message as blocked and fire nothing
 *               (keyword/phrase block, Pulse-style).
 */
enum class RuleAction { ALERT, SUPPRESS }

/**
 * A user-defined alert condition for a given app. When [enabled], the RuleEngine
 * evaluates incoming messages of [appPackage] against [type] using [pattern]
 * (keyword/regex/handle depending on type). On match, AlertDispatcher fires using
 * [soundPreset], optionally bypassing DND via [dndOverride].
 *
 * @param alertStyle   escalation behavior on match (§ Bundle 1).
 * @param action       fire vs. suppress (§ Bundle 4 keyword block).
 * @param windowStartMin start of the rule's active window, minutes-since-midnight
 *                       (0..1439); `-1` ⇒ no window (always active).
 * @param windowEndMin   end of the active window, minutes-since-midnight (exclusive);
 *                       wraps past midnight when end < start (e.g. 22:00→07:00).
 */
data class Rule(
    val id: Long,
    val appPackage: AppPackage,
    val type: TriggerType,
    val pattern: String,
    val soundPreset: SoundPreset,
    val dndOverride: Boolean,
    val enabled: Boolean,
    val alertStyle: AlertStyle = AlertStyle.STANDARD,
    val action: RuleAction = RuleAction.ALERT,
    val windowStartMin: Int = -1,
    val windowEndMin: Int = -1
) {
    /** True when this rule has a configured active window (vs. always-on). */
    val hasWindow: Boolean
        get() = windowStartMin in 0..1439 && windowEndMin in 0..1439 && windowStartMin != windowEndMin

    /**
     * Whether the rule is active at [minuteOfDay] (0..1439). Always true when no
     * window is set. Handles a window that wraps past midnight.
     */
    fun activeAt(minuteOfDay: Int): Boolean {
        if (!hasWindow) return true
        return if (windowStartMin < windowEndMin) {
            minuteOfDay >= windowStartMin && minuteOfDay < windowEndMin
        } else {
            // wraps midnight: active from start..24:00 and 00:00..end
            minuteOfDay >= windowStartMin || minuteOfDay < windowEndMin
        }
    }
}

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

/**
 * One recorded failed app-unlock attempt (privacy break-in log). [reason] is a
 * coarse tag (e.g. "biometric_failed", "decoy_pin"); no content, no image.
 */
data class BreakInEvent(
    val id: Long,
    val attemptedAt: Long,
    val reason: String
)

/**
 * A single file held in the on-device media vault — an image / video / voice note
 * QuietPing copied out of a messaging app before a "delete for everyone" could
 * remove the original. There is no message link in the model, so this is a flat
 * gallery entry, not a thread reference.
 *
 * @param path         absolute path to the file in the app's private storage
 *                     (`filesDir/media_vault/`); also the stable list key.
 * @param appLabel     lower-case source-app tag parsed from the file name
 *                     (e.g. "whatsapp"), or "" when it can't be determined.
 * @param displayName  the original file name the source app used.
 * @param sizeBytes    file size in bytes.
 * @param lastModified capture time (file mtime, epoch millis) — drives ordering.
 */
data class MediaItem(
    val path: String,
    val appLabel: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long
)
