package com.taller.app.bimodal

import com.taller.app.model.LearningQuestion
import com.taller.app.semantic.SemanticEvaluator
import com.taller.app.semantic.SemanticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del adaptador que conecta el evaluador semantico con el protocolo del
 * flujo bimodal. Reutiliza el [SemanticEvaluator] real (no se reimplementa) y
 * verifica el mapeo de cada resultado al evento del orquestador, la latencia y el
 * trato diferenciado de respuestas no interpretables y transcripciones vacias.
 */
class SemanticEvaluationAdapterTest {

    private lateinit var adapter: SemanticEvaluationAdapter

    private val question = LearningQuestion(
        id = "q1",
        questionText = "¿Que animal hace miau?",
        expectedAnswer = "gato",
        keywords = listOf("gato", "gatito", "miau"),
        maxTimeSeconds = 30,
        maxAttempts = 3
    )

    @Before
    fun setUp() {
        adapter = SemanticEvaluationAdapter(evaluator = SemanticEvaluator())
    }

    @Test
    fun correctAnswer_mapsToCorrectResult() {
        val outcome = adapter.evaluate("gato", question)
        assertEquals(SemanticResult.CORRECT, outcome.result)
        assertEquals(
            BimodalInteractionEvent.SemanticEvaluated(SemanticResult.CORRECT),
            outcome.toEvent()
        )
    }

    @Test
    fun keywordIsEnough_mapsToCorrectResult() {
        val outcome = adapter.evaluate("escucho un gatito", question)
        assertEquals(SemanticResult.CORRECT, outcome.result)
    }

    @Test
    fun wrongAnswer_mapsToIncorrectResult() {
        val outcome = adapter.evaluate("perro", question)
        assertEquals(SemanticResult.INCORRECT, outcome.result)
        assertEquals(
            BimodalInteractionEvent.SemanticEvaluated(SemanticResult.INCORRECT),
            outcome.toEvent()
        )
    }

    @Test
    fun notInterpretableAnswer_isNotTreatedAsIncorrect() {
        val outcome = adapter.evaluate("mmm", question)
        assertEquals(SemanticResult.NOT_INTERPRETABLE, outcome.result)
    }

    @Test
    fun emptyTranscription_isNotTreatedAsIncorrect() {
        val outcome = adapter.evaluate("", question)
        assertEquals(SemanticResult.NO_RESPONSE, outcome.result)
    }

    @Test
    fun blankTranscription_mapsToNoResponse() {
        val outcome = adapter.evaluate("   ", question)
        assertEquals(SemanticResult.NO_RESPONSE, outcome.result)
    }

    @Test
    fun latencyIsMeasuredInMillis() {
        // Reloj inyectado: avanza 5 ms (en nanosegundos) entre las dos lecturas.
        val times = ArrayDeque(listOf(0L, 5_000_000L))
        val timedAdapter = SemanticEvaluationAdapter(
            evaluator = SemanticEvaluator(),
            clock = { times.removeFirst() }
        )
        val outcome = timedAdapter.evaluate("gato", question)
        assertEquals(5L, outcome.latencyMillis)
    }

    @Test
    fun latencyIsNeverNegative() {
        val outcome = adapter.evaluate("gato", question)
        assertTrue(outcome.latencyMillis >= 0L)
    }
}
