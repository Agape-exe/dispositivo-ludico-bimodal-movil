package com.taller.app.bimodal

import com.taller.app.semantic.SemanticResult

/**
 * Instantanea del progreso de la sesion bimodal.
 *
 * Describe la actividad en curso, la pregunta actual, el intento vigente y los
 * ultimos datos capturados. Es un valor inmutable: el orquestador reemplaza la
 * instancia cada vez que el progreso cambia.
 */
data class BimodalQuestionProgress(
    val activityId: String,
    val activityName: String,
    val totalQuestions: Int,
    val currentQuestionIndex: Int,
    val currentQuestionId: String,
    val currentQuestionText: String,
    val currentAttempt: Int,
    val maxAttempts: Int,
    val maxTimeSeconds: Int,
    val lastTranscription: String? = null,
    val lastSemanticResult: SemanticResult? = null,
    val sessionStartedAt: Long,
    val questionStartedAt: Long? = null
) {
    /** Numero de pregunta orientado al usuario (1-based). */
    val questionNumber: Int get() = currentQuestionIndex + 1

    /** Indica si la pregunta actual es la ultima de la actividad. */
    val isLastQuestion: Boolean get() = currentQuestionIndex == totalQuestions - 1

    /** Indica si todavia quedan intentos disponibles en la pregunta actual. */
    val hasAttemptsLeft: Boolean get() = currentAttempt < maxAttempts
}
