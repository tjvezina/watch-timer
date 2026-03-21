package com.watchtimerapp.data

import org.junit.Assert.*
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `default sound enabled is true`() {
        assertTrue(SettingsRepository.DEFAULT_SOUND_ENABLED)
    }

    @Test
    fun `default vibration enabled is true`() {
        assertTrue(SettingsRepository.DEFAULT_VIBRATION_ENABLED)
    }
}
