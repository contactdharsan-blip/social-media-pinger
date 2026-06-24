package com.quietping.capture

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import com.quietping.domain.model.AppPackage
import com.quietping.domain.model.RawEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for the capture services. These services are instantiated by
 * the Android framework (notification listener / accessibility binders), so they
 * pull their singleton dependencies out of the application graph via
 * [EntryPointAccessors] rather than constructor/field injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CaptureEntryPoint {
    fun capturePipeline(): CapturePipeline
}

/**
 * Resolve the shared [CapturePipeline] from any [Context].
 */
internal fun Context.capturePipeline(): CapturePipeline =
    EntryPointAccessors.fromApplication(applicationContext, CaptureEntryPoint::class.java)
        .capturePipeline()

/**
 * Primary capture source (PRD §4). Reads [StatusBarNotification] extras for every
 * supported chat app and normalizes them into a [RawEvent.NotificationPosted].
 *
 * The framework callbacks ([onNotificationPosted] / [onNotificationRemoved]) run on
 * the main/binder thread and MUST return quickly — they only extract the extras and
 * hand off to [CapturePipeline.offer], which is non-blocking. No parsing, DB work,
 * or rule evaluation happens on the callback thread.
 *
 * While connected, this service also drives the SMS [SmsObserver] (registered in
 * [onListenerConnected], unregistered in [onListenerDisconnected]) so SMS capture
 * shares the listener's already-running process without a separate foreground svc.
 */
class PingNotificationListenerService : NotificationListenerService() {

    private val pipeline: CapturePipeline by lazy { applicationContext.capturePipeline() }

    private var smsObserver: SmsObserver? = null
    private var mediaObserver: MediaObserver? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        snapshotActiveNotifications()
        registerSmsObserver()
        registerMediaObserver()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
        unregisterSmsObserver()
        unregisterMediaObserver()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        unregisterSmsObserver()
        unregisterMediaObserver()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val event = sbn?.let { buildPostedEvent(it) } ?: return
        // Hand off immediately; never block the binder thread.
        pipeline.offer(event)
    }

    /**
     * 3-arg removal callback (API 26+, our minSdk). [reason] is forwarded so the
     * pipeline can ignore user-driven dismissals (swipe / tap / "clear all") and
     * only treat app-driven removals as a possible deletion signal.
     */
    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        val notification = sbn ?: return
        pipeline.offer(
            RawEvent.NotificationRemoved(
                packageName = notification.packageName,
                key = notification.key ?: keyFallback(notification),
                reason = reason,
            ),
        )
    }

    /**
     * On (re)connect — notably after a reboot — the chat apps' notifications are
     * already on the shade and will never re-fire [onNotificationPosted]. Replay
     * the currently-active set through the same path so those messages are still
     * captured. Re-ingesting an already-stored message is harmless: the VaultManager
     * dedupes by content key.
     */
    private fun snapshotActiveNotifications() {
        val active: Array<out StatusBarNotification> = try {
            activeNotifications ?: return
        } catch (e: SecurityException) {
            Log.d(TAG, "activeNotifications unavailable (not yet bound)")
            return
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read active notifications", e)
            return
        }
        for (sbn in active) {
            if (AppPackage.fromPackageId(sbn.packageName) == null) continue
            pipeline.offer(buildPostedEvent(sbn))
        }
    }

    private fun registerSmsObserver() {
        if (smsObserver != null) return
        smsObserver = SmsObserver.register(applicationContext, pipeline)
    }

    private fun unregisterSmsObserver() {
        smsObserver?.unregister(applicationContext)
        smsObserver = null
    }

    private fun registerMediaObserver() {
        if (mediaObserver != null) return
        mediaObserver = MediaObserver.register(applicationContext)
    }

    private fun unregisterMediaObserver() {
        mediaObserver?.unregister(applicationContext)
        mediaObserver = null
    }

    private fun buildPostedEvent(sbn: StatusBarNotification): RawEvent.NotificationPosted {
        val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY
        val postedAt = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()
        val style = extractMessagingStyle(extras)
        return RawEvent.NotificationPosted(
            packageName = sbn.packageName,
            key = sbn.key ?: keyFallback(sbn),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            messages = style.map { it.first to it.second },
            postedAt = postedAt,
            messageTimes = style.map { it.third },
        )
    }

    /**
     * Flatten a MessagingStyle notification's message list into ordered
     * (sender, body, time) triples. `time` is each entry's own timestamp (0L when
     * the bundle omits it). Falls back to an empty list when the notification is not
     * MessagingStyle (parsers then use the title/text/bigText extras).
     */
    private fun extractMessagingStyle(extras: Bundle): List<Triple<String, String, Long>> {
        val raw = extras.getParcelableArrayCompat(Notification.EXTRA_MESSAGES)
            ?: return emptyList()

        val out = ArrayList<Triple<String, String, Long>>(raw.size)
        for (item in raw) {
            val bundle = item as? Bundle ?: continue
            val body = bundle.getCharSequence(KEY_MESSAGING_TEXT)?.toString().orEmpty()
            if (body.isBlank()) continue
            val sender = bundle.getCharSequence(KEY_MESSAGING_SENDER)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: ""
            val time = bundle.getLong(KEY_MESSAGING_TIME, 0L)
            out += Triple(sender, body, time)
        }
        return out
    }

    private fun keyFallback(sbn: StatusBarNotification): String =
        "${sbn.packageName}:${sbn.id}:${sbn.tag ?: ""}"

    private companion object {
        const val TAG = "PingNotifListener"

        // Keys inside each EXTRA_MESSAGES bundle (NotificationCompat.MessagingStyle).
        const val KEY_MESSAGING_TEXT = "text"
        const val KEY_MESSAGING_SENDER = "sender"
        const val KEY_MESSAGING_TIME = "time"
    }
}

/**
 * API-safe retrieval of [Notification.EXTRA_MESSAGES] as an array of parcelables.
 */
private fun Bundle.getParcelableArrayCompat(key: String): Array<out android.os.Parcelable>? =
    @Suppress("DEPRECATION")
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, android.os.Parcelable::class.java)
    } else {
        getParcelableArray(key)
    }
