package com.taller.app.bimodal

import com.taller.app.gpt.judge.JudgeDecision
import com.taller.app.gpt.judge.JudgeDecisionLayer
import com.taller.app.gpt.judge.OpenAnswerJudge
import com.taller.app.gpt.judge.OpenAnswerJudgeInput
import com.taller.app.model.LearningQuestion
import com.taller.app.semantic.SemanticEvaluator
import com.taller.app.semantic.SemanticResult
import java.text.Normalizer

/**
 * MED01: contexto textual de la sesion que el juez de respuestas abiertas necesita.
 * Solo texto pedagogico general: nunca audio, imagenes ni nombres de ninos.
 */
data class HybridSessionContext(
    val ageRange: String,
    val topic: String,
    val classContext: String?,
    val currentAttempt: Int,
    val attemptsRemaining: Boolean
)

/**
 * Resultado de la evaluacion hibrida de un intento, con la decision final que
 * consume el orquestador y los metadatos tecnicos (capa, decision del juez,
 * confianza, motivo corto, latencia, respaldo). No contiene audio ni datos
 * sensibles.
 */
data class HybridEvaluationResult(
    val finalResult: SemanticResult,
    val decisionLayer: JudgeDecisionLayer,
    val localResult: SemanticResult,
    val judgeConsulted: Boolean,
    val judgeDecision: JudgeDecision?,
    val acceptedAsEquivalent: Boolean,
    val confidence: Double?,
    val reason: String?,
    val judgeLatencyMs: Long?,
    val usedFallback: Boolean
)

/**
 * MED01: evaluacion hibrida de respuestas abiertas para el modo inteligente.
 *
 * Primera capa SIEMPRE local ([SemanticEvaluator]): si la respuesta es claramente
 * correcta, se acepta sin consultar al juez externo. El juez textual solo se invoca
 * para casos abiertos o dudosos (la capa local marco incorrecto y la pregunta admite
 * ejemplos o la referencia parece una lista). Si el juez falla, se usa un respaldo
 * local seguro: nunca bloquea la sesion y, ante la duda, no sobreacepta.
 */
class HybridAnswerEvaluator(
    private val judge: OpenAnswerJudge,
    private val semanticEvaluator: SemanticEvaluator = SemanticEvaluator()
) {

    /**
     * Combina [localResult] (ya calculado por la capa local) con el juez cuando el
     * caso es abierto/dudoso. Devuelve la decision final y sus metadatos.
     */
    suspend fun evaluate(
        transcription: String,
        question: LearningQuestion,
        localResult: SemanticResult,
        context: HybridSessionContext
    ): HybridEvaluationResult {
        // Caso claro o no aplicable: la capa local decide y no se consulta al juez.
        if (!shouldConsultJudge(localResult, question)) {
            return HybridEvaluationResult(
                finalResult = localResult,
                decisionLayer = JudgeDecisionLayer.LOCAL,
                localResult = localResult,
                judgeConsulted = false,
                judgeDecision = null,
                acceptedAsEquivalent = localResult == SemanticResult.CORRECT,
                confidence = null,
                reason = null,
                judgeLatencyMs = null,
                usedFallback = false
            )
        }

        // Caso abierto pero el juez no esta disponible: respaldo local seguro.
        if (!judge.isConfigured()) {
            return fallbackResult(localResult, reason = "juez no configurado", latencyMs = null)
        }

        val input = OpenAnswerJudgeInput(
            questionText = question.questionText,
            childFriendlyQuestionText = question.childFriendlyQuestionText,
            referenceAnswer = question.expectedAnswer,
            childAnswer = transcription,
            ageRange = context.ageRange,
            topic = context.topic,
            classContext = context.classContext,
            currentAttempt = context.currentAttempt,
            attemptsRemaining = context.attemptsRemaining,
            availableHint = question.supportiveFeedbackText ?: question.retryPromptText,
            localResult = localResult.name
        )

        return when (val outcome = judge.judge(input)) {
            is OpenAnswerJudge.Outcome.FromModel -> {
                val verdict = outcome.verdict
                val finalResult = when (verdict.decision) {
                    JudgeDecision.CORRECT -> SemanticResult.CORRECT
                    // INCORRECT o UNCERTAIN: se conserva una decision segura
                    // (incorrecta) para dar pista/apoyo, nunca sobreacepta.
                    JudgeDecision.INCORRECT,
                    JudgeDecision.UNCERTAIN -> SemanticResult.INCORRECT
                }
                HybridEvaluationResult(
                    finalResult = finalResult,
                    decisionLayer = JudgeDecisionLayer.JUDGE,
                    localResult = localResult,
                    judgeConsulted = true,
                    judgeDecision = verdict.decision,
                    acceptedAsEquivalent = verdict.acceptedAsEquivalent &&
                        verdict.decision == JudgeDecision.CORRECT,
                    confidence = verdict.confidence,
                    reason = verdict.reason,
                    judgeLatencyMs = outcome.latencyMs,
                    usedFallback = false
                )
            }

            is OpenAnswerJudge.Outcome.Unavailable ->
                fallbackResult(localResult, reason = outcome.reason, latencyMs = outcome.latencyMs)
        }
    }

    private fun fallbackResult(
        localResult: SemanticResult,
        reason: String,
        latencyMs: Long?
    ): HybridEvaluationResult = HybridEvaluationResult(
        // Respaldo seguro: se mantiene la decision local (incorrecta) para no
        // sobreaceptar sin base. Los casos obvios ya los acepta la capa local.
        finalResult = localResult,
        decisionLayer = JudgeDecisionLayer.FALLBACK_LOCAL,
        localResult = localResult,
        judgeConsulted = true,
        judgeDecision = null,
        acceptedAsEquivalent = false,
        confidence = null,
        reason = reason,
        judgeLatencyMs = latencyMs,
        usedFallback = true
    )

    /**
     * El juez solo se consulta cuando la capa local marco incorrecto y el caso es
     * abierto: la referencia parece una lista/ejemplo o la pregunta admite ejemplos.
     * Las respuestas correctas, sin voz o no interpretables no pasan por el juez.
     */
    fun shouldConsultJudge(localResult: SemanticResult, question: LearningQuestion): Boolean {
        if (localResult != SemanticResult.INCORRECT) return false
        return semanticEvaluator.referenceLooksLikeList(question.expectedAnswer) ||
            questionLooksOpen(question.questionText)
    }

    /**
     * Heuristica local de pregunta abierta: detecta enunciados que piden mencionar,
     * nombrar o dar un ejemplo, donde la respuesta de referencia es solo una guia.
     */
    fun questionLooksOpen(questionText: String): Boolean {
        val normalized = normalize(questionText)
        return OPEN_QUESTION_MARKERS.any { normalized.contains(it) }
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private companion object {
        val OPEN_QUESTION_MARKERS = listOf(
            "menciona", "nombra", "di un", "di una", "dime un", "dime una",
            "dame un", "dame una", "ejemplo", "algun", "alguna", "un animal",
            "una cosa", "que animal", "que cosa", "como cual", "nombrame",
            "puedes decir", "dinos un", "dinos una"
        )
    }
}
