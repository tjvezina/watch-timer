package com.watchtimerapp.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.watchtimerapp.AlarmActivity
import com.watchtimerapp.MainActivity
import com.watchtimerapp.R
import com.watchtimerapp.data.TimerState
import com.watchtimerapp.service.TimerService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TIMER_EXPIRED) {
            // If the service already transitioned to Alarming, it handled it.
            // This is the backup path for when Doze delayed the service.
            val currentState = TimerService.timerState.value
            if (currentState is TimerState.Running && !MainActivity.isInForeground) {
                fireAlarm(context)
            }
        }
    }

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.watchtimerapp.action.TIMER_EXPIRED"
        private const val ALARM_CHANNEL_ID = "alarm_channel"
        const val ALARM_NOTIFICATION_ID = 2

        fun fireAlarm(context: Context) {
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
                .setContentTitle("Time's Up!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(ALARM_NOTIFICATION_ID, notification)
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
