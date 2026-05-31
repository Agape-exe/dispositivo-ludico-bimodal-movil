package com.taller.app.voice

/**
 * Abstracción común para los proveedores de voz del juguete.
 *
 * Permite intercambiar la fuente de audio (TTS local del dispositivo o un
 * proveedor neural por red) sin que la capa de UI conozca los detalles.
 */
interface ToyVoiceProvider {

    /**
     * Genera y reproduce el [text] indicado, suspendiendo hasta que termina la
     * reproducción o se produce un error controlado.
     *
     * @param onPlaybackStart se invoca cuando el audio empieza a sonar, para que
     * la UI pueda mostrar el estado "reproduciendo".
     */
    suspend fun speak(text: String, onPlaybackStart: () -> Unit = {}): VoicePlaybackResult

    /** Indica si el proveedor tiene la configuración mínima para operar. */
    fun isConfigured(): Boolean

    /** Detiene la reproducción en curso, si la hay. */
    fun stop()

    /** Libera los recursos asociados al proveedor. */
    fun release()
}

/** Resultado de una solicitud de reproducción a un proveedor de voz. */
sealed interface VoicePlaybackResult {
    data object Success : VoicePlaybackResult
    data class Error(val type: VoiceErrorType, val message: String) : VoicePlaybackResult
}

/** Categorías de error controlado que puede devolver un proveedor de voz. */
enum class VoiceErrorType {
    NOT_CONFIGURED,
    NO_NETWORK,
    HTTP_ERROR,
    TIMEOUT,
    INVALID_AUDIO,
    PLAYBACK_FAILED,
    UNKNOWN
}
