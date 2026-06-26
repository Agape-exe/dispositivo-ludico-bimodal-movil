package com.taller.app.voice

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
        text: String,
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome {
        return ToyVoiceFallback.speakWithFallback(
            text = text,
            providerRequested = ToyVoiceProviderType.OPENAI_TTS,
            providers = buildProviderChain(),
            onPlaybackStart = onPlaybackStart
        )
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
}
