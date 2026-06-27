package com.taller.app.export

data class ExportSessionDto(
    val sessionId: Long,
    val activityId: Long,
    val activityName: String?,
    val operationMode: String,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val finalState: String?,
    val totalDurationMs: Long?,
    val summary: ExportSessionSummaryDto,
    val attempts: List<ExportAttemptDto>,
    val technicalEvents: List<ExportTechnicalEventDto>
)

data class ExportSessionSummaryDto(
    val totalQuestions: Int,
    val completedQuestions: Int,
    val totalAttempts: Int,
    val correctCount: Int?,
    val incorrectCount: Int?,
    val noResponseCount: Int,
    val notInterpretableCount: Int?,
    val timeoutCount: Int,
    val technicalErrorCount: Int
)

data class ExportAttemptDto(
    val attemptId: Long,
    val questionId: Long,
    val questionOrder: Int,
    val attemptNumber: Int,
    val operationMode: String,
    val questionText: String?,
    val maxTimeMs: Long,
    val startedAtMs: Long,
    val responseReceivedAtMs: Long?,
    val finishedAtMs: Long?,
    val realResponseTimeMs: Long?,
    val transcription: String?,
    val semanticResult: String?,
    val classicResult: String?,
    val finalAttemptState: String,
    val usedSemanticEvaluation: Boolean,
    val usedSpeechToText: Boolean,
    val wasFinalAttempt: Boolean,
    val advancedFeedbackType: String?,
    val sttStartAtMs: Long?,
    val sttFinalAtMs: Long?,
    val semanticStartAtMs: Long?,
    val semanticEndAtMs: Long?,
    val logicalResponseAtMs: Long?,
    val feedbackStartAtMs: Long?,
    val totalResponseLatencyMs: Long?,
    val responseToFeedbackLatencyMs: Long?,
    val fullPipelineLatencyMs: Long?
)

data class ExportTechnicalEventDto(
    val eventId: Long,
    val questionId: Long?,
    val attemptId: Long?,
    val operationMode: String,
    val eventType: String,
    val message: String?,
    val timestampMs: Long,
    val latencyMs: Long?,
    val voiceProviderRequested: String? = null,
    val voiceProviderUsed: String? = null,
    val voiceFallbackUsed: String? = null,
    val voiceModel: String? = null,
    val voiceName: String? = null,
    val voiceCacheHit: String? = null,
    val voiceSynthesisLatencyMs: String? = null,
    val voicePlaybackDurationMs: String? = null,
    val voiceTotalLatencyMs: String? = null,
    val voiceErrorType: String? = null,
    val voiceContext: String? = null
)
