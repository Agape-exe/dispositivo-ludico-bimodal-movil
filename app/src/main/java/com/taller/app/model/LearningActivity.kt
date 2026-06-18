package com.taller.app.model

data class LearningActivity(
    val id: String,
    val title: String,
    val mode: OperationMode,
    val questions: List<LearningQuestion>
)

data class LearningQuestion(
    val id: String,
    val questionText: String,
    val expectedAnswer: String,
    val keywords: List<String>,
    val maxTimeSeconds: Int,
    val maxAttempts: Int,
    val mediationKey: String? = null
)

enum class OperationMode {
    CLASSIC,
    ADVANCED
}