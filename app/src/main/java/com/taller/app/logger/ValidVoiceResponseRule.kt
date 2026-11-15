package com.taller.app.logger

/**
 * FINAL-CORE02: regla pura que decide si una transcripcion cuenta como
 * "respuesta de voz valida" del nino para el registro oficial de la sesion
 * inteligente.
 *
 * Cuenta cuando la transcripcion:
 *  - no es nula ni vacia (tras recortar espacios),
 *  - contiene al menos una letra (no es solo ruido, signos o numeros sueltos),
 *  - tiene al menos dos caracteres utiles.
 *
 * NO decide el contexto: el llamador solo la aplica a las capturas de una
 * pregunta evaluada del modo inteligente. La activacion "Hola Seven", la
 * conversacion inicial, los timeouts sin habla y el modo temporizador nunca
 * pasan por esta regla.
 */
object ValidVoiceResponseRule {

    private val LETTER_REGEX = Regex("[a-zA-Záéíóúüñ]")

    fun isValid(transcript: String?): Boolean {
        val value = transcript?.trim() ?: return false
        if (value.length < 2) return false
        return LETTER_REGEX.containsMatchIn(value)
    }
}
