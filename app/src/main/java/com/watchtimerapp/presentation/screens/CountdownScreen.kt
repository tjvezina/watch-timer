package com.watchtimerapp.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService

@Composable
fun CountdownScreen(
    onCancelled: () -> Unit,
) {
    val timerState by TimerService.timerState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Navigate back when timer is cancelled/dismissed
    LaunchedEffect(timerState) {
        if (timerState is TimerState.Idle) {
            onCancelled()
        }
    }

    if (timerState is TimerState.Alarming) {
        val activity = context as? Activity
        DisposableEffect(Unit) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        val alarmState = timerState as TimerState.Alarming
        AlarmScreen(
            alarmStartMillis = alarmState.alarmStartMillis,
            onStop = { TimerService.dismissAlarm(context) },
            onRestart = { TimerService.restartTimer(context) },
        )
    } else {
        CountdownContent(timerState = timerState, context = context)
    }
}

@Composable
private fun CountdownContent(
    timerState: TimerState,
    context: android.content.Context,
) {
    var remainingMillis by remember { mutableLongStateOf(timerState.remainingMillis()) }
    LaunchedEffect(timerState) {
        if (timerState is TimerState.Idle || timerState is TimerState.Alarming) {
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { _ ->
                if (timerState is TimerState.Running || timerState is TimerState.Paused) {
                    remainingMillis = timerState.remainingMillis()
                }
            }
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
        // White circular progress — starts full, decreases with timer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
            )
        }

        // Cancel button at the top — no background, bold gray X
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { TimerService.cancelTimer(context) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp),
            )
        }

        // Timer text — nudged up for visual balance against the larger button
        Text(
            text = TimerState.formatRemainingTime(remainingMillis),
            fontSize = 36.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.offset(y = (-18).dp),
        )

        // Pause/play button toward the bottom
        Button(
            onClick = {
                if (isPaused) {
                    TimerService.resumeTimer(context)
                } else {
                    TimerService.pauseTimer(context)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = Color.Black,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
