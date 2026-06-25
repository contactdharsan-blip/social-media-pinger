package com.quietping.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quietping.domain.alerts.NotificationChannels
import com.quietping.domain.repo.MatchRepository
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Once-daily digest of low-priority matches (Daywise / iOS Scheduled Summary parity).
 *
 * When [com.quietping.domain.settings.AlertPrefs.digestEnabled] is on, this posts a
 * single quiet summary on the IMPORTANCE_LOW digest channel: "N alerts in the last
 * day — tap to review". It is intentionally content-free (no message bodies on the
 * lock screen) and deep-links to the app's home feed for detail.
 *
 * Like [PurgeWorker], dependencies are pulled via a Hilt [EntryPoint] rather than a
 * custom WorkerFactory. Failures return [Result.retry].
 */
class DigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DigestWorkerEntryPoint {
        fun matchRepository(): MatchRepository
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                DigestWorkerEntryPoint::class.java
            )
            val prefs = entryPoint.settingsRepository().alerts.first()
            if (!prefs.digestEnabled) return Result.success()

            val cutoff = System.currentTimeMillis() - MILLIS_PER_DAY
            val recent = entryPoint.matchRepository().recent(RECENT_LIMIT).first()
            val count = recent.count { it.firedAt >= cutoff }
            if (count == 0) return Result.success()

            postDigest(count)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    private fun postDigest(count: Int) {
        val channelId = NotificationChannels(applicationContext).ensureDigestChannel()
        val launch = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pending = launch?.let {
            PendingIntent.getActivity(
                applicationContext,
                DIGEST_REQUEST_CODE,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val icon = applicationContext.applicationInfo.icon
            .takeIf { it != 0 } ?: android.R.drawable.ic_dialog_email
        val title = "Daily digest"
        val body = if (count == 1) "1 alert in the last day" else "$count alerts in the last day"

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        pending?.let { builder.setContentIntent(it) }

        val notifier = NotificationManagerCompat.from(applicationContext)
        if (notifier.areNotificationsEnabled()) {
            @Suppress("MissingPermission")
            notifier.notify(DIGEST_NOTIFICATION_ID, builder.build())
        }
    }

    companion object {
        const val WORK_NAME = "quietping_daily_digest"

        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val RECENT_LIMIT = 500
        private const val DIGEST_NOTIFICATION_ID = 424242
        private const val DIGEST_REQUEST_CODE = 4242
    }
}
