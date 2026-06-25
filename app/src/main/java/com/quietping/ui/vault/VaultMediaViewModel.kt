package com.quietping.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.repo.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [VaultMediaScreen]: the on-device captured-media gallery.
 *
 * Reads the single reactive stream [MediaRepository.media] and wraps each snapshot
 * in a [VaultMediaUiState]. The vault is plain files with no change notifications,
 * so the screen's content is refreshed by pulsing [MediaRepository.refresh] on init
 * (and again whenever the screen calls [refresh]).
 */
@HiltViewModel
class VaultMediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val uiState: StateFlow<VaultMediaUiState> =
        mediaRepository.media()
            .map { items -> VaultMediaUiState(items = items, isLoading = false) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VaultMediaUiState()
            )

    init {
        refresh()
    }

    /** Re-scan the vault directory (on screen open / pull-to-refresh affordance). */
    fun refresh() {
        viewModelScope.launch { mediaRepository.refresh() }
    }
}
