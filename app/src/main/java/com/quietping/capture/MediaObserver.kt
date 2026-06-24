package com.quietping.capture

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Watches MediaStore for new images / video / audio that messaging apps write to
 * shared storage, and copies anything from a supported chat app into the private
 * [MediaVault] immediately — *before* a later "delete for everyone" can remove the
 * original from the device gallery (research: the WhatsDeleted "media observer"
 * technique).
 *
 * Registered alongside the notification listener (it shares that already-running
 * process; no extra foreground service). Like [SmsObserver], the callback returns
 * fast and does the provider read + file copy on a background coroutine.
 *
 * Scope is deliberately narrow: only rows whose storage path belongs to a known
 * messaging app are touched, so unrelated camera/screenshot media is never copied.
 * Reads degrade silently when the `READ_MEDIA_*` / `READ_EXTERNAL_STORAGE`
 * permission is not granted (PRD §7 skippable perms).
 *
 * Construct via [register]; always pair with [unregister].
 */
class MediaObserver private constructor(
    private val appContext: Context,
    handler: Handler,
    private val workerThread: HandlerThread,
) : ContentObserver(handler) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Highest MediaStore row id seen per collection; only newer rows are captured. */
    private val lastIdByCollection = HashMap<String, Long>()

    override fun deliverSelfNotifications(): Boolean = false

    override fun onChange(selfChange: Boolean) = onChange(selfChange, null)

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        scope.launch {
            runCatching { scanCollections() }
                .onFailure { Log.w(TAG, "Media change handling failed", it) }
        }
    }

    /** Query each watched collection for new messaging-app rows and capture them. */
    private fun scanCollections() {
        for (collection in COLLECTIONS) {
            captureNewIn(collection)
        }
    }

    private fun captureNewIn(collection: Collection) {
        val resolver: ContentResolver = appContext.contentResolver
        val lastId = lastIdByCollection[collection.label] ?: -1L
        var maxId = lastId

        val cursor: Cursor? = try {
            resolver.query(
                collection.uri,
                PROJECTION,
                if (lastId >= 0) "${MediaStore.MediaColumns._ID} > ?" else null,
                if (lastId >= 0) arrayOf(lastId.toString()) else null,
                "${MediaStore.MediaColumns._ID} ASC",
            )
        } catch (se: SecurityException) {
            Log.d(TAG, "Media read permission not granted; skipping ${collection.label}")
            return
        } catch (e: Exception) {
            Log.w(TAG, "Media query failed for ${collection.label}", e)
            return
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathCol = pathColumnIndex(c)
            if (idCol < 0) return

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                if (id > maxId) maxId = id

                val path = if (pathCol >= 0) c.getString(pathCol).orEmpty() else ""
                val app = messagingAppFor(path) ?: continue // not a chat-app file

                val name = if (nameCol >= 0) c.getString(nameCol) else null
                val itemUri = ContentUris.withAppendedId(collection.uri, id)
                MediaVault.capture(appContext, itemUri, app, id, name)
            }
        }

        lastIdByCollection[collection.label] = maxId
    }

    /**
     * Seed the per-collection baselines with the current max id so pre-existing
     * media isn't bulk-copied on first connect — only media arriving from now on is
     * captured. Mirrors [SmsObserver.primeBaseline].
     */
    private fun primeBaseline() {
        for (collection in COLLECTIONS) {
            val maxId = try {
                appContext.contentResolver.query(
                    collection.uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    null, null,
                    "${MediaStore.MediaColumns._ID} DESC",
                )?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idCol >= 0 && c.moveToFirst()) c.getLong(idCol) else -1L
                } ?: -1L
            } catch (e: Exception) {
                -1L
            }
            lastIdByCollection[collection.label] = maxId
        }
    }

    /** RELATIVE_PATH on API 29+, the deprecated DATA column on older devices. */
    private fun pathColumnIndex(c: Cursor): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            c.getColumnIndex(MediaStore.MediaColumns.DATA)
        }

    /** Match a storage path to a supported chat app by folder marker, else null. */
    private fun messagingAppFor(path: String): String? {
        if (path.isBlank()) return null
        val lower = path.lowercase()
        return APP_MARKERS.firstNotNullOfOrNull { (marker, label) ->
            if (lower.contains(marker)) label else null
        }
    }

    fun unregister(context: Context) {
        runCatching { context.contentResolver.unregisterContentObserver(this) }
        workerThread.quitSafely()
    }

    private data class Collection(val label: String, val uri: Uri)

    companion object {
        private const val TAG = "MediaObserver"

        private val COLLECTIONS: List<Collection> = listOf(
            Collection("images", MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
            Collection("video", MediaStore.Video.Media.EXTERNAL_CONTENT_URI),
            Collection("audio", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
        )

        private val PROJECTION: Array<String> = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()

        /** Lower-case storage-path markers → the app label stored on the file. */
        private val APP_MARKERS: List<Pair<String, String>> = listOf(
            "whatsapp" to "whatsapp",
            "instagram" to "instagram",
            "messenger" to "messenger",
            "/facebook" to "facebook",
        )

        /**
         * Create, register on the three media collections, and seed baselines so
         * only newly-arriving media is captured. Returns the live observer
         * (call [unregister] to tear down).
         */
        fun register(context: Context): MediaObserver {
            val thread = HandlerThread("quietping-media-observer").apply { start() }
            val handler = Handler(thread.looper)
            val observer = MediaObserver(context.applicationContext, handler, thread)

            val resolver = context.contentResolver
            runCatching {
                for (collection in COLLECTIONS) {
                    resolver.registerContentObserver(collection.uri, true, observer)
                }
            }.onFailure { Log.w(TAG, "Failed to register media observer", it) }

            observer.scope.launch {
                runCatching { observer.primeBaseline() }
                    .onFailure { Log.w(TAG, "Media baseline prime failed", it) }
            }
            return observer
        }
    }
}
