package com.quietping.domain.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType

/**
 * Owns QuietPing's notification channels — one per [TriggerType] × [SoundPreset]
 * pairing that is actually used by a rule — and the per-preset sound + vibration
 * design.
 *
 * Why one channel per (type, preset): Android binds sound and vibration to the
 * *channel*, immutably after creation. To let the user assign any preset to any
 * condition (PRD §8) we materialize a distinct channel per combination on demand
 * and reuse it thereafter. All alert channels are [NotificationManager.IMPORTANCE_HIGH]
 * so matches surface as heads-up notifications. DND bypass is enabled per channel
 * when the rule allows it and the app holds ACCESS_NOTIFICATION_POLICY.
 *
 * The user's own edits in system channel settings are respected: once a channel
 * exists Android ignores re-creation with changed importance/sound, so we never
 * silently override their choices (PRD §8).
 *
 * minSdk is 26, so NotificationChannel is always available — no version guards.
 */
class NotificationChannels(
    private val context: Context
) {

    private val manager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /**
     * Ensure the channel for ([type], [preset], [bypassDnd]) exists and return its
     * stable channel id. Idempotent: re-creating an existing channel is a no-op for
     * its mutable settings, so user customizations persist.
     */
    fun ensureChannel(
        type: TriggerType,
        preset: SoundPreset,
        bypassDnd: Boolean,
        critical: Boolean = false
    ): String {
        // A CRITICAL alert always pierces DND (it is the whole point), so a critical
        // channel implies bypass regardless of the rule's own dndOverride.
        val effectiveBypass = bypassDnd || critical
        val id = channelId(type, preset, effectiveBypass, critical)
        // Already created? Reuse — don't clobber user edits.
        if (manager.getNotificationChannel(id) != null) return id

        val channel = NotificationChannel(id, channelName(type, preset), IMPORTANCE).apply {
            description = channelDescription(type)
            enableLights(true)
            configureSound(this, preset, critical)
            configureVibration(this, preset)
            if (effectiveBypass) {
                // Effective only if the app has ACCESS_NOTIFICATION_POLICY; harmless
                // otherwise (the system ignores it).
                setBypassDnd(true)
            }
        }
        manager.createNotificationChannel(channel)
        return id
    }

    /**
     * Pre-create the default channel for every [TriggerType] using each type's
     * default preset (no DND bypass). Called from [AlertDispatcher.ensureChannels]
     * on every start so the channels exist before the first match.
     */
    fun ensureDefaults() {
        for (type in TriggerType.entries) {
            ensureChannel(type, defaultPresetFor(type), bypassDnd = false)
        }
    }

    /**
     * Ensure the low-importance digest channel exists and return its id. Digests are
     * a quiet once-daily summary (Daywise / iOS Scheduled Summary parity), so this
     * channel is IMPORTANCE_LOW — no heads-up, no sound.
     */
    fun ensureDigestChannel(): String {
        if (manager.getNotificationChannel(DIGEST_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                DIGEST_CHANNEL_ID,
                "Daily digest",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "A once-daily summary of low-priority matches."
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
        return DIGEST_CHANNEL_ID
    }

    /** Resolve a preset's bundled tone in res/raw to a content Uri, or null (silent). */
    fun soundUriFor(preset: SoundPreset): Uri? {
        if (preset == SoundPreset.SILENT) return null
        val resId = context.resources.getIdentifier(preset.rawName, "raw", context.packageName)
        if (resId == 0) return null
        return Uri.parse("android.resource://${context.packageName}/$resId")
    }

    /** Vibration pattern (ms on/off) paired with [preset]; empty ⇒ no vibration. */
    fun vibrationPatternFor(preset: SoundPreset): LongArray = when (preset) {
        SoundPreset.DROPLET -> longArrayOf(0, 40)
        SoundPreset.GLASS_TAP -> longArrayOf(0, 25)
        SoundPreset.SONAR -> longArrayOf(0, 60, 80, 60)
        SoundPreset.CHIME -> longArrayOf(0, 30, 60, 30, 60, 30)
        SoundPreset.PULSE -> longArrayOf(0, 120, 90, 120)
        SoundPreset.WHISPER -> longArrayOf(0, 20)
        SoundPreset.SILENT -> longArrayOf(0, 150, 120, 150) // vibrate-only condition
    }

    private fun configureSound(channel: NotificationChannel, preset: SoundPreset, critical: Boolean = false) {
        val uri = soundUriFor(preset)
        if (uri == null) {
            channel.setSound(null, null) // Silent+: no tone
            return
        }
        // A CRITICAL channel routes its tone through the ALARM usage so it sounds
        // even while the phone is in Do-Not-Disturb / silent (SOS-Ring pattern).
        val usage = if (critical) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
        val attrs = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        channel.setSound(uri, attrs)
    }

    private fun configureVibration(channel: NotificationChannel, preset: SoundPreset) {
        val pattern = vibrationPatternFor(preset)
        if (pattern.isEmpty()) {
            channel.enableVibration(false)
        } else {
            channel.enableVibration(true)
            channel.vibrationPattern = pattern
        }
    }

    private fun channelName(type: TriggerType, preset: SoundPreset): String =
        "${typeLabel(type)} · ${preset.displayName}"

    private fun channelDescription(type: TriggerType): String = when (type) {
        TriggerType.NAME_MENTION -> "Alerts when your name or handle is mentioned."
        TriggerType.POLL_CREATED -> "Alerts when a poll is created in a chat."
        TriggerType.KEYWORD -> "Alerts when one of your keywords appears."
        TriggerType.REPLY_MENTION -> "Alerts when someone replies to or @-mentions you."
        TriggerType.VIP_CONTACT -> "Alerts for messages from your VIP contacts."
        TriggerType.GROUP_EVENT -> "Alerts for group events (added, admin, calls)."
    }

    private fun typeLabel(type: TriggerType): String = when (type) {
        TriggerType.NAME_MENTION -> "Name mention"
        TriggerType.POLL_CREATED -> "Poll created"
        TriggerType.KEYWORD -> "Keyword"
        TriggerType.REPLY_MENTION -> "Reply / mention"
        TriggerType.VIP_CONTACT -> "VIP contact"
        TriggerType.GROUP_EVENT -> "Group event"
    }

    companion object {
        private const val IMPORTANCE = NotificationManager.IMPORTANCE_HIGH

        /** Stable id for the low-importance daily-digest channel. */
        const val DIGEST_CHANNEL_ID = "qp_v1_digest"

        /** PRD §8 default preset per condition type. */
        fun defaultPresetFor(type: TriggerType): SoundPreset = when (type) {
            TriggerType.NAME_MENTION -> SoundPreset.PULSE
            TriggerType.POLL_CREATED -> SoundPreset.CHIME
            TriggerType.KEYWORD -> SoundPreset.DROPLET
            TriggerType.REPLY_MENTION -> SoundPreset.GLASS_TAP
            TriggerType.VIP_CONTACT -> SoundPreset.SONAR
            TriggerType.GROUP_EVENT -> SoundPreset.DROPLET
        }

        /**
         * Stable channel id for a (type, preset, dnd) combination. Versioned so a
         * future change to channel design can migrate without colliding with
         * channels the user already tuned.
         */
        fun channelId(
            type: TriggerType,
            preset: SoundPreset,
            bypassDnd: Boolean,
            critical: Boolean = false
        ): String {
            val dnd = if (bypassDnd) "dnd" else "std"
            val crit = if (critical) "_crit" else ""
            return "qp_v1_${type.name.lowercase()}_${preset.name.lowercase()}_$dnd$crit"
        }
    }
}
