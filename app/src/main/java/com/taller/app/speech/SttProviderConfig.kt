package com.taller.app.speech

import android.content.Context
import com.taller.app.BuildConfig

/**
 * Lectura segura de las credenciales de los proveedores STT remotos desde la
 * configuracion de compilacion (local.properties → BuildConfig). Las claves
 * nunca se registran en logs ni se persisten en Room.
 */
object SttProviderConfig {

    /** Clave de Google Cloud Speech-to-Text (GOOGLE_SPEECH_API_KEY). */
    fun googleApiKey(): String = BuildConfig.GOOGLE_SPEECH_API_KEY

    fun googleConfigured(context: Context): Boolean =
        googleApiKey().isNotBlank() || GoogleSttCredentialStore.hasImportedCredential(context)

    fun googleCredentialStatus(context: Context): GoogleSttCredentialStatus =
        GoogleSttCredentialStatusResolver.resolve(
            imported = GoogleSttCredentialStore.hasImportedCredential(context),
            apiKeyConfigured = googleApiKey().isNotBlank(),
            desktopPathConfigured = BuildConfig.GOOGLE_CLOUD_STT_CREDENTIALS_PATH_CONFIGURED,
            desktopFileExistedAtBuild = BuildConfig.GOOGLE_CLOUD_STT_CREDENTIALS_FILE_EXISTS_AT_BUILD
        )

    /** La transcripcion de OpenAI reutiliza OPENAI_API_KEY. */
    fun openAiApiKey(): String = BuildConfig.OPENAI_API_KEY

    fun openAiConfigured(): Boolean = openAiApiKey().isNotBlank()

    /** Modelo de transcripcion de OpenAI (configurable, con valor moderno por defecto). */
    fun openAiTranscriptionModel(): String =
        BuildConfig.OPENAI_TRANSCRIPTION_MODEL.ifBlank { "gpt-4o-mini-transcribe" }
}

enum class GoogleSttCredentialStatus {
    IMPORTED_DEBUG,
    API_KEY_CONFIGURED,
    DESKTOP_PATH_EXISTS,
    DESKTOP_PATH_MISSING,
    NOT_CONFIGURED
}

object GoogleSttCredentialStatusResolver {
    fun resolve(
        imported: Boolean,
        apiKeyConfigured: Boolean,
        desktopPathConfigured: Boolean,
        desktopFileExistedAtBuild: Boolean
    ): GoogleSttCredentialStatus = when {
        imported -> GoogleSttCredentialStatus.IMPORTED_DEBUG
        apiKeyConfigured -> GoogleSttCredentialStatus.API_KEY_CONFIGURED
        desktopPathConfigured && desktopFileExistedAtBuild ->
            GoogleSttCredentialStatus.DESKTOP_PATH_EXISTS
        desktopPathConfigured -> GoogleSttCredentialStatus.DESKTOP_PATH_MISSING
        else -> GoogleSttCredentialStatus.NOT_CONFIGURED
    }
}
