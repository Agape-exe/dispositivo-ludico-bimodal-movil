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

        // La respuesta de referencia puede ser una lista o varios ejemplos validos
        // ("Perro, gato, hamster."): se separa en opciones y basta con que la
        // transcripcion contenga una de ellas como palabra/frase completa. Asi
        // "el perro" o "el gato" se aceptan localmente sin recurrir al juez externo.
        val expectedMatches = splitReferenceOptions(expectedAnswer).any { option ->
            val normalizedOption = normalize(option)
            normalizedOption.isNotBlank() && containsWholeWord(normalized, normalizedOption)
        }
        if (expectedMatches) {
            return SemanticResult.CORRECT
        }

        val hasKeyword = keywords.any { keyword ->
            val normalizedKeyword = normalize(keyword)
            normalizedKeyword.isNotBlank() && containsWholeWord(normalized, normalizedKeyword)
        }

        return if (hasKeyword) SemanticResult.CORRECT else SemanticResult.INCORRECT
    }

    /**
     * Indica si la respuesta de referencia esta redactada como una lista o conjunto
     * de ejemplos validos ("perro, gato, hamster" o "vaca o gallina"), en cuyo caso
     * la pregunta suele admitir cualquiera de ellos como respuesta. Sirve para
     * decidir si conviene consultar al juez de respuestas abiertas cuando la capa
     * local marca incorrecto.
     */
    fun referenceLooksLikeList(expectedAnswer: String): Boolean =
        splitReferenceOptions(expectedAnswer).count { it.isNotBlank() } > 1

    /**
     * Separa una respuesta de referencia en sus opciones validas. Reconoce comas,
     * punto y coma, barras, saltos de linea y los conectores " y " / " o " del
     * habla natural. Una referencia simple ("gato") devuelve una sola opcion, por lo
     * que el comportamiento para respuestas no enumeradas no cambia.
     */
    fun splitReferenceOptions(expectedAnswer: String): List<String> {
        if (expectedAnswer.isBlank()) return emptyList()
        return expectedAnswer
            .split(Regex("(?i)[,;/\\n]| y | o | u | e "))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(expectedAnswer.trim()) }
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
