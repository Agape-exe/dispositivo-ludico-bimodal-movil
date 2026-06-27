package com.taller.app.attention

import com.taller.app.bimodal.BimodalInteractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttentionVisualMapperTest {

    @Test
    fun unknown_mapsToWaiting() {
        assertEquals(
            SevenAttentionVisualExpression.WAITING,
            AttentionState.UNKNOWN.toSevenAttentionVisualExpression()
        )
    }

    @Test
    fun faceAbsent_mapsToSearching() {
        assertEquals(
            SevenAttentionVisualExpression.SEARCHING,
            AttentionState.FACE_ABSENT.toSevenAttentionVisualExpression()
        )
    }

    @Test
    fun facePresent_mapsToCurious() {
        assertEquals(
            SevenAttentionVisualExpression.CURIOUS,
            AttentionState.FACE_PRESENT.toSevenAttentionVisualExpression()
        )
    }

    @Test
    fun attentionStable_mapsToAttentive() {
        assertEquals(
            SevenAttentionVisualExpression.ATTENTIVE,
            AttentionState.ATTENTION_STABLE.toSevenAttentionVisualExpression()
        )
    }

    @Test
    fun temporarilyLost_mapsToSoftConfused() {
        assertEquals(
            SevenAttentionVisualExpression.SOFT_CONFUSED,
            AttentionState.TEMPORARILY_LOST.toSevenAttentionVisualExpression()
        )
    }

    @Test
    fun attentionLost_mapsToWaitingPatiently() {
        assertEquals(
            SevenAttentionVisualExpression.WAITING_PATIENTLY,
            AttentionState.ATTENTION_LOST.toSevenAttentionVisualExpression()
        )
    }

    @Test
    fun speaking_keepsFlowPriorityOverAttentionLost() {
        val resolved = resolveSevenAttentionVisualExpression(
            interactionState = BimodalInteractionState.PRESENTING_QUESTION,
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
            toyVoiceSpeaking = true
        )

        assertNull(resolved)
    }

    @Test
    fun listening_keepsFlowPriority() {
        val resolved = resolveSevenAttentionVisualExpression(
            interactionState = BimodalInteractionState.LISTENING,
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
            toyVoiceSpeaking = false
        )

        assertNull(resolved)
    }

    @Test
    fun fixedTimerMode_doesNotUseAttentionVisuals() {
        val resolved = resolveSevenAttentionVisualExpression(
            interactionState = BimodalInteractionState.READY,
            attentionSnapshot = snapshot(AttentionState.ATTENTION_STABLE),
            toyVoiceSpeaking = false,
            fixedTimerMode = true,
            attentionVisualDebugEnabled = true
        )

        assertNull(resolved)
    }

    @Test
    fun debugDisabled_waitingForFace_usesAttentionVisualOnlyWhenFlowAllowsIt() {
        val resolved = resolveSevenAttentionVisualExpression(
            interactionState = BimodalInteractionState.WAITING_FOR_FACE,
            attentionSnapshot = snapshot(AttentionState.FACE_ABSENT),
            toyVoiceSpeaking = false
        )

        assertEquals(SevenAttentionVisualExpression.SEARCHING, resolved)
    }

    @Test
    fun debugEnabled_speakingUsesAttentionVisualForDiagnosis() {
        val resolved = resolveSevenAttentionVisualExpression(
            interactionState = BimodalInteractionState.PRESENTING_QUESTION,
            attentionSnapshot = snapshot(AttentionState.ATTENTION_LOST),
            toyVoiceSpeaking = true,
            attentionVisualDebugEnabled = true
        )

        assertEquals(SevenAttentionVisualExpression.WAITING_PATIENTLY, resolved)
    }

    @Test
    fun debugEnabled_faceAbsentMapsToSearching() {
        val resolved = resolveSevenAttentionVisualExpression(
            interactionState = BimodalInteractionState.LISTENING,
            attentionSnapshot = snapshot(AttentionState.FACE_ABSENT),
            toyVoiceSpeaking = false,
            attentionVisualDebugEnabled = true
        )

        assertEquals(SevenAttentionVisualExpression.SEARCHING, resolved)
    }

    private fun snapshot(
        state: AttentionState,
        timestampMs: Long = 1_000L
    ): AttentionSnapshot {
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
            lastFaceDetectedAtMs = if (faceDetected) timestampMs else null,
            lastLookingAtDeviceAtMs = if (lookingAtDevice) timestampMs else null,
            lastLookAwayAtMs = if (state == AttentionState.TEMPORARILY_LOST) timestampMs else null,
            stateChangedAtMs = timestampMs,
            stableDurationMs = if (state == AttentionState.ATTENTION_STABLE) 1_500L else 0L,
            lookAwayDurationMs = if (state == AttentionState.TEMPORARILY_LOST) 1_200L else 0L,
            lostDurationMs = if (state == AttentionState.ATTENTION_LOST) 3_000L else 0L,
            consecutiveStableFrames = if (state == AttentionState.ATTENTION_STABLE) 3 else 0,
            consecutiveLostFrames = if (state == AttentionState.ATTENTION_LOST) 3 else 0
        )
    }
}
