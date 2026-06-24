package com.quietping.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.repo.MessageRepository
import com.quietping.domain.settings.PrivacySettings
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The retention-window options offered in the UI, in days. */
val RETENTION_OPTIONS: List<Int> = listOf(7, 14, 30, 60, 90, 365)

/**
 * Transient feedback after a manual purge, surfaced as a one-shot banner. The screen
 * acknowledges it via [PrivacyLockViewModel.consumePurgeResult].
 */
sealed interface PurgeResult {
    data object Running : PurgeResult
    data object Done : PurgeResult
}

/**
 * UI state for Privacy & lock.
 *
 * @param privacy     persisted privacy settings (biometric lock, retention days).
 * @param purgeResult transient purge feedback, or null when idle.
 */
data class PrivacyLockUiState(
    val privacy: PrivacySettings = PrivacySettings(),
    val purgeResult: PurgeResult? = null
)

/**
 * Drives Privacy & lock settings (PRD §11): biometric app-lock toggle, the auto-purge
 * retention window, and a "Purge now" action that drops everything currently held.
 * Export / cloud sync is intentionally impossible (no INTERNET permission) and the
 * screen states this; this VM exposes no such capability.
 */
@HiltViewModel
class PrivacyLockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val purgeResult = MutableStateFlow<PurgeResult?>(null)

    val uiState: StateFlow<PrivacyLockUiState> =
        combine(settingsRepository.privacy, purgeResult) { privacy, purge ->
            PrivacyLockUiState(privacy = privacy, purgeResult = purge)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PrivacyLockUiState()
        )

    /** Toggle the biometric app lock. */
    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.privacy
            settingsRepository.setPrivacy(current.copy(biometricLock = enabled))
        }
    }

    /** Set the auto-purge retention window in [days]. */
    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            val current = uiState.value.privacy
            settingsRepository.setPrivacy(current.copy(retentionDays = days))
        }
    }

    /**
     * Purge everything captured before now — an immediate, total clear of the vault
     * (PRD §11 "purge now"). Surfaces [PurgeResult] for confirmation feedback.
     */
    fun purgeNow() {
        if (purgeResult.value == PurgeResult.Running) return
        purgeResult.update { PurgeResult.Running }
        viewModelScope.launch {
            messageRepository.purgeOlderThan(System.currentTimeMillis())
            purgeResult.update { PurgeResult.Done }
        }
    }

    /** Acknowledge and clear the transient purge feedback. */
    fun consumePurgeResult() {
        purgeResult.update { null }
    }
}
