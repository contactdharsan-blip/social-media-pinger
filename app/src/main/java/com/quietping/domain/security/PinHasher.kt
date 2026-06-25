package com.quietping.domain.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Salted SHA-256 hashing for the decoy PIN. We never persist the raw PIN — only a
 * `salt:hash` hex string — so a vault dump can't reveal it.
 *
 * Pure and dependency-free (so it is unit-testable). The salt makes two identical
 * PINs hash differently and defeats precomputed-table lookups; iteration count is
 * modest by design — a 4–8 digit PIN has tiny entropy, so this is obfuscation of an
 * at-rest value, not a defense against an offline brute force (the real lock is
 * biometric; the decoy PIN is a coercion escape hatch, not the primary secret).
 */
object PinHasher {

    private const val SEP = ":"

    /** Produce a `salt:hash` string for [pin]. */
    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val digest = digest(pin, salt)
        return salt.toHex() + SEP + digest.toHex()
    }

    /** True when [pin] matches a previously [hash]ed [stored] value. */
    fun verify(pin: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        val parts = stored.split(SEP)
        if (parts.size != 2) return false
        val salt = parts[0].fromHex() ?: return false
        val expected = parts[1]
        return constantTimeEquals(digest(pin, salt).toHex(), expected)
    }

    private fun digest(pin: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        var out = md.digest()
        repeat(ITERATIONS - 1) {
            md.reset()
            out = md.digest(out)
        }
        return out
    }

    /** Length-stable comparison so verification time doesn't leak match position. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    private const val SALT_BYTES = 16
    private const val ITERATIONS = 1000
}
