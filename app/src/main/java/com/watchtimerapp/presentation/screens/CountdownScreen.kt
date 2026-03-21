package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.delay

@Composable
fun CountdownScreen(
    onCancelled: () -> Unit,
) {
    val timerState by TimerService.timerState.collectAsState()

    // Tick every second to update the display
    var remainingMillis by remember { mutableLongStateOf(timerState.remainingMillis()) }
    LaunchedEffect(timerState) {
        while (true) {
            remainingMillis = timerState.remainingMillis()
            if (timerState is TimerState.Idle) {
                onCancelled()
                break
            }
            if (timerState is TimerState.Alarming) {
                // AlarmActivity handles the alarm via full-screen intent
                break
            }
            delay(100L)
        }
    }

    val progress = when (val state = timerState) {
        is TimerState.Running -> {
            val total = state.originalDurationMillis.toFloat()
            if (total > 0) remainingMillis / total else 0f
        }
        is TimerState.Paused -> {
            val total = state.originalDurationMillis.toFloat()
            if (total > 0) state.remainingMillis / total else 0f
        }
        else -> 0f
    }

    val isPaused = timerState is TimerState.Paused

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Circular progress around the edge
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 4.dp,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = TimerState.formatRemainingTime(remainingMillis),
                fontSize = 36.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                val context = androidx.compose.ui.platform.LocalContext.current

                Button(
                    onClick = {
                        if (isPaused) {
                            TimerService.resumeTimer(context)
                        } else {
                            TimerService.pauseTimer(context)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text(if (isPaused) "▶" else "⏸", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = { TimerService.cancelTimer(context) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Text("✕", fontSize = 16.sp)
                }
            }
        }
    }
}
