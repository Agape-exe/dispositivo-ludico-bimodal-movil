package com.taller.app.ui.face

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.AttentionState
import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.classic.ClassicTimerState
import com.taller.app.ui.IntelligentSevenExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SevenFaceMapperTest {

    // ----- Modo inteligente -------------------------------------------------------

    @Test
    fun intelligentSpeakingMapsToSpeaking() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.PRESENTING_QUESTION,
            facePresent = true,
            toyVoiceSpeaking = true
        )
        assertEquals(SevenFaceState.SPEAKING, face)
    }

    @Test
    fun intelligentListeningMapsToListening() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.LISTENING,
            facePresent = true,
            toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.LISTENING, face)
    }

    @Test
    fun intelligentEvaluatingMapsToThinking() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.EVALUATING,
            facePresent = true,
            toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.THINKING, face)
    }

    @Test
    fun intelligentCorrectMapsToHappy() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.FEEDBACK_CORRECT,
            facePresent = true,
            toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.HAPPY, face)
    }

    @Test
    fun intelligentIncorrectWithAttemptsMapsToSupportive() {
        listOf(
            BimodalInteractionState.FEEDBACK_INCORRECT,
            BimodalInteractionState.FEEDBACK_NO_RESPONSE,
            BimodalInteractionState.TIME_EXPIRED,
            BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR
        ).forEach { state ->
            val face = intelligentSevenFaceState(
                interactionState = state,
                facePresent = true,
                toyVoiceSpeaking = false
            )
            assertEquals("estado $state", SevenFaceState.SUPPORTIVE, face)
        }
    }

    @Test
    fun intelligentNotInterpretableMapsToRetry() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE,
            facePresent = true,
            toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.RETRY, face)
    }

    @Test
    fun intelligentSessionCompletedMapsToClosing() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.SESSION_COMPLETED,
            facePresent = true,
            toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.CLOSING, face)
    }

    @Test
    fun intelligentTerminalErrorMapsToErrorSoft() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.ERROR,
            facePresent = true,
            toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.ERROR_SOFT, face)
    }

    @Test
    fun intelligentCancelledMapsToCalmWaiting() {
        val face = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.SESSION_CANCELLED,
            facePresent = true,
            toyVoiceSpeaking = false
        )
        assertEquals(SevenFaceState.WAITING, face)
    }

    @Test
    fun intelligentRenderKeepsHeldExpressionInNonTerminalStates() {
        // La pantalla retiene expresiones (duracion minima): el render respeta la
        // expresion retenida mientras el estado no sea terminal.
        val face = sevenFaceStateForIntelligentRender(
            interactionState = BimodalInteractionState.NEXT_QUESTION,
            resolvedExpression = IntelligentSevenExpression.HAPPY
        )
        assertEquals(SevenFaceState.HAPPY, face)
    }

    // ----- Seguridad visual: camara y depuracion ----------------------------------

    @Test
    fun intelligentFaceDoesNotRequireCamera() {
        // Sin rostro detectado y sin atencion funcional, la cara sigue el flujo:
        // hablar y escuchar se ven igual con o sin camara.
        val speaking = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.PRESENTING_QUESTION,
            facePresent = false,
            toyVoiceSpeaking = true,
            attentionSnapshot = null,
            attentionDrivenVisualsEnabled = false
        )
        assertEquals(SevenFaceState.SPEAKING, speaking)

        val listening = intelligentSevenFaceState(
            interactionState = BimodalInteractionState.LISTENING,
            facePresent = false,
            toyVoiceSpeaking = false,
            attentionSnapshot = null,
            attentionDrivenVisualsEnabled = false
        )
        assertEquals(SevenFaceState.LISTENING, listening)
    }

    @Test
    fun attentionSnapshotDoesNotChangeFaceWhenFunctionalAttentionIsOff() {
        // Mostrar el panel de atencion solo agrega texto tecnico: con la atencion
        // funcional apagada, un snapshot de atencion no altera la cara de Seven.
        BimodalInteractionState.entries.forEach { state ->
            val withoutSnapshot = intelligentSevenFaceState(
                interactionState = state,
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = null,
                attentionDrivenVisualsEnabled = false
            )
            val withSnapshot = intelligentSevenFaceState(
                interactionState = state,
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
                attentionDrivenVisualsEnabled = false
            )
            assertEquals("estado $state", withoutSnapshot, withSnapshot)
        }
    }

    // ----- Modo temporizador: solo estados neutrales -------------------------------

    @Test
    fun classicSpokenQuestionMapsToSpeaking() {
        val face = classicSevenFaceState(
            state = ClassicTimerState.PRESENTING_QUESTION,
            toyVoiceSpeaking = true,
            pausedByTeacher = false
        )
        assertEquals(SevenFaceState.SPEAKING, face)
    }

    @Test
    fun classicWaitingResponseMapsToListening() {
        val face = classicSevenFaceState(
            state = ClassicTimerState.WAITING_FIXED_RESPONSE,
            toyVoiceSpeaking = false,
            pausedByTeacher = false
        )
        assertEquals(SevenFaceState.LISTENING, face)
    }

    @Test
    fun classicTimeoutMapsToTimeoutNeutral() {
        val face = classicSevenFaceState(
            state = ClassicTimerState.TIME_EXPIRED,
            toyVoiceSpeaking = false,
            pausedByTeacher = false
        )
        assertEquals(SevenFaceState.TIMEOUT_NEUTRAL, face)
    }

    @Test
    fun classicAnswerReceivedStaysNeutralWithoutCelebration() {
        val face = classicSevenFaceState(
            state = ClassicTimerState.ANSWER_RECEIVED,
            toyVoiceSpeaking = false,
            pausedByTeacher = false
        )
        assertEquals(SevenFaceState.WAITING, face)
    }

    @Test
    fun classicSessionCompletedMapsToClosing() {
        val face = classicSevenFaceState(
            state = ClassicTimerState.SESSION_COMPLETED,
            toyVoiceSpeaking = false,
            pausedByTeacher = false
        )
        assertEquals(SevenFaceState.CLOSING, face)
    }

    @Test
    fun classicPausedMapsToCalmWaiting() {
        val face = classicSevenFaceState(
            state = ClassicTimerState.WAITING_FIXED_RESPONSE,
            toyVoiceSpeaking = false,
            pausedByTeacher = true
        )
        assertEquals(SevenFaceState.WAITING, face)
    }

    @Test
    fun classicNeverRevealsEvaluationInAnyCombination() {
        // El temporizador nunca muestra estados que revelen acierto/error ni
        // evaluacion en curso, sin importar la combinacion de estado y banderas.
        val forbidden = setOf(
            SevenFaceState.HAPPY,
            SevenFaceState.SUPPORTIVE,
            SevenFaceState.RETRY,
            SevenFaceState.THINKING
        )
        ClassicTimerState.entries.forEach { state ->
            listOf(true, false).forEach { speaking ->
                listOf(true, false).forEach { paused ->
                    val face = classicSevenFaceState(
                        state = state,
                        toyVoiceSpeaking = speaking,
                        pausedByTeacher = paused
                    )
                    assertFalse(
                        "estado $state speaking=$speaking paused=$paused produjo $face",
                        face in forbidden
                    )
                }
            }
        }
    }

    @Test
    fun classicTimeoutNeverLooksLikeError() {
        listOf(true, false).forEach { speaking ->
            val face = classicSevenFaceState(
                state = ClassicTimerState.TIME_EXPIRED,
                toyVoiceSpeaking = speaking,
                pausedByTeacher = false
            )
            assertTrue(
                "timeout produjo $face",
                face == SevenFaceState.TIMEOUT_NEUTRAL || face == SevenFaceState.SPEAKING
            )
        }
    }

    // ----- Utilitarios --------------------------------------------------------------

    private fun snapshot(state: AttentionState): AttentionSnapshot {
        val faceDetected = state != AttentionState.FACE_ABSENT
        val lookingAtDevice = state == AttentionState.FACE_PRESENT ||
            state == AttentionState.ATTENTION_STABLE
        return AttentionSnapshot(
            state = state,
            faceDetected = faceDetected,
            lookingAtDevice = faceDetected && lookingAtDevice,
            isAttentionStable = state == AttentionState.ATTENTION_STABLE,
            isTemporarilyLost = state == AttentionState.TEMPORARILY_LOST,
            isAttentionLost = state == AttentionState.ATTENTION_LOST,
            headYawDegrees = null,
            headPitchDegrees = null,
            headRollDegrees = null,
            lastFaceDetectedAtMs = if (faceDetected) 1_000L else null,
            lastLookingAtDeviceAtMs = if (lookingAtDevice) 1_000L else null,
            lastLookAwayAtMs = if (state == AttentionState.TEMPORARILY_LOST) 1_000L else null,
            stateChangedAtMs = 1_000L,
            stableDurationMs = if (state == AttentionState.ATTENTION_STABLE) 1_500L else 0L,
            lookAwayDurationMs = if (state == AttentionState.TEMPORARILY_LOST) 1_200L else 0L,
            lostDurationMs = if (state == AttentionState.ATTENTION_LOST) 3_000L else 0L,
            consecutiveStableFrames = if (state == AttentionState.ATTENTION_STABLE) 3 else 0,
            consecutiveLostFrames = if (state == AttentionState.ATTENTION_LOST) 3 else 0
        )
    }
}
