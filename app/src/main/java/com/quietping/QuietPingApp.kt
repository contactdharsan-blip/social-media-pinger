package com.quietping

import android.app.Application
import com.quietping.di.AppInitializerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp

/**
 * QuietPing application entry point.
 *
 * [HiltAndroidApp] generates the application-level Hilt component and triggers
 * member injection across the app (capture services, WorkManager workers,
 * Activities). All dependency graph configuration lives in the Hilt modules
 * (com.quietping.di); this class holds no business logic.
 *
 * The only startup action is to resolve [com.quietping.di.AppInitializer] from the
 * graph and run its one-time wiring (notification channels + periodic retention
 * purge). It is read through a Hilt [dagger.hilt.EntryPoint] rather than field
 * injection so the dependency is explicit and the cold-start path stays minimal.
 */
@HiltAndroidApp
class QuietPingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EntryPointAccessors
            .fromApplication(this, AppInitializerEntryPoint::class.java)
            .appInitializer()
            .initialize()
    }
}
