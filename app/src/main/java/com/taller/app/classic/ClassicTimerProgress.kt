package com.taller.app.classic

private val VALID_TIME_RANGE: IntRange = 1..600
const val CLASSIC_DEFAULT_MAX_TIME_SECONDS: Int = 10

data class ClassicTimerProgress(
    val activityId: String,
    val activityName: String,
    val totalQuestions: Int,
    val currentQuestionIndex: Int,
    val currentQuestionId: String,
    val currentQuestionText: String,
    val currentQuestionMediationKey: String? = null,
    val maxTimeSeconds: Int
) {
    val questionNumber: Int get() = currentQuestionIndex + 1
    val isLastQuestion: Boolean get() = currentQuestionIndex == totalQuestions - 1

    val effectiveMaxTimeSeconds: Int
        get() = if (maxTimeSeconds in VALID_TIME_RANGE) maxTimeSeconds
                else CLASSIC_DEFAULT_MAX_TIME_SECONDS
}
