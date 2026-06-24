package com.quietping.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * User-customizable appearance tokens, persisted in DataStore and applied through
 * the Compose theme.
 *
 * @param accentHex      accent color as a #RRGGBB hex string (default emerald).
 * @param glassIntensity multiplier (0f..1f) for glass opacity/blur strength.
 * @param motionEnabled  master toggle for spring/animation motion (honors reduced-motion).
 * @param darkMode       dark canvas (default true); light is optional.
 */
data class ThemeSettings(
    val accentHex: String = "#34d399",
    val glassIntensity: Float = 1f,
    val motionEnabled: Boolean = true,
    val darkMode: Boolean = true
)

/**
 * Privacy controls for the Vault and app lock.
 *
 * @param biometricLock require biometric auth on launch.
 * @param retentionDays auto-purge captured messages older than this many days.
 */
data class PrivacySettings(
    val biometricLock: Boolean = false,
    val retentionDays: Int = 30
)

/**
 * Single source of truth for persisted user settings (DataStore-backed). All
 * reads are reactive [Flow]s; writes are suspending.
 */
interface SettingsRepository {

    /** Appearance/theme settings. */
    val theme: Flow<ThemeSettings>

    /** Privacy/lock settings. */
    val privacy: Flow<PrivacySettings>

    /** The currently active app-icon alias (see [com.quietping.domain.icon.IconSwitcher]). */
    val activeIconAlias: Flow<String>

    /** Persist new theme settings. */
    suspend fun setTheme(t: ThemeSettings)

    /** Persist new privacy settings. */
    suspend fun setPrivacy(p: PrivacySettings)

    /** Persist the active icon alias. */
    suspend fun setActiveIconAlias(alias: String)
}
