package com.taller.app.voice

import android.util.Log
import com.taller.app.voice.prep.VoiceLinePrepResult
import com.taller.app.voice.prep.VoiceLineSynthesizer
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

    /**
     * Reproduce una frase SOLO desde cache, recorriendo la cadena de proveedores
     * que cachean por archivo (Gemini, OpenAI) en el orden de preferencia. Nunca
     * llama a la red: es el camino seguro para sesiones reales con ninos. Si ningun
     * proveedor tiene la frase cacheada, devuelve un [VoiceOutcome.Failed] seguro y
     * NO sintetiza nada.
     */
    suspend fun speakFromCacheOnly(
        text: String?,
        source: String = "unknown",
        mode: VoiceMode = modeFromSource(source),
        voiceContext: VoiceContext = VoiceContext.UNKNOWN,
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome = playbackMutex.withLock {
        val requested = normalizeProvider(preferredProvider())
        val startedAt = System.currentTimeMillis()
        for ((type, provider) in cacheableChain(requested)) {
            val result = provider.speakFromCacheOrNull(text.orEmpty(), onPlaybackStart)
            if (result is VoicePlaybackResult.Success) {
                val latencyMs = System.currentTimeMillis() - startedAt
                runCatching {
                    Log.d(TAG, "eventType=VOICE_CACHE_ONLY_HIT source=$source provider=$type")
                }
                return@withLock VoiceOutcome.Completed(
                    providerRequested = requested,
                    providerUsed = type,
                    fallbackUsed = type != requested,
                    errorMessage = null,
                    latencyMs = latencyMs,
                    cacheHit = true,
                    cacheKey = result.cacheKey,
                    totalLatencyMs = result.totalLatencyMs ?: latencyMs
                )
            }
        }
        val latencyMs = System.currentTimeMillis() - startedAt
        runCatching {
            Log.w(TAG, "eventType=VOICE_CACHE_ONLY_MISS source=$source")
        }
        VoiceOutcome.Failed(
            providerRequested = requested,
            errorMessage = SAFE_NOT_PREPARED_MESSAGE,
            latencyMs = latencyMs,
            errorType = VoiceErrorType.NOT_CONFIGURED
        )
    }

    /** true si la frase ya esta cacheada por algun proveedor reutilizable. */
    fun isLineCached(text: String): Boolean {
        val requested = normalizeProvider(preferredProvider())
        return cacheableChain(requested).any { (_, provider) -> provider.isCached(text) }
    }

    /**
     * Pre-genera una frase hacia cache sin reproducirla, intentando primero el
     * proveedor preferido (Gemini) y cayendo a los siguientes que cachean por
     * archivo si falla. Devuelve el proveedor que la dejo lista o un fallo seguro.
     */
    suspend fun prepareLine(text: String): VoiceLinePrepResult {
        val requested = normalizeProvider(preferredProvider())
        val chain = cacheableChain(requested)
        if (chain.isEmpty()) {
            return VoiceLinePrepResult.Failed("No hay un proveedor de voz disponible para preparar.")
        }
        var lastError: VoicePlaybackResult.Error? = null
        for ((type, provider) in chain) {
            when (val result = provider.synthesizeToCache(text)) {
                is VoicePlaybackResult.Success -> return VoiceLinePrepResult.Prepared(
                    provider = type,
                    fromCache = result.cacheHit == true
                )
                is VoicePlaybackResult.Error -> lastError = result
            }
        }
        return VoiceLinePrepResult.Failed(
            safeMessage = lastError?.message ?: "No se pudo preparar la voz de Seven.",
            errorCode = lastError?.type?.name
        )
    }

    /** Vista del servicio como [VoiceLineSynthesizer] para el preparador de sesion. */
    fun asLineSynthesizer(): VoiceLineSynthesizer = object : VoiceLineSynthesizer {
        override fun isCached(text: String): Boolean = this@SevenVoiceService.isLineCached(text)
        override suspend fun prepare(text: String): VoiceLinePrepResult =
            this@SevenVoiceService.prepareLine(text)
    }

    private fun cacheableChain(
        requested: ToyVoiceProviderType
    ): List<Pair<ToyVoiceProviderType, CacheableVoiceProvider>> =
        buildProviderChain(requested).mapNotNull { (type, provider) ->
            (provider as? CacheableVoiceProvider)?.let { type to it }
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
        const val SAFE_NOT_PREPARED_MESSAGE =
            "La voz de Seven para esta frase aun no esta preparada."
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
