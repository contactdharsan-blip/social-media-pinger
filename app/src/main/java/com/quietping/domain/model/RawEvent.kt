package com.quietping.domain.model

/**
 * The single normalized event type produced by every capture source
 * (NotificationListener, SMS ContentObserver, Accessibility). Capture services
 * emit these onto the ingestion channel; parsers and the VaultManager consume
 * them off the binder/callback thread.
 */
sealed interface RawEvent {

    /**
     * A status-bar notification was posted by [packageName]. [key] is the stable
     * StatusBarNotification key. [messages] is the flattened MessagingStyle list
     * as (sender, body) pairs when present; the title/text/bigText/subText extras
     * are forwarded raw for parser fallback.
     */
    data class NotificationPosted(
        val packageName: String,
        val key: String,
        val title: String?,
        val text: String?,
        val bigText: String?,
        val subText: String?,
        val messages: List<Pair<String, String>>,
        val postedAt: Long,
        /**
         * Per-entry timestamps parallel to [messages], from each MessagingStyle
         * bundle's `time` field. Used only by the deletion array-differ to give each
         * message a *stable* identity across re-posts (the notification's own
         * [postedAt] is shared by every entry and cannot distinguish them). Empty
         * when the source isn't MessagingStyle or omitted the field — the differ
         * then safely no-ops rather than guessing.
         */
        val messageTimes: List<Long> = emptyList()
    ) : RawEvent

    /**
     * A previously posted notification ([key]) from [packageName] was removed.
     * Used as a (weak) deletion heuristic for chat apps.
     *
     * [reason] is the framework removal reason (a `NotificationListenerService.REASON_*`
     * constant). It lets the pipeline ignore user-driven dismissals (swipe / tap /
     * "clear all"), which must never be mistaken for a message deletion. [REASON_UNKNOWN]
     * (0) is used when the platform did not supply one (pre-26 path / synthetic events).
     */
    data class NotificationRemoved(
        val packageName: String,
        val key: String,
        val reason: Int = REASON_UNKNOWN
    ) : RawEvent

    companion object {
        /** Sentinel for "no removal reason supplied". */
        const val REASON_UNKNOWN: Int = 0
    }

    /**
     * The SMS/MMS provider signalled a change at [uri]. Triggers a diff of the
     * provider against the local cache to detect new and deleted messages.
     */
    data class SmsChanged(
        val uri: String
    ) : RawEvent

    /**
     * Content captured via the opt-in AccessibilityService for [packageName],
     * filtered to the target packages. [text] is the visible message body.
     */
    data class AccessibilityCaptured(
        val packageName: String,
        val title: String?,
        val text: String,
        val postedAt: Long
    ) : RawEvent
}
