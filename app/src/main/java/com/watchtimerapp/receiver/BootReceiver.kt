package com.watchtimerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.watchtimerapp.data.TimerRepository
import com.watchtimerapp.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "onReceive: BOOT_COMPLETED")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timerRepository = TimerRepository(context)
                val persisted = timerRepository.loadPersistedTimer()
                if (persisted == null) {
                    Log.d(TAG, "No persisted timer found")
                    return@launch
                }

                when (persisted) {
                    is TimerRepository.PersistedTimer.Paused -> {
                        Log.i(TAG, "Restoring paused timer: remaining=${persisted.remainingMillis}ms")
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
                            Log.i(TAG, "Timer still active, resuming: endTime=${persisted.endTimeMillis}")
                            val serviceIntent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_RESUME_WITH_END_TIME
                                putExtra(TimerService.EXTRA_END_TIME_MILLIS, persisted.endTimeMillis)
                                putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                            }
                            context.startForegroundService(serviceIntent)
                        } else {
                            val elapsedSinceExpiry = now - persisted.endTimeMillis
                            if (elapsedSinceExpiry <= STALE_ALARM_THRESHOLD_MILLIS) {
                                Log.i(TAG, "Timer expired ${elapsedSinceExpiry}ms ago, firing alarm")
                                val serviceIntent = Intent(context, TimerService::class.java).apply {
                                    action = TimerService.ACTION_FIRE_EXPIRED
                                    putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, persisted.originalDurationMillis)
                                }
                                context.startForegroundService(serviceIntent)
                            } else {
                                Log.w(TAG, "Timer expired ${elapsedSinceExpiry}ms ago (stale), clearing without alarm")
                                timerRepository.clearPersistedTimer()
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        // Don't fire alarm for timers that expired more than 5 minutes ago.
        private const val STALE_ALARM_THRESHOLD_MILLIS = 300_000L
    }
}
