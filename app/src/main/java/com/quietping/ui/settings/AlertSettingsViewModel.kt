package com.quietping.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.model.SoundPreset
import com.quietping.domain.model.TriggerType
import com.quietping.domain.repo.RuleRepository
import com.quietping.domain.settings.AlertPrefs
import com.quietping.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The default sound preset each [TriggerType] is seeded with, mirroring the PRD §8
 * "default use" column. Used when no rule of that type yet exists.
 */
internal fun TriggerType.defaultPreset(): SoundPreset = when (this) {
    TriggerType.NAME_MENTION -> SoundPreset.PULSE
    TriggerType.POLL_CREATED -> SoundPreset.CHIME
    TriggerType.KEYWORD -> SoundPreset.DROPLET
    TriggerType.REPLY_MENTION -> SoundPreset.GLASS_TAP
    TriggerType.VIP_CONTACT -> SoundPreset.SONAR
    TriggerType.GROUP_EVENT -> SoundPreset.WHISPER
}

/** Whether this condition bypasses Do Not Disturb by default (urgent conditions). */
internal fun TriggerType.defaultDndOverride(): Boolean =
    this == TriggerType.NAME_MENTION || this == TriggerType.VIP_CONTACT

/** Human-readable label for a trigger condition. */
internal fun TriggerType.displayName(): String = when (this) {
    TriggerType.NAME_MENTION -> "Name mentioned"
    TriggerType.POLL_CREATED -> "Poll created"
    TriggerType.KEYWORD -> "Keyword match"
    TriggerType.REPLY_MENTION -> "Reply / @mention"
    TriggerType.VIP_CONTACT -> "VIP contact"
    TriggerType.GROUP_EVENT -> "Group event"
}

/** One short sentence describing what this condition fires on. */
internal fun TriggerType.description(): String = when (this) {
    TriggerType.NAME_MENTION -> "Your name or handle appears in a message."
    TriggerType.POLL_CREATED -> "A new poll drops in any watched chat."
    TriggerType.KEYWORD -> "A term you defined is mentioned."
    TriggerType.REPLY_MENTION -> "Someone replies to or @-mentions you."
    TriggerType.VIP_CONTACT -> "A starred contact messages you."
    TriggerType.GROUP_EVENT -> "Added to a group, admin change, or call."
}

/**
 * Per-condition alert configuration shown on the Alert settings screen.
 *
 * [soundPreset] and [dndOverride] are persisted onto every [com.quietping.domain.model.Rule]
 * of [triggerType] (the AlertDispatcher reads them off the rule). [vibrate] is a UI
 * affordance — the SILENT preset is vibrate-only by definition, so SILENT ⇒ the alert
 * is vibrate-only; any other preset plays its paired vibration when [vibrate] is on.
 * [ruleCount] is how many rules of this type exist (0 ⇒ the choice is held in memory
 * and applies as soon as a rule of this type is created in the Rules screen).
 */
data class AlertConfig(
    val triggerType: TriggerType,
    val soundPreset: SoundPreset,
    val dndOverride: Boolean,
    val vibrate: Boolean,
    val ruleCount: Int
) {
    /** True when there is no backing rule, so changes are pending. */
    val isPending: Boolean get() = ruleCount == 0
}

/** UI state: one [AlertConfig] per trigger type, plus the global alert prefs. */
data class AlertSettingsUiState(
    val configs: List<AlertConfig> = emptyList(),
    val alerts: AlertPrefs = AlertPrefs(),
    val errorMessage: String? = null
)

/**
 * Drives the per-condition Alert settings (PRD §8). Reads all rules reactively,
 * groups them by [TriggerType], and exposes one config per type.
 *
 * Edits update an in-memory [overrides] map immediately (so the UI is responsive and
 * the no-rule case stays editable) and are written through to every existing rule of
 * that type via [RuleRepository.upsert]. The rendered config layers [overrides] over
 * the rule-derived baseline.
 */
