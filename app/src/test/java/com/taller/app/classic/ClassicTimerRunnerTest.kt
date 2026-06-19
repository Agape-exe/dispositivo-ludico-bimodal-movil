package com.taller.app.classic

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClassicTimerRunnerTest {

    private lateinit var runner: ClassicTimerRunner

    @Before
    fun setUp() {
        runner = ClassicTimerRunner(now = { 1_000L })
    }

    private fun question(id: String, maxTimeSeconds: Int = 10, mediationKey: String? = null) = LearningQuestion(
        id = id,
        questionText = "Pregunta $id",
        expectedAnswer = "respuesta",
        keywords = listOf("respuesta"),
        maxTimeSeconds = maxTimeSeconds,
        maxAttempts = 1,
        mediationKey = mediationKey
    )

    private fun activity(vararg questions: LearningQuestion) = LearningActivity(
        id = "act-1",
        title = "Actividad de prueba",
        mode = OperationMode.CLASSIC,
        questions = questions.toList()
    )

    private fun load(activity: LearningActivity) {
        runner.loadActivity(activity)
        runner.markActivityLoaded()
    }

    private fun reachWaitingResponse(): Unit {
        runner.startSession()
        runner.presentCurrentQuestion()
        runner.startResponseWindow()
    }

    // ----- Carga de actividad --------------------------------------------------

    @Test
    fun loadValidActivity_reachesReadyState() {
        load(activity(question("q1")))
        assertEquals(ClassicTimerState.READY, runner.state)
        assertNotNull(runner.progress)
    }

    @Test
    fun loadEmptyActivity_transitionsToError() {
        runner.loadActivity(activity())
        runner.markActivityLoaded()
        assertEquals(ClassicTimerState.ERROR, runner.state)
        assertNotNull(runner.errorMessage)
    }

    // ----- Flujo básico --------------------------------------------------------

    @Test
    fun startSession_reachesSessionStarting() {
        load(activity(question("q1")))
        runner.startSession()
        assertEquals(ClassicTimerState.SESSION_STARTING, runner.state)
    }

    @Test
    fun presentCurrentQuestion_reachesPresentingQuestion() {
        load(activity(question("q1")))
        runner.startSession()
        runner.presentCurrentQuestion()
        assertEquals(ClassicTimerState.PRESENTING_QUESTION, runner.state)
    }

    @Test
    fun startResponseWindow_reachesWaitingFixedResponse() {
        load(activity(question("q1")))
        reachWaitingResponse()
        assertEquals(ClassicTimerState.WAITING_FIXED_RESPONSE, runner.state)
    }

    // ----- Respuesta recibida --------------------------------------------------

    @Test
    fun answerReceived_transitionsToAnswerReceived() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onAnswerReceived()
        assertEquals(ClassicTimerState.ANSWER_RECEIVED, runner.state)
    }

    @Test
    fun answerReceived_progressReflectsAnswer() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onAnswerReceived()
        assertTrue(runner.progress?.answerReceived == true)
    }

    @Test
    fun answerReceived_resultHasAnswerReceivedTrue() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onAnswerReceived()
        assertTrue(runner.lastResult?.answerReceived == true)
        assertFalse(runner.lastResult?.timedOut == true)
    }

    // ----- Tiempo agotado ------------------------------------------------------

    @Test
    fun timeExpired_transitionsToTimeExpired() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onTimeExpired()
        assertEquals(ClassicTimerState.TIME_EXPIRED, runner.state)
    }

    @Test
    fun timeExpired_resultHasTimedOutTrue() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onTimeExpired()
        assertTrue(runner.lastResult?.timedOut == true)
        assertFalse(runner.lastResult?.answerReceived == true)
    }

    @Test
    fun timeExpiredWithPartial_progressReflectsPartial() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onTimeExpired(hadPartial = true)
        assertTrue(runner.progress?.hadPartialResponseOnTimeout == true)
    }

    // ----- No invoca SemanticEvaluator -----------------------------------------

    @Test
    fun classicRunner_neverInvokesSemanticEvaluator() {
        // El runner no tiene ninguna referencia ni invocación de SemanticEvaluator.
        // Esta prueba verifica el flujo completo sin que se produzca ninguna excepción
        // relacionada con evaluación semántica.
        load(activity(question("q1"), question("q2")))
        reachWaitingResponse()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        runner.startResponseWindow()
        runner.onTimeExpired()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.SESSION_COMPLETED, runner.state)
    }

    // ----- Avance de preguntas -------------------------------------------------

    @Test
    fun advanceAfterAnswer_withMoreQuestions_goesToPresentingQuestion() {
        load(activity(question("q1"), question("q2")))
        reachWaitingResponse()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.PRESENTING_QUESTION, runner.state)
        assertEquals(1, runner.progress?.currentQuestionIndex)
    }

    @Test
    fun advanceAfterTimeout_withMoreQuestions_goesToPresentingQuestion() {
        load(activity(question("q1"), question("q2")))
        reachWaitingResponse()
        runner.onTimeExpired()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.PRESENTING_QUESTION, runner.state)
        assertEquals(1, runner.progress?.currentQuestionIndex)
    }

    @Test
    fun advanceOnLastQuestion_goesToSessionCompleted() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.SESSION_COMPLETED, runner.state)
    }

    @Test
    fun advanceOnLastQuestionAfterTimeout_goesToSessionCompleted() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onTimeExpired()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.SESSION_COMPLETED, runner.state)
    }

    // ----- El modo clásico no usa intentos para repetir preguntas --------------

    @Test
    fun classicRunner_doesNotRepeatQuestionsOnAnswer() {
        // En modo clásico, no hay reintentos: siempre avanza al siguiente.
        load(activity(question("q1", maxTimeSeconds = 10), question("q2", maxTimeSeconds = 10)))
        reachWaitingResponse()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        // Ahora está en pregunta 2, no en pregunta 1 de nuevo
        assertEquals(1, runner.progress?.currentQuestionIndex)
        assertEquals(ClassicTimerState.PRESENTING_QUESTION, runner.state)
    }

    // ----- Última pregunta -----------------------------------------------------

    @Test
    fun lastQuestionProgress_isLastQuestionTrue() {
        load(activity(question("q1"), question("q2")))
        runner.startSession()
        runner.presentCurrentQuestion()
        runner.startResponseWindow()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        // Ahora en última pregunta
        assertTrue(runner.progress?.isLastQuestion == true)
    }

    @Test
    fun firstQuestionProgress_isLastQuestionFalse() {
        load(activity(question("q1"), question("q2")))
        runner.startSession()
        runner.presentCurrentQuestion()
        assertFalse(runner.progress?.isLastQuestion == true)
    }

    // ----- Cancelación ---------------------------------------------------------

    @Test
    fun cancelSession_transitionsToSessionCancelled() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.cancelSession()
        assertEquals(ClassicTimerState.SESSION_CANCELLED, runner.state)
    }

    @Test
    fun cancelFromIdle_isIgnored() {
        runner.cancelSession()
        assertEquals(ClassicTimerState.IDLE, runner.state)
    }

    // ----- Error técnico -------------------------------------------------------

    @Test
    fun technicalError_transitionsToError() {
        load(activity(question("q1")))
        runner.reportTechnicalError("fallo grave")
        assertEquals(ClassicTimerState.ERROR, runner.state)
        assertEquals("fallo grave", runner.errorMessage)
    }

    // ----- Tiempo efectivo -----------------------------------------------------

    @Test
    fun invalidMaxTime_usesDefault() {
        load(activity(question("q1", maxTimeSeconds = -1)))
        reachWaitingResponse()
        assertEquals(CLASSIC_DEFAULT_MAX_TIME_SECONDS, runner.progress?.effectiveMaxTimeSeconds)
    }

    @Test
    fun progressCarriesMediationKey() {
        load(activity(question("q1", mediationKey = "ANIMAL_DOG_SOUND")))
        reachWaitingResponse()
        assertEquals("ANIMAL_DOG_SOUND", runner.progress?.currentQuestionMediationKey)
    }

    @Test
    fun validMaxTime_usesConfiguredValue() {
        load(activity(question("q1", maxTimeSeconds = 15)))
        reachWaitingResponse()
        assertEquals(15, runner.progress?.effectiveMaxTimeSeconds)
    }

    // ----- Reinicio de sesión --------------------------------------------------

    @Test
    fun reloadAfterCompletion_resetsToIdle_thenReady() {
        load(activity(question("q1")))
        reachWaitingResponse()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.SESSION_COMPLETED, runner.state)

        // Nueva carga desde estado terminal
        load(activity(question("q2")))
        assertEquals(ClassicTimerState.READY, runner.state)
        assertEquals(0, runner.progress?.currentQuestionIndex)
    }
}
