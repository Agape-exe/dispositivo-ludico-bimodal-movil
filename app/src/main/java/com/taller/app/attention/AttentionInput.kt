package com.taller.app.attention

sealed interface AttentionInput {
    val timestampMs: Long

    data class FaceDetected(override val timestampMs: Long) : AttentionInput
    data class FaceNotDetected(override val timestampMs: Long) : AttentionInput
    data class Reset(override val timestampMs: Long) : AttentionInput
}
