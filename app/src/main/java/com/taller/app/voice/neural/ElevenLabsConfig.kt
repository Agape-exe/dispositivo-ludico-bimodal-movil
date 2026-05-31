package com.taller.app.voice.neural

import com.taller.app.BuildConfig

/**
 * Configuración del proveedor neural ElevenLabs.
 *
 * La API key NUNCA se hardcodea: se inyecta en tiempo de compilación desde una
 * fuente local no versionada (local.properties o variable de entorno) a través
 * de [BuildConfig]. El voiceId puede tener un valor por defecto en BuildConfig y
 * además ser sobrescrito localmente desde la pantalla de configuración técnica.
 */
data class ElevenLabsConfig(
    val apiKey: String,
    val voiceId: String,
    val modelId: String = DEFAULT_MODEL_ID
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val hasVoiceId: Boolean get() = voiceId.isNotBlank()
    val isComplete: Boolean get() = hasApiKey && hasVoiceId

    companion object {
        const val DEFAULT_MODEL_ID = "eleven_multilingual_v2"

        /** API key proveniente de la configuración local de compilación. */
        fun apiKeyFromBuild(): String = BuildConfig.ELEVENLABS_API_KEY

        /** voiceId por defecto proveniente de la configuración local de compilación. */
        fun defaultVoiceIdFromBuild(): String = BuildConfig.ELEVENLABS_VOICE_ID

        /**
         * Crea la configuración combinando la API key de compilación con un
         * voiceId que el usuario puede haber configurado localmente. Si el
         * usuario no configuró ninguno, se usa el valor por defecto de BuildConfig.
         */
        fun from(userVoiceId: String?): ElevenLabsConfig {
            val voiceId = userVoiceId?.takeIf { it.isNotBlank() }
                ?: defaultVoiceIdFromBuild()
            return ElevenLabsConfig(
                apiKey = apiKeyFromBuild(),
                voiceId = voiceId
            )
        }
    }
}
