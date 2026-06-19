package com.taller.app.classic

import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion

/**
 * Máquina de estados del modo clásico con temporizador fijo.
 *
 * No conoce voz, STT, timers ni Room: es lógica pura. La pantalla envía
 * eventos ([ClassicTimerEvent]) y reacciona a los cambios de estado.
 * No invoca SemanticEvaluator en ningún momento.
 */
class ClassicTimerRunner(
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    var state: ClassicTimerState = ClassicTimerState.IDLE
        private set

    var progress: ClassicTimerProgress? = null
        private set

    var lastResult: ClassicTimerResult? = null
        private set

    var errorMessage: String? = null
        private set

    var onStateChange: ((ClassicTimerState) -> Unit)? = null

    private var activity: LearningActivity? = null
    private var pendingActivity: LearningActivity? = null
    private var questions: List<LearningQuestion> = emptyList()
    private var currentIndex: Int = 0
    private var sessionStartedAt: Long = 0L
    private var questionStartedAt: Long? = null

    fun onEvent(event: ClassicTimerEvent) {
        when (event) {
            is ClassicTimerEvent.LoadActivity -> handleLoadActivity(event.activity)
            ClassicTimerEvent.ActivityLoaded -> handleActivityLoaded()
            ClassicTimerEvent.StartSession -> handleStartSession()
            ClassicTimerEvent.PresentCurrentQuestion -> handlePresentCurrentQuestion()
            ClassicTimerEvent.StartResponseWindow -> handleStartResponseWindow()
            ClassicTimerEvent.AnswerReceived -> handleAnswerReceived()
            is ClassicTimerEvent.TimeExpired -> handleTimeExpired(event.hadPartialResponse)
            ClassicTimerEvent.AdvanceQuestion -> handleAdvanceQuestion()
            ClassicTimerEvent.CancelSession -> handleCancelSession()
            is ClassicTimerEvent.TechnicalError -> handleTechnicalError(event.message)
        }
    }

    // ----- Convenience helpers -------------------------------------------------

    fun loadActivity(activity: LearningActivity) =
        onEvent(ClassicTimerEvent.LoadActivity(activity))

    fun markActivityLoaded() = onEvent(ClassicTimerEvent.ActivityLoaded)

    fun startSession() = onEvent(ClassicTimerEvent.StartSession)

    fun presentCurrentQuestion() = onEvent(ClassicTimerEvent.PresentCurrentQuestion)

    fun startResponseWindow() = onEvent(ClassicTimerEvent.StartResponseWindow)

    fun onAnswerReceived() = onEvent(ClassicTimerEvent.AnswerReceived)

    fun onTimeExpired(hadPartial: Boolean = false) =
        onEvent(ClassicTimerEvent.TimeExpired(hadPartial))

    fun advanceQuestion() = onEvent(ClassicTimerEvent.AdvanceQuestion)

    fun cancelSession() = onEvent(ClassicTimerEvent.CancelSession)

    fun reportTechnicalError(message: String) =
        onEvent(ClassicTimerEvent.TechnicalError(message))

    // ----- Event handlers ------------------------------------------------------

    private fun handleLoadActivity(activity: LearningActivity) {
        if (state != ClassicTimerState.IDLE && !isTerminal(state)) return
        resetSession()
        pendingActivity = activity
        transition(ClassicTimerState.LOADING_ACTIVITY)
    }

    private fun handleActivityLoaded() {
        if (state != ClassicTimerState.LOADING_ACTIVITY) return
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
        sessionStartedAt = now()
        updateProgress()
        transition(ClassicTimerState.READY)
    }

    private fun handleStartSession() {
        if (state != ClassicTimerState.READY) return
        transition(ClassicTimerState.SESSION_STARTING)
    }

    private fun handlePresentCurrentQuestion() {
        if (state != ClassicTimerState.SESSION_STARTING &&
            state != ClassicTimerState.PRESENTING_QUESTION
        ) return
        questionStartedAt = null
        updateProgress()
        transition(ClassicTimerState.PRESENTING_QUESTION)
    }

    private fun handleStartResponseWindow() {
        if (state != ClassicTimerState.PRESENTING_QUESTION) return
        questionStartedAt = now()
        updateProgress()
        transition(ClassicTimerState.WAITING_FIXED_RESPONSE)
    }

    private fun handleAnswerReceived() {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE) return
        val latency = questionStartedAt?.let { now() - it }
        progress = progress?.copy(
            answerReceived = true,
            responseLatencyMs = latency
        )
        buildResult(answerReceived = true, timedOut = false)
        transition(ClassicTimerState.ANSWER_RECEIVED)
    }

    private fun handleTimeExpired(hadPartial: Boolean) {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE) return
        progress = progress?.copy(
            answerReceived = false,
            hadPartialResponseOnTimeout = hadPartial
        )
        buildResult(answerReceived = false, timedOut = true)
        transition(ClassicTimerState.TIME_EXPIRED)
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
        questionStartedAt = null
        updateProgress()
        transition(ClassicTimerState.PRESENTING_QUESTION)
    }

    private fun handleCancelSession() {
        if (state == ClassicTimerState.IDLE || isTerminal(state)) return
        transition(ClassicTimerState.SESSION_CANCELLED)
    }

    private fun handleTechnicalError(message: String) {
        fail(message)
    }

    // ----- Internal helpers ----------------------------------------------------

    private fun buildResult(answerReceived: Boolean, timedOut: Boolean) {
        val question = currentQuestion() ?: return
        lastResult = ClassicTimerResult(
            questionId = question.id,
            questionIndex = currentIndex,
            maxTimeSeconds = progress?.effectiveMaxTimeSeconds ?: CLASSIC_DEFAULT_MAX_TIME_SECONDS,
            answerReceived = answerReceived,
            responseLatencyMs = progress?.responseLatencyMs,
            isLastQuestion = currentIndex == questions.lastIndex,
            timedOut = timedOut
        )
    }

    private fun updateProgress() {
        val act = activity ?: return
        val question = currentQuestion() ?: return
        val existing = progress
        progress = ClassicTimerProgress(
            activityId = act.id,
            activityName = act.title,
            totalQuestions = questions.size,
            currentQuestionIndex = currentIndex,
            currentQuestionId = question.id,
            currentQuestionText = question.questionText,
            maxTimeSeconds = question.maxTimeSeconds,
            sessionStartedAt = sessionStartedAt,
            questionStartedAt = existing?.questionStartedAt,
            answerReceived = existing?.answerReceived ?: false,
            hadPartialResponseOnTimeout = existing?.hadPartialResponseOnTimeout ?: false,
            lastTranscription = existing?.lastTranscription,
            responseLatencyMs = existing?.responseLatencyMs
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
        sessionStartedAt = 0L
        questionStartedAt = null
        progress = null
        lastResult = null
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
