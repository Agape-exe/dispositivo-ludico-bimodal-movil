package com.taller.app.attention

data class AttentionSnapshot(
    val state: AttentionState,
    val faceDetected: Boolean,
    val isAttentionStable: Boolean,
    val isTemporarilyLost: Boolean,
    val isAttentionLost: Boolean,
    val lastFaceDetectedAtMs: Long?,
    val lastFaceLostAtMs: Long?,
    val stateChangedAtMs: Long,
    val stableDurationMs: Long,
    val lostDurationMs: Long
)
