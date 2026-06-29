package com.taller.app.voice.prep

/**
 * Rol pedagogico de una frase que Seven puede decir durante una sesion.
 *
 * Sirve para clasificar cada audio preparado y, en el futuro, para reproducir el
 * audio correcto segun el momento del flujo. No guarda el audio: solo el texto y
 * su rol.
 */
enum class VoiceLineRole {
    /** Frase generica reutilizable entre sesiones (saludos, transiciones, etc.). */
    GENERIC,
    INTRO,
    CLOSING,
    QUESTION,
    HINT_1,
    HINT_2,
    HINT_3,
    POSITIVE_FEEDBACK,
    SUPPORTIVE_FEEDBACK,
    RETRY_PROMPT
}

/**
 * Una linea finita de voz a pre-generar: el texto que Seven dira y su rol. Las
 * lineas a nivel de pregunta llevan el [questionId] de referencia (solo como
 * metadato, nunca reemplaza la pregunta original en los registros de tesis).
 */
data class VoiceLine(
    val role: VoiceLineRole,
    val text: String,
    val questionId: Long? = null
)
