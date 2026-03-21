package com.watchtimerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.watchtimerapp.data.TimerRepository
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timerRepository = TimerRepository(context)
                val persisted = timerRepository.loadPersistedTimer() ?: return@launch

                when (persisted) {
                    is TimerRepository.PersistedTimer.Paused -> {
                        // Restore paused state
                        val serviceIntent = Intent(context, TimerService::class.java).apply {
                            action = TimerService.ACTION_RESTORE_PAUSED
                            putExtra(TimerService.EXTRA_REMAINING_MILLIS, persisted.remainingMillis)
                            putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                        }
                        context.startForegroundService(serviceIntent)
                    }
                    is TimerRepository.PersistedTimer.Running -> {
                        val now = System.currentTimeMillis()
                        if (persisted.endTimeMillis > now) {
                            // Timer still active — resume countdown
                            val serviceIntent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_RESUME_WITH_END_TIME
                                putExtra(TimerService.EXTRA_END_TIME_MILLIS, persisted.endTimeMillis)
                                putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                            }
                            context.startForegroundService(serviceIntent)
                        } else {
                            // Timer expired while device was off — start service in alarm state, then fire
                            val serviceIntent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_FIRE_EXPIRED
                                putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                            }
                            context.startForegroundService(serviceIntent)
                            timerRepository.clearPersistedTimer()
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
