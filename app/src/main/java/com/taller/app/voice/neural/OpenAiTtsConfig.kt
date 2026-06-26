package com.taller.app.voice.neural

import com.taller.app.BuildConfig

data class OpenAiTtsConfig(
    val apiKey: String,
    val model: String,
    val voice: String,
    val instructions: String
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val isComplete: Boolean get() = hasApiKey

    companion object {
        const val DEFAULT_MODEL = "gpt-4o-mini-tts"
        const val DEFAULT_VOICE = "marin"
        const val DEFAULT_INSTRUCTIONS =
            "Habla en espanol latino con una voz calida, clara, amable y expresiva, como un companero de juego para ninos. Manten un ritmo natural, no demasiado rapido, con tono curioso y alegre. Evita sonar como profesor serio o como robot."

        val SUPPORTED_VOICES = listOf("marin", "cedar", "coral", "nova", "shimmer")

        fun apiKeyFromBuild(): String = BuildConfig.OPENAI_API_KEY
        fun modelFromBuild(): String = BuildConfig.OPENAI_TTS_MODEL.ifBlank { DEFAULT_MODEL }
        fun defaultVoiceFromBuild(): String = BuildConfig.OPENAI_TTS_VOICE.ifBlank { DEFAULT_VOICE }
        fun instructionsFromBuild(): String =
            BuildConfig.OPENAI_TTS_INSTRUCTIONS.ifBlank { DEFAULT_INSTRUCTIONS }

        fun fromBuild(
            overrideVoiceName: String? = null,
            overrideInstructions: String? = null
        ): OpenAiTtsConfig {
            val candidateVoice = overrideVoiceName?.takeIf { it.isNotBlank() }
                ?: defaultVoiceFromBuild()
            val voice = candidateVoice
                .takeIf { selected -> SUPPORTED_VOICES.any { it == selected } }
                ?: DEFAULT_VOICE
            val instructions = overrideInstructions?.takeIf { it.isNotBlank() }
                ?: instructionsFromBuild()
            return OpenAiTtsConfig(
                apiKey = apiKeyFromBuild(),
                model = modelFromBuild(),
                voice = voice,
                instructions = instructions
            )
        }
    }
}
