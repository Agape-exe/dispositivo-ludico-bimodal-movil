package com.taller.app.voice

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Adapta el [ToySpeechService] (Text-to-Speech local) a la interfaz común
 * [ToyVoiceProvider]. Funciona sin conexión y sirve como respaldo del proveedor
 * neural.
 *
 * Envuelve un servicio ya creado y su flujo de estado para reutilizar el mismo
 * motor TTS que administra la pantalla (evitando instancias duplicadas). El
 * ciclo de vida del servicio lo gestiona quien lo crea.
 */
class LocalToyVoiceProvider(
    private val service: ToySpeechService,
    private val state: StateFlow<ToySpeechState>,
    private val settingsProvider: () -> ToyVoiceSettings = { ToyVoiceSettings() }
) : ToyVoiceProvider {

    /** El TTS local siempre está disponible mientras el motor inicialice bien. */
    override fun isConfigured(): Boolean = true

    override suspend fun speak(text: String, onPlaybackStart: () -> Unit): VoicePlaybackResult {
        // Espera a que el motor termine de inicializar.
        val ready = state.first {
            it == ToySpeechState.READY || it == ToySpeechState.ERROR
        }
        if (ready == ToySpeechState.ERROR) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.PLAYBACK_FAILED,
                "No se pudo inicializar el motor de voz local."
            )
        }

        service.applySettings(settingsProvider())
        onPlaybackStart()
        service.speak(text)

        // speak() deja el estado en SPEAKING de forma síncrona; esperamos a que
        // la reproducción termine (READY) o falle (ERROR).
        val outcome = state.first {
            it == ToySpeechState.READY || it == ToySpeechState.ERROR
        }
        return if (outcome == ToySpeechState.ERROR) {
            VoicePlaybackResult.Error(
                VoiceErrorType.PLAYBACK_FAILED,
                "Error al reproducir con la voz local."
            )
        } else {
            VoicePlaybackResult.Success
        }
    }

    override fun stop() {
        service.stop()
    }

    override fun release() {
        // El ciclo de vida del servicio lo gestiona el propietario (la pantalla).
    }
}
