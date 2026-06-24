package com.quietping.domain.vault

import com.quietping.domain.model.AppPackage

/**
 * Stable identity keys for Vault dedupe and edit detection.
 *
 * Two keys are derived from a message's invariant coordinates:
 *  - [editKey] — `app + conversationKey + sender + postedAt`. Invariant across an
 *    edit (the body is excluded), so two captures sharing this key are the *same
 *    logical message* and a body change between them is an edit, not a new row.
 *  - [contentKey] — `editKey + bodyHash`. Changes when the body changes, so an
 *    identical re-delivery collides on it (⇒ exact duplicate, drop) while an edit
 *    does not (⇒ append a version).
 *
 * Keys are hex SHA-256 over a delimiter-joined tuple; the delimiter is a control
 * char (U+0001) that cannot appear in the inputs, so fields can never bleed into
 * each other. Pure and allocation-light — safe to call on the ingestion path.
 */
object StableKey {

    /** U+0001: never present in package ids, handles, or bodies ⇒ safe separator. */
    private const val SEP = '\u0001'

    /** Identity that survives an edit (body excluded). */
    fun editKey(
        appPackage: AppPackage,
        conversationKey: String,
        sender: String,
        postedAt: Long
    ): String = sha256(
        buildString {
            append(appPackage.packageId); append(SEP)
            append(conversationKey); append(SEP)
            append(sender.trim().lowercase()); append(SEP)
            append(postedAt)
        }
    )

    /** Identity that changes with the body (edit excluded ⇒ collision = duplicate). */
    fun contentKey(
        appPackage: AppPackage,
        conversationKey: String,
        sender: String,
        postedAt: Long,
        body: String
    ): String = sha256(
        buildString {
            append(appPackage.packageId); append(SEP)
            append(conversationKey); append(SEP)
            append(sender.trim().lowercase()); append(SEP)
            append(postedAt); append(SEP)
            append(body.trim())
        }
    )

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
