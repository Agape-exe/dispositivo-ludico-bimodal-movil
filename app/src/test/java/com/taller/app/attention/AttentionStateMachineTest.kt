package com.taller.app.attention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionStateMachineTest {

    private val thresholds = AttentionThresholds(
        stableLookMs = 1_000L,
        temporaryLookAwayMs = 1_200L,
        attentionLostMs = 3_000L,
        faceLostGraceMs = 1_000L,
        minStableFrames = 3,
        minLostFrames = 3
    )

    @Test
    fun initialState_isUnknown() {
        val machine = AttentionStateMachine(thresholds)

        assertEquals(AttentionState.UNKNOWN, machine.currentSnapshot.state)
    }

    @Test
    fun initialFaceNotDetected_goesToFaceAbsent() {
        val machine = AttentionStateMachine(thresholds)

        val snapshot = machine.onInput(observed(false, false, 100L))

        assertEquals(AttentionState.FACE_ABSENT, snapshot.state)
    }

    @Test
    fun facePresentButNotLooking_staysFacePresent() {
        val machine = AttentionStateMachine(thresholds)

        val snapshot = machine.onInput(observed(true, false, 100L))

        assertEquals(AttentionState.FACE_PRESENT, snapshot.state)
        assertFalse(snapshot.isAttentionStable)
    }

    @Test
    fun lookingForLessThanStableLookMs_staysFacePresent() {
        val machine = AttentionStateMachine(thresholds)

        machine.onInput(observed(true, true, 0L))
        machine.onInput(observed(true, true, 500L))
        val snapshot = machine.onInput(observed(true, true, 900L))

        assertEquals(AttentionState.FACE_PRESENT, snapshot.state)
    }

    @Test
    fun lookingForStableLookMsAndMinFrames_goesToAttentionStable() {
        val machine = AttentionStateMachine(thresholds)

        machine.onInput(observed(true, true, 0L))
        machine.onInput(observed(true, true, 500L))
        val snapshot = machine.onInput(observed(true, true, 1_000L))

        assertEquals(AttentionState.ATTENTION_STABLE, snapshot.state)
        assertTrue(snapshot.isAttentionStable)
    }

    @Test
    fun stableAttentionWithOneLookAwayFrame_goesTemporarilyLostOnly() {
        val machine = stableMachine()

        val snapshot = machine.onInput(observed(true, false, 1_100L))

        assertEquals(AttentionState.TEMPORARILY_LOST, snapshot.state)
        assertFalse(snapshot.isAttentionLost)
    }

    @Test
    fun briefLookAway_recoversStableAttention() {
        val machine = stableMachine()

        machine.onInput(observed(true, false, 1_100L))
        val snapshot = machine.onInput(observed(true, true, 2_000L))

        assertEquals(AttentionState.FACE_PRESENT, snapshot.state)
        assertFalse(snapshot.isAttentionStable)
    }

    @Test
    fun sustainedLookAway_goesToAttentionLost() {
        val machine = stableMachine()

        machine.onInput(observed(true, false, 1_100L))
        machine.onInput(observed(true, false, 2_000L))
        val snapshot = machine.onInput(observed(true, false, 4_100L))

        assertEquals(AttentionState.ATTENTION_LOST, snapshot.state)
    }

    @Test
    fun sustainedLookAwayBeforeLost_doesNotOscillateToFacePresent() {
        val machine = stableMachine()

        val first = machine.onInput(observed(true, false, 1_100L))
        val second = machine.onInput(observed(true, false, 2_000L))
        val third = machine.onInput(observed(true, false, 3_000L))

        assertEquals(AttentionState.TEMPORARILY_LOST, first.state)
        assertEquals(AttentionState.TEMPORARILY_LOST, second.state)
        assertEquals(AttentionState.TEMPORARILY_LOST, third.state)
    }

    @Test
    fun attentionLostWithFaceStillLookingAway_staysAttentionLost() {
        val machine = stableMachine()
        machine.onInput(observed(true, false, 1_100L))
        machine.onInput(observed(true, false, 2_000L))
        machine.onInput(observed(true, false, 4_100L))

        val snapshot = machine.onInput(observed(true, false, 4_500L))

        assertEquals(AttentionState.ATTENTION_LOST, snapshot.state)
        assertTrue(snapshot.isAttentionLost)
    }

    @Test
    fun attentionLostWithOneGoodFrame_doesNotRecoverDirectlyToStable() {
        val machine = stableMachine()
        machine.onInput(observed(true, false, 1_100L))
        machine.onInput(observed(true, false, 2_000L))
        machine.onInput(observed(true, false, 4_100L))

        val snapshot = machine.onInput(observed(true, true, 4_200L))

        assertEquals(AttentionState.FACE_PRESENT, snapshot.state)
        assertFalse(snapshot.isAttentionStable)
    }

    @Test
    fun stableAttentionWithBriefFaceLoss_goesTemporarilyLost() {
        val machine = stableMachine()

        val snapshot = machine.onInput(observed(false, false, 1_100L))

        assertEquals(AttentionState.TEMPORARILY_LOST, snapshot.state)
    }

    @Test
    fun sustainedFaceLoss_goesToAttentionLost() {
        val machine = stableMachine()

        machine.onInput(observed(false, false, 1_100L))
        machine.onInput(observed(false, false, 2_000L))
        val snapshot = machine.onInput(observed(false, false, 4_100L))

        assertEquals(AttentionState.ATTENTION_LOST, snapshot.state)
    }

    @Test
    fun attentionLostRecoversGraduallyAfterLookingAgain() {
        val machine = stableMachine()
        machine.onInput(observed(true, false, 1_100L))
        machine.onInput(observed(true, false, 2_000L))
        machine.onInput(observed(true, false, 4_100L))

        val recovering = machine.onInput(observed(true, true, 4_200L))
        machine.onInput(observed(true, true, 4_700L))
        val stable = machine.onInput(observed(true, true, 5_200L))

        assertEquals(AttentionState.FACE_PRESENT, recovering.state)
        assertEquals(AttentionState.ATTENTION_STABLE, stable.state)
    }

    @Test
    fun minStableFrames_preventsSingleGoodFrameStability() {
        val machine = AttentionStateMachine(
            thresholds.copy(stableLookMs = 0L, minStableFrames = 3)
        )

        val snapshot = machine.onInput(observed(true, true, 0L))

        assertEquals(AttentionState.FACE_PRESENT, snapshot.state)
    }

    @Test
    fun minLostFrames_preventsSingleBadFrameLoss() {
        val machine = AttentionStateMachine(
            thresholds.copy(temporaryLookAwayMs = 0L, attentionLostMs = 0L, minLostFrames = 3)
        )
        machine.onInput(observed(true, true, 0L))
        machine.onInput(observed(true, true, 500L))
        machine.onInput(observed(true, true, 1_000L))

        val snapshot = machine.onInput(observed(true, false, 1_100L))

        assertEquals(AttentionState.TEMPORARILY_LOST, snapshot.state)
        assertFalse(snapshot.isAttentionLost)
    }

    @Test
    fun yawInsideThreshold_isLookingAtDevice() {
        assertTrue(isLookingAtDevice(true, 10f, 0f, 0f))
    }

    @Test
    fun yawOutsideThreshold_isNotLookingAtDevice() {
        assertFalse(isLookingAtDevice(true, 30f, 0f, 0f))
    }

    @Test
    fun sustainedYawOutsideThreshold_reachesAttentionLost() {
        val machine = stableMachine()

        machine.onInput(observedWithAngles(31f, 0f, 0f, 1_100L))
        machine.onInput(observedWithAngles(31f, 0f, 0f, 2_000L))
        val snapshot = machine.onInput(observedWithAngles(31f, 0f, 0f, 4_100L))

        assertFalse(snapshot.lookingAtDevice)
        assertEquals(AttentionState.ATTENTION_LOST, snapshot.state)
    }

    @Test
    fun yawNearThresholdNoise_keepsLookingUntilExitHysteresis() {
        val machine = stableMachine()

        val first = machine.onInput(observedWithAngles(26f, 0f, 0f, 1_100L))
        val second = machine.onInput(observedWithAngles(24f, 0f, 0f, 1_200L))
        val third = machine.onInput(observedWithAngles(29f, 0f, 0f, 1_300L))

        assertTrue(first.lookingAtDevice)
        assertTrue(second.lookingAtDevice)
        assertTrue(third.lookingAtDevice)
        assertEquals(AttentionState.ATTENTION_STABLE, third.state)
    }

    @Test
    fun yawMustReturnInsideRecoveryHysteresisAfterLoss() {
        val machine = stableMachine()
        machine.onInput(observedWithAngles(31f, 0f, 0f, 1_100L))
        machine.onInput(observedWithAngles(31f, 0f, 0f, 2_000L))
        machine.onInput(observedWithAngles(31f, 0f, 0f, 4_100L))

        val nearThreshold = machine.onInput(observedWithAngles(24f, 0f, 0f, 4_200L))

        assertFalse(nearThreshold.lookingAtDevice)
        assertEquals(AttentionState.ATTENTION_LOST, nearThreshold.state)
    }

    @Test
    fun lostDurationDoesNotRestartWhileFacePresentButLookingAway() {
        val machine = stableMachine()

        machine.onInput(observed(true, false, 1_100L))
        val snapshot = machine.onInput(observed(true, false, 2_600L))

        assertEquals(1_500L, snapshot.lostDurationMs)
        assertEquals(1_500L, snapshot.lookAwayDurationMs)
        assertEquals(AttentionState.TEMPORARILY_LOST, snapshot.state)
    }

    @Test
    fun pitchOutsideThreshold_isNotLookingAtDevice() {
        assertFalse(isLookingAtDevice(true, 0f, 25f, 0f))
    }

    @Test
    fun rollOutsideThreshold_isNotLookingAtDevice() {
        assertFalse(isLookingAtDevice(true, 0f, 0f, 40f))
    }

    @Test
    fun nullAngles_doNotCrash() {
        assertTrue(isLookingAtDevice(true, null, null, null))
    }

    @Test
    fun reset_goesToUnknownAndClearsTimestamps() {
        val machine = stableMachine()

        val snapshot = machine.onInput(AttentionInput.Reset(5_000L))

        assertEquals(AttentionState.UNKNOWN, snapshot.state)
        assertEquals(null, snapshot.lastFaceDetectedAtMs)
        assertEquals(null, snapshot.lastLookingAtDeviceAtMs)
        assertEquals(null, snapshot.lastLookAwayAtMs)
    }

    @Test
    fun snapshotDoesNotExposeFaceGeometryOrBiometricFields() {
        val forbiddenNames = setOf(
            "boundingBox",
            "landmarks",
            "embedding",
            "coordinates",
            "frame",
            "image",
            "biometric",
            "identity",
            "name"
        )

        val fieldNames = AttentionSnapshot::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fieldNames.intersect(forbiddenNames).isEmpty())
    }

    private fun stableMachine(): AttentionStateMachine {
        val machine = AttentionStateMachine(thresholds)
        machine.onInput(observed(true, true, 0L))
        machine.onInput(observed(true, true, 500L))
        machine.onInput(observed(true, true, 1_000L))
        return machine
    }

    private fun observed(
        faceDetected: Boolean,
        lookingAtDevice: Boolean,
        timestampMs: Long
    ): AttentionInput = AttentionInput.FaceObserved(
        AttentionEvidence(
            faceDetected = faceDetected,
            lookingAtDevice = lookingAtDevice,
            headYawDegrees = if (faceDetected) {
                if (lookingAtDevice) 0f else thresholds.maxYawDegrees +
                    thresholds.hysteresisMarginDegrees + 1f
            } else {
                null
            },
            headPitchDegrees = if (faceDetected) 0f else null,
            headRollDegrees = if (faceDetected) 0f else null,
            timestampMs = timestampMs
        )
    )

    private fun observedWithAngles(
        yaw: Float?,
        pitch: Float?,
        roll: Float?,
        timestampMs: Long
    ): AttentionInput = AttentionInput.FaceObserved(
        AttentionEvidence(
            faceDetected = true,
            lookingAtDevice = isLookingAtDevice(
                faceDetected = true,
                headYawDegrees = yaw,
                headPitchDegrees = pitch,
                headRollDegrees = roll,
                thresholds = thresholds
            ),
            headYawDegrees = yaw,
            headPitchDegrees = pitch,
            headRollDegrees = roll,
            timestampMs = timestampMs
        )
    )
}
