package com.taller.app.voice

import android.util.Log

/**
 * Punto unico de entrada para la voz oficial de Seven.
 *
 * Los flujos de interaccion solo solicitan que Seven diga un texto; esta clase
 * decide la cadena de proveedores: OpenAI TTS como principal, Azure como
 * respaldo neural y TTS local como ultimo respaldo.
 */
class SevenVoiceService(
    private val openAiProvider: ToyVoiceProvider,
    private val azureProvider: ToyVoiceProvider,
    private val localProvider: ToyVoiceProvider
) {

    suspend fun speak(
        text: String?,
        source: String = "unknown",
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome {
        val outcome = ToyVoiceFallback.speakWithFallback(
            text = text,
            providerRequested = ToyVoiceProviderType.OPENAI_TTS,
            providers = buildProviderChain(),
            onPlaybackStart = onPlaybackStart
        )
        if (outcome is VoiceOutcome.SkippedInvalidText) {
            Log.w(
                TAG,
                "eventType=TTS_SKIPPED_INVALID_TEXT source=$source " +
                    "providerRequested=${outcome.providerRequested} providerUsed=NONE " +
                    "textLength=${outcome.textLength} reason=${outcome.reason} " +
                    "timestamp=${System.currentTimeMillis()}"
            )
        }
        return outcome
    }

    fun stop() {
        openAiProvider.stop()
        azureProvider.stop()
        localProvider.stop()
    }

    fun release() {
        openAiProvider.release()
        azureProvider.release()
        localProvider.release()
    }

    private fun buildProviderChain(): List<Pair<ToyVoiceProviderType, ToyVoiceProvider>> = buildList {
        add(ToyVoiceProviderType.OPENAI_TTS to openAiProvider)
        add(ToyVoiceProviderType.AZURE_NEURAL to azureProvider)
        add(ToyVoiceProviderType.LOCAL to localProvider)
    }

    private companion object {
        const val TAG = "SevenVoiceService"
    }
}
