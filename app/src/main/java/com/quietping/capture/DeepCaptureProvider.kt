package com.quietping.capture

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Log
import com.quietping.domain.model.RawEvent
import com.quietping.xposed.XposedGate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Face 1 sink of the vault bridge (FACE2_XPOSED_RD.md §9). Receives a "delete for everyone" message
 * recovered by the Face 2 Xposed hook (sent by [com.quietping.xposed.VaultBridge]) and feeds it into
 * the normal ingestion pipeline, which archives it encrypted in SQLCipher.
 *
 * ## Authentication — caller UID, not a shared secret
 * The hook runs inside the target app's process, so a legitimate delivery's caller UID resolves to
 * one of [XposedGate.TARGET_PACKAGES]. `ContentProvider.call()` runs in a Binder transaction, so
 * [Binder.getCallingUid] returns the genuine caller and is kernel-enforced — a standalone malicious
 * app has a different UID and is rejected. A token in the world-readable gate file would itself be
 * readable (and thus forgeable) by any app, so it is deliberately NOT used as the boundary.
 *
 * Hilt cannot inject a ContentProvider (providers initialise before the Hilt component), so the
 * [CapturePipeline] singleton is fetched lazily inside [call] via [EntryPointAccessors] — by then the
 * application's Hilt component is fully built.
 */
class DeepCaptureProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PipelineEntryPoint {
        fun capturePipeline(): CapturePipeline
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != XposedGate.METHOD_RECOVER || extras == null) return null
        val ctx = context ?: return null

        // AUTH BOUNDARY: caller must be one of the target app processes our hook runs in.
        val callingUid = Binder.getCallingUid()
        val callerPkgs = ctx.packageManager.getPackagesForUid(callingUid)?.toSet().orEmpty()
        if (callerPkgs.none { it in XposedGate.TARGET_PACKAGES }) {
            Log.w(TAG, "Rejected deep-capture call: uid=$callingUid pkgs=$callerPkgs not a target app.")
            return null
        }

        val packageName = extras.getString(XposedGate.EXTRA_PACKAGE) ?: return null
        val body = extras.getString(XposedGate.EXTRA_BODY) ?: return null
        val conversationKey = extras.getString(XposedGate.EXTRA_CONVERSATION_KEY) ?: packageName
        val sender = extras.getString(XposedGate.EXTRA_SENDER).orEmpty()
        val postedAt = extras.getLong(XposedGate.EXTRA_POSTED_AT, 0L)

        val pipeline = EntryPointAccessors
            .fromApplication(ctx.applicationContext, PipelineEntryPoint::class.java)
            .capturePipeline()
        pipeline.offer(
            RawEvent.DeepHookRecovered(
                packageName = packageName,
                conversationKey = conversationKey,
                sender = sender,
                body = body,
                postedAt = postedAt,
            ),
        )
        return null
    }

    // Unused CRUD surface — this provider exists solely for the authenticated call() bridge.
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val TAG = "DeepCaptureProvider"
    }
}
