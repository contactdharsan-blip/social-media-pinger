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
 * @param biometricLock          require biometric auth on launch.
 * @param retentionDays          auto-purge captured messages older than this many days.
 * @param screenshotBlock        set FLAG_SECURE app-wide (blocks screenshots / screen recording).
 * @param hideNotificationContent post alerts with a generic title/body + secret lock-screen visibility.
 * @param breakInLogEnabled      record failed unlock attempts to the on-device break-in log.
 * @param decoyPinEnabled        a secondary "decoy" PIN unlocks into an empty vault.
 * @param decoyPinHash           salted hash of the decoy PIN (never the raw PIN); "" when unset.
 */
data class PrivacySettings(
    val biometricLock: Boolean = false,
    val retentionDays: Int = 30,
    val screenshotBlock: Boolean = false,
    val hideNotificationContent: Boolean = false,
    val breakInLogEnabled: Boolean = false,
    val decoyPinEnabled: Boolean = false,
    val decoyPinHash: String = ""
)

/**
 * Alert-delivery preferences not tied to a single rule.
 *
 * @param digestEnabled       batch low-priority matches into a once-daily summary.
 * @param digestHour          hour-of-day (0..23) the digest is delivered.
 * @param otpAutoDeleteHours  auto-purge captured OTP SMS after this many hours (0 ⇒ off).
 */
data class AlertPrefs(
    val digestEnabled: Boolean = false,
    val digestHour: Int = 9,
    val otpAutoDeleteHours: Int = 0
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

    /** Alert-delivery preferences (digest, OTP auto-delete). */
    val alerts: Flow<AlertPrefs>

    /** The currently active app-icon alias (see [com.quietping.domain.icon.IconSwitcher]). */
    val activeIconAlias: Flow<String>

    /** Persist new theme settings. */
    suspend fun setTheme(t: ThemeSettings)

    /** Persist new privacy settings. */
    suspend fun setPrivacy(p: PrivacySettings)

    /** Persist new alert-delivery preferences. */
    suspend fun setAlerts(a: AlertPrefs)

    /** Persist the active icon alias. */
    suspend fun setActiveIconAlias(alias: String)
}
