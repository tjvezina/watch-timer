package com.watchtimerapp.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.watchtimerapp.MainActivity
import com.watchtimerapp.R
import com.watchtimerapp.complication.TimerComplicationService
import com.watchtimerapp.data.SettingsRepository
import com.watchtimerapp.data.TimerRepository
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null
    private lateinit var timerRepository: TimerRepository
    private var lastComplicationUpdateMinute = -1L
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        timerRepository = TimerRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val duration = intent.getLongExtra(EXTRA_DURATION_MILLIS, 0L)
                if (duration > 0) startTimer(duration)
            }
            ACTION_RESUME_WITH_END_TIME -> {
                val endTime = intent.getLongExtra(EXTRA_END_TIME_MILLIS, 0L)
                val originalDuration = intent.getLongExtra(EXTRA_ORIGINAL_DURATION_MILLIS, 0L)
                if (endTime > 0) resumeFromEndTime(endTime, originalDuration)
            }
            ACTION_RESTORE_PAUSED -> {
                val remaining = intent.getLongExtra(EXTRA_REMAINING_MILLIS, 0L)
                val originalDuration = intent.getLongExtra(EXTRA_ORIGINAL_DURATION_MILLIS, 0L)
                if (remaining > 0) restorePaused(remaining, originalDuration)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_CANCEL -> cancelTimer()
            ACTION_DISMISS_ALARM -> dismissAlarm()
            ACTION_RESTART -> restartTimer()
            ACTION_FIRE_EXPIRED -> {
                val originalDuration = intent.getLongExtra(EXTRA_ORIGINAL_DURATION_MILLIS, 0L)
                fireExpired(originalDuration)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarmFeedback()
        scope.cancel()
        super.onDestroy()
    }

    private fun startTimer(durationMillis: Long) {
        val endTime = System.currentTimeMillis() + durationMillis
        _timerState.value = TimerState.Running(
            endTimeMillis = endTime,
            originalDurationMillis = durationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(durationMillis))
        scheduleExactAlarm(endTime)
        scope.launch { timerRepository.persistRunningTimer(endTime, durationMillis) }
        startCountdown()
    }

    private fun resumeFromEndTime(endTimeMillis: Long, originalDurationMillis: Long) {
        _timerState.value = TimerState.Running(
            endTimeMillis = endTimeMillis,
            originalDurationMillis = originalDurationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(
            (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        ))
        scheduleExactAlarm(endTimeMillis)
        startCountdown()
    }

    private fun restorePaused(remainingMillis: Long, originalDurationMillis: Long) {
        _timerState.value = TimerState.Paused(
            remainingMillis = remainingMillis,
            originalDurationMillis = originalDurationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(remainingMillis))
    }

    private fun pauseTimer() {
        val current = _timerState.value
        if (current is TimerState.Running) {
            val remaining = current.remainingMillis()
            _timerState.value = TimerState.Paused(
                remainingMillis = remaining,
                originalDurationMillis = current.originalDurationMillis,
            )
            countdownJob?.cancel()
            cancelExactAlarm()
            updateNotification(remaining)
            scope.launch {
                timerRepository.persistPausedTimer(remaining, current.originalDurationMillis)
            }
        }
    }

    private fun resumeTimer() {
        val current = _timerState.value
        if (current is TimerState.Paused) {
            val endTime = System.currentTimeMillis() + current.remainingMillis
            _timerState.value = TimerState.Running(
                endTimeMillis = endTime,
                originalDurationMillis = current.originalDurationMillis,
            )
            scheduleExactAlarm(endTime)
            scope.launch {
                timerRepository.persistRunningTimer(endTime, current.originalDurationMillis)
            }
            startCountdown()
        }
    }

    private fun cancelTimer() {
        countdownJob?.cancel()
        cancelExactAlarm()
        _timerState.value = TimerState.Idle
        requestComplicationUpdate()
        scope.launch { timerRepository.clearPersistedTimer() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun restartTimer() {
        val current = _timerState.value
        val duration = when (current) {
            is TimerState.Alarming -> current.originalDurationMillis
            else -> return
        }
        stopAlarmFeedback()
        startTimer(duration)
    }

    private fun dismissAlarm() {
        stopAlarmFeedback()
        _timerState.value = TimerState.Idle
        requestComplicationUpdate()
        scope.launch { timerRepository.clearPersistedTimer() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fireExpired(originalDurationMillis: Long) {
        _timerState.value = TimerState.Alarming(originalDurationMillis = originalDurationMillis)
        startForeground(NOTIFICATION_ID, buildNotification(0L))
        startAlarmFeedback()
        AlarmReceiver.fireAlarm(this)
    }

    private fun startAlarmFeedback() {
        val settingsRepo = SettingsRepository(applicationContext)
        scope.launch {
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
                    val pattern = longArrayOf(0, 100, 150, 100, 400, 100, 150, 100, 900)
                    vibrate(VibrationEffect.createWaveform(pattern, 0))
                }
            }
        }
    }

    private fun stopAlarmFeedback() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (true) {
                val state = _timerState.value
                if (state !is TimerState.Running) break
                val remaining = state.remainingMillis()
                if (remaining <= 0) {
                    onTimerFinished(state.originalDurationMillis)
                    break
                }
                updateNotification(remaining)
                // Update complication once per minute
                val currentMinute = remaining / 60_000
                if (currentMinute != lastComplicationUpdateMinute) {
                    lastComplicationUpdateMinute = currentMinute
                    requestComplicationUpdate()
                }
                delay(1_000L)
            }
        }
    }

    private fun onTimerFinished(originalDurationMillis: Long) {
        _timerState.value = TimerState.Alarming(originalDurationMillis = originalDurationMillis)
        requestComplicationUpdate()
        cancelExactAlarm()
        startAlarmFeedback()
        if (!MainActivity.isInForeground) {
            AlarmReceiver.fireAlarm(this)
        }
    }

    private fun requestComplicationUpdate() {
        val requester = ComplicationDataSourceUpdateRequester.create(
            this,
            android.content.ComponentName(this, TimerComplicationService::class.java),
        )
        requester.requestUpdateAll()
    }

    private fun scheduleExactAlarm(triggerAtMillis: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            getAlarmPendingIntent(),
        )
    }

    private fun cancelExactAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getAlarmPendingIntent())
    }

    private fun getAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_EXPIRED
        }
        return PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.timer_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(remainingMillis: Long): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.timer_running))
            .setContentText(TimerState.formatRemainingTime(remainingMillis))
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(remainingMillis: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(remainingMillis))
    }

    companion object {
        const val ACTION_START = "com.watchtimerapp.action.START"
        const val ACTION_RESUME_WITH_END_TIME = "com.watchtimerapp.action.RESUME_WITH_END_TIME"
        const val ACTION_RESTORE_PAUSED = "com.watchtimerapp.action.RESTORE_PAUSED"
        const val ACTION_PAUSE = "com.watchtimerapp.action.PAUSE"
        const val ACTION_RESUME = "com.watchtimerapp.action.RESUME"
        const val ACTION_CANCEL = "com.watchtimerapp.action.CANCEL"
        const val ACTION_DISMISS_ALARM = "com.watchtimerapp.action.DISMISS_ALARM"
        const val ACTION_RESTART = "com.watchtimerapp.action.RESTART"
        const val ACTION_FIRE_EXPIRED = "com.watchtimerapp.action.FIRE_EXPIRED"

        const val EXTRA_DURATION_MILLIS = "duration_millis"
        const val EXTRA_END_TIME_MILLIS = "end_time_millis"
        const val EXTRA_ORIGINAL_DURATION_MILLIS = "original_duration_millis"
        const val EXTRA_REMAINING_MILLIS = "remaining_millis"

        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1

        private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        fun startTimer(context: Context, durationMillis: Long) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_MILLIS, durationMillis)
            }
            context.startForegroundService(intent)
        }

        fun pauseTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun cancelTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        fun dismissAlarm(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_DISMISS_ALARM
            }
            context.startService(intent)
        }

        fun restartTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESTART
            }
            context.startService(intent)
        }
    }
}
