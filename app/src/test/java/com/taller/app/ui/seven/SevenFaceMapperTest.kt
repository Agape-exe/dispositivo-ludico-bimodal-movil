package com.taller.app.ui.seven

import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.classic.ClassicTimerState
import com.taller.app.ui.IntelligentSevenExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SevenFaceMapperTest {

    // ── Modo inteligente: bimodal ──────────────────────────────────────────

    @Test
    fun bimodal_listening_mapsToListening() {
        val result = BimodalInteractionState.LISTENING.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Listening, result)
    }

    @Test
    fun bimodal_evaluating_mapsToThinking() {
        val result = BimodalInteractionState.EVALUATING.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Thinking, result)
    }

    @Test
    fun bimodal_transcribing_mapsToThinking() {
        val result = BimodalInteractionState.TRANSCRIBING.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Thinking, result)
    }

    @Test
    fun bimodal_feedbackCorrect_mapsToCorrect() {
        val result = BimodalInteractionState.FEEDBACK_CORRECT.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Correct, result)
    }

    @Test
    fun bimodal_feedbackIncorrect_mapsToSupportive() {
        val result = BimodalInteractionState.FEEDBACK_INCORRECT.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Supportive, result)
    }

    @Test
    fun bimodal_feedbackNoResponse_mapsToSupportive() {
        val result = BimodalInteractionState.FEEDBACK_NO_RESPONSE.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Supportive, result)
    }

    @Test
    fun bimodal_feedbackNotInterpretable_mapsToRetry() {
        val result = BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Retry, result)
    }

    @Test
    fun bimodal_waitingForFaceAbsent_mapsToAttentionLost() {
        val result = BimodalInteractionState.WAITING_FOR_FACE.toSevenFaceState(
            facePresent = false, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.AttentionLost, result)
    }

    @Test
    fun bimodal_pausedFaceLostFaceAbsent_mapsToAttentionLost() {
        val result = BimodalInteractionState.PAUSED_FACE_LOST.toSevenFaceState(
            facePresent = false, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.AttentionLost, result)
    }

    @Test
    fun bimodal_sessionCompleted_mapsToClosing() {
        val result = BimodalInteractionState.SESSION_COMPLETED.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.Closing, result)
    }

    @Test
    fun bimodal_toyVoiceSpeaking_overridesState_mapsToSpeaking() {
        val result = BimodalInteractionState.LISTENING.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = true
        )
        assertEquals(SevenFaceState.Speaking, result)
    }

    @Test
    fun bimodal_error_mapsToErrorSoft() {
        val result = BimodalInteractionState.ERROR.toSevenFaceState(
            facePresent = true, toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.ErrorSoft, result)
    }

    // ── Seguridad modo temporizador: nunca muestra evaluación ─────────────

    @Test
    fun classic_answerReceived_mapsToSpeaking_notCorrect() {
        // ANSWER_RECEIVED en modo clásico debe mostrar transición neutra (Speaking),
        // nunca Correct ni Supportive que revelarían evaluación al niño.
        val result = ClassicTimerState.ANSWER_RECEIVED.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = false
        )
        assertNotEquals(SevenFaceState.Correct, result)
        assertNotEquals(SevenFaceState.Supportive, result)
        assertNotEquals(SevenFaceState.Retry, result)
        assertEquals(SevenFaceState.Speaking, result)
    }

    @Test
    fun classic_timeExpired_mapsToSpeaking_notIncorrect() {
        val result = ClassicTimerState.TIME_EXPIRED.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = false
        )
        assertNotEquals(SevenFaceState.Correct, result)
        assertNotEquals(SevenFaceState.Supportive, result)
        assertEquals(SevenFaceState.Speaking, result)
    }

    @Test
    fun classic_waitingFixedResponse_mapsToListening() {
        val result = ClassicTimerState.WAITING_FIXED_RESPONSE.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = false
        )
        assertEquals(SevenFaceState.Listening, result)
    }

    @Test
    fun classic_presentingQuestion_mapsToSpeaking() {
        val result = ClassicTimerState.PRESENTING_QUESTION.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = false
        )
        assertEquals(SevenFaceState.Speaking, result)
    }

    @Test
    fun classic_sessionCompleted_mapsToClosing() {
        val result = ClassicTimerState.SESSION_COMPLETED.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = false
        )
        assertEquals(SevenFaceState.Closing, result)
    }

    @Test
    fun classic_paused_mapsToIdle_notListeningOrSpeaking() {
        val result = ClassicTimerState.WAITING_FIXED_RESPONSE.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = true
        )
        assertEquals(SevenFaceState.Idle, result)
    }

    @Test
    fun classic_idle_mapsToIntro() {
        val result = ClassicTimerState.IDLE.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = false
        )
        assertEquals(SevenFaceState.Intro, result)
    }

    @Test
    fun classic_error_mapsToErrorSoft() {
        val result = ClassicTimerState.ERROR.toSevenFaceState(
            toyVoiceSpeaking = false, isPaused = false
        )
        assertEquals(SevenFaceState.ErrorSoft, result)
    }

    @Test
    fun classic_toyVoiceSpeaking_mapsToSpeaking() {
        val result = ClassicTimerState.WAITING_FIXED_RESPONSE.toSevenFaceState(
            toyVoiceSpeaking = true, isPaused = false
        )
        assertEquals(SevenFaceState.Speaking, result)
    }

    // ── Puente desde IntelligentSevenExpression ────────────────────────────

    @Test
    fun bridge_happy_mapsToCorrect() {
        assertEquals(SevenFaceState.Correct, IntelligentSevenExpression.HAPPY.toSevenFaceState())
    }

    @Test
    fun bridge_encouraging_mapsToSupportive() {
        assertEquals(SevenFaceState.Supportive, IntelligentSevenExpression.ENCOURAGING.toSevenFaceState())
    }

    @Test
    fun bridge_searching_mapsToAttentionLost() {
        assertEquals(
            SevenFaceState.AttentionLost,
            IntelligentSevenExpression.SEARCHING_FACE.toSevenFaceState()
        )
    }

    @Test
    fun bridge_celebration_mapsToClosing() {
        assertEquals(SevenFaceState.Closing, IntelligentSevenExpression.CELEBRATION.toSevenFaceState())
    }

    @Test
    fun bridge_thinking_mapsToThinking() {
        assertEquals(SevenFaceState.Thinking, IntelligentSevenExpression.THINKING.toSevenFaceState())
    }
}
