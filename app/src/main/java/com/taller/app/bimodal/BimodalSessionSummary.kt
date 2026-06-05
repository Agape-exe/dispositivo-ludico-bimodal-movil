package com.taller.app.bimodal

/**
 * Resumen tecnico en memoria de una sesion del modo bimodal.
 *
 * Acumula los conteos por categoria a nivel de INTENTO: cada intento real con un
 * desenlace (correcto, incorrecto, no interpretable, sin respuesta, tiempo
 * agotado, error de reconocimiento o error tecnico) suma uno en su categoria, sin
 * importar si la pregunta aun admite reintento. Los intentos maximos solo deciden
 * si la pregunta se reintenta o finaliza, nunca si un desenlace se contabiliza.
 *
 * Por ejemplo, una pregunta con dos intentos ambos incorrectos suma 2 en
 * [incorrect]; un intento incorrecto seguido de uno correcto suma 1 en cada
 * categoria. Ademas se lleva el numero de preguntas distintas finalizadas
 * ([resolvedQuestions]).
 *
 * Por privacidad solo guarda conteos tecnicos: no contiene transcripciones,
 * audios, imagenes ni datos del nino.
 */
data class BimodalSessionSummary(
    /** Intentos con respuesta correcta. */
    val correct: Int = 0,
    /** Intentos con respuesta incorrecta. */
    val incorrect: Int = 0,
    /** Intentos sin poder interpretar la respuesta. */
    val notInterpretable: Int = 0,
    /** Intentos sin respuesta del nino. */
    val noResponse: Int = 0,
    /** Intentos terminados por agotarse el tiempo maximo. */
    val timeExpired: Int = 0,
    /** Intentos con error de reconocimiento de voz (STT). */
    val sttErrors: Int = 0,
    /** Intentos con un error tecnico recuperable distinto del STT. */
    val technicalErrors: Int = 0,
    /** Preguntas distintas finalizadas (con cualquier desenlace). */
    val resolvedQuestions: Int = 0
) {
    /**
     * Total de intentos contabilizados, sumando todas las categorias. Equivale al
     * numero de desenlaces de intento registrados en la sesion.
     */
    val totalAttempts: Int
        get() = correct + incorrect + notInterpretable + noResponse +
            timeExpired + sttErrors + technicalErrors

    /** Devuelve una copia con la categoria del intento indicado incrementada en uno. */
    internal fun recordingAttempt(category: BimodalOutcomeCategory): BimodalSessionSummary =
        when (category) {
            BimodalOutcomeCategory.CORRECT -> copy(correct = correct + 1)
            BimodalOutcomeCategory.INCORRECT -> copy(incorrect = incorrect + 1)
            BimodalOutcomeCategory.NOT_INTERPRETABLE -> copy(notInterpretable = notInterpretable + 1)
            BimodalOutcomeCategory.NO_RESPONSE -> copy(noResponse = noResponse + 1)
            BimodalOutcomeCategory.TIME_EXPIRED -> copy(timeExpired = timeExpired + 1)
            BimodalOutcomeCategory.STT_ERROR -> copy(sttErrors = sttErrors + 1)
            BimodalOutcomeCategory.TECHNICAL_ERROR -> copy(technicalErrors = technicalErrors + 1)
        }

    /** Devuelve una copia con una pregunta finalizada mas. */
    internal fun recordingResolvedQuestion(): BimodalSessionSummary =
        copy(resolvedQuestions = resolvedQuestions + 1)
}

/** Categoria del desenlace de un intento, usada para el resumen de sesion. */
internal enum class BimodalOutcomeCategory {
    CORRECT,
    INCORRECT,
    NOT_INTERPRETABLE,
    NO_RESPONSE,
    TIME_EXPIRED,
    STT_ERROR,
    TECHNICAL_ERROR
}
