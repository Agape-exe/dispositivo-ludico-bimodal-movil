package com.taller.app.voice

import com.taller.app.voice.neural.OpenAiTtsAudioCache
import com.taller.app.voice.neural.VoiceCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceCacheKeyTest {

    @Test
    fun cacheKey_includesProvider() {
        val gemini = key(provider = ToyVoiceProviderType.GEMINI_TTS)
        val openAi = key(provider = ToyVoiceProviderType.OPENAI_TTS)

        assertNotEquals(gemini, openAi)
    }

    @Test
    fun cacheKey_includesModel() {
        assertNotEquals(key(model = "model-a"), key(model = "model-b"))
    }

    @Test
    fun cacheKey_includesVoice() {
        assertNotEquals(key(voice = "Puck"), key(voice = "Leda"))
    }

    @Test
    fun cacheKey_includesInstructions() {
        assertNotEquals(key(instructions = "Seven natural"), key(instructions = "Seven ludico"))
    }

    @Test
    fun cacheKey_includesResponseFormat() {
        assertNotEquals(key(format = "wav"), key(format = "mp3"))
    }

    @Test
    fun cacheKey_usesNormalizedText() {
        assertEquals(key(text = "Hola Seven"), key(text = "  Hola   Seven  "))
    }

    @Test
    fun cacheKey_geminiPuckDiffersFromGeminiLeda() {
        assertNotEquals(
            key(provider = ToyVoiceProviderType.GEMINI_TTS, voice = "Puck"),
            key(provider = ToyVoiceProviderType.GEMINI_TTS, voice = "Leda")
        )
    }

    @Test
    fun cacheKey_geminiPuckDiffersFromOpenAiSameText() {
        assertNotEquals(
            key(provider = ToyVoiceProviderType.GEMINI_TTS, voice = "Puck", text = "Hola"),
            key(provider = ToyVoiceProviderType.OPENAI_TTS, voice = "Puck", text = "Hola")
        )
    }

    @Test
    fun invalidTextDoesNotGenerateCacheKey() {
        assertNull(
            VoiceCacheKey.keyFor(
                text = "No hay texto para reproducir",
                provider = ToyVoiceProviderType.GEMINI_TTS,
                model = "gemini",
                voice = "Puck",
                instructions = "Seven natural",
                responseFormat = OpenAiTtsAudioCache.RESPONSE_FORMAT_WAV
            )
        )
    }

    @Test
    fun entryFor_usesHashedFileNameWithoutFullText() {
        val cacheDir = File("build/tmp/voice-cache-key-test")
        val entry = VoiceCacheKey.entryFor(
            text = "Hola Seven explorador",
            provider = ToyVoiceProviderType.GEMINI_TTS,
            model = "gemini",
            voice = "Puck",
            instructions = "Seven natural",
            responseFormat = OpenAiTtsAudioCache.RESPONSE_FORMAT_WAV,
            cacheDir = cacheDir
        )

        assertTrue(entry.file.name.startsWith("gemini-"))
        assertTrue(entry.file.name.endsWith(".wav"))
        assertTrue(!entry.file.name.contains("Hola"))
        assertEquals(12, entry.shortKey.length)
    }

    private fun key(
        text: String = "Hola Seven",
        provider: ToyVoiceProviderType = ToyVoiceProviderType.GEMINI_TTS,
        model: String = "gemini-tts",
        voice: String = "Puck",
        instructions: String = "Seven natural",
        format: String = OpenAiTtsAudioCache.RESPONSE_FORMAT_WAV
    ): String = requireNotNull(
        VoiceCacheKey.keyFor(
            text = text,
            provider = provider,
            model = model,
            voice = voice,
            instructions = instructions,
            responseFormat = format
        )
    )
}
