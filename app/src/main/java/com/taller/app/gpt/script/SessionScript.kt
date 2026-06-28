package com.taller.app.gpt.script

/**
 * GEN01: guion de Seven para una sesion educativa.
 *
 * Es una sugerencia pedagogica editable por la docente, nunca una verdad cerrada.
 * Se genera en la creacion/preparacion de la sesion y se revisa antes de usarse.
 */
data class SessionScript(
    val intro: String,
    val closing: String,
    val toneNotes: String,
    val pedagogicalWarnings: String,
    val questions: List<QuestionScript>
)

data class QuestionScript(
    val questionId: Long,
    val orderIndex: Int,
    val childFriendlyQuestionText: String,
    val hintLevel1: String,
    val hintLevel2: String,
    val hintLevel3: String,
    val positiveFeedbackText: String,
    val supportiveFeedbackText: String,
    val retryPromptText: String,
    val answerReferenceWarning: String,
    val suggestedReferenceAnswer: String
)

/** Estado del guion persistido a nivel de sesion. */
enum class ScriptStatus(val storageValue: String) {
    NOT_GENERATED("NOT_GENERATED"),
    GENERATED_PENDING_REVIEW("GENERATED_PENDING_REVIEW"),
    REVIEWED("REVIEWED");

    companion object {
        fun fromStorage(value: String?): ScriptStatus =
            entries.firstOrNull { it.storageValue == value } ?: NOT_GENERATED
    }
}
