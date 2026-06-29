package com.taller.app.gpt.script

import com.taller.app.gpt.GptConfig

/**
 * GEN01-FIX01: ajusta la configuración del cliente GPT solo para la generación del
 * guion de Seven.
 *
 * El presupuesto de tokens por defecto (pensado para las respuestas habladas cortas
 * del modo inteligente) es demasiado pequeño para un guion completo de varias
 * preguntas, por lo que la respuesta llegaba truncada y no se podía interpretar. Aquí
 * se amplía el presupuesto de salida y el tiempo de espera SOLO para esta tarea, sin
 * tocar la configuración global que usan el juez (MED01) ni las respuestas en vivo.
 */
object SessionScriptClientConfig {

    /** Presupuesto base de salida para intro, cierre y notas de la sesión. */
    const val BASE_OUTPUT_TOKENS = 900

    /** Presupuesto adicional por pregunta (pregunta amigable, 3 pistas y feedbacks). */
    const val PER_QUESTION_OUTPUT_TOKENS = 340

    /** Cota inferior y superior del presupuesto de salida para el guion. */
    const val MIN_OUTPUT_TOKENS = 1200
    const val MAX_OUTPUT_TOKENS = 6000

    /** Tiempo de espera para una acción única de la docente que puede tardar más. */
    const val SCRIPT_TIMEOUT_MS = 45_000L

    /** Presupuesto de salida estimado para [questionCount] preguntas. */
    fun outputTokensFor(questionCount: Int): Int {
        val safeCount = questionCount.coerceAtLeast(0)
        val estimated = BASE_OUTPUT_TOKENS + safeCount * PER_QUESTION_OUTPUT_TOKENS
        return estimated.coerceIn(MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS)
    }

    /**
     * Devuelve una copia de [base] con un presupuesto de salida y un tiempo de espera
     * adecuados para generar el guion completo de [questionCount] preguntas.
     */
    fun forScript(base: GptConfig, questionCount: Int): GptConfig = base.copy(
        maxOutputTokens = outputTokensFor(questionCount),
        timeoutMs = maxOf(base.timeoutMs, SCRIPT_TIMEOUT_MS)
    )
}
