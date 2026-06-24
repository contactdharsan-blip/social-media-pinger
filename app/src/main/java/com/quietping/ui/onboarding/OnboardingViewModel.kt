package com.quietping.ui.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The discrete permission steps of onboarding (PRD §7). Notification access and
 * DND are core; SMS and Accessibility are explicitly optional and skippable — the
 * app degrades to the features each grant unlocks rather than blocking.
 *
 * [optional] marks steps the user may skip without losing core functionality.
 */
enum class OnboardingStep(val optional: Boolean) {
    /** Bind the NotificationListenerService — the primary capture source. */
    NOTIFICATION_ACCESS(optional = false),

    /** READ_SMS runtime grant for the SMS/MMS provider (V2 deep features). */
    SMS_ACCESS(optional = true),

    /** ACCESS_NOTIFICATION_POLICY so VIP/name alerts can bypass Do Not Disturb. */
    DND_ACCESS(optional = false),

    /** Opt-in AccessibilityService for wider live capture coverage. */
    ACCESSIBILITY_ACCESS(optional = true);

    companion object {
        /** Steps in presentation order. */
        val ordered: List<OnboardingStep> = entries.toList()
    }
}

/**
 * UI state for the stepped onboarding flow.
 *
 * @param stepIndex        index into [OnboardingStep.ordered] of the visible step.
 * @param granted          per-step grant status, refreshed from system on resume.
 * @param notificationDeniedOnce whether the user explicitly skipped the required
 *                         notification step at least once (drives gentle nudge copy).
 */
data class OnboardingUiState(
    val stepIndex: Int = 0,
    val granted: Map<OnboardingStep, Boolean> = emptyMap(),
    val notificationDeniedOnce: Boolean = false
) {
    /** The currently visible step. */
    val currentStep: OnboardingStep
        get() = OnboardingStep.ordered[stepIndex.coerceIn(0, OnboardingStep.ordered.lastIndex)]

    /** Total number of steps (for the progress indicator). */
    val stepCount: Int get() = OnboardingStep.ordered.size

    /** Whether this is the final step (the CTA becomes "Done"). */
    val isLastStep: Boolean get() = stepIndex >= OnboardingStep.ordered.lastIndex

    /** Whether the currently visible step has been granted. */
    val isCurrentGranted: Boolean get() = granted[currentStep] == true
}

/**
 * Drives the onboarding wizard. The ViewModel owns *which* step is active and the
 * latest known grant state; the screen owns the Android side-effects (launching
 * system settings intents and the READ_SMS runtime request) because those need an
 * Activity context. The screen pushes fresh grant booleans back via
 * [updateGrant]/[refreshGrants] whenever the user returns from a system screen.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** Advance to the next step, clamped to the last step. */
    fun next() {
        _uiState.update { s ->
            s.copy(stepIndex = (s.stepIndex + 1).coerceAtMost(OnboardingStep.ordered.lastIndex))
        }
    }

    /** Go back to the previous step, clamped to the first step. */
    fun back() {
        _uiState.update { s -> s.copy(stepIndex = (s.stepIndex - 1).coerceAtLeast(0)) }
    }

    /**
     * Skip the current step. Optional steps simply advance; skipping the required
     * notification step is permitted (graceful degradation) but recorded so the UI
     * can show a soft reminder.
     */
    fun skip() {
        _uiState.update { s ->
            val deniedOnce = s.notificationDeniedOnce ||
                (s.currentStep == OnboardingStep.NOTIFICATION_ACCESS && !s.isCurrentGranted)
            s.copy(
                stepIndex = (s.stepIndex + 1).coerceAtMost(OnboardingStep.ordered.lastIndex),
                notificationDeniedOnce = deniedOnce
            )
        }
    }

    /** Record the grant status of a single [step] (called after a system return). */
    fun updateGrant(step: OnboardingStep, isGranted: Boolean) {
        _uiState.update { s -> s.copy(granted = s.granted + (step to isGranted)) }
    }

    /** Bulk-refresh grant status for all steps (called on screen resume). */
    fun refreshGrants(values: Map<OnboardingStep, Boolean>) {
        _uiState.update { s -> s.copy(granted = s.granted + values) }
    }
}
