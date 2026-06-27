package com.taller.app.voice

data class ToyVoiceSettings(
    val selectedVoiceName: String? = null,
    val speechRate: Float = 0.92f,
    val pitch: Float = 1.12f,
    val localeTag: String? = null,
    val provider: ToyVoiceProviderType = ToyVoiceProviderType.GEMINI_TTS,
    val neuralVoiceId: String? = null,
    val openAiVoiceName: String? = null,
    val openAiInstructions: String? = null,
    val geminiVoiceName: String? = null,
    val geminiInstructions: String? = null,
    val azureVoiceName: String? = null,
    val fallbackToLocal: Boolean = true,
    val updatedAt: Long = 0L
)
