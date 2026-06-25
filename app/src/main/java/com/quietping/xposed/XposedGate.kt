package com.quietping.xposed

import de.robv.android.xposed.XSharedPreferences

/**
 * Face 1 ⇄ Face 2 bridge contract (FACE2_XPOSED_RD.md §6).
 *
 * The manager (Face 1) and the in-process hook (Face 2) run in DIFFERENT processes — the
 * QuietPing app vs. inside WhatsApp/Instagram/Facebook — so a normal [android.content.SharedPreferences]
 * cannot be shared. [XSharedPreferences] is the canonical Xposed bridge: Face 1 writes a
 * world-readable prefs file, Face 2 reads it from inside the target process.
 *
 * This object owns the file name + key convention so both faces agree on it. The WRITE side
 * lives in Face 1 (the per-app "Deep capture (root)" toggle) and is out of scope for the
 * Face 2 scaffold.
 */
object XposedGate {

    /** Module package that owns the prefs file (this app). */
    const val MODULE_PACKAGE = "com.quietping"

    /** World-readable prefs file shared from Face 1 to Face 2. */
    const val PREFS_NAME = "quietping_xposed"

    /** Per-package enable key, e.g. `hook_enabled__com.whatsapp`. */
    fun enabledKey(targetPackage: String): String = "hook_enabled__$targetPackage"

    // --- Vault bridge contract (Face 2 sender ⇄ Face 1 sink) ---------------------------------------
    // A message the hook recovers is delivered to QuietPing's DeepCaptureProvider via a
    // ContentProvider.call(). The auth boundary is the KERNEL-ENFORCED CALLER UID checked at the
    // provider (must be one of [TARGET_PACKAGES] — the apps our hook runs inside), NOT a shared
    // secret: any secret in the world-readable gate file would itself be spoofable. Content transits
    // Binder IPC only, never world-readable disk; the provider writes it encrypted to SQLCipher.

    /** ContentProvider authority of the Face 1 sink. */
    const val DEEP_CAPTURE_AUTHORITY = "com.quietping.deepcapture"

    /** call() method name on the sink provider. */
    const val METHOD_RECOVER = "recover"

    /**
     * Packages our hook legitimately runs inside (matches `@array/xposed_scope`). The provider
     * accepts a delivery ONLY when the caller UID resolves to one of these — a standalone app has a
     * different UID and is rejected.
     */
    val TARGET_PACKAGES: Set<String> = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.instagram.android",
        "com.facebook.orca", "com.facebook.katana",
    )

    const val EXTRA_PACKAGE = "qp_package"
    const val EXTRA_CONVERSATION_KEY = "qp_conversation_key"
    const val EXTRA_SENDER = "qp_sender"
    const val EXTRA_BODY = "qp_body"
    const val EXTRA_POSTED_AT = "qp_posted_at"

    /**
     * Read side (Face 2). Returns true only if Face 1 has explicitly enabled deep capture for
     * [targetPackage]. Fails safe to `false`: if the prefs file is missing/unreadable or the
     * key is absent, the hook must no-op (zero footprint inside the host app).
     */
    fun isHookEnabled(targetPackage: String): Boolean = try {
        val prefs = XSharedPreferences(MODULE_PACKAGE, PREFS_NAME)
        prefs.makeWorldReadable()
        prefs.reload()
        prefs.getBoolean(enabledKey(targetPackage), false)
    } catch (t: Throwable) {
        false
    }
}
