package com.taller.app.bimodal

/**
 * Resultado de un intento de captura de voz, ya desacoplado del reconocedor de
 * Android. La pantalla bimodal traduce los callbacks del servicio de voz a uno
 * de estos resultados y delega en [SpeechCaptureEventMapper] la decision del
 * evento que debe recibir el orquestador.
 */
sealed class SpeechCaptureOutcome {

    /** Se obtuvo una transcripcion de la respuesta del nino. */
    data class Transcribed(val text: String) : SpeechCaptureOutcome()

    /** No se detecto voz: silencio o frase no reconocida. */
    object NoSpeech : SpeechCaptureOutcome()

    /** Fallo tecnico del reconocedor (audio, red, servicio ocupado, etc.). */
    data class Failed(val reason: String) : SpeechCaptureOutcome()
}

/**
 * Traduce el resultado de la captura de voz al evento correspondiente del
 * orquestador bimodal.
 *
 * Es logica pura, sin dependencias de Android, para poder validarla con pruebas
 * unitarias. No decide si la respuesta es correcta o incorrecta: solo distingue
 * entre transcripcion obtenida, ausencia de voz y fallo tecnico.
 */
object SpeechCaptureEventMapper {

    fun toEvent(outcome: SpeechCaptureOutcome): BimodalInteractionEvent = when (outcome) {
        is SpeechCaptureOutcome.Transcribed -> {
            val text = outcome.text.trim()
            if (text.isEmpty()) {
                BimodalInteractionEvent.NoResponse
            } else {
                BimodalInteractionEvent.SpeechCaptured(text)
            }
        }

        SpeechCaptureOutcome.NoSpeech -> BimodalInteractionEvent.NoResponse

        is SpeechCaptureOutcome.Failed -> BimodalInteractionEvent.SpeechFailed(outcome.reason)
    }
}
