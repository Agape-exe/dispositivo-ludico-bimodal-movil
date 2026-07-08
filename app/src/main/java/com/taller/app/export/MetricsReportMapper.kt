package com.taller.app.export

object MetricsReportMapper {

    fun mapAll(sessions: List<ExportSessionDto>): MetricsReport =
        MetricsReport(sessions = sessions.map(::mapSession))

    fun mapSession(session: ExportSessionDto): MetricsSessionReport {
        val details = session.attempts
            .sortedWith(compareBy<ExportAttemptDto> { it.questionOrder }.thenBy { it.attemptNumber })
            .map { attempt -> mapAttempt(session, attempt) }
        val answeredTimes = session.attempts.mapNotNull { attempt ->
            attempt.realResponseTimeMs?.takeIf { it >= 0L && !isNoResponse(attempt) }
        }
        val avgMs = answeredTimes.takeIf { it.isNotEmpty() }?.average()?.toLong()
        // FINAL-FLOW01: la sesion deja un marcador tecnico con su configuracion de
        // atencion. Sin marcador (sesiones antiguas) se asume atencion activa para
        // conservar la lectura historica de los conteos.
        val attentionDisabled = session.technicalEvents.any {
            it.eventType == "ATTENTION_TRACKING_DISABLED"
        }
        val recaptureDisabled = attentionDisabled || session.technicalEvents.any {
            it.eventType == "RECAPTURE_TRACKING_DISABLED"
        }
        return MetricsSessionReport(
            sessionId = session.sessionId,
            activityName = session.activityName?.takeIf { it.isNotBlank() } ?: NOT_RECORDED,
            topic = session.activityTopic?.takeIf { it.isNotBlank() } ?: NOT_RECORDED,
            modeLabel = modeLabel(session.operationMode),
            modeCode = session.operationMode,
            startedAtMs = session.startedAtMs,
            finishedAtMs = session.finishedAtMs,
            durationMs = session.totalDurationMs ?: durationFrom(session),
            finalState = stateLabel(session.finalState),
            totalQuestions = session.summary.totalQuestions,
            correctCount = session.summary.correctCount,
            incorrectCount = session.summary.incorrectCount,
            noResponseCount = session.summary.noResponseCount,
            notInterpretableCount = session.summary.notInterpretableCount,
            timeoutCount = session.summary.timeoutCount,
            totalAttempts = session.summary.totalAttempts,
            averageResponseTimeMs = avgMs,
            attentionLossCount = session.technicalEvents.count { it.isAttentionLoss() },
            recaptureCount = session.technicalEvents.count { it.isRecaptureExecuted() },
            attentionTrackingEnabled = !attentionDisabled,
            recaptureTrackingEnabled = !recaptureDisabled,
            closeReason = closeReason(session),
            configuredTimePerQuestionMs = session.attempts
                .map { it.maxTimeMs }
                .firstOrNull { it > 0L },
            details = details
        )
    }

    private fun mapAttempt(
        session: ExportSessionDto,
        attempt: ExportAttemptDto
    ): MetricsQuestionAttemptReport {
        val questionEvents = session.technicalEvents.filter { event ->
            event.questionId == attempt.questionId || event.attemptId == attempt.attemptId
        }
        return MetricsQuestionAttemptReport(
            questionOrder = attempt.questionOrder + 1,
            questionText = attempt.questionText?.takeIf { it.isNotBlank() } ?: NOT_RECORDED,
            expectedAnswer = attempt.expectedAnswer?.takeIf { it.isNotBlank() } ?: NOT_RECORDED,
            capturedAnswer = attempt.transcription?.takeIf { it.isNotBlank() } ?: NO_RESPONSE,
            attemptNumber = attempt.attemptNumber,
            result = resultLabel(attempt),
            responseTimeMs = attempt.realResponseTimeMs,
            timeout = isTimeout(attempt),
            timestampMs = attempt.responseReceivedAtMs ?: attempt.finishedAtMs,
            evaluationLayer = evaluationLayer(attempt),
            attentionLost = questionEvents.any { it.isAttentionLoss() },
            recaptured = questionEvents.any { it.isRecaptureExecuted() || it.isAttentionRecovered() }
        )
    }

