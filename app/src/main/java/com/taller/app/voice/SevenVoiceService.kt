package com.taller.app.voice

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val preferredProvider: () -> ToyVoiceProviderType = { ToyVoiceProviderType.GEMINI_TTS },
    private val providerInfo: (ToyVoiceProviderType) -> VoiceProviderInfo = { VoiceProviderInfo() }
) {
    private val playbackMutex = Mutex()

    suspend fun speak(
        text: String?,
        source: String = "unknown",
        mode: VoiceMode = modeFromSource(source),
        voiceContext: VoiceContext = VoiceContext.UNKNOWN,
        providerOverride: ToyVoiceProviderType? = null,
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome = playbackMutex.withLock {
        val requested = normalizeProvider(providerOverride ?: preferredProvider())
        val outcome = ToyVoiceFallback.speakWithFallback(
            text = text,
            providerRequested = requested,
            providers = buildProviderChain(requested),
            mode = mode,
            voiceContext = voiceContext,
            providerInfo = providerInfo,
            onPlaybackStart = onPlaybackStart
        )
        outcome.metric?.let { metric ->
            val message = "eventType=${metric.eventType.name} source=$source ${metric.toTechnicalMessage()}"
            runCatching {
                if (outcome is VoiceOutcome.SkippedInvalidText || outcome is VoiceOutcome.Failed) {
                    Log.w(TAG, message)
                } else {
                    Log.d(TAG, message)
                }
            }
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

private fun modeFromSource(source: String): VoiceMode = when (source.lowercase()) {
    "configurar" -> VoiceMode.CONFIGURAR
    "inteligente" -> VoiceMode.INTELLIGENT
    "temporizador" -> VoiceMode.TIMER
    else -> VoiceMode.UNKNOWN
}
