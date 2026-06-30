package com.taller.app.gpt.judge

import java.text.Normalizer
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class OpenAnswerJudgeParseException(message: String) : Exception(message)

/**
 * MED01: convierte el texto JSON del juez en un [OpenAnswerJudgeVerdict] validado y
 * saneado localmente. Aplica el principio "el modelo propone, el orquestador
 * dispone": aunque el modelo se equivoque, la app garantiza que nunca se revele la
 * respuesta cuando quedan intentos y que los valores esten dentro de rango.
 */
object OpenAnswerJudgeParser {

    private const val MAX_REASON_LENGTH = 90
    private const val MAX_NORMALIZED_LENGTH = 60

    /**
     * Parsea y valida el veredicto. Lanza [OpenAnswerJudgeParseException] si el JSON
     * es invalido o le falta una decision reconocible. El saneo de seguridad (no
     * revelar respuesta cuando quedan intentos, rangos) se aplica siempre.
     */
    fun parse(rawText: String, input: OpenAnswerJudgeInput): OpenAnswerJudgeVerdict {
        val jsonText = stripFences(rawText)
        val root = try {
            JSONTokener(jsonText).nextValue()
        } catch (e: JSONException) {
            throw OpenAnswerJudgeParseException("El veredicto no es JSON valido")
        }
        if (root !is JSONObject) {
            throw OpenAnswerJudgeParseException("El veredicto no es un objeto JSON")
        }

        val decision = JudgeDecision.parse(root.optString("decision"))
            ?: throw OpenAnswerJudgeParseException("Decision invalida o ausente")

        val confidence = root.optDouble("confidence", 0.0)
            .let { if (it.isNaN()) 0.0 else it }
            .coerceIn(0.0, 1.0)

        val attemptsRemaining = input.attemptsRemaining
        val rawReason = root.optString("reason").trim().take(MAX_REASON_LENGTH)
        val rawRevealsAnswer = root.optBoolean("revealsAnswer", false)

        // Salvaguarda de seguridad: si quedan intentos, nunca se revela la respuesta.
        // Si el motivo menciona la referencia o el modelo marco revealsAnswer, se
        // redacta el motivo y se fuerza revealsAnswer=false.
        val mentionsAnswer = attemptsRemaining &&
            reasonMentionsAnswer(rawReason, input.referenceAnswer)
        val mustHide = attemptsRemaining && (rawRevealsAnswer || mentionsAnswer)
        val reason = if (mustHide) {
            "motivo oculto para no revelar la respuesta"
        } else {
            rawReason.ifBlank { defaultReasonFor(decision) }
        }
        val revealsAnswer = if (attemptsRemaining) false else rawRevealsAnswer

        return OpenAnswerJudgeVerdict(
            decision = decision,
            confidence = confidence,
            reason = reason,
            acceptedAsEquivalent = root.optBoolean("acceptedAsEquivalent", decision == JudgeDecision.CORRECT),
            shouldRetry = root.optBoolean("shouldRetry", decision != JudgeDecision.CORRECT && attemptsRemaining),
            feedbackType = JudgeFeedbackType.parse(root.optString("feedbackType")),
            safeHintLevel = root.optInt("safeHintLevel", 0).coerceIn(0, 3),
            revealsAnswer = revealsAnswer,
            normalizedChildAnswer = root.optString("normalizedChildAnswer").trim().take(MAX_NORMALIZED_LENGTH),
            normalizedExpectedConcept = root.optString("normalizedExpectedConcept").trim().take(MAX_NORMALIZED_LENGTH),
            // MED02: tipo de aceptacion declarado por el modelo, saneado a un valor conocido.
            // Solo es informativo; si el juez no acepto, no se conserva un tipo de aceptacion.
            acceptanceType = if (decision == JudgeDecision.CORRECT) {
                JudgeAcceptanceType.parse(root.optString("acceptanceType"))
            } else {
                JudgeAcceptanceType.NONE
            }
        )
    }

    private fun defaultReasonFor(decision: JudgeDecision): String = when (decision) {
        JudgeDecision.CORRECT -> "respuesta valida"
        JudgeDecision.INCORRECT -> "no responde la pregunta"
        JudgeDecision.UNCERTAIN -> "sin base suficiente para decidir"
    }

    /**
     * Indica si [reason] menciona alguna opcion de la respuesta de referencia como
     * palabra completa, lo que revelaria la respuesta. Comparacion normalizada.
     */
    private fun reasonMentionsAnswer(reason: String, referenceAnswer: String): Boolean {
        if (reason.isBlank() || referenceAnswer.isBlank()) return false
        val normalizedReason = " ${normalize(reason)} "
        return referenceAnswer
            .split(Regex("(?i)[,;/\\n]| y | o | u | e "))
            .map { normalize(it) }
            .filter { it.length >= 3 }
            .any { normalizedReason.contains(" $it ") }
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun stripFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val lines = trimmed.lines()
        if (lines.size < 3) throw OpenAnswerJudgeParseException("Veredicto con formato invalido")
        val first = lines.first().trim()
        val last = lines.last().trim()
        if ((first == "```" || first == "```json") && last == "```") {
            return lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        throw OpenAnswerJudgeParseException("Veredicto con formato invalido")
    }
}
