package com.watchtimerapp

import android.app.NotificationManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.presentation.screens.AlarmScreen
import com.watchtimerapp.presentation.theme.WatchTimerTheme
import com.watchtimerapp.receiver.AlarmReceiver
import com.watchtimerapp.service.TimerService

class AlarmActivity : ComponentActivity() {

    private var handledExplicitly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val currentState = TimerService.timerState.value
        if (currentState !is TimerState.Alarming) {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val alarmStart = currentState.alarmStartMillis

        setContent {
            WatchTimerTheme {
                AlarmScreen(
                    alarmStartMillis = alarmStart,
                    onStop = { dismiss() },
                    onRestart = { restart() },
                )
            }
        }
    }

    private fun dismiss() {
        handledExplicitly = true
        cancelAlarmNotification()
        TimerService.dismissAlarm(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun restart() {
        handledExplicitly = true
        cancelAlarmNotification()
        TimerService.restartTimer(this)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun cancelAlarmNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(AlarmReceiver.ALARM_NOTIFICATION_ID)
    }

    override fun onDestroy() {
        if (!handledExplicitly && TimerService.timerState.value is TimerState.Alarming) {
            TimerService.dismissAlarm(this)
        }
        super.onDestroy()
    }
}
