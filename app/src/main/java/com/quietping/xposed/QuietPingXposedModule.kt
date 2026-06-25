package com.quietping.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Face 2 entry point (FACE2_XPOSED_RD.md). Listed in `assets/xposed_init`; LSPosed instantiates
 * it and calls [handleLoadPackage] inside EVERY scoped target process at spawn time.
 *
 * Responsibilities here are deliberately thin:
 *  1. Identify which target app this process is.
 *  2. Check the per-app gate written by Face 1 ([XposedGate]). Off/absent → return, zero footprint.
 *  3. Delegate to the app-specific hook.
 *
 * Anything heavier (the obfuscated revoke-handler lookup) lives in the per-app hook so a
 * failure there can fail safe without crashing the host app.
 */
class QuietPingXposedModule : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName

        // Only the chat-app targets (matches @array/xposed_scope). Anything else is ignored even
        // if LSPosed somehow loaded us into it.
        val isTarget = pkg in WHATSAPP_PACKAGES || pkg in INSTAGRAM_PACKAGES || pkg in FACEBOOK_PACKAGES
        if (!isTarget) return

        // Per-app gate from Face 1. Disabled → no-op, the host app is untouched.
        if (!XposedGate.isHookEnabled(pkg)) return

        try {
            when (pkg) {
                in WHATSAPP_PACKAGES -> WhatsAppRevokeHook.install(lpparam)
                // Instagram "unsend" + Facebook/Messenger "remove for everyone" use different
                // internal models — separate hooks (each fail-safe inert until per-version RE).
                in INSTAGRAM_PACKAGES -> InstagramUnsendHook.install(lpparam)
                in FACEBOOK_PACKAGES -> FacebookRemoveHook.install(lpparam)
            }
        } catch (t: Throwable) {
            // Fail safe: never crash the host app because our hook broke (e.g. after a target update).
            XposedBridge.log("QuietPing: hook install failed for $pkg: $t")
        }
    }

    companion object {
        val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val INSTAGRAM_PACKAGES = setOf("com.instagram.android")
        val FACEBOOK_PACKAGES = setOf("com.facebook.orca", "com.facebook.katana")
    }
}
