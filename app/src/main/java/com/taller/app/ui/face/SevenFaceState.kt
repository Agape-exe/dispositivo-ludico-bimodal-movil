package com.taller.app.ui.face

/**
 * Estados visuales de la cara final de Seven (UI02-FINAL).
 *
 * Es un modelo puramente visual: no conoce Room, STT, GPT ni la camara. Cada
 * pantalla traduce sus estados de flujo a uno de estos valores mediante
 * [classicSevenFaceState] o el mapeo del modo inteligente en SevenFaceMapper.
 *
 * Regla de neutralidad: el modo temporizador solo puede usar estados neutrales
 * (IDLE, INTRO, SPEAKING, LISTENING, WAITING, TIMEOUT_NEUTRAL, CLOSING,
 * ERROR_SOFT). HAPPY, SUPPORTIVE, RETRY y THINKING revelan evaluacion y estan
 * reservados al modo inteligente.
 */
enum class SevenFaceState {
    /** Cara tranquila con parpadeo suave, sin actividad especial. */
    IDLE,

    /** Presentacion o apertura de la sesion (antes de hablar). */
    INTRO,

    /** Seven esta hablando: la boca se mueve suavemente. */
    SPEAKING,

    /** Seven espera la respuesta del nino: ojos atentos, boca cerrada. */
    LISTENING,

    /** Seven evalua la respuesta: mirada hacia arriba/lado, espera suave. */
    THINKING,

    /** Respuesta correcta (solo modo inteligente): ojos felices y sonrisa. */
    HAPPY,

    /** Acompanamiento tierno tras una respuesta insuficiente (solo inteligente). */
    SUPPORTIVE,

    /** Invitacion suave a intentar de nuevo (solo inteligente), sin regano. */
    RETRY,

    /** Espera calmada (pausa, transicion o carga), sin juicio. */
    WAITING,

    /** Tiempo agotado sin juicio: cara neutra/calmada, nunca de error. */
    TIMEOUT_NEUTRAL,

    /** Cierre de la sesion: despedida feliz y calmada. */
    CLOSING,

    /** Problema tecnico suave: confusion tierna, sin alarma ni miedo. */
    ERROR_SOFT
}
