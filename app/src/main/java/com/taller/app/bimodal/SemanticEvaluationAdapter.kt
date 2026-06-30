package com.taller.app.bimodal

import com.taller.app.model.LearningQuestion
import com.taller.app.semantic.LocalAcceptanceType
import com.taller.app.semantic.SemanticEvaluator
import com.taller.app.semantic.SemanticResult

/**
 * Resultado de evaluar semanticamente la transcripcion final de una respuesta
 * contra la pregunta actual, junto con la latencia local de la evaluacion.
 *
 * @property result resultado semantico obtenido por el [SemanticEvaluator].
 * @property latencyMillis tiempo aproximado de la evaluacion en milisegundos.
 */
data class SemanticEvaluationOutcome(
    val result: SemanticResult,
    val latencyMillis: Long,
    /** MED01-FIX01: se acepto por equivalencia de sonido/onomatopeya. */
    val aliasApplied: Boolean = false,
    /** Motivo corto y seguro del alias aplicado, para el reporte tecnico. */
    val aliasReason: String? = null,
    /** Transcripcion normalizada local, util para el reporte tecnico. */
    val normalizedAnswer: String? = null,
    /** MED02: tipo de aceptacion/rechazo de la capa local. */
    val acceptanceType: LocalAcceptanceType = LocalAcceptanceType.NONE
) {
    /** Evento del orquestador correspondiente a este resultado semantico. */
    fun toEvent(): BimodalInteractionEvent =
        BimodalInteractionEvent.SemanticEvaluated(result)
}

/**
 * Conecta el [SemanticEvaluator] existente con el protocolo del flujo bimodal.
 *
 * Es logica pura, sin dependencias de Android: toma la transcripcion final de una
 * respuesta verbal, la compara con la respuesta esperada y las palabras clave de
 * la pregunta actual, mide la latencia local de la evaluacion y produce el
 * resultado que el orquestador consume mediante
 * [BimodalInteractionEvent.SemanticEvaluated].
 *
 * No decide reintentos ni avances ni reescribe el evaluador: solo traduce la
 * respuesta verbal a un resultado semantico del protocolo. La logica de intentos
 * vive en [BimodalFlowOrchestrator].
 *
 * @param evaluator evaluador semantico local existente.
 * @param clock proveedor de tiempo en nanosegundos, inyectable para pruebas.
 */
class SemanticEvaluationAdapter(
    private val evaluator: SemanticEvaluator = SemanticEvaluator(),
    private val clock: () -> Long = { System.nanoTime() }
) {

    /**
     * Evalua [transcription] contra [question] y devuelve el resultado semantico
     * con su latencia. No interpreta una transcripcion vacia ni un fallo de voz
     * como respuesta incorrecta: esa distincion la realiza el [SemanticEvaluator].
     */
    fun evaluate(
        transcription: String,
        question: LearningQuestion
    ): SemanticEvaluationOutcome {
        val startedAt = clock()
        val evaluation = evaluator.evaluateDetailed(
            transcription = transcription,
            expectedAnswer = question.expectedAnswer,
            keywords = question.keywords,
            questionText = question.questionText
        )
        val elapsedNanos = (clock() - startedAt).coerceAtLeast(0L)
        return SemanticEvaluationOutcome(
            result = evaluation.result,
            latencyMillis = elapsedNanos / 1_000_000,
            aliasApplied = evaluation.aliasApplied,
            aliasReason = evaluation.aliasReason,
            normalizedAnswer = evaluation.normalizedAnswer,
            acceptanceType = evaluation.acceptanceType
        )
    }
}
