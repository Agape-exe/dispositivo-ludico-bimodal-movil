package com.taller.app.bimodal

/**
 * Accion automatica que corresponde al desenlace actual de una pregunta, segun
 * las reglas del flujo bimodal. La calcula [BimodalFlowOrchestrator.resolveAutoAction]
 * como logica pura; la capa de UI decide cuando aplicarla (tras una pausa breve de
 * retroalimentacion) y la traduce en la llamada correspondiente del orquestador.
 */
enum class BimodalAutoAction {
    /** El estado no admite progresion automatica (no hay nada que hacer). */
    NONE,

    /** Quedan intentos y procede reintentar la misma pregunta. */
    RETRY,

    /** Avanzar automaticamente a la siguiente pregunta. */
    ADVANCE,

    /** Era la ultima pregunta: finalizar la sesion automaticamente. */
    COMPLETE
}
