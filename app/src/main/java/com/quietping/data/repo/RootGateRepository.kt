package com.quietping.data.repo

import android.content.Context
import android.content.SharedPreferences
import com.quietping.xposed.XposedGate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A deep-capture target app: its [packageName] and a human label for the UI. */
data class DeepCaptureTarget(val packageName: String, val label: String)

/**
 * Face 1 write side of the Face 1 ⇄ Face 2 bridge (FACE2_XPOSED_RD.md §6 / [XposedGate]).
 *
 * The per-app "Deep capture (root)" toggle persists to the SAME world-readable prefs file
 * Face 2 reads from inside the host app. We open it with [Context.MODE_WORLD_READABLE] so the
 * hook process (running inside WhatsApp/Instagram/Facebook) can read it via XSharedPreferences;
 * that mode throws on non-LSPosed devices (and is forbidden on stock Android N+), so every
 * access is wrapped to degrade gracefully — a missing/unwritable gate simply reads as disabled.
 */
@Singleton
class RootGateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** The deep-capture target apps, mirroring `@array/xposed_scope`. */
    val targets: List<DeepCaptureTarget> = listOf(
        DeepCaptureTarget("com.whatsapp", "WhatsApp"),
        DeepCaptureTarget("com.whatsapp.w4b", "WhatsApp Business"),
        DeepCaptureTarget("com.instagram.android", "Instagram"),
        DeepCaptureTarget("com.facebook.orca", "Messenger"),
        DeepCaptureTarget("com.facebook.katana", "Facebook")
    )

    /**
     * The shared prefs handle, or null when the platform refuses world-readable mode
     * (i.e. no LSPosed). Lazy + cached so we only hit the throwing path once.
     */
    private val prefs: SharedPreferences? by lazy { openWorldReadablePrefs() }

    @Suppress("DEPRECATION")
    private fun openWorldReadablePrefs(): SharedPreferences? = try {
        context.getSharedPreferences(XposedGate.PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (t: Throwable) {
        null
    }

    /** True when the deep-capture hook is enabled for [packageName]. Fails safe to false. */
    fun isEnabled(packageName: String): Boolean = try {
        prefs?.getBoolean(XposedGate.enabledKey(packageName), false) ?: false
    } catch (t: Throwable) {
        false
    }

    /** Per-package enabled flags for every [targets] entry. */
    fun enabledFlags(): Map<String, Boolean> =
        targets.associate { it.packageName to isEnabled(it.packageName) }

    /**
     * Enable/disable deep capture for [packageName]. No-ops (returns false) when the gate
     * file is unavailable, so a non-LSPosed device can never crash on a toggle.
     */
    fun setEnabled(packageName: String, enabled: Boolean): Boolean = try {
        val editor = prefs?.edit() ?: return false
        editor.putBoolean(XposedGate.enabledKey(packageName), enabled).commit()
    } catch (t: Throwable) {
        false
    }

    // NOTE: the Face 2 → Face 1 vault bridge is authenticated by kernel-enforced CALLER UID at
    // DeepCaptureProvider, NOT by a shared secret. A token in this world-readable gate file would be
    // readable (and thus spoofable) by any app on the device, so it is deliberately NOT used as an
    // auth boundary. See DeepCaptureProvider / FACE2_XPOSED_RD.md §9.
}
