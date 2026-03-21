package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.watchtimerapp.data.PresetRepository

@Composable
fun PresetListScreen(
    presetRepository: PresetRepository,
    onPresetSelected: (Long) -> Unit,
    onCustomSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
) {
    val presets by presetRepository.presets.collectAsState(initial = PresetRepository.DEFAULT_PRESETS)
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        item {
            ListHeader {
                Text("Timer")
            }
        }

        items(presets) { durationMillis ->
            TitleCard(
                onClick = { onPresetSelected(durationMillis) },
                title = { Text(PresetRepository.formatPresetLabel(durationMillis)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }

        item {
            TitleCard(
                onClick = onCustomSelected,
                title = {
                    Text(
                        text = "+ Custom",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }

        item {
            TitleCard(
                onClick = onSettingsSelected,
                title = {
                    Text(
                        text = "Settings",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {}
        }
    }
}
