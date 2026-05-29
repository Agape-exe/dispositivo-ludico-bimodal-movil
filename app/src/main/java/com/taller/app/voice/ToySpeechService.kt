package com.taller.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class ToySpeechService(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var state: ToySpeechState = ToySpeechState.UNINITIALIZED
    private var onStateChange: ((ToySpeechState) -> Unit)? = null

    fun initialize(onStateChange: (ToySpeechState) -> Unit) {
        if (state != ToySpeechState.UNINITIALIZED) return

        this.onStateChange = onStateChange
        updateState(ToySpeechState.INITIALIZING)

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = selectLocale()
                if (locale != null) {
                    tts?.language = locale
                    tts?.setSpeechRate(0.9f)
                    tts?.setPitch(1.0f)
                    tts?.setOnUtteranceProgressListener(buildUtteranceListener())
                    updateState(ToySpeechState.READY)
                } else {
                    Log.e(TAG, "Ningún idioma español disponible en este dispositivo")
                    updateState(ToySpeechState.ERROR)
                }
            } else {
                Log.e(TAG, "Fallo al inicializar TTS, status=$status")
                updateState(ToySpeechState.ERROR)
            }
        }
    }

    fun speak(phrase: ToySpeechPhrase) {
        speak(phrase.text)
    }

    fun speak(text: String) {
        if (state != ToySpeechState.READY && state != ToySpeechState.SPEAKING) {
            Log.w(TAG, "speak() ignorado, estado=$state")
            return
        }
        updateState(ToySpeechState.SPEAKING)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        if (state == ToySpeechState.SPEAKING) {
            tts?.stop()
            updateState(ToySpeechState.READY)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (t: Throwable) {
            Log.e(TAG, "Error al cerrar TTS", t)
        }
        tts = null
        onStateChange = null
        state = ToySpeechState.UNINITIALIZED
    }

    fun getState(): ToySpeechState = state

    private fun selectLocale(): Locale? {
        val candidates = listOf("es-PE", "es-ES", "es").map { Locale.forLanguageTag(it) }
        for (locale in candidates) {
            val result = tts?.isLanguageAvailable(locale)
            if (result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                Log.d(TAG, "Idioma seleccionado: $locale")
                return locale
            }
        }
        return null
    }

    private fun buildUtteranceListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                if (state != ToySpeechState.SPEAKING) {
                    updateState(ToySpeechState.SPEAKING)
                }
            }

            override fun onDone(utteranceId: String?) {
                if (state == ToySpeechState.SPEAKING) {
                    updateState(ToySpeechState.READY)
                }
            }

            @Deprecated("Deprecated in API 21")
            override fun onError(utteranceId: String?) {
                if (state == ToySpeechState.SPEAKING) {
                    Log.e(TAG, "Error durante reproducción de voz")
                    updateState(ToySpeechState.ERROR)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (state == ToySpeechState.SPEAKING) {
                    Log.e(TAG, "Error durante reproducción de voz, código=$errorCode")
                    updateState(ToySpeechState.ERROR)
                }
            }
        }
    }

    private fun updateState(newState: ToySpeechState) {
        state = newState
        onStateChange?.invoke(newState)
    }

    companion object {
        private const val TAG = "ToySpeechService"
        private const val UTTERANCE_ID = "toy_utterance"
    }
}
