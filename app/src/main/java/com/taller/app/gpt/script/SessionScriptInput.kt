package com.taller.app.gpt.script

/**
 * GEN01: informacion textual pedagogica de la sesion enviada al modelo para
 * preparar el guion de Seven. Solo texto: nunca imagenes, audio ni datos de ninos.
 */
data class SessionScriptInput(
    val sessionName: String,
    val topic: String,
    val description: String,
    val objective: String,
    val ageLevel: String,
    val contextNotes: String,
    val questions: List<SessionScriptQuestionInput>
)

data class SessionScriptQuestionInput(
    val questionId: Long,
    val orderIndex: Int,
    val questionText: String,
    val referenceAnswer: String
)
