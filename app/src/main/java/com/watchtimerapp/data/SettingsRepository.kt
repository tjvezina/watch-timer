package com.watchtimerapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val soundKey = booleanPreferencesKey("sound_enabled")
    private val vibrationKey = booleanPreferencesKey("vibration_enabled")
    private val secondsIntervalKey = intPreferencesKey("seconds_interval")

    val soundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[soundKey] ?: DEFAULT_SOUND_ENABLED
    }

    val vibrationEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[vibrationKey] ?: DEFAULT_VIBRATION_ENABLED
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[soundKey] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[vibrationKey] = enabled
        }
    }

    val secondsInterval: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[secondsIntervalKey] ?: DEFAULT_SECONDS_INTERVAL
    }

    suspend fun setSecondsInterval(interval: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[secondsIntervalKey] = interval
        }
    }

    companion object {
        const val DEFAULT_SOUND_ENABLED = true
        const val DEFAULT_VIBRATION_ENABLED = true
        const val DEFAULT_SECONDS_INTERVAL = 15
        val SECONDS_INTERVAL_OPTIONS = listOf(1, 5, 10, 15, 30)
    }
}
