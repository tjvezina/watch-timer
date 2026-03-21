package com.watchtimerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stub — implemented in Task 8
    }

    companion object {
        const val ACTION_TIMER_EXPIRED = "com.watchtimerapp.action.TIMER_EXPIRED"
        fun fireAlarm(context: Context) {
            // Stub — implemented in Task 8
        }
    }
}
