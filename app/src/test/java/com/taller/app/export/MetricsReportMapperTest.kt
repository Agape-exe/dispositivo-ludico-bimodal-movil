package com.taller.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsReportMapperTest {

    @Test
    fun classicSession_mapsInternalCountsAndAverageResponseTime() {
        val report = MetricsReportMapper.mapSession(classicSession())

        assertEquals("Temporizador fijo", report.modeLabel)
        assertEquals(1, report.correctCount)
        assertEquals(1, report.incorrectCount)
        assertEquals(1, report.noResponseCount)
        assertEquals(1000L, report.averageResponseTimeMs)
        assertEquals("Completada", report.finalState)
        assertFalse(report.isIntelligent)
    }

    @Test
    fun intelligentSession_mapsAttentionRecapturesAndAttempts() {
        val report = MetricsReportMapper.mapSession(intelligentSession())

        assertEquals("Inteligente", report.modeLabel)
        assertEquals(1, report.attentionLossCount)
        assertEquals(1, report.recaptureCount)
        assertEquals(2, report.totalAttempts)
        assertTrue(report.isIntelligent)
        assertTrue(report.details.first().attentionLost)
        assertTrue(report.details.first().recaptured)
    }

    @Test
    fun missingFields_useFallbackText() {
        val session = intelligentSession().copy(
            activityName = null,
            activityTopic = null,
            attempts = listOf(intelligentAttempt().copy(questionText = null, expectedAnswer = null))
        )

        val report = MetricsReportMapper.mapSession(session)

        assertEquals("--", report.activityName)
        assertEquals("--", report.topic)
        assertEquals("--", report.details.first().questionText)
        assertEquals("--", report.details.first().expectedAnswer)
    }

    @Test
    fun resultLabels_translateKnownValues() {
        assertEquals("Correcta", MetricsReportMapper.resultLabel(intelligentAttempt()))
        assertEquals(
            "Nula / sin respuesta",
            MetricsReportMapper.resultLabel(
                classicAttempt().copy(semanticResult = null, classicResult = "TIMEOUT_NO_RESPONSE")
            )
        )
        assertEquals(
            "No interpretable",
            MetricsReportMapper.resultLabel(intelligentAttempt().copy(semanticResult = "NOT_INTERPRETABLE"))
        )
    }

    private fun classicSession() = ExportSessionDto(
        sessionId = 1L,
        activityId = 10L,
        activityName = "Animales",
        operationMode = "CLASSIC",
        startedAtMs = 1_000L,
        finishedAtMs = 6_000L,
        finalState = "SESSION_COMPLETED",
        totalDurationMs = 5_000L,
        summary = ExportSessionSummaryDto(
            totalQuestions = 3,
            completedQuestions = 3,
            totalAttempts = 3,
            correctCount = 1,
            incorrectCount = 1,
            noResponseCount = 1,
            notInterpretableCount = 0,
            timeoutCount = 1,
            technicalErrorCount = 0
        ),
        attempts = listOf(
            classicAttempt(),
            classicAttempt().copy(attemptId = 2L, questionId = 2L, classicResult = "TIMEOUT_NO_RESPONSE", transcription = null),
            classicAttempt().copy(attemptId = 3L, questionId = 3L, semanticResult = "INCORRECT")
        ),
        technicalEvents = emptyList(),
        activityTopic = "La granja"
    )

    private fun intelligentSession() = ExportSessionDto(
        sessionId = 2L,
        activityId = 20L,
        activityName = "Colores",
        operationMode = "BIMODAL_INTELLIGENT",
        startedAtMs = 10_000L,
        finishedAtMs = 16_000L,
        finalState = "SESSION_COMPLETED",
        totalDurationMs = 6_000L,
        summary = ExportSessionSummaryDto(
            totalQuestions = 1,
            completedQuestions = 1,
            totalAttempts = 2,
            correctCount = 1,
            incorrectCount = 1,
            noResponseCount = 0,
            notInterpretableCount = 0,
            timeoutCount = 0,
            technicalErrorCount = 0
        ),
        attempts = listOf(
            intelligentAttempt(),
            intelligentAttempt().copy(attemptId = 11L, attemptNumber = 2, semanticResult = "INCORRECT")
        ),
        technicalEvents = listOf(
            ExportTechnicalEventDto(
                eventId = 1L,
                questionId = 9L,
                attemptId = 10L,
                operationMode = "BIMODAL_INTELLIGENT",
                eventType = "INTELLIGENT_ATTENTION_LOST",
                message = null,
                timestampMs = 12_000L,
                latencyMs = null
            ),
            ExportTechnicalEventDto(
                eventId = 2L,
                questionId = 9L,
                attemptId = 10L,
                operationMode = "BIMODAL_INTELLIGENT",
                eventType = "RECAPTURE_DECISION_EXECUTE",
                message = null,
                timestampMs = 13_000L,
                latencyMs = null
            )
        ),
        activityTopic = "Primarios"
    )

    private fun classicAttempt() = ExportAttemptDto(
        attemptId = 1L,
        questionId = 1L,
        questionOrder = 0,
        attemptNumber = 1,
        operationMode = "CLASSIC",
        questionText = "Que animal dice mu?",
        maxTimeMs = 10_000L,
        startedAtMs = 1_100L,
        responseReceivedAtMs = 2_100L,
        finishedAtMs = 2_200L,
        realResponseTimeMs = 1_000L,
        transcription = "vaca",
        semanticResult = "CORRECT",
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
        fullPipelineLatencyMs = null,
        expectedAnswer = "vaca"
    )

    private fun intelligentAttempt() = ExportAttemptDto(
        attemptId = 10L,
        questionId = 9L,
        questionOrder = 0,
        attemptNumber = 1,
        operationMode = "BIMODAL_INTELLIGENT",
        questionText = "De que color es el cielo?",
        maxTimeMs = 30_000L,
        startedAtMs = 10_500L,
        responseReceivedAtMs = 11_500L,
        finishedAtMs = 11_700L,
        realResponseTimeMs = 1_000L,
        transcription = "azul",
        semanticResult = "CORRECT",
        classicResult = null,
        finalAttemptState = "FEEDBACK_CORRECT",
        usedSemanticEvaluation = true,
        usedSpeechToText = true,
        wasFinalAttempt = true,
        advancedFeedbackType = "CORRECT",
        sttStartAtMs = 10_700L,
        sttFinalAtMs = 11_300L,
        semanticStartAtMs = 11_350L,
        semanticEndAtMs = 11_450L,
        logicalResponseAtMs = 11_480L,
        feedbackStartAtMs = 11_500L,
        totalResponseLatencyMs = 20L,
        responseToFeedbackLatencyMs = 20L,
        fullPipelineLatencyMs = 1_000L,
        expectedAnswer = "azul"
    )
}
