package com.quietping.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.quietping.domain.settings.AlertPrefs
import com.quietping.domain.settings.PrivacySettings
import com.quietping.domain.settings.SettingsRepository
import com.quietping.domain.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [SettingsRepository] over Preferences DataStore. Each settings group is read as a
 * reactive [Flow] that falls back to the domain defaults when a key is absent, and
 * written field-by-field inside a single [edit] transaction.
 */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object Keys {
        // Theme
        val ACCENT_HEX = stringPreferencesKey("theme_accent_hex")
        val GLASS_INTENSITY = floatPreferencesKey("theme_glass_intensity")
        val MOTION_ENABLED = booleanPreferencesKey("theme_motion_enabled")
        val DARK_MODE = booleanPreferencesKey("theme_dark_mode")

        // Privacy
        val BIOMETRIC_LOCK = booleanPreferencesKey("privacy_biometric_lock")
        val RETENTION_DAYS = intPreferencesKey("privacy_retention_days")
        val SCREENSHOT_BLOCK = booleanPreferencesKey("privacy_screenshot_block")
        val HIDE_NOTIF_CONTENT = booleanPreferencesKey("privacy_hide_notif_content")
        val BREAK_IN_LOG = booleanPreferencesKey("privacy_break_in_log")
        val DECOY_PIN_ENABLED = booleanPreferencesKey("privacy_decoy_pin_enabled")
        val DECOY_PIN_HASH = stringPreferencesKey("privacy_decoy_pin_hash")

        // Alerts
        val DIGEST_ENABLED = booleanPreferencesKey("alerts_digest_enabled")
        val DIGEST_HOUR = intPreferencesKey("alerts_digest_hour")
        val OTP_AUTO_DELETE_HOURS = intPreferencesKey("alerts_otp_auto_delete_hours")

        // Icon
        val ACTIVE_ICON_ALIAS = stringPreferencesKey("active_icon_alias")
    }

    /** Defaults sourced from the domain data classes (single source of truth). */
    private val themeDefaults = ThemeSettings()
    private val privacyDefaults = PrivacySettings()
    private val alertDefaults = AlertPrefs()

    override val theme: Flow<ThemeSettings> = dataStore.data.map { prefs ->
        ThemeSettings(
            accentHex = prefs[Keys.ACCENT_HEX] ?: themeDefaults.accentHex,
            glassIntensity = prefs[Keys.GLASS_INTENSITY] ?: themeDefaults.glassIntensity,
            motionEnabled = prefs[Keys.MOTION_ENABLED] ?: themeDefaults.motionEnabled,
            darkMode = prefs[Keys.DARK_MODE] ?: themeDefaults.darkMode
        )
    }

    override val privacy: Flow<PrivacySettings> = dataStore.data.map { prefs ->
        PrivacySettings(
            biometricLock = prefs[Keys.BIOMETRIC_LOCK] ?: privacyDefaults.biometricLock,
            retentionDays = prefs[Keys.RETENTION_DAYS] ?: privacyDefaults.retentionDays,
            screenshotBlock = prefs[Keys.SCREENSHOT_BLOCK] ?: privacyDefaults.screenshotBlock,
            hideNotificationContent = prefs[Keys.HIDE_NOTIF_CONTENT] ?: privacyDefaults.hideNotificationContent,
            breakInLogEnabled = prefs[Keys.BREAK_IN_LOG] ?: privacyDefaults.breakInLogEnabled,
            decoyPinEnabled = prefs[Keys.DECOY_PIN_ENABLED] ?: privacyDefaults.decoyPinEnabled,
            decoyPinHash = prefs[Keys.DECOY_PIN_HASH] ?: privacyDefaults.decoyPinHash
        )
    }

    override val alerts: Flow<AlertPrefs> = dataStore.data.map { prefs ->
        AlertPrefs(
            digestEnabled = prefs[Keys.DIGEST_ENABLED] ?: alertDefaults.digestEnabled,
            digestHour = prefs[Keys.DIGEST_HOUR] ?: alertDefaults.digestHour,
            otpAutoDeleteHours = prefs[Keys.OTP_AUTO_DELETE_HOURS] ?: alertDefaults.otpAutoDeleteHours
        )
    }

    override val activeIconAlias: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_ICON_ALIAS] ?: DEFAULT_ICON_ALIAS
    }

    override suspend fun setTheme(t: ThemeSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCENT_HEX] = t.accentHex
            prefs[Keys.GLASS_INTENSITY] = t.glassIntensity
            prefs[Keys.MOTION_ENABLED] = t.motionEnabled
            prefs[Keys.DARK_MODE] = t.darkMode
        }
    }

    override suspend fun setPrivacy(p: PrivacySettings) {
        dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOCK] = p.biometricLock
            prefs[Keys.RETENTION_DAYS] = p.retentionDays
            prefs[Keys.SCREENSHOT_BLOCK] = p.screenshotBlock
            prefs[Keys.HIDE_NOTIF_CONTENT] = p.hideNotificationContent
            prefs[Keys.BREAK_IN_LOG] = p.breakInLogEnabled
            prefs[Keys.DECOY_PIN_ENABLED] = p.decoyPinEnabled
            prefs[Keys.DECOY_PIN_HASH] = p.decoyPinHash
        }
    }

    override suspend fun setAlerts(a: AlertPrefs) {
        dataStore.edit { prefs ->
            prefs[Keys.DIGEST_ENABLED] = a.digestEnabled
            prefs[Keys.DIGEST_HOUR] = a.digestHour
            prefs[Keys.OTP_AUTO_DELETE_HOURS] = a.otpAutoDeleteHours
        }
    }

    override suspend fun setActiveIconAlias(alias: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_ICON_ALIAS] = alias
        }
    }

    companion object {
        /** Matches the enabled launcher alias declared in the manifest (".alias.Default"). */
        const val DEFAULT_ICON_ALIAS = "Default"
    }
}
