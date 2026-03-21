package com.watchtimerapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.presetDataStore: DataStore<Preferences> by preferencesDataStore(name = "presets")

class PresetRepository(private val context: Context) {

    private val presetsKey = stringPreferencesKey("preset_list")

    val presets: Flow<List<Long>> = context.presetDataStore.data.map { prefs ->
        prefs[presetsKey]?.let { raw ->
            raw.split(",").mapNotNull { it.trim().toLongOrNull() }.sorted()
        } ?: DEFAULT_PRESETS
    }

    suspend fun savePresets(presets: List<Long>) {
        context.presetDataStore.edit { prefs ->
            prefs[presetsKey] = presets.joinToString(",")
        }
    }

    companion object {
        val DEFAULT_PRESETS: List<Long> = listOf(
            1 * 60_000L,
            3 * 60_000L,
            5 * 60_000L,
            10 * 60_000L,
            15 * 60_000L,
            30 * 60_000L,
        )

        fun formatPresetLabel(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 && minutes > 0 -> "$hours hr $minutes min"
                hours > 0 -> "$hours hr"
                minutes > 0 -> "$minutes min"
                else -> "$seconds sec"
            }
        }
    }
}
