package com.taller.app.voice

/**
 * Resultado de alto nivel de una reproduccion solicitada por la UI, indicando
 * que proveedor termino atendiendo la frase y si hubo fallback.
 */
sealed interface VoiceOutcome {
    val providerRequested: ToyVoiceProviderType
    val providerUsed: ToyVoiceProviderType?
    val fallbackUsed: Boolean
    val fallbackFrom: ToyVoiceProviderType?
    val fallbackTo: ToyVoiceProviderType?
    val errorMessage: String?
    val errorType: VoiceErrorType?
    val latencyMs: Long
    val cacheHit: Boolean?
    val cacheKey: String?
    val cacheLookupLatencyMs: Long?
    val synthesisLatencyMs: Long?
    val playbackLatencyMs: Long?
    val totalLatencyMs: Long?
    val metric: VoicePlaybackMetric?

    data class Completed(
        override val providerRequested: ToyVoiceProviderType,
        override val providerUsed: ToyVoiceProviderType,
        override val fallbackUsed: Boolean,
        override val fallbackFrom: ToyVoiceProviderType? = null,
        override val fallbackTo: ToyVoiceProviderType? = null,
        override val errorMessage: String?,
        override val errorType: VoiceErrorType? = null,
        override val latencyMs: Long,
        override val cacheHit: Boolean? = null,
        override val cacheKey: String? = null,
        override val cacheLookupLatencyMs: Long? = null,
        override val synthesisLatencyMs: Long? = null,
        override val playbackLatencyMs: Long? = null,
        override val totalLatencyMs: Long? = null,
        override val metric: VoicePlaybackMetric? = null
    ) : VoiceOutcome

    data class Failed(
        override val providerRequested: ToyVoiceProviderType,
        override val errorMessage: String,
        override val latencyMs: Long,
        override val errorType: VoiceErrorType? = null,
        override val metric: VoicePlaybackMetric? = null
    ) : VoiceOutcome {
        override val providerUsed: ToyVoiceProviderType? = null
        override val fallbackUsed: Boolean = false
        override val fallbackFrom: ToyVoiceProviderType? = null
        override val fallbackTo: ToyVoiceProviderType? = null
        override val cacheHit: Boolean? = null
        override val cacheKey: String? = null
        override val cacheLookupLatencyMs: Long? = null
        override val synthesisLatencyMs: Long? = null
        override val playbackLatencyMs: Long? = null
        override val totalLatencyMs: Long? = null
    }

    data class SkippedInvalidText(
        override val providerRequested: ToyVoiceProviderType,
        val reason: InvalidToyVoiceTextReason,
        val textLength: Int,
        override val latencyMs: Long,
        override val metric: VoicePlaybackMetric? = null
    ) : VoiceOutcome {
        override val providerUsed: ToyVoiceProviderType? = null
        override val fallbackUsed: Boolean = false
        override val fallbackFrom: ToyVoiceProviderType? = null
        override val fallbackTo: ToyVoiceProviderType? = null
        override val errorMessage: String = SAFE_INVALID_TEXT_MESSAGE
        override val errorType: VoiceErrorType = VoiceErrorType.INVALID_TTS_TEXT
        override val cacheHit: Boolean? = null
        override val cacheKey: String? = null
        override val cacheLookupLatencyMs: Long? = null
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
        mode: VoiceMode = VoiceMode.UNKNOWN,
        voiceContext: VoiceContext = VoiceContext.UNKNOWN,
        providerInfo: (ToyVoiceProviderType) -> VoiceProviderInfo = { VoiceProviderInfo() },
        onPlaybackStart: () -> Unit = {}
    ): VoiceOutcome {
        val startedAt = System.currentTimeMillis()
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) {
            val latencyMs = System.currentTimeMillis() - startedAt
            val metric = VoicePlaybackMetric(
                eventType = VoiceMetricEventType.VOICE_SKIPPED_INVALID_TEXT,
                timestamp = System.currentTimeMillis(),
                mode = mode,
                voiceContext = voiceContext,
                providerRequested = providerRequested,
                providerUsed = null,
                fallbackUsed = false,
                textLength = text?.length ?: 0,
                textHash = textHashForVoiceMetric(text),
                totalVoiceLatencyMs = latencyMs,
                errorType = VoiceErrorType.INVALID_TTS_TEXT,
                safeErrorMessage = VoiceOutcome.SAFE_INVALID_TEXT_MESSAGE,
                skippedInvalidText = true
            )
            return VoiceOutcome.SkippedInvalidText(
                providerRequested = providerRequested,
                reason = validation.reason ?: InvalidToyVoiceTextReason.EMPTY_TEXT,
                textLength = text?.length ?: 0,
                latencyMs = latencyMs,
                metric = metric
            )
        }

