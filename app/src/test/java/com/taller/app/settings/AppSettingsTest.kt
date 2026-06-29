package com.taller.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun defaultsMatchCfg01Recommendations() {
        val defaults = AppSettings.defaults()
        assertEquals(10, defaults.classicResponseTimeSeconds)
        assertEquals(5, defaults.intelligentMaxRecaptures)
    }

    @Test
    fun validatesClassicResponseTimeRange() {
        assertFalse(AppSettings.isValidClassicResponseTime(4))
        assertTrue(AppSettings.isValidClassicResponseTime(5))
        assertTrue(AppSettings.isValidClassicResponseTime(120))
        assertFalse(AppSettings.isValidClassicResponseTime(121))
    }

    @Test
    fun validatesIntelligentRecaptureRange() {
        assertFalse(AppSettings.isValidIntelligentMaxRecaptures(-1))
        assertTrue(AppSettings.isValidIntelligentMaxRecaptures(0))
        assertTrue(AppSettings.isValidIntelligentMaxRecaptures(10))
        assertFalse(AppSettings.isValidIntelligentMaxRecaptures(11))
    }

    @Test
    fun sanitizesOutOfRangeValues() {
        val safe = AppSettings(
            classicResponseTimeSeconds = 500,
            intelligentMaxRecaptures = -3
        ).sanitized(nowMs = 123L)
        assertEquals(120, safe.classicResponseTimeSeconds)
        assertEquals(0, safe.intelligentMaxRecaptures)
        assertEquals(123L, safe.updatedAt)
    }
}
