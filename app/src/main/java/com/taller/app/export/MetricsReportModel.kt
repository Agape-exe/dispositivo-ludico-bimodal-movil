package com.taller.app.export

data class MetricsReport(
    val sessions: List<MetricsSessionReport>
)

data class MetricsSessionReport(
    val sessionId: Long,
    val activityName: String,
    val topic: String,
    val modeLabel: String,
    val modeCode: String,
    val startedAtMs: Long?,
    val finishedAtMs: Long?,
    val durationMs: Long?,
    val finalState: String,
    val totalQuestions: Int,
    val correctCount: Int?,
    val incorrectCount: Int?,
    val noResponseCount: Int,
    val notInterpretableCount: Int?,
    val timeoutCount: Int,
    val totalAttempts: Int,
    val averageResponseTimeMs: Long?,
    val attentionLossCount: Int,
    val recaptureCount: Int,
    val closeReason: String,
    val configuredTimePerQuestionMs: Long?,
    val details: List<MetricsQuestionAttemptReport>
) {
    val isIntelligent: Boolean
        get() = modeCode != "CLASSIC"
}

data class MetricsQuestionAttemptReport(
    val questionOrder: Int,
    val questionText: String,
    val expectedAnswer: String,
    val capturedAnswer: String,
    val attemptNumber: Int,
    val result: String,
    val responseTimeMs: Long?,
    val timeout: Boolean,
    val timestampMs: Long?,
    val evaluationLayer: String,
    val attentionLost: Boolean,
    val recaptured: Boolean
)
