package com.taller.app.bimodal

import com.taller.app.voice.VoicePlaybackDebugInfo
import com.taller.app.voice.VoicePlaybackMode
import com.taller.app.voice.VoicePlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentSessionReportTest {

    private fun voice(
        source: VoicePlaybackSource,
        mode: VoicePlaybackMode = VoicePlaybackMode.CACHE_ONLY,
        provider: String? = "Gemini",
        lineType: String = "QUESTION",
        playbackStartMs: Long? = 80L,
        totalVoiceMs: Long? = 900L
    ) = VoicePlaybackDebugInfo.none().copy(
        source = source,
        playbackMode = mode,
        provider = provider,
        lineType = lineType,
        playbackStartMs = playbackStartMs,
        totalVoiceMs = totalVoiceMs,
        textHashShort = "abc123",
        updatedAtMs = 1L
    )

    @Test
    fun summarizeCountsSourcesAndModes() {
        val events = listOf(
            voice(VoicePlaybackSource.CACHE_HIT),
            voice(VoicePlaybackSource.CACHE_HIT),
            voice(VoicePlaybackSource.CACHE_MISS),
            voice(
                VoicePlaybackSource.NETWORK_SYNTHESIS,
                mode = VoicePlaybackMode.CACHE_OR_SYNTHESIZE
            ),
            VoicePlaybackDebugInfo.none() // NONE: se ignora
        )
        val stats = IntelligentSessionReport.summarizeVoice(events)

        assertEquals(4, stats.total)
        assertEquals(2, stats.cacheHit)
        assertEquals(1, stats.cacheMiss)
        assertEquals(1, stats.networkSynthesis)
        assertEquals(3, stats.cacheOnly)
        assertEquals(1, stats.cacheOrSynthesize)
        assertEquals(4, stats.gemini)
        assertTrue(stats.anyNonCacheOnly)
        assertEquals(80L, stats.avgPlaybackStartMs)
        assertEquals(80L, stats.maxPlaybackStartMs)
    }

    @Test
    fun allCacheOnlyReportsNoNonCacheOnly() {
        val events = listOf(
            voice(VoicePlaybackSource.CACHE_HIT),
            voice(VoicePlaybackSource.CACHE_HIT)
        )
        val stats = IntelligentSessionReport.summarizeVoice(events)
        assertFalse(stats.anyNonCacheOnly)
    }

    @Test
    fun reportTextHasSectionsAndNoCredentials() {
        val voiceEvents = listOf(voice(VoicePlaybackSource.CACHE_HIT))
        val evaluations = listOf(
            AnswerEvaluationDebugInfo(
                questionOrder = 1,
                questionId = 10L,
                questionTextShort = "¿Qué animal dice guau?",
                expectedAnswerShort = "perro",
                sttFinalTranscriptShort = "el perro",
                localEvaluationResult = "CORRECT",
                localReason = null,
                evaluationLatencyMs = 12L,
                attemptNumber = 1
            )
        )
        val text = IntelligentSessionReport.buildReportText(voiceEvents, evaluations)

        assertTrue(text.contains("Resumen de voz"))
        assertTrue(text.contains("Historial de voces"))
        assertTrue(text.contains("Evaluacion hibrida"))
        assertTrue(text.contains("CACHE_HIT"))
        assertTrue(text.contains("CORRECT"))
        assertFalse(text.contains("api_key"))
        assertFalse(text.contains("Bearer"))
    }

    @Test
    fun reportShowsSoundAliasReasonAndNormalizedAnswer() {
        val voiceEvents = listOf(voice(VoicePlaybackSource.CACHE_HIT))
        val evaluations = listOf(
            AnswerEvaluationDebugInfo(
                questionOrder = 2,
                questionId = 11L,
                questionTextShort = "¿Qué sonido hace el perro?",
                expectedAnswerShort = "guau",
                sttFinalTranscriptShort = "wow",
                localEvaluationResult = "CORRECT",
                localReason = "alias de sonido: wow -> guau",
                localNormalizedAnswerShort = "wow",
                evaluationLatencyMs = 8L,
                attemptNumber = 1,
                decisionLayer = "LOCAL",
                finalResult = "CORRECT"
            )
        )
        val text = IntelligentSessionReport.buildReportText(voiceEvents, evaluations)

        assertTrue(text.contains("alias de sonido: wow -> guau"))
        assertTrue(text.contains("Normalizada: wow"))
        // El reporte no expone prompts ni payloads.
        assertFalse(text.contains("systemInstruction"))
        assertFalse(text.contains("api_key"))
    }

    @Test
    fun reportShowsAcceptanceType() {
        val voiceEvents = listOf(voice(VoicePlaybackSource.CACHE_HIT))
        val evaluations = listOf(
            AnswerEvaluationDebugInfo(
                questionOrder = 3,
                questionId = 12L,
                questionTextShort = "Menciona una fruta",
                expectedAnswerShort = "manzana",
                sttFinalTranscriptShort = "una banana",
                localEvaluationResult = "CORRECT",
                localReason = "equivalencia infantil",
                evaluationLatencyMs = 9L,
                attemptNumber = 1,
                decisionLayer = "LOCAL",
                finalResult = "CORRECT",
                acceptanceType = "EQUIVALENCIA"
            )
        )
        val text = IntelligentSessionReport.buildReportText(voiceEvents, evaluations)

        assertTrue(text.contains("Tipo de aceptacion: EQUIVALENCIA"))
        // Compatibilidad: una evaluacion sin acceptanceType muestra el guion.
        val legacy = IntelligentSessionReport.buildReportText(
            voiceEvents,
            evaluations.map { it.copy(acceptanceType = null) }
        )
        assertTrue(legacy.contains("Tipo de aceptacion: —"))
    }

    @Test
    fun shortSafeRedactsAndTruncates() {
        val redacted = IntelligentSessionReport.shortSafe("api_key=SECRET12345 hola")
        assertTrue(redacted!!.contains("credential_redacted"))
        assertFalse(redacted.contains("SECRET12345"))

        val truncated = IntelligentSessionReport.shortSafe("x".repeat(200), maxLength = 60)
        assertTrue(truncated!!.length <= 60)

        assertEquals(null, IntelligentSessionReport.shortSafe("   "))
    }
}
