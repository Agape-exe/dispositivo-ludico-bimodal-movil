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
        val hasCandidates = hasJsonField(responseBody, "candidates")
        if (!hasCandidates) {
            throw GeminiTtsParseException(
                safeMessage = "Gemini fallo: respuesta sin candidatos.",
                diagnostics = ParseDiagnostics(hasCandidates = false)
            )
        }
        val hasInlineData = hasJsonField(responseBody, "inlineData") || hasJsonField(responseBody, "inline_data")
        if (!hasInlineData) {
            throw GeminiTtsParseException(
                safeMessage = "Gemini fallo: respuesta sin audio.",
                diagnostics = ParseDiagnostics(hasCandidates = true, hasInlineData = false)
            )
        }
        for (match in inlineDataRegex.findAll(responseBody)) {
            val inlineData = match.value
            val data = stringField(inlineData, "data")?.takeIf { it.isNotBlank() }
                ?: throw GeminiTtsParseException(
                    safeMessage = "Gemini fallo: respuesta sin audio.",
                    diagnostics = ParseDiagnostics(hasCandidates = true, hasInlineData = true)
                )
            val mimeType = stringField(inlineData, "mimeType")
                ?: stringField(inlineData, "mime_type")
                ?: "audio/pcm"
            val bytes = try {
                Base64.getMimeDecoder().decode(data)
            } catch (_: IllegalArgumentException) {
                throw GeminiTtsParseException(
                    safeMessage = "Gemini fallo: audio invalido.",
                    diagnostics = ParseDiagnostics(
                        hasCandidates = true,
                        hasInlineData = true,
                        mimeType = mimeType,
                        base64DecodeFailed = true
                    )
                )
            }
            if (bytes.isEmpty()) {
                throw GeminiTtsParseException(
                    safeMessage = "Gemini fallo: respuesta sin audio.",
                    diagnostics = ParseDiagnostics(
                        hasCandidates = true,
                        hasInlineData = true,
                        mimeType = mimeType,
                        audioBytes = 0
                    )
                )
            }
            return AudioPayload(bytes = bytes, mimeType = mimeType)
        }

        throw GeminiTtsParseException(
            safeMessage = "Gemini fallo: respuesta sin audio.",
            diagnostics = ParseDiagnostics(hasCandidates = true, hasInlineData = false)
        )
    }

    fun shouldWrapAsWav(mimeType: String): Boolean {
        val lower = mimeType.lowercase()
        return lower.contains("pcm") || lower.contains("l16") || lower == "audio/raw"
    }

    data class AudioPayload(
        val bytes: ByteArray,
        val mimeType: String
    )

    data class ParseDiagnostics(
        val hasCandidates: Boolean,
        val hasInlineData: Boolean = false,
        val mimeType: String? = null,
        val audioBytes: Int? = null,
        val base64DecodeFailed: Boolean = false
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

    fun safeErrorMessage(responseBody: String): String? =
        stringField(responseBody, "message")?.takeIf { it.isNotBlank() }
            ?: stringField(responseBody, "status")?.takeIf { it.isNotBlank() }

    private fun hasJsonField(json: String, fieldName: String): Boolean =
        Regex(""""${Regex.escape(fieldName)}"\s*:""").containsMatchIn(json)

    private val inlineDataRegex = Regex(
        pattern = """"(?:inlineData|inline_data)"\s*:\s*\{[^{}]*\}""",
        option = RegexOption.DOT_MATCHES_ALL
    )
}

class GeminiTtsParseException(
    val safeMessage: String,
    val diagnostics: GeminiTtsProtocol.ParseDiagnostics
) : Exception(safeMessage)
