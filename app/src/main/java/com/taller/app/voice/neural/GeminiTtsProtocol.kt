package com.taller.app.voice.neural

import org.json.JSONObject
import java.util.Base64

object GeminiTtsProtocol {

    fun buildPrompt(text: String, instructions: String): String =
        "${instructions.trim()} Di exactamente: \"$text\""

    fun endpointUrl(model: String): String =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

    fun buildRequestJson(text: String, config: GeminiTtsConfig): String {
        val prompt = jsonString(buildPrompt(text, config.instructions))
        val voiceName = jsonString(config.voiceName)
        // El modelo viaja en la URL (models/{model}:generateContent). Incluirlo
        // tambien en el body provoca HTTP 400 en generateContent, por eso se omite.
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
              }
            }
        """.trimIndent()
    }

    fun parseAudio(responseBody: String): AudioPayload {
        parseAudioWithJson(responseBody)?.let { return it }
        return parseAudioWithFallbackScanner(responseBody)
    }

    private fun parseAudioWithJson(responseBody: String): AudioPayload? {
        try {
            val root = JSONObject(responseBody)
            val promptFeedbackPresent = root.has("promptFeedback")
            val candidates = root.optJSONArray("candidates")
                ?: throw GeminiTtsParseException(
                    safeMessage = "Gemini fallo: la respuesta no contiene candidates.",
                    diagnostics = ParseDiagnostics(
                        hasCandidates = false,
                        hasPromptFeedback = promptFeedbackPresent
                    )
                )
            if (candidates.length() == 0) {
                throw GeminiTtsParseException(
                    safeMessage = "Gemini fallo: candidates vacios.",
                    diagnostics = ParseDiagnostics(
                        hasCandidates = true,
                        candidateCount = 0,
                        hasPromptFeedback = promptFeedbackPresent
                    )
                )
            }

            var totalParts = 0
            var hasContent = false
            var hasText = false
            var hasInlineData = false
            val finishReasons = mutableListOf<String>()

            for (candidateIndex in 0 until candidates.length()) {
                val candidate = candidates.optJSONObject(candidateIndex) ?: continue
                candidate.optString("finishReason").takeIf { it.isNotBlank() }?.let(finishReasons::add)
                val content = candidate.optJSONObject("content") ?: continue
                hasContent = true
                val parts = content.optJSONArray("parts") ?: continue
                totalParts += parts.length()
                for (partIndex in 0 until parts.length()) {
                    val part = parts.optJSONObject(partIndex) ?: continue
                    hasText = hasText || part.optString("text").isNotBlank()
                    val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                    if (inlineData != null) {
                        hasInlineData = true
                        val data = inlineData.optString("data").takeIf { it.isNotBlank() }
                        val mimeType = inlineData.optString("mimeType").takeIf { it.isNotBlank() }
                            ?: inlineData.optString("mime_type").takeIf { it.isNotBlank() }
                            ?: "audio/pcm"
                        if (data.isNullOrBlank()) {
                            throw GeminiTtsParseException(
                                safeMessage = "Gemini fallo: inlineData sin data.",
                                diagnostics = ParseDiagnostics(
                                    hasCandidates = true,
                                    candidateCount = candidates.length(),
                                    hasContent = hasContent,
                                    hasParts = totalParts > 0,
                                    partCount = totalParts,
                                    hasInlineData = true,
                                    hasText = hasText,
                                    mimeType = mimeType,
                                    finishReason = finishReasons.joinToString(",").ifBlank { null },
                                    hasPromptFeedback = promptFeedbackPresent
                                )
                            )
                        }
                        return decodeAudioPayload(
                            data = data,
                            mimeType = mimeType,
                            diagnostics = ParseDiagnostics(
                                hasCandidates = true,
                                candidateCount = candidates.length(),
                                hasContent = hasContent,
                                hasParts = totalParts > 0,
                                partCount = totalParts,
                                hasInlineData = true,
                                hasText = hasText,
                                mimeType = mimeType,
                                base64Chars = data.length,
                                finishReason = finishReasons.joinToString(",").ifBlank { null },
                                hasPromptFeedback = promptFeedbackPresent
                            )
                        )
                    }
                }
            }

            val message = when {
                !hasContent -> "Gemini fallo: candidates sin content."
                totalParts == 0 -> "Gemini fallo: candidates sin parts."
                hasText -> "Gemini fallo: respuesta textual sin audio."
                !hasInlineData -> "Gemini fallo: parts sin inlineData."
                else -> "Gemini fallo: respuesta sin audio."
            }
            throw GeminiTtsParseException(
                safeMessage = message,
                diagnostics = ParseDiagnostics(
                    hasCandidates = true,
                    candidateCount = candidates.length(),
                    hasContent = hasContent,
                    hasParts = totalParts > 0,
                    partCount = totalParts,
                    hasInlineData = hasInlineData,
                    hasText = hasText,
                    finishReason = finishReasons.joinToString(",").ifBlank { null },
                    hasPromptFeedback = promptFeedbackPresent
                )
            )
        } catch (e: GeminiTtsParseException) {
            throw e
        } catch (_: RuntimeException) {
            // En unit tests JVM las clases org.json de Android estan stubbeadas.
            return null
        }
    }

    private fun parseAudioWithFallbackScanner(responseBody: String): AudioPayload {
        val hasCandidates = hasJsonField(responseBody, "candidates")
        if (!hasCandidates) {
            throw GeminiTtsParseException(
                safeMessage = "Gemini fallo: la respuesta no contiene candidates.",
                diagnostics = ParseDiagnostics(
                    hasCandidates = false,
                    hasPromptFeedback = hasJsonField(responseBody, "promptFeedback")
                )
            )
        }
        val hasInlineData = hasJsonField(responseBody, "inlineData") || hasJsonField(responseBody, "inline_data")
        val hasText = hasJsonField(responseBody, "text")
        val candidateCount = maxOf(0, Regex(""""content"\s*:""").findAll(responseBody).count())
        val partCount = maxOf(0, Regex(""""(?:inlineData|inline_data|text)"\s*:""").findAll(responseBody).count())
        if (Regex(""""candidates"\s*:\s*\[\s*]""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(responseBody)) {
            throw GeminiTtsParseException(
                safeMessage = "Gemini fallo: candidates vacios.",
                diagnostics = ParseDiagnostics(
                    hasCandidates = true,
                    candidateCount = 0,
                    hasPromptFeedback = hasJsonField(responseBody, "promptFeedback")
                )
            )
        }
        if (Regex(""""parts"\s*:\s*\[\s*]""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(responseBody)) {
            throw GeminiTtsParseException(
                safeMessage = "Gemini fallo: candidates sin parts.",
                diagnostics = ParseDiagnostics(
                    hasCandidates = true,
                    candidateCount = candidateCount,
                    hasContent = true,
                    hasParts = false,
                    partCount = 0,
                    finishReason = stringField(responseBody, "finishReason"),
                    hasPromptFeedback = hasJsonField(responseBody, "promptFeedback")
                )
            )
        }
        if (!hasInlineData) {
            val message = if (hasText) {
                "Gemini fallo: respuesta textual sin audio."
            } else {
                "Gemini fallo: parts sin inlineData."
            }
            throw GeminiTtsParseException(
                safeMessage = message,
                diagnostics = ParseDiagnostics(
                    hasCandidates = true,
                    candidateCount = candidateCount,
                    hasParts = partCount > 0,
                    partCount = partCount,
                    hasInlineData = false,
                    hasText = hasText,
                    finishReason = stringField(responseBody, "finishReason"),
                    hasPromptFeedback = hasJsonField(responseBody, "promptFeedback")
                )
            )
        }
        for (match in inlineDataRegex.findAll(responseBody)) {
            val inlineData = match.value
            val data = stringField(inlineData, "data")?.takeIf { it.isNotBlank() }
                ?: throw GeminiTtsParseException(
                    safeMessage = "Gemini fallo: inlineData sin data.",
                    diagnostics = ParseDiagnostics(
                        hasCandidates = true,
                        candidateCount = candidateCount,
                        hasParts = partCount > 0,
                        partCount = partCount,
                        hasInlineData = true,
                        hasText = hasText,
                        finishReason = stringField(responseBody, "finishReason"),
                        hasPromptFeedback = hasJsonField(responseBody, "promptFeedback")
                    )
                )
            val mimeType = stringField(inlineData, "mimeType")
                ?: stringField(inlineData, "mime_type")
                ?: "audio/pcm"
            return decodeAudioPayload(
                data = data,
                mimeType = mimeType,
                diagnostics = ParseDiagnostics(
                    hasCandidates = true,
                    candidateCount = candidateCount,
                    hasParts = partCount > 0,
                    partCount = partCount,
                    hasInlineData = true,
                    hasText = hasText,
                    mimeType = mimeType,
                    base64Chars = data.length,
                    finishReason = stringField(responseBody, "finishReason"),
                    hasPromptFeedback = hasJsonField(responseBody, "promptFeedback")
                )
            )
        }

        throw GeminiTtsParseException(
            safeMessage = "Gemini fallo: parts sin inlineData.",
            diagnostics = ParseDiagnostics(
                hasCandidates = true,
                candidateCount = candidateCount,
                hasParts = partCount > 0,
                partCount = partCount,
                hasInlineData = false,
                hasText = hasText,
                finishReason = stringField(responseBody, "finishReason"),
                hasPromptFeedback = hasJsonField(responseBody, "promptFeedback")
            )
        )
    }

    private fun decodeAudioPayload(
        data: String,
        mimeType: String,
        diagnostics: ParseDiagnostics
    ): AudioPayload {
        val bytes = try {
            Base64.getMimeDecoder().decode(data)
        } catch (_: IllegalArgumentException) {
            throw GeminiTtsParseException(
                safeMessage = "Gemini fallo: audio Base64 invalido.",
                diagnostics = diagnostics.copy(base64DecodeFailed = true)
            )
        }
        if (bytes.isEmpty()) {
            throw GeminiTtsParseException(
                safeMessage = "Gemini fallo: inlineData sin data.",
                diagnostics = diagnostics.copy(audioBytes = 0)
            )
        }
        return AudioPayload(bytes = bytes, mimeType = mimeType)
    }

    fun shouldWrapAsWav(mimeType: String): Boolean {
        val lower = mimeType.lowercase()
        return lower.contains("pcm") || lower.contains("l16") || lower == "audio/raw"
    }

    /**
     * Extrae el sample rate del mimeType PCM de Gemini, por ejemplo
     * "audio/L16;codec=pcm;rate=24000". Si no se indica, asume 24000 Hz.
     */
    fun pcmSampleRate(mimeType: String, default: Int = 24000): Int {
        val match = sampleRateRegex.find(mimeType) ?: return default
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 8000..48000 } ?: default
    }

    private val sampleRateRegex = Regex("""rate=(\d+)""", RegexOption.IGNORE_CASE)

    data class AudioPayload(
        val bytes: ByteArray,
        val mimeType: String
    )

    data class ParseDiagnostics(
        val hasCandidates: Boolean,
        val candidateCount: Int? = null,
        val hasContent: Boolean = false,
        val hasParts: Boolean = false,
        val partCount: Int? = null,
        val hasInlineData: Boolean = false,
        val hasText: Boolean = false,
        val mimeType: String? = null,
        val base64Chars: Int? = null,
        val audioBytes: Int? = null,
        val base64DecodeFailed: Boolean = false,
        val finishReason: String? = null,
        val hasPromptFeedback: Boolean = false
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

    /**
     * Extrae el tiempo de espera que Google pide tras un 429, desde el campo
     * `retryDelay` del bloque RetryInfo del error (por ejemplo "17s" o "1.5s"),
     * convertido a milisegundos. Devuelve null si no viene o no es parseable, en cuyo
     * caso la app no impone ningun enfriamiento propio.
     */
    fun retryDelayMs(responseBody: String): Long? {
        val raw = stringField(responseBody, "retryDelay")?.trim() ?: return null
        val seconds = retryDelaySecondsRegex.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return null
        if (seconds <= 0.0) return null
        return (seconds * 1000.0).toLong()
    }

    private val retryDelaySecondsRegex = Regex("""([0-9]+(?:\.[0-9]+)?)s""")

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
