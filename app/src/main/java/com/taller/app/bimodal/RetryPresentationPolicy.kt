package com.taller.app.bimodal

import kotlin.random.Random

/**
 * FINAL-CORE02: politica pura del flujo de reintento del modo inteligente.
 *
 * Problema que corrige: Seven decia "intentalo de nuevo" y algunos ninos
 * respondian de inmediato, aunque Seven todavia iba a repetir la pregunta. La
 * senal de "ahora respondes tu" quedaba ambigua.
 *
 * Orden correcto de un reintento:
 *  1. feedback breve de la respuesta anterior (contextual, sin regano);
 *  2. aviso claro de repeticion ("Escucha otra vez la pregunta.");
 *  3. repeticion de la pregunta completa;
 *  4. RECIEN entonces se abre la escucha (nunca mientras Seven habla).
 *
 * Es logica pura sin Android para poder probar el orden de los pasos.
 */
object RetryPresentationPolicy {

    /** Pasos ordenados de un reintento; la escucha siempre es el ultimo. */
    enum class Step { FEEDBACK, ANNOUNCE_REPEAT, QUESTION, OPEN_LISTENING }

    /** Orden canonico del reintento. */
    val STEP_ORDER: List<Step> = listOf(
        Step.FEEDBACK,
        Step.ANNOUNCE_REPEAT,
        Step.QUESTION,
        Step.OPEN_LISTENING
    )

    /**
     * Avisos de repeticion. Ninguno es un "intentalo de nuevo" aislado: todos
     * dejan claro que primero se escucha la pregunta y despues se responde.
     */
    val ANNOUNCE_PHRASES: List<String> = listOf(
        "Escucha otra vez la pregunta.",
        "Te la repito. Escucha con atencion.",
        "Escucha de nuevo y, cuando termine, me respondes."
    )

    fun announcePhrase(random: Random = Random.Default): String =
        ANNOUNCE_PHRASES[random.nextInt(ANNOUNCE_PHRASES.size)]

    /**
     * Segmentos de voz de la re-presentacion de un reintento: aviso + pregunta.
     * No incluye la introduccion narrativa completa (eso alargaba el reintento
     * y diluia la senal); la escucha se abre despues del ultimo segmento.
     */
    fun buildRetrySpeechSegments(
        questionText: String,
        random: Random = Random.Default
    ): List<String> = listOf(announcePhrase(random), questionText.trim())
}
