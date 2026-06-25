package com.quietping.data.repo

import android.content.Context
import com.quietping.capture.MediaVault
import com.quietping.domain.model.MediaItem
import com.quietping.domain.repo.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the private [MediaVault] directory and exposes it as a flat [MediaItem]
 * gallery for the UI.
 *
 * The vault is plain files on disk with no change-notification stream, so this
 * repository emits a fresh snapshot whenever [refresh] is pulsed (the gallery
 * screen does so on open). Each [MediaVault] file is named
 * `${appLabel}_${mediaId}_${displayName}` by [com.quietping.capture.MediaObserver];
 * [parse] recovers the app label and original display name from that shape, failing
 * safe to "" / the raw file name when it doesn't match. All disk work runs on
 * [Dispatchers.IO]; the capture layer's no-`INTERNET` guarantee is preserved since
 * nothing here touches the network.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    /** Bumped by [refresh] to re-trigger a vault re-scan downstream. */
    private val refreshTrigger = MutableStateFlow(0)

    override fun media(): Flow<List<MediaItem>> =
        refreshTrigger
            .map { scan() }
            .flowOn(Dispatchers.IO)

    override suspend fun refresh() {
        refreshTrigger.value += 1
    }

    /** Snapshot the vault directory into ordered [MediaItem]s (newest first). */
    private fun scan(): List<MediaItem> =
        MediaVault.list(context)
            .filter { it.isFile && it.length() > 0L }
            .map { it.toMediaItem() }
            .sortedByDescending { it.lastModified }

    private fun File.toMediaItem(): MediaItem {
        val (appLabel, displayName) = parse(name)
        return MediaItem(
            path = absolutePath,
            appLabel = appLabel,
            displayName = displayName,
            sizeBytes = length(),
            lastModified = lastModified()
        )
    }

    /**
     * Recover (appLabel, displayName) from a `${appLabel}_${mediaId}_${displayName}`
     * file name. The id segment is digits, so split on the first two underscores and
     * verify the middle token is numeric; otherwise treat the whole name as the
     * display name with an empty label (fail safe — never throw on a hostile name).
     */
    private fun parse(fileName: String): Pair<String, String> {
        val parts = fileName.split("_", limit = 3)
        return if (parts.size == 3 && parts[1].toLongOrNull() != null) {
            parts[0] to parts[2]
        } else {
            "" to fileName
        }
    }
}
