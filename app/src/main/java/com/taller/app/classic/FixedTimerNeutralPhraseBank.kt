package com.taller.app.classic

import com.taller.app.model.LocalMediationKey

/**
 * Banco local de frases de Seven para el modo de temporizador fijo ("ronda rápida").
 *
 * Seven mantiene su identidad de alien explorador en el modo clásico, pero sin
 * evaluar semánticamente la respuesta del niño. Las frases son divertidas y
 * participativas, pero nunca adaptativas: no dicen "correcto", "incorrecto" ni
 * revelan el resultado semántico.
 *
 * Vocabulario de Seven en modo clásico: reto, ronda, ronda espacial, señal,
 * exploración, registro. No se usa "pista" como término principal.
 *
 * Decisiones de diseño:
 * - La transición y la pregunta se combinan en una sola frase ([getRoundPrompt]).
 * - La última ronda usa frases que no anuncian "otra pregunta".
 * - Selección anti-repetición: nunca repite la última frase usada en cada
 *   categoría si hay al menos dos opciones disponibles.
 * - [getNextRound] es un anuncio opcional entre rondas (sin la pregunta).
 */
class FixedTimerNeutralPhraseBank {

    private val lastUsed = mutableMapOf<String, Int>()

    // ----- Inicio de sesión (CLASSIC_SESSION_START) --------------------------------

    private val sessionStart = listOf(
        "Seven activará una ronda rápida de exploración. Responde con tu voz antes de que termine el tiempo.",
        "Iniciamos rondas espaciales. Seven escuchará tus respuestas y seguirá avanzando.",
        "Comienza la exploración rápida de Seven. Cada ronda tendrá un reto con tiempo.",
        "Seven preparó una ronda de retos terrestres. Responde cuando escuches cada reto.",
        "Activando temporizador espacial. Escucha cada reto y responde con tu voz.",
        "Seven iniciará una misión rápida. Cada ronda durará solo unos segundos.",
        "Mi nave está lista para recibir señales. Vamos con rondas rápidas.",
        "Comienza la ronda de exploración. Seven escuchará tus respuestas una por una.",
        "Seven encendió su reloj espacial. Responde cada reto antes de que termine.",
        "Empezamos una misión con tiempo. Seven recibirá tus señales en cada ronda."
    )

    // ----- Ronda con pregunta (CLASSIC_ROUND_PROMPT) --------------------------------
    // Usan {n} como número de ronda y {question} como texto de la pregunta.

    private val roundQuestionPrompt = listOf(
        "Ronda espacial {n}. Responde este reto: {question}",
        "Ronda {n}. Seven necesita escuchar tu señal: {question}",
        "Ronda rápida {n}. El reto es: {question}",
        "Exploración {n}. Responde con tu voz: {question}",
        "Ronda espacial {n}. Seven está atento: {question}",
        "Reto rápido {n}. Dime tu respuesta: {question}",
        "Ronda {n}. Mi nave escuchará tu voz: {question}",
        "Exploración rápida {n}. Vamos con este reto: {question}",
        "Ronda {n}. Seven activa su antena: {question}",
        "Ronda espacial {n}. Tienes unos segundos: {question}"
    )

    // Última ronda: no anuncia otra pregunta. Usan {question} (sin número de ronda).
    private val lastRoundPrompt = listOf(
        "Última ronda espacial. Responde este reto: {question}",
        "Seven llega a la ronda final. Escucha: {question}",
        "Última señal para Seven. El reto es: {question}",
        "Ronda final de exploración. Responde: {question}",
        "Seven cierra su misión con este reto: {question}"
    )

    // ----- Lead-ins temáticos por mediationKey (CLASSIC_ROUND_PROMPT) -------------

    private val mediationPrompts: Map<LocalMediationKey, List<String>> = mapOf(
        LocalMediationKey.ANIMAL_DOG_SOUND to listOf(
            "Ronda {n}. Seven piensa en un perrito: {question}",
            "Ronda {n}. Imagina un perro moviendo la cola: {question}",
            "Ronda espacial {n}. Escucha este reto de perrito: {question}"
        ),
        LocalMediationKey.ANIMAL_DOMESTIC to listOf(
            "Ronda {n}. Seven busca una mascota terrestre: {question}",
            "Ronda espacial {n}. Imagina una casita con mascota: {question}",
            "Ronda {n}. Seven quiere conocer una mascota: {question}"
        ),
        LocalMediationKey.ANIMAL_CAT_SOUND to listOf(
            "Ronda {n}. Seven piensa en un gatito: {question}",
            "Ronda espacial {n}. Imagina un gato caminando suavecito: {question}",
            "Ronda {n}. Escucha este reto de gatito: {question}"
        ),
        LocalMediationKey.ANIMAL_FARM to listOf(
            "Ronda {n}. Seven imagina una granja: {question}",
            "Ronda espacial {n}. Pensemos en una granja con corrales: {question}",
            "Ronda {n}. Vamos con un reto de la granja: {question}"
        )
    )

