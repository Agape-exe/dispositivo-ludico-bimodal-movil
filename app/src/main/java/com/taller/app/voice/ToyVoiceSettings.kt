package com.taller.app.voice

data class ToyVoiceSettings(
    val selectedVoiceName: String? = null,
    val speechRate: Float = 0.92f,
    val pitch: Float = 1.12f,
    val localeTag: String? = null,
    val updatedAt: Long = 0L
)
