package com.taller.app.bimodal

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode
import com.taller.app.semantic.SemanticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BimodalFlowOrchestratorTest {

    private lateinit var orchestrator: BimodalFlowOrchestrator

    @Before
    fun setUp() {
        // Tiempo fijo para mantener las pruebas deterministas.
        orchestrator = BimodalFlowOrchestrator(now = { 1_000L })
    }

    private fun question(
        id: String,
        maxAttempts: Int = 1,
        maxTimeSeconds: Int = 10
    ) = LearningQuestion(
        id = id,
        questionText = "Pregunta $id",
        expectedAnswer = "respuesta",
        keywords = listOf("respuesta"),
        maxTimeSeconds = maxTimeSeconds,
        maxAttempts = maxAttempts
    )

    private fun activity(vararg questions: LearningQuestion) = LearningActivity(
        id = "act-1",
        title = "Actividad de prueba",
        mode = OperationMode.ADVANCED,
        questions = questions.toList()
    )

    private fun load(activity: LearningActivity) {
        orchestrator.loadActivity(activity)
        orchestrator.markActivityLoaded()
    }

    /** Lleva el flujo hasta LISTENING en la pregunta actual. */
    private fun reachListening() {
        orchestrator.startSession()
        orchestrator.onFaceDetected()
        orchestrator.startListening()
    }

    @Test
    fun loadValidActivity_reachesReadyWithProgress() {
        load(activity(question("q1")))

        assertEquals(BimodalInteractionState.READY, orchestrator.state)
        val progress = orchestrator.progress
        assertNotNull(progress)
        assertEquals("act-1", progress!!.activityId)
        assertEquals(1, progress.totalQuestions)
        assertEquals("q1", progress.currentQuestionId)
        assertEquals(0, progress.currentQuestionIndex)
        assertEquals(1, progress.currentAttempt)
    }

    @Test
    fun loadActivityWithoutQuestions_goesToError() {
        load(activity())

        assertEquals(BimodalInteractionState.ERROR, orchestrator.state)
        assertNotNull(orchestrator.errorMessage)
        assertNull(orchestrator.progress)
    }

    @Test
    fun faceDetected_movesFromWaitingForFaceToPresentingQuestion() {
        load(activity(question("q1")))

        orchestrator.startSession()
        assertEquals(BimodalInteractionState.WAITING_FOR_FACE, orchestrator.state)

        orchestrator.onFaceDetected()
        assertEquals(BimodalInteractionState.PRESENTING_QUESTION, orchestrator.state)
        assertEquals(1_000L, orchestrator.progress?.questionStartedAt)
    }

    @Test
    fun correctAnswer_advancesToNextQuestion() {
        load(activity(question("q1"), question("q2")))

        reachListening()
        orchestrator.onSpeechCaptured("respuesta")
        orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)

        assertEquals(BimodalInteractionState.FEEDBACK_CORRECT, orchestrator.state)
        assertFalse(orchestrator.lastResult!!.canRetry)

        orchestrator.moveToNextQuestion()
        assertEquals(BimodalInteractionState.WAITING_FOR_FACE, orchestrator.state)
        assertEquals(1, orchestrator.progress?.currentQuestionIndex)
        assertEquals("q2", orchestrator.progress?.currentQuestionId)
    }

    @Test
    fun incorrectAnswerWithAttemptsLeft_allowsRetry() {
        load(activity(question("q1", maxAttempts = 2)))

        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)

        assertEquals(BimodalInteractionState.FEEDBACK_INCORRECT, orchestrator.state)
        assertTrue(orchestrator.lastResult!!.canRetry)

        orchestrator.retryQuestion()
        assertEquals(BimodalInteractionState.PRESENTING_QUESTION, orchestrator.state)
        assertEquals(2, orchestrator.progress?.currentAttempt)
        assertNull(orchestrator.progress?.lastTranscription)
    }

    @Test
    fun incorrectAnswerWithoutAttemptsLeft_advancesToNextQuestion() {
        load(activity(question("q1", maxAttempts = 1), question("q2")))

        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)

        assertEquals(BimodalInteractionState.FEEDBACK_INCORRECT, orchestrator.state)
        assertFalse(orchestrator.lastResult!!.canRetry)

        // Sin intentos restantes, el reintento no debe tener efecto.
        orchestrator.retryQuestion()
        assertEquals(BimodalInteractionState.FEEDBACK_INCORRECT, orchestrator.state)

        orchestrator.moveToNextQuestion()
        assertEquals(BimodalInteractionState.WAITING_FOR_FACE, orchestrator.state)
        assertEquals(1, orchestrator.progress?.currentQuestionIndex)
    }

    @Test
    fun notInterpretableWithAttemptsLeft_allowsRetry() {
        load(activity(question("q1", maxAttempts = 2)))

        reachListening()
        orchestrator.onSpeechCaptured("mmm")
        orchestrator.onSemanticEvaluated(SemanticResult.NOT_INTERPRETABLE)

        assertEquals(BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE, orchestrator.state)
        assertTrue(orchestrator.lastResult!!.canRetry)

        orchestrator.retryQuestion()
        assertEquals(BimodalInteractionState.PRESENTING_QUESTION, orchestrator.state)
        assertEquals(2, orchestrator.progress?.currentAttempt)
    }

    @Test
    fun completingLastQuestion_endsSession() {
        load(activity(question("q1")))

        reachListening()
        orchestrator.onSpeechCaptured("respuesta")
        orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)
        orchestrator.moveToNextQuestion()

        assertEquals(BimodalInteractionState.SESSION_COMPLETED, orchestrator.state)
    }

    @Test
    fun cancelSession_movesToSessionCancelled() {
        load(activity(question("q1")))
        orchestrator.startSession()

        orchestrator.cancelSession()

        assertEquals(BimodalInteractionState.SESSION_CANCELLED, orchestrator.state)
    }

    @Test
    fun noResponse_whileListening_producesNoResponseFeedback() {
        load(activity(question("q1", maxAttempts = 1), question("q2")))

        reachListening()
        orchestrator.onNoResponse()

        assertEquals(BimodalInteractionState.FEEDBACK_NO_RESPONSE, orchestrator.state)
        assertEquals(SemanticResult.NO_RESPONSE, orchestrator.lastResult?.semanticResult)
    }
}
