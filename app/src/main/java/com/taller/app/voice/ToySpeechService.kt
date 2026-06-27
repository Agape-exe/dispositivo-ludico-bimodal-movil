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

    fun initialize(
        initialSettings: ToyVoiceSettings = ToyVoiceSettings(),
        onStateChange: (ToySpeechState) -> Unit
    ) {
        if (state != ToySpeechState.UNINITIALIZED) return

        this.onStateChange = onStateChange
        updateState(ToySpeechState.INITIALIZING)

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(buildUtteranceListener())
                applySettingsInternal(initialSettings)
                updateState(ToySpeechState.READY)
            } else {
                Log.e(TAG, "Fallo al inicializar TTS, status=$status")
                updateState(ToySpeechState.ERROR)
            }
        }
    }

    fun applySettings(settings: ToyVoiceSettings) {
        if (state == ToySpeechState.READY || state == ToySpeechState.SPEAKING) {
            applySettingsInternal(settings)
        }
    }

    fun getAvailableVoices(): List<ToyVoiceInfo> {
        val instance = tts ?: return emptyList()
        val voices = try {
            instance.voices ?: emptySet()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudieron obtener las voces disponibles", e)
            return emptyList()
        }

        val spanishPrefixes = listOf("es-PE", "es-ES", "es-US", "es-MX", "es")

        val allInfos = voices
            .filter { voice ->
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }
            .map { voice ->
                ToyVoiceInfo(
                    name = voice.name,
                    locale = voice.locale.toLanguageTag(),
                    isNetworkRequired = voice.isNetworkConnectionRequired,
                    quality = voice.quality
                )
            }

        val spanishVoices = allInfos.filter { info ->
            spanishPrefixes.any { prefix ->
                info.locale.startsWith(prefix, ignoreCase = true)
            }
        }.sortedByDescending { it.quality }

        return if (spanishVoices.isNotEmpty()) spanishVoices else allInfos.sortedByDescending { it.quality }
    }

    fun speak(phrase: ToySpeechPhrase) {
        speak(phrase.text)
    }

    fun speak(text: String) {
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) {
            Log.w(
                TAG,
                "eventType=TTS_SKIPPED_INVALID_TEXT providerRequested=LOCAL providerUsed=NONE " +
                    "textLength=${text.length} reason=${validation.reason} timestamp=${System.currentTimeMillis()}"
            )
            return
        }
        if (state != ToySpeechState.READY && state != ToySpeechState.SPEAKING) {
            Log.w(TAG, "speak() ignorado, estado=$state")
            return
        }
        updateState(ToySpeechState.SPEAKING)
        tts?.speak(validation.normalizedText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
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

    private fun applySettingsInternal(settings: ToyVoiceSettings) {
        val instance = tts ?: return

        if (settings.selectedVoiceName != null) {
            val target = try {
                instance.voices?.find { it.name == settings.selectedVoiceName }
            } catch (e: Exception) {
                null
            }
            if (target != null) {
                try {
                    instance.setVoice(target)
                    Log.d(TAG, "Voz aplicada: ${settings.selectedVoiceName}")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo aplicar la voz '${settings.selectedVoiceName}', usando idioma de respaldo")
                    applyFallbackLocale(instance, settings.localeTag)
                }
            } else {
                Log.w(TAG, "Voz '${settings.selectedVoiceName}' no encontrada, usando idioma de respaldo")
                applyFallbackLocale(instance, settings.localeTag)
            }
        } else {
            applyFallbackLocale(instance, settings.localeTag)
        }

        instance.setSpeechRate(settings.speechRate)
        instance.setPitch(settings.pitch)
    }

    private fun applyFallbackLocale(instance: TextToSpeech, localeTag: String?) {
        val candidates = buildList {
            if (!localeTag.isNullOrBlank()) add(Locale.forLanguageTag(localeTag))
            add(Locale.forLanguageTag("es-PE"))
            add(Locale.forLanguageTag("es-ES"))
            add(Locale.forLanguageTag("es"))
        }
        for (locale in candidates) {
            val result = instance.isLanguageAvailable(locale)
            if (result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                instance.language = locale
                Log.d(TAG, "Idioma de respaldo aplicado: $locale")
                return
            }
        }
        Log.e(TAG, "No se encontró ningún idioma español disponible en este dispositivo")
    }

    private fun buildUtteranceListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (state != ToySpeechState.SPEAKING) updateState(ToySpeechState.SPEAKING)
            }

            override fun onDone(utteranceId: String?) {
                if (state == ToySpeechState.SPEAKING) updateState(ToySpeechState.READY)
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
