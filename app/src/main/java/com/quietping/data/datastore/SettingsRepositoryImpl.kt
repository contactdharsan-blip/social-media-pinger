package com.quietping.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

        // Icon
        val ACTIVE_ICON_ALIAS = stringPreferencesKey("active_icon_alias")
    }

    /** Defaults sourced from the domain data classes (single source of truth). */
    private val themeDefaults = ThemeSettings()
    private val privacyDefaults = PrivacySettings()

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
            retentionDays = prefs[Keys.RETENTION_DAYS] ?: privacyDefaults.retentionDays
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
