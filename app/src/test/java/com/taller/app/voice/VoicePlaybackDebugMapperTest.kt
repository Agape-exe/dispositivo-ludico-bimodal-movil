package com.taller.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlaybackDebugMapperTest {

    private fun metric(
        provider: ToyVoiceProviderType?,
        voiceContext: VoiceContext = VoiceContext.QUESTION,
        cacheHit: Boolean? = null,
        textHash: String? = "ab12cd34"
    ) = VoicePlaybackMetric(
        eventType = VoiceMetricEventType.VOICE_PLAYBACK_COMPLETED,
        timestamp = 0L,
        mode = VoiceMode.INTELLIGENT,
        voiceContext = voiceContext,
        providerRequested = ToyVoiceProviderType.GEMINI_TTS,
        providerUsed = provider,
        fallbackUsed = false,
        model = "gemini-3.1-flash-tts-preview",
        voice = "Puck",
        cacheHit = cacheHit,
        textHash = textHash,
        cacheLookupLatencyMs = 12L,
        playbackStartLatencyMs = 80L,
        totalVoiceLatencyMs = 980L
    )

    private fun completed(
        providerUsed: ToyVoiceProviderType,
        fallbackUsed: Boolean = false,
        cacheHit: Boolean? = null,
        errorType: VoiceErrorType? = null,
        voiceContext: VoiceContext = VoiceContext.QUESTION
    ) = VoiceOutcome.Completed(
        providerRequested = ToyVoiceProviderType.GEMINI_TTS,
        providerUsed = providerUsed,
        fallbackUsed = fallbackUsed,
        errorMessage = null,
        errorType = errorType,
        latencyMs = 980L,
        cacheHit = cacheHit,
        cacheLookupLatencyMs = 12L,
        totalLatencyMs = 980L,
        metric = metric(providerUsed, voiceContext, cacheHit)
    )

    @Test
    fun cacheHitMapsToCacheHitSource() {
        val info = VoicePlaybackDebugMapper.fromOutcome(
            outcome = completed(ToyVoiceProviderType.GEMINI_TTS, cacheHit = true),
            playbackMode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE
        )
        assertEquals(VoicePlaybackSource.CACHE_HIT, info.source)
        assertEquals("Gemini", info.provider)
        assertEquals("Puck", info.voice)
        assertEquals(true, info.cacheHit)
        assertEquals(12L, info.cacheLookupMs)
        assertEquals(80L, info.playbackStartMs)
        assertEquals(980L, info.totalVoiceMs)
        assertEquals("QUESTION", info.lineType)
        assertEquals("ab12cd34", info.textHashShort)
        assertNull(info.fallbackReason)
    }

    @Test
    fun cacheMissSynthesisMapsToNetworkSynthesis() {
        val info = VoicePlaybackDebugMapper.fromOutcome(
            outcome = completed(ToyVoiceProviderType.GEMINI_TTS, cacheHit = false),
            playbackMode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE
        )
        assertEquals(VoicePlaybackSource.NETWORK_SYNTHESIS, info.source)
        assertEquals(false, info.cacheHit)
    }

    @Test
    fun fallbackMapsToFallbackProviderWithReason() {
        val info = VoicePlaybackDebugMapper.fromOutcome(
            outcome = completed(
                providerUsed = ToyVoiceProviderType.OPENAI_TTS,
                fallbackUsed = true,
                cacheHit = false,
                errorType = VoiceErrorType.RATE_LIMITED
            ),
            playbackMode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE
        )
        assertEquals(VoicePlaybackSource.FALLBACK_PROVIDER, info.source)
        assertEquals("OpenAI", info.provider)
        assertEquals("RATE_LIMIT", info.fallbackReason)
    }

    @Test
    fun localProviderMapsToLocalTts() {
        val info = VoicePlaybackDebugMapper.fromOutcome(
            outcome = completed(ToyVoiceProviderType.LOCAL, cacheHit = null),
            playbackMode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE
        )
        assertEquals(VoicePlaybackSource.LOCAL_TTS, info.source)
        assertEquals("Android local", info.provider)
    }

    @Test
    fun cacheOnlyFailureMapsToCacheMissWithoutNetworkError() {
        val failed = VoiceOutcome.Failed(
            providerRequested = ToyVoiceProviderType.GEMINI_TTS,
            errorMessage = "La voz de Seven para esta frase aun no esta preparada.",
            latencyMs = 5L,
            errorType = VoiceErrorType.NOT_CONFIGURED
        )
        val info = VoicePlaybackDebugMapper.fromOutcome(
            outcome = failed,
            playbackMode = VoicePlaybackMode.CACHE_ONLY
        )
        // En cache-only un fallo es CACHE_MISS, no ERROR: no hubo llamada de red.
        assertEquals(VoicePlaybackSource.CACHE_MISS, info.source)
        assertEquals("NOT_PREPARED", info.fallbackReason)
    }

    @Test
    fun failureInSynthesisModeMapsToError() {
        val failed = VoiceOutcome.Failed(
            providerRequested = ToyVoiceProviderType.GEMINI_TTS,
            errorMessage = "Gemini fallo: sin conexion disponible.",
            latencyMs = 30L,
            errorType = VoiceErrorType.NO_NETWORK
        )
        val info = VoicePlaybackDebugMapper.fromOutcome(
            outcome = failed,
            playbackMode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE
        )
        assertEquals(VoicePlaybackSource.ERROR, info.source)
        assertEquals("NETWORK_ERROR", info.fallbackReason)
        assertTrue(info.sanitizedError!!.isNotBlank())
    }

    @Test
    fun nullOutcomeMapsToError() {
        val info = VoicePlaybackDebugMapper.fromOutcome(
            outcome = null,
            playbackMode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE
        )
        assertEquals(VoicePlaybackSource.ERROR, info.source)
        assertEquals("SIN_RESULTADO_O_TIMEOUT", info.sanitizedError)
    }

    @Test
    fun lineTypeFromContextMapsKnownContexts() {
        assertEquals("INTRO", VoicePlaybackDebugMapper.lineTypeFromContext(VoiceContext.GREETING))
        assertEquals("QUESTION", VoicePlaybackDebugMapper.lineTypeFromContext(VoiceContext.QUESTION))
        assertEquals("POSITIVE_FEEDBACK", VoicePlaybackDebugMapper.lineTypeFromContext(VoiceContext.FEEDBACK_CORRECT))
        assertEquals("CLOSING", VoicePlaybackDebugMapper.lineTypeFromContext(VoiceContext.CLOSING))
        assertEquals("UNKNOWN", VoicePlaybackDebugMapper.lineTypeFromContext(null))
    }

    @Test
    fun sanitizeErrorRedactsCredentialsAndTruncates() {
        val sanitized = VoicePlaybackDebugMapper.sanitizeError("Error api_key=SECRET12345 con\nsalto")
        assertNotNull(sanitized)
        assertFalse(sanitized!!.contains("SECRET12345"))
        assertTrue(sanitized.contains("credential_redacted"))
        assertFalse(sanitized.contains("\n"))
    }

    @Test
    fun longErrorIsTruncated() {
        val sanitized = VoicePlaybackDebugMapper.sanitizeError("x".repeat(500))
        assertNotNull(sanitized)
        assertTrue(sanitized!!.length <= 90)
    }
}
