package com.quietping.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The single Preferences DataStore instance backing [SettingsRepositoryImpl].
 * Exposed as a Context extension so the DI layer can provide it without owning the
 * DataStore wiring. File name is intentionally generic for privacy.
 */
val Context.quietPingSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "quietping_settings"
)
