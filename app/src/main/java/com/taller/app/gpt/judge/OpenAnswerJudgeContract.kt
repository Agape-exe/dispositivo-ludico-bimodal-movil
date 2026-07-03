package com.taller.app.gpt.judge

/**
 * MED01: contratos del juez de respuestas abiertas.
 *
 * El juez es una capa textual silenciosa que decide si la respuesta del nino
 * responde la pregunta, incluso cuando no coincide literalmente con la respuesta
 * de referencia de la docente (que es una guia, no una verdad cerrada). Nunca
 * conversa con el nino ni genera voz: solo emite un veredicto estructurado que el
 * orquestador usa para decidir aceptar, reintentar o cerrar.
 */

/** Decision del juez sobre la respuesta del nino. */
enum class JudgeDecision {
    CORRECT,
    INCORRECT,
    UNCERTAIN;

    companion object {
        fun parse(raw: String?): JudgeDecision? = when (raw?.trim()?.uppercase()) {
            "CORRECT" -> CORRECT
            "INCORRECT" -> INCORRECT
            "UNCERTAIN" -> UNCERTAIN
            else -> null
        }
    }
}

/**
 * MED02: por que el juez acepto (o no) la respuesta. Orientativo para el reporte,
 * nunca decide por si solo. Se sanea localmente al parsear.
 */
enum class JudgeAcceptanceType {
    /** Coincide literalmente con el concepto esperado. */
    LITERAL,
    /** Respuesta equivalente o sinonimo valido del concepto. */
    EQUIVALENT,
    /** Probable error del reconocimiento de voz infantil (alias del sonido/palabra). */
    STT_ALIAS,
    /** Ejemplo valido de una categoria abierta ("menciona un animal"). */
    OPEN_CATEGORY,
    /** No aplica (respuesta rechazada o sin tipo declarado). */
    NONE;

    companion object {
        fun parse(raw: String?): JudgeAcceptanceType = when (raw?.trim()?.uppercase()) {
            "LITERAL" -> LITERAL
            "EQUIVALENT", "EQUIVALENTE", "EQUIVALENCIA" -> EQUIVALENT
            "STT_ALIAS", "ALIAS", "ALIAS_STT", "STT" -> STT_ALIAS
            "OPEN_CATEGORY", "CATEGORIA", "CATEGORY", "CATEGORIA_ABIERTA" -> OPEN_CATEGORY
            else -> NONE
        }
    }
}

/** Tipo de retroalimentacion sugerida por el juez (orientativa para el flujo). */
enum class JudgeFeedbackType {
    POSITIVE,
    SUPPORTIVE,
    RETRY,
    NONE;

    companion object {
        fun parse(raw: String?): JudgeFeedbackType = when (raw?.trim()?.uppercase()) {
            "POSITIVE" -> POSITIVE
            "SUPPORTIVE" -> SUPPORTIVE
            "RETRY" -> RETRY
            else -> NONE
        }
    }
}

/**
 * Capa que tomo la decision final de un intento en el modo inteligente.
 * - [LOCAL]: la decidio el evaluador semantico local sin consultar al juez.
 * - [JUDGE]: la decidio el juez textual externo.
 * - [FALLBACK_LOCAL]: el juez fallo (sin clave, sin red, JSON invalido, etc.) y
 *   se uso un respaldo local seguro.
 */
enum class JudgeDecisionLayer {
    LOCAL,
    JUDGE,
    FALLBACK_LOCAL
}

/**
 * Datos que recibe el juez. Solo texto pedagogico estrictamente necesario: nunca
 * audio, imagenes, datos biometricos ni nombres de ninos.
 *
 * @property attemptsRemaining indica si aun quedan intentos; si es true, el juez no
 *   debe revelar la respuesta correcta en su motivo ni pistas.
 */
data class OpenAnswerJudgeInput(
    val questionText: String,
    val childFriendlyQuestionText: String?,
    val referenceAnswer: String,
    val childAnswer: String,
    val ageRange: String,
    val topic: String,
    val classContext: String?,
    val currentAttempt: Int,
    val attemptsRemaining: Boolean,
    val availableHint: String?,
    val localResult: String
)

/**
 * Veredicto estructurado del juez, ya validado localmente.
 *
 * @property decision decision principal.
 * @property confidence confianza en 0..1.
 * @property reason motivo breve y seguro (sin revelar la respuesta si quedan intentos).
 * @property acceptedAsEquivalent true si acepta la respuesta como equivalente valida.
 * @property shouldRetry true si conviene reintentar (cuando hay intentos).
 * @property feedbackType tipo de retroalimentacion sugerida.
 * @property safeHintLevel nivel de pista seguro sugerido (0 = ninguna).
 * @property revealsAnswer true si el motivo/pista revelaria la respuesta; debe ser
 *   false cuando quedan intentos.
 * @property normalizedChildAnswer respuesta del nino normalizada (corta).
 * @property normalizedExpectedConcept concepto esperado normalizado (corto).
 * @property acceptanceType MED02: tipo de aceptacion declarado por el juez (orientativo).
 */
data class OpenAnswerJudgeVerdict(
    val decision: JudgeDecision,
    val confidence: Double,
    val reason: String,
    val acceptedAsEquivalent: Boolean,
    val shouldRetry: Boolean,
    val feedbackType: JudgeFeedbackType,
    val safeHintLevel: Int,
    val revealsAnswer: Boolean,
    val normalizedChildAnswer: String,
    val normalizedExpectedConcept: String,
    val acceptanceType: JudgeAcceptanceType = JudgeAcceptanceType.NONE
)
