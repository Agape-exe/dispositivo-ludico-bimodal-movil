package com.taller.app.attention

import kotlin.math.abs

data class AttentionEvidence(
    val faceDetected: Boolean,
    val lookingAtDevice: Boolean,
    val headYawDegrees: Float? = null,
    val headPitchDegrees: Float? = null,
    val headRollDegrees: Float? = null,
    val eyeLookingAtDevice: Boolean? = null,
    val confidence: Float? = null,
    val timestampMs: Long
)

data class AttentionThresholds(
    val stableLookMs: Long = 1_000L,
    val temporaryLookAwayMs: Long = 1_200L,
    val attentionLostMs: Long = 3_000L,
    val faceLostGraceMs: Long = 1_000L,
    val minStableFrames: Int = 3,
    val minLostFrames: Int = 3,
    val maxYawDegrees: Float = 25f,
    val maxPitchDegrees: Float = 20f,
    val maxRollDegrees: Float = 35f,
    val hysteresisMarginDegrees: Float = 5f
) {
    init {
        require(stableLookMs >= 0L) { "stableLookMs debe ser >= 0" }
        require(temporaryLookAwayMs >= 0L) { "temporaryLookAwayMs debe ser >= 0" }
        require(attentionLostMs >= temporaryLookAwayMs) {
            "attentionLostMs debe ser >= temporaryLookAwayMs"
        }
        require(faceLostGraceMs >= 0L) { "faceLostGraceMs debe ser >= 0" }
        require(minStableFrames >= 1) { "minStableFrames debe ser >= 1" }
        require(minLostFrames >= 1) { "minLostFrames debe ser >= 1" }
        require(maxYawDegrees >= 0f) { "maxYawDegrees debe ser >= 0" }
        require(maxPitchDegrees >= 0f) { "maxPitchDegrees debe ser >= 0" }
        require(maxRollDegrees >= 0f) { "maxRollDegrees debe ser >= 0" }
        require(hysteresisMarginDegrees >= 0f) { "hysteresisMarginDegrees debe ser >= 0" }
    }
}

fun isLookingAtDevice(
    faceDetected: Boolean,
    headYawDegrees: Float?,
    headPitchDegrees: Float?,
    headRollDegrees: Float?,
    eyeLookingAtDevice: Boolean? = null,
    thresholds: AttentionThresholds = AttentionThresholds()
): Boolean {
    if (!faceDetected) return false

    val headLooksAtDevice =
        (headYawDegrees == null || abs(headYawDegrees) <= thresholds.maxYawDegrees) &&
            (headPitchDegrees == null || abs(headPitchDegrees) <= thresholds.maxPitchDegrees) &&
            (headRollDegrees == null || abs(headRollDegrees) <= thresholds.maxRollDegrees)

    // Si no existe una senal confiable de mirada ocular, se usa solo la orientacion de cabeza.
    return headLooksAtDevice && (eyeLookingAtDevice ?: true)
}
