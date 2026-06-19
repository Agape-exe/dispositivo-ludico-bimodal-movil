package com.taller.app.bimodal

/**
 * Estados del flujo del modo bimodal inteligente.
 *
 * Algunos estados son de reposo (la maquina permanece en ellos esperando un
 * evento externo) y otros son de transito (se atraviesan momentaneamente al
 * procesar un evento). El orquestador notifica todos los estados por los que
 * pasa a traves de su listener, mientras que [BimodalFlowOrchestrator.state]
 * siempre refleja el estado de reposo actual.
 */
enum class BimodalInteractionState {
    /** Sin actividad cargada. Estado inicial. */
    IDLE,

    /** Se solicito cargar una actividad y se espera la confirmacion de carga. */
    LOADING_ACTIVITY,

    /** Actividad cargada y validada, lista para iniciar la sesion. */
    READY,

    /** Esperando la presencia (rostro) del nino frente al dispositivo. */
    WAITING_FOR_FACE,

    /**
     * Sesion activa pausada porque el rostro se perdio mientras se presentaba
     * la pregunta o se escuchaba la respuesta. No cuenta como error del nino.
     * Al volver a detectar rostro, retoma la pregunta sin repetir la introduccion.
     */
    PAUSED_FACE_LOST,

    /** Presencia detectada (estado de transito hacia la presentacion). */
    FACE_DETECTED,

    /** Presentando la pregunta actual (el juguete la enuncia). */
    PRESENTING_QUESTION,

    /** Pregunta presentada, ventana de respuesta abierta (transito). */
    WAITING_FOR_RESPONSE,

    /** Capturando activamente la voz del nino. */
    LISTENING,

    /** Convirtiendo la voz capturada en texto (transito). */
    TRANSCRIBING,

    /** Evaluando semanticamente la transcripcion. */
    EVALUATING,

    /** Retroalimentacion: respuesta correcta. */
    FEEDBACK_CORRECT,

    /** Retroalimentacion: respuesta incorrecta. */
    FEEDBACK_INCORRECT,

    /** Retroalimentacion: respuesta no interpretable. */
    FEEDBACK_NOT_INTERPRETABLE,

    /** Retroalimentacion: no hubo respuesta. */
    FEEDBACK_NO_RESPONSE,

    /**
     * Retroalimentacion: error tecnico recuperable durante la pregunta (fallo de
     * reconocimiento de voz, de evaluacion semantica o de la voz del juguete).
     *
     * A diferencia de [ERROR] no es terminal: la respuesta del nino no se clasifica
     * como incorrecta y, si quedan intentos, la pregunta puede reintentarse.
     */
    FEEDBACK_TECHNICAL_ERROR,

    /** Se agoto el tiempo maximo para responder. */
    TIME_EXPIRED,

    /** Avanzando a la siguiente pregunta (transito). */
    NEXT_QUESTION,

    /** Todas las preguntas fueron recorridas. Fin normal de la sesion. */
    SESSION_COMPLETED,

    /** La sesion fue cancelada antes de finalizar. */
    SESSION_CANCELLED,

    /** Error tecnico o de configuracion (ver mensaje del orquestador). */
    ERROR
}
