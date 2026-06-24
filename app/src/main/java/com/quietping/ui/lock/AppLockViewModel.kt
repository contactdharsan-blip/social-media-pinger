package com.quietping.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The outcome of a biometric attempt, surfaced to the screen for messaging.
 */
enum class AuthPhase {
    /** Idle / awaiting the user to start (or auto-prompt) authentication. */
    IDLE,

    /** A prompt is currently being shown. */
    PROMPTING,

    /** Auth failed or was cancelled; the user can retry. */
    ERROR,

    /** Auth succeeded; the screen should navigate onward. */
    SUCCESS
}

/**
 * UI state for the biometric app-lock gate.
 *
 * @param lockEnabled whether the user has enabled biometric lock in settings.
 * @param phase       current authentication phase (drives retry/message UI).
 * @param errorText   user-facing message for the current [AuthPhase.ERROR], if any.
 */
data class AppLockUiState(
    val lockEnabled: Boolean = false,
    val phase: AuthPhase = AuthPhase.IDLE,
    val errorText: String? = null
)

/**
 * ViewModel for the AppLock screen. It only owns lock *policy* and phase state;
 * the actual BiometricPrompt is shown by the screen (it needs an Activity host).
 * The screen reports results back via [onAuthSucceeded] / [onAuthError] /
 * [onPromptShown].
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val lockEnabled: StateFlow<Boolean> =
        settingsRepository.privacy
            .map { it.biometricLock }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = false
            )

    private val phase = MutableStateFlow(AuthPhase.IDLE)
    private val errorText = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppLockUiState> =
        combine(lockEnabled, phase, errorText) { enabled, ph, err ->
            AppLockUiState(lockEnabled = enabled, phase = ph, errorText = err)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppLockUiState()
        )

    /** Mark that a prompt is now visible. */
    fun onPromptShown() {
        phase.value = AuthPhase.PROMPTING
        errorText.value = null
    }

    /** The biometric check passed. */
    fun onAuthSucceeded() {
        errorText.value = null
        phase.value = AuthPhase.SUCCESS
    }

    /**
     * The biometric check failed, errored, or was cancelled. [message] is the
     * system-provided (or our fallback) explanation; null clears it.
     */
    fun onAuthError(message: String?) {
        errorText.value = message
        phase.value = AuthPhase.ERROR
    }

    /** Reset to idle so the user can trigger another attempt. */
    fun resetToIdle() {
        phase.value = AuthPhase.IDLE
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
