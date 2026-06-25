package com.quietping.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietping.domain.icon.IconSwitcher
import com.quietping.domain.settings.SettingsRepository
import com.quietping.domain.settings.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Selectable accent presets for the appearance picker. Each is a #RRGGBB hex string
 * matching the DESIGN.md emerald/teal family plus a few same-energy alternates that
 * still read well on the near-black canvas.
 */
enum class AccentPreset(val displayName: String, val hex: String) {
    EMERALD("Emerald", "#34D399"),
    TEAL("Teal", "#2DD4BF"),
    MINT("Mint", "#6EE7B7"),
    SKY("Sky", "#38BDF8"),
    VIOLET("Violet", "#A78BFA"),
    ROSE("Rose", "#FB7185"),
    AMBER("Amber", "#FBBF24"),
    LIME("Lime", "#A3E635");

    companion object {
        /** The preset whose hex matches [hex] (case-insensitive), if any. */
        fun fromHex(hex: String): AccentPreset? =
            entries.firstOrNull { it.hex.equals(hex.trim(), ignoreCase = true) }
    }
}

/**
 * UI state for the Appearance screen.
 *
 * [pending] is the live-edited [ThemeSettings] rendered by the preview card; it is
 * seeded from the persisted value and written back on each change. [iconAliases] /
 * [activeIconAlias] drive the app-icon grid.
 */
data class AppearanceUiState(
    val pending: ThemeSettings = ThemeSettings(),
    val iconAliases: List<String> = emptyList(),
    val activeIconAlias: String = "Default",
    val errorMessage: String? = null
) {
    /** The accent preset currently selected, or null for a custom hex. */
    val selectedAccent: AccentPreset? get() = AccentPreset.fromHex(pending.accentHex)
}

/**
 * Drives the Appearance settings (PRD 6E): accent color, glass intensity, motion,
 * dark/light, and the app-icon switcher. Theme edits persist through
 * [SettingsRepository.setTheme]; icon changes go through [IconSwitcher].
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val iconSwitcher: IconSwitcher
) : ViewModel() {

    /** Live-edited theme; null until the persisted value is first observed. */
    private val draft = MutableStateFlow<ThemeSettings?>(null)

    /** Active icon alias, refreshed after a switch. */
    private val activeAlias = MutableStateFlow(safeCurrentAlias())

    val uiState: StateFlow<AppearanceUiState> =
        combine(settingsRepository.theme, draft, activeAlias) { persisted, edited, alias ->
            AppearanceUiState(
                pending = edited ?: persisted,
                iconAliases = iconSwitcher.available(),
                activeIconAlias = alias
            )
        }.catch {
            // Don't let a settings-read failure cancel the scope; degrade to defaults + note.
            emit(
                AppearanceUiState(
                    iconAliases = iconSwitcher.available(),
                    activeIconAlias = activeAlias.value,
                    errorMessage = "Couldn't load appearance settings."
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppearanceUiState(
                iconAliases = iconSwitcher.available(),
                activeIconAlias = activeAlias.value
            )
        )

    /** Pick an accent from a preset. */
    fun setAccent(preset: AccentPreset) = updateTheme { it.copy(accentHex = preset.hex) }

    /** Set the glass intensity (0f..1f). */
    fun setGlassIntensity(value: Float) =
        updateTheme { it.copy(glassIntensity = value.coerceIn(0f, 1f)) }

    /** Toggle the spring/animation motion master switch. */
    fun setMotionEnabled(enabled: Boolean) = updateTheme { it.copy(motionEnabled = enabled) }

    /** Toggle the dark/light canvas. */
    fun setDarkMode(dark: Boolean) = updateTheme { it.copy(darkMode = dark) }

    /**
     * Switch the launcher icon to [alias]. Triggers a brief launcher refresh; the
     * persisted active alias is updated by the [IconSwitcher] implementation.
     */
    fun selectIcon(alias: String) {
        iconSwitcher.switch(alias)
        activeAlias.value = safeCurrentAlias()
    }

    /**
     * Apply [edit] to the live draft and persist it. The draft is seeded from the
     * current rendered value so edits compose on top of the latest state.
     */
    private fun updateTheme(edit: (ThemeSettings) -> ThemeSettings) {
        val next = edit(draft.value ?: uiState.value.pending)
        draft.update { next }
        viewModelScope.launch { settingsRepository.setTheme(next) }
    }

    /** Reading the current alias may touch PackageManager; never let it crash the VM. */
    private fun safeCurrentAlias(): String =
        runCatching { iconSwitcher.current() }.getOrDefault("Default")
}
