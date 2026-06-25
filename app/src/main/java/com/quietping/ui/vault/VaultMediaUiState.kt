package com.quietping.ui.vault

import com.quietping.domain.model.MediaItem

/**
 * Immutable UI state for [VaultMediaScreen] — the flat captured-media gallery.
 *
 * [items] is rendered verbatim as a thumbnail grid. [isLoading] is true only until
 * the first repository emission (gating the shimmer); [isEmpty] distinguishes
 * "nothing captured yet" once loading has settled.
 *
 * @param items     the captured media files, most-recent first.
 * @param isLoading true until the first emission arrives.
 */
data class VaultMediaUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true
) {
    /** No media to show once the first scan has completed. */
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
