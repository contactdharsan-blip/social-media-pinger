package com.quietping.xposed

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Facebook/Messenger "remove for everyone" hook (FACE2_XPOSED_RD.md §4).
 *
 * Strategy: Messenger (com.facebook.orca) and Facebook (com.facebook.katana) use a **remove for
 * everyone** model — the sender's removal propagates and the recipient client *removes* the message
 * from the thread store. Intercept the handler that applies that "remove for everyone" mutation to
 * the local thread and SKIP it, so Messenger's own renderer keeps the original bubble — genuinely
 * inside the real chat UI. Optionally flag it `removed-but-kept`.
 *
 * ## Difference from WhatsApp/Instagram
 * WhatsApp = protocol *revoke* on a message row; Instagram = *unsend* on a Direct thread item.
 * Facebook/Messenger = *remove for everyone*, deleting a Messenger thread item for all participants.
 * Same goal (keep the message), different internal model, so this is a distinct hook.
 *
 * ## Why the hook point is a TODO and not hardcoded
 * Facebook/Messenger ship heavily obfuscated and reshuffle class/method names almost every update,
 * so you CANNOT hook by name. The durable approach (do this per supported version):
 *
 *   1. Pull the current target APK, decompile with `jadx`.
 *   2. Locate the remove handler by BEHAVIOURAL signature — the method that, given a "remove for
 *      everyone" event for a thread item, removes/marks-removed that item — not by its (rotating) name.
 *   3. Encode a bytecode/shape matcher in [findRemoveHandler] so the hook re-finds it after each update.
 *   4. Fail safe: if the matcher finds nothing, no-op (never crash Facebook/Messenger).
 *
 * Until that matcher is filled in, [install] verifies it can run and logs — it makes no unsafe
 * assumptions about Facebook/Messenger internals.
 */
object FacebookRemoveHook {

    /** Messenger thread-store table anchors (threads_db2; observe-only until confirmed on-device). */
    private val MESSAGE_TABLES = setOf("messages", "message", "thread_messages")

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Primary, version-DURABLE path: hook the framework SQLite layer. Remove-for-everyone = a row
        // DELETE in the thread store; observe-only until the table/shape is confirmed on a real device.
        val dbAttached = SqliteWriteHook.install(
            appLabel = "Facebook",
            appPackage = lpparam.packageName,
            tables = MESSAGE_TABLES,
            validated = false,
            recover = true,
            detector = { op, _, _, _ -> op == SqliteWriteHook.Op.DELETE },
        )

        // Secondary, version-PRECISE path: the obfuscated remove handler. Inert until per-version RE.
        val handler = findRemoveHandler(lpparam)
        if (handler == null) {
            if (!dbAttached) {
                XposedBridge.log("QuietPing: Facebook remove hook inert — DB strategy unattached and handler not located.")
            }
            return
        }

        // TODO(Face 2): once findRemoveHandler resolves the obfuscated target, hook it here:
        //   XposedHelpers.findAndHookMethod(handler.clazz, handler.method, *handler.paramTypes,
        //       object : XC_MethodHook() {
        //           override fun beforeHookedMethod(param: MethodHookParam) {
        //               // skip the remove-for-everyone mutation and/or mark the item removed-but-kept;
        //               // optionally forward recovered text to Face 1's SQLCipher vault bridge.
        //               param.result = null
        //           }
        //       })
        XposedBridge.log("QuietPing: Facebook remove hook point resolved — wiring pending.")
    }

    /**
     * Resolve the obfuscated remove-handler for the loaded Facebook/Messenger version. Returns null
     * until the per-version bytecode matcher is implemented (see class KDoc). Returning null MUST
     * keep the host app fully functional.
     */
    private fun findRemoveHandler(lpparam: XC_LoadPackage.LoadPackageParam): RemoveHandler? {
        // TODO(Face 2 RE workflow): match by behavioural signature against lpparam.classLoader.
        return null
    }

    /** Resolved obfuscated hook target (class + method + parameter types). */
    private data class RemoveHandler(
        val clazz: Class<*>,
        val method: String,
        val paramTypes: Array<Class<*>>,
    )
}
