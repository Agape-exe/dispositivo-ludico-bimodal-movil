package com.taller.app.gpt.script

import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptPrompt
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.StructuredGptResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun animalsInput() = SessionScriptInput(
        sessionName = "Los animales que viven cerca de nosotros",
        topic = "Animales domésticos y de granja",
        description = "Sesión breve para reconocer animales cercanos.",
        objective = "Que los niños identifiquen animales conocidos de su entorno.",
        ageLevel = "4 a 5 años",
        contextNotes = "Grupo de inicial. Actividad oral breve.",
        questions = listOf(
            SessionScriptQuestionInput(1L, 1, "Menciona un animal doméstico.", "perro"),
            SessionScriptQuestionInput(2L, 2, "¿Qué sonido hace el perro?", "guau"),
            SessionScriptQuestionInput(3L, 3, "Menciona un animal que puede vivir en una granja.", "vaca"),
            SessionScriptQuestionInput(4L, 4, "¿Qué sonido hace el gato?", "miau"),
            SessionScriptQuestionInput(5L, 5, "Dime un animal que tenga plumas.", "gallina")
        )
    )

    private fun animalsJson(): String {
        val questions = (1..5).joinToString(",\n") { n ->
            """
            {
              "orderIndex": $n,
              "childFriendlyQuestionText": "Reto $n para el pequeño explorador",
              "hintLevel1": "Pista suave $n",
              "hintLevel2": "Pista media $n",
              "hintLevel3": "Pista final $n",
              "positiveFeedbackText": "Qué bien, exploraste muy bien",
              "supportiveFeedbackText": "Sigamos pensando juntitos",
              "retryPromptText": "Probemos otra vez, tú puedes",
              "answerReferenceWarning": "",
              "suggestedReferenceAnswer": "referencia $n"
            }
            """.trimIndent()
        }
        return """
            {
              "intro": "Hola, soy Seven y vamos a descubrir animales",
              "closing": "Gracias por ayudarme, pequeño explorador",
              "toneNotes": "calido y lúdico",
              "pedagogicalWarnings": "",
              "questions": [
              $questions
              ]
            }
        """.trimIndent()
    }

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
    fun invalidModelResponse_fallsBackToLocalWithReason() = runBlocking {
        val client = FakeGptClient(
            GptResult.Success(text = "no soy json", modelUsed = "test", latencyMs = 1L)
        )
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromLocal)
        val local = outcome as SessionScriptGenerator.Outcome.FromLocal
        assertTrue(local.script.intro.isNotBlank())
        assertEquals(SessionScriptGenerator.Reason.INVALID_JSON, local.reason)
    }

    @Test
    fun emptyResponse_fallsBackWithEmptyReason() = runBlocking {
        val client = FakeGptClient(
            GptResult.Success(text = "   ", modelUsed = "test", latencyMs = 1L)
        )
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromLocal)
        assertEquals(
            SessionScriptGenerator.Reason.EMPTY_RESPONSE,
            (outcome as SessionScriptGenerator.Outcome.FromLocal).reason
        )
    }

    @Test
    fun truncatedResponse_fallsBackWithTruncatedReason() = runBlocking {
        val truncated = "{ \"intro\": \"Hola\", \"questions\": [ { \"orderIndex\": 1, \"childFriendlyQuestionText"
        val client = FakeGptClient(
            GptResult.Success(text = truncated, modelUsed = "test", latencyMs = 1L)
        )
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromLocal)
        assertEquals(
            SessionScriptGenerator.Reason.TRUNCATED_RESPONSE,
            (outcome as SessionScriptGenerator.Outcome.FromLocal).reason
        )
    }

    @Test
    fun partiallyValidResponse_recoversWithLocalFields() = runBlocking {
        // hint1 revela la respuesta y el feedback positivo viene vacío: solo esos
        // campos se completan con el guion local, sin descartar el resto.
        val partial = """
            {
              "intro": "Hola explorador",
              "closing": "Adios explorador",
              "toneNotes": "",
              "pedagogicalWarnings": "",
              "questions": [
                {
                  "orderIndex": 1,
                  "childFriendlyQuestionText": "Dime un animalito que viva con las personas",
                  "hintLevel1": "Es un perro",
                  "hintLevel2": "Tiene cuatro patas",
                  "hintLevel3": "Le gusta jugar",
                  "positiveFeedbackText": "",
                  "supportiveFeedbackText": "Sigamos pensando",
                  "retryPromptText": "Probemos otra vez",
                  "answerReferenceWarning": "",
                  "suggestedReferenceAnswer": "perro"
                }
              ]
            }
        """.trimIndent()
        val client = FakeGptClient(GptResult.Success(text = partial, modelUsed = "test", latencyMs = 1L))
        val outcome = SessionScriptGenerator(client) {}.generate(input())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromModelPartial)
        val partialOutcome = outcome as SessionScriptGenerator.Outcome.FromModelPartial
        assertEquals(SessionScriptGenerator.Reason.PARTIAL_RECOVERY, partialOutcome.reason)
        // Conservó lo válido del modelo.
        assertEquals("Hola explorador", partialOutcome.script.intro)
        assertEquals(
            "Dime un animalito que viva con las personas",
            partialOutcome.script.questions[0].childFriendlyQuestionText
        )
        // Reparó lo inválido: hint que revelaba la respuesta y feedback vacío.
        assertTrue(partialOutcome.script.questions[0].positiveFeedbackText.isNotBlank())
        assertFalse(partialOutcome.script.questions[0].hintLevel1.lowercase().contains("perro"))
    }

    @Test
    fun fullAnimalsResponse_producesCompleteScript() = runBlocking {
        val client = FakeGptClient(
            GptResult.Success(text = animalsJson(), modelUsed = "test", latencyMs = 1L)
        )
        val outcome = SessionScriptGenerator(client) {}.generate(animalsInput())

        assertTrue(outcome is SessionScriptGenerator.Outcome.FromModel)
        val script = outcome.script
        assertTrue(script.intro.isNotBlank())
        assertTrue(script.closing.isNotBlank())
        assertEquals(5, script.questions.size)
        script.questions.forEach { q ->
            assertTrue(q.childFriendlyQuestionText.isNotBlank())
            assertTrue(q.hintLevel1.isNotBlank())
            assertTrue(q.hintLevel2.isNotBlank())
            assertTrue(q.hintLevel3.isNotBlank())
            assertTrue(q.positiveFeedbackText.isNotBlank())
            assertTrue(q.supportiveFeedbackText.isNotBlank())
            assertTrue(q.retryPromptText.isNotBlank())
        }
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
