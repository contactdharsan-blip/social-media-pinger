package com.quietping.xposed

import android.content.ContentValues
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * WhatsApp block-revoke via the durable SQLite storage layer ([SqliteWriteHook]).
 *
 * WhatsApp processes "delete for everyone" as an UPDATE on the message row, flipping it into a
 * revoked state (type/status changed, text cleared). Block-revoke = neutralise that UPDATE so the
 * original content row survives and WhatsApp's own renderer keeps drawing the real bubble.
 *
 * The SQLite hook always attaches (framework class, never obfuscated). The only per-version unknown
 * — the exact revoke write shape — is isolated in [looksLikeRevoke]. See WHATSAPP_RE_PROCEDURE.md.
 */
object WhatsAppDbRevokeStrategy {

    /**
     * Flip to `true` ONLY after confirming, from the OBSERVE logs on a real device, that
     * [looksLikeRevoke] correctly identifies the revoke UPDATE for the WhatsApp build in use.
     * Off by default — observe-only, never mutates the DB.
     */
    private const val REVOKE_SCHEMA_VALIDATED = false

    /** WhatsApp message table across schema generations (modern `message`, legacy `messages`). */
    private val MESSAGE_TABLES = setOf("message", "messages")

    fun install(lpparam: XC_LoadPackage.LoadPackageParam): Boolean = SqliteWriteHook.install(
        appLabel = "WhatsApp",
        appPackage = lpparam.packageName,
        tables = MESSAGE_TABLES,
        validated = REVOKE_SCHEMA_VALIDATED,
        recover = true,
        detector = { op, _, values, _ ->
            op == SqliteWriteHook.Op.UPDATE && looksLikeRevoke(values)
        },
    )

    /**
     * Per-version schema knob: does this write look like WhatsApp marking a message revoked? The
     * exact column/value differs across msgstore schema versions, so this is the one place to tune
     * (using the OBSERVE logs as evidence). It must stay CONSERVATIVE: return true only for a write
     * that is, with high confidence, the revoke marker — any doubt returns false so the original
     * write proceeds. Default (a starting point, NOT validated): a revoke write touches the message
     * type/status. Tighten with the confirmed value once known.
     */
    private fun looksLikeRevoke(values: ContentValues?): Boolean {
        if (values == null) return false
        val cols = values.keySet().map { it.lowercase() }
        val touchesType = cols.any { it == "message_type" || it == "media_wa_type" || it.endsWith("_type") }
        val touchesStatus = cols.any { it == "status" || it == "revoked" || it.contains("revoke") }
        return touchesType && touchesStatus
    }
}
