package com.taller.app.voice

import android.util.Log

/**
 * Punto unico de entrada para la voz oficial de Seven.
 *
 * Los flujos de interaccion solo solicitan que Seven diga un texto; esta clase
 * decide la cadena de proveedores segun la preferencia guardada. Gemini TTS es
 * la voz principal recomendada; OpenAI, Azure y TTS local quedan como respaldo.
 */
class SevenVoiceService(
    private val geminiProvider: ToyVoiceProvider,
    private val openAiProvider: ToyVoiceProvider,
    private val azureProvider: ToyVoiceProvider,
    private val localProvider: ToyVoiceProvider,
    private val preferredProvider: () -> ToyVoiceProviderType = { ToyVoiceProviderType.GEMINI_TTS }
) {

    suspend fun speak(
        text: String?,
        source: String = "unknown",
        providerOverride: ToyVoiceProviderType? = null,
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome {
        val requested = normalizeProvider(providerOverride ?: preferredProvider())
        val outcome = ToyVoiceFallback.speakWithFallback(
            text = text,
            providerRequested = requested,
            providers = buildProviderChain(requested),
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
        geminiProvider.stop()
        openAiProvider.stop()
        azureProvider.stop()
        localProvider.stop()
    }

    fun release() {
        geminiProvider.release()
        openAiProvider.release()
        azureProvider.release()
        localProvider.release()
    }

    private fun buildProviderChain(
        requested: ToyVoiceProviderType
    ): List<Pair<ToyVoiceProviderType, ToyVoiceProvider>> {
        val providers = mapOf(
            ToyVoiceProviderType.GEMINI_TTS to geminiProvider,
            ToyVoiceProviderType.OPENAI_TTS to openAiProvider,
            ToyVoiceProviderType.AZURE_NEURAL to azureProvider,
            ToyVoiceProviderType.LOCAL to localProvider
        )
        return ToyVoiceProviderFallbackOrder.forPreferred(requested).map { type ->
            type to providers.getValue(type)
        }
    }

    private companion object {
        const val TAG = "SevenVoiceService"
    }
}

object ToyVoiceProviderFallbackOrder {
    fun forPreferred(provider: ToyVoiceProviderType): List<ToyVoiceProviderType> = when (normalizeProvider(provider)) {
        ToyVoiceProviderType.GEMINI_TTS -> listOf(
            ToyVoiceProviderType.GEMINI_TTS,
            ToyVoiceProviderType.OPENAI_TTS,
            ToyVoiceProviderType.AZURE_NEURAL,
            ToyVoiceProviderType.LOCAL
        )
        ToyVoiceProviderType.OPENAI_TTS -> listOf(
            ToyVoiceProviderType.OPENAI_TTS,
            ToyVoiceProviderType.AZURE_NEURAL,
            ToyVoiceProviderType.LOCAL
        )
        ToyVoiceProviderType.AZURE_NEURAL -> listOf(
            ToyVoiceProviderType.AZURE_NEURAL,
            ToyVoiceProviderType.LOCAL
        )
        ToyVoiceProviderType.LOCAL -> listOf(ToyVoiceProviderType.LOCAL)
        ToyVoiceProviderType.ELEVENLABS -> forPreferred(ToyVoiceProviderType.GEMINI_TTS)
    }
}

private fun normalizeProvider(provider: ToyVoiceProviderType): ToyVoiceProviderType =
    if (provider == ToyVoiceProviderType.ELEVENLABS) ToyVoiceProviderType.GEMINI_TTS else provider
