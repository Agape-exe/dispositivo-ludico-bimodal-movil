package com.taller.app.bimodal

/**
 * FINAL-FLOW01: banco local de la conversacion inicial opcional.
 *
 * Antes de las preguntas evaluadas, Seven puede invitar al nino a decirle algo
 * breve. Las respuestas de Seven salen SOLO de este banco finito y seguro: no se
 * usa IA generativa ni sintesis de voz por red para texto impredecible, y el
 * contenido de lo que dice el nino no se guarda. Es logica pura sin Android.
 *
 * La conversacion nunca cuenta como pregunta evaluada: no toca el orquestador ni
 * los conteos de la sesion.
 */
object InitialConversationBank {

    private val INVITE_PHRASES = listOf(
        "Antes de empezar, puedes contarme algo o hacerme una preguntita.",
        "Antes de comenzar, ¿quieres decirme algo? Te escucho.",
        "Antes de la aventura, puedes hacerme una preguntita si quieres."
    )

    /**
     * Respuestas breves, calidas y genericas: sirven para cualquier cosa que el
     * nino diga sin repetir su contenido ni inventar datos. Se rotan por turno.
     */
    private val REPLY_PHRASES = listOf(
        "¡Qué buena pregunta! Me encanta explorar y aprender contigo.",
        "¡Qué divertido! A mí también me gustan esas cosas.",
        "¡Me gusta escucharte! Eres muy curioso, como yo."
    )

    private val TRANSITION_PHRASES = listOf(
        "Ahora sí, empecemos nuestra aventura.",
        "¡Listo! Ahora sí, vamos a jugar y aprender.",
        "Muy bien, ahora empieza nuestra misión."
    )

    fun invitePhrase(): String = INVITE_PHRASES.random()

    /** Respuesta para el turno [turnNumber] (1, 2, 3...), rotando sin repetir seguido. */
    fun replyPhrase(turnNumber: Int): String {
        val index = ((turnNumber - 1).coerceAtLeast(0)) % REPLY_PHRASES.size
        return REPLY_PHRASES[index]
    }

    fun transitionPhrase(): String = TRANSITION_PHRASES.random()

    /** Frases expuestas para validaciones/pruebas de seguridad del contenido. */
    fun allPhrases(): List<String> = INVITE_PHRASES + REPLY_PHRASES + TRANSITION_PHRASES
}
