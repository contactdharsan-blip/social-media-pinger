package com.quietping.data.db

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the SQLCipher passphrase for [AppDatabase].
 *
 * Strategy (matches the PRD's "Keystore-wrapped key"): a random 256-bit passphrase
 * is generated once on first launch and stored — encrypted — in a private prefs
 * file. The encryption key never leaves the hardware-backed Android Keystore, so the
 * passphrase at rest is useless without the device's Keystore. Decryption requires
 * no user authentication so capture services can open the vault in the background.
 *
 * Fully on-device; no network, no plaintext key persisted.
 */
object DbKey {

    private const val PREFS_NAME = "quietping_db_key"
    private const val KEY_CIPHERTEXT = "passphrase_ct"
    private const val KEY_IV = "passphrase_iv"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "quietping_db_master"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    private val lock = Any()

    /**
     * Return the raw SQLCipher passphrase bytes, generating and persisting them on
     * first call. Subsequent calls decrypt and return the same value. If the stored
     * material can no longer be decrypted (e.g. the Keystore key was invalidated),
     * a fresh passphrase is generated — callers must treat that as a new, empty DB.
     *
     * The returned array is a fresh copy each call; callers may zero it after use.
     */
    fun passphrase(context: Context): ByteArray = synchronized(lock) {
        val prefs = prefs(context)
        val existing = readEncrypted(prefs)
        if (existing != null) {
            decryptOrNull(existing)?.let { return it }
        }
        // No usable passphrase on disk (first run, or undecryptable) — mint a new one.
        return generateAndStore(prefs)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private data class Encrypted(val iv: ByteArray, val ciphertext: ByteArray)

    private fun readEncrypted(prefs: SharedPreferences): Encrypted? {
        val ctB64 = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        return try {
            Encrypted(
                iv = Base64.decode(ivB64, Base64.NO_WRAP),
                ciphertext = Base64.decode(ctB64, Base64.NO_WRAP)
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun generateAndStore(prefs: SharedPreferences): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey) }
        val ciphertext = cipher.doFinal(passphrase)
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun decryptOrNull(encrypted: Encrypted): ByteArray? = try {
        val secretKey = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, encrypted.iv))
        }
        cipher.doFinal(encrypted.ciphertext)
    } catch (_: Exception) {
        // Key invalidated / material tampered — signal "no usable passphrase".
        null
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
