package com.quietping.domain.model

/**
 * The chat / messaging apps QuietPing observes. Each entry carries the real
 * Android package id so capture services and parsers can route by source.
 */
enum class AppPackage(val packageId: String) {
    WHATSAPP("com.whatsapp"),
    INSTAGRAM("com.instagram.android"),
    MESSENGER("com.facebook.orca"),
    FACEBOOK("com.facebook.katana"),
    SMS("com.android.messaging");

    companion object {
        /** Resolve an [AppPackage] from a raw Android package id, or null if unknown. */
        fun fromPackageId(packageId: String): AppPackage? =
            entries.firstOrNull { it.packageId == packageId }
    }
}

/**
 * The kinds of conditions a [Rule] can match against an incoming message.
 */
enum class TriggerType {
    NAME_MENTION,
    POLL_CREATED,
    KEYWORD,
    REPLY_MENTION,
    VIP_CONTACT,
    GROUP_EVENT
}

/**
 * Lifecycle state of a captured message as observed through the notification /
 * provider stream. Drives Vault presentation (edited history, recovered/deleted).
 */
enum class MessageStatus {
    ACTIVE,
    EDITED,
    DELETED
}

/**
 * Which on-device channel produced a captured message.
 */
enum class CaptureSource {
    NOTIFICATION,
    SMS,
    ACCESSIBILITY
}

/**
 * Bundled short alert tones. [rawName] is the resource name in res/raw (without
 * extension); [displayName] is shown in the UI. SILENT means vibrate-only.
 */
enum class SoundPreset(val rawName: String, val displayName: String) {
    DROPLET("preset_droplet", "Droplet"),
    GLASS_TAP("preset_glass_tap", "Glass Tap"),
    SONAR("preset_sonar", "Sonar"),
    CHIME("preset_chime", "Chime"),
    PULSE("preset_pulse", "Pulse"),
    WHISPER("preset_whisper", "Whisper"),
    SILENT("preset_silent", "Silent+")
}
