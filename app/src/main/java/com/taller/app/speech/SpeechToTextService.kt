package com.taller.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechToTextService(
    private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening(
        onReady: () -> Unit,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                onReady()
            }

            override fun onBeginningOfSpeech() {
                // El usuario empezó a hablar.
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Nivel de audio detectado. Puede servir luego para VAD básico.
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // No se almacena audio crudo por privacidad.
            }

            override fun onEndOfSpeech() {
                // El usuario dejó de hablar.
            }

            override fun onError(error: Int) {
                onError(getErrorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                val transcription = matches?.firstOrNull().orEmpty()

                if (transcription.isBlank()) {
                    onError("No se obtuvo una transcripción interpretable.")
                } else {
                    onFinalResult(transcription)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                val partialText = matches?.firstOrNull().orEmpty()

                if (partialText.isNotBlank()) {
                    onPartialResult(partialText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Eventos adicionales del reconocedor.
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Error de audio."
            SpeechRecognizer.ERROR_CLIENT -> "Error del cliente de reconocimiento."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono insuficiente."
            SpeechRecognizer.ERROR_NETWORK -> "Error de red."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tiempo de red agotado."
            SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció ninguna frase."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor está ocupado."
            SpeechRecognizer.ERROR_SERVER -> "Error del servicio de reconocimiento."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz dentro del tiempo esperado."
            else -> "Error desconocido en reconocimiento de voz."
        }
    }
}