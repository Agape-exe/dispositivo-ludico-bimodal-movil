package com.taller.app.classic

/**
 * Banco local de frases neutras para el modo de temporizador fijo.
 *
 * Las frases son agradables pero no adaptativas: no dicen "correcto",
 * "incorrecto" ni revelan el resultado semántico de la respuesta del niño.
 * Selección anti-repetición: nunca repite la última frase usada en cada
 * categoría si hay al menos dos opciones disponibles.
 */
class FixedTimerNeutralPhraseBank {

    private val lastUsed = mutableMapOf<Category, Int>()

    enum class Category {
        SESSION_START,
        QUESTION_TRANSITION,
        ANSWER_RECEIVED,
        TIME_EXPIRED_WITH_RESPONSE,
        TIME_EXPIRED_NO_RESPONSE,
        NEXT_QUESTION,
        LAST_QUESTION,
        SESSION_COMPLETED
    }

    private val phrases = mapOf(
        Category.SESSION_START to listOf(
            "Hola, vamos a responder algunas preguntas sobre animales. Escucha con atención y responde con voz clara.",
            "Vamos a jugar con preguntas de animales. Responde cuando escuches cada pregunta.",
            "Empecemos una actividad corta sobre animales. Yo haré preguntas y tú puedes responder.",
            "Hoy tenemos algunas preguntas de animales. Responde con calma cuando estés listo.",
            "Vamos a iniciar. Escucha cada pregunta y responde con tu voz.",
            "Preparémonos para una ronda de preguntas sobre animales.",
            "Hola, hoy vamos a participar en una actividad de animales.",
            "Vamos a responder una pregunta a la vez. Escucha bien.",
            "Empecemos con calma. Yo haré la pregunta y tú respondes.",
            "Iniciamos la actividad. Responde cuando escuches cada pregunta."
        ),
        Category.QUESTION_TRANSITION to listOf(
            "Ahora viene una pregunta de animales.",
            "Escucha con atención esta pregunta.",
            "Vamos con una nueva pregunta.",
            "Preparando la siguiente pregunta.",
            "Ahora pensemos en otro animal.",
            "Sigamos con la actividad.",
            "Aquí viene una pregunta.",
            "Vamos con la siguiente parte.",
            "Escucha bien.",
            "Responde cuando estés listo."
        ),
        Category.ANSWER_RECEIVED to listOf(
            "¡Listo, escuché tu respuesta!",
            "¡Respuesta recibida! Sigamos con la actividad.",
            "¡Ya participaste en esta pregunta!",
            "¡Genial, seguimos jugando!",
            "¡Tu voz llegó hasta mí!",
            "¡Listo! Pasemos a otra pregunta.",
            "¡Gracias por responder! Continuemos con calma.",
            "¡Respuesta escuchada! Ahora viene otra.",
            "¡Qué buena energía! Sigamos.",
            "¡Perfecto, ya tenemos tu respuesta!",
            "¡Vamos avanzando en la actividad!",
            "¡Escuché tu idea! Ahora seguimos.",
            "¡Listo, ronda respondida!",
            "¡Gracias por participar en esta parte!",
            "¡Sigamos descubriendo animales!",
            "¡Respuesta recibida por mi radar de juguete!",
            "¡Ya te escuché! Vamos con la próxima.",
            "¡Buen intento! Sigamos con la actividad.",
            "¡Gracias por ayudarme con esta pregunta!",
            "¡Seguimos jugando con los animales!"
        ),
        Category.TIME_EXPIRED_WITH_RESPONSE to listOf(
            "Se terminó el tiempo de esta pregunta. Sigamos.",
            "Tiempo cumplido. Vamos con la siguiente.",
            "Esta ronda terminó. Continuemos con otra pregunta.",
            "El tiempo terminó y seguimos avanzando.",
            "Terminó el tiempo. Vamos a descubrir otra pregunta.",
            "Pasamos a la siguiente parte.",
            "Esta pregunta ya terminó. Sigamos.",
            "Ronda completada. Vamos con otra.",
            "El reloj terminó su vuelta. Continuemos.",
            "Siguiente pista de animales."
        ),
        Category.TIME_EXPIRED_NO_RESPONSE to listOf(
            "Se terminó el tiempo. No pasa nada, vamos con la siguiente.",
            "Esta vez no escuché respuesta, pero seguimos jugando.",
            "El tiempo terminó. Intentemos la próxima pregunta.",
            "No pasa nada, continuemos con otra.",
            "Esta pregunta ya terminó. Vamos a la siguiente.",
            "El reloj avanzó rápido esta vez. Sigamos.",
            "No escuché tu voz en esta ronda. Vamos con otra oportunidad.",
            "Está bien, seguimos con calma.",
            "El tiempo terminó, pero la actividad continúa.",
            "Vamos a intentarlo en la siguiente pregunta."
        ),
        Category.NEXT_QUESTION to listOf(
            "Ahora viene otra pregunta de animales.",
            "Sigamos con una nueva pista.",
            "Vamos con la siguiente ronda.",
            "Preparando otra pregunta.",
            "Escucha con atención la siguiente.",
            "Ahora pensemos en otro animal.",
            "Seguimos explorando el mundo de los animales.",
            "Vamos con una pregunta más.",
            "La actividad continúa.",
            "Aquí viene la siguiente pregunta."
        ),
        Category.LAST_QUESTION to listOf(
            "Llegamos a la última pregunta de la actividad.",
            "Esta será la última pregunta de animales.",
            "Vamos con la última ronda.",
            "Última pregunta, escucha con atención.",
            "Terminemos la actividad con una pregunta más.",
            "Nos queda una última parte.",
            "Vamos con el último reto de animales.",
            "Esta es la última pregunta.",
            "Falta poquito para terminar.",
            "Vamos a cerrar con una última pregunta."
        ),
        Category.SESSION_COMPLETED to listOf(
            "Terminamos la actividad. Gracias por participar.",
            "Muy bien, completamos todas las preguntas.",
            "La actividad terminó. Gracias por jugar conmigo.",
            "Hemos terminado por hoy. Me gustó escucharte.",
            "Gracias por responder las preguntas de animales.",
            "La actividad de animales terminó por ahora.",
            "Completamos la ronda. Gracias por participar.",
            "Terminamos esta parte. Lo hiciste con mucho ánimo.",
            "Gracias por acompañarme en esta actividad.",
            "Ya terminamos. Nos vemos en otra aventura."
        )
    )

    fun get(category: Category): String {
        val list = phrases[category] ?: return ""
        if (list.isEmpty()) return ""

        val last = lastUsed[category] ?: -1
        val candidates = list.indices.filter { it != last }
        val chosen = if (candidates.isNotEmpty()) candidates.random() else list.indices.random()
        lastUsed[category] = chosen
        return list[chosen]
    }

    fun getSessionStart(): String = get(Category.SESSION_START)

    fun getQuestionTransition(isLast: Boolean): String =
        if (isLast) get(Category.LAST_QUESTION) else get(Category.QUESTION_TRANSITION)

    fun getAnswerReceived(): String = get(Category.ANSWER_RECEIVED)

    fun getTimeExpired(hadPartialResponse: Boolean): String =
        if (hadPartialResponse) get(Category.TIME_EXPIRED_WITH_RESPONSE)
        else get(Category.TIME_EXPIRED_NO_RESPONSE)

    fun getSessionCompleted(): String = get(Category.SESSION_COMPLETED)
}