        val failures = mutableListOf<ProviderFailure>()
        for ((type, provider) in providers) {
            val playbackStartRequestedAt = System.currentTimeMillis()
            var playbackStartedAt: Long? = null
            val wrappedPlaybackStart = {
                playbackStartedAt = System.currentTimeMillis()
                onPlaybackStart()
            }
            when (val result = provider.speak(validation.normalizedText, wrappedPlaybackStart)) {
                is VoicePlaybackResult.Success -> {
                    val latencyMs = System.currentTimeMillis() - startedAt
                    val fallbackUsed = type != providerRequested
                    val firstFailure = failures.firstOrNull()
                    val info = providerInfo(type)
                    val metric = VoicePlaybackMetric(
                        eventType = if (fallbackUsed) {
                            VoiceMetricEventType.VOICE_FALLBACK_USED
                        } else {
                            VoiceMetricEventType.VOICE_PLAYBACK_COMPLETED
                        },
                        timestamp = System.currentTimeMillis(),
                        mode = mode,
                        voiceContext = voiceContext,
                        providerRequested = providerRequested,
                        providerUsed = type,
                        fallbackUsed = fallbackUsed,
                        fallbackFrom = if (fallbackUsed) providerRequested else null,
                        fallbackTo = if (fallbackUsed) type else null,
                        model = info.model,
                        voice = info.voice,
                        cacheHit = result.cacheHit,
                        cacheProvider = if (result.cacheHit != null) type else null,
                        cacheKey = result.cacheKey,
                        textLength = validation.normalizedText.length,
                        textHash = textHashForVoiceMetric(validation.normalizedText),
                        cacheLookupLatencyMs = result.cacheLookupLatencyMs,
                        synthesisLatencyMs = result.synthesisLatencyMs,
                        playbackStartLatencyMs = playbackStartedAt?.minus(playbackStartRequestedAt),
                        playbackDurationMs = result.playbackLatencyMs,
                        totalVoiceLatencyMs = result.totalLatencyMs ?: latencyMs,
                        errorType = firstFailure?.safeType(),
                        safeErrorMessage = firstFailure?.message
                    )
                    return VoiceOutcome.Completed(
                        providerRequested = providerRequested,
                        providerUsed = type,
                        fallbackUsed = fallbackUsed,
                        fallbackFrom = metric.fallbackFrom,
                        fallbackTo = metric.fallbackTo,
                        errorMessage = firstFailure?.message,
                        errorType = firstFailure?.safeType(),
                        latencyMs = latencyMs,
                        cacheHit = result.cacheHit,
                        cacheKey = result.cacheKey,
                        cacheLookupLatencyMs = result.cacheLookupLatencyMs,
                        synthesisLatencyMs = result.synthesisLatencyMs,
                        playbackLatencyMs = result.playbackLatencyMs,
                        totalLatencyMs = result.totalLatencyMs ?: latencyMs,
                        metric = metric
                    )
                }

                is VoicePlaybackResult.Error -> {
                    failures.add(ProviderFailure(type, result.type, result.message))
                }
            }
        }

        val latencyMs = System.currentTimeMillis() - startedAt
        val lastFailure = failures.lastOrNull()
        val info = providerInfo(providerRequested)
        val safeMessage = failures.joinToString(" ") { it.message }.ifBlank { "No se pudo reproducir la voz." }
        val metric = VoicePlaybackMetric(
            eventType = VoiceMetricEventType.VOICE_PLAYBACK_FAILED,
            timestamp = System.currentTimeMillis(),
            mode = mode,
            voiceContext = voiceContext,
            providerRequested = providerRequested,
            providerUsed = null,
            fallbackUsed = false,
            model = info.model,
            voice = info.voice,
            textLength = validation.normalizedText.length,
            textHash = textHashForVoiceMetric(validation.normalizedText),
            totalVoiceLatencyMs = latencyMs,
            errorType = lastFailure?.safeType(),
            safeErrorMessage = safeMessage
        )
        return VoiceOutcome.Failed(
            providerRequested = providerRequested,
            errorMessage = safeMessage,
            latencyMs = latencyMs,
            errorType = lastFailure?.safeType(),
            metric = metric
        )
    }

    private data class ProviderFailure(
        val provider: ToyVoiceProviderType,
        val type: VoiceErrorType,
        val message: String
    )

    private fun ProviderFailure.safeType(): VoiceErrorType = when {
        type == VoiceErrorType.HTTP_ERROR && message.contains("401") -> VoiceErrorType.HTTP_401
        type == VoiceErrorType.HTTP_ERROR && message.contains("403") -> VoiceErrorType.HTTP_403
        type == VoiceErrorType.HTTP_ERROR && message.contains("429") -> VoiceErrorType.HTTP_429
        type == VoiceErrorType.NO_NETWORK -> VoiceErrorType.NETWORK_ERROR
        else -> type
    }
}
