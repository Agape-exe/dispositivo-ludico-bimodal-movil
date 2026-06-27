package com.taller.app.gpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GptRuntimeSettingsTest {

    @Test
    fun defaults_useSafeBuildOrFallbackValues() {
        val settings = GptRuntimeSettings.defaults()

        assertTrue(settings.model in GptRuntimeSettings.ALLOWED_MODELS)
        assertTrue(settings.fallbackModel in GptRuntimeSettings.ALLOWED_MODELS)
        assertTrue(settings.maxOutputTokens in 80..400)
        assertTrue(settings.timeoutMs in 3_000..20_000)
        assertTrue(settings.temperature in 0.0f..1.0f)
        assertTrue(settings.localFallbackEnabled)
        assertTrue(settings.structuredOutputsEnabled)
    }

    @Test
    fun sanitized_rejectsInvalidModelsAndClampsRanges() {
        val settings = GptRuntimeSettings(
            model = "gemini-tts",
            fallbackModel = "gpt-4o-mini-tts",
            maxOutputTokens = 40,
            timeoutMs = 1_000,
            temperature = 1.8f,
            structuredOutputsEnabled = false
        ).sanitized()

        assertEquals(GptRuntimeSettings.DEFAULT_MODEL, settings.model)
        assertEquals(GptRuntimeSettings.DEFAULT_FALLBACK_MODEL, settings.fallbackModel)
        assertEquals(80, settings.maxOutputTokens)
        assertEquals(3_000, settings.timeoutMs)
        assertEquals(1.0f, settings.temperature)
        assertTrue(settings.structuredOutputsEnabled)
    }

    @Test
    fun fromBuildRuntime_keepsApiKeyOutOfRuntimeSettings() {
        val runtimeSettings = GptRuntimeSettings(
            enabled = true,
            model = "gpt-5.4",
            fallbackModel = "gpt-5.4-nano",
            maxOutputTokens = 399,
            timeoutMs = 19_000,
            temperature = 0.2f,
            localFallbackEnabled = false
        )

        val config = GptConfig.fromBuild(runtimeSettings)

        assertEquals("gpt-5.4", config.model)
        assertEquals("gpt-5.4-nano", config.fallbackModel)
        assertEquals(399, config.maxOutputTokens)
        assertEquals(19_000L, config.timeoutMs)
        assertEquals(0.2f, config.temperature)
        assertTrue(config.enabled)
        assertFalse(config.localFallbackEnabled)
    }
}
