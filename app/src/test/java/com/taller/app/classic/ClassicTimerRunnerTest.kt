package com.taller.app.classic

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClassicTimerRunnerTest {

    private lateinit var runner: ClassicTimerRunner

    @Before
    fun setUp() {
        runner = ClassicTimerRunner()
    }

    private fun question(id: String, mediationKey: String? = null) = LearningQuestion(
        id = id,
        questionText = "Pregunta $id",
        expectedAnswer = "respuesta que el temporizador no usa",
        keywords = listOf("respuesta"),
        maxTimeSeconds = 99,
        maxAttempts = 3,
        mediationKey = mediationKey
    )

    private fun activity(vararg questions: LearningQuestion) = LearningActivity(
        id = "act-1",
        title = "Actividad de prueba",
        mode = OperationMode.CLASSIC,
        questions = questions.toList()
    )

    private fun load(vararg questions: LearningQuestion) {
        runner.loadActivity(activity(*questions))
        runner.markActivityLoaded()
    }

    private fun reachResponseWindow() {
        runner.startSession()
        runner.presentCurrentQuestion()
        runner.startResponseWindow()
    }

    @Test
    fun completeFlowIsIntroQuestionWaitNextQuestionAndClosing() {
        load(question("q1"), question("q2"))
        assertEquals(ClassicTimerState.READY, runner.state)
        runner.startSession()
        assertEquals(ClassicTimerState.SESSION_STARTING, runner.state)
        runner.presentCurrentQuestion()
        runner.startResponseWindow()
        runner.onTimeExpired()
        runner.advanceQuestion()
        assertEquals(1, runner.progress?.currentQuestionIndex)
        runner.startResponseWindow()
        runner.onResponseStarted()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.SESSION_COMPLETED, runner.state)
    }

    @Test
    fun timeoutWithoutSpeechAdvancesToNextQuestion() {
        load(question("q1"), question("q2"))
        reachResponseWindow()
        runner.onTimeExpired()
        assertEquals(ClassicTimerState.TIME_EXPIRED, runner.state)
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.PRESENTING_QUESTION, runner.state)
        assertEquals(1, runner.progress?.currentQuestionIndex)
    }

    @Test
    fun responseStartedBeforeTimeoutKeepsQuestionOpenUntilSpeechEnds() {
        load(question("q1"), question("q2"))
        reachResponseWindow()
        runner.onResponseStarted()
        assertEquals(ClassicTimerState.RESPONSE_IN_PROGRESS, runner.state)

        runner.onTimeExpired()
        assertEquals(ClassicTimerState.RESPONSE_IN_PROGRESS, runner.state)

        runner.onAnswerReceived()
        assertEquals(ClassicTimerState.ANSWER_RECEIVED, runner.state)
        runner.advanceQuestion()
        assertEquals(1, runner.progress?.currentQuestionIndex)
    }

    @Test
    fun safetyCompletionUsesSameNeutralEndEvent() {
        load(question("q1"))
        reachResponseWindow()
        runner.onResponseStarted()
        runner.onAnswerReceived()
        runner.advanceQuestion()
        assertEquals(ClassicTimerState.SESSION_COMPLETED, runner.state)
    }

    @Test
    fun answerCanFinishEvenWhenRecognizerOnlyReturnsFinalText() {
        load(question("q1"))
        reachResponseWindow()
        runner.onAnswerReceived()
        assertEquals(ClassicTimerState.ANSWER_RECEIVED, runner.state)
    }

    @Test
    fun runnerHasNoEvaluationPersistenceOrCapturedAnswerSurface() {
        val surface = (ClassicTimerRunner::class.java.declaredFields.map { it.name + it.type.name } +
            ClassicTimerRunner::class.java.declaredMethods.map { it.name }).joinToString(" ").lowercase()

        assertFalse(surface.contains("semantic"))
        assertFalse(surface.contains("transcript"))
        assertFalse(surface.contains("logger"))
        assertFalse(surface.contains("correct"))
        assertFalse(surface.contains("attempt"))
        assertFalse(surface.contains("result"))
    }

    @Test
    fun noRetriesOrFeedbackStatesExist() {
        val states = ClassicTimerState.entries.map { it.name }
        assertFalse(states.any { it.contains("RETRY") || it.contains("FEEDBACK") })
    }

    @Test
    fun centralTimeAppliesToEveryQuestion() {
        runner = ClassicTimerRunner(responseTimeSeconds = 20)
        load(question("q1"), question("q2"))
        reachResponseWindow()
        assertEquals(20, runner.progress?.effectiveMaxTimeSeconds)
        runner.onTimeExpired()
        runner.advanceQuestion()
        assertEquals(20, runner.progress?.effectiveMaxTimeSeconds)
    }

    @Test
    fun defaultTimeIsUsedWhenCentralValueIsInvalid() {
        runner = ClassicTimerRunner(responseTimeSeconds = -1)
        load(question("q1"))
        reachResponseWindow()
        assertEquals(CLASSIC_DEFAULT_MAX_TIME_SECONDS, runner.progress?.effectiveMaxTimeSeconds)
    }

    @Test
    fun emptyActivityFailsClearly() {
        load()
        assertEquals(ClassicTimerState.ERROR, runner.state)
        assertNotNull(runner.errorMessage)
    }

    @Test
    fun progressCarriesPresentationDataOnly() {
        load(question("q1", mediationKey = "ANIMAL_DOG_SOUND"))
        assertEquals("ANIMAL_DOG_SOUND", runner.progress?.currentQuestionMediationKey)
        assertTrue(runner.progress?.isLastQuestion == true)
    }

    @Test
    fun cancelActiveSessionIsTerminal() {
        load(question("q1"))
        reachResponseWindow()
        runner.cancelSession()
        assertEquals(ClassicTimerState.SESSION_CANCELLED, runner.state)
    }
}