    // ----- Respuesta recibida (CLASSIC_ANSWER_RECEIVED) ---------------------------

    private val answerReceived = listOf(
        "Señal recibida por la nave de Seven.",
        "Tu voz llegó hasta mi radar espacial.",
        "Respuesta registrada en la nave.",
        "Seven recibió tu señal.",
        "Dato recibido para la exploración.",
        "Tu señal entró a mi computadora espacial.",
        "Seven escuchó tu respuesta.",
        "Registro de voz completado.",
        "Señal guardada para esta ronda.",
        "Tu respuesta llegó a la nave de Seven."
    )

    // ----- Última respuesta recibida (CLASSIC_LAST_ANSWER_RECEIVED) ---------------

    private val lastAnswerReceived = listOf(
        "Última señal recibida. Seven completó todas las rondas.",
        "Tu última respuesta llegó a la nave.",
        "Última ronda registrada por Seven.",
        "Señal final recibida. La exploración rápida terminó.",
        "Seven guardó la última respuesta de esta misión.",
        "Último dato recibido en la computadora espacial.",
        "La última ronda quedó registrada.",
        "Seven recibió tu última señal.",
        "Última respuesta guardada. La misión está por cerrar.",
        "Registro final completado por la nave de Seven."
    )

    // ----- Tiempo agotado (CLASSIC_TIMEOUT_OR_NO_RESPONSE) -----------------------

    private val timeoutPhrases = listOf(
        "El tiempo de esta ronda terminó. Seven seguirá con la exploración.",
        "Mi reloj espacial llegó al final de esta ronda.",
        "La ronda terminó y Seven continuará con la siguiente.",
        "El temporizador espacial se apagó por esta vez.",
        "Esta ronda llegó a su fin. Sigamos explorando.",
        "Seven no recibió señal a tiempo. Continuaremos la misión.",
        "El tiempo se acabó para esta ronda espacial.",
        "La nave cerró esta ronda. Vamos a seguir.",
        "El reloj de Seven marcó el final de esta parte.",
        "Esta señal no llegó a tiempo, pero la exploración continúa."
    )

    // Para la última ronda se filtran las que anuncian continuación.
    private val lastTimeoutPhrases = timeoutPhrases.filterNot { phrase ->
        val lower = phrase.lowercase()
        listOf("siguiente", "otra ronda", "próxima", "intentemos").any { lower.contains(it) }
    }

    // ----- Transición entre rondas (CLASSIC_NEXT_ROUND) ---------------------------

    private val nextRoundPhrases = listOf(
        "Preparando la siguiente ronda espacial.",
        "Seven activa otro reto rápido.",
        "Mi nave está lista para una nueva ronda.",
        "Continuamos con otra exploración.",
        "Vamos con el siguiente reto de Seven.",
        "Nueva ronda en camino.",
        "La antena de Seven se prepara otra vez.",
        "Sigamos con otra ronda rápida.",
        "Seven abre un nuevo registro espacial.",
        "La misión continúa con otro reto."
    )

    // ----- Cierre de sesión (CLASSIC_SESSION_COMPLETED) ---------------------------

    private val sessionCompleted = listOf(
        "La ronda rápida terminó. Gracias por ayudar a Seven.",
        "Seven completó la exploración con tiempo.",
        "La misión rápida llegó a su final.",
        "Terminamos todas las rondas espaciales.",
        "Seven guardó las señales de esta exploración.",
        "La actividad terminó. Gracias por participar con Seven.",
        "Exploración finalizada. La nave cerró sus registros.",
        "Seven terminó la ronda de retos terrestres.",
        "Misión con temporizador completada.",
        "Todas las rondas fueron registradas por Seven."
    )

    // ----- API -------------------------------------------------------------------

    fun getSessionStart(): String = pick("session_start", sessionStart)

    /**
     * Frase combinada de transición + pregunta para una ronda.
     *
     * En la última ronda usa [lastRoundPrompt], que no anuncia otra pregunta.
     * Con [mediationKey] válida usa lead-ins temáticos de Seven.
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
     * [lastTimeoutPhrases], que no anuncia otra pregunta.
     */
    fun getTimeExpired(hadPartialResponse: Boolean, isLast: Boolean = false): String =
        if (isLast) pick("last_time_expired", lastTimeoutPhrases)
        else pick("time_expired", timeoutPhrases)

    /** Anuncio opcional entre rondas, sin incluir la pregunta. */
    fun getNextRound(): String = pick("next_round", nextRoundPhrases)

    fun getSessionCompleted(): String = pick("session_completed", sessionCompleted)

    // ----- Interno ---------------------------------------------------------------

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
