package com.taller.app.recapture

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.AttentionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecaptureControllerTest {
    @Test
    fun attentionLostLessThanThreshold_waitsMore() {
        val controller = RecaptureController()

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 1_500L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_000L
        )

        assertTrue(decision is RecaptureDecision.WaitMore)
    }

    @Test
    fun attentionLostAfterThresholdBetweenQuestions_executes() {
        val controller = RecaptureController()

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 3_100L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_000L
        )

        assertTrue(decision is RecaptureDecision.Execute)
        decision as RecaptureDecision.Execute
        assertEquals(1, decision.attemptInQuestion)
        assertEquals(1, decision.attemptInSession)
    }

    @Test
    fun suppressedPhases_doNotExecute() {
        val phases = listOf(
            FlowPhase.SEVEN_SPEAKING,
            FlowPhase.STT_LISTENING,
            FlowPhase.CHILD_RESPONDING,
            FlowPhase.EVALUATING_RESPONSE,
            FlowPhase.ACTIVITY_ENDING
        )

        phases.forEach { phase ->
            val decision = RecaptureController().evaluate(
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
                flowPhase = phase,
                nowMs = 10_000L
            )
            assertTrue("phase=$phase", decision is RecaptureDecision.Suppress)
        }
    }

    @Test
    fun temporarilyLost_neverExecutes() {
        val decision = RecaptureController().evaluate(
            attentionSnapshot = snapshot(AttentionState.TEMPORARILY_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_000L
        )

        assertEquals(RecaptureDecision.Idle, decision)
    }

    @Test
    fun attentionStableCancelsPendingRecapture() {
        val controller = RecaptureController()
        controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_000L
        )

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_100L
        )

        assertTrue(decision is RecaptureDecision.Cancel)
        assertEquals(0, controller.attemptsInQuestion)
    }

    @Test
    fun childReturnsBeforeTts_attemptIsNotCounted() {
        val controller = RecaptureController()

        controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_000L
        )
        controller.onAttentionRestored(nowMs = 10_050L, duringTts = false)

        assertEquals(0, controller.attemptsInQuestion)
        assertEquals(0, controller.attemptsInSession)
    }

    @Test
    fun childReturnsDuringTts_attemptIsCounted() {
        val controller = RecaptureController()

        controller.onRecaptureStarted(10_000L)
        controller.onRecaptureTtsStarted(10_010L)
        val decision = controller.onAttentionRestored(nowMs = 10_100L, duringTts = true)

        assertEquals(1, controller.attemptsInQuestion)
        assertEquals(1, controller.attemptsInSession)
        assertTrue(decision is RecaptureDecision.Cancel)
    }

    @Test
    fun cooldownBlocksImmediateRepeat() {
        val controller = RecaptureController()
        controller.onRecaptureStarted(10_000L)

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_500L
        )

        assertTrue(decision is RecaptureDecision.WaitCooldown)
    }

    @Test
    fun maxPerQuestionBlocksThirdAttempt() {
        val controller = RecaptureController()
        controller.onRecaptureStarted(0L)
        controller.onRecaptureStarted(20_000L)

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 40_000L
        )

        assertTrue(decision is RecaptureDecision.CloseGracefully)
    }

    @Test
    fun resetForNextQuestionKeepsSessionCounter() {
        val controller = RecaptureController()
        controller.onRecaptureStarted(0L)

        controller.resetForNextQuestion()

        assertEquals(0, controller.attemptsInQuestion)
        assertEquals(1, controller.attemptsInSession)
    }

    @Test
    fun maxPerSessionBlocksAfterFiveAttempts() {
        val controller = RecaptureController()
        repeat(5) { controller.onRecaptureStarted(it * 20_000L) }
        controller.resetForNextQuestion()

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 120_000L
        )

        assertTrue(decision is RecaptureDecision.CloseGracefully)
    }

    @Test
    fun configurableMaxPerSessionBlocksAtConfiguredLimit() {
        val controller = RecaptureController(maxRecapturesPerSession = 3)
        repeat(3) { controller.onRecaptureStarted(it * 20_000L) }
        controller.resetForNextQuestion()

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 80_000L
        )

        assertTrue(decision is RecaptureDecision.CloseGracefully)
    }

    @Test
    fun zeroMaxPerSessionDoesNotExecuteRecapture() {
        val controller = RecaptureController(maxRecapturesPerSession = 0)

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_000L
        )

        assertTrue(decision is RecaptureDecision.CloseGracefully)
    }

    @Test
    fun fixedTimerModeSuppressesRecapture() {
        val controller = RecaptureController(isIntelligentMode = false)

        val decision = controller.evaluate(
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST, lostDurationMs = 4_000L),
            flowPhase = FlowPhase.BETWEEN_QUESTIONS,
            nowMs = 10_000L
        )

        assertTrue(decision is RecaptureDecision.Suppress)
    }

    private fun snapshot(
        state: AttentionState,
        lostDurationMs: Long = 0L,
        timestampMs: Long = 10_000L
    ) = AttentionSnapshot(
        state = state,
        faceDetected = state != AttentionState.FACE_ABSENT,
        lookingAtDevice = state == AttentionState.ATTENTION_STABLE,
        isAttentionStable = state == AttentionState.ATTENTION_STABLE,
        isTemporarilyLost = state == AttentionState.TEMPORARILY_LOST,
        isAttentionLost = state == AttentionState.ATTENTION_LOST,
        headYawDegrees = null,
        headPitchDegrees = null,
        headRollDegrees = null,
        lastFaceDetectedAtMs = timestampMs,
        lastLookingAtDeviceAtMs = if (state == AttentionState.ATTENTION_STABLE) timestampMs else null,
        lastLookAwayAtMs = if (state != AttentionState.ATTENTION_STABLE) timestampMs else null,
        stateChangedAtMs = timestampMs - lostDurationMs,
        stableDurationMs = if (state == AttentionState.ATTENTION_STABLE) 1_000L else 0L,
        lookAwayDurationMs = lostDurationMs,
        lostDurationMs = lostDurationMs,
        consecutiveStableFrames = if (state == AttentionState.ATTENTION_STABLE) 3 else 0,
        consecutiveLostFrames = if (state == AttentionState.ATTENTION_LOST) 3 else 0
    )
}
