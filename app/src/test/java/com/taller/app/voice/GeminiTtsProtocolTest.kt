package com.taller.app.voice

import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.GeminiTtsParseException
import com.taller.app.voice.neural.GeminiTtsProtocol
import com.taller.app.voice.neural.GeminiWavWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GeminiTtsProtocolTest {

    @Test
    fun config_withoutApiKey_isNotConfigured() {
        val config = GeminiTtsConfig(
            apiKey = "",
            model = GeminiTtsConfig.DEFAULT_MODEL,
            voiceName = GeminiTtsConfig.DEFAULT_VOICE,
            instructions = GeminiTtsConfig.DEFAULT_INSTRUCTIONS
        )

        assertFalse(config.isComplete)
    }

    @Test
    fun providerEnum_recognizesGemini() {
        assertEquals(ToyVoiceProviderType.GEMINI_TTS, ToyVoiceProviderType.valueOf("GEMINI_TTS"))
    }

    @Test
    fun invalidText_isRejectedBeforeProviderCall() {
        val validation = ToyVoiceTextValidator.validate("No hay texto para reproducir")

        assertFalse(validation.isValid)
    }

    @Test
    fun validText_buildsAudioRequest() {
        val request = GeminiTtsProtocol.buildRequestJson("Hola", defaultConfig()).withoutWhitespace()

        assertTrue(request.contains(""""responseModalities":["AUDIO"]"""))
        assertTrue(request.contains(""""text":""""))
    }

    @Test
    fun endpoint_includesModelInUrl() {
        val endpoint = GeminiTtsProtocol.endpointUrl(GeminiTtsConfig.DEFAULT_MODEL)

        assertTrue(endpoint.endsWith("/models/${GeminiTtsConfig.DEFAULT_MODEL}:generateContent"))
    }

    @Test
    fun request_doesNotIncludeModelInBody() {
        // El modelo viaja en la URL; incluirlo en el body provoca HTTP 400.
        val request = GeminiTtsProtocol.buildRequestJson("Hola", defaultConfig()).withoutWhitespace()

        assertFalse(request.contains(""""model":"""))
    }

    @Test
    fun request_usesDefaultPuckVoice() {
        val request = GeminiTtsProtocol.buildRequestJson("Hola", defaultConfig()).withoutWhitespace()

        assertTrue(request.contains(""""voiceName":"Puck""""))
    }

    @Test(expected = GeminiTtsParseException::class)
    fun response_withoutInlineData_failsSafely() {
        GeminiTtsProtocol.parseAudio(
            """{"candidates":[{"content":{"parts":[{"text":"hola"}]}}]}"""
        )
    }

    @Test(expected = GeminiTtsParseException::class)
    fun response_withInvalidBase64_failsSafely() {
        GeminiTtsProtocol.parseAudio(
            """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"***"}}]}}]}"""
        )
    }

    @Test
    fun response_withBase64Audio_parsesPayload() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val encoded = Base64.getEncoder().encodeToString(bytes)

        val payload = GeminiTtsProtocol.parseAudio(
            """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"$encoded"}}]}}]}"""
        )

        assertEquals("audio/pcm", payload.mimeType)
        assertTrue(payload.bytes.contentEquals(bytes))
    }

    @Test
    fun response_withAudioInSecondPart_parsesPayload() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val encoded = Base64.getEncoder().encodeToString(bytes)

        val payload = GeminiTtsProtocol.parseAudio(
            """{"candidates":[{"content":{"parts":[{"text":"nota"},{"inlineData":{"mimeType":"audio/l16; rate=24000; channels=1","data":"$encoded"}}]},"finishReason":"STOP"}]}"""
        )

        assertEquals("audio/l16; rate=24000; channels=1", payload.mimeType)
        assertTrue(payload.bytes.contentEquals(bytes))
    }

    @Test
    fun response_withSnakeCaseInlineData_parsesPayload() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val encoded = Base64.getMimeEncoder(4, "\n".toByteArray()).encodeToString(bytes)

        val payload = GeminiTtsProtocol.parseAudio(
            """{"candidates":[{"content":{"parts":[{"inline_data":{"mime_type":"audio/L16;codec=pcm;rate=24000","data":"$encoded"}}]}}]}"""
        )

        assertEquals("audio/L16;codec=pcm;rate=24000", payload.mimeType)
        assertTrue(payload.bytes.contentEquals(bytes))
    }

    @Test
    fun pcmAudio_isWrappedAsWav() {
        val wav = GeminiWavWriter.wrapPcm16Mono(byteArrayOf(1, 0, 2, 0))

        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("fmt ", wav.copyOfRange(12, 16).toString(Charsets.US_ASCII))
        assertEquals("data", wav.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(48, wav.size)
        assertTrue(GeminiTtsProtocol.shouldWrapAsWav("audio/L16;codec=pcm;rate=24000"))
    }

    @Test
    fun pcmSampleRate_isParsedFromMimeType() {
        assertEquals(24000, GeminiTtsProtocol.pcmSampleRate("audio/L16;codec=pcm;rate=24000"))
        assertEquals(24000, GeminiTtsProtocol.pcmSampleRate("audio/l16; rate=24000; channels=1"))
        assertEquals(16000, GeminiTtsProtocol.pcmSampleRate("audio/L16;rate=16000"))
        assertEquals(24000, GeminiTtsProtocol.pcmSampleRate("audio/pcm"))
    }

    @Test
    fun emptyCandidates_reportsSpecificError() {
        val error = runCatching {
            GeminiTtsProtocol.parseAudio("""{"candidates":[]}""")
        }.exceptionOrNull()

        assertTrue(error is GeminiTtsParseException)
        assertEquals("Gemini fallo: candidates vacios.", (error as GeminiTtsParseException).safeMessage)
    }

    @Test
    fun emptyParts_reportsSpecificError() {
        val error = runCatching {
            GeminiTtsProtocol.parseAudio("""{"candidates":[{"content":{"parts":[]},"finishReason":"STOP"}]}""")
        }.exceptionOrNull()

        assertTrue(error is GeminiTtsParseException)
        assertEquals("Gemini fallo: candidates sin parts.", (error as GeminiTtsParseException).safeMessage)
    }

    @Test
    fun textOnlyResponse_reportsTextualWithoutAudio() {
        val error = runCatching {
            GeminiTtsProtocol.parseAudio("""{"candidates":[{"content":{"parts":[{"text":"hola"}]},"finishReason":"STOP"}]}""")
        }.exceptionOrNull()

        assertTrue(error is GeminiTtsParseException)
        assertEquals("Gemini fallo: respuesta textual sin audio.", (error as GeminiTtsParseException).safeMessage)
    }

    @Test
    fun inlineDataWithoutData_reportsSpecificError() {
        val error = runCatching {
            GeminiTtsProtocol.parseAudio("""{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/l16; rate=24000; channels=1"}}]},"finishReason":"STOP"}]}""")
        }.exceptionOrNull()

        assertTrue(error is GeminiTtsParseException)
        assertEquals("Gemini fallo: inlineData sin data.", (error as GeminiTtsParseException).safeMessage)
    }

    @Test
    fun wavHeader_usesParsedSampleRate() {
        val wav = GeminiWavWriter.wrapPcm16Mono(byteArrayOf(1, 0, 2, 0), sampleRate = 16000)

        // Sample rate (LE) en el offset 24 del header WAV.
        val rate = (wav[24].toInt() and 0xFF) or
            ((wav[25].toInt() and 0xFF) shl 8) or
            ((wav[26].toInt() and 0xFF) shl 16) or
            ((wav[27].toInt() and 0xFF) shl 24)
        assertEquals(16000, rate)
    }

    private fun defaultConfig(): GeminiTtsConfig = GeminiTtsConfig(
        apiKey = "test-key",
        model = GeminiTtsConfig.DEFAULT_MODEL,
        voiceName = GeminiTtsConfig.DEFAULT_VOICE,
        instructions = GeminiTtsConfig.DEFAULT_INSTRUCTIONS
    )

    private fun String.withoutWhitespace(): String = replace("\\s+".toRegex(), "")
}