    fun modeLabel(mode: String): String = when (mode) {
        "CLASSIC" -> "Temporizador fijo"
        "BIMODAL_INTELLIGENT", "ADVANCED" -> "Inteligente"
        else -> mode.ifBlank { NOT_RECORDED }
    }

    fun stateLabel(state: String?): String = when (state) {
        "SESSION_COMPLETED" -> "Completada"
        "SESSION_CANCELLED" -> "Cancelada / interrumpida"
        "SESSION_PAUSED_NO_ATTENTION", "RECAPTURE_SESSION_PAUSED_NO_ATTENTION" ->
            "Interrumpida por no recaptura de atencion"
        "ERROR" -> "Error"
        null, "" -> NOT_RECORDED
        else -> state.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    }

    fun resultLabel(attempt: ExportAttemptDto): String {
        val result = attempt.semanticResult ?: attempt.classicResult ?: attempt.finalAttemptState
        return when (result) {
            "CORRECT", "FEEDBACK_CORRECT" -> "Correcta"
            "INCORRECT", "FEEDBACK_INCORRECT" -> "Incorrecta"
            "NO_RESPONSE", "FEEDBACK_NO_RESPONSE", "TIMEOUT_NO_RESPONSE" -> "Nula / sin respuesta"
            "TIME_EXPIRED", "TIMEOUT", "TIMEOUT_PARTIAL" -> "Nula / timeout"
            "NOT_INTERPRETABLE", "FEEDBACK_NOT_INTERPRETABLE" -> "No interpretable"
            "ANSWERED", "ANSWER_RECEIVED" -> "Respondida"
            "UNKNOWN", "" -> NOT_RECORDED
            else -> result.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    private fun evaluationLayer(attempt: ExportAttemptDto): String = when {
        attempt.operationMode == "CLASSIC" -> "Evaluacion interna"
        attempt.usedSemanticEvaluation && attempt.semanticResult != null -> "Local"
        attempt.advancedFeedbackType?.contains("JUDGE", ignoreCase = true) == true -> "Juez"
        attempt.usedSemanticEvaluation -> "Local"
        else -> NOT_RECORDED
    }

    private fun closeReason(session: ExportSessionDto): String {
        val noAttention = session.technicalEvents.lastOrNull {
            it.eventType == "RECAPTURE_SESSION_PAUSED_NO_ATTENTION"
        }
        return noAttention?.eventType?.let(::stateLabel) ?: stateLabel(session.finalState)
    }

    private fun durationFrom(session: ExportSessionDto): Long? =
        session.finishedAtMs?.let { (it - session.startedAtMs).coerceAtLeast(0L) }

    private fun isNoResponse(attempt: ExportAttemptDto): Boolean =
        attempt.transcription.isNullOrBlank() || resultLabel(attempt).contains("sin respuesta", ignoreCase = true)

    private fun isTimeout(attempt: ExportAttemptDto): Boolean =
        attempt.classicResult?.contains("TIMEOUT", ignoreCase = true) == true ||
            attempt.finalAttemptState.contains("TIME", ignoreCase = true)

    private fun ExportTechnicalEventDto.isAttentionLoss(): Boolean =
        eventType == "FACE_LOST" ||
            eventType == "INTELLIGENT_ATTENTION_LOST" ||
            eventType == "INTELLIGENT_ATTENTION_TEMPORARILY_LOST"

    private fun ExportTechnicalEventDto.isAttentionRecovered(): Boolean =
        eventType == "FACE_RETURNED" || eventType == "INTELLIGENT_ATTENTION_RECOVERED"

    private fun ExportTechnicalEventDto.isRecaptureExecuted(): Boolean =
        eventType == "RECAPTURE_DECISION_EXECUTE" || eventType == "RECAPTURE_TTS_STARTED"

    const val NOT_RECORDED = "--"
    const val NO_RESPONSE = "Sin respuesta"
}
