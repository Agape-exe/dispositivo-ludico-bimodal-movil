package com.taller.app.gpt

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class GptClientImplTest {

    private val prompt = GptPrompt(
        systemInstruction = "Sistema de prueba.",
        userMessage = "Mensaje artificial fijo.",
        contextTag = "TEST_SETTINGS"
    )

    private fun config(
        enabled: Boolean = true,
        apiKey: String = "test-key",
        model: String = "gpt-5.4-mini",
        fallbackModel: String = "gpt-5.4-nano",
        localFallbackEnabled: Boolean = true,
        maxOutputTokens: Int = 220,
        timeoutMs: Long = 12_000L
    ) = GptConfig(
        apiKey = apiKey,
        model = model,
        fallbackModel = fallbackModel,
        maxOutputTokens = maxOutputTokens,
        timeoutMs = timeoutMs,
        temperature = 0.4f,
        enabled = enabled,
        localFallbackEnabled = localFallbackEnabled
    )

    @Test
    fun fromBuild_usesDefaultGptValues() {
        val config = GptConfig.fromBuild()

        assertFalse(config.enabled)
        assertEquals("gpt-5.4-mini", config.model)
        assertEquals("gpt-5.4-nano", config.fallbackModel)
        assertEquals(220, config.maxOutputTokens)
        assertEquals(12_000L, config.timeoutMs)
        assertEquals(0.4f, config.temperature)
    }

    @Test
    fun disabled_returnsLocalTextWithoutNetwork() = runBlocking {
        var calls = 0
        val client = GptClientImpl(
            configProvider = { config(enabled = false) },
            clientProvider = { respondingClient { calls++; successResponse(it, outputText = "remoto") } }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Disabled)
        assertEquals(0, calls)
        assertEquals(GptLocalFallback.phraseFor("TEST_SETTINGS"), (result as GptResult.Disabled).fallbackText)
    }

    @Test
    fun missingApiKey_returnsNotConfiguredWithoutNetwork() = runBlocking {
        var calls = 0
        val client = GptClientImpl(
            configProvider = { config(apiKey = "") },
            clientProvider = { respondingClient { calls++; successResponse(it, outputText = "remoto") } }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Failure)
        assertEquals(GptErrorType.NOT_CONFIGURED, (result as GptResult.Failure).errorType)
        assertEquals(0, calls)
    }

    @Test
    fun missingApiKeyWithLocalFallbackDisabled_returnsSafeFailureWithoutLocalText() = runBlocking {
        var calls = 0
        val client = GptClientImpl(
            configProvider = { config(apiKey = "", localFallbackEnabled = false) },
            clientProvider = { respondingClient { calls++; successResponse(it, outputText = "remoto") } }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Failure)
        assertEquals(GptErrorType.NOT_CONFIGURED, (result as GptResult.Failure).errorType)
        assertEquals("", result.fallbackText)
        assertEquals(0, calls)
    }

    @Test
    fun request_usesResponsesEndpointModelTokensAndSystemUserInput() = runBlocking {
        var capturedUrl = ""
        var capturedBody = ""
        val client = GptClientImpl(
            configProvider = { config() },
            clientProvider = {
                respondingClient { request ->
                    capturedUrl = request.url.toString()
                    capturedBody = request.bodyString()
                    successResponse(request, outputText = "Hola.")
                }
            }
        )

        val result = client.generate(prompt)
        val json = JSONObject(capturedBody)
        val input = json.getJSONArray("input")

        assertTrue(result is GptResult.Success)
        assertEquals(GptConfig.ENDPOINT, capturedUrl)
        assertEquals("gpt-5.4-mini", json.getString("model"))
        assertEquals(220, json.getInt("max_output_tokens"))
        assertEquals("system", input.getJSONObject(0).getString("role"))
        assertEquals(prompt.systemInstruction, input.getJSONObject(0).getString("content"))
        assertEquals("user", input.getJSONObject(1).getString("role"))
        assertEquals(prompt.userMessage, input.getJSONObject(1).getString("content"))
    }

    @Test
    fun request_usesRuntimeModelTokensAndTimeout() = runBlocking {
        var capturedBody = ""
        var capturedTimeoutMs = 0L
        val client = GptClientImpl(
            configProvider = {
                config(
                    model = "gpt-5.4",
                    maxOutputTokens = 333,
                    timeoutMs = 7_000L
                )
            },
            clientProvider = { config ->
                capturedTimeoutMs = config.timeoutMs
                respondingClient { request ->
                    capturedBody = request.bodyString()
                    successResponse(request, outputText = "Hola.")
                }
            }
        )

        val result = client.generate(prompt)
        val json = JSONObject(capturedBody)

        assertTrue(result is GptResult.Success)
        assertEquals("gpt-5.4", json.getString("model"))
        assertEquals(333, json.getInt("max_output_tokens"))
        assertEquals(7_000L, capturedTimeoutMs)
    }

    @Test
    fun safeLogs_doNotContainApiKey() = runBlocking {
        val logs = mutableListOf<String>()
        val client = GptClientImpl(
            configProvider = { config(apiKey = "secret-key-marker") },
            clientProvider = { respondingClient { successResponse(it, outputText = "Hola.") } },
            logSink = { _, message -> logs += message }
        )

        client.generate(prompt)

        assertTrue(logs.isNotEmpty())
        assertFalse(logs.joinToString("\n").contains("secret-key-marker"))
        assertFalse(logs.joinToString("\n").contains("Bearer"))
    }

    @Test
    fun parseResponseText_supportsTopLevelOutputText() {
        val body = """{"output_text":"Hola desde Seven."}"""

        assertEquals("Hola desde Seven.", GptClientImpl.parseResponseText(body))
    }

    @Test
    fun parseResponseText_supportsOutputContentTextAndWalksAllItems() {
        val body = """
            {
              "output": [
                {"content": [{"type": "metadata"}]},
                {"content": [
                  {"type": "output_text", "text": "Hola"},
                  {"type": "output_text", "text": "explorador"}
                ]}
              ]
            }
        """.trimIndent()

        assertEquals("Hola\nexplorador", GptClientImpl.parseResponseText(body))
    }

    @Test
    fun parseResponseText_returnsNullForNoTextOrInvalidJson() {
        assertNull(GptClientImpl.parseResponseText("""{"output":[]}"""))
        assertNull(GptClientImpl.parseResponseText("no-json"))
    }

    @Test
    fun http401_returnsTypedFailureWithoutFallbackModel() = runBlocking {
        var calls = 0
        val client = GptClientImpl(
            configProvider = { config() },
            clientProvider = {
                respondingClient { request ->
                    calls++
                    jsonResponse(request, code = 401, body = """{"error":"unauthorized"}""")
                }
            }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Failure)
        assertEquals(GptErrorType.HTTP_401, (result as GptResult.Failure).errorType)
        assertEquals(1, calls)
    }

    @Test
    fun http403_returnsTypedFailureWithoutFallbackModel() = runBlocking {
        var calls = 0
        val client = GptClientImpl(
            configProvider = { config() },
            clientProvider = {
                respondingClient { request ->
                    calls++
                    jsonResponse(request, code = 403, body = """{"error":"forbidden"}""")
                }
            }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Failure)
        assertEquals(GptErrorType.HTTP_403, (result as GptResult.Failure).errorType)
        assertEquals(1, calls)
    }

    @Test
    fun http429_triesFallbackModelOnce() = runBlocking {
        val models = mutableListOf<String>()
        val client = GptClientImpl(
            configProvider = { config() },
            clientProvider = {
                respondingClient { request ->
                    val model = JSONObject(request.bodyString()).getString("model")
                    models += model
                    if (models.size == 1) {
                        jsonResponse(request, code = 429, body = """{"error":"quota"}""")
                    } else {
                        successResponse(request, outputText = "Fallback listo.")
                    }
                }
            }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Success)
        assertEquals(listOf("gpt-5.4-mini", "gpt-5.4-nano"), models)
        assertTrue((result as GptResult.Success).fallbackUsed)
    }

    @Test
    fun http5xx_triesFallbackModelOnce() = runBlocking {
        val models = mutableListOf<String>()
        val client = GptClientImpl(
            configProvider = { config() },
            clientProvider = {
                respondingClient { request ->
                    val model = JSONObject(request.bodyString()).getString("model")
                    models += model
                    if (models.size == 1) {
                        jsonResponse(request, code = 500, body = """{"error":"server"}""")
                    } else {
                        successResponse(request, outputText = "Fallback listo.")
                    }
                }
            }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Success)
        assertEquals(listOf("gpt-5.4-mini", "gpt-5.4-nano"), models)
    }

    @Test
    fun emptyResponse_returnsEmptyResponseFailureAfterFallbackAttempt() = runBlocking {
        val client = GptClientImpl(
            configProvider = { config(fallbackModel = "gpt-5.4-mini") },
            clientProvider = { respondingClient { jsonResponse(it, code = 200, body = """{"output":[]}""") } }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Failure)
        assertEquals(GptErrorType.EMPTY_RESPONSE, (result as GptResult.Failure).errorType)
    }

    @Test
    fun timeout_returnsSafeError() = runBlocking {
        val client = GptClientImpl(
            configProvider = { config(fallbackModel = "gpt-5.4-mini") },
            clientProvider = { throwingClient(SocketTimeoutException("timeout")) }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Failure)
        assertEquals(GptErrorType.TIMEOUT, (result as GptResult.Failure).errorType)
    }

    @Test
    fun ioException_returnsNoNetwork() = runBlocking {
        val client = GptClientImpl(
            configProvider = { config() },
            clientProvider = { throwingClient(IOException("offline")) }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Failure)
        assertEquals(GptErrorType.NO_NETWORK, (result as GptResult.Failure).errorType)
    }

    @Test
    fun temperatureHttp400_retriesSameModelWithoutTemperature() = runBlocking {
        val temperatures = mutableListOf<Boolean>()
        val client = GptClientImpl(
            configProvider = { config() },
            clientProvider = {
                respondingClient { request ->
                    val hasTemperature = JSONObject(request.bodyString()).has("temperature")
                    temperatures += hasTemperature
                    if (hasTemperature) {
                        jsonResponse(request, code = 400, body = """{"error":"unsupported temperature"}""")
                    } else {
                        successResponse(request, outputText = "Sin temperature.")
                    }
                }
            }
        )

        val result = client.generate(prompt)

        assertTrue(result is GptResult.Success)
        assertEquals(listOf(true, false), temperatures)
    }

    @Test
    fun structuredRequest_includesJsonSchemaFormat() {
        val json = JSONObject(
            GptClientImpl.buildStructuredRequestJson(
                input = structuredInput(),
                config = config(),
                model = "gpt-5.4-mini"
            )
        )
        val format = json.getJSONObject("text").getJSONObject("format")

        assertEquals("json_schema", format.getString("type"))
        assertEquals("SevenResponse", format.getString("name"))
        assertTrue(format.getBoolean("strict"))
        assertFalse(format.getJSONObject("schema").getBoolean("additionalProperties"))
    }

    @Test
    fun parser_parsesValidGreetingJson() {
        val response = GptResponseParser.parseApiResponse(
            """{"output_text":${JSONObject.quote(validSevenJson())}}"""
        )

        assertEquals(SevenIntent.GREETING, response.intent)
        assertEquals(SevenResponseType.GREET, response.responseType)
        assertEquals("Hola, explorador!", response.visibleText)
    }

    @Test(expected = SevenParseException::class)
    fun parser_failsWhenRequiredFieldIsMissing() {
        GptResponseParser.parseApiResponse(
            """{"output_text":${JSONObject.quote(validSevenJson().replace(""""validationNotes":"ok"""", """"notes":"ok""""))}}"""
        )
    }

    @Test(expected = SevenParseException::class)
    fun parser_failsForPlainText() {
        GptResponseParser.parseApiResponse("""{"output_text":"Hola sin JSON"}""")
    }

    @Test
    fun validator_rejectsAiModelTerms() {
        val validation = SevenResponseValidator.validate(
            validResponse(visibleText = "Soy un modelo de IA."),
            structuredInput()
        )

        assertFalse(validation.effectiveSafeForTts)
        assertTrue(validation.failedRules.contains("V10_NO_AI_OR_SYSTEM_TERMS"))
    }

    @Test
    fun validator_rejectsSurveillanceTerms() {
        val validation = SevenResponseValidator.validate(
            validResponse(visibleText = "Te veo por la camara."),
            structuredInput()
        )

        assertFalse(validation.effectiveSafeForTts)
        assertTrue(validation.failedRules.contains("V11_NO_SURVEILLANCE_TERMS"))
    }

    @Test
    fun validator_rejectsPersonalDataRequests() {
        val validation = SevenResponseValidator.validate(
            validResponse(visibleText = "Como te llamas?"),
            structuredInput()
        )

        assertFalse(validation.effectiveSafeForTts)
        assertTrue(validation.failedRules.contains("V12_NO_PERSONAL_DATA_REQUEST"))
    }

    @Test
    fun validator_rejectsFinalAnswerWhenNotAllowed() {
        val input = structuredInput(
            intent = "feedback_incorrect",
            localEvaluation = "incorrect",
            canGiveHint = true,
            answerTokens = listOf("guau")
        )
        val validation = SevenResponseValidator.validate(
            validResponse(
                intent = SevenIntent.FEEDBACK_INCORRECT,
                responseType = SevenResponseType.HINT,
                visibleText = "La respuesta es guau.",
                localEvaluation = SevenLocalEvaluation.INCORRECT
            ),
            input
        )

        assertFalse(validation.effectiveSafeForTts)
        assertTrue(validation.failedRules.contains("V13_NO_FINAL_ANSWER"))
    }

    @Test
    fun validator_acceptsBriefCorrectFeedback() {
        val input = structuredInput(intent = "feedback_correct", localEvaluation = "correct")
        val validation = SevenResponseValidator.validate(
            validResponse(
                intent = SevenIntent.FEEDBACK_CORRECT,
                responseType = SevenResponseType.PRAISE,
                visibleText = "Muy bien, explorador!",
                localEvaluation = SevenLocalEvaluation.CORRECT
            ),
            input
        )

        assertTrue(validation.effectiveSafeForTts)
    }

    @Test
    fun validator_acceptsNotInterpretableWithRepeat() {
        val input = structuredInput(intent = "not_interpretable", localEvaluation = "not_interpretable")
        val validation = SevenResponseValidator.validate(
            validResponse(
                intent = SevenIntent.NOT_INTERPRETABLE,
                responseType = SevenResponseType.ASK_REPEAT,
                visibleText = "Mis antenas no entendieron. Puedes repetirlo?",
                localEvaluation = SevenLocalEvaluation.NOT_INTERPRETABLE,
                shouldAskRepeat = true
            ),
            input
        )

        assertTrue(validation.effectiveSafeForTts)
    }

    @Test
    fun validator_rejectsNotInterpretableWithoutRepeat() {
        val input = structuredInput(intent = "not_interpretable", localEvaluation = "not_interpretable")
        val validation = SevenResponseValidator.validate(
            validResponse(
                intent = SevenIntent.NOT_INTERPRETABLE,
                responseType = SevenResponseType.ASK_REPEAT,
                visibleText = "Intentemos otra vez.",
                localEvaluation = SevenLocalEvaluation.NOT_INTERPRETABLE,
                shouldAskRepeat = false
            ),
            input
        )

        assertFalse(validation.effectiveSafeForTts)
        assertTrue(validation.failedRules.contains("V15_NOT_INTERPRETABLE_ASK_REPEAT"))
    }

    @Test
    fun validator_requiresRecaptureFlag() {
        val validation = SevenResponseValidator.validate(
            validResponse(
                intent = SevenIntent.RECAPTURE_ATTENTION,
                responseType = SevenResponseType.RECAPTURE,
                visibleText = "Ey, explorador! Sigamos.",
                shouldRecaptureAttention = false
            ),
            structuredInput(intent = "recapture_attention")
        )

        assertFalse(validation.effectiveSafeForTts)
        assertTrue(validation.failedRules.contains("V16_RECAPTURE_FLAG"))
    }

    @Test
    fun structuredDisabled_returnsLocalFallbackWithoutNetwork() = runBlocking {
        var calls = 0
        val client = GptClientImpl(
            configProvider = { config(enabled = false) },
            clientProvider = { respondingClient { calls++; successStructuredResponse(it, validSevenJson()) } }
        )

        val result = client.generateStructured(structuredInput())

        assertTrue(result is StructuredGptResult.Fallback)
        assertEquals(0, calls)
        assertTrue(result.fallbackUsed)
        assertTrue(result.validation.effectiveSafeForTts)
    }

    @Test
    fun structuredInvalidJson_returnsLocalFallback() = runBlocking {
        val client = GptClientImpl(
            configProvider = { config(fallbackModel = "gpt-5.4-mini") },
            clientProvider = { respondingClient { jsonResponse(it, code = 200, body = """{"output_text":"texto plano"}""") } }
        )

        val result = client.generateStructured(structuredInput())

        assertTrue(result is StructuredGptResult.Fallback)
        assertEquals(GptErrorType.PARSE_ERROR, result.errorType)
        assertTrue(result.response.fallbackUsed)
    }

    @Test
    fun structuredFailureWithLocalFallbackDisabled_doesNotCrashOrUseLocalFallback() = runBlocking {
        val client = GptClientImpl(
            configProvider = { config(localFallbackEnabled = false, fallbackModel = "gpt-5.4-mini") },
            clientProvider = { respondingClient { jsonResponse(it, code = 200, body = """{"output_text":"texto plano"}""") } }
        )

        val result = client.generateStructured(structuredInput())

        assertTrue(result is StructuredGptResult.Fallback)
        assertFalse(result.fallbackUsed)
        assertEquals(GptErrorType.PARSE_ERROR, result.errorType)
        assertEquals("", result.response.visibleText)
    }

    @Test
    fun inputContract_doesNotContainProhibitedFields() {
        val json = JSONObject(structuredInput().toJsonString())

        listOf("childName", "age", "audio", "image", "biometric", "face", "camera", "transcript").forEach {
            assertFalse(json.has(it))
        }
    }

    @Test
    fun safeForTtsFromGptIsNotAuthoritative() {
        val validation = SevenResponseValidator.validate(
            validResponse(visibleText = "Soy un modelo de IA.", safeForTts = true),
            structuredInput()
        )

        assertFalse(validation.effectiveSafeForTts)
    }

    @Test
    fun validator_rejectsBlockedSafetyLevel() {
        val validation = SevenResponseValidator.validate(
            validResponse(
                safetyLevel = SevenSafetyLevel.BLOCKED,
                blockedReason = SevenBlockedReason.UNSAFE_CONTENT
            ),
            structuredInput()
        )

        assertFalse(validation.effectiveSafeForTts)
        assertTrue(validation.failedRules.contains("SAFETY_LEVEL_BLOCKED"))
    }

    private fun respondingClient(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun throwingClient(exception: IOException): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { throw exception }
            .build()

    private fun successResponse(request: okhttp3.Request, outputText: String): Response =
        jsonResponse(request, code = 200, body = """{"output_text":"$outputText"}""")

    private fun successStructuredResponse(request: okhttp3.Request, outputText: String): Response =
        jsonResponse(request, code = 200, body = """{"output_text":${JSONObject.quote(outputText)}}""")

    private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            .build()

    private fun okhttp3.Request.bodyString(): String {
        val body = requireNotNull(body)
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun structuredInput(
        intent: String = "greeting",
        localEvaluation: String = "not_applicable",
        canGiveHint: Boolean = false,
        answerTokens: List<String> = emptyList()
    ) = SevenInputContract(
        intent = intent,
        topic = "Animales",
        questionText = "",
        localEvaluation = localEvaluation,
        attemptsRemaining = 3,
        expectedResponseType = "greet",
        canGiveHint = canGiveHint,
        canGiveFinalAnswer = false,
        maxWords = 25,
        allowedHint = "",
        restrictions = listOf("no_personal_data", "no_ai_mention", "spanish_latin_only"),
        language = "es-419",
        tone = "friendly_curious_alien",
        contextTag = "settings_test",
        answerTokens = answerTokens
    )

    private fun validResponse(
        intent: SevenIntent = SevenIntent.GREETING,
        responseType: SevenResponseType = SevenResponseType.GREET,
        visibleText: String = "Hola, explorador!",
        safetyLevel: SevenSafetyLevel = SevenSafetyLevel.SAFE,
        localEvaluation: SevenLocalEvaluation = SevenLocalEvaluation.NOT_APPLICABLE,
        shouldAskRepeat: Boolean = false,
        shouldRecaptureAttention: Boolean = false,
        safeForTts: Boolean = true,
        blockedReason: SevenBlockedReason = SevenBlockedReason.NONE
    ) = SevenResponse(
        intent = intent,
        responseType = responseType,
        visibleText = visibleText,
        safetyLevel = safetyLevel,
        fallbackUsed = false,
        canGiveHint = false,
        canGiveFinalAnswer = false,
        shouldAskRepeat = shouldAskRepeat,
        shouldRecaptureAttention = shouldRecaptureAttention,
        topic = "Animales",
        localEvaluation = localEvaluation,
        attemptsRemaining = 3,
        maxWords = 25,
        blockedReason = blockedReason,
        safeForTts = safeForTts,
        validationNotes = "ok"
    )

    private fun validSevenJson(): String = JSONObject()
        .put("intent", "greeting")
        .put("responseType", "greet")
        .put("visibleText", "Hola, explorador!")
        .put("safetyLevel", "safe")
        .put("fallbackUsed", false)
        .put("canGiveHint", false)
        .put("canGiveFinalAnswer", false)
        .put("shouldAskRepeat", false)
        .put("shouldRecaptureAttention", false)
        .put("topic", "Animales")
        .put("localEvaluation", "not_applicable")
        .put("attemptsRemaining", 3)
        .put("maxWords", 25)
        .put("blockedReason", "none")
        .put("safeForTts", true)
        .put("validationNotes", "ok")
        .toString()
}
