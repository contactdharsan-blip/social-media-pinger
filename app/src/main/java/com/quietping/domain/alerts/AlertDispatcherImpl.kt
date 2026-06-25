package com.quietping.domain.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quietping.domain.model.AlertStyle
import com.quietping.domain.model.MatchLog
import com.quietping.domain.model.MatchResult
import com.quietping.domain.model.Rule
import com.quietping.domain.repo.MatchRepository
import com.quietping.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

/**
 * Default [AlertDispatcher].
 *
 * On a [MatchResult] it (1) ensures the channel for the rule's
 * [com.quietping.domain.model.TriggerType] + sound preset + DND setting exists,
 * (2) posts an IMPORTANCE_HIGH heads-up re-notification on that channel, and
 * (3) records a [MatchLog] via [MatchRepository] for Home's match feed.
 *
 * The notification deep-links back into the app: tapping it launches the single
 * Activity (resolved dynamically from the package's launch intent — no hard-coded
 * class), so this stays decoupled from the UI module and survives icon-alias
 * switches.
 *
 * Logging is suspend but [fire] is not, so the audit write is dispatched on a
 * private IO scope; the binder/callback thread that produced the match is never
 * blocked. Posting requires POST_NOTIFICATIONS on API 33+; if it is missing we
 * skip the post (and never crash) — the feature degrades, matching the PRD's
 * permission-optional posture.
 */
