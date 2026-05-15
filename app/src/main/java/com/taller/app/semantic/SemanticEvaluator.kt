package com.taller.app.semantic

enum class SemanticResult {
    CORRECT,
    INCORRECT,
    NOT_INTERPRETABLE,
    NO_RESPONSE
}

class SemanticEvaluator {

    fun evaluate(
        transcription: String,
        expectedAnswer: String,
        keywords: List<String>
    ): SemanticResult {
        if (transcription.isBlank()) {
            return SemanticResult.NO_RESPONSE
        }

        val normalizedResponse = transcription.lowercase().trim()
        val normalizedExpected = expectedAnswer.lowercase().trim()

        if (normalizedResponse.contains(normalizedExpected)) {
            return SemanticResult.CORRECT
        }

        val hasKeyword = keywords.any { keyword ->
            normalizedResponse.contains(keyword.lowercase().trim())
        }

        return if (hasKeyword) {
            SemanticResult.CORRECT
        } else {
            SemanticResult.INCORRECT
        }
    }
}