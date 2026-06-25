package com.quietping.xposed

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Instagram "unsend" hook (FACE2_XPOSED_RD.md §4).
 *
 * Strategy: Instagram Direct uses an **unsend** model — the sender issues an `unsend` action that
 * tells the recipient client to *remove* the message item from the thread (Direct uses a realtime/
 * MQTT thread store, not WhatsApp-style protocol revoke). Intercept the handler that applies that
 * unsend mutation to the local thread and SKIP it, so Instagram's own renderer keeps the original
 * message item — genuinely inside the real Direct UI. Optionally flag it `unsent-but-kept`.
 *
 * ## Difference from WhatsApp/Facebook
 * WhatsApp = protocol *revoke* on a message row; Facebook/Messenger = *remove for everyone* on a
 * Messenger thread item. Instagram = *unsend*, mutating the Direct thread store entry. Same goal
 * (keep the message), different internal model, so this is a distinct hook.
 *
 * ## Why the hook point is a TODO and not hardcoded
 * Instagram ships heavily obfuscated and reshuffles class/method names almost every update, so you
 * CANNOT hook by name. The durable approach (do this per supported version):
 *
 *   1. Pull the current target APK, decompile with `jadx`.
 *   2. Locate the unsend handler by BEHAVIOURAL signature — the method that, given an unsend action
 *      for a Direct thread item, removes/marks-removed that item — not by its (rotating) name.
 *   3. Encode a bytecode/shape matcher in [findUnsendHandler] so the hook re-finds it after each update.
 *   4. Fail safe: if the matcher finds nothing, no-op (never crash Instagram).
 *
 * Until that matcher is filled in, [install] verifies it can run and logs — it makes no unsafe
 * assumptions about Instagram internals.
 */
object InstagramUnsendHook {

    /** Instagram Direct message-store table anchors (observe-only until confirmed on-device). */
    private val MESSAGE_TABLES = setOf("messages", "message", "thread_messages")

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Primary, version-DURABLE path: hook the framework SQLite layer. Unsend = a row DELETE in
        // the Direct store; observe-only until the table/shape is confirmed on a real device.
        val dbAttached = SqliteWriteHook.install(
            appLabel = "Instagram",
            appPackage = lpparam.packageName,
            tables = MESSAGE_TABLES,
            validated = false,
            recover = true,
            detector = { op, _, _, _ -> op == SqliteWriteHook.Op.DELETE },
        )

        // Secondary, version-PRECISE path: the obfuscated unsend handler. Inert until per-version RE.
        val handler = findUnsendHandler(lpparam)
        if (handler == null) {
            if (!dbAttached) {
                XposedBridge.log("QuietPing: Instagram unsend hook inert — DB strategy unattached and handler not located.")
            }
            return
        }

        // TODO(Face 2): once findUnsendHandler resolves the obfuscated target, hook it here:
        //   XposedHelpers.findAndHookMethod(handler.clazz, handler.method, *handler.paramTypes,
        //       object : XC_MethodHook() {
        //           override fun beforeHookedMethod(param: MethodHookParam) {
        //               // skip the unsend mutation and/or mark the item unsent-but-kept;
        //               // optionally forward recovered text to Face 1's SQLCipher vault bridge.
        //               param.result = null
        //           }
        //       })
        XposedBridge.log("QuietPing: Instagram unsend hook point resolved — wiring pending.")
    }

    /**
     * Resolve the obfuscated unsend-handler for the loaded Instagram version. Returns null until
     * the per-version bytecode matcher is implemented (see class KDoc). Returning null MUST keep
     * Instagram fully functional.
     */
    private fun findUnsendHandler(lpparam: XC_LoadPackage.LoadPackageParam): UnsendHandler? {
        // TODO(Face 2 RE workflow): match by behavioural signature against lpparam.classLoader.
        return null
    }

    /** Resolved obfuscated hook target (class + method + parameter types). */
    private data class UnsendHandler(
        val clazz: Class<*>,
        val method: String,
        val paramTypes: Array<Class<*>>,
    )
}
