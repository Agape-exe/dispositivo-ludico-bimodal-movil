package com.taller.app.gpt.script

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class SessionScriptParseException(message: String) : Exception(message)

/**
 * GEN01 / GEN01-FIX01: convierte el texto JSON devuelto por el modelo en un
 * [SessionScript]. Es tolerante a propósito:
 *
 *  - Acepta JSON limpio, JSON envuelto en vallas ```json y JSON con texto antes o
 *    después (extrae el primer objeto JSON balanceado).
 *  - Acepta nombres de campo equivalentes razonables (alias).
 *  - Empareja cada pregunta del guion con la pregunta original por orderIndex y, si
 *    no coincide, por posición en el arreglo.
 *  - NO lanza por campos opcionales faltantes ni por una pregunta incompleta: deja
 *    esos campos en blanco para que la capa de recuperación los complete con el
 *    guion local ([SessionScriptRecovery]). Solo lanza cuando no hay ningún objeto
 *    JSON utilizable o no hay arreglo de preguntas.
 */
object SessionScriptParser {

    private val INTRO_KEYS = listOf("intro", "generatedIntroText", "sessionIntro")
    private val CLOSING_KEYS = listOf("closing", "generatedClosingText", "sessionClosing")
    private val TONE_KEYS = listOf("toneNotes", "tone", "generatedToneNotes")
    private val WARNINGS_KEYS =
        listOf("pedagogicalWarnings", "warnings", "generatedPedagogicalWarnings")
    private val QUESTIONS_KEYS = listOf("questions", "items", "questionScripts")

    private val CHILD_QUESTION_KEYS =
        listOf("childFriendlyQuestionText", "childFriendlyQuestion", "spokenQuestion")
    private val POSITIVE_KEYS = listOf("positiveFeedbackText", "positiveFeedback")
    private val SUPPORTIVE_KEYS = listOf("supportiveFeedbackText", "supportiveFeedback")
    private val RETRY_KEYS = listOf("retryPromptText", "retryPrompt")
    private val WARNING_KEYS = listOf("answerReferenceWarning", "referenceWarning")
    private val SUGGESTED_KEYS = listOf("suggestedReferenceAnswer", "suggestedReference")
    private val HINTS_ARRAY_KEYS = listOf("hints", "hintLevels")

    fun parse(rawText: String, input: SessionScriptInput): SessionScript {
        val jsonText = extractJsonObject(rawText)
        val root = try {
            JSONTokener(jsonText).nextValue()
        } catch (e: JSONException) {
            throw SessionScriptParseException("El guion no es JSON valido")
        }
        if (root !is JSONObject) {
            throw SessionScriptParseException("El guion no es un objeto JSON")
        }

        val questionsArray = root.firstArray(QUESTIONS_KEYS)
            ?: throw SessionScriptParseException("El guion no incluye preguntas")

        // Indexa los nodos del modelo por orderIndex (cuando viene) y por posición,
        // para tolerar guiones cuyo orderIndex no coincida o falte.
        val byOrder = HashMap<Int, JSONObject>()
        val positional = ArrayList<JSONObject>()
        for (i in 0 until questionsArray.length()) {
            val item = questionsArray.optJSONObject(i) ?: continue
            positional.add(item)
            if (item.has("orderIndex")) {
                val order = item.optInt("orderIndex", Int.MIN_VALUE)
                if (order != Int.MIN_VALUE) byOrder[order] = item
            }
        }

        val questionScripts = input.questions.mapIndexed { index, source ->
            val node = byOrder[source.orderIndex] ?: positional.getOrNull(index)
            mapQuestion(source, node)
        }

        return SessionScript(
            intro = root.firstString(INTRO_KEYS),
            closing = root.firstString(CLOSING_KEYS),
            toneNotes = root.firstString(TONE_KEYS),
            pedagogicalWarnings = root.firstString(WARNINGS_KEYS),
            questions = questionScripts
        )
    }

    private fun mapQuestion(
        source: SessionScriptQuestionInput,
        node: JSONObject?
    ): QuestionScript {
        if (node == null) {
            // Pregunta sin guion del modelo: se deja en blanco; la recuperación la
            // completará con el guion local sin descartar el resto.
            return QuestionScript(
                questionId = source.questionId,
                orderIndex = source.orderIndex,
                childFriendlyQuestionText = "",
                hintLevel1 = "",
                hintLevel2 = "",
                hintLevel3 = "",
                positiveFeedbackText = "",
                supportiveFeedbackText = "",
                retryPromptText = "",
                answerReferenceWarning = "",
                suggestedReferenceAnswer = source.referenceAnswer.trim()
            )
        }
        val hintsArray = node.firstArray(HINTS_ARRAY_KEYS)
        return QuestionScript(
            questionId = source.questionId,
            orderIndex = source.orderIndex,
            childFriendlyQuestionText = node.firstString(CHILD_QUESTION_KEYS),
            hintLevel1 = node.hint(1, hintsArray),
            hintLevel2 = node.hint(2, hintsArray),
            hintLevel3 = node.hint(3, hintsArray),
            positiveFeedbackText = node.firstString(POSITIVE_KEYS),
            supportiveFeedbackText = node.firstString(SUPPORTIVE_KEYS),
            retryPromptText = node.firstString(RETRY_KEYS),
            answerReferenceWarning = node.firstString(WARNING_KEYS),
            suggestedReferenceAnswer = node.firstString(SUGGESTED_KEYS)
                .ifEmpty { source.referenceAnswer.trim() }
        )
    }

    /** Pista de nivel [level] (1..3): primero por clave directa, si no por arreglo. */
    private fun JSONObject.hint(level: Int, hintsArray: JSONArray?): String {
        val direct = firstString(listOf("hintLevel$level", "hint$level"))
        if (direct.isNotEmpty()) return direct
        val fromArray = hintsArray?.optString(level - 1, "").orEmpty().trim()
        return fromArray
    }

    private fun JSONObject.firstString(keys: List<String>): String {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val value = optString(key, "").trim()
                if (value.isNotEmpty()) return value
            }
        }
        return ""
    }

    private fun JSONObject.firstArray(keys: List<String>): JSONArray? {
        for (key in keys) {
            optJSONArray(key)?.let { return it }
        }
        return null
    }

    /**
     * Extrae el primer objeto JSON balanceado del texto, ignorando vallas de código
     * y cualquier texto antes o después. Respeta cadenas y escapes para no cortar en
     * una llave dentro de una cadena. Lanza si no hay un objeto utilizable.
     */
    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw SessionScriptParseException("El guion llegó vacío")
        }
        val start = trimmed.indexOf('{')
        if (start < 0) {
            throw SessionScriptParseException("El guion no contiene JSON")
        }

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until trimmed.length) {
            val c = trimmed[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return trimmed.substring(start, i + 1)
                }
            }
        }
        // No se cerró el objeto: la respuesta probablemente llegó truncada.
        throw SessionScriptParseException("El guion JSON parece incompleto")
    }
}
