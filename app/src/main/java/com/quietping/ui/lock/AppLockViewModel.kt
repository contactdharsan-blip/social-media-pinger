package com.quietping.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.repo.BreakInRepository
import com.quietping.domain.security.DecoyMode
import com.quietping.domain.security.PinHasher
import com.quietping.domain.settings.PrivacySettings
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
 * @param lockEnabled    whether the user has enabled biometric lock in settings.
 * @param decoyAvailable whether a decoy PIN is configured (offer the PIN entry).
 * @param phase          current authentication phase (drives retry/message UI).
 * @param errorText      user-facing message for the current [AuthPhase.ERROR], if any.
 */
data class AppLockUiState(
    val lockEnabled: Boolean = false,
    val decoyAvailable: Boolean = false,
    val phase: AuthPhase = AuthPhase.IDLE,
    val errorText: String? = null
)

/**
 * ViewModel for the AppLock screen. It owns lock *policy* and phase state; the actual
 * BiometricPrompt is shown by the screen (it needs an Activity host). The screen
 * reports results back via [onAuthSucceeded] / [onAuthError] / [onAuthFailed] /
 * [onPromptShown], and submits a typed PIN via [submitPin].
 *
 * Two privacy features ride here:
 *  - failed unlock attempts are recorded to the [BreakInRepository] when the user has
 *    enabled the break-in log;
 *  - a matching decoy PIN enters [DecoyMode] (an empty-vault session) instead of the
 *    real unlock — a coercion escape hatch.
 */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val breakInRepository: BreakInRepository
) : ViewModel() {

    private val privacy: StateFlow<PrivacySettings> =
        settingsRepository.privacy.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PrivacySettings()
        )

    private val phase = MutableStateFlow(AuthPhase.IDLE)
    private val errorText = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppLockUiState> =
        combine(privacy, phase, errorText) { p, ph, err ->
            AppLockUiState(
                lockEnabled = p.biometricLock,
                decoyAvailable = p.decoyPinEnabled && p.decoyPinHash.isNotBlank(),
                phase = ph,
                errorText = err
            )
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

    /** The biometric check passed — a genuine (non-decoy) unlock. */
    fun onAuthSucceeded() {
        DecoyMode.clear()
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

    /** A single bad biometric read (wrong finger/face) — record as a break-in. */
    fun onAuthFailed() {
        recordBreakIn("biometric_failed")
    }

    /**
     * Validate a typed [pin] against the configured decoy PIN. On match, enter
     * [DecoyMode] and signal success (the empty-vault session); on mismatch, record a
     * break-in and surface an error.
     */
    fun submitPin(pin: String) {
        viewModelScope.launch {
            val p = settingsRepository.privacy.first()
            if (p.decoyPinEnabled && PinHasher.verify(pin.trim(), p.decoyPinHash)) {
                DecoyMode.enter()
                errorText.value = null
                phase.value = AuthPhase.SUCCESS
            } else {
                recordBreakIn("pin_failed")
                onAuthError("Incorrect PIN.")
            }
        }
    }

    /** Reset to idle so the user can trigger another attempt. */
    fun resetToIdle() {
        phase.value = AuthPhase.IDLE
    }

    private fun recordBreakIn(reason: String) {
        viewModelScope.launch {
            if (privacy.value.breakInLogEnabled) {
                breakInRepository.record(reason)
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
