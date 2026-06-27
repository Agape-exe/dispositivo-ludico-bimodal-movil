package com.taller.app.gpt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class GptClientImpl(
    private val configProvider: () -> GptConfig,
    private val clientProvider: (GptConfig) -> OkHttpClient = ::defaultClient,
    private val logSink: (String, String) -> Unit = ::safeLog
) : GptClient {

    override fun isEnabled(): Boolean = configProvider().enabled

    override fun isConfigured(): Boolean = configProvider().hasApiKey

    override suspend fun generate(prompt: GptPrompt): GptResult {
        val config = configProvider()
        val localFallback = GptLocalFallback.phraseFor(prompt.contextTag)

        if (!config.enabled) {
            logInfo(
                "AI_GPT_DISABLED",
                "contextTag=${prompt.contextTag} model=${config.model} " +
                    "systemLen=${prompt.systemInstruction.length} userLen=${prompt.userMessage.length}"
            )
            return GptResult.Disabled(localFallback)
        }

        if (!config.hasApiKey) {
            logInfo("AI_GPT_NOT_CONFIGURED", "contextTag=${prompt.contextTag} model=${config.model}")
            return failure(
                type = GptErrorType.NOT_CONFIGURED,
                message = "GPT: falta configurar API key.",
                fallbackText = localFallback,
                model = config.model
            )
        }

        return withContext(Dispatchers.IO) {
            logInfo(
                "AI_GPT_TEST_REQUEST",
                "mode=CONFIGURAR contextTag=${prompt.contextTag} modelRequested=${config.model} " +
                    "inputLength=${prompt.systemInstruction.length + prompt.userMessage.length}"
            )

            val primary = requestModel(
                prompt = prompt,
                config = config,
                model = config.model,
                fallbackUsed = false
            )
            if (primary is ModelAttempt.Success) {
                return@withContext primary.result
            }

            val primaryFailure = primary as ModelAttempt.Failure
            if (primaryFailure.recoverable && config.fallbackModel.isNotBlank() && config.fallbackModel != config.model) {
                val fallback = requestModel(
                    prompt = prompt,
                    config = config,
                    model = config.fallbackModel,
                    fallbackUsed = true
                )
                if (fallback is ModelAttempt.Success) {
                    return@withContext fallback.result
                }
                return@withContext (fallback as ModelAttempt.Failure).result
            }

            primaryFailure.result
        }
    }

    override suspend fun generateStructured(input: SevenInputContract): StructuredGptResult {
        val config = configProvider()

        if (!config.enabled) {
            logInfo(
                "GPT_STRUCTURED_DISABLED",
                "model=${config.model} contextTag=${input.contextTag} intent=${input.intent} " +
                    "topic=${input.topic} maxWords=${input.maxWords}"
            )
            return structuredFallbackResult(
                input = input,
                model = config.model,
                errorType = GptErrorType.NOT_ENABLED,
                safeMessage = "GPT desactivado.",
                reason = SevenBlockedReason.UNKNOWN
            )
        }

        if (!config.hasApiKey) {
            logInfo("GPT_STRUCTURED_NOT_CONFIGURED", "contextTag=${input.contextTag} model=${config.model}")
            return structuredFallbackResult(
                input = input,
                model = config.model,
                errorType = GptErrorType.NOT_CONFIGURED,
                safeMessage = "GPT: falta configurar API key.",
                reason = SevenBlockedReason.UNKNOWN
            )
        }

        return withContext(Dispatchers.IO) {
            val userMessage = SevenStructuredPrompt.buildStructuredUserMessage(input)
            logInfo(
                "GPT_STRUCTURED_REQUEST",
                "model=${config.model} contextTag=${input.contextTag} intent=${input.intent} topic=${input.topic} " +
                    "maxWords=${input.maxWords} systemLen=${SevenStructuredPrompt.SYSTEM_INSTRUCTION.length} " +
                    "userLen=${userMessage.length}"
            )

            val primary = requestStructuredModel(
                input = input,
                userMessage = userMessage,
                config = config,
                model = config.model,
                fallbackModelUsed = false
            )
            if (primary is StructuredAttempt.Success) {
                return@withContext primary.result
            }

            val primaryFailure = primary as StructuredAttempt.Failure
            if (primaryFailure.recoverable && config.fallbackModel.isNotBlank() && config.fallbackModel != config.model) {
                val fallback = requestStructuredModel(
                    input = input,
                    userMessage = userMessage,
                    config = config,
                    model = config.fallbackModel,
                    fallbackModelUsed = true
                )
                if (fallback is StructuredAttempt.Success) {
                    return@withContext fallback.result
                }
                return@withContext (fallback as StructuredAttempt.Failure).result
            }

            primaryFailure.result
        }
    }

    private fun requestStructuredModel(
        input: SevenInputContract,
        userMessage: String,
        config: GptConfig,
        model: String,
        fallbackModelUsed: Boolean
    ): StructuredAttempt {
        val start = System.nanoTime()
        return try {
            executeStructuredRequest(
                input = input,
                userMessage = userMessage,
                config = config,
                model = model,
                includeTemperature = true,
                fallbackModelUsed = fallbackModelUsed,
                start = start
            )
        } catch (e: SocketTimeoutException) {
            val result = structuredFallbackResult(
                input = input,
                model = model,
                errorType = GptErrorType.TIMEOUT,
                safeMessage = "GPT: tiempo de espera agotado.",
                reason = SevenBlockedReason.UNKNOWN
            )
            logWarn("GPT_STRUCTURED_FAILED", "contextTag=${input.contextTag} model=$model errorType=TIMEOUT")
            StructuredAttempt.Failure(result, recoverable = true)
        } catch (e: IOException) {
            val result = structuredFallbackResult(
                input = input,
                model = model,
                errorType = GptErrorType.NO_NETWORK,
                safeMessage = "GPT: sin conexion disponible.",
                reason = SevenBlockedReason.UNKNOWN
            )
            logWarn("GPT_STRUCTURED_FAILED", "contextTag=${input.contextTag} model=$model errorType=NO_NETWORK")
            StructuredAttempt.Failure(result, recoverable = false)
        } catch (e: Exception) {
            val result = structuredFallbackResult(
                input = input,
                model = model,
                errorType = GptErrorType.UNKNOWN,
                safeMessage = "GPT: error inesperado.",
                reason = SevenBlockedReason.UNKNOWN
            )
            logWarn("GPT_STRUCTURED_FAILED", "contextTag=${input.contextTag} model=$model errorType=UNKNOWN")
            StructuredAttempt.Failure(result, recoverable = false)
        }
    }

    private fun executeStructuredRequest(
        input: SevenInputContract,
        userMessage: String,
        config: GptConfig,
        model: String,
        includeTemperature: Boolean,
        fallbackModelUsed: Boolean,
        start: Long
    ): StructuredAttempt {
        val request = Request.Builder()
            .url(GptConfig.ENDPOINT)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(buildStructuredRequestJson(input, userMessage, config, model, includeTemperature).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        clientProvider(config).newCall(request).execute().use { response ->
            val latencyMs = (System.nanoTime() - start) / 1_000_000
            logInfo(
                "GPT_STRUCTURED_HTTP",
                "contextTag=${input.contextTag} model=$model code=${response.code} latencyMs=$latencyMs " +
                    "userLen=${userMessage.length} maxTokens=${config.maxOutputTokens}"
            )

            if (response.code == 400 && includeTemperature) {
                logWarn("GPT_STRUCTURED_TEMPERATURE_RETRY", "contextTag=${input.contextTag} model=$model")
                return executeStructuredRequest(
                    input = input,
                    userMessage = userMessage,
                    config = config,
                    model = model,
                    includeTemperature = false,
                    fallbackModelUsed = fallbackModelUsed,
                    start = start
                )
            }

            if (!response.isSuccessful) {
                val errorType = errorTypeForHttp(response.code)
                val result = structuredFallbackResult(
                    input = input,
                    model = model,
                    errorType = errorType,
                    safeMessage = safeMessageFor(errorType),
                    reason = SevenBlockedReason.UNKNOWN,
                    latencyMs = latencyMs
                )
                logWarn(
                    "GPT_STRUCTURED_FAILED",
                    "contextTag=${input.contextTag} model=$model code=${response.code} errorType=$errorType " +
                        "fallbackUsed=true"
                )
                return StructuredAttempt.Failure(result, recoverable = isRecoverable(errorType))
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                val result = structuredFallbackResult(
                    input = input,
                    model = model,
                    errorType = GptErrorType.EMPTY_RESPONSE,
                    safeMessage = "GPT: respuesta vacia.",
                    reason = SevenBlockedReason.UNKNOWN,
                    latencyMs = latencyMs
                )
                logWarn("GPT_STRUCTURED_EMPTY_RESPONSE", "contextTag=${input.contextTag} model=$model bodyLen=0")
                return StructuredAttempt.Failure(result, recoverable = true)
            }

            val rawText = try {
                GptResponseParser.extractOutputText(body)
            } catch (e: SevenParseException) {
                val result = structuredFallbackResult(
                    input = input,
                    model = model,
                    errorType = GptErrorType.PARSE_ERROR,
                    safeMessage = "GPT: respuesta no interpretable.",
                    reason = SevenBlockedReason.INVALID_CONTEXT,
                    latencyMs = latencyMs
                )
                logWarn(
                    "GPT_STRUCTURED_PARSE_ERROR",
                    "contextTag=${input.contextTag} model=$model bodyLen=${body.length} errorType=${e.javaClass.simpleName} " +
                        "outputTextLen=0 parseSuccess=false fallbackUsed=true"
                )
                return StructuredAttempt.Failure(result, recoverable = false)
            }

            val parsed = try {
                GptResponseParser.parseSevenResponseText(rawText)
            } catch (e: SevenParseException) {
                val result = structuredFallbackResult(
                    input = input,
                    model = model,
                    errorType = GptErrorType.PARSE_ERROR,
                    safeMessage = "GPT: respuesta no interpretable.",
                    reason = SevenBlockedReason.INVALID_CONTEXT,
                    latencyMs = latencyMs,
                    rawTextLength = rawText.length
                )
                logWarn(
                    "GPT_STRUCTURED_PARSE_ERROR",
                    "contextTag=${input.contextTag} model=$model bodyLen=${body.length} errorType=${e.javaClass.simpleName} " +
                        "outputTextLen=${rawText.length} parseSuccess=false fallbackUsed=true"
                )
                return StructuredAttempt.Failure(result, recoverable = false)
            }

            val validation = SevenResponseValidator.validate(parsed, input)
            if (!validation.isValid) {
                val result = structuredFallbackResult(
                    input = input,
                    model = model,
                    errorType = GptErrorType.PARSE_ERROR,
                    safeMessage = "GPT: respuesta no validada localmente.",
                    reason = validation.blockedReason,
                    latencyMs = latencyMs,
                    rawTextLength = rawText.length
                )
                logWarn(
                    "GPT_STRUCTURED_VALIDATION_FAILED",
                    "contextTag=${input.contextTag} model=$model parseSuccess=true validationSuccess=false " +
                        "failedRules=${validation.failedRules.size} outputTextLen=${rawText.length} fallbackUsed=true"
                )
                return StructuredAttempt.Failure(result, recoverable = false)
            }

            logInfo(
                "GPT_STRUCTURED_SUCCESS",
                "contextTag=${input.contextTag} modelUsed=$model latencyMs=$latencyMs parseSuccess=true " +
                    "validationSuccess=true failedRules=0 outputTextLen=${rawText.length} textLen=${parsed.visibleText.length} " +
                    "fallbackUsed=$fallbackModelUsed"
            )
            return StructuredAttempt.Success(
                StructuredGptResult.Success(
                    response = parsed,
                    validation = validation,
                    modelUsed = model,
                    latencyMs = latencyMs,
                    fallbackUsed = fallbackModelUsed,
                    rawTextLength = rawText.length
                )
            )
        }
    }

    private fun structuredFallbackResult(
        input: SevenInputContract,
        model: String,
        errorType: GptErrorType,
        safeMessage: String,
        reason: SevenBlockedReason,
        latencyMs: Long = 0L,
        rawTextLength: Int = 0
    ): StructuredGptResult.Fallback {
        val fallback = GptLocalFallback.structuredFallback(input, reason)
        val validation = SevenResponseValidator.validate(fallback, input)
        return StructuredGptResult.Fallback(
            response = fallback,
            validation = validation.copy(effectiveSafeForTts = validation.isValid),
            modelUsed = model,
            latencyMs = latencyMs,
            fallbackUsed = true,
            errorType = errorType,
            safeMessage = safeMessage,
            rawTextLength = rawTextLength
        )
    }

    private fun requestModel(
        prompt: GptPrompt,
        config: GptConfig,
        model: String,
        fallbackUsed: Boolean
    ): ModelAttempt {
        val start = System.nanoTime()
        return try {
            executeRequest(
                prompt = prompt,
                config = config,
                model = model,
                includeTemperature = true,
                fallbackUsed = fallbackUsed,
                start = start
            )
        } catch (e: SocketTimeoutException) {
            val result = failure(
                type = GptErrorType.TIMEOUT,
                message = "GPT: tiempo de espera agotado.",
                fallbackText = GptLocalFallback.phraseFor(prompt.contextTag),
                model = model
            )
            logWarn("AI_GPT_TEST_FAILED", "contextTag=${prompt.contextTag} model=$model errorType=TIMEOUT")
            ModelAttempt.Failure(result, recoverable = true)
        } catch (e: IOException) {
            val result = failure(
                type = GptErrorType.NO_NETWORK,
                message = "GPT: sin conexion disponible.",
                fallbackText = GptLocalFallback.phraseFor(prompt.contextTag),
                model = model
            )
            logWarn("AI_GPT_TEST_FAILED", "contextTag=${prompt.contextTag} model=$model errorType=NO_NETWORK")
            ModelAttempt.Failure(result, recoverable = false)
        } catch (e: Exception) {
            val result = failure(
                type = GptErrorType.UNKNOWN,
                message = "GPT: error inesperado.",
                fallbackText = GptLocalFallback.phraseFor(prompt.contextTag),
                model = model
            )
            logWarn("AI_GPT_TEST_FAILED", "contextTag=${prompt.contextTag} model=$model errorType=UNKNOWN")
            ModelAttempt.Failure(result, recoverable = false)
        }
    }

    private fun executeRequest(
        prompt: GptPrompt,
        config: GptConfig,
        model: String,
        includeTemperature: Boolean,
        fallbackUsed: Boolean,
        start: Long
    ): ModelAttempt {
        val request = Request.Builder()
            .url(GptConfig.ENDPOINT)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(buildRequestJson(prompt, config, model, includeTemperature).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        clientProvider(config).newCall(request).execute().use { response ->
            val latencyMs = (System.nanoTime() - start) / 1_000_000
            logInfo(
                "AI_GPT_HTTP",
                "contextTag=${prompt.contextTag} model=$model code=${response.code} latencyMs=$latencyMs " +
                    "systemLen=${prompt.systemInstruction.length} userLen=${prompt.userMessage.length} " +
                    "maxTokens=${config.maxOutputTokens}"
            )

            if (response.code == 400 && includeTemperature) {
                logWarn("AI_GPT_TEMPERATURE_RETRY", "contextTag=${prompt.contextTag} model=$model")
                return executeRequest(
                    prompt = prompt,
                    config = config,
                    model = model,
                    includeTemperature = false,
                    fallbackUsed = fallbackUsed,
                    start = start
                )
            }

            if (!response.isSuccessful) {
                val errorType = errorTypeForHttp(response.code)
                val result = failure(
                    type = errorType,
                    message = safeMessageFor(errorType),
                    fallbackText = GptLocalFallback.phraseFor(prompt.contextTag),
                    model = model
                )
                logWarn(
                    "AI_GPT_TEST_FAILED",
                    "contextTag=${prompt.contextTag} model=$model code=${response.code} errorType=$errorType"
                )
                return ModelAttempt.Failure(result, recoverable = isRecoverable(errorType))
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                val result = failure(
                    type = GptErrorType.EMPTY_RESPONSE,
                    message = "GPT: respuesta vacia.",
                    fallbackText = GptLocalFallback.phraseFor(prompt.contextTag),
                    model = model
                )
                logWarn("AI_GPT_EMPTY_RESPONSE", "contextTag=${prompt.contextTag} model=$model bodyLen=0")
                return ModelAttempt.Failure(result, recoverable = true)
            }

            val parseOutcome = parseResponse(body)
            return when (parseOutcome) {
                is ParseOutcome.Text -> {
                    val text = parseOutcome.text.trim()
                    logInfo(
                        "AI_GPT_TEST_SUCCESS",
                        "mode=CONFIGURAR contextTag=${prompt.contextTag} modelUsed=$model " +
                            "fallbackUsed=$fallbackUsed latencyMs=$latencyMs outputLength=${text.length}"
                    )
                    ModelAttempt.Success(
                        GptResult.Success(
                            text = text,
                            modelUsed = model,
                            latencyMs = latencyMs,
                            fallbackUsed = fallbackUsed
                        )
                    )
                }
                ParseOutcome.Empty -> {
                    val result = failure(
                        type = GptErrorType.EMPTY_RESPONSE,
                        message = "GPT: respuesta sin texto.",
                        fallbackText = GptLocalFallback.phraseFor(prompt.contextTag),
                        model = model
                    )
                    logWarn("AI_GPT_EMPTY_RESPONSE", "contextTag=${prompt.contextTag} model=$model bodyLen=${body.length}")
                    ModelAttempt.Failure(result, recoverable = true)
                }
                ParseOutcome.InvalidJson -> {
                    val result = failure(
                        type = GptErrorType.PARSE_ERROR,
                        message = "GPT: respuesta no interpretable.",
                        fallbackText = GptLocalFallback.phraseFor(prompt.contextTag),
                        model = model
                    )
                    logWarn("AI_GPT_PARSE_ERROR", "contextTag=${prompt.contextTag} model=$model bodyLen=${body.length}")
                    ModelAttempt.Failure(result, recoverable = false)
                }
            }
        }
    }

    private fun failure(
        type: GptErrorType,
        message: String,
        fallbackText: String,
        model: String
    ): GptResult.Failure = GptResult.Failure(
        errorType = type,
        safeMessage = message,
        fallbackText = fallbackText,
        modelAttempted = model
    )

    private fun logInfo(eventType: String, message: String) {
        logSink("d", "eventType=$eventType $message")
    }

    private fun logWarn(eventType: String, message: String) {
        logSink("w", "eventType=$eventType $message")
    }

    private sealed interface ModelAttempt {
        data class Success(val result: GptResult.Success) : ModelAttempt
        data class Failure(val result: GptResult.Failure, val recoverable: Boolean) : ModelAttempt
    }

    private sealed interface StructuredAttempt {
        data class Success(val result: StructuredGptResult.Success) : StructuredAttempt
        data class Failure(val result: StructuredGptResult.Fallback, val recoverable: Boolean) : StructuredAttempt
    }

    companion object {
        private const val TAG = "GptClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(config: GptConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
                .build()

        private fun safeLog(level: String, message: String) {
            runCatching {
                if (level == "w") {
                    Log.w(TAG, message)
                } else {
                    Log.d(TAG, message)
                }
            }
        }

        internal fun buildRequestJson(
            prompt: GptPrompt,
            config: GptConfig,
            model: String,
            includeTemperature: Boolean = true
        ): String {
            val input = JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt.systemInstruction))
                .put(JSONObject().put("role", "user").put("content", prompt.userMessage))

            return JSONObject().apply {
                put("model", model)
                put("input", input)
                put("max_output_tokens", config.maxOutputTokens)
                if (includeTemperature) {
                    put("temperature", config.temperature.toDouble())
                }
            }.toString()
        }

        internal fun buildStructuredRequestJson(
            input: SevenInputContract,
            userMessage: String = SevenStructuredPrompt.buildStructuredUserMessage(input),
            config: GptConfig,
            model: String,
            includeTemperature: Boolean = true
        ): String {
            val format = JSONObject()
                .put("type", "json_schema")
                .put("name", SevenResponseSchema.NAME)
                .put("strict", true)
                .put("schema", SevenResponseSchema.asJsonObject())
            val inputArray = JSONArray()
                .put(JSONObject().put("role", "system").put("content", SevenStructuredPrompt.SYSTEM_INSTRUCTION))
                .put(JSONObject().put("role", "user").put("content", userMessage))

            return JSONObject().apply {
                put("model", model)
                put("input", inputArray)
                put("max_output_tokens", config.maxOutputTokens)
                put("text", JSONObject().put("format", format))
                if (includeTemperature) {
                    put("temperature", config.temperature.toDouble())
                }
            }.toString()
        }

        internal fun parseResponseText(body: String): String? =
            when (val result = parseResponse(body)) {
                is ParseOutcome.Text -> result.text
                else -> null
            }

        private fun parseResponse(body: String): ParseOutcome {
            val root = try {
                JSONObject(body)
            } catch (e: JSONException) {
                return ParseOutcome.InvalidJson
            }

            root.optString("output_text")
                .takeIf { it.isNotBlank() }
                ?.let { return ParseOutcome.Text(it) }

            val collected = StringBuilder()
            val output = root.optJSONArray("output")
            if (output != null) {
                for (i in 0 until output.length()) {
                    val outputItem = output.optJSONObject(i) ?: continue
                    val content = outputItem.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val contentItem = content.optJSONObject(j) ?: continue
                        val text = contentItem.optString("text")
                            .ifBlank { contentItem.optString("value") }
                        if (text.isNotBlank()) {
                            if (collected.isNotEmpty()) collected.append('\n')
                            collected.append(text)
                        }
                    }
                }
            }

            return collected.toString()
                .takeIf { it.isNotBlank() }
                ?.let { ParseOutcome.Text(it) }
                ?: ParseOutcome.Empty
        }

        private fun errorTypeForHttp(code: Int): GptErrorType = when (code) {
            401 -> GptErrorType.HTTP_401
            403 -> GptErrorType.HTTP_403
            429 -> GptErrorType.HTTP_429
            else -> GptErrorType.HTTP_ERROR
        }

        private fun safeMessageFor(type: GptErrorType): String = when (type) {
            GptErrorType.HTTP_401 -> "GPT: API key no autorizada."
            GptErrorType.HTTP_403 -> "GPT: permisos insuficientes o acceso denegado."
            GptErrorType.HTTP_429 -> "GPT: limite de cuota alcanzado. Reintenta mas tarde."
            GptErrorType.HTTP_ERROR -> "GPT: error HTTP del servicio."
            else -> "GPT: error seguro."
        }

        private fun isRecoverable(type: GptErrorType): Boolean =
            type == GptErrorType.HTTP_429 ||
                type == GptErrorType.HTTP_ERROR ||
                type == GptErrorType.TIMEOUT ||
                type == GptErrorType.EMPTY_RESPONSE
    }
}

private sealed interface ParseOutcome {
    data class Text(val text: String) : ParseOutcome
    data object Empty : ParseOutcome
    data object InvalidJson : ParseOutcome
}
