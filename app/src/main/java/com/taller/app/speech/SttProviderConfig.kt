package com.taller.app.speech

import com.taller.app.BuildConfig

/**
 * Lectura segura de las credenciales de los proveedores STT remotos desde la
 * configuracion de compilacion (local.properties → BuildConfig). Las claves
 * nunca se registran en logs ni se persisten en Room.
 */
object SttProviderConfig {

    /** Clave de Google Cloud Speech-to-Text (GOOGLE_SPEECH_API_KEY). */
    fun googleApiKey(): String = BuildConfig.GOOGLE_SPEECH_API_KEY

    fun googleConfigured(): Boolean = googleApiKey().isNotBlank()

    /** La transcripcion de OpenAI reutiliza OPENAI_API_KEY. */
    fun openAiApiKey(): String = BuildConfig.OPENAI_API_KEY

    fun openAiConfigured(): Boolean = openAiApiKey().isNotBlank()

    /** Modelo de transcripcion de OpenAI (configurable, con valor moderno por defecto). */
    fun openAiTranscriptionModel(): String =
        BuildConfig.OPENAI_TRANSCRIPTION_MODEL.ifBlank { "gpt-4o-mini-transcribe" }
}
