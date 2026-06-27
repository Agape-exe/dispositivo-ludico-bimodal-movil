package com.taller.app.attention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionStateMachineTest {

    @Test
    fun initialState_isUnknown() {
        val machine = AttentionStateMachine()

        assertEquals(AttentionState.UNKNOWN, machine.currentSnapshot.state)
    }

    @Test
    fun initialFaceNotDetected_goesToFaceAbsent() {
        val machine = AttentionStateMachine()

        val snapshot = machine.onInput(AttentionInput.FaceNotDetected(100L))

        assertEquals(AttentionState.FACE_ABSENT, snapshot.state)
    }

    @Test
    fun initialFaceDetected_goesToFacePresent() {
        val machine = AttentionStateMachine()

        val snapshot = machine.onInput(AttentionInput.FaceDetected(100L))

        assertEquals(AttentionState.FACE_PRESENT, snapshot.state)
        assertTrue(snapshot.faceDetected)
    }

    @Test
    fun faceDetectedMaintainedForStableFaceMs_goesToAttentionStable() {
        val machine = AttentionStateMachine(stableFaceMs = 1_000L)

        machine.onInput(AttentionInput.FaceDetected(0L))
        val snapshot = machine.onInput(AttentionInput.FaceDetected(1_000L))

        assertEquals(AttentionState.ATTENTION_STABLE, snapshot.state)
        assertTrue(snapshot.isAttentionStable)
    }

    @Test
    fun stableAttentionWithFaceNotDetected_goesToTemporarilyLost() {
        val machine = stableMachine()

        val snapshot = machine.onInput(AttentionInput.FaceNotDetected(1_100L))

        assertEquals(AttentionState.TEMPORARILY_LOST, snapshot.state)
        assertTrue(snapshot.isTemporarilyLost)
    }

    @Test
    fun temporarilyLostWithFaceDetectedBeforeLostThreshold_recoversStableAttention() {
        val machine = stableMachine()

        machine.onInput(AttentionInput.FaceNotDetected(1_100L))
        val snapshot = machine.onInput(AttentionInput.FaceDetected(2_000L))

        assertEquals(AttentionState.ATTENTION_STABLE, snapshot.state)
        assertTrue(snapshot.isAttentionStable)
    }

    @Test
    fun temporarilyLostSustainedPastLostThreshold_goesToAttentionLost() {
        val machine = stableMachine()

        machine.onInput(AttentionInput.FaceNotDetected(1_100L))
        val snapshot = machine.onInput(AttentionInput.FaceNotDetected(4_100L))

        assertEquals(AttentionState.ATTENTION_LOST, snapshot.state)
        assertTrue(snapshot.isAttentionLost)
    }

    @Test
    fun attentionLostWithFaceDetected_goesToFacePresent() {
        val machine = stableMachine()
        machine.onInput(AttentionInput.FaceNotDetected(1_100L))
        machine.onInput(AttentionInput.FaceNotDetected(4_100L))

        val snapshot = machine.onInput(AttentionInput.FaceDetected(4_200L))

        assertEquals(AttentionState.FACE_PRESENT, snapshot.state)
    }

    @Test
    fun reset_goesToUnknownAndClearsTimestamps() {
        val machine = stableMachine()

        val snapshot = machine.onInput(AttentionInput.Reset(5_000L))

        assertEquals(AttentionState.UNKNOWN, snapshot.state)
        assertEquals(null, snapshot.lastFaceDetectedAtMs)
        assertEquals(null, snapshot.lastFaceLostAtMs)
    }

    @Test
    fun stableAttentionDoesNotGoDirectlyToLostOnSingleInstantLoss() {
        val machine = stableMachine()

        val snapshot = machine.onInput(AttentionInput.FaceNotDetected(1_100L))

        assertEquals(AttentionState.TEMPORARILY_LOST, snapshot.state)
        assertFalse(snapshot.isAttentionLost)
    }

    @Test
    fun stableDurationMs_isCalculatedFromFirstDetectedTimestamp() {
        val machine = AttentionStateMachine(stableFaceMs = 1_000L)

        machine.onInput(AttentionInput.FaceDetected(500L))
        val snapshot = machine.onInput(AttentionInput.FaceDetected(1_250L))

        assertEquals(750L, snapshot.stableDurationMs)
    }

    @Test
    fun lostDurationMs_isCalculatedFromLossStartTimestamp() {
        val machine = stableMachine()

        machine.onInput(AttentionInput.FaceNotDetected(1_100L))
        val snapshot = machine.onInput(AttentionInput.FaceNotDetected(2_000L))

        assertEquals(900L, snapshot.lostDurationMs)
    }

    @Test
    fun snapshotDoesNotExposeFaceGeometryOrBiometricFields() {
        val forbiddenNames = setOf(
            "boundingBox",
            "landmarks",
            "embedding",
            "coordinates",
            "frame",
            "image"
        )

        val fieldNames = AttentionSnapshot::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fieldNames.intersect(forbiddenNames).isEmpty())
    }

    private fun stableMachine(): AttentionStateMachine {
        val machine = AttentionStateMachine(stableFaceMs = 1_000L)
        machine.onInput(AttentionInput.FaceDetected(0L))
        machine.onInput(AttentionInput.FaceDetected(1_000L))
        return machine
    }
}
