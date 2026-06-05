package com.taller.app.bimodal.feedback

import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.semantic.SemanticResult

/**
 * Contexto minimo que necesita [GeneralTeacherFeedbackGenerator] para decidir la
 * categoria de retroalimentacion y elegir una frase.
 *
 * Es un valor inmutable e independiente de Android: lo construye la capa de UI a
 * partir del estado del orquestador y del ultimo resultado. No genera
 * explicaciones especificas del contenido; [questionText] y [expectedAnswer] solo
 * se incluyen para no perder contexto y para reglas internas, nunca para
 * pronunciarse, y la respuesta esperada jamas debe revelarse mientras
 * [canRetry] sea verdadero.
 *
 * @param state estado de retroalimentacion o de transicion del flujo.
 * @param questionIndex indice (cero-basado) de la pregunta actual.
 * @param currentAttempt intento vigente (1-basado).
 * @param maxAttempts intentos maximos de la pregunta.
 * @param canRetry si el orquestador permite reintentar la pregunta actual.
 * @param isLastQuestion si la pregunta evaluada era la ultima de la actividad.
 * @param semanticResult resultado semantico del intento, si lo hubo.
 * @param timeExpired si el desenlace fue por tiempo agotado.
 * @param technicalError si el desenlace fue un error tecnico recuperable.
 * @param sttError si hubo un fallo del reconocimiento de voz.
 * @param questionText texto de la pregunta (solo contexto, no se pronuncia).
 * @param expectedAnswer respuesta esperada (solo reglas internas, nunca se dice
 *        si quedan intentos).
 */
data class GeneralTeacherFeedbackContext(
    val state: BimodalInteractionState,
    val questionIndex: Int = 0,
    val currentAttempt: Int = 1,
    val maxAttempts: Int = 1,
    val canRetry: Boolean = false,
    val isLastQuestion: Boolean = false,
    val semanticResult: SemanticResult? = null,
    val timeExpired: Boolean = state == BimodalInteractionState.TIME_EXPIRED,
    val technicalError: Boolean = state == BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR,
    val sttError: Boolean = false,
    val questionText: String? = null,
    val expectedAnswer: String? = null
) {
    /** Intentos que aun quedan disponibles en la pregunta actual (nunca negativo). */
    val attemptsRemaining: Int get() = (maxAttempts - currentAttempt).coerceAtLeast(0)

    /** Indica si todavia quedan intentos disponibles en la pregunta actual. */
    val hasAttemptsLeft: Boolean get() = currentAttempt < maxAttempts
}
