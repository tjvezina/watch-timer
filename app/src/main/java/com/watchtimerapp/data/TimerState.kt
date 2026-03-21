package com.watchtimerapp.data

sealed class TimerState {

    abstract fun remainingMillis(): Long

    object Idle : TimerState() {
        override fun remainingMillis(): Long = 0L
    }

    data class Running(
        val endTimeMillis: Long,
        val originalDurationMillis: Long,
    ) : TimerState() {
        override fun remainingMillis(): Long =
            (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    data class Paused(
        val remainingMillis: Long,
        val originalDurationMillis: Long,
    ) : TimerState() {
        override fun remainingMillis(): Long = remainingMillis
    }

    data class Alarming(
        val originalDurationMillis: Long,
    ) : TimerState() {
        override fun remainingMillis(): Long = 0L
    }

    companion object {
        fun formatRemainingTime(millis: Long): String {
            val totalSeconds = (millis + 999) / 1000 // round up
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%d:%02d".format(minutes, seconds)
            }
        }

        fun formatApproxRemainingTime(millis: Long): String {
            if (millis < 60_000L) return "<1 min"
            val totalMinutes = (millis + 59_999) / 60_000 // round up
            return when {
                totalMinutes < 60 -> "$totalMinutes min"
                else -> {
                    val hours = totalMinutes / 60
                    val mins = totalMinutes % 60
                    if (mins == 0L) "$hours hr"
                    else "$hours hr $mins min"
                }
            }
        }
    }
}
