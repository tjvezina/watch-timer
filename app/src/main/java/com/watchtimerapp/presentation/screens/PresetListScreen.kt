package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.data.TimerState

private val PresetButtonColor = Color(0xFF274F27)
private val PresetTextColor = Color(0xFFCCCCCC)

@Composable
fun PresetListScreen(
    presetRepository: PresetRepository,
    onPresetSelected: (Long) -> Unit,
    onPresetRemoved: (Long) -> Unit,
    onCustomSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
) {
    val presets by presetRepository.presets.collectAsState(initial = PresetRepository.DEFAULT_PRESETS)
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
        autoCentering = null,
    ) {
        item {
            ListHeader {
                Text("Timer")
            }
        }

        items(presets) { durationMillis ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 32.dp)
                    .background(PresetButtonColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .clickable { onPresetSelected(durationMillis) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    TimerState.formatRemainingTime(durationMillis),
                    color = PresetTextColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(
                    onClick = { onPresetRemoved(durationMillis) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove preset",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = onCustomSelected,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF666666)),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Custom timer",
                        tint = PresetTextColor,
                    )
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))

                IconButton(
                    onClick = onSettingsSelected,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF666666)),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = PresetTextColor,
                    )
                }
            }
        }
    }
}
