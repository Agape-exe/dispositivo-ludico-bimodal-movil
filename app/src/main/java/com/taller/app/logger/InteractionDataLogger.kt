package com.taller.app.logger

import com.taller.app.bimodal.BimodalLatencySample
import com.taller.app.bimodal.BimodalSessionSummary
import com.taller.app.data.local.dao.AttemptDao
import com.taller.app.data.local.dao.SessionDao
import com.taller.app.data.local.dao.TechnicalEventDao
import com.taller.app.data.local.entity.AttemptEntity
import com.taller.app.data.local.entity.SessionEntity
import com.taller.app.data.local.entity.TechnicalEventEntity

/**
 * Registra sesiones, intentos y eventos técnicos de ambos modos de interacción
 * (bimodal inteligente y clásico) en Room. Es stateless: el llamador gestiona
 * los IDs de sesión e intento en curso.
 *
 * Nunca almacena audio, imágenes, frames, datos biométricos ni nombres de niños.
 * Solo guarda conteos, marcas de tiempo, transcripciones de texto producidas por
 * STT y resultados semánticos.
 *
 * Todas las operaciones son suspend y deben llamarse desde un contexto de
 * coroutine. Los callers deben envolver las llamadas en runCatching para que
 * ningún error de Room afecte el flujo de interacción.
 */
