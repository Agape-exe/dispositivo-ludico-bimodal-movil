package com.taller.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsJsonExporterTest {

    private val exporter = MetricsJsonExporter()

    // -------------------------------------------------------------------------
    // Estructura básica
    // -------------------------------------------------------------------------

    @Test
    fun exportEmpty_contieneSchemaVersionYTimestamp() {
        val json = exporter.export(emptyList(), exportedAtMs = 1000L)
        assertTrue(json.contains("\"schemaVersion\": \"1.0\""))
        assertTrue(json.contains("\"exportedAtMs\": 1000"))
    }

    @Test
    fun exportEmpty_sessionesEsListaVacia() {
        val json = exporter.export(emptyList(), exportedAtMs = 1000L)
        assertTrue(json.contains("\"sessions\": []"))
    }

    @Test
    fun exportEmpty_noCrashea() {
        val json = exporter.export(emptyList(), exportedAtMs = 0L)
        assertTrue(json.startsWith("{"))
        assertTrue(json.trimEnd().endsWith("}"))
    }

    // -------------------------------------------------------------------------
    // Sesión bimodal
    // -------------------------------------------------------------------------

    @Test
    fun sesionBimodal_incluyeSemanticResult() {
        val sessions = listOf(bimodalSession())
        val json = exporter.export(sessions, exportedAtMs = 2000L)
        assertTrue(json.contains("\"semanticResult\": \"CORRECT\""))
    }

    @Test
    fun sesionBimodal_classicResultEsNull() {
        val sessions = listOf(bimodalSession())
        val json = exporter.export(sessions, exportedAtMs = 2000L)
        assertTrue(json.contains("\"classicResult\": null"))
    }

    @Test
    fun sesionBimodal_incluyeLatencias() {
        val sessions = listOf(bimodalSession())
        val json = exporter.export(sessions, exportedAtMs = 2000L)
        assertTrue(json.contains("\"totalResponseLatencyMs\": 150"))
        assertTrue(json.contains("\"fullPipelineLatencyMs\": 950"))
    }

    @Test
    fun sesionBimodal_incluyeResumen() {
        val sessions = listOf(bimodalSession())
        val json = exporter.export(sessions, exportedAtMs = 2000L)
        assertTrue(json.contains("\"correctCount\": 1"))
        assertTrue(json.contains("\"incorrectCount\": 0"))
        assertTrue(json.contains("\"totalAttempts\": 1"))
    }

    // -------------------------------------------------------------------------
    // Sesión clásica
    // -------------------------------------------------------------------------

    @Test
    fun sesionClasica_semanticResultEsNull() {
        val sessions = listOf(classicSession())
        val json = exporter.export(sessions, exportedAtMs = 3000L)
        assertTrue(json.contains("\"semanticResult\": null"))
    }

    @Test
    fun sesionClasica_incluyeClassicResult() {
        val sessions = listOf(classicSession())
        val json = exporter.export(sessions, exportedAtMs = 3000L)
        assertTrue(json.contains("\"classicResult\": \"ANSWERED\""))
    }

    @Test
    fun sesionClasica_correctCountEsNull() {
        val sessions = listOf(classicSession())
        val json = exporter.export(sessions, exportedAtMs = 3000L)
        assertTrue(json.contains("\"correctCount\": null"))
        assertTrue(json.contains("\"incorrectCount\": null"))
    }

    // -------------------------------------------------------------------------
    // Eventos técnicos
    // -------------------------------------------------------------------------

    @Test
    fun eventosIncluidos_aparecenEnJson() {
        val sessions = listOf(sessionWithEvents())
        val json = exporter.export(sessions, exportedAtMs = 4000L)
        assertTrue(json.contains("\"technicalEvents\""))
        assertTrue(json.contains("\"STT_STARTED\""))
        assertTrue(json.contains("\"latencyMs\": null"))
    }

    @Test
    fun eventoConLatencia_aparece() {
        val sessions = listOf(sessionWithEvents())
        val json = exporter.export(sessions, exportedAtMs = 4000L)
        assertTrue(json.contains("\"latencyMs\": 320"))
    }

    @Test
    fun eventoVoz_incluyeCamposDeVozEnJson() {
        val event = ExportTechnicalEventDto(
            eventId = 3L,
            questionId = 10L,
            attemptId = 1L,
            operationMode = "ADVANCED",
            eventType = "VOICE_PLAYBACK_COMPLETED",
            message = "voiceProviderRequested=GEMINI_TTS voiceProviderUsed=GEMINI_TTS",
            timestampMs = 2000L,
            latencyMs = 250L,
            voiceProviderRequested = "GEMINI_TTS",
            voiceProviderUsed = "GEMINI_TTS",
            voiceFallbackUsed = "false",
            voiceModel = "gemini-3.1-flash-tts-preview",
            voiceName = "Puck",
            voiceCacheHit = "true",
            voiceSynthesisLatencyMs = "0",
            voicePlaybackDurationMs = "120",
            voiceTotalLatencyMs = "250",
            voiceErrorType = null,
            voiceContext = "QUESTION"
        )
        val json = exporter.export(
            listOf(bimodalSession().copy(technicalEvents = listOf(event))),
            exportedAtMs = 4000L
        )

        assertTrue(json.contains("\"voiceProviderRequested\": \"GEMINI_TTS\""))
        assertTrue(json.contains("\"voiceProviderUsed\": \"GEMINI_TTS\""))
        assertTrue(json.contains("\"voiceModel\": \"gemini-3.1-flash-tts-preview\""))
        assertTrue(json.contains("\"voiceContext\": \"QUESTION\""))
    }

    // -------------------------------------------------------------------------
    // Escapes JSON
    // -------------------------------------------------------------------------

    @Test
    fun jsonStr_null_retornaLiteralNull() {
        assertEquals("null", MetricsJsonExporter.jsonStr(null))
    }

    @Test
    fun jsonStr_cadenaSimple_retornaConComillas() {
        assertEquals("\"azul\"", MetricsJsonExporter.jsonStr("azul"))
    }

    @Test
    fun jsonStr_escapaDobleComilla() {
        assertEquals("\"say \\\"hello\\\"\"", MetricsJsonExporter.jsonStr("say \"hello\""))
    }

    @Test
    fun jsonStr_escapaSaltoDeLinea() {
        assertEquals("\"linea1\\nlinea2\"", MetricsJsonExporter.jsonStr("linea1\nlinea2"))
    }

    @Test
    fun jsonStr_escapaRetornoCarro() {
        assertEquals("\"a\\rb\"", MetricsJsonExporter.jsonStr("a\rb"))
    }

    @Test
    fun jsonStr_escapaBackslash() {
        assertEquals("\"path\\\\file\"", MetricsJsonExporter.jsonStr("path\\file"))
    }

    @Test
    fun jsonNum_null_retornaLiteralNull() {
        assertEquals("null", MetricsJsonExporter.jsonNum(null as Long?))
    }

    @Test
    fun jsonNum_valor_retornaString() {
        assertEquals("42", MetricsJsonExporter.jsonNum(42L))
    }

    // -------------------------------------------------------------------------
    // Múltiples sesiones
    // -------------------------------------------------------------------------

    @Test
    fun dosSessiones_ambasApareceEnJson() {
        val sessions = listOf(bimodalSession(), classicSession())
        val json = exporter.export(sessions, exportedAtMs = 5000L)
        assertTrue(json.contains("\"sessionId\": 1"))
        assertTrue(json.contains("\"sessionId\": 2"))
        assertTrue(json.contains("BIMODAL_INTELLIGENT"))
        assertTrue(json.contains("CLASSIC"))
    }

    @Test
    fun sesionSinIntentos_attemptsEsListaVacia() {
        val session = bimodalSession().copy(attempts = emptyList())
        val json = exporter.export(listOf(session), exportedAtMs = 6000L)
        assertTrue(json.contains("\"attempts\": []"))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun bimodalSession() = ExportSessionDto(
        sessionId = 1L,
        activityId = 1L,
        activityName = "Actividad de prueba",
        operationMode = "BIMODAL_INTELLIGENT",
        startedAtMs = 1000L,
        finishedAtMs = 5000L,
        finalState = "SESSION_COMPLETED",
        totalDurationMs = 4000L,
        summary = ExportSessionSummaryDto(
            totalQuestions = 1,
            completedQuestions = 1,
            totalAttempts = 1,
            correctCount = 1,
            incorrectCount = 0,
            noResponseCount = 0,
            notInterpretableCount = 0,
            timeoutCount = 0,
            technicalErrorCount = 0
        ),
        attempts = listOf(bimodalAttempt()),
        technicalEvents = emptyList()
    )

    private fun bimodalAttempt() = ExportAttemptDto(
        attemptId = 1L,
        questionId = 10L,
        questionOrder = 0,
        attemptNumber = 1,
        operationMode = "BIMODAL_INTELLIGENT",
        questionText = "¿De qué color es el cielo?",
        maxTimeMs = 30000L,
        startedAtMs = 1100L,
        responseReceivedAtMs = 2000L,
        finishedAtMs = 2100L,
        realResponseTimeMs = 900L,
        transcription = "azul",
        semanticResult = "CORRECT",
        classicResult = null,
        finalAttemptState = "FEEDBACK_CORRECT",
        usedSemanticEvaluation = true,
        usedSpeechToText = true,
        wasFinalAttempt = true,
        advancedFeedbackType = "CORRECT",
        sttStartAtMs = 1150L,
        sttFinalAtMs = 1800L,
        semanticStartAtMs = 1820L,
        semanticEndAtMs = 1900L,
        logicalResponseAtMs = 1950L,
        feedbackStartAtMs = 2000L,
        totalResponseLatencyMs = 150L,
        responseToFeedbackLatencyMs = 50L,
        fullPipelineLatencyMs = 950L
    )

    private fun classicSession() = ExportSessionDto(
        sessionId = 2L,
        activityId = 2L,
        activityName = "Actividad clásica",
        operationMode = "CLASSIC",
        startedAtMs = 2000L,
        finishedAtMs = 6000L,
        finalState = "SESSION_COMPLETED",
        totalDurationMs = 4000L,
        summary = ExportSessionSummaryDto(
            totalQuestions = 1,
            completedQuestions = 1,
            totalAttempts = 1,
            correctCount = null,
            incorrectCount = null,
            noResponseCount = 0,
            notInterpretableCount = null,
            timeoutCount = 0,
            technicalErrorCount = 0
        ),
        attempts = listOf(classicAttempt()),
        technicalEvents = emptyList()
    )

    private fun classicAttempt() = ExportAttemptDto(
        attemptId = 2L,
        questionId = 20L,
        questionOrder = 0,
        attemptNumber = 1,
        operationMode = "CLASSIC",
        questionText = "¿Cuánto es 2+2?",
        maxTimeMs = 15000L,
        startedAtMs = 2100L,
        responseReceivedAtMs = 3000L,
        finishedAtMs = 3100L,
        realResponseTimeMs = 900L,
        transcription = "cuatro",
        semanticResult = null,
        classicResult = "ANSWERED",
        finalAttemptState = "ANSWER_RECEIVED",
        usedSemanticEvaluation = false,
        usedSpeechToText = true,
        wasFinalAttempt = true,
        advancedFeedbackType = null,
        sttStartAtMs = null,
        sttFinalAtMs = null,
        semanticStartAtMs = null,
        semanticEndAtMs = null,
        logicalResponseAtMs = null,
        feedbackStartAtMs = null,
        totalResponseLatencyMs = null,
        responseToFeedbackLatencyMs = null,
        fullPipelineLatencyMs = null
    )

    private fun sessionWithEvents() = bimodalSession().copy(
        technicalEvents = listOf(
            ExportTechnicalEventDto(
                eventId = 1L,
                questionId = 10L,
                attemptId = 1L,
                operationMode = "BIMODAL_INTELLIGENT",
                eventType = "STT_STARTED",
                message = null,
                timestampMs = 1150L,
                latencyMs = null
            ),
            ExportTechnicalEventDto(
                eventId = 2L,
                questionId = 10L,
                attemptId = 1L,
                operationMode = "BIMODAL_INTELLIGENT",
                eventType = "STT_FINAL",
                message = null,
                timestampMs = 1800L,
                latencyMs = 320L
            )
        )
    )
}
