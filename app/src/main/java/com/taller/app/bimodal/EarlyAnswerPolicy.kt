package com.taller.app.bimodal

import com.taller.app.settings.AppSettings

/**
 * FINAL-FLOW01: politica pura de la "ventana temprana de escucha" en reintentos.
 *
 * En pruebas reales, muchos ninos empiezan a responder apenas Seven dice la frase
 * de apoyo ("intentemos nuevamente"), antes de que repita toda la pregunta. Esta
 * politica decide cuando abrir una escucha breve ANTES de releer la pregunta, para
 * no perder esas respuestas anticipadas.
 *
 * Reglas:
 *  - solo en reintentos (intento > 1): la primera presentacion siempre lee la
 *    pregunta completa;
 *  - solo con la opcion activada y permiso de microfono;
 *  - nunca mientras Seven esta hablando (sin control de eco, el reconocimiento
 *    capturaria la voz del propio juguete).
 *
 * Es logica pura sin Android, para poder probarla de forma aislada.
 */
object EarlyAnswerPolicy {

    /** Pausa breve entre el fin de la voz de apoyo y el inicio de la escucha. */
    const val PRE_LISTEN_PAUSE_MS = 300L

    /**
     * Tope de espera del resultado final cuando el nino SI empezo a hablar dentro
     * de la ventana: la frase se deja terminar, pero nunca de forma indefinida.
     */
    const val COMPLETION_TIMEOUT_MS = 8_000L

    /** True si corresponde abrir la ventana temprana para este intento. */
    fun shouldOpenEarlyWindow(
        enabled: Boolean,
        attemptNumber: Int,
        audioGranted: Boolean,
        toyVoiceSpeaking: Boolean
    ): Boolean = enabled && attemptNumber > 1 && audioGranted && !toyVoiceSpeaking

    /** Duracion efectiva de la ventana, acotada al rango seguro de configuracion. */
    fun effectiveWindowMs(configuredMs: Int): Long = configuredMs
        .coerceIn(AppSettings.MIN_EARLY_ANSWER_WINDOW_MS, AppSettings.MAX_EARLY_ANSWER_WINDOW_MS)
        .toLong()
}
