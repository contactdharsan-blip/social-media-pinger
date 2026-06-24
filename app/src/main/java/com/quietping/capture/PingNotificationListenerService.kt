package com.quietping.capture

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        registerSmsObserver()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
        unregisterSmsObserver()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        unregisterSmsObserver()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val event = sbn?.let { buildPostedEvent(it) } ?: return
        // Hand off immediately; never block the binder thread.
        pipeline.offer(event)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        pipeline.offer(
            RawEvent.NotificationRemoved(
                packageName = notification.packageName,
                key = notification.key ?: keyFallback(notification),
            ),
        )
    }

    private fun registerSmsObserver() {
        if (smsObserver != null) return
        smsObserver = SmsObserver.register(applicationContext, pipeline)
    }

    private fun unregisterSmsObserver() {
        smsObserver?.unregister(applicationContext)
        smsObserver = null
    }

    private fun buildPostedEvent(sbn: StatusBarNotification): RawEvent.NotificationPosted {
        val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY
        return RawEvent.NotificationPosted(
            packageName = sbn.packageName,
            key = sbn.key ?: keyFallback(sbn),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            messages = extractMessagingStyle(extras),
            postedAt = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis(),
        )
    }

    /**
     * Flatten a MessagingStyle notification's message list into ordered
     * (sender, body) pairs. Falls back to an empty list when the notification is not
     * MessagingStyle (parsers then use the title/text/bigText extras).
     */
    private fun extractMessagingStyle(extras: Bundle): List<Pair<String, String>> {
        val raw = extras.getParcelableArrayCompat(Notification.EXTRA_MESSAGES)
            ?: return emptyList()

        val out = ArrayList<Pair<String, String>>(raw.size)
        for (item in raw) {
            val bundle = item as? Bundle ?: continue
            val body = bundle.getCharSequence(KEY_MESSAGING_TEXT)?.toString().orEmpty()
            if (body.isBlank()) continue
            val sender = bundle.getCharSequence(KEY_MESSAGING_SENDER)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: ""
            out += sender to body
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
