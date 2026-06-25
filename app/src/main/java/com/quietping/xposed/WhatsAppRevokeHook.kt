package com.quietping.xposed

import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * WhatsApp "block-revoke" hook (FACE2_XPOSED_RD.md §4).
 *
 * Strategy: intercept the handler that processes an incoming **revoke** ("delete for everyone")
 * protocol message and SKIP the deletion, so WhatsApp's own renderer keeps drawing the original
 * bubble — genuinely inside the real chat UI. Optionally flag it `revoked-but-kept` for a marker.
 *
 * ## Why the hook point is a TODO and not hardcoded
 * WhatsApp ships heavily obfuscated: class/method names are `a.b.c` and change almost every
 * update, so you CANNOT hook by name. The durable approach (do this per supported version):
 *
 *   1. Pull the current target APK, decompile with `jadx`.
 *   2. Locate the revoke handler by BEHAVIOURAL signature — the method that, given a protocol
 *      message of the revoke type, marks a message row deleted — not by its (rotating) name.
 *   3. Encode a bytecode/shape matcher in [findRevokeHandler] so the hook re-finds it after
 *      each update.
 *   4. Fail safe: if the matcher finds nothing, no-op (never crash WhatsApp).
 *
 * Until that matcher is filled in, [install] verifies it can run and logs — it makes no
 * unsafe assumptions about WhatsApp internals.
 */
object WhatsAppRevokeHook {

    fun install(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Primary, version-DURABLE path: hook the framework SQLite layer (never obfuscated), so the
        // hook always attaches and surfaces/blocks the revoke write. See WhatsAppDbRevokeStrategy.
        val dbAttached = WhatsAppDbRevokeStrategy.install(lpparam)

        // Secondary, version-PRECISE path: the obfuscated revoke handler located by behavioural
        // shape. Inert until per-version predicates are filled in (WHATSAPP_RE_PROCEDURE.md).
        val handler = findRevokeHandler(lpparam)
        if (handler == null) {
            if (!dbAttached) {
                XposedBridge.log("QuietPing: WhatsApp revoke hook inert — DB strategy unattached and handler not located.")
            }
            return
        }

        // TODO(Face 2): once findRevokeHandler resolves the obfuscated target, hook it here:
        //   XposedHelpers.findAndHookMethod(handler.clazz, handler.method, *handler.paramTypes,
        //       object : XC_MethodHook() {
        //           override fun beforeHookedMethod(param: MethodHookParam) {
        //               // skip the deletion (block-revoke) and/or mark the row revoked-but-kept;
        //               // optionally forward recovered text to Face 1's SQLCipher vault bridge.
        //               param.result = null
        //           }
        //       })
        XposedBridge.log("QuietPing: WhatsApp revoke hook point resolved — wiring pending.")
    }

    /**
     * Resolve the obfuscated revoke-handler for the loaded WhatsApp version by BEHAVIOURAL shape,
     * via [WhatsAppRevokeMatcher] over `lpparam.classLoader`. Returns null until the per-version
     * predicates below are filled in (see WHATSAPP_RE_PROCEDURE.md). Returning null MUST keep
     * WhatsApp fully functional — the matcher and both inputs below fail safe to "no match".
     *
     * The two inputs are the per-version extension point; everything else (reflection, scanning,
     * failure handling) is reusable in [WhatsAppRevokeMatcher]:
     *
     *  - [candidateClassNames]: obfuscated class names located via jadx for the CURRENT build.
     *  - the [WhatsAppRevokeMatcher.MethodPredicate]: the behavioural signature of the handler.
     *
     * Both are intentionally empty/non-matching TODOs right now, so [WhatsAppRevokeMatcher.firstMatch]
     * returns null and the hook stays inert.
     */
    private fun findRevokeHandler(lpparam: XC_LoadPackage.LoadPackageParam): RevokeHandler? {
        val matcher = WhatsAppRevokeMatcher(lpparam.classLoader)

        // TODO(Face 2 RE — per version): obfuscated names of the class(es) that host the revoke
        // handler, found via jadx (WHATSAPP_RE_PROCEDURE.md step 2). Empty ⇒ matcher returns null.
        val candidateClassNames = emptyList<String>()

        // TODO(Face 2 RE — per version): the handler's behavioural signature. Compose shape +
        // modifier predicates from what jadx shows (WHATSAPP_RE_PROCEDURE.md step 3). The example
        // below is a SHAPE SKELETON, deliberately wired to match nothing until verified against a
        // real APK — `paramCount = -1` can never equal a real method's parameter count, so the
        // predicate (and thus firstMatch) yields no match and the hook stays inert. Fail-safe.
        val predicate = matcher.allOf(
            matcher.matchByModifiers(),
            matcher.matchByParamShape(
                returnType = null,   // TODO: e.g. Void.TYPE once RE confirms it
                paramCount = -1,     // TODO: real declared param count; -1 = no-match sentinel
                paramTypePredicates = emptyList(), // TODO: one ParamPredicate per param, by SHAPE
            ),
        )

        val method = matcher.firstMatch(candidateClassNames, predicate) ?: return null
        return RevokeHandler(
            clazz = method.declaringClass,
            method = method.name,
            paramTypes = method.parameterTypes,
        )
    }

    /** Resolved obfuscated hook target (class + method + parameter types). */
    private data class RevokeHandler(
        val clazz: Class<*>,
        val method: String,
        val paramTypes: Array<Class<*>>,
    )
}
