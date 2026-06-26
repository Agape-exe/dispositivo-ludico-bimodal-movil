package com.taller.app.voice

import java.text.Normalizer

enum class InvalidToyVoiceTextReason {
    EMPTY_TEXT,
    NO_USEFUL_CONTENT,
    PLACEHOLDER_TEXT,
    TECHNICAL_TEXT
}

data class ToyVoiceTextValidation(
    val isValid: Boolean,
    val normalizedText: String,
    val reason: InvalidToyVoiceTextReason? = null
)

object ToyVoiceTextValidator {

    fun validate(text: String?): ToyVoiceTextValidation {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ToyVoiceTextValidation(
                isValid = false,
                normalizedText = trimmed,
                reason = InvalidToyVoiceTextReason.EMPTY_TEXT
            )
        }
        if (!trimmed.any { it.isLetterOrDigit() }) {
            return ToyVoiceTextValidation(
                isValid = false,
                normalizedText = trimmed,
                reason = InvalidToyVoiceTextReason.NO_USEFUL_CONTENT
            )
        }

        val normalized = normalizeForMatching(trimmed)
        if (blockedPlaceholders.any { normalized == it }) {
            return ToyVoiceTextValidation(
                isValid = false,
                normalizedText = trimmed,
                reason = InvalidToyVoiceTextReason.PLACEHOLDER_TEXT
            )
        }
        if (blockedTechnicalFragments.any { normalized.contains(it) }) {
            return ToyVoiceTextValidation(
                isValid = false,
                normalizedText = trimmed,
                reason = InvalidToyVoiceTextReason.TECHNICAL_TEXT
            )
        }

        return ToyVoiceTextValidation(isValid = true, normalizedText = trimmed)
    }

    private fun normalizeForMatching(text: String): String {
        val withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return withoutMarks
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")
    }

    private val blockedPlaceholders = setOf(
        "dime que quieres que diga",
        "no tengo nada que decir",
        "no hay texto",
        "no hay texto para reproducir",
        "no se proporciono texto",
        "texto no proporcionado",
        "sin texto",
        "texto vacio",
        "input vacio",
        "empty text",
        "no text",
        "no text provided",
        "no input provided"
    )

    private val blockedTechnicalFragments = setOf(
        "no se proporciono texto",
        "no hay texto para reproducir",
        "texto de voz vacio",
        "texto de voz invalido",
        "invalid tts text",
        "invalid voice text",
        "tts text is empty",
        "tts input is empty",
        "speech input is empty",
        "no hay input",
        "entrada vacia"
    )
}
