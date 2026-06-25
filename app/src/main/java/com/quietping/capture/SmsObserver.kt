package com.quietping.capture

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import android.util.Log
import com.quietping.domain.model.CaptureSource
import com.quietping.domain.model.Message
import com.quietping.domain.model.MessageStatus
import com.quietping.domain.model.RawEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Watches the local SMS/MMS store (PRD §6A). Registered while the notification
 * listener is connected so it shares that already-running process (no separate
 * foreground service required).
 *
 * On every provider change it:
 *  1. emits a [RawEvent.SmsChanged] signal onto the pipeline (for any cross-cutting
 *     listeners), and
 *  2. reads new/changed rows via [readNewSms] and pushes each as an SMS-sourced
 *     [Message] through [CapturePipeline.ingestSms], and
 *  3. diffs the current provider id set against the previously seen set to detect
 *     deletions, flagging the missing rows via [CapturePipeline.markSmsDeleted].
 *
 * Construct via [register]; always pair with [unregister].
 */
class SmsObserver private constructor(
    private val pipeline: CapturePipeline,
    handler: Handler,
    private val workerThread: HandlerThread,
) : ContentObserver(handler) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Ids of SMS rows we've already observed, used to detect deletions on diff. */
    private val seenSmsIds = LinkedHashSet<Long>()

    /** Conversation key (thread id as string) last known for each SMS row id. */
    private val rowConversationKey = HashMap<Long, String>()

    /** Cached per-thread group verdict (recipient count > 1), to avoid re-querying. */
    private val threadIsGroup = HashMap<Long, Boolean>()

    /** Highest SMS row id ingested so far; only rows above it are treated as new. */
    @Volatile
    private var lastMaxId: Long = -1L

    /** Application context captured at registration; used for provider queries. */
    @Volatile
    private var appContext: Context? = null

    override fun deliverSelfNotifications(): Boolean = false

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        val changedUri = uri?.toString() ?: SMS_URI.toString()
        scope.launch {
            runCatching {
                pipeline.submit(RawEvent.SmsChanged(changedUri))
                processChange(applicationContext = appContext ?: return@runCatching)
            }.onFailure { Log.w(TAG, "SMS change handling failed", it) }
        }
    }

    private suspend fun processChange(applicationContext: Context) {
        val newMessages = readNewSms(applicationContext)
        for (message in newMessages) {
            // The SMS thread id rides raw in conversationId until the pipeline resolves
            // it; use it to classify the thread as a group (multi-recipient MMS) so the
            // per-group mute gate applies to SMS groups just like app groups.
            val isGroup = isGroupThread(applicationContext, message.conversationId)
            pipeline.ingestSms(message, isGroup = isGroup)
        }
        detectDeletions(applicationContext)
    }

    /**
     * Classify an SMS/MMS thread as a group by counting its recipients. Android stores
     * the canonical recipient ids for a thread (space-separated) in the simple
     * conversations view; more than one recipient ⇒ a group thread. Cached per thread;
     * fails safe to non-group on any provider/permission error.
     */
    private fun isGroupThread(context: Context, threadId: Long): Boolean {
        threadIsGroup[threadId]?.let { return it }
        val recipientIds: String? = try {
            context.contentResolver.query(
                CONVERSATIONS_URI,
                arrayOf(RECIPIENT_IDS),
                "${Telephony.Threads._ID} = ?",
                arrayOf(threadId.toString()),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val col = c.getColumnIndex(RECIPIENT_IDS)
                    if (col >= 0) c.getString(col) else null
                } else {
                    null
                }
            }
        } catch (se: SecurityException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "SMS recipient lookup failed for thread $threadId", e)
            null
        }
        val isGroup = SmsGrouping.isGroupThread(recipientIds)
        threadIsGroup[threadId] = isGroup
        return isGroup
    }

    /**
     * Read SMS rows newer than the last ingested id and map them to domain
     * [Message]s. The SMS thread id is carried *raw* in [Message.conversationId] as a
     * conversation key — [CapturePipeline.ingestSms] resolves it into a real
     * conversation row id (the message's FK parent) before persisting, so this
     * observer needs no [com.quietping.domain.repo.MessageRepository] handle. The
     * stable conversation key (thread id as string) is also tracked internally for
     * deletion diffing. Returns an empty list when [android.Manifest.permission.READ_SMS]
     * is not held or no new rows exist (fail safe).
     */
    fun readNewSms(context: Context): List<Message> {
        val resolver: ContentResolver = context.contentResolver
        val out = ArrayList<Message>()
        var maxIdThisPass = lastMaxId

        val cursor: Cursor? = try {
            resolver.query(
                SMS_URI,
                PROJECTION,
                if (lastMaxId >= 0) "${Telephony.Sms._ID} > ?" else null,
                if (lastMaxId >= 0) arrayOf(lastMaxId.toString()) else null,
                "${Telephony.Sms._ID} ASC",
            )
        } catch (se: SecurityException) {
            // READ_SMS not granted yet — degrade silently (PRD §7 skippable perms).
            Log.d(TAG, "READ_SMS not granted; skipping SMS read")
            return emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "SMS query failed", e)
            return emptyList()
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndex(Telephony.Sms._ID)
            val bodyCol = c.getColumnIndex(Telephony.Sms.BODY)
            val addressCol = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateCol = c.getColumnIndex(Telephony.Sms.DATE)
            val threadCol = c.getColumnIndex(Telephony.Sms.THREAD_ID)
            val typeCol = c.getColumnIndex(Telephony.Sms.TYPE)

            while (c.moveToNext()) {
                val id = if (idCol >= 0) c.getLong(idCol) else continue
                // Only capture inbound messages; outbound/draft/sent are the user's own.
                if (typeCol >= 0 && c.getInt(typeCol) != Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    seenSmsIds += id
                    continue
                }
                val body = if (bodyCol >= 0) c.getString(bodyCol).orEmpty() else ""
                if (body.isBlank()) {
                    seenSmsIds += id
                    continue
                }
                val sender = if (addressCol >= 0) c.getString(addressCol).orEmpty() else ""
                val postedAt = if (dateCol >= 0) c.getLong(dateCol) else System.currentTimeMillis()
                val threadId = if (threadCol >= 0) c.getLong(threadCol) else id

                seenSmsIds += id
                rowConversationKey[id] = threadId.toString()
                if (id > maxIdThisPass) maxIdThisPass = id

                out += Message(
                    id = 0L,
                    conversationId = threadId,
                    sender = sender,
                    postedAt = postedAt,
                    capturedAt = System.currentTimeMillis(),
                    source = CaptureSource.SMS,
                    status = MessageStatus.ACTIVE,
                    currentBody = body,
                )
            }
        }

        lastMaxId = maxIdThisPass
        return out
    }

    /**
     * Compare the ids currently present in the provider against [seenSmsIds]. Any id
     * we previously cached but that is now gone has been deleted in the Messages app
     * → surface it as Recovered/DELETED in the Vault.
     */
    private suspend fun detectDeletions(context: Context) {
        if (seenSmsIds.isEmpty()) return
        val present = HashSet<Long>(seenSmsIds.size)
        val cursor: Cursor? = try {
            context.contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                null,
            )
        } catch (se: SecurityException) {
            return
        } catch (e: Exception) {
            Log.w(TAG, "SMS deletion scan failed", e)
            return
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndex(Telephony.Sms._ID)
            if (idCol < 0) return
            while (c.moveToNext()) present += c.getLong(idCol)
        }

        val deletedIds = seenSmsIds.filter { it !in present }
        for (id in deletedIds) {
            val conversationKey = rowConversationKey[id] ?: continue
            pipeline.markSmsDeleted(conversationKey, body = null)
            seenSmsIds.remove(id)
            rowConversationKey.remove(id)
        }
    }

    /**
     * Stop observing and release the worker thread.
     */
    fun unregister(context: Context) {
        runCatching { context.contentResolver.unregisterContentObserver(this) }
        appContext = null
        workerThread.quitSafely()
    }

    companion object {
        private const val TAG = "SmsObserver"

        private val SMS_URI: Uri = Telephony.Sms.CONTENT_URI
        private val MMS_URI: Uri = Telephony.Mms.CONTENT_URI

        /** Simple conversations view exposing per-thread `recipient_ids`. */
        private val CONVERSATIONS_URI: Uri = Telephony.Threads.CONTENT_URI

        /** Space-separated canonical recipient ids for a thread. */
        private const val RECIPIENT_IDS = "recipient_ids"

        private val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.TYPE,
        )

        /**
         * Create, register (on both SMS and MMS uris), and prime the observer.
         * The initial [readNewSms] pass seeds [lastMaxId] so existing history is not
         * re-ingested on first connect — only messages arriving from now on flow
         * through. Returns the live observer (call [unregister] to tear down).
         */
        fun register(context: Context, pipeline: CapturePipeline): SmsObserver {
            val thread = HandlerThread("quietping-sms-observer").apply { start() }
            val handler = Handler(thread.looper)
            val observer = SmsObserver(pipeline, handler, thread)
            observer.appContext = context.applicationContext

            val resolver = context.contentResolver
            runCatching {
                resolver.registerContentObserver(SMS_URI, true, observer)
                resolver.registerContentObserver(MMS_URI, true, observer)
            }.onFailure { Log.w(TAG, "Failed to register SMS observer", it) }

            // Seed baseline so pre-existing messages aren't replayed as "new".
            observer.scope.launch {
                runCatching { observer.primeBaseline(context.applicationContext) }
                    .onFailure { Log.w(TAG, "SMS baseline prime failed", it) }
            }
            return observer
        }
    }

    /**
     * Seed [lastMaxId] / [seenSmsIds] with the current provider contents without
     * ingesting them, so only genuinely new messages are captured after registration.
     */
    private fun primeBaseline(context: Context) {
        val cursor: Cursor? = try {
            context.contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID),
                null,
                null,
                "${Telephony.Sms._ID} ASC",
            )
        } catch (se: SecurityException) {
            return
        } catch (e: Exception) {
            Log.w(TAG, "SMS baseline query failed", e)
            return
        }
        cursor?.use { c ->
            val idCol = c.getColumnIndex(Telephony.Sms._ID)
            val threadCol = c.getColumnIndex(Telephony.Sms.THREAD_ID)
            if (idCol < 0) return
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                seenSmsIds += id
                if (threadCol >= 0) rowConversationKey[id] = c.getLong(threadCol).toString()
                if (id > lastMaxId) lastMaxId = id
            }
        }
    }
}
