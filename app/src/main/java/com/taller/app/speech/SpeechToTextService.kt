package com.taller.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

enum class SttState {
    IDLE,
    LISTENING,
    STOPPING,
    SUCCESS,
    STOPPED,
    ERROR
}

class SpeechToTextService(
    private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private var state: SttState = SttState.IDLE
    private var userStopped: Boolean = false
    private var lastPartial: String = ""
    private var finalDelivered: Boolean = false

    private var onStateChange: ((SttState) -> Unit)? = null
    private var onReady: (() -> Unit)? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    private var onStopped: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun startListening(
        onStateChange: (SttState) -> Unit,
        onReady: () -> Unit,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onStopped: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }

        this.onStateChange = onStateChange
        this.onReady = onReady
        this.onPartial = onPartialResult
        this.onFinal = onFinalResult
        this.onStopped = onStopped
        this.onError = onError

        userStopped = false
        lastPartial = ""
        finalDelivered = false

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(buildListener())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        updateState(SttState.LISTENING)
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        if (state != SttState.LISTENING) {
            Log.d(TAG, "stopListening() ignorado, estado actual=$state")
            return
        }
        userStopped = true
        updateState(SttState.STOPPING)
        try {
            speechRecognizer?.stopListening()
        } catch (t: Throwable) {
            Log.e(TAG, "stopListening lanzó excepción", t)
            deliverStopped()
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (t: Throwable) {
            Log.e(TAG, "destroy lanzó excepción", t)
        }
        speechRecognizer = null
        clearCallbacks()
        state = SttState.IDLE
    }

    private fun buildListener(): RecognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            onReady?.invoke()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (userStopped || state == SttState.STOPPING) {
                Log.d(TAG, "onError($error) tras stop manual — se trata como STOPPED")
                deliverStopped()
                return
            }
            if (finalDelivered) {
                Log.d(TAG, "onError($error) tras resultado final — se ignora")
                return
            }
            val message = getErrorMessage(error)
            updateState(SttState.ERROR)
            onError?.invoke(message)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val transcription = matches?.firstOrNull().orEmpty()

            if (userStopped || state == SttState.STOPPING) {
                if (transcription.isNotBlank()) lastPartial = transcription
                deliverStopped()
                return
            }

            if (transcription.isBlank()) {
                if (lastPartial.isNotBlank()) {
                    finalDelivered = true
                    updateState(SttState.SUCCESS)
                    onFinal?.invoke(lastPartial)
                } else {
                    updateState(SttState.ERROR)
                    onError?.invoke("No se obtuvo una transcripción interpretable.")
                }
            } else {
                finalDelivered = true
                lastPartial = transcription
                updateState(SttState.SUCCESS)
                onFinal?.invoke(transcription)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull().orEmpty()
            if (partialText.isNotBlank()) {
                lastPartial = partialText
                onPartial?.invoke(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun deliverStopped() {
        if (state == SttState.STOPPED || state == SttState.SUCCESS) return
        updateState(SttState.STOPPED)
        onStopped?.invoke(lastPartial)
    }

    private fun updateState(newState: SttState) {
        state = newState
        onStateChange?.invoke(newState)
    }

    private fun clearCallbacks() {
        onStateChange = null
        onReady = null
        onPartial = null
        onFinal = null
        onStopped = null
        onError = null
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

    companion object {
        private const val TAG = "SpeechToTextService"
    }
}
