package com.taller.app.bimodal.feedback

/**
 * Categorias de retroalimentacion general tipo profesor para el modo bimodal.
 *
 * Cada categoria agrupa un conjunto de frases breves y seguras adecuadas para
 * ninos. La eleccion entre la variante "_RETRY" y "_NEXT" depende de si todavia
 * quedan intentos en la pregunta actual: nunca se revela la respuesta esperada
 * mientras se puede reintentar.
 *
 * No hay categoria de "parcialmente correcto": el flujo no la modela, por lo que
 * no existen frases del tipo "vas bien" o "estas cerca" que enganarian al nino.
 */
enum class GeneralTeacherFeedbackType {
    /** Respuesta correcta. */
    CORRECT,

    /** Respuesta incorrecta con intentos disponibles. */
    INCORRECT_RETRY,

    /** Respuesta incorrecta sin intentos restantes. */
    INCORRECT_NEXT,

    /** Respuesta no interpretable con intentos disponibles. */
    NOT_INTERPRETABLE_RETRY,

    /** Respuesta no interpretable sin intentos restantes. */
    NOT_INTERPRETABLE_NEXT,

    /** Sin respuesta con intentos disponibles. */
    NO_RESPONSE_RETRY,

    /** Sin respuesta sin intentos restantes. */
    NO_RESPONSE_NEXT,

    /** Tiempo agotado con intentos disponibles. */
    TIME_EXPIRED_RETRY,

    /** Tiempo agotado sin intentos restantes. */
    TIME_EXPIRED_NEXT,

    /** Error tecnico recuperable con intentos disponibles. */
    TECHNICAL_ERROR_RETRY,

    /** Error tecnico recuperable sin intentos restantes. */
    TECHNICAL_ERROR_NEXT,

    /** Inicio de la sesion. */
    SESSION_START,

    /** Presentacion de una pregunta (antes de enunciar su texto). */
    QUESTION_INTRO,

    /** Sesion completada. */
    SESSION_COMPLETED
}
