package com.taller.app.gpt.script

import com.taller.app.gpt.GptConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScriptClientConfigTest {

    private fun baseConfig() = GptConfig(
        apiKey = "x",
        model = "gpt-test",
        fallbackModel = "gpt-test-mini",
        maxOutputTokens = 220,
        timeoutMs = 12_000L,
        temperature = 0.4f,
        enabled = true
    )

    @Test
    fun outputTokens_scaleWithQuestionsAndStayWithinBounds() {
        // Cinco preguntas requieren mucho más que el presupuesto corto por defecto.
        val five = SessionScriptClientConfig.outputTokensFor(5)
        assertTrue(five > 220)
        assertTrue(five >= SessionScriptClientConfig.MIN_OUTPUT_TOKENS)
        assertTrue(five <= SessionScriptClientConfig.MAX_OUTPUT_TOKENS)

        // Cota inferior aplicada con pocas preguntas.
        assertEquals(
            SessionScriptClientConfig.MIN_OUTPUT_TOKENS,
            SessionScriptClientConfig.outputTokensFor(0)
        )

        // Cota superior aplicada con muchas preguntas.
        assertEquals(
            SessionScriptClientConfig.MAX_OUTPUT_TOKENS,
            SessionScriptClientConfig.outputTokensFor(1000)
        )
    }

    @Test
    fun forScript_raisesBudgetAndTimeout() {
        val base = baseConfig()
        val scriptConfig = SessionScriptClientConfig.forScript(base, questionCount = 5)

        assertTrue(scriptConfig.maxOutputTokens > base.maxOutputTokens)
        assertTrue(scriptConfig.timeoutMs >= SessionScriptClientConfig.SCRIPT_TIMEOUT_MS)
        // No cambia credenciales ni modelo.
        assertEquals(base.model, scriptConfig.model)
        assertEquals(base.apiKey, scriptConfig.apiKey)
    }

    @Test
    fun forScript_keepsLongerExistingTimeout() {
        val base = baseConfig().copy(timeoutMs = 60_000L)
        val scriptConfig = SessionScriptClientConfig.forScript(base, questionCount = 3)
        assertEquals(60_000L, scriptConfig.timeoutMs)
    }
}
