package com.taller.app.bimodal

import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptPrompt
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.StructuredGptResult
import com.taller.app.gpt.judge.JudgeDecision
import com.taller.app.gpt.judge.JudgeDecisionLayer
import com.taller.app.gpt.judge.OpenAnswerJudge
import com.taller.app.model.LearningQuestion
import com.taller.app.semantic.SemanticResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridAnswerEvaluatorTest {

    /** Cliente GPT falso configurable, sin red. */
    private class FakeGptClient(
        private val enabled: Boolean = true,
        private val configured: Boolean = true,
        private val result: () -> GptResult
    ) : GptClient {
        var generateCalls = 0
            private set

        override suspend fun generate(prompt: GptPrompt): GptResult {
            generateCalls++
            return result()
        }

        override suspend fun generateStructured(input: SevenInputContract): StructuredGptResult =
            throw UnsupportedOperationException("no usado por el juez")

        override fun isEnabled(): Boolean = enabled
        override fun isConfigured(): Boolean = configured
    }

    private fun success(json: String) = GptResult.Success(
        text = json,
        modelUsed = "gpt-5.4-mini",
        latencyMs = 120L,
        fallbackUsed = false
    )

    private fun question(
        text: String = "Menciona un animal domestico",
        expected: String = "perro",
        maxAttempts: Int = 2
    ) = LearningQuestion(
        id = "1",
        questionText = text,
        expectedAnswer = expected,
        keywords = emptyList(),
        maxTimeSeconds = 30,
        maxAttempts = maxAttempts
    )

    private fun context(attempt: Int = 1, attemptsRemaining: Boolean = true) = HybridSessionContext(
        ageRange = "3-5",
        topic = "animales",
        classContext = null,
        currentAttempt = attempt,
        attemptsRemaining = attemptsRemaining
    )

    private fun evaluatorWith(result: () -> GptResult, enabled: Boolean = true, configured: Boolean = true): Pair<HybridAnswerEvaluator, FakeGptClient> {
        val client = FakeGptClient(enabled = enabled, configured = configured, result = result)
        val judge = OpenAnswerJudge(gptClient = client, logSink = {})
        return HybridAnswerEvaluator(judge = judge) to client
    }

    @Test
    fun localCorrect_doesNotCallJudge() = runBlocking {
        val (evaluator, client) = evaluatorWith({ success("{}") })
        val result = evaluator.evaluate(
            transcription = "el perro",
            question = question(),
            localResult = SemanticResult.CORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.CORRECT, result.finalResult)
        assertFalse(result.judgeConsulted)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun openIncorrect_judgeAccepts_layerIsJudge() = runBlocking {
        val json = """{ "decision": "CORRECT", "confidence": 0.9, "reason": "ejemplo valido",
            "acceptedAsEquivalent": true }"""
        val (evaluator, client) = evaluatorWith({ success(json) })
        val result = evaluator.evaluate(
            transcription = "el gato",
            question = question(expected = "perro"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.JUDGE, result.decisionLayer)
        assertEquals(SemanticResult.CORRECT, result.finalResult)
        assertEquals(JudgeDecision.CORRECT, result.judgeDecision)
        assertTrue(result.acceptedAsEquivalent)
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun openIncorrect_judgeRejects_staysIncorrect() = runBlocking {
        val json = """{ "decision": "INCORRECT", "confidence": 0.8, "reason": "no responde" }"""
        val (evaluator, _) = evaluatorWith({ success(json) })
        val result = evaluator.evaluate(
            transcription = "el escritorio",
            question = question(text = "Menciona un animal de granja", expected = "vaca"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.JUDGE, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertFalse(result.acceptedAsEquivalent)
    }

    @Test
    fun openIncorrect_invalidJson_fallsBackLocal() = runBlocking {
        val (evaluator, _) = evaluatorWith({ success("no soy json") })
        val result = evaluator.evaluate(
            transcription = "el gato",
            question = question(),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.FALLBACK_LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertTrue(result.usedFallback)
    }

    @Test
    fun openIncorrect_networkFailure_fallsBackLocal() = runBlocking {
        val failure = {
            GptResult.Failure(
                errorType = GptErrorType.NO_NETWORK,
                safeMessage = "sin red",
                fallbackText = "",
                modelAttempted = "gpt-5.4-mini"
            ) as GptResult
        }
        val (evaluator, _) = evaluatorWith(failure)
        val result = evaluator.evaluate(
            transcription = "el gato",
            question = question(),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.FALLBACK_LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertTrue(result.usedFallback)
    }

    @Test
    fun closedIncorrect_doesNotCallJudge() = runBlocking {
        // Pregunta cerrada, referencia simple: el juez no se consulta.
        val (evaluator, client) = evaluatorWith({ success("{}") })
        val result = evaluator.evaluate(
            transcription = "azul",
            question = question(text = "De que color es el cielo", expected = "celeste"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun openIncorrect_judgeNotConfigured_fallsBackLocal() = runBlocking {
        val (evaluator, client) = evaluatorWith({ success("{}") }, enabled = false)
        val result = evaluator.evaluate(
            transcription = "el gato",
            question = question(),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.FALLBACK_LOCAL, result.decisionLayer)
        assertTrue(result.usedFallback)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun notInterpretable_doesNotCallJudge() = runBlocking {
        val (evaluator, client) = evaluatorWith({ success("{}") })
        val result = evaluator.evaluate(
            transcription = "mmm",
            question = question(),
            localResult = SemanticResult.NOT_INTERPRETABLE,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.NOT_INTERPRETABLE, result.finalResult)
        assertNull(result.judgeDecision)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun listReferenceIncorrect_consultsJudge() = runBlocking {
        // Referencia tipo lista: caso abierto aunque la capa local marque incorrecto.
        val json = """{ "decision": "CORRECT", "confidence": 0.85, "acceptedAsEquivalent": true }"""
        val (evaluator, client) = evaluatorWith({ success(json) })
        val result = evaluator.evaluate(
            transcription = "el conejo",
            question = question(text = "Dime mascotas", expected = "perro, gato, hamster"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.JUDGE, result.decisionLayer)
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun questionLooksOpen_detectsExampleQuestions() {
        val (evaluator, _) = evaluatorWith({ success("{}") })
        assertTrue(evaluator.questionLooksOpen("Menciona un animal domestico"))
        assertTrue(evaluator.questionLooksOpen("Dime un ejemplo de fruta"))
        assertFalse(evaluator.questionLooksOpen("De que color es el cielo"))
    }

    // ----- MED01-FIX01: preguntas de sonido / onomatopeyas ----------------------

    private fun soundQuestion(expected: String = "guau") = question(
        text = "¿Qué sonido hace el perro?",
        expected = expected
    )

    @Test
    fun aliasAcceptedLocally_doesNotCallJudge() = runBlocking {
        // La capa local ya acepto el alias (CORRECT): no se consulta al juez.
        val (evaluator, client) = evaluatorWith({ success("{}") })
        val result = evaluator.evaluate(
            transcription = "wow",
            question = soundQuestion(),
            localResult = SemanticResult.CORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.CORRECT, result.finalResult)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun soundPromptDubiousAnswer_consultsJudge() = runBlocking {
        // Pregunta de sonido, transcripcion corta no reconocida localmente: dudoso.
        val json = """{ "decision": "CORRECT", "confidence": 0.8, "acceptedAsEquivalent": true }"""
        val (evaluator, client) = evaluatorWith({ success(json) })
        val result = evaluator.evaluate(
            transcription = "grr",
            question = soundQuestion(),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.JUDGE, result.decisionLayer)
        assertEquals(SemanticResult.CORRECT, result.finalResult)
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun soundPromptContradictorySound_doesNotCallJudge() = runBlocking {
        // "miau" como sonido del perro es contradictorio: caso cerrado, sin juez.
        val (evaluator, client) = evaluatorWith({ success("{}") })
        val result = evaluator.evaluate(
            transcription = "miau",
            question = soundQuestion(),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun soundPromptJudgeFails_fallsBackLocal() = runBlocking {
        val failure = {
            GptResult.Failure(
                errorType = GptErrorType.NO_NETWORK,
                safeMessage = "sin red",
                fallbackText = "",
                modelAttempted = "gpt-5.4-mini"
            ) as GptResult
        }
        val (evaluator, _) = evaluatorWith(failure)
        val result = evaluator.evaluate(
            transcription = "grr",
            question = soundQuestion(),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.FALLBACK_LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertTrue(result.usedFallback)
    }

    // ----- MED02: equivalencias y categorias abiertas de varios temas ------------

    @Test
    fun openTransportThatFlies_judgeAcceptsHelicopter() = runBlocking {
        // Pregunta abierta ("menciona"): el juez puede aceptar un equivalente valido.
        val json = """{ "decision": "CORRECT", "confidence": 0.85, "acceptedAsEquivalent": true,
            "acceptanceType": "EQUIVALENT" }"""
        val (evaluator, client) = evaluatorWith({ success(json) })
        val result = evaluator.evaluate(
            transcription = "helicoptero",
            question = question(text = "Menciona un transporte que vuela", expected = "avion"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.JUDGE, result.decisionLayer)
        assertEquals(SemanticResult.CORRECT, result.finalResult)
        assertEquals("EQUIVALENT", result.acceptanceType)
        assertEquals(1, client.generateCalls)
    }

    @Test
    fun closedTransportThatFlies_bicycle_doesNotCallJudge() = runBlocking {
        // Pregunta cerrada y referencia simple: contradiccion clara, sin juez.
        val (evaluator, client) = evaluatorWith({ success("{}") })
        val result = evaluator.evaluate(
            transcription = "bicicleta",
            question = question(text = "¿Qué transporte vuela?", expected = "avion"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.LOCAL, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun closedBodyQuestion_orejas_doesNotCallJudge() = runBlocking {
        // "orejas" cuando se pregunta con que vemos: pregunta cerrada, sin juez.
        val (evaluator, client) = evaluatorWith({ success("{}") })
        val result = evaluator.evaluate(
            transcription = "orejas",
            question = question(text = "¿Con qué parte del cuerpo vemos?", expected = "ojos"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.LOCAL, result.decisionLayer)
        assertEquals(0, client.generateCalls)
    }

    @Test
    fun openFood_judgeRejectsNonFood_noFalsePositive() = runBlocking {
        // Pregunta abierta pero respuesta claramente no comestible: el juez rechaza.
        val json = """{ "decision": "INCORRECT", "confidence": 0.9, "reason": "no se come" }"""
        val (evaluator, _) = evaluatorWith({ success(json) })
        val result = evaluator.evaluate(
            transcription = "piedra",
            question = question(text = "Menciona algo que se puede comer", expected = "pan"),
            localResult = SemanticResult.INCORRECT,
            context = context()
        )
        assertEquals(JudgeDecisionLayer.JUDGE, result.decisionLayer)
        assertEquals(SemanticResult.INCORRECT, result.finalResult)
        assertFalse(result.acceptedAsEquivalent)
        assertNull(result.acceptanceType)
    }

    @Test
    fun questionLooksLikeSoundPrompt_detectsSoundQuestions() {
        val (evaluator, _) = evaluatorWith({ success("{}") })
        assertTrue(evaluator.questionLooksLikeSoundPrompt(soundQuestion()))
        assertFalse(
            evaluator.questionLooksLikeSoundPrompt(
                question(text = "Menciona un animal domestico", expected = "perro")
            )
        )
    }
}
