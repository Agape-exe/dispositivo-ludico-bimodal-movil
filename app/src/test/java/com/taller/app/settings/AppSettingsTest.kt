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
    fun defaultsMatchFinalFlow01Decisions() {
        val defaults = AppSettings.defaults()
        // La camara/atencion y la recaptura quedan apagadas por defecto: la sesion
        // inteligente no debe depender de la camara ni interrumpirse por atencion.
        assertFalse(defaults.intelligentAttentionEnabled)
        assertFalse(defaults.intelligentRecaptureEnabled)
        // La conversacion inicial es opcional y viene apagada.
        assertFalse(defaults.initialConversationEnabled)
        assertEquals(1, defaults.initialConversationMaxChildTurns)
        assertEquals(60, defaults.initialConversationMaxDurationSeconds)
        // La captura temprana de respuestas viene activada.
        assertTrue(defaults.earlyAnswerCaptureEnabled)
        assertEquals(2000, defaults.earlyAnswerWindowMs)
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
    fun validatesInitialConversationTurnsRange() {
        assertFalse(AppSettings.isValidInitialConversationTurns(-1))
        assertTrue(AppSettings.isValidInitialConversationTurns(0))
        assertTrue(AppSettings.isValidInitialConversationTurns(3))
        assertFalse(AppSettings.isValidInitialConversationTurns(4))
    }

    @Test
    fun validatesInitialConversationDurationRange() {
        assertFalse(AppSettings.isValidInitialConversationDurationSeconds(14))
        assertTrue(AppSettings.isValidInitialConversationDurationSeconds(15))
        assertTrue(AppSettings.isValidInitialConversationDurationSeconds(120))
        assertFalse(AppSettings.isValidInitialConversationDurationSeconds(121))
    }

    @Test
    fun validatesEarlyAnswerWindowRange() {
        assertFalse(AppSettings.isValidEarlyAnswerWindowMs(499))
        assertTrue(AppSettings.isValidEarlyAnswerWindowMs(500))
        assertTrue(AppSettings.isValidEarlyAnswerWindowMs(4000))
        assertFalse(AppSettings.isValidEarlyAnswerWindowMs(4001))
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

    @Test
    fun sanitizesFinalFlow01OutOfRangeValues() {
        val safe = AppSettings(
            initialConversationMaxChildTurns = 9,
            initialConversationMaxDurationSeconds = 1,
            earlyAnswerWindowMs = 99_999
        ).sanitized(nowMs = 456L)
        assertEquals(3, safe.initialConversationMaxChildTurns)
        assertEquals(15, safe.initialConversationMaxDurationSeconds)
        assertEquals(4000, safe.earlyAnswerWindowMs)
        assertEquals(456L, safe.updatedAt)
    }
}