class InteractionDataLogger(
    private val sessionDao: SessionDao,
    private val attemptDao: AttemptDao,
    private val eventDao: TechnicalEventDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Abre una nueva sesión en Room y devuelve su ID.
     * Retorna -1 si activityId no es válido (datos de muestra o ID nulo).
     */
    suspend fun startSession(
        activityId: Long,
        activityName: String,
        operationMode: String,
        totalQuestions: Int
    ): Long {
        if (activityId <= 0L) return -1L
        val entity = SessionEntity(
            activityId = activityId,
            activityName = activityName,
            operationMode = operationMode,
            startedAt = now(),
            completed = false,
            totalQuestions = totalQuestions
        )
        return sessionDao.insert(entity)
    }

    /**
     * Cierra la sesión con el estado final y los conteos del modo bimodal.
     * Usa [summary] para llenar correctCount, incorrectCount, etc.
     */
    suspend fun finishBimodalSession(
        sessionId: Long,
        finalState: String,
        startedAtMs: Long,
        completedQuestions: Int,
        summary: BimodalSessionSummary
    ) {
        if (sessionId <= 0L) return
        val endedAt = now()
        val durationMs = (endedAt - startedAtMs).coerceAtLeast(0L)
        sessionDao.updateFinal(
            id = sessionId,
            endedAt = endedAt,
            durationSeconds = durationMs / 1000L,
            totalDurationMs = durationMs,
            finalStatus = finalState,
            completed = finalState == "SESSION_COMPLETED",
            completedQuestions = completedQuestions,
            totalAttempts = summary.totalAttempts,
            correctCount = summary.correct,
            incorrectCount = summary.incorrect,
            noResponseCount = summary.noResponse,
            notInterpretableCount = summary.notInterpretable,
            timeoutCount = summary.timeExpired,
            technicalErrorCount = summary.technicalErrors + summary.sttErrors
        )
    }

    /**
     * Cierra la sesión con el estado final y los conteos del modo clásico.
     * correctCount e incorrectCount quedan nulos (no aplican en modo clásico).
     */
    suspend fun finishClassicSession(
        sessionId: Long,
        finalState: String,
        startedAtMs: Long,
        completedQuestions: Int,
        totalAttempts: Int,
        noResponseCount: Int,
        timeoutCount: Int,
        correctCount: Int? = null,
        incorrectCount: Int? = null,
        notInterpretableCount: Int? = null
    ) {
        if (sessionId <= 0L) return
        val endedAt = now()
        val durationMs = (endedAt - startedAtMs).coerceAtLeast(0L)
        sessionDao.updateFinal(
            id = sessionId,
            endedAt = endedAt,
            durationSeconds = durationMs / 1000L,
            totalDurationMs = durationMs,
            finalStatus = finalState,
            completed = finalState == "SESSION_COMPLETED",
            completedQuestions = completedQuestions,
            totalAttempts = totalAttempts,
            correctCount = correctCount,
            incorrectCount = incorrectCount,
            noResponseCount = noResponseCount,
            notInterpretableCount = notInterpretableCount,
            timeoutCount = timeoutCount,
            technicalErrorCount = 0
        )
    }

    /**
     * Registra el inicio de un intento y devuelve su ID.
     * Retorna -1 si sessionId o questionId no son válidos.
     */
    suspend fun logAttemptStarted(
        sessionId: Long,
        questionId: Long,
        questionOrder: Int,
        attemptNumber: Int,
        operationMode: String,
        questionText: String? = null,
        maxTimeMs: Long = 0L,
        usedSemanticEvaluation: Boolean = false,
        usedSpeechToText: Boolean = false
    ): Long {
        if (sessionId <= 0L || questionId <= 0L) return -1L
        val ts = now()
        val entity = AttemptEntity(
            sessionId = sessionId,
            questionId = questionId,
            questionOrder = questionOrder,
            attemptNumber = attemptNumber,
            operationMode = operationMode,
            questionText = questionText,
            maxTimeMs = maxTimeMs,
            usedSemanticEvaluation = usedSemanticEvaluation,
            usedSpeechToText = usedSpeechToText,
            startedAtMs = ts,
            createdAt = ts
        )
        return attemptDao.insert(entity)
    }

    /**
     * Cierra un intento del modo bimodal con su resultado semántico y latencias.
     */
    suspend fun finishBimodalAttempt(
        attemptId: Long,
        finalAttemptState: String,
        wasFinalAttempt: Boolean,
        transcript: String? = null,
        semanticResult: String? = null,
        responseReceivedAtMs: Long? = null,
        realResponseTimeMs: Long? = null,
        advancedFeedbackType: String? = null,
        usedStt: Boolean = false,
        latencySample: BimodalLatencySample? = null
    ) {
        if (attemptId <= 0L) return
        attemptDao.updateFinished(
            attemptId = attemptId,
            finalState = finalAttemptState,
            wasFinal = wasFinalAttempt,
            transcript = transcript,
            semanticResult = semanticResult,
            classicResult = null,
            responseReceivedAtMs = responseReceivedAtMs,
            finishedAtMs = now(),
            realResponseTimeMs = realResponseTimeMs,
            advancedFeedbackType = advancedFeedbackType,
            usedStt = usedStt,
            sttStart = latencySample?.sttStartAtMs,
            sttFinalAt = latencySample?.sttFinalAtMs,
            semStart = latencySample?.semanticStartAtMs,
            semEnd = latencySample?.semanticEndAtMs,
            logicAt = latencySample?.logicalResponseAtMs,
            feedbackAt = latencySample?.feedbackStartAtMs,
            responseLatency = latencySample?.totalResponseLatencyMs,
            feedbackLatency = latencySample?.responseToFeedbackLatencyMs,
            pipelineLatency = latencySample?.fullPipelineLatencyMs
        )
    }

    /**
     * Cierra un intento del modo clásico (sin evaluación semántica).
     * classicResult: "ANSWERED", "TIMEOUT_PARTIAL" o "TIMEOUT_NO_RESPONSE".
     */
    suspend fun finishClassicAttempt(
        attemptId: Long,
        finalAttemptState: String,
        classicResult: String,
        transcript: String? = null,
        semanticResult: String? = null,
        responseReceivedAtMs: Long? = null,
        realResponseTimeMs: Long? = null,
        usedStt: Boolean = false,
        semanticStartAtMs: Long? = null,
        semanticEndAtMs: Long? = null
    ) {
        if (attemptId <= 0L) return
        attemptDao.updateFinished(
            attemptId = attemptId,
            finalState = finalAttemptState,
            wasFinal = true,
            transcript = transcript,
            semanticResult = semanticResult,
            classicResult = classicResult,
            responseReceivedAtMs = responseReceivedAtMs,
            finishedAtMs = now(),
            realResponseTimeMs = realResponseTimeMs,
            advancedFeedbackType = null,
            usedStt = usedStt,
            sttStart = null,
            sttFinalAt = null,
            semStart = semanticStartAtMs,
            semEnd = semanticEndAtMs,
            logicAt = null,
            feedbackAt = null,
            responseLatency = null,
            feedbackLatency = null,
            pipelineLatency = null
        )
    }

    /**
     * Registra un evento técnico del flujo (STT, error, latencia, etc.).
     * No almacena datos del niño: solo tipos de evento y marcas de tiempo.
     */
    suspend fun logTechnicalEvent(
        sessionId: Long,
        questionId: Long? = null,
        attemptId: Long? = null,
        operationMode: String = "",
        eventType: String,
        message: String? = null,
        latencyMs: Long? = null
    ) {
        if (sessionId <= 0L) return
        val entity = TechnicalEventEntity(
            sessionId = sessionId,
            questionId = questionId?.takeIf { it > 0L },
            attemptId = attemptId?.takeIf { it > 0L },
            operationMode = operationMode,
            eventType = eventType,
            message = message,
            latencyMs = latencyMs,
            timestamp = now()
        )
        eventDao.insert(entity)
    }
}
