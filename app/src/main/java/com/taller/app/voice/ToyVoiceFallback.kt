package com.taller.app.voice

/**
 * Resultado de alto nivel de una reproducción solicitada por la UI, indicando
 * qué proveedor terminó atendiendo la frase.
 */
sealed interface VoiceOutcome {
    /** El proveedor neural reprodujo correctamente. */
    data object NeuralSuccess : VoiceOutcome

    /** El proveedor local reprodujo correctamente (selección directa). */
    data object LocalSuccess : VoiceOutcome

    /** El proveedor neural falló y se usó la voz local como respaldo. */
    data class FallbackUsed(val reason: String) : VoiceOutcome

    /** No fue posible reproducir con ningún proveedor. */
    data class Failed(val reason: String) : VoiceOutcome
}

/**
 * Orquesta la reproducción entre el proveedor neural y el local, aplicando el
 * respaldo automático cuando corresponde. Es independiente de Android para
 * poder probarse de forma aislada.
 */
object ToyVoiceFallback {

    /**
     * @param useNeural si el usuario eligió el proveedor neural.
     * @param allowFallback si se permite caer a la voz local cuando el neural falla.
     * @param neural proveedor neural.
     * @param local proveedor local (respaldo).
     * @param onPlaybackStart se invoca cuando empieza a sonar el audio.
     */
    suspend fun speak(
        text: String,
        useNeural: Boolean,
        allowFallback: Boolean,
        neural: ToyVoiceProvider,
        local: ToyVoiceProvider,
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome {
        if (!useNeural) {
            return when (val result = local.speak(text, onPlaybackStart)) {
                is VoicePlaybackResult.Success -> VoiceOutcome.LocalSuccess
                is VoicePlaybackResult.Error -> VoiceOutcome.Failed(result.message)
            }
        }

        when (val neuralResult = neural.speak(text, onPlaybackStart)) {
            is VoicePlaybackResult.Success -> return VoiceOutcome.NeuralSuccess
            is VoicePlaybackResult.Error -> {
                if (!allowFallback) {
                    return VoiceOutcome.Failed(neuralResult.message)
                }
                return when (val localResult = local.speak(text, onPlaybackStart)) {
                    is VoicePlaybackResult.Success -> VoiceOutcome.FallbackUsed(neuralResult.message)
                    is VoicePlaybackResult.Error ->
                        VoiceOutcome.Failed("${neuralResult.message} ${localResult.message}")
                }
            }
        }
    }
}
