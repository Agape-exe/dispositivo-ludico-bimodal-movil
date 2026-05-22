package com.taller.app.semantic

import java.text.Normalizer

enum class SemanticResult {
    CORRECT,
    INCORRECT,
    NOT_INTERPRETABLE,
    NO_RESPONSE
}

class SemanticEvaluator {

    private val nonInformativeExpressions = setOf(
        "no se", "nose", "mmm", "eh", "ah", "umm", "hmm", "uh"
    )

    fun evaluate(
        transcription: String,
        expectedAnswer: String,
        keywords: List<String>
    ): SemanticResult {
        if (transcription.isBlank()) {
            return SemanticResult.NO_RESPONSE
        }

        val normalized = normalize(transcription)

        if (normalized.isEmpty() || normalized.none { it.isLetter() }) {
            return SemanticResult.NOT_INTERPRETABLE
        }

        if (normalized.length == 1) {
            return SemanticResult.NOT_INTERPRETABLE
        }

        if (normalized in nonInformativeExpressions) {
            return SemanticResult.NOT_INTERPRETABLE
        }

        val normalizedExpected = normalize(expectedAnswer)
        if (normalized.contains(normalizedExpected)) {
            return SemanticResult.CORRECT
        }

        val hasKeyword = keywords.any { keyword ->
            normalized.contains(normalize(keyword))
        }

        return if (hasKeyword) SemanticResult.CORRECT else SemanticResult.INCORRECT
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val lowercase = withoutAccents.lowercase()
        val onlyAlphanumericAndSpace = lowercase.replace(Regex("[^a-z0-9\\s]"), "")
        return onlyAlphanumericAndSpace.trim().replace(Regex("\\s+"), " ")
    }
}
