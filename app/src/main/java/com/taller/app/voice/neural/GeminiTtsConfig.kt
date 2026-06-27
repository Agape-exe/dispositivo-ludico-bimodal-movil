package com.taller.app.voice.neural

import com.taller.app.BuildConfig

data class GeminiTtsConfig(
    val apiKey: String,
    val model: String,
    val voiceName: String,
    val instructions: String
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val isComplete: Boolean get() = hasApiKey

    companion object {
        const val DEFAULT_MODEL = "gemini-3.1-flash-tts-preview"
        const val DEFAULT_VOICE = "Puck"
        const val DEFAULT_INSTRUCTIONS =
            "Habla en espanol latino con una voz calida, clara, curiosa y amigable para ninos. Manten un ritmo natural, tono alegre y expresivo, como Seven, un pequeno alien explorador que aprende sobre la Tierra."

        fun apiKeyFromBuild(): String = BuildConfig.GEMINI_API_KEY
        fun modelFromBuild(): String = BuildConfig.GEMINI_TTS_MODEL.ifBlank { DEFAULT_MODEL }
        fun voiceFromBuild(): String = BuildConfig.GEMINI_TTS_VOICE.ifBlank { DEFAULT_VOICE }
        fun instructionsFromBuild(): String =
            BuildConfig.GEMINI_TTS_INSTRUCTIONS.ifBlank { DEFAULT_INSTRUCTIONS }

        fun fromBuild(
            voiceName: String? = null,
            instructions: String? = null
        ): GeminiTtsConfig = GeminiTtsConfig(
            apiKey = apiKeyFromBuild(),
            model = modelFromBuild(),
            voiceName = GeminiTtsVoices.normalizeId(voiceName ?: voiceFromBuild()),
            instructions = instructions?.takeIf { it.isNotBlank() } ?: instructionsFromBuild()
        )
    }
}

data class GeminiTtsVoice(
    val id: String,
    val displayName: String,
    val description: String
)

object GeminiTtsVoices {
    val supported: List<GeminiTtsVoice> = listOf(
        GeminiTtsVoice("Puck", "Puck", "Animada"),
        GeminiTtsVoice("Leda", "Leda", "Juvenil"),
        GeminiTtsVoice("Achird", "Achird", "Amigable"),
        GeminiTtsVoice("Sadachbia", "Sadachbia", "Vivaz"),
        GeminiTtsVoice("Sulafat", "Sulafat", "Calida"),
        GeminiTtsVoice("Aoede", "Aoede", "Ligera"),
        GeminiTtsVoice("Laomedeia", "Laomedeia", "Animada"),
        GeminiTtsVoice("Autonoe", "Autonoe", "Brillante"),
        GeminiTtsVoice("Zephyr", "Zephyr", "Brillante"),
        GeminiTtsVoice("Kore", "Kore", "Clara/firme")
    )

    fun normalizeId(id: String?): String =
        supported.firstOrNull { it.id == id }?.id ?: GeminiTtsConfig.DEFAULT_VOICE

    fun descriptionFor(id: String): String =
        supported.firstOrNull { it.id == normalizeId(id) }?.description.orEmpty()
}
