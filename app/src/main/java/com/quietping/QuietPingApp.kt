package com.quietping

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * QuietPing application entry point.
 *
 * [HiltAndroidApp] generates the application-level Hilt component and triggers
 * member injection across the app (capture services, WorkManager workers,
 * Activities). All dependency graph configuration lives in the Hilt modules
 * (provided by the DI/integration layer); this class intentionally holds no
 * logic so the cold-start path stays minimal.
 */
@HiltAndroidApp
class QuietPingApp : Application()
