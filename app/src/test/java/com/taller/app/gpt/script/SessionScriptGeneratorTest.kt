package com.taller.app.gpt.script

import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptPrompt
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.StructuredGptResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScriptGeneratorTest {

    private fun input() = SessionScriptInput(
        sessionName = "Animales",
        topic = "Animales domésticos",
        description = "",
        objective = "",
        ageLevel = "3 a 5 años",
        contextNotes = "",
        questions = listOf(
            SessionScriptQuestionInput(1L, 1, "Menciona un animal doméstico.", "perro")
        )
    )

    private fun validJson() = """
        {
          "intro": "Hola explorador",
          "closing": "Adios explorador",
          "toneNotes": "calido",
          "pedagogicalWarnings": "",
          "questions": [
            {
              "orderIndex": 1,
              "childFriendlyQuestionText": "Dime un animalito que viva con las personas",
              "hintLevel1": "Algunos viven en casa",
              "hintLevel2": "Tiene cuatro patas",
              "hintLevel3": "Le gusta jugar",
              "positiveFeedbackText": "Muy bien",
              "supportiveFeedbackText": "Casi, sigamos pensando",
              "retryPromptText": "Probemos otra vez",
              "answerReferenceWarning": "",
              "suggestedReferenceAnswer": "perro, gato"
            }
          ]
        }
    """.trimIndent()

    private class FakeGptClient(private val result: GptResult) : GptClient {
        override suspend fun generate(prompt: GptPrompt): GptResult = result
        override suspend fun generateStructured(input: SevenInputContract): StructuredGptResult =
            throw UnsupportedOperationException("not used")
        override fun isEnabled(): Boolean = true
        override fun isConfigured(): Boolean = true
    }

    @Test
    fun validModelResponse_returnsFromModel() = runBlocking {
        val client = FakeGptClient(
            GptResult.Success(text = validJson(), modelUsed = "test", latencyMs = 1L)
        )
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromModel)
        assertEquals("perro, gato", outcome.script.questions[0].suggestedReferenceAnswer)
    }

    @Test
    fun invalidModelResponse_fallsBackToLocal() = runBlocking {
        val client = FakeGptClient(
            GptResult.Success(text = "no soy json", modelUsed = "test", latencyMs = 1L)
        )
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromLocal)
        assertTrue(outcome.script.intro.isNotBlank())
    }

    @Test
    fun disabledClient_fallsBackToLocal() = runBlocking {
        val client = FakeGptClient(GptResult.Disabled(fallbackText = ""))
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromLocal)
    }

    @Test
    fun failure_fallsBackToLocalWithFriendlyMessage() = runBlocking {
        val client = FakeGptClient(
            GptResult.Failure(
                errorType = GptErrorType.NO_NETWORK,
                safeMessage = "sin red",
                fallbackText = "",
                modelAttempted = "test"
            )
        )
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromLocal)
        val local = outcome as SessionScriptGenerator.Outcome.FromLocal
        assertTrue(local.message.contains("conexión"))
    }
}
