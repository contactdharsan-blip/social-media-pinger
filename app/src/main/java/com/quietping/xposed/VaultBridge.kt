package com.quietping.xposed

import android.app.AndroidAppHelper
import android.net.Uri
import android.os.Bundle
import de.robv.android.xposed.XposedBridge

/**
 * Face 2 → Face 1 bridge sender (FACE2_XPOSED_RD.md §9). Called from inside a hooked app's process
 * when the hook recovers a "delete for everyone" message, to hand the original content to QuietPing's
 * encrypted vault.
 *
 * ## Why this is safe (encrypted-at-rest + spoofing)
 * The hook runs as the TARGET app's UID and cannot write QuietPing's SQLCipher DB directly. Instead
 * it delivers via `ContentResolver.call()` to QuietPing's `DeepCaptureProvider`:
 *  - The provider authenticates the sender by KERNEL-ENFORCED caller UID (`Binder.getCallingUid()`),
 *    accepting only callers whose UID resolves to a target package — a standalone app cannot forge
 *    that. No shared secret is used (a secret in the world-readable gate file would be spoofable).
 *  - The recovered text transits Binder IPC between two UIDs only — never world-readable disk. The
 *    provider is what writes it, encrypted, to SQLCipher.
 *
 * Fail safe: if the application context is unavailable or the call throws, the send is skipped
 * silently — the host app is never affected.
 */
object VaultBridge {

    private val PROVIDER_URI: Uri = Uri.parse("content://${XposedGate.DEEP_CAPTURE_AUTHORITY}")

    fun send(packageName: String, conversationKey: String, sender: String, body: String, postedAt: Long) {
        try {
            val app = AndroidAppHelper.currentApplication() ?: return
            val extras = Bundle().apply {
                putString(XposedGate.EXTRA_PACKAGE, packageName)
                putString(XposedGate.EXTRA_CONVERSATION_KEY, conversationKey)
                putString(XposedGate.EXTRA_SENDER, sender)
                putString(XposedGate.EXTRA_BODY, body)
                putLong(XposedGate.EXTRA_POSTED_AT, postedAt)
            }
            // call() runs as a Binder transaction so the provider sees our real caller UID and can
            // authenticate it. Delivered only to QuietPing's authority.
            app.contentResolver.call(PROVIDER_URI, XposedGate.METHOD_RECOVER, null, extras)
        } catch (t: Throwable) {
            XposedBridge.log("QuietPing: vault bridge send failed: $t")
        }
    }
}
