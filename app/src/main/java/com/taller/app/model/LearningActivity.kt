package com.taller.app.model

data class LearningActivity(
    val id: String,
    val title: String,
    val mode: OperationMode,
    val questions: List<LearningQuestion>,
    // TTSV01: guion de Seven a nivel de sesion y estado de la voz preparada.
    // Permiten reproducir desde cache las lineas finitas sin sintetizar en vivo.
    val generatedIntroText: String? = null,
    val generatedClosingText: String? = null,
    val voicePrepReady: Boolean = false,
    // MED01: contexto pedagogico textual usado por el juez de respuestas abiertas.
    val topic: String = "",
    val ageLevel: String? = null,
    val classContextNotes: String? = null
)

data class LearningQuestion(
    val id: String,
    val questionText: String,
    val expectedAnswer: String,
    val keywords: List<String>,
    val maxTimeSeconds: Int,
    val maxAttempts: Int,
    val mediationKey: String? = null,
    // TTSV01: guion de Seven a nivel de pregunta (texto amigable y feedbacks).
    // La voz usa estos textos preparados; los registros conservan questionText.
    val childFriendlyQuestionText: String? = null,
    val positiveFeedbackText: String? = null,
    val supportiveFeedbackText: String? = null,
    val retryPromptText: String? = null
)

enum class OperationMode {
    CLASSIC,
    ADVANCED
}