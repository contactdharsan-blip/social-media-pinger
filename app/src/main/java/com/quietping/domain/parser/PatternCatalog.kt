package com.quietping.domain.parser

import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.TriggerType

/**
 * Versioned, data-driven catalog of the textual sentinels and regular expressions
 * QuietPing uses to recognize app/trigger-specific notification content.
 *
 * Detection is intentionally fragile by nature — the wording of WhatsApp /
 * Instagram / Messenger notifications changes with app version and, especially,
 * device language. Centralizing every pattern here (rather than scattering regex
 * across parsers and the rule engine) keeps them:
 *  - easy to audit and unit-test,
 *  - cheap to extend per locale (just add entries to the lists), and
 *  - safe to "fail closed" — when nothing matches we surface no false ping.
 *
 * [CATALOG_VERSION] is bumped whenever the patterns change so callers/telemetry
 * can reason about which revision produced a match.
 *
 * All matching is case-insensitive. Patterns are precompiled once at class-load
 * time; nothing here allocates per message.
 */
object PatternCatalog {

    /** Monotonic revision of the pattern set. Bump on every functional change. */
    const val CATALOG_VERSION: Int = 1

    private val CI = setOf(RegexOption.IGNORE_CASE)

    /**
     * Phrases that indicate a poll was created, keyed by app. Matched as a
     * substring (case-insensitive) against the notification body. WhatsApp also
     * prefixes polls with a bar-chart glyph; we match on the textual label, not
     * the emoji, so locale variants that drop the glyph still fire.
     */
    private val POLL_SENTINELS: Map<AppPackage, List<Regex>> = mapOf(
        AppPackage.WHATSAPP to listOf(
            Regex("""\bpoll:""", CI),
            Regex("""created a poll""", CI),
            Regex("""\bpoll\b.*\?""", CI)
        ),
        AppPackage.INSTAGRAM to listOf(
            Regex("""created a poll""", CI),
            Regex("""added a poll""", CI),
            Regex("""\bpoll\b""", CI)
        ),
        AppPackage.MESSENGER to listOf(
            Regex("""created a poll""", CI),
            Regex("""\bpoll:""", CI)
        ),
        AppPackage.FACEBOOK to listOf(
            Regex("""created a poll""", CI),
            Regex("""\bpoll:""", CI)
        )
    )

    /**
     * Phrases indicating the message mentions / replied-to the user. These back
     * [TriggerType.REPLY_MENTION] and the app-native "mentioned you" path of
     * [TriggerType.NAME_MENTION]. Generic enough to survive minor copy changes.
     */
    private val MENTION_SENTINELS: Map<AppPackage, List<Regex>> = mapOf(
        AppPackage.WHATSAPP to listOf(
            Regex("""mentioned you""", CI),
            Regex("""replied to you""", CI),
            Regex("""\breplied:""", CI)
        ),
        AppPackage.INSTAGRAM to listOf(
            Regex("""mentioned you""", CI),
            Regex("""replied to you""", CI),
            Regex("""replied to your""", CI)
        ),
        AppPackage.MESSENGER to listOf(
            Regex("""mentioned you""", CI),
            Regex("""replied to you""", CI),
            Regex("""replied to your""", CI)
        ),
        AppPackage.FACEBOOK to listOf(
            Regex("""mentioned you""", CI),
            Regex("""replied to you""", CI)
        )
    )

