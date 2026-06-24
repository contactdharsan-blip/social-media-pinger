package com.quietping.capture

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Re-establishes capture after a device reboot (PRD §6F).
 *
 * The OS automatically rebinds an enabled [PingNotificationListenerService] after
 * BOOT_COMPLETED, so there is no service to manually start here. The receiver:
 *  - logs the boot so listener-rebind issues are diagnosable, and
 *  - touches the [CapturePipeline] entry point so the application graph (and the
 *    singleton pipeline consumer) is warm before the first post-boot event, and
 *  - notes whether READ_SMS is still granted (the listener will re-register the SMS
 *    observer on its next [PingNotificationListenerService.onListenerConnected]).
 *
 * It intentionally does no heavy work: a [BroadcastReceiver.onReceive] runs on the
 * main thread and must return fast.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "BOOT_COMPLETED received; capture services are rebindable by the OS")

        // Warm the application graph so the singleton CapturePipeline consumer is
        // running by the time the rebound listener delivers its first notification.
        runCatching { ctx.applicationContext.capturePipeline() }
            .onFailure { Log.w(TAG, "Could not warm CapturePipeline on boot", it) }

        val smsGranted = ctx.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Post-boot READ_SMS granted=$smsGranted; SMS observer re-registers on listener connect")
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
