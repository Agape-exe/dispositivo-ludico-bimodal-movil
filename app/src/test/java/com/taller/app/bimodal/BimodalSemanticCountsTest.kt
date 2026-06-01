package com.taller.app.bimodal

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode
import com.taller.app.semantic.SemanticEvaluator
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Prueba de extremo a extremo (logica pura) del conteo del modo bimodal usando el
 * evaluador semantico REAL a traves del [SemanticEvaluationAdapter] y el
 * [BimodalFlowOrchestrator]. Garantiza que el resultado semantico de una
 * transcripcion concreta se contabilice en la categoria correcta del resumen, en
 * particular que una respuesta incorrecta nunca incremente las correctas.
 */
class BimodalSemanticCountsTest {

    private lateinit var orchestrator: BimodalFlowOrchestrator
    private val adapter = SemanticEvaluationAdapter(
        evaluator = SemanticEvaluator(),
        clock = { 0L }
    )

    @Before
    fun setUp() {
        orchestrator = BimodalFlowOrchestrator(now = { 1_000L })
    }

    private fun question(
        expected: String = "gato",
        keywords: List<String> = listOf("gato", "minino"),
        maxAttempts: Int = 1
    ) = LearningQuestion(
        id = "q1",
        questionText = "¿Qué animal hace miau?",
        expectedAnswer = expected,
        keywords = keywords,
        maxTimeSeconds = 30,
        maxAttempts = maxAttempts
    )

    private fun load(vararg questions: LearningQuestion) {
        orchestrator.loadActivity(
            LearningActivity(
                id = "act-1",
                title = "Actividad",
                mode = OperationMode.ADVANCED,
                questions = questions.toList()
            )
        )
        orchestrator.markActivityLoaded()
    }

    /** Recorre el flujo real: rostro -> escucha -> transcripcion -> evaluacion real. */
    private fun answerWith(transcription: String) {
        orchestrator.startSession()
        orchestrator.onFaceDetected()
        orchestrator.startListening()
        orchestrator.onSpeechCaptured(transcription)
        val question = LearningQuestion(
            id = orchestrator.progress!!.currentQuestionId,
            questionText = orchestrator.progress!!.currentQuestionText,
            expectedAnswer = currentExpected,
            keywords = currentKeywords,
            maxTimeSeconds = 30,
            maxAttempts = orchestrator.progress!!.maxAttempts
        )
        val outcome = adapter.evaluate(transcription, question)
        orchestrator.onEvent(outcome.toEvent())
    }

    private var currentExpected: String = "gato"
    private var currentKeywords: List<String> = listOf("gato", "minino")

    @Test
    fun correctAnswer_incrementsOnlyCorrectCount() {
        load(question())
        answerWith("es un gato")
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(1, s.correct)
        assertEquals(0, s.incorrect)
        assertEquals(0, s.notInterpretable)
    }

    @Test
    fun incorrectAnswer_incrementsIncorrectNotCorrect() {
        load(question())
        answerWith("un perro")
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(0, s.correct)
        assertEquals(1, s.incorrect)
    }

    @Test
    fun partialWordMatch_isNotCountedAsCorrect() {
        // Regresion del bug: "azulejo" no debe contar como "azul".
        currentExpected = "azul"
        currentKeywords = listOf("azul", "celeste")
        load(question(expected = "azul", keywords = listOf("azul", "celeste")))
        answerWith("azulejo")
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(0, s.correct)
        assertEquals(1, s.incorrect)
    }

    @Test
    fun blankExpectedAnswer_doesNotCountAnyTranscriptionAsCorrect() {
        currentExpected = ""
        currentKeywords = emptyList()
        load(question(expected = "", keywords = emptyList()))
        answerWith("lo que sea")
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(0, s.correct)
        assertEquals(1, s.incorrect)
    }

    @Test
    fun notInterpretable_incrementsNotInterpretableNotCorrect() {
        load(question())
        answerWith("mmm")
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(0, s.correct)
        assertEquals(1, s.notInterpretable)
    }

    @Test
    fun emptyTranscription_isNoResponseNotCorrect() {
        load(question())
        orchestrator.startSession()
        orchestrator.onFaceDetected()
        orchestrator.startListening()
        // Transcripcion vacia: el flujo la trata como ausencia de respuesta.
        orchestrator.onEvent(SpeechCaptureEventMapper.toEvent(SpeechCaptureOutcome.NoSpeech))
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(0, s.correct)
        assertEquals(1, s.noResponse)
    }

    @Test
    fun sttError_isTechnicalErrorNotCorrect() {
        load(question())
        orchestrator.startSession()
        orchestrator.onFaceDetected()
        orchestrator.startListening()
        orchestrator.onEvent(
            SpeechCaptureEventMapper.toEvent(SpeechCaptureOutcome.Failed("reconocedor ocupado"))
        )
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(0, s.correct)
        assertEquals(1, s.technicalErrors)
    }

    @Test
    fun sameEvaluation_isNotCountedTwice() {
        load(question())
        answerWith("es un gato")
        // Reenviar la misma evaluacion estando ya en feedback no debe recontar.
        orchestrator.onSemanticEvaluated(orchestrator.lastResult!!.semanticResult!!)
        orchestrator.completeSession()

        val s = orchestrator.summary
        assertEquals(1, s.correct)
        assertEquals(1, s.resolvedQuestions)
        assertEquals(1, s.totalAttempts)
    }
}
