package com.taller.app.gpt.conversation

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class InitialConversationParseException(message: String) : Exception(message)

/**
 * FINAL-FLOW01-FIX01: veredicto saneado del juez conversacional.
 *
 * @property answer texto que Seven diria en voz alta (solo si [allowed]).
 * @property allowed si la respuesta es segura y dentro del alcance del juguete.
 * @property reason codigo corto y seguro (SAFE_CHILD_CONTEXT / OUT_OF_SCOPE / UNSAFE).
 */
data class InitialConversationVerdict(
    val answer: String,
    val allowed: Boolean,
    val reason: String
)

/**
 * FINAL-FLOW01-FIX01: convierte el texto JSON del juez conversacional en un
 * [InitialConversationVerdict] validado y saneado localmente. Aplica el principio
 * "el modelo propone, la app dispone": aunque el modelo se equivoque, la app acota
 * la longitud y descarta respuestas vacias o marcadas como no permitidas.
 */
object InitialConversationParser {

    private const val MAX_ANSWER_LENGTH = 180
    private const val MAX_REASON_LENGTH = 40

    /**
     * Parsea y valida el veredicto. Lanza [InitialConversationParseException] si el
     * JSON es invalido. Una respuesta permitida pero vacia se considera no utilizable.
     */
    fun parse(rawText: String): InitialConversationVerdict {
        val jsonText = stripFences(rawText)
        val root = try {
            JSONTokener(jsonText).nextValue()
        } catch (e: JSONException) {
            throw InitialConversationParseException("La respuesta no es JSON valido")
        }
        if (root !is JSONObject) {
            throw InitialConversationParseException("La respuesta no es un objeto JSON")
        }

        val allowed = root.optBoolean("allowed", false)
        val answer = root.optString("answer").trim().take(MAX_ANSWER_LENGTH)
        val reason = root.optString("reason").trim().take(MAX_REASON_LENGTH)
            .ifBlank { if (allowed) "SAFE_CHILD_CONTEXT" else "OUT_OF_SCOPE" }

        // Una respuesta permitida sin texto no sirve: se trata como no permitida.
        val effectiveAllowed = allowed && answer.isNotBlank()
        return InitialConversationVerdict(
            answer = if (effectiveAllowed) answer else "",
            allowed = effectiveAllowed,
            reason = reason
        )
    }

    private fun stripFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val lines = trimmed.lines()
        if (lines.size < 3) throw InitialConversationParseException("Respuesta con formato invalido")
        val first = lines.first().trim()
        val last = lines.last().trim()
        if ((first == "```" || first == "```json") && last == "```") {
            return lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        throw InitialConversationParseException("Respuesta con formato invalido")
    }
}
