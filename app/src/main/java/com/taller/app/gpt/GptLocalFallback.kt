package com.taller.app.gpt

object GptLocalFallback {
    private val phrases = mapOf(
        "TEST_SETTINGS" to "¡Hola, explorador! Soy Seven. ¿Listo para descubrir cosas nuevas?",
        "GREETING" to "¡Bienvenido! Soy Seven, tu compañero de aventuras.",
        "GENERIC" to "¡Vamos a aprender juntos, explorador!"
    )

    fun phraseFor(contextTag: String): String =
        phrases[contextTag] ?: "¡Hola! Soy Seven."
}
