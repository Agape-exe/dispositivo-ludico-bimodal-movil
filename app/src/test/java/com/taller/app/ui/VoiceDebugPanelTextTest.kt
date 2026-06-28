package com.taller.app.ui

import com.taller.app.gpt.GptConfig
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.VoicePlaybackDebugInfo
import com.taller.app.voice.VoicePlaybackMode
import com.taller.app.voice.VoicePlaybackSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDebugPanelTextTest {

    private fun gptConfig() = GptConfig(
        apiKey = "",
        model = "gpt-5.4-mini",
        fallbackModel = "gpt-5.4-nano",
        maxOutputTokens = 220,
        timeoutMs = 12_000L,
        temperature = 0.4f,
        enabled = false,
        localFallbackEnabled = true,
        structuredOutputsEnabled = true
    )

    private fun panel(voiceDebug: VoicePlaybackDebugInfo?): String =
        intelligentDebugPanelText(
            showAttention = false,
            attentionSnapshot = null,
            showTts = true,
            ttsProviderConfigured = ToyVoiceProviderType.GEMINI_TTS,
            ttsProviderUsedLabel = "Gemini",
            ttsVoice = "Puck",
            ttsFallbackUsed = false,
            ttsStatus = "OK",
            ttsContextLabel = "QUESTION",
            ttsFallbackReason = "NONE",
            ttsLatencyMs = 980L,
            voiceDebug = voiceDebug,
            showGpt = false,
            gptConfig = gptConfig(),
            lastGptUsageStatus = "sin datos"
        )

    @Test
    fun showsCacheHitDiagnosticsWithoutSpokenText() {
        val debug = VoicePlaybackDebugInfo.none().copy(
            source = VoicePlaybackSource.CACHE_HIT,
            playbackMode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE,
            provider = "Gemini",
            voice = "Puck",
            lineType = "QUESTION",
            cacheHit = true,
            cacheLookupMs = 12L,
            playbackStartMs = 80L,
            totalVoiceMs = 980L,
            textHashShort = "ab12cd34",
            updatedAtMs = 1L
        )
        val label = panel(debug)

        assertTrue(label.contains("Origen voz: CACHE_HIT"))
        assertTrue(label.contains("Modo repro: CACHE_OR_SYNTHESIZE"))
        assertTrue(label.contains("Linea: QUESTION"))
        assertTrue(label.contains("Cache: hit"))
        assertTrue(label.contains("Cache lookup: 12 ms"))
        assertTrue(label.contains("Total voz: 980 ms"))
        assertTrue(label.contains("Hash pieza: ab12cd34"))
        // Solo hash corto: nunca el texto hablado completo.
        assertFalse(label.contains("¿"))
    }

    @Test
    fun omitsCacheDiagnosticsWhenNoVoiceYet() {
        val label = panel(VoicePlaybackDebugInfo.none())
        assertFalse(label.contains("Origen voz:"))
        // Mantiene las metricas previas (compatibilidad).
        assertTrue(label.contains("TTS preferido: Gemini / Puck"))
    }
}
