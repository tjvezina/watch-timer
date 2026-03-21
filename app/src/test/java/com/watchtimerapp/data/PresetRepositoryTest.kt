package com.watchtimerapp.data

import org.junit.Assert.*
import org.junit.Test

class PresetRepositoryTest {

    @Test
    fun `default presets are 1, 3, 5, 10, 15, 30 minutes`() {
        val defaults = PresetRepository.DEFAULT_PRESETS
        assertEquals(
            listOf(60_000L, 180_000L, 300_000L, 600_000L, 900_000L, 1_800_000L),
            defaults
        )
    }

    @Test
    fun `formatPresetLabel formats minutes only`() {
        assertEquals("1 min", PresetRepository.formatPresetLabel(60_000L))
        assertEquals("5 min", PresetRepository.formatPresetLabel(300_000L))
        assertEquals("30 min", PresetRepository.formatPresetLabel(1_800_000L))
    }

    @Test
    fun `formatPresetLabel formats hours and minutes`() {
        assertEquals("1 hr", PresetRepository.formatPresetLabel(3_600_000L))
        assertEquals("1 hr 30 min", PresetRepository.formatPresetLabel(5_400_000L))
    }

    @Test
    fun `formatPresetLabel formats seconds when under a minute`() {
        assertEquals("30 sec", PresetRepository.formatPresetLabel(30_000L))
        assertEquals("45 sec", PresetRepository.formatPresetLabel(45_000L))
    }
}
