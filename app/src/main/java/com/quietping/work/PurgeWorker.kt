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
 * Periodic retention purge.
 *
 * Deletes captured messages older than the user's configured retention window
 * (PRD §6C / §11). The Vault stores third-party message content, so bounded
 * retention with auto-purge is a privacy guarantee, not a nicety.
 *
 * This worker is intentionally **not** Hilt-injected (no `@HiltWorker` / custom
 * `WorkerFactory` wiring lives here, to respect module ownership). Instead it pulls
 * its two repository dependencies through a Hilt [EntryPoint] at run time via
 * [EntryPointAccessors.fromApplication] — the standard way to use Hilt graph
 * objects from a component the framework instantiates. Scheduling (constraints,
 * cadence, unique work) is configured by the Integration layer.
 *
 * Idempotent and side-effect-light: it reads the current [SettingsRepository.privacy]
 * retention, computes a cutoff, and calls [MessageRepository.purgeOlderThan]. Any
 * failure returns [Result.retry] so WorkManager can back off and try again.
 */
class PurgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    /**
     * Hilt accessor for the singleton repositories. Defined here (an `@EntryPoint`
     * interface is not a module) so the worker can resolve dependencies without a
     * custom WorkerFactory.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PurgeWorkerEntryPoint {
        fun messageRepository(): MessageRepository
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                PurgeWorkerEntryPoint::class.java
            )
            val retentionDays = entryPoint.settingsRepository().privacy.first().retentionDays
            // Guard against a zero/negative window disabling retention by accident;
            // fall back to the model default rather than purging everything.
            val effectiveDays = if (retentionDays > 0) retentionDays else DEFAULT_RETENTION_DAYS
            val cutoff = System.currentTimeMillis() - effectiveDays.toLong() * MILLIS_PER_DAY
            entryPoint.messageRepository().purgeOlderThan(cutoff)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        /** Stable unique-work name for the Integration layer to schedule against. */
        const val WORK_NAME = "quietping_retention_purge"

        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val DEFAULT_RETENTION_DAYS = 30
    }
}
