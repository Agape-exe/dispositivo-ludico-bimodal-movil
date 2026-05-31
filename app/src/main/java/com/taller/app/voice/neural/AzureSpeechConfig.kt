package com.taller.app.voice.neural

import com.taller.app.BuildConfig

data class AzureSpeechConfig(
    val subscriptionKey: String,
    val region: String,
    val voiceName: String
) {
    val hasKey: Boolean get() = subscriptionKey.isNotBlank()
    val hasRegion: Boolean get() = region.isNotBlank()
    val isComplete: Boolean get() = hasKey && hasRegion

    companion object {
        private const val DEFAULT_VOICE = "es-PE-AlexNeural"

        fun keyFromBuild(): String = BuildConfig.AZURE_SPEECH_KEY
        fun regionFromBuild(): String = BuildConfig.AZURE_SPEECH_REGION
        fun defaultVoiceFromBuild(): String = BuildConfig.AZURE_SPEECH_VOICE

        fun fromBuild(overrideVoiceName: String? = null): AzureSpeechConfig {
            val voiceName = overrideVoiceName?.takeIf { it.isNotBlank() }
                ?: defaultVoiceFromBuild().takeIf { it.isNotBlank() }
                ?: DEFAULT_VOICE
            return AzureSpeechConfig(
                subscriptionKey = keyFromBuild(),
                region = regionFromBuild(),
                voiceName = voiceName
            )
        }
    }
}
