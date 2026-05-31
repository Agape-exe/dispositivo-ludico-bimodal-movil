package com.taller.app.bimodal

/**
 * Resumen tecnico en memoria de una sesion del modo bimodal.
 *
 * Acumula, por categoria, el desenlace final de cada pregunta resuelta, ademas
 * del total de intentos consumidos. Es un valor inmutable que el orquestador
 * reemplaza al cerrar cada pregunta.
 *
 * Por privacidad solo guarda conteos tecnicos: no contiene transcripciones,
 * audios, imagenes ni datos del nino.
 */
data class BimodalSessionSummary(
    /** Preguntas resueltas con respuesta correcta. */
    val correct: Int = 0,
    /** Preguntas finalizadas con la ultima respuesta incorrecta. */
    val incorrect: Int = 0,
    /** Preguntas finalizadas sin poder interpretar la respuesta. */
    val notInterpretable: Int = 0,
    /** Preguntas finalizadas sin respuesta del nino. */
    val noResponse: Int = 0,
    /** Preguntas finalizadas por agotarse el tiempo maximo. */
    val timeExpired: Int = 0,
    /** Preguntas finalizadas tras un error tecnico recuperable. */
    val technicalErrors: Int = 0,
    /** Total de intentos consumidos sumando todas las preguntas resueltas. */
    val totalAttempts: Int = 0
) {
    /** Total de preguntas resueltas (cualquier categoria). */
    val resolvedQuestions: Int
        get() = correct + incorrect + notInterpretable + noResponse + timeExpired + technicalErrors

    /** Devuelve una copia con la categoria indicada incrementada y los intentos sumados. */
    internal fun recording(
        category: BimodalOutcomeCategory,
        attemptsUsed: Int
    ): BimodalSessionSummary {
        val withAttempts = copy(totalAttempts = totalAttempts + attemptsUsed)
        return when (category) {
            BimodalOutcomeCategory.CORRECT -> withAttempts.copy(correct = correct + 1)
            BimodalOutcomeCategory.INCORRECT -> withAttempts.copy(incorrect = incorrect + 1)
            BimodalOutcomeCategory.NOT_INTERPRETABLE ->
                withAttempts.copy(notInterpretable = notInterpretable + 1)
            BimodalOutcomeCategory.NO_RESPONSE -> withAttempts.copy(noResponse = noResponse + 1)
            BimodalOutcomeCategory.TIME_EXPIRED -> withAttempts.copy(timeExpired = timeExpired + 1)
            BimodalOutcomeCategory.TECHNICAL_ERROR ->
                withAttempts.copy(technicalErrors = technicalErrors + 1)
        }
    }
}

/** Categoria del desenlace final de una pregunta, usada para el resumen de sesion. */
internal enum class BimodalOutcomeCategory {
    CORRECT,
    INCORRECT,
    NOT_INTERPRETABLE,
    NO_RESPONSE,
    TIME_EXPIRED,
    TECHNICAL_ERROR
}
