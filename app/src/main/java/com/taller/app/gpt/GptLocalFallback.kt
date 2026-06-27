package com.taller.app.gpt

object GptLocalFallback {
    private val phrases = mapOf(
        "TEST_SETTINGS" to "Hola, explorador! Soy Seven. Listo para descubrir cosas nuevas?",
        "GREETING" to "Bienvenido! Soy Seven, tu companero de aventuras.",
        "GENERIC" to "Vamos a aprender juntos, explorador!"
    )

    fun phraseFor(contextTag: String): String =
        phrases[contextTag] ?: "Hola! Soy Seven."

    fun structuredFallback(
        input: SevenInputContract,
        reason: SevenBlockedReason = SevenBlockedReason.UNKNOWN
    ): SevenResponse {
        val intent = runCatching { SevenIntent.parse(input.intent) }.getOrDefault(SevenIntent.FALLBACK)
        val localEvaluation = runCatching {
            SevenLocalEvaluation.parse(input.localEvaluation)
        }.getOrDefault(SevenLocalEvaluation.NOT_APPLICABLE)
        val text = when (intent) {
            SevenIntent.GREETING -> "Hola, explorador! Soy Seven. Listo para descubrir cosas nuevas?"
            SevenIntent.MISSION_START -> "Mision lista! Vamos a explorar juntos."
            SevenIntent.PRESENT_QUESTION -> "Mision lista! Escucha con atencion la siguiente pregunta."
            SevenIntent.FEEDBACK_CORRECT -> "Muy bien, explorador! Lo lograste."
            SevenIntent.FEEDBACK_INCORRECT -> safeHintOrDefault(input)
            SevenIntent.NOT_INTERPRETABLE -> "Mis antenas no entendieron bien. Puedes repetirlo?"
            SevenIntent.RETRY -> "Animo, explorador! Probemos otra vez."
            SevenIntent.RECAPTURE_ATTENTION -> "Ey, explorador! La mision sigue esperando por ti."
            SevenIntent.CLOSING -> "Mision cumplida! Gracias por explorar conmigo."
            SevenIntent.FALLBACK -> "Sigamos explorando juntos!"
        }.limitWords(input.maxWords)

        return SevenResponse(
            intent = intent,
            responseType = fallbackResponseType(intent, localEvaluation, input),
            visibleText = text,
            safetyLevel = if (reason == SevenBlockedReason.NONE) SevenSafetyLevel.SAFE else SevenSafetyLevel.CORRECTED,
            fallbackUsed = true,
            canGiveHint = input.canGiveHint,
            canGiveFinalAnswer = input.canGiveFinalAnswer,
            shouldAskRepeat = localEvaluation == SevenLocalEvaluation.NOT_INTERPRETABLE,
            shouldRecaptureAttention = intent == SevenIntent.RECAPTURE_ATTENTION,
            topic = input.topic,
            localEvaluation = localEvaluation,
            attemptsRemaining = input.attemptsRemaining,
            maxWords = input.maxWords,
            blockedReason = reason,
            safeForTts = true,
            validationNotes = "local_fallback"
        )
    }

    private fun safeHintOrDefault(input: SevenInputContract): String {
        if (input.canGiveHint && input.allowedHint.isNotBlank()) {
            val hint = SevenResponseValidator.normalize(input.allowedHint)
            val revealsAnswer = input.answerTokens
                .map { SevenResponseValidator.normalize(it) }
                .filter { it.isNotBlank() }
                .any { hint.contains(it) }
            if (!revealsAnswer) return input.allowedHint
        }
        return "Casi, explorador! Intentemos una vez mas."
    }

    private fun fallbackResponseType(
        intent: SevenIntent,
        localEvaluation: SevenLocalEvaluation,
        input: SevenInputContract
    ): SevenResponseType = when {
        intent == SevenIntent.GREETING -> SevenResponseType.GREET
        intent == SevenIntent.PRESENT_QUESTION -> SevenResponseType.PRESENT
        intent == SevenIntent.FEEDBACK_CORRECT -> SevenResponseType.PRAISE
        intent == SevenIntent.FEEDBACK_INCORRECT && input.canGiveHint && input.allowedHint.isNotBlank() -> SevenResponseType.HINT
        intent == SevenIntent.FEEDBACK_INCORRECT -> SevenResponseType.ENCOURAGE
        intent == SevenIntent.NOT_INTERPRETABLE || localEvaluation == SevenLocalEvaluation.NOT_INTERPRETABLE -> SevenResponseType.ASK_REPEAT
        intent == SevenIntent.RECAPTURE_ATTENTION -> SevenResponseType.RECAPTURE
        intent == SevenIntent.CLOSING -> SevenResponseType.CLOSE
        else -> SevenResponseType.FALLBACK
    }

    private fun String.limitWords(maxWords: Int): String {
        if (maxWords <= 0) return this
        val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (words.size <= maxWords) this else words.take(maxWords).joinToString(" ")
    }
}
