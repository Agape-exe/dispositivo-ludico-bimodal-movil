package com.taller.app.attention

class AttentionStateMachine(
    private val thresholds: AttentionThresholds = AttentionThresholds()
) {
    constructor(
        stableFaceMs: Long,
        temporaryLostMs: Long = 1_200L,
        attentionLostMs: Long = 3_000L
    ) : this(
        AttentionThresholds(
            stableLookMs = stableFaceMs,
            temporaryLookAwayMs = temporaryLostMs,
            attentionLostMs = attentionLostMs
        )
    )

    private var state: AttentionState = AttentionState.UNKNOWN
    private var lookStartedAtMs: Long? = null
    private var lookAwayStartedAtMs: Long? = null
    private var faceLostStartedAtMs: Long? = null
    private var attentionLossStartedAtMs: Long? = null
    private var lastFaceDetectedAtMs: Long? = null
    private var lastLookingAtDeviceAtMs: Long? = null
    private var lastLookAwayAtMs: Long? = null
    private var stateChangedAtMs: Long = 0L
    private var wasStableBeforeLoss: Boolean = false
    private var faceDetected: Boolean = false
    private var lookingAtDevice: Boolean = false
    private var headYawDegrees: Float? = null
    private var headPitchDegrees: Float? = null
    private var headRollDegrees: Float? = null
    private var consecutiveStableFrames: Int = 0
    private var consecutiveLostFrames: Int = 0

    val currentSnapshot: AttentionSnapshot
        get() = snapshotAt(stateChangedAtMs)

    fun onInput(input: AttentionInput): AttentionSnapshot =
        when (input) {
            is AttentionInput.FaceObserved -> onEvidence(input.evidence)
            is AttentionInput.FaceDetected -> onEvidence(
                AttentionEvidence(
                    faceDetected = true,
                    lookingAtDevice = true,
                    timestampMs = input.timestampMs
                )
            )
            is AttentionInput.FaceNotDetected -> onEvidence(
                AttentionEvidence(
                    faceDetected = false,
                    lookingAtDevice = false,
                    timestampMs = input.timestampMs
                )
            )
            is AttentionInput.Reset -> reset(input.timestampMs)
        }

    private fun onEvidence(evidence: AttentionEvidence): AttentionSnapshot {
        val timestampMs = evidence.timestampMs
        val previousState = state
        faceDetected = evidence.faceDetected
        lookingAtDevice = evidence.faceDetected && evidence.lookingAtDevice
        headYawDegrees = evidence.headYawDegrees
        headPitchDegrees = evidence.headPitchDegrees
        headRollDegrees = evidence.headRollDegrees

        if (faceDetected) {
            lastFaceDetectedAtMs = timestampMs
            faceLostStartedAtMs = null
        } else if (faceLostStartedAtMs == null) {
            faceLostStartedAtMs = timestampMs
        }

        val nextState = if (lookingAtDevice) {
            handleLookingAtDevice(timestampMs)
        } else {
            handleNotLookingAtDevice(timestampMs)
        }

        transitionTo(nextState, previousState, timestampMs)
        return snapshotAt(timestampMs)
    }

    private fun handleLookingAtDevice(timestampMs: Long): AttentionState {
        lastLookingAtDeviceAtMs = timestampMs
        lookAwayStartedAtMs = null
        faceLostStartedAtMs = null
        attentionLossStartedAtMs = null
        consecutiveLostFrames = 0
        consecutiveStableFrames += 1

        if (lookStartedAtMs == null) {
            lookStartedAtMs = timestampMs
        }

        val stableDurationMs = timestampMs - (lookStartedAtMs ?: timestampMs)
        val stableEnough = stableDurationMs >= thresholds.stableLookMs &&
            consecutiveStableFrames >= thresholds.minStableFrames

        return when {
            state == AttentionState.TEMPORARILY_LOST && wasStableBeforeLoss -> {
                wasStableBeforeLoss = false
                AttentionState.ATTENTION_STABLE
            }
            stableEnough -> AttentionState.ATTENTION_STABLE
            else -> AttentionState.FACE_PRESENT
        }
    }

    private fun handleNotLookingAtDevice(timestampMs: Long): AttentionState {
        lookStartedAtMs = null
        consecutiveStableFrames = 0
        consecutiveLostFrames += 1

        if (attentionLossStartedAtMs == null) {
            attentionLossStartedAtMs = timestampMs
            wasStableBeforeLoss = state == AttentionState.ATTENTION_STABLE ||
                (state == AttentionState.TEMPORARILY_LOST && wasStableBeforeLoss)
        }

        if (faceDetected) {
            lastLookAwayAtMs = timestampMs
            if (lookAwayStartedAtMs == null) lookAwayStartedAtMs = timestampMs
        }

        val lossDurationMs = timestampMs - (attentionLossStartedAtMs ?: timestampMs)
        val lostEnough = lossDurationMs >= thresholds.attentionLostMs &&
            consecutiveLostFrames >= thresholds.minLostFrames

        return when (state) {
            AttentionState.UNKNOWN ->
                if (faceDetected) AttentionState.FACE_PRESENT else AttentionState.FACE_ABSENT
            AttentionState.FACE_ABSENT ->
                if (faceDetected) AttentionState.FACE_PRESENT else AttentionState.FACE_ABSENT
            AttentionState.FACE_PRESENT ->
                if (lostEnough) AttentionState.ATTENTION_LOST else AttentionState.FACE_PRESENT
            AttentionState.ATTENTION_STABLE -> AttentionState.TEMPORARILY_LOST
            AttentionState.TEMPORARILY_LOST -> {
                if (lostEnough) {
                    wasStableBeforeLoss = false
                    AttentionState.ATTENTION_LOST
                } else {
                    AttentionState.TEMPORARILY_LOST
                }
            }
            AttentionState.ATTENTION_LOST ->
                if (faceDetected) AttentionState.FACE_PRESENT else AttentionState.ATTENTION_LOST
        }
    }

    private fun reset(timestampMs: Long): AttentionSnapshot {
        state = AttentionState.UNKNOWN
        lookStartedAtMs = null
        lookAwayStartedAtMs = null
        faceLostStartedAtMs = null
        attentionLossStartedAtMs = null
        lastFaceDetectedAtMs = null
        lastLookingAtDeviceAtMs = null
        lastLookAwayAtMs = null
        stateChangedAtMs = timestampMs
        wasStableBeforeLoss = false
        faceDetected = false
        lookingAtDevice = false
        headYawDegrees = null
        headPitchDegrees = null
        headRollDegrees = null
        consecutiveStableFrames = 0
        consecutiveLostFrames = 0
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
        val stableDurationMs =
            if (lookingAtDevice) timestampMs - (lookStartedAtMs ?: timestampMs) else 0L
        val lookAwayDurationMs =
            if (faceDetected && !lookingAtDevice) {
                timestampMs - (lookAwayStartedAtMs ?: timestampMs)
            } else {
                0L
            }
        val lostDurationMs =
            if (!faceDetected) timestampMs - (faceLostStartedAtMs ?: timestampMs) else 0L

        return AttentionSnapshot(
            state = state,
            faceDetected = faceDetected,
            lookingAtDevice = lookingAtDevice,
            isAttentionStable = state == AttentionState.ATTENTION_STABLE,
            isTemporarilyLost = state == AttentionState.TEMPORARILY_LOST,
            isAttentionLost = state == AttentionState.ATTENTION_LOST,
            headYawDegrees = headYawDegrees,
            headPitchDegrees = headPitchDegrees,
            headRollDegrees = headRollDegrees,
            lastFaceDetectedAtMs = lastFaceDetectedAtMs,
            lastLookingAtDeviceAtMs = lastLookingAtDeviceAtMs,
            lastLookAwayAtMs = lastLookAwayAtMs,
            stateChangedAtMs = stateChangedAtMs,
            stableDurationMs = stableDurationMs.coerceAtLeast(0L),
            lookAwayDurationMs = lookAwayDurationMs.coerceAtLeast(0L),
            lostDurationMs = lostDurationMs.coerceAtLeast(0L),
            consecutiveStableFrames = consecutiveStableFrames,
            consecutiveLostFrames = consecutiveLostFrames
        )
    }
}
