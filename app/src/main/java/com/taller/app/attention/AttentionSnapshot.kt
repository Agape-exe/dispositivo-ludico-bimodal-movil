package com.taller.app.attention

data class AttentionSnapshot(
    val state: AttentionState,
    val faceDetected: Boolean,
    val lookingAtDevice: Boolean,
    val isAttentionStable: Boolean,
    val isTemporarilyLost: Boolean,
    val isAttentionLost: Boolean,
    val headYawDegrees: Float?,
    val headPitchDegrees: Float?,
    val headRollDegrees: Float?,
    val lastFaceDetectedAtMs: Long?,
    val lastLookingAtDeviceAtMs: Long?,
    val lastLookAwayAtMs: Long?,
    val stateChangedAtMs: Long,
    val stableDurationMs: Long,
    val lookAwayDurationMs: Long,
    val lostDurationMs: Long,
    val consecutiveStableFrames: Int,
    val consecutiveLostFrames: Int
)
