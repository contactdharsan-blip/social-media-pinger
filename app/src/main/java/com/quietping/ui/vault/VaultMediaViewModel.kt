package com.quietping.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.repo.MediaRepository
import com.quietping.domain.security.DecoyMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
            .map { items ->
                // Decoy session (unlocked with the decoy PIN): reveal an empty gallery.
                val visible = if (DecoyMode.isActive) emptyList() else items
                VaultMediaUiState(items = visible, isLoading = false)
            }
            .catch {
                emit(VaultMediaUiState(isLoading = false, errorMessage = "Couldn't load media"))
            }
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
