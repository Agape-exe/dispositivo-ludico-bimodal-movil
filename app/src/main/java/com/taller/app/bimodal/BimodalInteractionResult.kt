package com.taller.app.bimodal

import com.taller.app.semantic.SemanticResult

/**
 * Resultado del procesamiento de una respuesta (o ausencia de respuesta) en la
 * pregunta actual.
 *
 * El orquestador lo expone como [BimodalFlowOrchestrator.lastResult] para que la
 * capa superior pueda decidir la retroalimentacion a mostrar y si corresponde
 * reintentar o avanzar.
 */
data class BimodalInteractionResult(
    val questionId: String,
    val questionIndex: Int,
    val attempt: Int,
    val maxAttempts: Int,
    val transcription: String?,
    val semanticResult: SemanticResult?,
    /** Estado de retroalimentacion asociado al resultado. */
    val feedbackState: BimodalInteractionState,
    /** Indica si la pregunta puede reintentarse con este resultado. */
    val canRetry: Boolean,
    /** Indica si la pregunta evaluada era la ultima de la actividad. */
    val isLastQuestion: Boolean
)
