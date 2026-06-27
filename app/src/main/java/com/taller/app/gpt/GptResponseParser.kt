package com.taller.app.gpt

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

class SevenParseException(message: String) : Exception(message)

object GptResponseParser {
    private val requiredKeys = setOf(
        "intent",
        "responseType",
        "visibleText",
        "safetyLevel",
        "fallbackUsed",
        "canGiveHint",
        "canGiveFinalAnswer",
        "shouldAskRepeat",
        "shouldRecaptureAttention",
        "topic",
        "localEvaluation",
        "attemptsRemaining",
        "maxWords",
        "blockedReason",
        "safeForTts",
        "validationNotes"
    )

    fun extractOutputText(body: String): String {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw SevenParseException("Response body is not JSON")
        }

        root.optString("output_text")
            .takeIf { it.isNotBlank() }
            ?.let { return it }

        val collected = StringBuilder()
        val output = root.optJSONArray("output")
        if (output != null) {
            for (i in 0 until output.length()) {
                val outputItem = output.optJSONObject(i) ?: continue
                val content = outputItem.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val contentItem = content.optJSONObject(j) ?: continue
                    val text = contentItem.optString("text").ifBlank { contentItem.optString("value") }
                    if (text.isNotBlank()) {
                        if (collected.isNotEmpty()) collected.append('\n')
                        collected.append(text)
                    }
                }
            }
        }

        return collected.toString().takeIf { it.isNotBlank() }
            ?: throw SevenParseException("Response has no output text")
    }

    fun parseApiResponse(body: String): SevenResponse =
        parseSevenResponseText(extractOutputText(body))

    fun parseSevenResponseText(text: String): SevenResponse {
        val jsonText = normalizeModelJsonText(text)
        val parsed = try {
            JSONTokener(jsonText).nextValue()
        } catch (e: JSONException) {
            throw SevenParseException("Output text is not JSON")
        }

        val obj = when (parsed) {
            is JSONObject -> parsed
            is String -> parseObjectFromString(parsed)
            else -> throw SevenParseException("Output JSON is not an object")
        }
        validateExactKeys(obj)

        return SevenResponse(
            intent = SevenIntent.parse(obj.getString("intent")),
            responseType = SevenResponseType.parse(obj.getString("responseType")),
            visibleText = obj.getString("visibleText"),
            safetyLevel = SevenSafetyLevel.parse(obj.getString("safetyLevel")),
            fallbackUsed = obj.getBoolean("fallbackUsed"),
            canGiveHint = obj.getBoolean("canGiveHint"),
            canGiveFinalAnswer = obj.getBoolean("canGiveFinalAnswer"),
            shouldAskRepeat = obj.getBoolean("shouldAskRepeat"),
            shouldRecaptureAttention = obj.getBoolean("shouldRecaptureAttention"),
            topic = obj.getString("topic"),
            localEvaluation = SevenLocalEvaluation.parse(obj.getString("localEvaluation")),
            attemptsRemaining = obj.getInt("attemptsRemaining"),
            maxWords = obj.getInt("maxWords"),
            blockedReason = SevenBlockedReason.parse(obj.getString("blockedReason")),
            safeForTts = obj.getBoolean("safeForTts"),
            validationNotes = obj.getString("validationNotes")
        )
    }

    private fun parseObjectFromString(value: String): JSONObject {
        val reparsed = try {
            JSONTokener(value.trim()).nextValue()
        } catch (e: JSONException) {
            throw SevenParseException("Output JSON string is not an object")
        }
        return reparsed as? JSONObject ?: throw SevenParseException("Output JSON string is not an object")
    }

    private fun validateExactKeys(obj: JSONObject) {
        val keys = obj.keys().asSequence().toSet()
        val missing = requiredKeys - keys
        if (missing.isNotEmpty()) {
            throw SevenParseException("Missing required fields")
        }
        val extra = keys - requiredKeys
        if (extra.isNotEmpty()) {
            throw SevenParseException("Unexpected fields")
        }
    }

    private fun normalizeModelJsonText(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed

        val lines = trimmed.lines()
        if (lines.size < 3) throw SevenParseException("Invalid fenced JSON")
        val first = lines.first().trim()
        val last = lines.last().trim()
        if ((first == "```" || first == "```json") && last == "```") {
            return lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        throw SevenParseException("Unsafe fenced JSON")
    }
}
