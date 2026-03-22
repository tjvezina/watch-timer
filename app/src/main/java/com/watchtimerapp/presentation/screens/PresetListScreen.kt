package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.data.TimerState

private val PresetButtonColor = Color(0xFF274F27)
private val DeleteModeButtonColor = Color(0xFF8B2020)
private val PresetTextColor = Color.White
private val ButtonDiameter = 54.dp

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
    var deleteMode by remember { mutableStateOf(false) }

    // Grid items: add button first, then presets
    val gridItems = listOf<Long?>(null) + presets
    val gridRows = gridItems.chunked(3)

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

        items(gridRows.size) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                gridRows[rowIndex].forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = Modifier.size(3.dp))
                    if (item == null) {
                        // Add button
                        IconButton(
                            onClick = onCustomSelected,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF27529C)),
                            modifier = Modifier.size(ButtonDiameter),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Custom timer",
                                tint = PresetTextColor,
                            )
                        }
                    } else {
                        // Preset button
                        Box(
                            modifier = Modifier.size(ButtonDiameter),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(ButtonDiameter)
                                    .clip(CircleShape)
                                    .background(PresetButtonColor)
                                    .clickable {
                                        if (deleteMode) onPresetRemoved(item)
                                        else onPresetSelected(item)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    TimerState.formatRemainingTime(item),
                                    color = PresetTextColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            if (deleteMode) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-1).dp, y = 1.dp)
                                        .background(DeleteModeButtonColor, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "✕",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.offset(y = (-1).dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { deleteMode = !deleteMode },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (deleteMode) DeleteModeButtonColor else Color(0xFF852222),
                    ),
                    modifier = Modifier.size(ButtonDiameter),
                ) {
                    Icon(
                        imageVector = if (deleteMode) Icons.Filled.Close else Icons.Outlined.DeleteOutline,
                        contentDescription = if (deleteMode) "Exit delete mode" else "Delete presets",
                        tint = PresetTextColor,
                    )
                }

                Spacer(modifier = Modifier.size(3.dp))

                IconButton(
                    onClick = onSettingsSelected,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFF666666)),
                    modifier = Modifier.size(ButtonDiameter),
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
