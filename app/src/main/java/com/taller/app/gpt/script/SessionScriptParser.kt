package com.taller.app.gpt.script

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class SessionScriptParseException(message: String) : Exception(message)

/**
 * GEN01: convierte el texto JSON devuelto por el modelo en un [SessionScript].
 * Empareja cada pregunta del guion con la pregunta original por orderIndex.
 */
object SessionScriptParser {

    fun parse(rawText: String, input: SessionScriptInput): SessionScript {
        val jsonText = stripFences(rawText)
        val root = try {
            JSONTokener(jsonText).nextValue()
        } catch (e: JSONException) {
            throw SessionScriptParseException("El guion no es JSON valido")
        }
        if (root !is JSONObject) {
            throw SessionScriptParseException("El guion no es un objeto JSON")
        }

        val questionsArray = root.optJSONArray("questions")
            ?: throw SessionScriptParseException("El guion no incluye preguntas")

        val byOrder = HashMap<Int, JSONObject>()
        for (i in 0 until questionsArray.length()) {
            val item = questionsArray.optJSONObject(i) ?: continue
            val order = if (item.has("orderIndex")) item.optInt("orderIndex", -1) else (i + 1)
            byOrder[order] = item
        }

        val questionScripts = input.questions.map { source ->
            val node = byOrder[source.orderIndex]
                ?: throw SessionScriptParseException("Falta el guion de la pregunta ${source.orderIndex}")
            QuestionScript(
                questionId = source.questionId,
                orderIndex = source.orderIndex,
                childFriendlyQuestionText = node.string("childFriendlyQuestionText"),
                hintLevel1 = node.string("hintLevel1"),
                hintLevel2 = node.string("hintLevel2"),
                hintLevel3 = node.string("hintLevel3"),
                positiveFeedbackText = node.string("positiveFeedbackText"),
                supportiveFeedbackText = node.string("supportiveFeedbackText"),
                retryPromptText = node.string("retryPromptText"),
                answerReferenceWarning = node.string("answerReferenceWarning"),
                suggestedReferenceAnswer = node.string("suggestedReferenceAnswer")
            )
        }

        return SessionScript(
            intro = root.string("intro"),
            closing = root.string("closing"),
            toneNotes = root.string("toneNotes"),
            pedagogicalWarnings = root.string("pedagogicalWarnings"),
            questions = questionScripts
        )
    }

    private fun JSONObject.string(key: String): String = optString(key, "").trim()

    private fun stripFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val lines = trimmed.lines()
        if (lines.size < 3) throw SessionScriptParseException("Guion con formato invalido")
        val first = lines.first().trim()
        val last = lines.last().trim()
        if ((first == "```" || first == "```json") && last == "```") {
            return lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        throw SessionScriptParseException("Guion con formato invalido")
    }
}
