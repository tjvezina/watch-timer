package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import com.watchtimerapp.data.SettingsRepository
import kotlinx.coroutines.launch

private val switchColors
    @Composable get() = SwitchButtonDefaults.switchButtonColors(
        checkedContainerColor = Color(0xFF505050),
        checkedThumbColor = Color.White,
        checkedTrackColor = Color.Gray,
        checkedTrackBorderColor = Color.Gray,
        uncheckedContainerColor = Color(0xFF252525),
        uncheckedThumbColor = Color.Gray,
        uncheckedTrackColor = Color.Transparent,
        uncheckedTrackBorderColor = Color.Gray,
    )

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
) {
    val soundEnabled by settingsRepository.soundEnabled.collectAsState(initial = true)
    val vibrationEnabled by settingsRepository.vibrationEnabled.collectAsState(initial = true)
    val secondsInterval by settingsRepository.secondsInterval.collectAsState(
        initial = SettingsRepository.DEFAULT_SECONDS_INTERVAL,
    )
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        autoCentering = null,
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
                    .fillMaxWidth(0.9f)
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                colors = switchColors,
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
                    .fillMaxWidth(0.9f)
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                colors = switchColors,
            )
        }

        // Seconds interval — tap to cycle
        item {
            Button(
                onClick = {
                    val options = SettingsRepository.SECONDS_INTERVAL_OPTIONS
                    val nextIndex = (options.indexOf(secondsInterval) + 1) % options.size
                    scope.launch { settingsRepository.setSecondsInterval(options[nextIndex]) }
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF505050),
                ),
            ) {
                Text("Sec interval", color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp)
                        .size(32.dp)
                        .background(Color(0xFF707070), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$secondsInterval",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
