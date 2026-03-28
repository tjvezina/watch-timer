package com.watchtimerapp.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.watchtimerapp.AlarmActivity
import com.watchtimerapp.R
import com.watchtimerapp.service.TimerService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_EXPIRED) return

        val originalDuration = intent.getLongExtra(
            TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, 0L
        )
        Log.i(TAG, "onReceive: TIMER_EXPIRED, originalDuration=${originalDuration}ms")

        // Add a zero-size overlay window so the system sees the app as having a
        // "visible window", which grants the bg activity start exemption via
        // SYSTEM_ALERT_WINDOW. The overlay is removed by AlarmActivity.onCreate().
        TimerService.addOverlay(context)

        Log.i(TAG, "onReceive: launching AlarmActivity (SAW overlay grants bg exemption)")
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, originalDuration)
        }
        context.startActivity(activityIntent)

        // Start the service for state management and alarm feedback.
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_FIRE_EXPIRED
            putExtra(TimerService.EXTRA_ORIGINAL_DURATION_MILLIS, originalDuration)
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_TIMER_EXPIRED = "com.watchtimerapp.action.TIMER_EXPIRED"
        private const val ALARM_CHANNEL_ID = "alarm_channel"
        const val ALARM_NOTIFICATION_ID = 2

        /**
         * Posts a SEPARATE IMPORTANCE_HIGH notification with setFullScreenIntent().
         * This MUST be posted via NotificationManager.notify() — NOT via startForeground().
         * FSI on a foreground service notification does not reliably launch the activity.
         */
        fun fireAlarm(context: Context) {
            Log.d(TAG, "fireAlarm: posting FSI notification (ID=$ALARM_NOTIFICATION_ID)")
            createAlarmChannel(context)

            val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context, 0, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(context.getString(R.string.timer_finished))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(ALARM_NOTIFICATION_ID, notification)
        }

        fun cancelAlarmNotification(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancel(ALARM_NOTIFICATION_ID)
        }

        private fun createAlarmChannel(context: Context) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Timer Alarm",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Timer alarm notifications"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