    /**
     * Phrases describing a group lifecycle event (added/removed/admin/call/etc.).
     * Backs [TriggerType.GROUP_EVENT]. WhatsApp & Messenger expose these as system
     * lines in the notification body; Instagram support is limited (smaller set).
     */
    private val GROUP_EVENT_SENTINELS: Map<AppPackage, List<Regex>> = mapOf(
        AppPackage.WHATSAPP to listOf(
            Regex("""added you""", CI),
            Regex("""\byou were added""", CI),
            Regex("""you're now an admin""", CI),
            Regex("""now an admin""", CI),
            Regex("""removed you""", CI),
            Regex("""changed the group""", CI),
            Regex("""\bmissed (voice|video|group) call""", CI),
            Regex("""\bincoming call""", CI),
            Regex("""started a call""", CI)
        ),
        AppPackage.MESSENGER to listOf(
            Regex("""added you""", CI),
            Regex("""added you to""", CI),
            Regex("""named the group""", CI),
            Regex("""\bmissed (a )?(voice|video)? ?call""", CI),
            Regex("""started a call""", CI)
        ),
        AppPackage.FACEBOOK to listOf(
            Regex("""added you""", CI),
            Regex("""\bmissed (a )?call""", CI)
        ),
        AppPackage.INSTAGRAM to listOf(
            Regex("""added you""", CI),
            Regex("""started a video chat""", CI)
        )
    )

    /**
     * Sentinels emitted when the *source app itself* announces a revoked/deleted
     * message (WhatsApp's "This message was deleted", Messenger's "unsent" line).
     * Used by the capture/vault path to flag a cached message as DELETED rather
     * than guessing from a removed notification. Not app-keyed because the wording
     * is effectively shared across the chat apps we cover.
     */
    private val DELETED_SENTINELS: List<Regex> = listOf(
        Regex("""this message was deleted""", CI),
        Regex("""you deleted this message""", CI),
        Regex("""message was deleted""", CI),
        Regex("""\bunsent a message""", CI),
        Regex("""removed a message""", CI),
        Regex("""deleted a message""", CI)
    )

    /** Lines an app uses as a "new messages" rollup that carry no real body. */
    private val SUMMARY_NOISE: List<Regex> = listOf(
        Regex("""^\d+ new messages?$""", CI),
        Regex("""^\d+ messages? from \d+ chats?$""", CI),
        Regex("""^\d+ new notifications?$""", CI),
        Regex("""^you have \d+ new""", CI),
        Regex("""^checking for new messages""", CI),
        Regex("""^@?\s*WhatsApp\s*$""", CI)
    )

    /** True if [text] contains a poll sentinel for [app]. */
    fun isPoll(app: AppPackage, text: String): Boolean =
        POLL_SENTINELS[app]?.any { it.containsMatchIn(text) } == true

    /** True if [text] contains a reply/mention sentinel for [app]. */
    fun isReplyOrMention(app: AppPackage, text: String): Boolean =
        MENTION_SENTINELS[app]?.any { it.containsMatchIn(text) } == true

    /** True if [text] contains a group-event sentinel for [app]. */
    fun isGroupEvent(app: AppPackage, text: String): Boolean =
        GROUP_EVENT_SENTINELS[app]?.any { it.containsMatchIn(text) } == true

    /** True if [text] is one of the source apps' "message deleted/unsent" lines. */
    fun isDeletedSentinel(text: String): Boolean =
        DELETED_SENTINELS.any { it.containsMatchIn(text) }

    /**
     * True if [text] is a content-free rollup line (e.g. "5 new messages") that
     * should never be ingested as a real message body.
     */
    fun isSummaryNoise(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        return SUMMARY_NOISE.any { it.containsMatchIn(t) }
    }

    /**
     * Match an explicit `@handle` mention of any of [handles] inside [text].
     * Word-boundary aware and case-insensitive. Returns true on the first hit.
     * Empty [handles] yields false (nothing to look for ⇒ no false ping).
     */
    fun mentionsHandle(text: String, handles: Collection<String>): Boolean {
        if (handles.isEmpty()) return false
        for (raw in handles) {
            val handle = raw.trim().removePrefix("@")
            if (handle.isEmpty()) continue
            val quoted = Regex.escape(handle)
            // Optional leading '@', bounded so "ann" doesn't match "annual".
            val pattern = Regex("""(^|[^A-Za-z0-9_])@?$quoted($|[^A-Za-z0-9_])""", CI)
            if (pattern.containsMatchIn(text)) return true
        }
        return false
    }
}
