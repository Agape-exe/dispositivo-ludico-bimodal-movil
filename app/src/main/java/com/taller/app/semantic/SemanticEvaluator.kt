package com.taller.app.semantic

import java.text.Normalizer

enum class SemanticResult {
    CORRECT,
    INCORRECT,
    NOT_INTERPRETABLE,
    NO_RESPONSE
}

/**
 * MED02: por que la capa local acepto o rechazo una respuesta. Sirve para que el
 * reporte tecnico explique la decision sin exponer datos sensibles.
 */
enum class LocalAcceptanceType {
    /** Coincide textualmente con la respuesta de referencia o una de sus opciones. */
    REFERENCE,
    /** Coincide con una palabra clave de la pregunta. */
    KEYWORD,
    /** Equivalencia infantil segura: plural/singular, diminutivo o sinonimo comun. */
    EQUIVALENCE,
    /** Variante de una onomatopeya esperada (posible error de reconocimiento de voz). */
    ONOMATOPOEIA,
    /** No se acepto localmente (incorrecta, sin voz o no interpretable). */
    NONE
}

/**
 * Resultado detallado de la evaluacion local, con metadatos para el reporte tecnico.
 *
 * @property aliasApplied se acepto por equivalencia de sonido/onomatopeya (no por
 *   coincidencia directa con la referencia o palabras clave).
 * @property aliasReason motivo corto y seguro (por ejemplo "alias de sonido: wow -> guau").
 * @property normalizedAnswer transcripcion normalizada local, util para depuracion.
 * @property acceptanceType tipo de aceptacion/rechazo de la capa local (MED02).
 */
data class SemanticEvaluation(
    val result: SemanticResult,
    val aliasApplied: Boolean = false,
    val aliasReason: String? = null,
    val normalizedAnswer: String? = null,
    val acceptanceType: LocalAcceptanceType = LocalAcceptanceType.NONE
)

class SemanticEvaluator {

    private val nonInformativeExpressions = setOf(
        "no se", "nose", "mmm", "eh", "ah", "umm", "hmm", "uh"
    )

    /** Compatibilidad: evaluacion sin texto de pregunta (sin contexto de sonido). */
    fun evaluate(
        transcription: String,
        expectedAnswer: String,
        keywords: List<String>
    ): SemanticResult = evaluate(transcription, expectedAnswer, keywords, questionText = "")

    /** Evaluacion con texto de pregunta, para habilitar la tolerancia de sonidos. */
    fun evaluate(
        transcription: String,
        expectedAnswer: String,
        keywords: List<String>,
        questionText: String
    ): SemanticResult =
        evaluateDetailed(transcription, expectedAnswer, keywords, questionText).result

    /**
     * Igual que [evaluate] pero devuelve los metadatos de la decision (incluido si se
     * aplico un alias de sonido). Antes de declarar INCORRECT en una respuesta de
     * sonido, intenta reconocer la onomatopeya equivalente segun el contexto.
     */
    fun evaluateDetailed(
        transcription: String,
        expectedAnswer: String,
        keywords: List<String>,
        questionText: String = ""
    ): SemanticEvaluation {
        if (transcription.isBlank()) {
            return SemanticEvaluation(SemanticResult.NO_RESPONSE)
        }

        val normalized = normalize(transcription)

        if (normalized.isEmpty() || normalized.none { it.isLetter() }) {
            return SemanticEvaluation(SemanticResult.NOT_INTERPRETABLE)
        }

        if (normalized.length == 1) {
            return SemanticEvaluation(SemanticResult.NOT_INTERPRETABLE)
        }

        if (normalized in nonInformativeExpressions) {
            return SemanticEvaluation(SemanticResult.NOT_INTERPRETABLE)
        }

        // La respuesta de referencia puede ser una lista o varios ejemplos validos
        // ("Perro, gato, hamster."): se separa en opciones y basta con que la
        // transcripcion contenga una de ellas como palabra/frase completa. Asi
        // "el perro" o "el gato" se aceptan localmente sin recurrir al juez externo.
        val referenceOptions = splitReferenceOptions(expectedAnswer)
        val expectedMatches = referenceOptions.any { option ->
            val normalizedOption = normalize(option)
            normalizedOption.isNotBlank() && containsWholeWord(normalized, normalizedOption)
        }
        if (expectedMatches) {
            return SemanticEvaluation(
                SemanticResult.CORRECT,
                normalizedAnswer = normalized,
                acceptanceType = LocalAcceptanceType.REFERENCE
            )
        }

        val hasKeyword = keywords.any { keyword ->
            val normalizedKeyword = normalize(keyword)
            normalizedKeyword.isNotBlank() && containsWholeWord(normalized, normalizedKeyword)
        }
        if (hasKeyword) {
            return SemanticEvaluation(
                SemanticResult.CORRECT,
                normalizedAnswer = normalized,
                acceptanceType = LocalAcceptanceType.KEYWORD
            )
        }

        // MED02: equivalencias infantiles seguras (plural/singular, diminutivos y
        // sinonimos comunes) cuando no hubo coincidencia textual. La referencia y las
        // palabras clave siguen mandando: solo se acepta lo equivalente a ellas.
        val equivalentOptions = (referenceOptions + keywords).filter { it.isNotBlank() }
        if (ChildAnswerEquivalences.answerMatchesAnyOption(transcription, equivalentOptions)) {
            return SemanticEvaluation(
                SemanticResult.CORRECT,
                normalizedAnswer = normalized,
                acceptanceType = LocalAcceptanceType.EQUIVALENCE,
                aliasReason = "equivalencia infantil"
            )
        }

        // MED01-FIX01: tolerancia de sonidos. Solo acepta variantes de la onomatopeya
        // esperada cuando el contexto (referencia o pregunta) lo justifica.
        val onomatopoeia = OnomatopoeiaNormalizer.match(transcription, expectedAnswer, questionText)
        if (onomatopoeia.matchedAlias) {
            return SemanticEvaluation(
                result = SemanticResult.CORRECT,
                aliasApplied = true,
                aliasReason = onomatopoeia.aliasReason,
                normalizedAnswer = normalized,
                acceptanceType = LocalAcceptanceType.ONOMATOPOEIA
            )
        }

        return SemanticEvaluation(
            SemanticResult.INCORRECT,
            normalizedAnswer = normalized,
            acceptanceType = LocalAcceptanceType.NONE
        )
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
