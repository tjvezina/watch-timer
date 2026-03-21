package com.watchtimerapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_state")

class TimerRepository(private val context: Context) {

    private val endTimeKey = longPreferencesKey("end_time_millis")
    private val originalDurationKey = longPreferencesKey("original_duration_millis")
    private val isPausedKey = booleanPreferencesKey("is_paused")
    private val pausedRemainingKey = longPreferencesKey("paused_remaining_millis")

    suspend fun persistRunningTimer(endTimeMillis: Long, originalDurationMillis: Long) {
        context.timerDataStore.edit { prefs ->
            prefs[endTimeKey] = endTimeMillis
            prefs[originalDurationKey] = originalDurationMillis
            prefs[isPausedKey] = false
            prefs.remove(pausedRemainingKey)
        }
    }

    suspend fun persistPausedTimer(remainingMillis: Long, originalDurationMillis: Long) {
        context.timerDataStore.edit { prefs ->
            prefs.remove(endTimeKey)
            prefs[originalDurationKey] = originalDurationMillis
            prefs[isPausedKey] = true
            prefs[pausedRemainingKey] = remainingMillis
        }
    }

    suspend fun clearPersistedTimer() {
        context.timerDataStore.edit { it.clear() }
    }

    suspend fun loadPersistedTimer(): PersistedTimer? {
        val prefs = context.timerDataStore.data.first()
        val originalDuration = prefs[originalDurationKey] ?: return null
        val isPaused = prefs[isPausedKey] ?: false

        return if (isPaused) {
            val remaining = prefs[pausedRemainingKey] ?: return null
            PersistedTimer.Paused(remaining, originalDuration)
        } else {
            val endTime = prefs[endTimeKey] ?: return null
            PersistedTimer.Running(endTime, originalDuration)
        }
    }

    sealed class PersistedTimer {
        abstract val originalDurationMillis: Long

        data class Running(
            val endTimeMillis: Long,
            override val originalDurationMillis: Long,
        ) : PersistedTimer()

        data class Paused(
            val remainingMillis: Long,
            override val originalDurationMillis: Long,
        ) : PersistedTimer()
    }
}
