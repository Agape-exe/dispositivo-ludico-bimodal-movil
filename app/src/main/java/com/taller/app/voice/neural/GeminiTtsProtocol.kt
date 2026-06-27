package com.taller.app.voice.neural

import java.util.Base64

object GeminiTtsProtocol {

    fun buildPrompt(text: String, instructions: String): String =
        "${instructions.trim()} Di exactamente: \"$text\""

    fun buildRequestJson(text: String, config: GeminiTtsConfig): String {
        val prompt = jsonString(buildPrompt(text, config.instructions))
        val voiceName = jsonString(config.voiceName)
        val model = jsonString(config.model)
        return """
            {
              "contents":[{"parts":[{"text":$prompt}]}],
              "generationConfig":{
                "responseModalities":["AUDIO"],
                "speechConfig":{
                  "voiceConfig":{
                    "prebuiltVoiceConfig":{"voiceName":$voiceName}
                  }
                }
              },
              "model":$model
            }
        """.trimIndent()
    }

    fun parseAudio(responseBody: String): AudioPayload {
        if (!responseBody.contains("\"candidates\"")) {
            throw GeminiTtsParseException("Gemini no devolvio candidatos de audio.")
        }
        if (!responseBody.contains("\"inlineData\"")) {
            throw GeminiTtsParseException("Gemini no devolvio inlineData de audio.")
        }
        for (match in INLINE_DATA_REGEX.findAll(responseBody)) {
            val inlineData = match.value
            val data = stringField(inlineData, "data")?.takeIf { it.isNotBlank() }
                ?: throw GeminiTtsParseException("Gemini devolvio audio vacio.")
            val mimeType = stringField(inlineData, "mimeType").orEmpty().ifBlank { "audio/pcm" }
            val bytes = try {
                Base64.getDecoder().decode(data)
            } catch (_: IllegalArgumentException) {
                throw GeminiTtsParseException("Gemini devolvio audio invalido.")
            }
            if (bytes.isEmpty()) {
                throw GeminiTtsParseException("Gemini devolvio audio vacio.")
            }
            return AudioPayload(bytes = bytes, mimeType = mimeType)
        }

        throw GeminiTtsParseException("Gemini no devolvio inlineData de audio.")
    }

    fun shouldWrapAsWav(mimeType: String): Boolean {
        val lower = mimeType.lowercase()
        return lower.contains("pcm") || lower.contains("l16") || lower == "audio/raw"
    }

    data class AudioPayload(
        val bytes: ByteArray,
        val mimeType: String
    )

    private fun jsonString(value: String): String {
        val escaped = buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
        return "\"$escaped\""
    }

    private fun stringField(jsonFragment: String, fieldName: String): String? {
        val pattern = Regex(""""${Regex.escape(fieldName)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = pattern.find(jsonFragment)?.groupValues?.get(1) ?: return null
        return raw
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private val INLINE_DATA_REGEX = Regex(
        pattern = """"inlineData"\s*:\s*\{[^{}]*\}""",
        option = RegexOption.DOT_MATCHES_ALL
    )
}

class GeminiTtsParseException(message: String) : Exception(message)
