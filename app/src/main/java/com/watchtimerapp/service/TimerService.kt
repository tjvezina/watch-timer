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
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager
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
    private var alarmTimeoutJob: Job? = null
    private lateinit var timerRepository: TimerRepository
    private var lastComplicationUpdateMinute = -1L
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var alarmWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        timerRepository = TimerRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Service restarted by OS after process death (START_STICKY).
            // Restore from persisted state or stop.
            Log.w(TAG, "onStartCommand: null intent — service restarted by OS, restoring state")
            restoreFromPersistedState()
            return START_STICKY
        }

        Log.d(TAG, "onStartCommand: action=${intent.action}")

        when (intent.action) {
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
        Log.d(TAG, "onDestroy")
        stopAlarmFeedback()
        scope.cancel()
        super.onDestroy()
    }

    private fun restoreFromPersistedState() {
        // Must call startForeground quickly to avoid ANR on service restart.
        startForeground(NOTIFICATION_ID, buildNotification(0L))
        scope.launch {
            val persisted = timerRepository.loadPersistedTimer()
            if (persisted == null) {
                Log.d(TAG, "restoreFromPersistedState: no persisted timer, stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            when (persisted) {
                is TimerRepository.PersistedTimer.Paused -> {
                    Log.i(TAG, "restoreFromPersistedState: restoring paused timer")
                    restorePaused(persisted.remainingMillis, persisted.originalDurationMillis)
                }
                is TimerRepository.PersistedTimer.Running -> {
                    val now = System.currentTimeMillis()
                    if (persisted.endTimeMillis > now) {
                        Log.i(TAG, "restoreFromPersistedState: timer still active, resuming")
                        resumeFromEndTime(persisted.endTimeMillis, persisted.originalDurationMillis)
                    } else {
                        val elapsedSinceExpiry = now - persisted.endTimeMillis
                        if (elapsedSinceExpiry <= STALE_ALARM_THRESHOLD_MILLIS) {
                            Log.i(TAG, "restoreFromPersistedState: timer expired ${elapsedSinceExpiry}ms ago, firing")
                            fireExpired(persisted.originalDurationMillis)
                        } else {
                            Log.w(TAG, "restoreFromPersistedState: timer expired ${elapsedSinceExpiry}ms ago (stale), clearing")
                            timerRepository.clearPersistedTimer()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    private fun startTimer(durationMillis: Long) {
        val endTime = System.currentTimeMillis() + durationMillis
        Log.i(TAG, "startTimer: duration=${durationMillis}ms, endTime=$endTime")
        _timerState.value = TimerState.Running(
            endTimeMillis = endTime,
            originalDurationMillis = durationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(durationMillis))
        scheduleExactAlarm(endTime, durationMillis)
        scope.launch { timerRepository.persistRunningTimer(endTime, durationMillis) }
        startCountdown()
    }

    private fun resumeFromEndTime(endTimeMillis: Long, originalDurationMillis: Long) {
        Log.i(TAG, "resumeFromEndTime: endTime=$endTimeMillis, originalDuration=$originalDurationMillis")
        _timerState.value = TimerState.Running(
            endTimeMillis = endTimeMillis,
            originalDurationMillis = originalDurationMillis,
        )
        startForeground(NOTIFICATION_ID, buildNotification(
            (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        ))
        scheduleExactAlarm(endTimeMillis, originalDurationMillis)
        startCountdown()
    }

    private fun restorePaused(remainingMillis: Long, originalDurationMillis: Long) {
        Log.i(TAG, "restorePaused: remaining=${remainingMillis}ms")
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
            Log.i(TAG, "pauseTimer: remaining=${remaining}ms")
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
            Log.i(TAG, "resumeTimer: endTime=$endTime")
            _timerState.value = TimerState.Running(
                endTimeMillis = endTime,
                originalDurationMillis = current.originalDurationMillis,
            )
            scheduleExactAlarm(endTime, current.originalDurationMillis)
            scope.launch {
                timerRepository.persistRunningTimer(endTime, current.originalDurationMillis)
            }
            startCountdown()
        }
    }

    private fun cancelTimer() {
        Log.i(TAG, "cancelTimer")
        countdownJob?.cancel()
        cancelExactAlarm()
        _timerState.value = TimerState.Idle
        requestComplicationUpdate()
        // Await persistence before stopping to avoid race condition
        // where stopSelf() destroys the scope before the write completes.
        scope.launch {
            timerRepository.clearPersistedTimer()
            Log.d(TAG, "cancelTimer: persisted state cleared, stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun restartTimer() {
        val current = _timerState.value
        val duration = when (current) {
            is TimerState.Alarming -> current.originalDurationMillis
            else -> return
        }
        Log.i(TAG, "restartTimer: duration=${duration}ms")
        stopAlarmFeedback()
        AlarmReceiver.cancelAlarmNotification(this)
        startTimer(duration)
    }

    private fun dismissAlarm() {
        Log.i(TAG, "dismissAlarm")
        stopAlarmFeedback()
        cancelExactAlarm()
        AlarmReceiver.cancelAlarmNotification(this)
        _timerState.value = TimerState.Idle
        requestComplicationUpdate()
        // Await persistence before stopping to avoid race condition
        // where stopSelf() destroys the scope before the write completes.
        scope.launch {
            timerRepository.clearPersistedTimer()
            Log.d(TAG, "dismissAlarm: persisted state cleared, stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun fireExpired(originalDurationMillis: Long) {
        if (_timerState.value is TimerState.Alarming) {
            Log.d(TAG, "fireExpired: already alarming, ignoring")
            return
        }
        Log.i(TAG, "fireExpired: originalDuration=${originalDurationMillis}ms")
        _timerState.value = TimerState.Alarming(originalDurationMillis = originalDurationMillis)
        countdownJob?.cancel()
        requestComplicationUpdate()
        addOverlayWindow()
        // Use buildNotification (points to MainActivity) — NOT buildAlarmNotification
        // (which points to AlarmActivity). The FSI notification also uses a PendingIntent
        // to AlarmActivity with requestCode=0. Using the same PendingIntent here via
        // startForeground() prevents the FSI from launching the activity.
        startForeground(NOTIFICATION_ID, buildNotification(0L))
        // Clear persisted timer immediately so it won't re-fire on reboot.
        scope.launch { timerRepository.clearPersistedTimer() }
        startAlarmFeedback()
        // Post separate IMPORTANCE_HIGH notification with FSI to launch AlarmActivity.
        AlarmReceiver.fireAlarm(this)
    }

    private fun onTimerFinished(originalDurationMillis: Long) {
        if (_timerState.value is TimerState.Alarming) return
        Log.i(TAG, "onTimerFinished: originalDuration=${originalDurationMillis}ms")
        _timerState.value = TimerState.Alarming(originalDurationMillis = originalDurationMillis)
        requestComplicationUpdate()
        // Add overlay window so the app has a "visible window" when AlarmReceiver fires
        // ~1s later and calls startActivity(). This is the bg activity start exemption.
        addOverlayWindow()
        // DO NOT cancel the AlarmManager alarm here. The receiver needs to fire so it
        // can call startActivity(AlarmActivity).
        // Clear persisted timer immediately so it won't re-fire on reboot.
        scope.launch { timerRepository.clearPersistedTimer() }
        startAlarmFeedback()
    }

    private fun addOverlayWindow() {
        addOverlay(this)
    }

    private fun startAlarmFeedback() {
        Log.d(TAG, "startAlarmFeedback: acquiring wakelock")
        acquireAlarmWakeLock()

        val settingsRepo = SettingsRepository(applicationContext)
        scope.launch {
            val soundEnabled = settingsRepo.soundEnabled.first()
            val vibrationEnabled = settingsRepo.vibrationEnabled.first()
            Log.d(TAG, "startAlarmFeedback: sound=$soundEnabled, vibration=$vibrationEnabled")

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
                    val pattern = longArrayOf(0, 200, 50, 200, 300, 200, 50, 200, 800)
                    vibrate(VibrationEffect.createWaveform(pattern, 0))
                }
            }
        }

        scheduleAlarmTimeout()
    }

    private fun stopAlarmFeedback() {
        Log.d(TAG, "stopAlarmFeedback")
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = null
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        releaseAlarmWakeLock()
        removeOverlay(this)
    }

    private fun acquireAlarmWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        alarmWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "watchtimerapp:alarm_feedback",
        ).apply {
            // Timeout slightly longer than alarm timeout as a safety net.
            acquire(ALARM_TIMEOUT_MILLIS + 5_000L)
        }
    }

    private fun releaseAlarmWakeLock() {
        alarmWakeLock?.let {
            if (it.isHeld) {
                Log.d(TAG, "releaseAlarmWakeLock: releasing")
                it.release()
            }
        }
        alarmWakeLock = null
    }

    private fun scheduleAlarmTimeout() {
        alarmTimeoutJob?.cancel()
        alarmTimeoutJob = scope.launch {
            delay(ALARM_TIMEOUT_MILLIS)
            Log.w(TAG, "Alarm auto-dismissed after ${ALARM_TIMEOUT_MILLIS}ms timeout")
            dismissAlarm()
        }
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

    private fun requestComplicationUpdate() {
        val requester = ComplicationDataSourceUpdateRequester.create(
            this,
            android.content.ComponentName(this, TimerComplicationService::class.java),
        )
        requester.requestUpdateAll()
    }

    private fun scheduleExactAlarm(triggerAtMillis: Long, originalDurationMillis: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "scheduleExactAlarm: triggerAt=$triggerAtMillis")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            getAlarmBroadcastPendingIntent(originalDurationMillis),
        )
    }

    private fun cancelExactAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "cancelExactAlarm")
        alarmManager.cancel(getAlarmBroadcastPendingIntent())
    }

    private fun getAlarmBroadcastPendingIntent(originalDurationMillis: Long = 0L): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TIMER_EXPIRED
            putExtra(EXTRA_ORIGINAL_DURATION_MILLIS, originalDurationMillis)
        }
        return PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // -- Notification channels & builders ----------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.timer_notification_channel),
            NotificationManager.IMPORTANCE_MIN,
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
        private const val TAG = "TimerService"

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
        private const val ALARM_TIMEOUT_MILLIS = 600_000L // 10 minutes
        private const val STALE_ALARM_THRESHOLD_MILLIS = 300_000L // 5 minutes

        private var overlayView: View? = null

        private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        fun addOverlay(context: Context) {
            if (overlayView != null) return
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val params = WindowManager.LayoutParams(
                    0, 0,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT,
                )
                val view = View(context)
                wm.addView(view, params)
                overlayView = view
                Log.d(TAG, "addOverlay: overlay added for bg activity start exemption")
            } catch (e: Exception) {
                Log.w(TAG, "addOverlay: failed", e)
            }
        }

        fun removeOverlay(context: Context) {
            overlayView?.let {
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(it)
                    Log.d(TAG, "removeOverlay: overlay removed")
                } catch (e: Exception) {
                    Log.w(TAG, "removeOverlay: failed", e)
                }
            }
            overlayView = null
        }

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
