package com.taller.app.classic

import com.taller.app.model.LocalMediationKey

/**
 * Banco local de frases neutras para el modo de temporizador fijo ("ronda rápida").
 *
 * Las frases son dinámicas y participativas, pero nunca adaptativas: no dicen
 * "correcto", "incorrecto", "exacto" ni revelan el resultado semántico de la
 * respuesta del niño. El modo clásico reconoce la participación, no evalúa el
 * desempeño.
 *
 * Decisiones de diseño de C01.1:
 * - La transición y la pregunta se combinan en una sola frase ([getRoundPrompt])
 *   para reducir capas de voz y demora.
 * - La última ronda usa frases propias que no anuncian "otra pregunta"
 *   ([getRoundPrompt] con `isLast`, [getAnswerReceived] con `isLast`,
 *   [getTimeExpired] con `isLast`).
 * - Selección anti-repetición: nunca repite la última frase usada en cada
 *   categoría si hay al menos dos opciones disponibles.
 */
class FixedTimerNeutralPhraseBank {

    private val lastUsed = mutableMapOf<String, Int>()

    private val sessionStart = listOf(
        "Vamos a jugar una ronda rápida de animales. Yo diré una pista y tú respondes con tu voz.",
        "Empezamos una ronda rápida. Escucha cada pista y responde cuando estés listo.",
        "Hoy jugaremos con pistas de animales. Responde antes de que termine el tiempo.",
        "Vamos a responder algunas rondas de animales. Yo pregunto y tú participas.",
        "Comenzamos el juego de rondas. Escucha bien y responde con voz clara."
    )

    /** Plantillas genéricas de ronda. Usan {n} (número de ronda) y {question}. */
    private val roundQuestionPrompt = listOf(
        "Ronda {n}. Escucha esta pista animal: {question}",
        "Ronda {n}. Vamos con una pista rápida: {question}",
        "Ronda {n}. Ahora piensa en animales: {question}",
        "Ronda {n}. Responde con tu voz: {question}",
        "Ronda {n}. Aquí viene una pista: {question}",
        "Ronda {n}. Vamos a jugar con esta pregunta: {question}",
        "Ronda {n}. Escucha y responde: {question}",
        "Ronda {n}. Atención a esta pista: {question}",
        "Ronda {n}. Vamos con el siguiente reto: {question}"
    )

