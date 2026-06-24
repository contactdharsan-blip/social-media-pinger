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
import com.quietping.domain.model.MatchLog
import com.quietping.domain.model.MatchResult
import com.quietping.domain.model.Rule
import com.quietping.domain.repo.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val channels: NotificationChannels = NotificationChannels(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AlertDispatcher {

    private val notifier = NotificationManagerCompat.from(context)

    override fun ensureChannels() {
        channels.ensureDefaults()
    }

    override fun fire(match: MatchResult) {
        val rule = match.rule
        val channelId = channels.ensureChannel(
            type = rule.type,
            preset = rule.soundPreset,
            bypassDnd = rule.dndOverride
        )

        val notificationId = notificationIdFor(match)
        if (canPost()) {
            val notification = buildNotification(match, channelId)
            // Guarded by canPost(); the lint suppression documents that contract.
            @Suppress("MissingPermission")
            notifier.notify(notificationId, notification)
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

    private fun buildNotification(match: MatchResult, channelId: String): Notification {
        val rule = match.rule
        val message = match.message
        val title = alertTitle(rule)
        val body = previewBody(message.currentBody, message.sender)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)

        launchIntent()?.let { builder.setContentIntent(it) }
        return builder.build()
    }

    /** A PendingIntent that re-opens QuietPing's launcher Activity, or null. */
    private fun launchIntent(): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, /* requestCode = */ 0, launch, flags)
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

    private companion object {
        const val MAX_PREVIEW = 240
    }
}
