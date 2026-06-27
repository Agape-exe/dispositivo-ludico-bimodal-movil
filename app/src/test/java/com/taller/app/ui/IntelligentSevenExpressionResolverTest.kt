package com.taller.app.ui

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.AttentionState
import com.taller.app.bimodal.BimodalInteractionState
import org.junit.Assert.assertEquals
import org.junit.Test

class IntelligentSevenExpressionResolverTest {

    @Test
    fun speakingKeepsPriorityOverAttentionLost() {
        val expression = BimodalInteractionState.PRESENTING_QUESTION
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = true,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST)
            )

        assertEquals(IntelligentSevenExpression.SPEAKING, expression)
    }

    @Test
    fun listeningKeepsListeningExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST)
            )

        assertEquals(IntelligentSevenExpression.LISTENING, expression)
    }

    @Test
    fun waitingForFaceUsesSearchingForFaceAbsent() {
        val expression = BimodalInteractionState.WAITING_FOR_FACE
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.FACE_ABSENT)
            )

        assertEquals(IntelligentSevenExpression.SEARCHING_FACE, expression)
    }

    @Test
    fun debugDisabledKeepsNormalVisualFlow() {
        val expression = BimodalInteractionState.WAITING_FOR_FACE
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.TEMPORARILY_LOST),
                attentionVisualDebugEnabled = false
            )

        assertEquals(IntelligentSevenExpression.READY, expression)
    }

    @Test
    fun debugEnabledFaceAbsentUsesSearching() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = false,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.FACE_ABSENT),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.SEARCHING_FACE, expression)
    }

    @Test
    fun debugEnabledAttentionStableUsesReadyHappyExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.HAPPY, expression)
    }

    @Test
    fun debugEnabledTemporarilyLostUsesSoftConfusedExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.TEMPORARILY_LOST),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.CONFUSED, expression)
    }

    @Test
    fun debugEnabledAttentionLostUsesSearchingExpression() {
        val expression = BimodalInteractionState.LISTENING
            .toIntelligentSevenExpression(
                facePresent = true,
                toyVoiceSpeaking = false,
                attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
                attentionVisualDebugEnabled = true
            )

        assertEquals(IntelligentSevenExpression.SEARCHING_FACE, expression)
    }

    @Test
    fun attentionDebugLabelShowsOnlyStateFaceAndLooking() {
        val label = attentionDebugLabel(snapshot(AttentionState.ATTENTION_LOST))

        assertEquals(
            "Atencion: ATTENTION_LOST\nRostro: Si\nMirando: No",
            label
        )
    }

    @Test
    fun attentionDebugLabelDefaultsToUnknown() {
        assertEquals(
            "Atencion: UNKNOWN\nRostro: No\nMirando: No",
            attentionDebugLabel(null)
        )
    }

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
