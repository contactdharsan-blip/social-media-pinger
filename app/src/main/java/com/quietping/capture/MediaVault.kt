package com.quietping.capture

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * On-device store for media (images / video / voice notes) captured from
 * messaging apps before a "delete for everyone" can remove the original.
 *
 * Files are copied into the app's **private** internal storage
 * (`filesDir/media_vault/`). That directory is inside the app sandbox — not
 * world-readable, not indexed by MediaStore, and (consistent with QuietPing's
 * no-`INTERNET` guarantee) can never be exfiltrated off-device. Deleting QuietPing
 * deletes the vault with it.
 *
 * This class is intentionally storage-only: it does not parse, match, or notify.
 * [MediaObserver] decides *what* to copy; the Vault decides *where* and dedupes by
 * destination filename so the same source is never copied twice.
 */
internal object MediaVault {

    private const val TAG = "MediaVault"
    private const val DIR_NAME = "media_vault"

    /** Resolve (and create) the private vault directory. */
    fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * Copy the content at [source] into the vault, tagged with its [appLabel]
     * (e.g. "whatsapp") and original [displayName]. [mediaId] is the MediaStore row
     * id, used to build a stable, collision-free destination name so re-observing
     * the same row is a cheap no-op. Returns the destination [File], or null when
     * the source can't be read (already deleted, permission lost) — failure is
     * logged at debug and swallowed (fail safe; never crash capture).
     */
    fun capture(
        context: Context,
        source: Uri,
        appLabel: String,
        mediaId: Long,
        displayName: String?,
    ): File? {
        val safeName = sanitize(displayName ?: "media")
        val dest = File(dir(context), "${appLabel}_${mediaId}_$safeName")
        if (dest.exists() && dest.length() > 0L) return dest // already captured

        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                Log.d(TAG, "No stream for $source (gone before capture?)")
                null
            }
            dest.takeIf { it.exists() && it.length() > 0L }
        } catch (e: SecurityException) {
            Log.d(TAG, "Media read permission missing; skipping $source")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture media $source", e)
            dest.delete().let { null } // remove any partial write
        }
    }

    /** All currently-captured media files (most useful for a future Vault media tab). */
    fun list(context: Context): List<File> =
        dir(context).listFiles()?.toList().orEmpty()

    /** Strip path separators and clamp length so a hostile display name is inert. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifEmpty { "media" }
}
