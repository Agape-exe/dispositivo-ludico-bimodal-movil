package com.taller.app.bimodal.feedback

/**
 * Mensaje de retroalimentacion general tipo profesor ya resuelto: la categoria
 * elegida, el texto a reproducir y el tiempo que tomo generarlo localmente.
 *
 * El texto se genera mediante reglas y un banco de frases predefinidas: no
 * proviene de ningun servicio externo ni de generacion automatica de lenguaje.
 *
 * @param type categoria de retroalimentacion seleccionada.
 * @param text frase a mostrar y a enviar a la voz del juguete.
 * @param generationLatencyNanos tiempo de seleccion de la frase, en nanosegundos.
 */
data class GeneralTeacherFeedbackMessage(
    val type: GeneralTeacherFeedbackType,
    val text: String,
    val generationLatencyNanos: Long = 0L
) {
    /** Tiempo de generacion expresado en microsegundos (practicamente inmediato). */
    val generationLatencyMicros: Long get() = generationLatencyNanos / 1_000
}
