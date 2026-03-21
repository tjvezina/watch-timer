package com.watchtimerapp

import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.watchtimerapp.data.PresetRepository
import com.watchtimerapp.data.SettingsRepository
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.presentation.screens.AlarmScreen
import com.watchtimerapp.presentation.theme.WatchTimerTheme
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {

    private var ringtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on (lock screen + turn on handled via manifest attributes)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val currentState = TimerService.timerState.value
        val originalDuration = when (currentState) {
            is TimerState.Alarming -> currentState.originalDurationMillis
            is TimerState.Running -> currentState.originalDurationMillis
            else -> 0L
        }
        val durationLabel = PresetRepository.formatPresetLabel(originalDuration)

        startAlarmFeedback()

        setContent {
            WatchTimerTheme {
                AlarmScreen(
                    originalDurationLabel = durationLabel,
                    onDismiss = { dismiss() },
                )
            }
        }
    }

    private fun startAlarmFeedback() {
        val settingsRepo = SettingsRepository(applicationContext)
        lifecycleScope.launch {
            val soundEnabled = settingsRepo.soundEnabled.first()
            val vibrationEnabled = settingsRepo.vibrationEnabled.first()

            if (soundEnabled) {
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
                    isLooping = true
                    play()
                }
            }

            if (vibrationEnabled) {
                vibrator = getSystemService(Vibrator::class.java)?.apply {
                    val pattern = longArrayOf(0, 500, 250, 500)
                    vibrate(VibrationEffect.createWaveform(pattern, 0))
                }
            }
        }
    }

    private fun dismiss() {
        ringtone?.stop()
        vibrator?.cancel()
        TimerService.dismissAlarm(this)
        finish()
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }
}
