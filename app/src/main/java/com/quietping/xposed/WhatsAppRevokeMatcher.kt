package com.quietping.xposed

import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Behavioural-signature matcher for obfuscated host methods (FACE2_XPOSED_RD.md §4.1).
 *
 * WhatsApp ships heavily obfuscated: class and method names are `a.b.c` and rotate almost every
 * update, so you can NEVER hook by name. The only durable handle on the revoke ("delete for
 * everyone") handler is its *behavioural shape* — return type, parameter count, the runtime types
 * of its parameters — which is dictated by WhatsApp's protocol model and is far stabler than the
 * obfuscated identifiers around it.
 *
 * This is a REUSABLE framework, not a WhatsApp-specific table. It is pure reflection over the host
 * [ClassLoader]; it fabricates NOTHING about WhatsApp internals. The per-version knowledge — which
 * candidate classes to scan and which shape predicates identify the revoke handler — is supplied by
 * the caller ([WhatsAppRevokeHook.findRevokeHandler]) and is the documented per-version extension
 * point (see WHATSAPP_RE_PROCEDURE.md).
 *
 * ## Fail-safe contract
 * Every entry point swallows reflection failures ([ClassNotFoundException], [NoClassDefFoundError],
 * linkage errors from half-loaded classes, etc.) and degrades to "no match". A miss here means the
 * hook stays inert and the host app runs completely normally — never a crash.
 *
 * ## How a caller uses it
 * ```
 * val matcher = WhatsAppRevokeMatcher(lpparam.classLoader)
 * val method = matcher.firstMatch(
 *     candidateClassNames = listOf(/* obfuscated names found via jadx for THIS version */),
 *     predicate = matcher.matchByParamShape(
 *         returnType = Void.TYPE,          // or a boolean, etc. — whatever RE shows
 *         paramCount = 2,
 *         paramTypePredicates = listOf(
 *             { it.name.contains("...") },  // shape, not name; see procedure doc
 *             { true },
 *         ),
 *     ),
 * )
 * ```
 */
class WhatsAppRevokeMatcher(private val classLoader: ClassLoader) {

    /**
     * A behavioural predicate over a candidate [Method]. Returns true if the method's *shape*
     * matches the target handler. Compose several with [and] / [allOf] to tighten a match.
     */
    fun interface MethodPredicate {
        fun matches(method: Method): Boolean
    }

    /** A predicate over a single parameter [Class]. Used for per-parameter shape checks. */
    fun interface ParamPredicate {
        fun matches(type: Class<*>): Boolean
    }

    /**
     * Match by the method's coarse shape — its return type, declared parameter count, and the
     * runtime type of each parameter. This is the primary behavioural handle: WhatsApp can rename
     * the method and its enclosing class freely, but the protocol-driven shape (e.g. "takes the
     * revoke protocol message + the chat-row store, returns void") is what actually changes the
     * deletion behaviour and so is far more stable.
     *
     * @param returnType exact return type to require, or `null` to accept any return type.
     * @param paramCount exact declared parameter count to require, or `null` to accept any count.
     * @param paramTypePredicates one [ParamPredicate] per positional parameter. When non-empty its
     *   size must equal the method's parameter count for the method to match; each predicate is
     *   applied to the parameter at its index. Predicates check SHAPE (interfaces implemented,
     *   superclass, field/method counts) — NOT obfuscated names, which rotate. Pass `{ true }` for
     *   a positional wildcard.
     */
    fun matchByParamShape(
        returnType: Class<*>? = null,
        paramCount: Int? = null,
        paramTypePredicates: List<ParamPredicate> = emptyList(),
    ): MethodPredicate = MethodPredicate { method ->
        if (returnType != null && method.returnType != returnType) return@MethodPredicate false

        val params = method.parameterTypes
        if (paramCount != null && params.size != paramCount) return@MethodPredicate false

        if (paramTypePredicates.isNotEmpty()) {
            if (params.size != paramTypePredicates.size) return@MethodPredicate false
            for (i in params.indices) {
                if (!safeParamMatch(paramTypePredicates[i], params[i])) return@MethodPredicate false
            }
        }
        true
    }

    /**
     * Require a method's modifiers — e.g. instance vs. static, or non-synthetic. Obfuscators emit
     * synthetic bridge methods that can collide with a shape match; gating those out sharpens the
     * result. Pass the [Modifier] flags that MUST be set and those that MUST be clear.
     */
    fun matchByModifiers(
        mustHave: Int = 0,
        mustNotHave: Int = Modifier.ABSTRACT,
        excludeSynthetic: Boolean = true,
    ): MethodPredicate = MethodPredicate { method ->
        val mods = method.modifiers
        if (mods and mustHave != mustHave) return@MethodPredicate false
        if (mods and mustNotHave != 0) return@MethodPredicate false
        if (excludeSynthetic && (method.isSynthetic || method.isBridge)) return@MethodPredicate false
        true
    }

    /** Logical AND of two predicates. */
    infix fun MethodPredicate.and(other: MethodPredicate): MethodPredicate =
        MethodPredicate { m -> this.matches(m) && other.matches(m) }

    /** Logical AND of any number of predicates; an empty list matches everything. */
    fun allOf(vararg predicates: MethodPredicate): MethodPredicate =
        MethodPredicate { m -> predicates.all { it.matches(m) } }

    /**
     * Load a candidate class by its (per-version, obfuscated) name via the host classloader.
     * Returns null on any failure — the class may not exist in this WhatsApp build, or may fail to
     * link. Callers must treat null as "skip this candidate", never as fatal.
     */
    fun loadClass(name: String): Class<*>? = try {
        Class.forName(name, false, classLoader)
    } catch (t: Throwable) {
        null
    }

    /**
     * Scan the declared methods of a single [clazz] and return the first one satisfying [predicate],
     * or null. Uses [Class.getDeclaredMethods] so private/obfuscated handlers are visible. All
     * reflection is guarded: a class that fails to enumerate its methods yields null, not a crash.
     */
    fun firstMatchIn(clazz: Class<*>, predicate: MethodPredicate): Method? = try {
        clazz.declaredMethods.firstOrNull { method ->
            try {
                predicate.matches(method)
            } catch (t: Throwable) {
                false
            }
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * Scan a whole candidate class set (by obfuscated name) and return the first matching [Method]
     * across all of them, or null if none match. This is the top-level entry the hook calls.
     *
     * @param candidateClassNames obfuscated class names located via jadx for the CURRENT version.
     *   Keeping this list small and targeted is what makes the scan cheap; do not brute-force the
     *   whole APK. Empty list ⇒ null (the inert default until the per-version RE is done).
     */
    fun firstMatch(candidateClassNames: List<String>, predicate: MethodPredicate): Method? {
        for (name in candidateClassNames) {
            val clazz = loadClass(name) ?: continue
            val hit = firstMatchIn(clazz, predicate)
            if (hit != null) {
                XposedBridge.log("QuietPing: revoke matcher resolved ${clazz.name}#${hit.name}")
                return hit
            }
        }
        return null
    }

    private fun safeParamMatch(predicate: ParamPredicate, type: Class<*>): Boolean = try {
        predicate.matches(type)
    } catch (t: Throwable) {
        false
    }
}
