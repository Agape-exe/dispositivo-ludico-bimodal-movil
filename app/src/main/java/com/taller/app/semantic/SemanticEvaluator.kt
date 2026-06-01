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
        if (normalizedExpected.isNotBlank() && containsWholeWord(normalized, normalizedExpected)) {
            return SemanticResult.CORRECT
        }

        val hasKeyword = keywords.any { keyword ->
            val normalizedKeyword = normalize(keyword)
            normalizedKeyword.isNotBlank() && containsWholeWord(normalized, normalizedKeyword)
        }

        return if (hasKeyword) SemanticResult.CORRECT else SemanticResult.INCORRECT
    }

    /**
     * Indica si [haystack] contiene [needle] como palabra o frase completa, no como
     * subcadena. Evita falsos positivos del tipo "azulejo" para la respuesta "azul"
     * o "ninguno" para la palabra clave "uno". El acolchado con espacios obliga a
     * que la coincidencia respete los limites de palabra; ambos textos ya vienen
     * normalizados (minusculas, sin acentos y con espacios simples).
     *
     * Un [needle] en blanco nunca coincide: una respuesta esperada o palabra clave
     * vacia no debe convertir cualquier transcripcion en correcta.
     */
    private fun containsWholeWord(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return " $haystack ".contains(" $needle ")
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val lowercase = withoutAccents.lowercase()
        val onlyAlphanumericAndSpace = lowercase.replace(Regex("[^a-z0-9\\s]"), "")
        return onlyAlphanumericAndSpace.trim().replace(Regex("\\s+"), " ")
    }
}
