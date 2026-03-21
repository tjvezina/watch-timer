package com.watchtimerapp.data

import org.junit.Assert.*
import org.junit.Test

class TimerStateTest {

    @Test
    fun `idle state has no remaining time`() {
        val state = TimerState.Idle
        assertEquals(0L, state.remainingMillis())
    }

    @Test
    fun `running state calculates remaining time from end time`() {
        val now = System.currentTimeMillis()
        val endTime = now + 60_000L
        val state = TimerState.Running(endTimeMillis = endTime, originalDurationMillis = 60_000L)
        val remaining = state.remainingMillis()
        assertTrue("Remaining should be ~60s, was $remaining", remaining in 59_000L..60_000L)
    }

    @Test
    fun `running state remaining never goes negative`() {
        val pastEndTime = System.currentTimeMillis() - 5_000L
        val state = TimerState.Running(endTimeMillis = pastEndTime, originalDurationMillis = 60_000L)
        assertEquals(0L, state.remainingMillis())
    }

    @Test
    fun `paused state returns stored remaining time`() {
        val state = TimerState.Paused(remainingMillis = 45_000L, originalDurationMillis = 60_000L)
        assertEquals(45_000L, state.remainingMillis())
    }

    @Test
    fun `alarming state has no remaining time`() {
        val state = TimerState.Alarming(originalDurationMillis = 60_000L)
        assertEquals(0L, state.remainingMillis())
    }

    @Test
    fun `formatRemainingTime formats minutes and seconds`() {
        assertEquals("5:00", TimerState.formatRemainingTime(300_000L))
        assertEquals("1:30", TimerState.formatRemainingTime(90_000L))
        assertEquals("0:05", TimerState.formatRemainingTime(5_000L))
        assertEquals("0:00", TimerState.formatRemainingTime(0L))
    }

    @Test
    fun `formatRemainingTime includes hours when over 60 minutes`() {
        assertEquals("1:00:00", TimerState.formatRemainingTime(3_600_000L))
        assertEquals("1:30:00", TimerState.formatRemainingTime(5_400_000L))
        assertEquals("2:05:30", TimerState.formatRemainingTime(7_530_000L))
    }

    @Test
    fun `formatApproxRemainingTime shows approximate values`() {
        assertEquals("5 min", TimerState.formatApproxRemainingTime(300_000L))
        assertEquals("2 min", TimerState.formatApproxRemainingTime(90_000L))
        assertEquals("<1 min", TimerState.formatApproxRemainingTime(30_000L))
        assertEquals("1 hr", TimerState.formatApproxRemainingTime(3_600_000L))
        assertEquals("1 hr 30 min", TimerState.formatApproxRemainingTime(5_400_000L))
    }
}
