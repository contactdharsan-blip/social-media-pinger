package com.quietping.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.quietping.domain.alerts.AlertDispatcher
import com.quietping.work.PurgeWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time, idempotent application startup wiring.
 *
 * [com.quietping.QuietPingApp] holds no logic of its own; it resolves this
 * initializer from the Hilt graph (via [AppInitializerEntryPoint]) and calls
 * [initialize] once in `onCreate`. Doing the work here — rather than with field
 * injection on the Application — keeps the cold-start path explicit and unit-wirable.
 *
 * Startup responsibilities:
 *  - [AlertDispatcher.ensureChannels]: pre-create the per-condition notification
 *    channels so the first match can fire a heads-up immediately (PRD §8). Safe to
 *    repeat — re-creating an existing channel never clobbers the user's edits.
 *  - schedule the periodic [PurgeWorker]: bounded retention with auto-purge is a
 *    privacy guarantee, not a nicety (PRD §6C / §11). Enqueued as unique work with
 *    [ExistingPeriodicWorkPolicy.KEEP] so re-launches don't reset its schedule.
 */
@Singleton
class AppInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertDispatcher: AlertDispatcher
) {

    fun initialize() {
        alertDispatcher.ensureChannels()
        schedulePurge()
    }

    private fun schedulePurge() {
        val constraints = Constraints.Builder()
            // The purge is housekeeping, not urgent — defer it off the critical path.
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<PurgeWorker>(
            PURGE_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PurgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        /** Run the retention purge roughly once a day. */
        const val PURGE_INTERVAL_HOURS = 24L
    }
}

/**
 * Hilt entry point so the framework-instantiated [com.quietping.QuietPingApp] can
 * pull the singleton [AppInitializer] out of the application graph.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppInitializerEntryPoint {
    fun appInitializer(): AppInitializer
}
