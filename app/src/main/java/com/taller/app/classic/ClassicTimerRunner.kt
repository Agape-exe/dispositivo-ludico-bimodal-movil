package com.taller.app.classic

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion

/**
 * Maquina de estados pura del temporizador. Solo modela presentacion, ventana
 * para empezar a hablar y avance; no conoce texto STT, evaluacion ni persistencia.
 */
class ClassicTimerRunner(
    private val responseTimeSeconds: Int = CLASSIC_DEFAULT_MAX_TIME_SECONDS
) {

    var state: ClassicTimerState = ClassicTimerState.IDLE
        private set

    var progress: ClassicTimerProgress? = null
        private set

    var errorMessage: String? = null
        private set

    var onStateChange: ((ClassicTimerState) -> Unit)? = null

    private var activity: LearningActivity? = null
    private var pendingActivity: LearningActivity? = null
    private var questions: List<LearningQuestion> = emptyList()
    private var currentIndex = 0

    fun onEvent(event: ClassicTimerEvent) {
        when (event) {
            is ClassicTimerEvent.LoadActivity -> handleLoadActivity(event.activity)
            ClassicTimerEvent.ActivityLoaded -> handleActivityLoaded()
            ClassicTimerEvent.StartSession -> handleStartSession()
            ClassicTimerEvent.PresentCurrentQuestion -> handlePresentCurrentQuestion()
            ClassicTimerEvent.StartResponseWindow -> handleStartResponseWindow()
            ClassicTimerEvent.ResponseStarted -> handleResponseStarted()
            ClassicTimerEvent.AnswerReceived -> handleAnswerReceived()
            ClassicTimerEvent.TimeExpired -> handleTimeExpired()
            ClassicTimerEvent.AdvanceQuestion -> handleAdvanceQuestion()
            ClassicTimerEvent.CancelSession -> handleCancelSession()
            is ClassicTimerEvent.TechnicalError -> fail(event.message)
        }
    }

    fun loadActivity(activity: LearningActivity) = onEvent(ClassicTimerEvent.LoadActivity(activity))
    fun markActivityLoaded() = onEvent(ClassicTimerEvent.ActivityLoaded)
    fun startSession() = onEvent(ClassicTimerEvent.StartSession)
    fun presentCurrentQuestion() = onEvent(ClassicTimerEvent.PresentCurrentQuestion)
    fun startResponseWindow() = onEvent(ClassicTimerEvent.StartResponseWindow)
    fun onResponseStarted() = onEvent(ClassicTimerEvent.ResponseStarted)
    fun onAnswerReceived() = onEvent(ClassicTimerEvent.AnswerReceived)
    fun onTimeExpired() = onEvent(ClassicTimerEvent.TimeExpired)
    fun advanceQuestion() = onEvent(ClassicTimerEvent.AdvanceQuestion)
    fun cancelSession() = onEvent(ClassicTimerEvent.CancelSession)
    fun reportTechnicalError(message: String) = onEvent(ClassicTimerEvent.TechnicalError(message))

    private fun handleLoadActivity(activity: LearningActivity) {
        if (state != ClassicTimerState.IDLE && !isTerminal(state)) return
        resetSession()
        pendingActivity = activity
        transition(ClassicTimerState.LOADING_ACTIVITY)
    }

    private fun handleActivityLoaded() {
        if (state != ClassicTimerState.LOADING_ACTIVITY) return
        val loaded = pendingActivity ?: return fail("No hay actividad para cargar.")
        activity = loaded
        questions = loaded.questions
        pendingActivity = null
        if (questions.isEmpty()) return fail("La actividad no contiene preguntas.")
        currentIndex = 0
        updateProgress()
        transition(ClassicTimerState.READY)
    }

    private fun handleStartSession() {
        if (state == ClassicTimerState.READY) transition(ClassicTimerState.SESSION_STARTING)
    }

    private fun handlePresentCurrentQuestion() {
        if (state != ClassicTimerState.SESSION_STARTING &&
            state != ClassicTimerState.PRESENTING_QUESTION
        ) return
        updateProgress()
        transition(ClassicTimerState.PRESENTING_QUESTION)
    }

    private fun handleStartResponseWindow() {
        if (state != ClassicTimerState.PRESENTING_QUESTION) return
        transition(ClassicTimerState.WAITING_FIXED_RESPONSE)
    }

    private fun handleResponseStarted() {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE) return
        transition(ClassicTimerState.RESPONSE_IN_PROGRESS)
    }

    private fun handleAnswerReceived() {
        if (state != ClassicTimerState.RESPONSE_IN_PROGRESS &&
            state != ClassicTimerState.WAITING_FIXED_RESPONSE
        ) return
        transition(ClassicTimerState.ANSWER_RECEIVED)
    }

    private fun handleTimeExpired() {
        if (state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            transition(ClassicTimerState.TIME_EXPIRED)
        }
    }

    private fun handleAdvanceQuestion() {
        if (state != ClassicTimerState.ANSWER_RECEIVED &&
            state != ClassicTimerState.TIME_EXPIRED
        ) return
        if (currentIndex >= questions.lastIndex) {
            transition(ClassicTimerState.SESSION_COMPLETED)
            return
        }
        currentIndex += 1
        updateProgress()
        transition(ClassicTimerState.PRESENTING_QUESTION)
    }

    private fun handleCancelSession() {
        if (state != ClassicTimerState.IDLE && !isTerminal(state)) {
            transition(ClassicTimerState.SESSION_CANCELLED)
        }
    }

    private fun updateProgress() {
        val loadedActivity = activity ?: return
        val question = currentQuestion() ?: return
        progress = ClassicTimerProgress(
            activityId = loadedActivity.id,
            activityName = loadedActivity.title,
            totalQuestions = questions.size,
            currentQuestionIndex = currentIndex,
            currentQuestionId = question.id,
            currentQuestionText = question.questionText,
            currentQuestionMediationKey = question.mediationKey,
            maxTimeSeconds = responseTimeSeconds
        )
    }

    private fun fail(message: String) {
        errorMessage = message
        transition(ClassicTimerState.ERROR)
    }

    private fun resetSession() {
        activity = null
        pendingActivity = null
        questions = emptyList()
        currentIndex = 0
        progress = null
        errorMessage = null
    }

    private fun currentQuestion(): LearningQuestion? = questions.getOrNull(currentIndex)

    private fun transition(target: ClassicTimerState) {
        state = target
        onStateChange?.invoke(target)
    }

    private fun isTerminal(value: ClassicTimerState): Boolean =
        value == ClassicTimerState.SESSION_COMPLETED ||
            value == ClassicTimerState.SESSION_CANCELLED ||
            value == ClassicTimerState.ERROR
}
