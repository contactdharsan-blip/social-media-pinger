package com.quietping.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.data.repo.DeepCaptureTarget
import com.quietping.data.repo.RootGateRepository
import com.quietping.root.RootManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Why deep capture can't be offered on this device. */
enum class DeepCaptureUnavailableReason {
    /** The device is not rooted. */
    NOT_ROOTED,

    /** Rooted, but the LSPosed framework that runs the module isn't active. */
    LSPOSED_INACTIVE
}

/**
 * UI state for Deep capture (root).
 *
 * @param loading    true until the root/LSPosed probe + gate read complete.
 * @param available  true only when the device is rooted AND LSPosed is active.
 * @param reason     populated when [available] is false, to explain why.
 * @param targets    the deep-capture target apps (package + label).
 * @param enabled    per-package enabled flags, keyed by package name.
 * @param errorMessage set when probing root/LSPosed or reading the gate fails.
 */
data class DeepCaptureUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val reason: DeepCaptureUnavailableReason? = null,
    val targets: List<DeepCaptureTarget> = emptyList(),
    val enabled: Map<String, Boolean> = emptyMap(),
    val errorMessage: String? = null
)

/**
 * Drives Deep capture (root) settings (FACE2_XPOSED_RD.md). On init it probes
 * [RootManager.status] off the main thread; only when the device is both rooted and
 * running an active LSPosed framework does it expose the per-app toggles — otherwise it
 * surfaces an "unavailable" state with the reason so the screen can explain instead of
 * offering switches that would silently no-op. Toggles write through [RootGateRepository].
 */
@HiltViewModel
class DeepCaptureViewModel @Inject constructor(
    private val rootManager: RootManager,
    private val gateRepository: RootGateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeepCaptureUiState(targets = gateRepository.targets)
    )
    val uiState: StateFlow<DeepCaptureUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Re-probe root/LSPosed status and reload the gate flags. */
    fun refresh() {
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val status = rootManager.status()
                val flags = withContext(Dispatchers.IO) { gateRepository.enabledFlags() }
                status to flags
            }.onSuccess { (status, flags) ->
                val reason = when {
                    !status.rooted -> DeepCaptureUnavailableReason.NOT_ROOTED
                    !status.lsposedActive -> DeepCaptureUnavailableReason.LSPOSED_INACTIVE
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        available = reason == null,
                        reason = reason,
                        enabled = flags
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        loading = false,
                        available = false,
                        errorMessage = "Couldn't check deep-capture availability."
                    )
                }
            }
        }
    }

    /** Enable/disable deep capture for [packageName]. */
    fun setEnabled(packageName: String, enabled: Boolean) {
        if (!_uiState.value.available) return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                gateRepository.setEnabled(packageName, enabled)
            }
            if (ok) {
                _uiState.update { state ->
                    state.copy(enabled = state.enabled + (packageName to enabled))
                }
            }
        }
    }
}