@HiltViewModel
class AlertSettingsViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** User edits not (yet) reflected by the reactive rule stream. */
    private val overrides = MutableStateFlow<Map<TriggerType, AlertConfig>>(emptyMap())

    val uiState: StateFlow<AlertSettingsUiState> =
        combine(ruleRepository.rules(), overrides, settingsRepository.alerts) { rules, edits, alerts ->
            val byType = rules.groupBy { it.type }
            val configs = TriggerType.entries.map { type ->
                val baseline = baselineFor(type, byType[type].orEmpty().let { ofType ->
                    if (ofType.isEmpty()) null else AlertConfig(
                        triggerType = type,
                        soundPreset = ofType.first().soundPreset,
                        dndOverride = ofType.any { it.dndOverride },
                        vibrate = ofType.first().soundPreset != SoundPreset.SILENT,
                        ruleCount = ofType.size
                    )
                })
                // An override wins only until the rule stream confirms the same value.
                edits[type]?.copy(ruleCount = baseline.ruleCount) ?: baseline
            }
            AlertSettingsUiState(configs = configs, alerts = alerts)
        }.catch {
            // A failed Room/DataStore read shouldn't cancel the scope or strand the UI;
            // surface the default config plus an error note instead.
            emit(
                AlertSettingsUiState(
                    configs = TriggerType.entries.map { baselineFor(it, null) },
                    errorMessage = "Couldn't load alert settings."
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlertSettingsUiState(
                configs = TriggerType.entries.map { baselineFor(it, null) }
            )
        )

    /** The rule-derived config, or the PRD default when [derived] is null. */
    private fun baselineFor(type: TriggerType, derived: AlertConfig?): AlertConfig =
        derived ?: AlertConfig(
            triggerType = type,
            soundPreset = type.defaultPreset(),
            dndOverride = type.defaultDndOverride(),
            vibrate = type.defaultPreset() != SoundPreset.SILENT,
            ruleCount = 0
        )

    /** Set the sound preset for [type] across all its rules. */
    fun setSoundPreset(type: TriggerType, preset: SoundPreset) {
        record(type) { it.copy(soundPreset = preset, vibrate = preset != SoundPreset.SILENT) }
        writeThrough(type) { it.copy(soundPreset = preset) }
    }

    /** Toggle DND override for [type] across all its rules. */
    fun setDndOverride(type: TriggerType, enabled: Boolean) {
        record(type) { it.copy(dndOverride = enabled) }
        writeThrough(type) { it.copy(dndOverride = enabled) }
    }

    /**
     * Toggle vibration for [type]. There is no vibration field in the domain, so
     * this is reflected in UI state only; the SILENT preset already encodes the
     * vibrate-only case and a sounding preset carries its paired pattern.
     */
    fun setVibrate(type: TriggerType, enabled: Boolean) {
        record(type) { it.copy(vibrate = enabled) }
    }

    /** Toggle the once-daily digest of low-priority matches. */
    fun setDigestEnabled(enabled: Boolean) = updateAlerts { it.copy(digestEnabled = enabled) }

    /** Set the digest delivery hour (0..23). */
    fun setDigestHour(hour: Int) = updateAlerts { it.copy(digestHour = hour.coerceIn(0, 23)) }

    /** Set the OTP auto-delete window in hours (0 ⇒ off). */
    fun setOtpAutoDeleteHours(hours: Int) = updateAlerts { it.copy(otpAutoDeleteHours = hours.coerceAtLeast(0)) }

    private inline fun updateAlerts(crossinline transform: (AlertPrefs) -> AlertPrefs) {
        viewModelScope.launch {
            settingsRepository.setAlerts(transform(uiState.value.alerts))
        }
    }

    /** Merge an edit into the in-memory override map. */
    private fun record(type: TriggerType, edit: (AlertConfig) -> AlertConfig) {
        overrides.update { map ->
            val base = map[type] ?: currentConfig(type)
            map + (type to edit(base))
        }
    }

    /** Apply [transform] to every existing rule of [type] (no-op when none exist). */
    private fun writeThrough(
        type: TriggerType,
        transform: (com.quietping.domain.model.Rule) -> com.quietping.domain.model.Rule
    ) {
        viewModelScope.launch {
            ruleRepository.rules().first()
                .filter { it.type == type }
                .forEach { ruleRepository.upsert(transform(it)) }
        }
    }

    private fun currentConfig(type: TriggerType): AlertConfig =
        uiState.value.configs.firstOrNull { it.triggerType == type }
            ?: baselineFor(type, null)
}
