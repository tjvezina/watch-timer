package com.watchtimerapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.presentation.screens.AlarmScreen
import com.watchtimerapp.presentation.theme.WatchTimerTheme
import com.watchtimerapp.receiver.AlarmReceiver
import com.watchtimerapp.service.TimerService

class AlarmActivity : ComponentActivity() {

    private var handledExplicitly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Ensure the activity shows over lock screen and turns the screen on.
        // Belt-and-suspenders with the manifest attributes.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // AlarmManager may launch this activity directly (via setAlarmClock PendingIntent)
        // before the service has transitioned to Alarming. Kick the service if needed.
        ensureServiceFiring(intent)

        // Cancel the FSI notification and remove the overlay now that the activity is visible.
        AlarmReceiver.cancelAlarmNotification(this)
        TimerService.removeOverlay(this)

        setContent {
            WatchTimerTheme {
                val timerState by TimerService.timerState.collectAsState()
                when (timerState) {
                    is TimerState.Alarming -> {
                        val alarmState = timerState as TimerState.Alarming
                        AlarmScreen(
                            alarmStartMillis = alarmState.alarmStartMillis,
                            onStop = { dismiss() },
                            onRestart = { restart() },
                        )
                    }
                    is TimerState.Idle -> {
                        // Alarm was dismissed externally (e.g. notification action), finish.
                        LaunchedEffect(Unit) {
                            Log.d(TAG, "State is Idle, finishing")
                            finish()
                        }
                    }
                    else -> {
                        // Running or Paused — service will transition to Alarming momentarily.
                        // Show nothing briefly while we wait.
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent")
        // singleTask: system redelivers if already open. Kick service again if needed.
        ensureServiceFiring(intent)
    }

    private fun ensureServiceFiring(launchIntent: Intent?) {
        val currentState = TimerService.timerState.value
        if (currentState is TimerState.Alarming) return

        val originalDuration = launchIntent?.getLongExtra(
            TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, 0L
        ) ?: 0L
        Log.d(TAG, "ensureServiceFiring: state=${currentState::class.simpleName}, " +
            "starting service with originalDuration=$originalDuration")
        startForegroundService(Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_FIRE_EXPIRED
            putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, originalDuration)
        })
    }

    private fun dismiss() {
        handledExplicitly = true
        Log.d(TAG, "dismiss: user tapped stop")
        TimerService.dismissAlarm(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun restart() {
        handledExplicitly = true
        Log.d(TAG, "restart: user tapped restart")
        TimerService.restartTimer(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // Only auto-dismiss if the user intentionally navigated away (isFinishing),
        // NOT when the system destroys the activity to reclaim resources.
        if (!handledExplicitly && isFinishing && TimerService.timerState.value is TimerState.Alarming) {
            Log.d(TAG, "onDestroy: user navigated away, auto-dismissing alarm")
            TimerService.dismissAlarm(this)
        } else if (!handledExplicitly) {
            Log.d(TAG, "onDestroy: system destroy (isFinishing=$isFinishing), NOT dismissing alarm")
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmActivity"
    }
}
