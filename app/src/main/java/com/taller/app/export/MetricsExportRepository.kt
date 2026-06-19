package com.taller.app.export

import com.taller.app.data.local.dao.AttemptDao
import com.taller.app.data.local.dao.SessionDao
import com.taller.app.data.local.dao.TechnicalEventDao

class MetricsExportRepository(
    private val sessionDao: SessionDao,
    private val attemptDao: AttemptDao,
    private val eventDao: TechnicalEventDao
) {

    suspend fun countSessions(): Int = sessionDao.count()
    suspend fun countAttempts(): Int = attemptDao.count()
    suspend fun countTechnicalEvents(): Int = eventDao.count()

    suspend fun buildExportSessions(): List<ExportSessionDto> {
        val sessions = sessionDao.getAllOnce()
        return sessions.map { session ->
            val attempts = attemptDao.getBySessionIdOnce(session.id)
            val events = eventDao.getBySessionIdOnce(session.id)
            ExportSessionDto(
                sessionId = session.id,
                activityId = session.activityId,
                activityName = session.activityName,
                operationMode = session.operationMode,
                startedAtMs = session.startedAt,
                finishedAtMs = session.endedAt,
                finalState = session.finalStatus,
                totalDurationMs = session.totalDurationMs,
                summary = ExportSessionSummaryDto(
                    totalQuestions = session.totalQuestions,
                    completedQuestions = session.completedQuestions,
                    totalAttempts = session.totalAttempts,
                    correctCount = session.correctCount,
                    incorrectCount = session.incorrectCount,
                    noResponseCount = session.noResponseCount,
                    notInterpretableCount = session.notInterpretableCount,
                    timeoutCount = session.timeoutCount,
                    technicalErrorCount = session.technicalErrorCount
                ),
                attempts = attempts.map { a ->
                    ExportAttemptDto(
                        attemptId = a.id,
                        questionId = a.questionId,
                        questionOrder = a.questionOrder,
                        attemptNumber = a.attemptNumber,
                        operationMode = a.operationMode,
                        questionText = a.questionText,
                        maxTimeMs = a.maxTimeMs,
                        startedAtMs = a.startedAtMs,
                        responseReceivedAtMs = a.responseReceivedAtMs,
                        finishedAtMs = a.finishedAtMs,
                        realResponseTimeMs = a.realResponseTimeMs,
                        transcription = a.transcription,
                        semanticResult = a.semanticResult,
                        classicResult = a.classicResult,
                        finalAttemptState = a.finalAttemptState,
                        usedSemanticEvaluation = a.usedSemanticEvaluation,
                        usedSpeechToText = a.usedSpeechToText,
                        wasFinalAttempt = a.wasFinalAttempt,
                        advancedFeedbackType = a.advancedFeedbackType,
                        sttStartAtMs = a.sttStartAtMs,
                        sttFinalAtMs = a.sttFinalAtMs,
                        semanticStartAtMs = a.semanticStartAtMs,
                        semanticEndAtMs = a.semanticEndAtMs,
                        logicalResponseAtMs = a.logicalResponseAtMs,
                        feedbackStartAtMs = a.feedbackStartAtMs,
                        totalResponseLatencyMs = a.totalResponseLatencyMs,
                        responseToFeedbackLatencyMs = a.responseToFeedbackLatencyMs,
                        fullPipelineLatencyMs = a.fullPipelineLatencyMs
                    )
                },
                technicalEvents = events.map { e ->
                    ExportTechnicalEventDto(
                        eventId = e.id,
                        questionId = e.questionId,
                        attemptId = e.attemptId,
                        operationMode = e.operationMode,
                        eventType = e.eventType,
                        message = e.message,
                        timestampMs = e.timestamp,
                        latencyMs = e.latencyMs
                    )
                }
            )
        }
    }
}
