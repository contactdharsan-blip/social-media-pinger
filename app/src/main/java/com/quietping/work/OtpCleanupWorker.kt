package com.quietping.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Periodic OTP auto-delete (SMS Organizer / Google Messages "delete OTP after 24h"
 * parity). When the user has set a non-zero [com.quietping.domain.settings.AlertPrefs.otpAutoDeleteHours],
 * captured SMS that read as one-time passcodes and are older than that window are
 * purged from the vault — OTPs are sensitive and short-lived, so keeping them around
 * is pure risk.
 *
 * Dependencies via Hilt [EntryPoint], like [PurgeWorker]. No-op when the feature is
 * off; [Result.retry] on failure.
 */
class OtpCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface OtpCleanupEntryPoint {
        fun messageRepository(): MessageRepository
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                OtpCleanupEntryPoint::class.java
            )
            val hours = entryPoint.settingsRepository().alerts.first().otpAutoDeleteHours
            if (hours <= 0) return Result.success()

            val cutoff = System.currentTimeMillis() - hours.toLong() * MILLIS_PER_HOUR
            entryPoint.messageRepository().purgeOtpOlderThan(cutoff)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "quietping_otp_cleanup"
        private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
    }
}
