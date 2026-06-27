package com.taller.app.gpt

import java.text.Normalizer

data class ValidationResult(
    val isValid: Boolean,
    val effectiveSafeForTts: Boolean,
    val failedRules: List<String>,
    val blockedReason: SevenBlockedReason,
    val safeVisibleText: String?
)

object SevenResponseValidator {
    private val systemTerms = listOf(
        "inteligencia artificial",
        "modelo",
        "sistema",
        "prompt",
        "openai",
        "gemini",
        "asistente",
        "android",
        "codigo",
        "ia",
        "robot",
        "chatbot",
        "api"
    )
    private val surveillanceTerms = listOf(
        "camara",
        "microfono",
        "te veo",
        "deteccion",
        "grabacion",
        "vigilancia",
        "facial",
        "cara",
        "rostro",
        "te miro",
        "estoy mirando"
    )
    private val personalDataTerms = listOf(
        "como te llamas",
        "tu nombre",
        "dime tu nombre",
        "cuantos anos",
        "donde vives",
        "tu telefono",
        "tu escuela",
        "tu colegio",
        "tu direccion",
        "tu correo"
    )

    fun validate(response: SevenResponse, input: SevenInputContract): ValidationResult {
        val failed = mutableListOf<String>()
        var reason = SevenBlockedReason.NONE
        val text = response.visibleText
        val normalizedText = normalize(text)

        fun fail(rule: String, blockedReason: SevenBlockedReason) {
            failed += rule
            if (reason == SevenBlockedReason.NONE) {
                reason = blockedReason
            }
        }

        if (text.isBlank()) fail("V08_VISIBLE_TEXT_NOT_EMPTY", SevenBlockedReason.INVALID_CONTEXT)

        val responseMax = response.maxWords.coerceAtMost(input.maxWords)
        if (wordCount(text) > responseMax) fail("V09_MAX_WORDS", SevenBlockedReason.TOO_LONG)

        if (containsAny(normalizedText, systemTerms)) {
            fail("V10_NO_AI_OR_SYSTEM_TERMS", SevenBlockedReason.MENTIONS_INTERNAL_SYSTEM)
        }
        if (containsAny(normalizedText, surveillanceTerms)) {
            fail("V11_NO_SURVEILLANCE_TERMS", SevenBlockedReason.PRIVACY_RISK)
        }
        if (containsAny(normalizedText, personalDataTerms)) {
            fail("V12_NO_PERSONAL_DATA_REQUEST", SevenBlockedReason.PRIVACY_RISK)
        }

        val inputEvaluation = SevenLocalEvaluation.parse(input.localEvaluation)
        if (!input.canGiveFinalAnswer && inputEvaluation == SevenLocalEvaluation.INCORRECT) {
            val answerTokens = input.answerTokens.map(::normalize).filter { it.isNotBlank() }
            if (answerTokens.any { normalizedText.contains(it) }) {
                fail("V13_NO_FINAL_ANSWER", SevenBlockedReason.GIVES_ANSWER_NOT_ALLOWED)
            } else if (input.answerTokens.isEmpty() && input.allowedHint.isNotBlank()) {
                val normalizedHint = normalize(input.allowedHint)
                if (normalizedHint.isNotBlank() && normalizedText.contains(normalizedHint)) {
                    fail("V13_NO_DIRECT_ALLOWED_HINT", SevenBlockedReason.GIVES_ANSWER_NOT_ALLOWED)
                }
            }
        }

        if (inputEvaluation == SevenLocalEvaluation.INCORRECT && input.attemptsRemaining > 0) {
            if (response.responseType != SevenResponseType.HINT && response.responseType != SevenResponseType.ENCOURAGE) {
                fail("V14_INCORRECT_REQUIRES_HINT_OR_ENCOURAGE", SevenBlockedReason.INVALID_CONTEXT)
            }
        }

        if (inputEvaluation == SevenLocalEvaluation.NOT_INTERPRETABLE) {
            if (!response.shouldAskRepeat) {
                fail("V15_NOT_INTERPRETABLE_ASK_REPEAT", SevenBlockedReason.INVALID_CONTEXT)
            }
            if (response.responseType != SevenResponseType.ASK_REPEAT && response.responseType != SevenResponseType.ENCOURAGE) {
                fail("V15_NOT_INTERPRETABLE_RESPONSE_TYPE", SevenBlockedReason.INVALID_CONTEXT)
            }
        }

        if (response.intent == SevenIntent.RECAPTURE_ATTENTION) {
            if (!response.shouldRecaptureAttention) {
                fail("V16_RECAPTURE_FLAG", SevenBlockedReason.INVALID_CONTEXT)
            }
            if (response.responseType != SevenResponseType.RECAPTURE) {
                fail("V16_RECAPTURE_RESPONSE_TYPE", SevenBlockedReason.INVALID_CONTEXT)
            }
        }

        if (response.safetyLevel == SevenSafetyLevel.BLOCKED) {
            fail("SAFETY_LEVEL_BLOCKED", response.blockedReason.takeUnless { it == SevenBlockedReason.NONE }
                ?: SevenBlockedReason.UNKNOWN)
        }

        val valid = failed.isEmpty()
        return ValidationResult(
            isValid = valid,
            effectiveSafeForTts = valid,
            failedRules = failed,
            blockedReason = if (valid) SevenBlockedReason.NONE else reason,
            safeVisibleText = if (valid) text else null
        )
    }

    internal fun wordCount(text: String): Int =
        normalize(text).split(Regex("\\s+")).count { it.isNotBlank() }

    internal fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    private fun containsAny(text: String, terms: List<String>): Boolean =
        terms.any { term ->
            val normalizedTerm = normalize(term)
            if (normalizedTerm.length <= 3 && normalizedTerm.all { it.isLetterOrDigit() }) {
                Regex("(^|[^a-z0-9])${Regex.escape(normalizedTerm)}([^a-z0-9]|$)").containsMatchIn(text)
            } else {
                text.contains(normalizedTerm)
            }
        }
}
