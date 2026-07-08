package com.taller.app.bimodal

import java.text.Normalizer

/**
 * FINAL-FLOW01 / FINAL-FLOW01-FIX01: banco local de la conversacion inicial.
 *
 * Antes de las preguntas evaluadas, Seven puede invitar al nino a decirle algo
 * breve. Este banco resuelve LOCALMENTE las preguntas mas comunes (como se llama,
 * quien es, que van a hacer) con respuestas cortas, calidas y seguras. Solo cuando
 * la pregunta no coincide con ningun caso local se recurre al juez conversacional
 * limitado (ver [InitialConversationResponder]).
 *
 * Es logica pura sin Android: no usa IA generativa, no sintetiza voz por red y no
 * guarda el contenido de lo que dice el nino. La conversacion nunca cuenta como
 * pregunta evaluada.
 */
object InitialConversationBank {

    private val INVITE_PHRASES = listOf(
        "Antes de empezar, puedes contarme algo o hacerme una preguntita.",
        "Antes de comenzar, ¿quieres decirme algo? Te escucho.",
        "Antes de la aventura, puedes hacerme una preguntita si quieres."
    )

    private val TRANSITION_PHRASES = listOf(
        "Ahora sí, empecemos nuestra aventura.",
        "¡Listo! Ahora sí, vamos a jugar y aprender.",
        "Muy bien, ahora empieza nuestra misión."
    )

    /** Frase de respaldo cuando la pregunta no se puede responder de forma segura. */
    const val SAFE_FALLBACK_ANSWER =
        "Qué buena pregunta. La guardaré en mi nave, y ahora sigamos con nuestra aventura."

    /** Redireccion suave para preguntas fuera del alcance del juguete. */
    const val REDIRECT_ANSWER =
        "Esa pregunta es grande para mi nave. Mejor sigamos con nuestra aventura."

    fun invitePhrase(): String = INVITE_PHRASES.random()

    fun transitionPhrase(): String = TRANSITION_PHRASES.random()

    /**
     * Respuesta local para una pregunta inicial conocida, o null si ninguna coincide
     * (en cuyo caso el responder puede delegar al juez conversacional). [topic] es el
     * tema de la sesion, usado para explicar de que trata la actividad.
     */
    fun localAnswer(childQuestion: String, topic: String): String? {
        val normalized = normalize(childQuestion)
        if (normalized.isBlank()) return null

        val topicText = topic.trim().ifBlank { "cosas divertidas" }
        // El orden importa: los casos mas especificos van primero para que, por
        // ejemplo, "eres un robot" no se confunda con "que eres".
        return when {
            containsAny(normalized, NAME_MARKERS) ->
                "Me llamo Seven. Soy un explorador curioso que quiere aprender contigo."
            containsAny(normalized, ROBOT_MARKERS) ->
                "Soy Seven, tu amigo explorador para esta aventura."
            containsAny(normalized, WHAT_ARE_YOU_MARKERS) ->
                "Soy Seven, un pequeño explorador que está conociendo la Tierra."
            containsAny(normalized, ACTIVITY_MARKERS) ->
                "Vamos a jugar con unas preguntitas sobre $topicText. Puedes responder con calma."
            containsAny(normalized, GREETING_MARKERS) ->
                "¡Hola! Soy Seven. Me alegra mucho jugar contigo."
            else -> null
        }
    }

    /** Frases fijas expuestas para validaciones/pruebas de seguridad del contenido. */
    fun staticPhrases(): List<String> =
        INVITE_PHRASES + TRANSITION_PHRASES + listOf(SAFE_FALLBACK_ANSWER, REDIRECT_ANSWER)

    /** Ejemplos de respuestas locales, para pruebas de seguridad del contenido. */
    fun sampleLocalAnswers(): List<String> = listOf(
        "Me llamo Seven. Soy un explorador curioso que quiere aprender contigo.",
        "Soy Seven, tu amigo explorador para esta aventura.",
        "Soy Seven, un pequeño explorador que está conociendo la Tierra.",
        "Vamos a jugar con unas preguntitas sobre animales. Puedes responder con calma.",
        "¡Hola! Soy Seven. Me alegra mucho jugar contigo."
    )

    private val NAME_MARKERS = listOf(
        "como te llamas", "cual es tu nombre", "tu nombre", "quien eres",
        "como te dices", "como te llama", "tienes nombre"
    )

    private val ROBOT_MARKERS = listOf(
        "eres un robot", "eres robot", "eres una maquina", "eres maquina",
        "eres humano", "eres real", "eres de verdad", "eres un juguete"
    )

    private val WHAT_ARE_YOU_MARKERS = listOf(
        "que eres", "que cosa eres", "que animal eres", "eres un animal", "eres un alien"
    )

    private val ACTIVITY_MARKERS = listOf(
        "que vamos a hacer", "que haremos", "que hacemos", "de que trata",
        "que vamos a jugar", "a que jugamos", "que jugamos", "que voy a hacer",
        "de que se trata", "que hay que hacer"
    )

    private val GREETING_MARKERS = listOf(
        "hola", "buenos dias", "buenas tardes", "como estas", "que tal"
    )

    private fun containsAny(normalized: String, markers: List<String>): Boolean =
        markers.any { normalized.contains(it) }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase()
            .replace(Regex("[^a-z0-9ñ\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
