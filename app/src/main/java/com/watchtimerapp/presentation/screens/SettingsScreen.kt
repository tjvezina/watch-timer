package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.watchtimerapp.data.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
) {
    val soundEnabled by settingsRepository.soundEnabled.collectAsState(initial = true)
    val vibrationEnabled by settingsRepository.vibrationEnabled.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        item {
            ListHeader {
                Text("Settings")
            }
        }

        // Sound toggle
        item {
            SwitchButton(
                checked = soundEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setSoundEnabled(enabled) }
                },
                label = { Text("Sound") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }

        // Vibration toggle
        item {
            SwitchButton(
                checked = vibrationEnabled,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setVibrationEnabled(enabled) }
                },
                label = { Text("Vibration") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}
