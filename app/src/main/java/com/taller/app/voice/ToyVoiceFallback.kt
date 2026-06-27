package com.taller.app.voice

/**
 * Resultado de alto nivel de una reproduccion solicitada por la UI, indicando
 * que proveedor termino atendiendo la frase y si hubo fallback.
 */
sealed interface VoiceOutcome {
    val providerRequested: ToyVoiceProviderType
    val providerUsed: ToyVoiceProviderType?
    val fallbackUsed: Boolean
    val errorMessage: String?
    val latencyMs: Long
    val cacheHit: Boolean?
    val cacheKey: String?
    val synthesisLatencyMs: Long?
    val playbackLatencyMs: Long?
    val totalLatencyMs: Long?

    data class Completed(
        override val providerRequested: ToyVoiceProviderType,
        override val providerUsed: ToyVoiceProviderType,
        override val fallbackUsed: Boolean,
        override val errorMessage: String?,
        override val latencyMs: Long,
        override val cacheHit: Boolean? = null,
        override val cacheKey: String? = null,
        override val synthesisLatencyMs: Long? = null,
        override val playbackLatencyMs: Long? = null,
        override val totalLatencyMs: Long? = null
    ) : VoiceOutcome

    data class Failed(
        override val providerRequested: ToyVoiceProviderType,
        override val errorMessage: String,
        override val latencyMs: Long
    ) : VoiceOutcome {
        override val providerUsed: ToyVoiceProviderType? = null
        override val fallbackUsed: Boolean = false
        override val cacheHit: Boolean? = null
        override val cacheKey: String? = null
        override val synthesisLatencyMs: Long? = null
        override val playbackLatencyMs: Long? = null
        override val totalLatencyMs: Long? = null
    }

    data class SkippedInvalidText(
        override val providerRequested: ToyVoiceProviderType,
        val reason: InvalidToyVoiceTextReason,
        val textLength: Int,
        override val latencyMs: Long
    ) : VoiceOutcome {
        override val providerUsed: ToyVoiceProviderType? = null
        override val fallbackUsed: Boolean = false
        override val errorMessage: String = SAFE_INVALID_TEXT_MESSAGE
        override val cacheHit: Boolean? = null
        override val cacheKey: String? = null
        override val synthesisLatencyMs: Long? = null
        override val playbackLatencyMs: Long? = null
        override val totalLatencyMs: Long? = null
    }

    companion object {
        const val SAFE_INVALID_TEXT_MESSAGE = "Texto de voz vacio o invalido."
    }
}

/**
 * Orquesta la reproduccion entre proveedores, aplicando respaldo automatico
 * cuando corresponde. Es independiente de Android para poder probarse de forma
 * aislada.
 */
object ToyVoiceFallback {

    suspend fun speak(
        text: String?,
        useNeural: Boolean,
        allowFallback: Boolean,
        neural: ToyVoiceProvider,
        local: ToyVoiceProvider,
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome {
        val requested = if (useNeural) ToyVoiceProviderType.AZURE_NEURAL else ToyVoiceProviderType.LOCAL
        val providers = if (!useNeural) {
            listOf(ToyVoiceProviderType.LOCAL to local)
        } else if (allowFallback) {
            listOf(ToyVoiceProviderType.AZURE_NEURAL to neural, ToyVoiceProviderType.LOCAL to local)
        } else {
            listOf(ToyVoiceProviderType.AZURE_NEURAL to neural)
        }
        return speakWithFallback(
            text = text,
            providerRequested = requested,
            providers = providers,
            onPlaybackStart = onPlaybackStart
        )
    }

    suspend fun speakWithFallback(
        text: String?,
        providerRequested: ToyVoiceProviderType,
        providers: List<Pair<ToyVoiceProviderType, ToyVoiceProvider>>,
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome {
        val startedAt = System.currentTimeMillis()
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) {
            return VoiceOutcome.SkippedInvalidText(
                providerRequested = providerRequested,
                reason = validation.reason ?: InvalidToyVoiceTextReason.EMPTY_TEXT,
                textLength = text?.length ?: 0,
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }
        val errors = mutableListOf<String>()

        for ((type, provider) in providers) {
            when (val result = provider.speak(validation.normalizedText, onPlaybackStart)) {
                is VoicePlaybackResult.Success -> {
                    val latencyMs = System.currentTimeMillis() - startedAt
                    return VoiceOutcome.Completed(
                        providerRequested = providerRequested,
                        providerUsed = type,
                        fallbackUsed = type != providerRequested,
                        errorMessage = errors.firstOrNull(),
                        latencyMs = latencyMs,
                        cacheHit = result.cacheHit,
                        cacheKey = result.cacheKey,
                        synthesisLatencyMs = result.synthesisLatencyMs,
                        playbackLatencyMs = result.playbackLatencyMs,
                        totalLatencyMs = result.totalLatencyMs ?: latencyMs
                    )
                }
                is VoicePlaybackResult.Error -> errors.add(result.message)
            }
        }

        return VoiceOutcome.Failed(
            providerRequested = providerRequested,
            errorMessage = errors.joinToString(" ").ifBlank { "No se pudo reproducir la voz." },
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }
}
