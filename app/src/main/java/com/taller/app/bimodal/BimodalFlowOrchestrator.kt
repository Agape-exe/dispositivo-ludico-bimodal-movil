package com.taller.app.bimodal

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.semantic.SemanticResult

/**
 * Orquestador del modo bimodal inteligente.
 *
 * Es una maquina de estados independiente de Android: no conoce camara,
 * reconocimiento de voz, sintesis de voz ni persistencia. Recibe eventos
 * ([BimodalInteractionEvent]) a traves de [onEvent] y expone el estado actual
 * ([state]), el progreso ([progress]) y el ultimo resultado ([lastResult]).
 *
 * La conexion con los sensores y servicios reales se realizara en iteraciones
 * posteriores traduciendo sus resultados a eventos de este orquestador.
 *
 * @param now proveedor de tiempo (epoch millis) inyectable para pruebas.
 */
class BimodalFlowOrchestrator(
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    var state: BimodalInteractionState = BimodalInteractionState.IDLE
        private set

    var progress: BimodalQuestionProgress? = null
        private set

    var lastResult: BimodalInteractionResult? = null
        private set

    /**
     * Mensaje asociado a un estado de error: terminal
     * ([BimodalInteractionState.ERROR]) o recuperable
     * ([BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR]).
     */
    var errorMessage: String? = null
        private set

    /** Resumen tecnico en memoria de la sesion en curso. */
    var summary: BimodalSessionSummary = BimodalSessionSummary()
        private set

    /** Listener opcional que recibe cada estado por el que pasa el flujo. */
    var onStateChange: ((BimodalInteractionState) -> Unit)? = null

    private var activity: LearningActivity? = null
    private var pendingActivity: LearningActivity? = null
    private var questions: List<LearningQuestion> = emptyList()

    private var currentIndex: Int = 0
    private var currentAttempt: Int = 1
    private var lastTranscription: String? = null
    private var lastSemanticResult: SemanticResult? = null
    private var sessionStartedAt: Long = 0L
    private var questionStartedAt: Long? = null

    /** Procesa un evento del flujo y aplica la transicion correspondiente. */
    fun onEvent(event: BimodalInteractionEvent) {
        when (event) {
            is BimodalInteractionEvent.LoadActivity -> handleLoadActivity(event.activity)
            BimodalInteractionEvent.ActivityLoaded -> handleActivityLoaded()
            BimodalInteractionEvent.StartQuestion -> handleStartQuestion()
            BimodalInteractionEvent.FaceDetected -> handleFaceDetected()
            BimodalInteractionEvent.FaceLost -> handleFaceLost()
            BimodalInteractionEvent.StartListening -> handleStartListening()
            is BimodalInteractionEvent.SpeechCaptured -> handleSpeechCaptured(event.transcription)
            is BimodalInteractionEvent.SpeechFailed -> handleSpeechFailed(event.reason)
            BimodalInteractionEvent.NoResponse -> handleNoResponse()
            BimodalInteractionEvent.TimeExpired -> handleTimeExpired()
            is BimodalInteractionEvent.SemanticEvaluated -> handleSemanticEvaluated(event.result)
            BimodalInteractionEvent.RetryQuestion -> handleRetryQuestion()
            BimodalInteractionEvent.MoveToNextQuestion -> handleMoveToNextQuestion()
            BimodalInteractionEvent.CompleteSession -> handleCompleteSession()
            BimodalInteractionEvent.CancelSession -> handleCancelSession()
            is BimodalInteractionEvent.TechnicalError -> handleTechnicalError(event.message)
            is BimodalInteractionEvent.RecoverableError -> handleRecoverableError(event.message)
        }
    }

    // ----- Convenience helpers -------------------------------------------------

    fun loadActivity(activity: LearningActivity) =
        onEvent(BimodalInteractionEvent.LoadActivity(activity))

    fun markActivityLoaded() = onEvent(BimodalInteractionEvent.ActivityLoaded)

    fun startSession() = onEvent(BimodalInteractionEvent.StartQuestion)

    fun onFaceDetected() = onEvent(BimodalInteractionEvent.FaceDetected)

    fun onFaceLost() = onEvent(BimodalInteractionEvent.FaceLost)

    fun startListening() = onEvent(BimodalInteractionEvent.StartListening)

    fun onSpeechCaptured(transcription: String) =
        onEvent(BimodalInteractionEvent.SpeechCaptured(transcription))

    fun onSpeechFailed(reason: String? = null) =
        onEvent(BimodalInteractionEvent.SpeechFailed(reason))

    fun onNoResponse() = onEvent(BimodalInteractionEvent.NoResponse)

    fun onTimeExpired() = onEvent(BimodalInteractionEvent.TimeExpired)

    fun onSemanticEvaluated(result: SemanticResult) =
        onEvent(BimodalInteractionEvent.SemanticEvaluated(result))

    fun retryQuestion() = onEvent(BimodalInteractionEvent.RetryQuestion)

    fun moveToNextQuestion() = onEvent(BimodalInteractionEvent.MoveToNextQuestion)

    fun completeSession() = onEvent(BimodalInteractionEvent.CompleteSession)

    fun cancelSession() = onEvent(BimodalInteractionEvent.CancelSession)

    fun reportTechnicalError(message: String) =
        onEvent(BimodalInteractionEvent.TechnicalError(message))

    fun reportRecoverableError(message: String) =
        onEvent(BimodalInteractionEvent.RecoverableError(message))

    // ----- Event handlers ------------------------------------------------------

    private fun handleLoadActivity(activity: LearningActivity) {
        if (state != BimodalInteractionState.IDLE && !isTerminal(state)) return
        resetSession()
        pendingActivity = activity
        transition(BimodalInteractionState.LOADING_ACTIVITY)
    }

    private fun handleActivityLoaded() {
        if (state != BimodalInteractionState.LOADING_ACTIVITY) return
        val loaded = pendingActivity ?: run {
            fail("No hay actividad para cargar.")
            return
        }
        activity = loaded
        questions = loaded.questions
        pendingActivity = null

        if (questions.isEmpty()) {
            fail("La actividad no contiene preguntas.")
            return
        }

        currentIndex = 0
        currentAttempt = 1
        sessionStartedAt = now()
        questionStartedAt = null
        updateProgress()
        transition(BimodalInteractionState.READY)
    }

    private fun handleStartQuestion() {
        if (state != BimodalInteractionState.READY) return
        transition(BimodalInteractionState.WAITING_FOR_FACE)
    }

    private fun handleFaceDetected() {
        if (state != BimodalInteractionState.WAITING_FOR_FACE) return
        transition(BimodalInteractionState.FACE_DETECTED)
        presentCurrentQuestion()
    }

    private fun handleFaceLost() {
        when (state) {
            BimodalInteractionState.FACE_DETECTED,
            BimodalInteractionState.PRESENTING_QUESTION,
            BimodalInteractionState.WAITING_FOR_RESPONSE,
            BimodalInteractionState.LISTENING ->
                transition(BimodalInteractionState.WAITING_FOR_FACE)
            else -> Unit
        }
    }

    private fun handleStartListening() {
        if (state != BimodalInteractionState.PRESENTING_QUESTION) return
        transition(BimodalInteractionState.WAITING_FOR_RESPONSE)
        transition(BimodalInteractionState.LISTENING)
    }

    private fun handleSpeechCaptured(transcription: String) {
        if (state != BimodalInteractionState.LISTENING) return
        lastTranscription = transcription
        updateProgress()
        transition(BimodalInteractionState.TRANSCRIBING)
        transition(BimodalInteractionState.EVALUATING)
    }

    private fun handleSpeechFailed(reason: String?) {
        if (state != BimodalInteractionState.LISTENING) return
        // Un fallo del reconocedor es un problema tecnico, no una respuesta del
        // nino: no se clasifica como incorrecta ni como no interpretable.
        applyRecoverableError(reason ?: "No se pudo procesar la voz.")
    }

    private fun handleNoResponse() {
        when (state) {
            BimodalInteractionState.PRESENTING_QUESTION,
            BimodalInteractionState.WAITING_FOR_RESPONSE,
            BimodalInteractionState.LISTENING ->
                applyResponseResult(SemanticResult.NO_RESPONSE)
            else -> Unit
        }
    }

    private fun handleTimeExpired() {
        when (state) {
            BimodalInteractionState.PRESENTING_QUESTION,
            BimodalInteractionState.WAITING_FOR_RESPONSE,
            BimodalInteractionState.LISTENING -> {
                lastSemanticResult = SemanticResult.NO_RESPONSE
                buildResult(SemanticResult.NO_RESPONSE, BimodalInteractionState.TIME_EXPIRED)
                updateProgress()
                transition(BimodalInteractionState.TIME_EXPIRED)
            }
            else -> Unit
        }
    }

    private fun handleSemanticEvaluated(result: SemanticResult) {
        if (state != BimodalInteractionState.EVALUATING) return
        applyResponseResult(result)
    }

    private fun handleRetryQuestion() {
        val result = lastResult
        if (!isResolvedQuestionState(state) || result == null || !result.canRetry) return
        currentAttempt += 1
        lastTranscription = null
        lastSemanticResult = null
        errorMessage = null
        updateProgress()
        presentCurrentQuestion()
    }

    private fun handleMoveToNextQuestion() {
        if (!isResolvedQuestionState(state)) return
        recordCurrentQuestionOutcome()
        errorMessage = null
        transition(BimodalInteractionState.NEXT_QUESTION)
        if (currentIndex >= questions.lastIndex) {
            transition(BimodalInteractionState.SESSION_COMPLETED)
            return
        }
        currentIndex += 1
        currentAttempt = 1
        lastTranscription = null
        lastSemanticResult = null
        questionStartedAt = null
        updateProgress()
        transition(BimodalInteractionState.WAITING_FOR_FACE)
    }

    private fun handleCompleteSession() {
        if (isTerminal(state)) return
        transition(BimodalInteractionState.SESSION_COMPLETED)
    }

    private fun handleCancelSession() {
        if (isTerminal(state)) return
        transition(BimodalInteractionState.SESSION_CANCELLED)
    }

    private fun handleTechnicalError(message: String) {
        fail(message)
    }

    private fun handleRecoverableError(message: String) {
        // Solo tiene sentido durante una pregunta en curso; en cualquier otro
        // estado se ignora para no corromper el flujo.
        when (state) {
            BimodalInteractionState.FACE_DETECTED,
            BimodalInteractionState.PRESENTING_QUESTION,
            BimodalInteractionState.WAITING_FOR_RESPONSE,
            BimodalInteractionState.LISTENING,
            BimodalInteractionState.TRANSCRIBING,
            BimodalInteractionState.EVALUATING -> applyRecoverableError(message)
            else -> Unit
        }
    }

    // ----- Internal logic ------------------------------------------------------

    private fun presentCurrentQuestion() {
        questionStartedAt = now()
        updateProgress()
        transition(BimodalInteractionState.PRESENTING_QUESTION)
    }

    private fun applyResponseResult(result: SemanticResult) {
        lastSemanticResult = result
        val feedbackState = feedbackStateFor(result)
        buildResult(result, feedbackState)
        updateProgress()
        transition(feedbackState)
    }

    private fun applyRecoverableError(message: String) {
        // No es la respuesta del nino: el resultado semantico queda nulo. La
        // pregunta puede reintentarse si aun quedan intentos disponibles.
        errorMessage = message
        buildResult(result = null, feedbackState = BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR)
        updateProgress()
        transition(BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR)
    }

    private fun buildResult(result: SemanticResult?, feedbackState: BimodalInteractionState) {
        val question = currentQuestion() ?: return
        val canRetry = result != SemanticResult.CORRECT &&
            currentAttempt < question.maxAttempts
        lastResult = BimodalInteractionResult(
            questionId = question.id,
            questionIndex = currentIndex,
            attempt = currentAttempt,
            maxAttempts = question.maxAttempts,
            transcription = lastTranscription,
            semanticResult = result,
            feedbackState = feedbackState,
            canRetry = canRetry,
            isLastQuestion = currentIndex == questions.lastIndex
        )
    }

    private fun feedbackStateFor(result: SemanticResult): BimodalInteractionState =
        when (result) {
            SemanticResult.CORRECT -> BimodalInteractionState.FEEDBACK_CORRECT
            SemanticResult.INCORRECT -> BimodalInteractionState.FEEDBACK_INCORRECT
            SemanticResult.NOT_INTERPRETABLE -> BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE
            SemanticResult.NO_RESPONSE -> BimodalInteractionState.FEEDBACK_NO_RESPONSE
        }

    private fun fail(message: String) {
        errorMessage = message
        transition(BimodalInteractionState.ERROR)
    }

    private fun resetSession() {
        activity = null
        questions = emptyList()
        currentIndex = 0
        currentAttempt = 1
        lastTranscription = null
        lastSemanticResult = null
        sessionStartedAt = 0L
        questionStartedAt = null
        progress = null
        lastResult = null
        errorMessage = null
        summary = BimodalSessionSummary()
    }

    /**
     * Registra en el resumen el desenlace final de la pregunta actual segun el
     * estado resuelto vigente, sumando los intentos consumidos. Se invoca una sola
     * vez por pregunta, justo antes de avanzar o finalizar.
     */
    private fun recordCurrentQuestionOutcome() {
        val category = when (state) {
            BimodalInteractionState.FEEDBACK_CORRECT -> BimodalOutcomeCategory.CORRECT
            BimodalInteractionState.FEEDBACK_INCORRECT -> BimodalOutcomeCategory.INCORRECT
            BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ->
                BimodalOutcomeCategory.NOT_INTERPRETABLE
            BimodalInteractionState.FEEDBACK_NO_RESPONSE -> BimodalOutcomeCategory.NO_RESPONSE
            BimodalInteractionState.TIME_EXPIRED -> BimodalOutcomeCategory.TIME_EXPIRED
            BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR ->
                BimodalOutcomeCategory.TECHNICAL_ERROR
            else -> return
        }
        summary = summary.recording(category, attemptsUsed = currentAttempt)
    }

    private fun currentQuestion(): LearningQuestion? = questions.getOrNull(currentIndex)

    private fun updateProgress() {
        val act = activity ?: return
        val question = currentQuestion() ?: return
        progress = BimodalQuestionProgress(
            activityId = act.id,
            activityName = act.title,
            totalQuestions = questions.size,
            currentQuestionIndex = currentIndex,
            currentQuestionId = question.id,
            currentQuestionText = question.questionText,
            currentAttempt = currentAttempt,
            maxAttempts = question.maxAttempts,
            maxTimeSeconds = question.maxTimeSeconds,
            lastTranscription = lastTranscription,
            lastSemanticResult = lastSemanticResult,
            sessionStartedAt = sessionStartedAt,
            questionStartedAt = questionStartedAt
        )
    }

    private fun transition(target: BimodalInteractionState) {
        state = target
        onStateChange?.invoke(target)
    }

    private fun isFeedbackState(value: BimodalInteractionState): Boolean =
        value == BimodalInteractionState.FEEDBACK_CORRECT ||
            value == BimodalInteractionState.FEEDBACK_INCORRECT ||
            value == BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ||
            value == BimodalInteractionState.FEEDBACK_NO_RESPONSE ||
            value == BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR

    /**
     * Estados en los que la pregunta actual ya tiene un desenlace y admite
     * reintentar (si quedan intentos) o avanzar: retroalimentaciones y tiempo
     * agotado.
     */
    private fun isResolvedQuestionState(value: BimodalInteractionState): Boolean =
        isFeedbackState(value) || value == BimodalInteractionState.TIME_EXPIRED

    private fun isTerminal(value: BimodalInteractionState): Boolean =
        value == BimodalInteractionState.SESSION_COMPLETED ||
            value == BimodalInteractionState.SESSION_CANCELLED ||
            value == BimodalInteractionState.ERROR
}
