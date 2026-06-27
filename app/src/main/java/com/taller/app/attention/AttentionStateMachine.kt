package com.taller.app.attention

class AttentionStateMachine(
    private val stableFaceMs: Long = 1_000L,
    private val temporaryLostMs: Long = 1_500L,
    private val attentionLostMs: Long = 3_000L
) {
    init {
        require(stableFaceMs >= 0L) { "stableFaceMs debe ser >= 0" }
        require(temporaryLostMs >= 0L) { "temporaryLostMs debe ser >= 0" }
        require(attentionLostMs >= temporaryLostMs) {
            "attentionLostMs debe ser >= temporaryLostMs"
        }
    }

    private var state: AttentionState = AttentionState.UNKNOWN
    private var firstFaceDetectedAtMs: Long? = null
    private var lastFaceDetectedAtMs: Long? = null
    private var lostStartedAtMs: Long? = null
    private var stateChangedAtMs: Long = 0L
    private var wasStableBeforeLoss: Boolean = false

    val currentSnapshot: AttentionSnapshot
        get() = snapshotAt(stateChangedAtMs)

    fun onInput(input: AttentionInput): AttentionSnapshot =
        when (input) {
            is AttentionInput.FaceDetected -> onFaceDetected(input.timestampMs)
            is AttentionInput.FaceNotDetected -> onFaceNotDetected(input.timestampMs)
            is AttentionInput.Reset -> reset(input.timestampMs)
        }

    private fun onFaceDetected(timestampMs: Long): AttentionSnapshot {
        val previousState = state

        if (state == AttentionState.ATTENTION_LOST || state == AttentionState.FACE_ABSENT) {
            firstFaceDetectedAtMs = timestampMs
            wasStableBeforeLoss = false
        } else if (firstFaceDetectedAtMs == null) {
            firstFaceDetectedAtMs = timestampMs
        }

        lastFaceDetectedAtMs = timestampMs

        val recoveredFromTemporaryLoss = state == AttentionState.TEMPORARILY_LOST
        lostStartedAtMs = null

        val firstDetectedAt = firstFaceDetectedAtMs ?: timestampMs
        val stableEnough = timestampMs - firstDetectedAt >= stableFaceMs
        val nextState = if ((recoveredFromTemporaryLoss && wasStableBeforeLoss) || stableEnough) {
            AttentionState.ATTENTION_STABLE
        } else {
            AttentionState.FACE_PRESENT
        }

        transitionTo(nextState, previousState, timestampMs)
        return snapshotAt(timestampMs)
    }

    private fun onFaceNotDetected(timestampMs: Long): AttentionSnapshot {
        val previousState = state
        val hasFaceHistory = firstFaceDetectedAtMs != null || lastFaceDetectedAtMs != null

        val nextState = when (state) {
            AttentionState.UNKNOWN ->
                if (hasFaceHistory) AttentionState.TEMPORARILY_LOST else AttentionState.FACE_ABSENT
            AttentionState.FACE_ABSENT,
            AttentionState.ATTENTION_LOST ->
                state
            AttentionState.FACE_PRESENT,
            AttentionState.ATTENTION_STABLE -> {
                if (lostStartedAtMs == null) {
                    lostStartedAtMs = timestampMs
                    wasStableBeforeLoss = state == AttentionState.ATTENTION_STABLE
                }
                AttentionState.TEMPORARILY_LOST
            }
            AttentionState.TEMPORARILY_LOST -> {
                val lostAt = lostStartedAtMs ?: timestampMs.also { lostStartedAtMs = it }
                if (timestampMs - lostAt >= attentionLostMs) {
                    firstFaceDetectedAtMs = null
                    wasStableBeforeLoss = false
                    AttentionState.ATTENTION_LOST
                } else {
                    AttentionState.TEMPORARILY_LOST
                }
            }
        }

        transitionTo(nextState, previousState, timestampMs)
        return snapshotAt(timestampMs)
    }

    private fun reset(timestampMs: Long): AttentionSnapshot {
        state = AttentionState.UNKNOWN
        firstFaceDetectedAtMs = null
        lastFaceDetectedAtMs = null
        lostStartedAtMs = null
        stateChangedAtMs = timestampMs
        wasStableBeforeLoss = false
        return snapshotAt(timestampMs)
    }

    private fun transitionTo(
        nextState: AttentionState,
        previousState: AttentionState,
        timestampMs: Long
    ) {
        state = nextState
        if (nextState != previousState) {
            stateChangedAtMs = timestampMs
        }
    }

    private fun snapshotAt(timestampMs: Long): AttentionSnapshot {
        val lostAt = lostStartedAtMs
        val stableDurationMs =
            if (state == AttentionState.FACE_PRESENT || state == AttentionState.ATTENTION_STABLE) {
                val firstDetectedAt = firstFaceDetectedAtMs
                if (firstDetectedAt == null) 0L else (timestampMs - firstDetectedAt).coerceAtLeast(0L)
            } else {
                0L
            }
        val lostDurationMs =
            if (state == AttentionState.TEMPORARILY_LOST || state == AttentionState.ATTENTION_LOST) {
                if (lostAt == null) 0L else (timestampMs - lostAt).coerceAtLeast(0L)
            } else {
                0L
            }

        return AttentionSnapshot(
            state = state,
            faceDetected = state == AttentionState.FACE_PRESENT ||
                state == AttentionState.ATTENTION_STABLE,
            isAttentionStable = state == AttentionState.ATTENTION_STABLE,
            isTemporarilyLost = state == AttentionState.TEMPORARILY_LOST,
            isAttentionLost = state == AttentionState.ATTENTION_LOST,
            lastFaceDetectedAtMs = lastFaceDetectedAtMs,
            lastFaceLostAtMs = lostStartedAtMs,
            stateChangedAtMs = stateChangedAtMs,
            stableDurationMs = stableDurationMs,
            lostDurationMs = lostDurationMs
        )
    }
}