    /**
     * Lead-ins naturales por [LocalMediationKey]. Cada plantilla combina ronda +
     * pista temática + {question}, sin evaluar ni adelantar la respuesta.
     */
    private val mediationPrompts: Map<LocalMediationKey, List<String>> = mapOf(
        LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
            "Ronda {n}. Piensa en un perrito: {question}",
            "Ronda {n}. Imagina un perro moviendo la colita: {question}",
            "Ronda {n}. Escucha esta pista de perrito: {question}"
        ),
        LocalMediationKey.ANIMAL_DOMESTIC to listOf(
            "Ronda {n}. Pensemos en una mascota: {question}",
            "Ronda {n}. Imagina una casita con una mascota: {question}",
            "Ronda {n}. Busca en tu mente una mascota: {question}"
        ),
        LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
            "Ronda {n}. Piensa en un gatito: {question}",
            "Ronda {n}. Imagina un gato caminando suavecito: {question}",
            "Ronda {n}. Escucha esta pista de gatito: {question}"
        ),
        LocalMediationKey.ANIMAL_FARM to listOf(
            "Ronda {n}. Imagina una granja: {question}",
            "Ronda {n}. Pensemos en una granja con corrales: {question}",
            "Ronda {n}. Vamos con una pista de granja: {question}"
        )
    )

    /** Plantillas de última ronda. No anuncian otra pregunta. Usan {question}. */
    private val lastRoundPrompt = listOf(
        "Última ronda. Escucha con atención: {question}",
        "Llegamos a la última ronda: {question}",
        "Última pista de animales: {question}",
        "Vamos a cerrar con esta ronda: {question}",
        "Falta poquito. Última pregunta: {question}"
    )

    private val answerReceived = listOf(
        "¡Respuesta recibida por mi radar animal!",
        "¡Listo, tu voz llegó hasta mí!",
        "¡Ronda respondida!",
        "¡Escuché tu idea, seguimos!",
        "¡Tu respuesta quedó lista para esta ronda!",
        "¡Muy bien, seguimos con la actividad!",
        "¡Listo, pasamos a otra pista!",
        "¡Participación recibida!",
        "¡Ya te escuché!",
        "¡Vamos avanzando!"
    )

    private val lastAnswerReceived = listOf(
        "¡Última respuesta recibida! Completamos todas las rondas.",
        "¡Listo, escuché tu última respuesta!",
        "¡Última ronda respondida!",
        "¡Tu voz llegó en la última ronda!",
        "¡Gracias, completamos la actividad!",
        "¡Ronda final recibida!",
        "¡Muy bien, llegamos al final!",
        "¡Última participación registrada!",
        "¡Ya terminamos las rondas!",
        "¡Gracias por responder hasta el final!"
    )

    private val timeExpiredWithResponse = listOf(
        "El tiempo de esta ronda terminó. Seguimos.",
        "Tiempo cumplido. Vamos con otra pista.",
        "Esta ronda terminó. Continuemos.",
        "El reloj terminó su vuelta. Sigamos.",
        "Ronda completada. Vamos con la siguiente."
    )

    private val timeExpiredNoResponse = listOf(
        "El tiempo terminó. No pasa nada, seguimos con otra ronda.",
        "Esta vez no escuché respuesta. Vamos con la siguiente.",
        "Se acabó el tiempo. Intentemos la próxima.",
        "El reloj fue rápido esta vez. Sigamos.",
        "No escuché tu voz en esta ronda. Continuemos con calma."
    )

    /** Tiempo agotado en la última ronda: no anuncia otra pregunta. */
    private val lastTimeExpired = listOf(
        "El tiempo de la última ronda terminó. Completamos la actividad.",
        "Se acabó el tiempo de esta última pista. Llegamos al final.",
        "El reloj terminó en la ronda final. Ya terminamos las rondas.",
        "Cerramos la última ronda. Gracias por participar.",
        "La última ronda terminó. Completamos todas las pistas."
    )

    private val sessionCompleted = listOf(
        "La actividad terminó. Gracias por participar.",
        "Terminamos la ronda rápida de animales.",
        "Completamos todas las rondas. Gracias por jugar.",
        "La actividad finalizó. Me gustó escucharte.",
        "Gracias por participar en el juego de animales.",
        "Cerramos la actividad por ahora.",
        "Terminamos el recorrido de animales.",
        "Gracias por acompañarme en esta ronda.",
        "La ronda rápida terminó.",
        "Ya terminamos. Nos vemos en otra actividad."
    )

    // ----- API ------------------------------------------------------------------

    fun getSessionStart(): String = pick("session_start", sessionStart)

    /**
     * Frase combinada de transición + pregunta para una ronda.
     *
     * Reduce las capas de voz a una sola reproducción. En la última ronda usa
     * [lastRoundPrompt], que nunca anuncia otra pregunta.
     */
    fun getRoundPrompt(
        round: Int,
        questionText: String,
        isLast: Boolean,
        mediationKey: LocalMediationKey = LocalMediationKey.NONE
    ): String {
        val template = when {
            isLast -> pick("last_round_prompt", lastRoundPrompt)
            mediationKey != LocalMediationKey.NONE ->
                pick("mediation_${mediationKey.name}", mediationPrompts[mediationKey] ?: roundQuestionPrompt)
            else -> pick("round_prompt", roundQuestionPrompt)
        }
        return fill(template, round, questionText)
    }

    /**
     * Frase neutra al recibir respuesta. En la última ronda usa
     * [lastAnswerReceived], que cierra sin prometer otra pregunta.
     */
    fun getAnswerReceived(isLast: Boolean = false): String =
        if (isLast) pick("last_answer_received", lastAnswerReceived)
        else pick("answer_received", answerReceived)

    /**
     * Frase neutra al agotarse el tiempo. En la última ronda usa
     * [lastTimeExpired], que no anuncia otra pregunta.
     */
    fun getTimeExpired(hadPartialResponse: Boolean, isLast: Boolean = false): String = when {
        isLast -> pick("last_time_expired", lastTimeExpired)
        hadPartialResponse -> pick("time_expired_with", timeExpiredWithResponse)
        else -> pick("time_expired_no", timeExpiredNoResponse)
    }

    fun getSessionCompleted(): String = pick("session_completed", sessionCompleted)

    // ----- Internal -------------------------------------------------------------

    private fun fill(template: String, round: Int, questionText: String): String =
        template
            .replace("{n}", round.toString())
            .replace("{question}", questionText.trim())

    private fun pick(bucket: String, list: List<String>): String {
        if (list.isEmpty()) return ""
        val last = lastUsed[bucket] ?: -1
        val candidates = list.indices.filter { it != last }
        val chosen = if (candidates.isNotEmpty()) candidates.random() else list.indices.random()
        lastUsed[bucket] = chosen
        return list[chosen]
    }
}
