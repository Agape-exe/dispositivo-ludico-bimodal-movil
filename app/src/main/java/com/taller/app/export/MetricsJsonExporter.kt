package com.taller.app.export

class MetricsJsonExporter {

    fun export(
        sessions: List<ExportSessionDto>,
        exportedAtMs: Long = System.currentTimeMillis()
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"schemaVersion\": \"1.0\",\n")
        sb.append("  \"exportedAtMs\": ").append(exportedAtMs).append(",\n")
        sb.append("  \"sessions\": [")
        if (sessions.isEmpty()) {
            sb.append("]")
        } else {
            sessions.forEachIndexed { i, session ->
                if (i > 0) sb.append(",")
                sb.append("\n")
                appendSession(sb, session)
            }
            sb.append("\n  ]")
        }
        sb.append("\n}")
        return sb.toString()
    }

    private fun appendSession(sb: StringBuilder, s: ExportSessionDto) {
        sb.append("    {\n")
        sb.append("      \"sessionId\": ").append(s.sessionId).append(",\n")
        sb.append("      \"activityId\": ").append(s.activityId).append(",\n")
        sb.append("      \"activityName\": ").append(jsonStr(s.activityName)).append(",\n")
        sb.append("      \"operationMode\": ").append(jsonStr(s.operationMode)).append(",\n")
        sb.append("      \"startedAtMs\": ").append(s.startedAtMs).append(",\n")
        sb.append("      \"finishedAtMs\": ").append(jsonNum(s.finishedAtMs)).append(",\n")
        sb.append("      \"finalState\": ").append(jsonStr(s.finalState)).append(",\n")
        sb.append("      \"totalDurationMs\": ").append(jsonNum(s.totalDurationMs)).append(",\n")
        sb.append("      \"summary\": ")
        appendSummary(sb, s.summary)
        sb.append(",\n")
        sb.append("      \"attempts\": [")
        if (s.attempts.isEmpty()) {
            sb.append("]")
        } else {
            s.attempts.forEachIndexed { i, a ->
                if (i > 0) sb.append(",")
                sb.append("\n")
                appendAttempt(sb, a)
            }
            sb.append("\n      ]")
        }
        sb.append(",\n")
        sb.append("      \"technicalEvents\": [")
        if (s.technicalEvents.isEmpty()) {
            sb.append("]")
        } else {
            s.technicalEvents.forEachIndexed { i, e ->
                if (i > 0) sb.append(",")
                sb.append("\n")
                appendEvent(sb, e)
            }
            sb.append("\n      ]")
        }
        sb.append("\n    }")
    }

    private fun appendSummary(sb: StringBuilder, s: ExportSessionSummaryDto) {
        sb.append("{\n")
        sb.append("        \"totalQuestions\": ").append(s.totalQuestions).append(",\n")
        sb.append("        \"completedQuestions\": ").append(s.completedQuestions).append(",\n")
        sb.append("        \"totalAttempts\": ").append(s.totalAttempts).append(",\n")
        sb.append("        \"correctCount\": ").append(jsonNum(s.correctCount)).append(",\n")
        sb.append("        \"incorrectCount\": ").append(jsonNum(s.incorrectCount)).append(",\n")
        sb.append("        \"noResponseCount\": ").append(s.noResponseCount).append(",\n")
        sb.append("        \"notInterpretableCount\": ").append(jsonNum(s.notInterpretableCount)).append(",\n")
        sb.append("        \"timeoutCount\": ").append(s.timeoutCount).append(",\n")
        sb.append("        \"technicalErrorCount\": ").append(s.technicalErrorCount).append("\n")
        sb.append("      }")
    }

    private fun appendAttempt(sb: StringBuilder, a: ExportAttemptDto) {
        sb.append("        {\n")
        sb.append("          \"attemptId\": ").append(a.attemptId).append(",\n")
        sb.append("          \"questionId\": ").append(a.questionId).append(",\n")
        sb.append("          \"questionOrder\": ").append(a.questionOrder).append(",\n")
        sb.append("          \"attemptNumber\": ").append(a.attemptNumber).append(",\n")
        sb.append("          \"operationMode\": ").append(jsonStr(a.operationMode)).append(",\n")
        sb.append("          \"questionText\": ").append(jsonStr(a.questionText)).append(",\n")
        sb.append("          \"maxTimeMs\": ").append(a.maxTimeMs).append(",\n")
        sb.append("          \"startedAtMs\": ").append(a.startedAtMs).append(",\n")
        sb.append("          \"responseReceivedAtMs\": ").append(jsonNum(a.responseReceivedAtMs)).append(",\n")
        sb.append("          \"finishedAtMs\": ").append(jsonNum(a.finishedAtMs)).append(",\n")
        sb.append("          \"realResponseTimeMs\": ").append(jsonNum(a.realResponseTimeMs)).append(",\n")
        sb.append("          \"transcription\": ").append(jsonStr(a.transcription)).append(",\n")
        sb.append("          \"semanticResult\": ").append(jsonStr(a.semanticResult)).append(",\n")
        sb.append("          \"classicResult\": ").append(jsonStr(a.classicResult)).append(",\n")
        sb.append("          \"finalAttemptState\": ").append(jsonStr(a.finalAttemptState)).append(",\n")
        sb.append("          \"usedSemanticEvaluation\": ").append(a.usedSemanticEvaluation).append(",\n")
        sb.append("          \"usedSpeechToText\": ").append(a.usedSpeechToText).append(",\n")
        sb.append("          \"wasFinalAttempt\": ").append(a.wasFinalAttempt).append(",\n")
        sb.append("          \"advancedFeedbackType\": ").append(jsonStr(a.advancedFeedbackType)).append(",\n")
        sb.append("          \"sttStartAtMs\": ").append(jsonNum(a.sttStartAtMs)).append(",\n")
        sb.append("          \"sttFinalAtMs\": ").append(jsonNum(a.sttFinalAtMs)).append(",\n")
        sb.append("          \"semanticStartAtMs\": ").append(jsonNum(a.semanticStartAtMs)).append(",\n")
        sb.append("          \"semanticEndAtMs\": ").append(jsonNum(a.semanticEndAtMs)).append(",\n")
        sb.append("          \"logicalResponseAtMs\": ").append(jsonNum(a.logicalResponseAtMs)).append(",\n")
        sb.append("          \"feedbackStartAtMs\": ").append(jsonNum(a.feedbackStartAtMs)).append(",\n")
        sb.append("          \"totalResponseLatencyMs\": ").append(jsonNum(a.totalResponseLatencyMs)).append(",\n")
        sb.append("          \"responseToFeedbackLatencyMs\": ").append(jsonNum(a.responseToFeedbackLatencyMs)).append(",\n")
        sb.append("          \"fullPipelineLatencyMs\": ").append(jsonNum(a.fullPipelineLatencyMs)).append("\n")
        sb.append("        }")
    }

    private fun appendEvent(sb: StringBuilder, e: ExportTechnicalEventDto) {
        sb.append("        {\n")
        sb.append("          \"eventId\": ").append(e.eventId).append(",\n")
        sb.append("          \"questionId\": ").append(jsonNum(e.questionId)).append(",\n")
        sb.append("          \"attemptId\": ").append(jsonNum(e.attemptId)).append(",\n")
        sb.append("          \"operationMode\": ").append(jsonStr(e.operationMode)).append(",\n")
        sb.append("          \"eventType\": ").append(jsonStr(e.eventType)).append(",\n")
        sb.append("          \"message\": ").append(jsonStr(e.message)).append(",\n")
        sb.append("          \"timestampMs\": ").append(e.timestampMs).append(",\n")
        sb.append("          \"latencyMs\": ").append(jsonNum(e.latencyMs)).append(",\n")
        sb.append("          \"voiceProviderRequested\": ").append(jsonStr(e.voiceProviderRequested)).append(",\n")
        sb.append("          \"voiceProviderUsed\": ").append(jsonStr(e.voiceProviderUsed)).append(",\n")
        sb.append("          \"voiceFallbackUsed\": ").append(jsonStr(e.voiceFallbackUsed)).append(",\n")
        sb.append("          \"voiceModel\": ").append(jsonStr(e.voiceModel)).append(",\n")
        sb.append("          \"voiceName\": ").append(jsonStr(e.voiceName)).append(",\n")
        sb.append("          \"voiceCacheHit\": ").append(jsonStr(e.voiceCacheHit)).append(",\n")
        sb.append("          \"voiceSynthesisLatencyMs\": ").append(jsonStr(e.voiceSynthesisLatencyMs)).append(",\n")
        sb.append("          \"voicePlaybackDurationMs\": ").append(jsonStr(e.voicePlaybackDurationMs)).append(",\n")
        sb.append("          \"voiceTotalLatencyMs\": ").append(jsonStr(e.voiceTotalLatencyMs)).append(",\n")
        sb.append("          \"voiceErrorType\": ").append(jsonStr(e.voiceErrorType)).append(",\n")
        sb.append("          \"voiceContext\": ").append(jsonStr(e.voiceContext)).append("\n")
        sb.append("        }")
    }

    companion object {
        fun jsonStr(value: String?): String {
            if (value == null) return "null"
            val escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            return "\"$escaped\""
        }

        fun jsonNum(value: Long?): String = value?.toString() ?: "null"
        fun jsonNum(value: Int?): String = value?.toString() ?: "null"
    }
}
