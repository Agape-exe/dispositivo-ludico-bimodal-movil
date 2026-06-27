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
        fallbackModel: String = "gpt-5.4-nano"
    ) = GptConfig(
        apiKey = apiKey,
        model = model,
        fallbackModel = fallbackModel,
        maxOutputTokens = 220,
        timeoutMs = 12_000L,
        temperature = 0.4f,
        enabled = enabled
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
}
