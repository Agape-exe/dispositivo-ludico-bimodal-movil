package com.taller.app.recapture

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.AttentionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RecaptureController(
    private val isIntelligentMode: Boolean = true,
    val maxRecapturesPerSession: Int = RecapturePolicy.MAX_RECAPTURES_PER_SESSION
) {
    private val mutableState = MutableStateFlow(RecaptureState.IDLE)
    val state: StateFlow<RecaptureState> = mutableState

    var attemptsInQuestion: Int = 0
        private set
    var attemptsInSession: Int = 0
        private set
    var lastDecision: RecaptureDecision = RecaptureDecision.Idle
        private set

    private var lastRecaptureStartedAtMs: Long? = null
    private var lastFeedbackAtMs: Long? = null
    private var lastAttentionRestoredAtMs: Long? = null

    fun evaluate(
        attentionSnapshot: AttentionSnapshot,
        flowPhase: FlowPhase,
        nowMs: Long
    ): RecaptureDecision {
        if (!isIntelligentMode) return remember(RecaptureDecision.Suppress(RecaptureReason.SUPPRESSED_BY_FLOW_PHASE))
        val previousRecaptureState = mutableState.value
        mutableState.value = RecaptureState.EVALUATING

        if (flowPhase == FlowPhase.FEEDBACK) {
            lastFeedbackAtMs = nowMs
        }

        val suppression = suppressionFor(flowPhase)
        if (suppression != null) {
            mutableState.value = RecaptureState.MONITORING
            return remember(RecaptureDecision.Suppress(suppression))
        }

        if (attentionSnapshot.state != AttentionState.ATTENTION_LOST) {
            val decision = if (previousRecaptureState == RecaptureState.PENDING ||
                previousRecaptureState == RecaptureState.GENERATING_PHRASE
            ) {
                mutableState.value = RecaptureState.CANCELLED
                RecaptureDecision.Cancel(RecaptureReason.CHILD_RETURNED_BEFORE_SPEECH)
            } else {
                mutableState.value = RecaptureState.IDLE
                RecaptureDecision.Idle
            }
            return remember(decision)
        }

        mutableState.value = RecaptureState.MONITORING

        val lostDurationMs = attentionSnapshot.lostDurationMs.coerceAtLeast(
            nowMs - attentionSnapshot.stateChangedAtMs
        )
        if (lostDurationMs < RecapturePolicy.ATTENTION_LOST_BEFORE_RECAPTURE_MS) {
            return remember(
                RecaptureDecision.WaitMore(
                    RecapturePolicy.ATTENTION_LOST_BEFORE_RECAPTURE_MS - lostDurationMs
                )
            )
        }

        val cooldownRemaining = cooldownRemaining(nowMs)
        if (cooldownRemaining > 0L) {
            return remember(RecaptureDecision.WaitCooldown(cooldownRemaining))
        }

        if (attemptsInSession >= maxRecapturesPerSession) {
            mutableState.value = RecaptureState.EXHAUSTED
            return remember(RecaptureDecision.CloseGracefully(RecaptureReason.LIMIT_REACHED))
        }

        if (attemptsInQuestion >= RecapturePolicy.MAX_RECAPTURES_PER_QUESTION) {
            mutableState.value = RecaptureState.EXHAUSTED
            return remember(RecaptureDecision.CloseGracefully(RecaptureReason.LIMIT_REACHED))
        }

        val reason = when (flowPhase) {
            FlowPhase.BETWEEN_QUESTIONS -> RecaptureReason.ATTENTION_LOST_BETWEEN_QUESTIONS
            FlowPhase.BEFORE_ACTIVITY -> RecaptureReason.ATTENTION_LOST_BEFORE_ACTIVITY_START
            else -> RecaptureReason.ATTENTION_LOST_DURING_ALLOWED_WINDOW
        }
        mutableState.value = RecaptureState.PENDING
        return remember(
            RecaptureDecision.Execute(
                attemptInQuestion = attemptsInQuestion + 1,
                attemptInSession = attemptsInSession + 1,
                reason = reason
            )
        )
    }

    fun onRecaptureStarted(nowMs: Long) {
        attemptsInQuestion += 1
        attemptsInSession += 1
        lastRecaptureStartedAtMs = nowMs
        mutableState.value = RecaptureState.GENERATING_PHRASE
    }

    fun onRecaptureTtsStarted(nowMs: Long) {
        lastRecaptureStartedAtMs = nowMs
        mutableState.value = RecaptureState.SPEAKING
    }

    fun onRecaptureTtsEnded(nowMs: Long) {
        lastRecaptureStartedAtMs = lastRecaptureStartedAtMs ?: nowMs
        mutableState.value = RecaptureState.WAITING_RETURN
    }

    fun onAttentionRestored(nowMs: Long, duringTts: Boolean): RecaptureDecision {
        lastAttentionRestoredAtMs = nowMs
        val decision = if (duringTts) {
            RecaptureDecision.Cancel(RecaptureReason.CHILD_RETURNED_DURING_SPEECH)
        } else {
            RecaptureDecision.Cancel(
                if (mutableState.value == RecaptureState.WAITING_RETURN) {
                    RecaptureReason.CHILD_RETURNED_AFTER_SPEECH
                } else {
                    RecaptureReason.CHILD_RETURNED_BEFORE_SPEECH
                }
            )
        }
        mutableState.value = RecaptureState.CANCELLED
        return remember(decision)
    }

    fun resetForNextQuestion() {
        attemptsInQuestion = 0
        mutableState.value = RecaptureState.IDLE
    }

    fun resetForSession() {
        attemptsInQuestion = 0
        attemptsInSession = 0
        lastRecaptureStartedAtMs = null
        lastFeedbackAtMs = null
        lastAttentionRestoredAtMs = null
        lastDecision = RecaptureDecision.Idle
        mutableState.value = RecaptureState.IDLE
    }

    fun finalSilenceExceeded(nowMs: Long): RecaptureDecision? {
        val started = lastRecaptureStartedAtMs ?: return null
        if (attemptsInQuestion < RecapturePolicy.MAX_RECAPTURES_PER_QUESTION &&
            attemptsInSession < maxRecapturesPerSession
        ) return null
        return if (nowMs - started >= RecapturePolicy.MAX_SILENCE_AFTER_FINAL_MS) {
            mutableState.value = RecaptureState.EXHAUSTED
            remember(RecaptureDecision.CloseGracefully(RecaptureReason.NO_RETURN_AFTER_FINAL_RECAPTURE))
        } else {
            null
        }
    }

    private fun suppressionFor(flowPhase: FlowPhase): RecaptureReason? = when (flowPhase) {
        FlowPhase.SEVEN_SPEAKING ->
            if (RecapturePolicy.SUPPRESS_WHILE_SPEAKING) RecaptureReason.SUPPRESSED_BY_FLOW_PHASE else null
        FlowPhase.STT_LISTENING,
        FlowPhase.CHILD_RESPONDING ->
            if (RecapturePolicy.SUPPRESS_WHILE_LISTENING) RecaptureReason.SUPPRESSED_BY_FLOW_PHASE else null
        FlowPhase.EVALUATING_RESPONSE,
        FlowPhase.ACTIVITY_ENDING -> RecaptureReason.SUPPRESSED_BY_FLOW_PHASE
        FlowPhase.BEFORE_ACTIVITY,
        FlowPhase.FEEDBACK,
        FlowPhase.BETWEEN_QUESTIONS,
        FlowPhase.UNKNOWN -> null
    }

    private fun cooldownRemaining(nowMs: Long): Long {
        val recaptureCooldown = lastRecaptureStartedAtMs?.let {
            RecapturePolicy.RECAPTURE_COOLDOWN_MS - (nowMs - it)
        } ?: 0L
        val feedbackCooldown = lastFeedbackAtMs?.let {
            RecapturePolicy.POST_FEEDBACK_EXTRA_COOLDOWN_MS - (nowMs - it)
        } ?: 0L
        val positiveReturnCooldown = lastAttentionRestoredAtMs?.let {
            RecapturePolicy.POSITIVE_RETURN_COOLDOWN_MS - (nowMs - it)
        } ?: 0L
        return maxOf(recaptureCooldown, feedbackCooldown, positiveReturnCooldown, 0L)
    }

    private fun remember(decision: RecaptureDecision): RecaptureDecision {
        lastDecision = decision
        return decision
    }
}
