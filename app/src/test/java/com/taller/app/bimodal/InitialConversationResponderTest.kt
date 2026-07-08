package com.taller.app.bimodal

import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptPrompt
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.StructuredGptResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialConversationResponderTest {

    /** GptClient de prueba con comportamiento configurable y registro de llamadas. */
    private class FakeGptClient(
        private val enabled: Boolean = true,
        private val configured: Boolean = true,
        private val behavior: suspend (GptPrompt) -> GptResult = {
            GptResult.Success("{}", "gpt-test", 10L)
        }
    ) : GptClient {
        var generateCalls = 0
            private set
        var lastPrompt: GptPrompt? = null
            private set

        override suspend fun generate(prompt: GptPrompt): GptResult {
            generateCalls++
            lastPrompt = prompt
            return behavior(prompt)
        }

        override suspend fun generateStructured(input: SevenInputContract): StructuredGptResult =
            error("no usado en la conversacion inicial")

        override fun isEnabled(): Boolean = enabled
        override fun isConfigured(): Boolean = configured
    }

    @Test
    fun usesLocalBankAndDoesNotCallGptForKnownQuestion() = runBlocking {
        val client = FakeGptClient()
        val responder = InitialConversationResponder(client)

        val reply = responder.respond("¿Cómo te llamas?", "animales", "Granja", 1)

        assertEquals(InitialConversationSource.LOCAL, reply.source)
        assertTrue(reply.text.contains("Seven"))
        // El banco local resuelve: no se llama a GPT.
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun callsGptOnlyWhenLocalBankHasNoMatch() = runBlocking {
        val client = FakeGptClient(behavior = {
            GptResult.Success(
                "{\"answer\":\"Las estrellas brillan de noche.\",\"allowed\":true,\"reason\":\"SAFE_CHILD_CONTEXT\"}",
                "gpt-test",
                12L
            )
        })
        val responder = InitialConversationResponder(client)

        val reply = responder.respond("por que brillan las estrellas", "colores", null, 1)

        assertEquals(InitialConversationSource.GPT, reply.source)
        assertEquals("Las estrellas brillan de noche.", reply.text)
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun promptIncludesSevenTopicAndSafetyLimits() = runBlocking {
        val client = FakeGptClient(behavior = {
            GptResult.Success("{\"answer\":\"ok\",\"allowed\":true,\"reason\":\"SAFE_CHILD_CONTEXT\"}", "m", 1L)
        })
        val responder = InitialConversationResponder(client)

        responder.respond("cuentame un dato curioso", "los planetas", "Espacio", 2)

        val prompt = client.lastPrompt!!
        assertTrue(prompt.systemInstruction.contains("Seven"))
        // No pedir datos personales ni revelar respuestas.
        assertTrue(prompt.systemInstruction.contains("datos personales"))
        assertTrue(prompt.systemInstruction.lowercase().contains("no reveles"))
        // El contexto lleva el tema de la sesion.
        assertTrue(prompt.userMessage.contains("los planetas"))
    }

    @Test
    fun redirectsWhenGptMarksNotAllowed() = runBlocking {
        val client = FakeGptClient(behavior = {
            GptResult.Success("{\"answer\":\"\",\"allowed\":false,\"reason\":\"OUT_OF_SCOPE\"}", "m", 1L)
        })
        val responder = InitialConversationResponder(client)

        val reply = responder.respond("donde vives exactamente", "animales", null, 1)

        assertEquals(InitialConversationSource.GPT, reply.source)
        assertEquals(InitialConversationBank.REDIRECT_ANSWER, reply.text)
    }

    @Test
    fun fallsBackToLocalWhenGptFails() = runBlocking {
        val client = FakeGptClient(behavior = {
            GptResult.Failure(GptErrorType.NO_NETWORK, "sin red", "respaldo", "m")
        })
        val responder = InitialConversationResponder(client)

        val reply = responder.respond("cuentame algo", "animales", null, 1)

        assertEquals(InitialConversationSource.FALLBACK_LOCAL, reply.source)
        assertEquals(InitialConversationBank.SAFE_FALLBACK_ANSWER, reply.text)
    }

    @Test
    fun fallsBackToLocalWhenGptOutputIsNotValidJson() = runBlocking {
        val client = FakeGptClient(behavior = {
            GptResult.Success("esto no es json", "m", 1L)
        })
        val responder = InitialConversationResponder(client)

        val reply = responder.respond("cuentame algo", "animales", null, 1)

        assertEquals(InitialConversationSource.FALLBACK_LOCAL, reply.source)
    }

    @Test
    fun fallsBackWhenGptTimesOut() = runBlocking {
        val client = FakeGptClient(behavior = {
            delay(1_000L)
            GptResult.Success("{\"answer\":\"tarde\",\"allowed\":true,\"reason\":\"x\"}", "m", 1L)
        })
        val responder = InitialConversationResponder(client, gptTimeoutMs = 50L)

        val reply = responder.respond("cuentame algo", "animales", null, 1)

        assertEquals(InitialConversationSource.FALLBACK_LOCAL, reply.source)
        assertTrue(reply.timedOut)
    }

    @Test
    fun doesNotCallGptWhenNotConfigured() = runBlocking {
        val client = FakeGptClient(enabled = false, configured = false)
        val responder = InitialConversationResponder(client)

        val reply = responder.respond("cuentame algo", "animales", null, 1)

        assertEquals(InitialConversationSource.FALLBACK_LOCAL, reply.source)
        assertEquals(0, client.generateCalls)
    }
}
