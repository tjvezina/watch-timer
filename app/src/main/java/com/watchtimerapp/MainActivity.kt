package com.watchtimerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.presentation.navigation.TimerNavGraph
import com.watchtimerapp.presentation.navigation.Routes
import com.watchtimerapp.presentation.theme.WatchTimerTheme
import com.watchtimerapp.service.TimerService

class MainActivity : ComponentActivity() {

    companion object {
        var isInForeground = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startDestination = when (TimerService.timerState.value) {
            is TimerState.Running, is TimerState.Paused -> Routes.COUNTDOWN
            else -> Routes.PRESET_LIST
        }

        setContent {
            WatchTimerTheme {
                TimerNavGraph(startDestination = startDestination)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }
}
