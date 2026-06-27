package com.taller.app.voice

import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.GeminiTtsProtocol
import com.taller.app.voice.neural.GeminiTtsVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTtsVoiceSelectionTest {

    @Test
    fun defaultGeminiVoice_isPuck() {
        assertEquals("Puck", GeminiTtsConfig.DEFAULT_VOICE)
        assertEquals("Puck", GeminiTtsVoices.normalizeId(null))
    }

    @Test
    fun supportedGeminiVoices_includeRequiredList() {
        val ids = GeminiTtsVoices.supported.map { it.id }

        assertEquals(
            listOf(
                "Puck",
                "Leda",
                "Achird",
                "Sadachbia",
                "Sulafat",
                "Aoede",
                "Laomedeia",
                "Autonoe",
                "Zephyr",
                "Kore"
            ),
            ids
        )
    }

    @Test
    fun selectedGeminiVoice_isPreserved() {
        assertEquals("Leda", GeminiTtsVoices.normalizeId("Leda"))
        assertEquals("Sulafat", GeminiTtsVoices.normalizeId("Sulafat"))
    }

    @Test
    fun unknownGeminiVoice_fallsBackToPuck() {
        assertEquals("Puck", GeminiTtsVoices.normalizeId("UnknownVoice"))
    }

    @Test
    fun request_usesSelectedGeminiVoiceName() {
        val config = GeminiTtsConfig(
            apiKey = "test-key",
            model = GeminiTtsConfig.DEFAULT_MODEL,
            voiceName = GeminiTtsVoices.normalizeId("Sulafat"),
            instructions = GeminiTtsConfig.DEFAULT_INSTRUCTIONS
        )

        val request = GeminiTtsProtocol.buildRequestJson("Hola", config).withoutWhitespace()

        assertTrue(request.contains(""""voiceName":"Sulafat""""))
    }

    private fun String.withoutWhitespace(): String = replace("\\s+".toRegex(), "")
}
