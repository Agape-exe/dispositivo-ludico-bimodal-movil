package com.taller.app.classic

data class ClassicTimerResult(
    val questionId: String,
    val questionIndex: Int,
    val maxTimeSeconds: Int,
    val answerReceived: Boolean,
    val responseLatencyMs: Long?,
    val isLastQuestion: Boolean,
    val timedOut: Boolean
)
