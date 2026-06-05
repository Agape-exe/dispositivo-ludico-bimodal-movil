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
    fun lastQuestionResult_isFlaggedAsLastQuestion() {
        load(activity(question("q1"), question("q2")))

        // q1 (intermedia): el resultado no es la ultima pregunta.
        reachListening()
        orchestrator.onSpeechCaptured("respuesta")
        orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)
        assertFalse(orchestrator.lastResult!!.isLastQuestion)
        assertEquals(BimodalAutoAction.ADVANCE, orchestrator.resolveAutoAction())

        // q2 (ultima): el resultado se marca como ultima pregunta y completa.
        orchestrator.moveToNextQuestion()
        orchestrator.onFaceDetected()
        orchestrator.startListening()
        orchestrator.onSpeechCaptured("respuesta")
        orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)
        assertTrue(orchestrator.lastResult!!.isLastQuestion)
        assertEquals(BimodalAutoAction.COMPLETE, orchestrator.resolveAutoAction())

        // Avanzar desde la ultima pregunta finaliza la sesion directamente.
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

    @Test
    fun notInterpretableWithoutAttemptsLeft_advancesToNextQuestion() {
        load(activity(question("q1", maxAttempts = 1), question("q2")))

        reachListening()
        orchestrator.onSpeechCaptured("mmm")
        orchestrator.onSemanticEvaluated(SemanticResult.NOT_INTERPRETABLE)

        assertEquals(BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE, orchestrator.state)
        assertFalse(orchestrator.lastResult!!.canRetry)

        orchestrator.moveToNextQuestion()
        assertEquals(BimodalInteractionState.WAITING_FOR_FACE, orchestrator.state)
        assertEquals(1, orchestrator.progress?.currentQuestionIndex)
    }

    @Test
    fun noResponseWithAttemptsLeft_allowsRetry() {
        load(activity(question("q1", maxAttempts = 2)))

        reachListening()
        orchestrator.onNoResponse()

        assertEquals(BimodalInteractionState.FEEDBACK_NO_RESPONSE, orchestrator.state)
        assertTrue(orchestrator.lastResult!!.canRetry)

        orchestrator.retryQuestion()
        assertEquals(BimodalInteractionState.PRESENTING_QUESTION, orchestrator.state)
        assertEquals(2, orchestrator.progress?.currentAttempt)
    }

    @Test
    fun noResponseWithoutAttemptsLeft_advancesToNextQuestion() {
        load(activity(question("q1", maxAttempts = 1), question("q2")))

        reachListening()
        orchestrator.onNoResponse()

        assertEquals(BimodalInteractionState.FEEDBACK_NO_RESPONSE, orchestrator.state)
        assertFalse(orchestrator.lastResult!!.canRetry)

        orchestrator.moveToNextQuestion()
        assertEquals(BimodalInteractionState.WAITING_FOR_FACE, orchestrator.state)
        assertEquals(1, orchestrator.progress?.currentQuestionIndex)
    }

    @Test
    fun timeExpiredWithAttemptsLeft_allowsRetry() {
        load(activity(question("q1", maxAttempts = 2)))

        reachListening()
        orchestrator.onTimeExpired()

        assertEquals(BimodalInteractionState.TIME_EXPIRED, orchestrator.state)
        assertTrue(orchestrator.lastResult!!.canRetry)

        orchestrator.retryQuestion()
        assertEquals(BimodalInteractionState.PRESENTING_QUESTION, orchestrator.state)
        assertEquals(2, orchestrator.progress?.currentAttempt)
    }

    @Test
    fun timeExpiredWithoutAttemptsLeft_advancesToNextQuestion() {
        load(activity(question("q1", maxAttempts = 1), question("q2")))

        reachListening()
        orchestrator.onTimeExpired()

        assertEquals(BimodalInteractionState.TIME_EXPIRED, orchestrator.state)
        assertFalse(orchestrator.lastResult!!.canRetry)

        // Sin intentos, el reintento desde tiempo agotado no debe tener efecto.
        orchestrator.retryQuestion()
        assertEquals(BimodalInteractionState.TIME_EXPIRED, orchestrator.state)

        orchestrator.moveToNextQuestion()
        assertEquals(BimodalInteractionState.WAITING_FOR_FACE, orchestrator.state)
        assertEquals(1, orchestrator.progress?.currentQuestionIndex)
    }

    @Test
    fun technicalError_isNotClassifiedAsIncorrect_andAllowsRetry() {
        load(activity(question("q1", maxAttempts = 2)))

        reachListening()
        orchestrator.reportRecoverableError("fallo de voz")

        assertEquals(BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR, orchestrator.state)
        // No es la respuesta del nino: sin resultado semantico y nunca incorrecta.
        assertNull(orchestrator.lastResult!!.semanticResult)
        assertEquals("fallo de voz", orchestrator.errorMessage)
        assertTrue(orchestrator.lastResult!!.canRetry)

        orchestrator.retryQuestion()
        assertEquals(BimodalInteractionState.PRESENTING_QUESTION, orchestrator.state)
        assertEquals(2, orchestrator.progress?.currentAttempt)
        // El mensaje de error se limpia al reintentar.
        assertNull(orchestrator.errorMessage)
    }

    @Test
    fun technicalErrorWithoutAttemptsLeft_advancesToNextQuestion() {
        load(activity(question("q1", maxAttempts = 1), question("q2")))

        reachListening()
        orchestrator.reportRecoverableError("fallo de voz")

        assertEquals(BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR, orchestrator.state)
        assertFalse(orchestrator.lastResult!!.canRetry)

        orchestrator.moveToNextQuestion()
        assertEquals(BimodalInteractionState.WAITING_FOR_FACE, orchestrator.state)
        assertEquals(1, orchestrator.progress?.currentQuestionIndex)
        assertNull(orchestrator.errorMessage)
    }

    @Test
    fun speechFailed_whileListening_producesTechnicalError() {
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechFailed("reconocedor ocupado")

        assertEquals(BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR, orchestrator.state)
        assertNull(orchestrator.lastResult!!.semanticResult)
    }

    @Test
    fun sessionSummary_countsEachAttemptInItsCategory() {
        load(activity(question("q1", maxAttempts = 2), question("q2", maxAttempts = 1)))

        // q1: incorrecta (intento 1) -> reintento -> correcta (intento 2).
        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)
        orchestrator.retryQuestion()
        orchestrator.startListening()
        orchestrator.onSpeechCaptured("respuesta")
        orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)
        orchestrator.moveToNextQuestion()

        // q2: sin respuesta (intento 1, sin mas intentos) -> avanza y finaliza.
        orchestrator.onFaceDetected()
        orchestrator.startListening()
        orchestrator.onNoResponse()
        orchestrator.moveToNextQuestion()

        assertEquals(BimodalInteractionState.SESSION_COMPLETED, orchestrator.state)
        val summary = orchestrator.summary
        assertEquals(2, summary.resolvedQuestions)
        // El intento incorrecto se contabiliza aunque despues se acierte: no se pierde.
        assertEquals(1, summary.correct)
        assertEquals(1, summary.incorrect)
        assertEquals(1, summary.noResponse)
        // 3 intentos contabilizados: q1 (incorrecto + correcto) y q2 (sin respuesta).
        assertEquals(3, summary.totalAttempts)
    }

    @Test
    fun twoIncorrectAttempts_incrementIncorrectByTwo() {
        // Pregunta con maxAttempts = 2: ambos intentos incorrectos cuentan.
        load(activity(question("q1", maxAttempts = 2)))

        // Intento 1 incorrecto -> permite reintento.
        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)
        assertEquals(1, orchestrator.summary.incorrect)
        assertTrue(orchestrator.lastResult!!.canRetry)

        // Intento 2 incorrecto -> termina la pregunta.
        orchestrator.retryQuestion()
        orchestrator.startListening()
        orchestrator.onSpeechCaptured("otra cosa mas")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)
        assertEquals(2, orchestrator.summary.incorrect)
        assertFalse(orchestrator.lastResult!!.canRetry)

        orchestrator.completeSession()
        assertEquals(2, orchestrator.summary.incorrect)
        assertEquals(0, orchestrator.summary.correct)
        assertEquals(1, orchestrator.summary.resolvedQuestions)
        assertEquals(2, orchestrator.summary.totalAttempts)
    }

    @Test
    fun incorrectAttempt_doesNotIncrementCorrect() {
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)

        assertEquals(1, orchestrator.summary.incorrect)
        assertEquals(0, orchestrator.summary.correct)
    }

    @Test
    fun notInterpretableAttempt_doesNotIncrementIncorrect() {
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechCaptured("mmm")
        orchestrator.onSemanticEvaluated(SemanticResult.NOT_INTERPRETABLE)

        assertEquals(1, orchestrator.summary.notInterpretable)
        assertEquals(0, orchestrator.summary.incorrect)
    }

    @Test
    fun sttErrorAttempt_doesNotIncrementIncorrect() {
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechFailed("reconocedor ocupado")

        assertEquals(1, orchestrator.summary.sttErrors)
        assertEquals(0, orchestrator.summary.incorrect)
        assertEquals(0, orchestrator.summary.technicalErrors)
    }

    @Test
    fun sameAttempt_isNotCountedTwice() {
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)
        // Reenviar la misma evaluacion estando ya en feedback no debe recontar.
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)

        assertEquals(1, orchestrator.summary.incorrect)
        assertEquals(1, orchestrator.summary.totalAttempts)
    }

    @Test
    fun resolveAutoAction_retriesWhenAttemptsRemain() {
        load(activity(question("q1", maxAttempts = 2)))

        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)

        assertEquals(BimodalAutoAction.RETRY, orchestrator.resolveAutoAction())
    }

    @Test
    fun resolveAutoAction_advancesWhenNoAttemptsAndNotLast() {
        load(activity(question("q1", maxAttempts = 1), question("q2")))

        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)

        assertEquals(BimodalAutoAction.ADVANCE, orchestrator.resolveAutoAction())
    }

    @Test
    fun resolveAutoAction_completesOnLastQuestion() {
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechCaptured("respuesta")
        orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)

        assertEquals(BimodalAutoAction.COMPLETE, orchestrator.resolveAutoAction())
    }

    @Test
    fun resolveAutoAction_isNoneWhenNotResolved() {
        load(activity(question("q1")))
        reachListening()

        assertEquals(BimodalAutoAction.NONE, orchestrator.resolveAutoAction())
    }

    @Test
    fun completeSession_recordsPendingOutcome() {
        // Reproduce el caso reportado: la voz no se reconoce (sin respuesta) y la
        // sesion se finaliza sin avanzar manualmente. El desenlace debe contarse.
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onNoResponse()
        assertEquals(BimodalInteractionState.FEEDBACK_NO_RESPONSE, orchestrator.state)

        orchestrator.completeSession()

        assertEquals(BimodalInteractionState.SESSION_COMPLETED, orchestrator.state)
        assertEquals(1, orchestrator.summary.noResponse)
        assertEquals(1, orchestrator.summary.resolvedQuestions)
        assertEquals(1, orchestrator.summary.totalAttempts)
    }

    @Test
    fun cancelSession_recordsPendingOutcome() {
        load(activity(question("q1", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechCaptured("otra cosa")
        orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)

        orchestrator.cancelSession()

        assertEquals(BimodalInteractionState.SESSION_CANCELLED, orchestrator.state)
        assertEquals(1, orchestrator.summary.incorrect)
        assertEquals(1, orchestrator.summary.resolvedQuestions)
    }

    @Test
    fun advanceThenComplete_doesNotDoubleCountOutcome() {
        // Avanzar contabiliza la pregunta; completar despues no debe volver a contarla.
        load(activity(question("q1", maxAttempts = 1), question("q2", maxAttempts = 1)))

        reachListening()
        orchestrator.onSpeechCaptured("respuesta")
        orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)
        orchestrator.moveToNextQuestion() // cuenta q1, queda esperando rostro en q2

        orchestrator.completeSession() // q2 no tiene desenlace: no agrega conteos

        assertEquals(1, orchestrator.summary.correct)
        assertEquals(1, orchestrator.summary.resolvedQuestions)
        assertEquals(1, orchestrator.summary.totalAttempts)
    }

    @Test
    fun cancelWithoutResolvedQuestion_recordsNothing() {
        load(activity(question("q1")))
        orchestrator.startSession() // WAITING_FOR_FACE, sin desenlace

        orchestrator.cancelSession()

        assertEquals(0, orchestrator.summary.resolvedQuestions)
    }

    @Test
    fun progress_resolvesEffectiveMaxTimeWithSafeDefault() {
        // maxTimeSeconds invalido (0) -> usa el valor seguro por defecto.
        load(activity(question("q1", maxTimeSeconds = 0)))
        assertEquals(DEFAULT_MAX_TIME_SECONDS, orchestrator.progress?.effectiveMaxTimeSeconds)

        // maxTimeSeconds valido -> se respeta.
        setUp()
        load(activity(question("q2", maxTimeSeconds = 25)))
        assertEquals(25, orchestrator.progress?.effectiveMaxTimeSeconds)
    }
}
