package com.taller.app.gpt.script

/**
 * GEN01: capa de validacion local del guion antes de aceptarlo o guardarlo.
 * No pretende ser perfecta; protege contra contenido vacio, pistas que revelan la
 * respuesta, textos demasiado largos para ninos pequenos y frases no permitidas.
 */
object SessionScriptValidator {

    /** Limite de caracteres para frases habladas dirigidas a ninos de 3 a 5 anos. */
    const val MAX_SPOKEN_CHARS = 220

    private val FORBIDDEN_FRAGMENTS = listOf(
        "soy una ia",
        "como ia",
        "soy una inteligencia artificial",
        "como modelo",
        "modelo de lenguaje",
        "soy un asistente",
        "como asistente",
        "lenguaje de programacion",
        "openai",
        "chatgpt",
        "gpt"
    )

    data class Result(
        val isValid: Boolean,
        val issues: List<String>
    )

    fun validate(script: SessionScript, input: SessionScriptInput): Result {
        val issues = mutableListOf<String>()

        if (script.intro.isBlank()) issues += "Falta la presentación de Seven."
        if (script.closing.isBlank()) issues += "Falta la despedida de Seven."

        checkForbidden("la presentación", script.intro, issues)
        checkForbidden("la despedida", script.closing, issues)
        checkLength("la presentación", script.intro, issues)
        checkLength("la despedida", script.closing, issues)

        val referenceByOrder = input.questions.associateBy({ it.orderIndex }, { it.referenceAnswer })

        script.questions.forEach { q ->
            val label = "la pregunta ${q.orderIndex}"
            if (q.childFriendlyQuestionText.isBlank()) {
                issues += "Falta la pregunta amigable en $label."
            }
            checkLength("la pregunta amigable de $label", q.childFriendlyQuestionText, issues)

            val hints = listOf(q.hintLevel1, q.hintLevel2, q.hintLevel3)
            if (hints.any { it.isBlank() }) {
                issues += "Hay pistas vacías en $label."
            }
            if (q.positiveFeedbackText.isBlank()) issues += "Falta el feedback positivo en $label."
            if (q.supportiveFeedbackText.isBlank()) issues += "Falta el feedback de apoyo en $label."

            val reference = referenceByOrder[q.orderIndex].orEmpty()
            if (reference.isNotBlank()) {
                hints.forEachIndexed { index, hint ->
                    if (hint.isNotBlank() && revealsAnswer(hint, reference)) {
                        issues += "La pista ${index + 1} de $label revela la respuesta."
                    }
                }
            }

            listOf(
                "la pregunta amigable de $label" to q.childFriendlyQuestionText,
                "la pista 1 de $label" to q.hintLevel1,
                "la pista 2 de $label" to q.hintLevel2,
                "la pista 3 de $label" to q.hintLevel3,
                "el feedback positivo de $label" to q.positiveFeedbackText,
                "el feedback de apoyo de $label" to q.supportiveFeedbackText,
                "el reintento de $label" to q.retryPromptText
            ).forEach { (fieldLabel, text) ->
                checkForbidden(fieldLabel, text, issues)
                checkLength(fieldLabel, text, issues)
            }
        }

        return Result(isValid = issues.isEmpty(), issues = issues)
    }

    fun normalize(text: String): String = text.trim().lowercase()

    /** True si el texto contiene una frase no permitida (mención a IA, tecnología, etc.). */
    fun hasForbidden(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = normalize(text)
        return FORBIDDEN_FRAGMENTS.any { normalized.contains(it) }
    }

    /** True si el texto excede el largo recomendado para niños pequeños. */
    fun isTooLong(text: String): Boolean = text.length > MAX_SPOKEN_CHARS

    /** Una pista revela la respuesta si la contiene como palabra completa. */
    fun revealsAnswer(hint: String, reference: String): Boolean {
        val normalizedHint = " ${normalize(hint)} "
        return reference.split(",", ";", "/", " o ")
            .map { normalize(it) }
            .filter { it.length >= 3 }
            .any { token -> normalizedHint.contains(" $token ") }
    }

    private fun checkForbidden(label: String, text: String, issues: MutableList<String>) {
        if (text.isBlank()) return
        val normalized = normalize(text)
        if (FORBIDDEN_FRAGMENTS.any { normalized.contains(it) }) {
            issues += "Hay una frase no permitida en $label."
        }
    }

    private fun checkLength(label: String, text: String, issues: MutableList<String>) {
        if (text.length > MAX_SPOKEN_CHARS) {
            issues += "El texto de $label es demasiado largo para niños pequeños."
        }
    }
}
