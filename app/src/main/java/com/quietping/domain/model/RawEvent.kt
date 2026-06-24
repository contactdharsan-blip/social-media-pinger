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
        val postedAt: Long
    ) : RawEvent

    /**
     * A previously posted notification ([key]) from [packageName] was removed.
     * Used as a deletion heuristic for chat apps.
     */
    data class NotificationRemoved(
        val packageName: String,
        val key: String
    ) : RawEvent

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