class AlertDispatcherImpl(
    private val context: Context,
    private val matchRepository: MatchRepository,
    private val settingsRepository: SettingsRepository,
    private val channels: NotificationChannels = NotificationChannels(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val repeatTracker: RepeatSenderTracker = RepeatSenderTracker()
) : AlertDispatcher {

    private val notifier = NotificationManagerCompat.from(context)

    /** Active re-ping loops, keyed by conversation id; cancelled on read. */
    private val reminderJobs = ConcurrentHashMap<Long, Job>()

    /**
     * Latest value of the "hide notification content" privacy setting, mirrored from
     * DataStore so the hot [fire] path reads a plain field instead of suspending.
     */
    @Volatile
    private var hideContent: Boolean = false

    init {
        scope.launch {
            settingsRepository.privacy.collect { hideContent = it.hideNotificationContent }
        }
    }

    override fun ensureChannels() {
        channels.ensureDefaults()
    }

    override fun fire(match: MatchResult) {
        val rule = match.rule
        val conversationId = match.message.conversationId

        // Repeated-sender break-through: a second message from the same sender within
        // the burst window escalates to CRITICAL regardless of the rule's own style.
        val burst = repeatTracker.shouldBreakThrough(conversationId, match.message.sender)
        val style = if (burst) AlertStyle.CRITICAL else rule.alertStyle
        val critical = style == AlertStyle.CRITICAL

        val channelId = channels.ensureChannel(
            type = rule.type,
            preset = rule.soundPreset,
            bypassDnd = rule.dndOverride,
            critical = critical
        )

        val notificationId = notificationIdFor(match)
        if (canPost()) {
            val notification = buildNotification(match, channelId, notificationId, critical)
            // Guarded by canPost(); the lint suppression documents that contract.
            @Suppress("MissingPermission")
            notifier.notify(notificationId, notification)

            // PERSISTENT (and not already a one-shot critical): re-ping until the
            // source notification is removed (read) or the repeat cap is reached.
            if (style == AlertStyle.PERSISTENT) {
                startReminder(conversationId, notificationId, notification)
            }
        }

        // Always log the match (the feed should reflect a fire even if the OS
        // suppressed the visible post, e.g. notifications disabled).
        scope.launch {
            matchRepository.log(
                MatchLog(
                    id = 0L,
                    messageId = match.message.id,
                    ruleId = rule.id,
                    firedAt = System.currentTimeMillis(),
                    channelId = channelId
                )
            )
        }
    }

    override fun cancelReminders(conversationId: Long) {
        reminderJobs.remove(conversationId)?.cancel()
    }

    /**
     * Re-post [notification] on an interval until [MAX_REMINDERS] is reached or the
     * loop is cancelled by [cancelReminders] (the read signal). Keyed by
     * [conversationId]; a fresh persistent fire for the same conversation restarts it.
     */
    private fun startReminder(conversationId: Long, notificationId: Int, notification: Notification) {
        reminderJobs.remove(conversationId)?.cancel()
        reminderJobs[conversationId] = scope.launch {
            var count = 0
            while (count < MAX_REMINDERS) {
                delay(REMINDER_INTERVAL_MS)
                if (!canPost()) break
                @Suppress("MissingPermission")
                notifier.notify(notificationId, notification)
                count++
            }
            reminderJobs.remove(conversationId)
        }
    }

    private fun buildNotification(
        match: MatchResult,
        channelId: String,
        notificationId: Int,
        critical: Boolean
    ): Notification {
        val rule = match.rule
        val message = match.message
        // Content-hidden mode (privacy): show a generic title/body and keep the real
        // text off the lock screen entirely (VISIBILITY_SECRET).
        val title = if (hideContent) GENERIC_TITLE else alertTitle(rule)
        val body = if (hideContent) GENERIC_BODY else previewBody(message.currentBody, message.sender)

        val intent = launchIntent(message.conversationId, notificationId)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setVisibility(
                if (hideContent) NotificationCompat.VISIBILITY_SECRET
                else NotificationCompat.VISIBILITY_PRIVATE
            )

        if (!hideContent) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        intent?.let { builder.setContentIntent(it) }
        // CRITICAL: launch a full-screen heads-up that wakes the screen even from
        // lock / DND (BuzzKill "Alarm" pattern). Needs USE_FULL_SCREEN_INTENT.
        if (critical && intent != null) {
            builder.setFullScreenIntent(intent, true)
        }
        return builder.build()
    }

    /**
     * A PendingIntent that re-opens QuietPing's launcher Activity deep-linked to the
     * thread the alert fired from, or null.
     *
     * The [conversationId] rides as an extra; [com.quietping.MainActivity] reads it and
     * drives the nav graph to the matching [com.quietping.ui.nav.Dest.VaultThread].
     *
     * [requestCode] is the per-match notification id: PendingIntent equality ignores
     * extras, so distinct alerts MUST use distinct request codes — otherwise
     * FLAG_UPDATE_CURRENT would collapse every live alert onto one intent and they'd
     * all open the most recently fired thread.
     */
    private fun launchIntent(conversationId: Long, requestCode: Int): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CONVERSATION_ID, conversationId)
            }
            ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, launch, flags)
    }

    private fun alertTitle(rule: Rule): String = when (rule.type) {
        com.quietping.domain.model.TriggerType.NAME_MENTION -> "You were mentioned"
        com.quietping.domain.model.TriggerType.POLL_CREATED -> "New poll"
        com.quietping.domain.model.TriggerType.KEYWORD -> "Keyword match"
        com.quietping.domain.model.TriggerType.REPLY_MENTION -> "New reply"
        com.quietping.domain.model.TriggerType.VIP_CONTACT -> "VIP message"
        com.quietping.domain.model.TriggerType.GROUP_EVENT -> "Group activity"
    }

    private fun previewBody(body: String, sender: String): String {
        val clipped = if (body.length > MAX_PREVIEW) body.take(MAX_PREVIEW).trimEnd() + "…" else body
        return if (sender.isNotBlank()) "$sender: $clipped" else clipped
    }

    /**
     * Use the app's launcher icon as the status-bar small icon. Resolved from the
     * manifest application icon to avoid depending on a specific drawable owned by
     * another module.
     */
    private fun smallIcon(): Int {
        val appInfo = context.applicationInfo
        val icon = appInfo.icon
        return if (icon != 0) icon else android.R.drawable.ic_dialog_email
    }

    private fun canPost(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-13: gated by the user's notification toggle, which the OS enforces.
            notifier.areNotificationsEnabled()
        }

    /**
     * Stable-ish notification id per logical match so distinct alerts coexist while
     * a re-fire of the same message replaces its prior heads-up. Derived from the
     * message id and rule id; kept positive and non-zero.
     */
    private fun notificationIdFor(match: MatchResult): Int {
        val raw = (match.message.id * 31 + match.rule.id)
        val id = (raw % Int.MAX_VALUE).toInt().absoluteValue
        return if (id == 0) 1 else id
    }

    companion object {
        const val MAX_PREVIEW = 240

        /** Intent extra carrying the conversation id an alert deep-links to. */
        const val EXTRA_CONVERSATION_ID = "com.quietping.extra.CONVERSATION_ID"

        /** Re-ping cadence and cap for a PERSISTENT rule. */
        const val REMINDER_INTERVAL_MS = 60_000L
        const val MAX_REMINDERS = 5

        /** Generic strings used when content-hidden privacy mode is on. */
        const val GENERIC_TITLE = "QuietPing"
        const val GENERIC_BODY = "New alert — open to view"
    }
}
