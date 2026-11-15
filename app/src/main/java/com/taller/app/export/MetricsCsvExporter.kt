package com.taller.app.export

class MetricsCsvExporter {

    fun exportSessionsCsv(sessions: List<ExportSessionDto>): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.appendLine(SESSIONS_HEADER.joinToString(SEP))
        for (session in sessions) {
            sb.appendLine(buildSessionsRow(session))
        }
        return sb.toString()
    }

    fun exportAttemptsCsv(sessions: List<ExportSessionDto>): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.appendLine(ATTEMPTS_HEADER.joinToString(SEP))
        for (session in sessions) {
            for (attempt in session.attempts) {
                sb.appendLine(buildAttemptsRow(session, attempt))
            }
        }
        return sb.toString()
    }

    fun exportEventsCsv(sessions: List<ExportSessionDto>): String {
        val sb = StringBuilder()
        sb.append(BOM)
        sb.appendLine(EVENTS_HEADER.joinToString(SEP))
        for (session in sessions) {
            for (event in session.technicalEvents) {
                sb.appendLine(buildEventsRow(session.sessionId, event))
            }
        }
        return sb.toString()
    }

    private fun buildSessionsRow(s: ExportSessionDto): String = listOf(
        f(s.sessionId),
        f(s.activityId),
        f(s.activityName),
        f(s.operationMode),
        f(s.startedAtMs),
        f(s.finishedAtMs),
        f(s.finalState),
        f(s.totalDurationMs),
        f(s.summary.totalQuestions),
        f(s.summary.completedQuestions),
        f(s.summary.totalAttempts),
        f(s.summary.correctCount),
        f(s.summary.incorrectCount),
        f(s.summary.noResponseCount),
        f(s.summary.notInterpretableCount),
        f(s.summary.timeoutCount),
        f(s.summary.technicalErrorCount),
        f(s.summary.validVoiceResponseCount)
    ).joinToString(SEP)

    private fun buildAttemptsRow(session: ExportSessionDto, a: ExportAttemptDto): String =
        listOf(
            f(session.sessionId),
            f(session.activityId),
            f(session.activityName),
            f(session.operationMode),
            f(session.startedAtMs),
            f(session.finishedAtMs),
            f(session.finalState),
            f(a.questionId),
            f(a.questionOrder),
            f(a.attemptId),
            f(a.attemptNumber),
            f(a.maxTimeMs),
            f(a.startedAtMs),
            f(a.responseReceivedAtMs),
            f(a.finishedAtMs),
            f(a.realResponseTimeMs),
            f(a.transcription),
            f(a.semanticResult),
            f(a.classicResult),
            f(a.finalAttemptState),
            f(a.usedSemanticEvaluation),
            f(a.usedSpeechToText),
            f(a.wasFinalAttempt),
            f(a.advancedFeedbackType),
            f(a.sttStartAtMs),
            f(a.sttFinalAtMs),
            f(a.semanticStartAtMs),
            f(a.semanticEndAtMs),
            f(a.logicalResponseAtMs),
            f(a.feedbackStartAtMs),
            f(a.totalResponseLatencyMs),
            f(a.responseToFeedbackLatencyMs),
            f(a.fullPipelineLatencyMs)
        ).joinToString(SEP)

    private fun buildEventsRow(sessionId: Long, e: ExportTechnicalEventDto): String =
        listOf(
            f(sessionId),
            f(e.eventId),
            f(e.operationMode),
            f(e.questionId),
            f(e.attemptId),
            f(e.eventType),
            f(e.message),
            f(e.timestampMs),
            f(e.latencyMs),
            f(e.voiceProviderRequested),
            f(e.voiceProviderUsed),
            f(e.voiceFallbackUsed),
            f(e.voiceModel),
            f(e.voiceName),
            f(e.voiceCacheHit),
            f(e.voiceSynthesisLatencyMs),
            f(e.voicePlaybackDurationMs),
            f(e.voiceTotalLatencyMs),
            f(e.voiceErrorType),
            f(e.voiceContext)
        ).joinToString(SEP)

    private fun f(value: Any?): String {
        if (value == null) return ""
        val s = value.toString()
        return if (s.contains(';') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
            "\"${s.replace("\"", "\"\"")}\""
        } else {
            s
        }
    }

    companion object {
        internal const val SEP = ";"
        internal const val BOM = "﻿"

        val SESSIONS_HEADER = listOf(
            "session_id", "activity_id", "activity_name", "operation_mode",
            "started_at_ms", "finished_at_ms", "final_state", "total_duration_ms",
            "total_questions", "completed_questions", "total_attempts",
            "correct_count", "incorrect_count", "no_response_count",
            "not_interpretable_count", "timeout_count", "technical_error_count",
            "valid_voice_response_count"
        )

        val ATTEMPTS_HEADER = listOf(
            "session_id", "activity_id", "activity_name", "operation_mode",
            "session_started_at_ms", "session_finished_at_ms", "session_final_state",
            "question_id", "question_order", "attempt_id", "attempt_number",
            "max_time_ms", "attempt_started_at_ms", "response_received_at_ms",
            "attempt_finished_at_ms", "real_response_time_ms", "transcript",
            "semantic_result", "classic_result", "final_attempt_state",
            "used_semantic_evaluation", "used_speech_to_text", "was_final_attempt",
            "advanced_feedback_type",
            "stt_start_at_ms", "stt_final_at_ms", "semantic_start_at_ms",
            "semantic_end_at_ms", "logical_response_at_ms", "feedback_start_at_ms",
            "total_response_latency_ms", "response_to_feedback_latency_ms",
            "full_pipeline_latency_ms"
        )

        val EVENTS_HEADER = listOf(
            "session_id", "event_id", "operation_mode", "question_id", "attempt_id",
            "event_type", "message", "timestamp_ms", "latency_ms",
            "voiceProviderRequested", "voiceProviderUsed", "voiceFallbackUsed",
            "voiceModel", "voiceName", "voiceCacheHit", "voiceSynthesisLatencyMs",
            "voicePlaybackDurationMs", "voiceTotalLatencyMs", "voiceErrorType", "voiceContext"
        )
    }
}
