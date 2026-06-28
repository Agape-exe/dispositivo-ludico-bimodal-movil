package com.taller.app.voice

/**
 * Origen real de la ultima voz reproducida por Seven. Permite responder en el
 * panel tecnico: "¿el ultimo mensaje salio de cache o se genero en vivo?".
 */
enum class VoicePlaybackSource {
    /** Aun no se ha reproducido ninguna voz en esta sesion. */
    NONE,

    /** El audio venia de cache local: no hubo llamada de red. */
    CACHE_HIT,

    /** Se buscaba en cache pero no existia el audio (sin red en modo cache-only). */
    CACHE_MISS,

    /** El audio se sintetizo en vivo con el proveedor preferido (llamada de red). */
    NETWORK_SYNTHESIS,

    /** Se uso un proveedor de respaldo porque el preferido fallo. */
    FALLBACK_PROVIDER,

    /** Se reprodujo con el TTS local del dispositivo. */
    LOCAL_TTS,

    /** Hubo un error y no se reprodujo voz. */
    ERROR
}

/**
 * Modo con el que se solicito la reproduccion, para distinguir el camino estricto
 * de cache del camino que aun puede sintetizar en vivo.
 */
enum class VoicePlaybackMode {
    /** Solo cache: nunca llama a la red (camino seguro de sesion real). */
    CACHE_ONLY,

    /** Reutiliza cache y, si falta, puede sintetizar (camino actual del bimodal). */
    CACHE_OR_SYNTHESIZE,

    /** Sintesis en vivo forzada (pruebas/configuracion). */
    LIVE_SYNTHESIS,

    /** Respaldo con TTS local. */
    LOCAL_FALLBACK,

    UNKNOWN
}

/**
 * Estado tecnico, en memoria, de la ultima reproduccion de voz. Solo guarda
 * etiquetas cortas, codigos y latencias: nunca el texto hablado completo, claves
 * ni payloads. El [textHashShort] es un hash corto no reversible en la practica.
 */
data class VoicePlaybackDebugInfo(
    val source: VoicePlaybackSource,
    val playbackMode: VoicePlaybackMode,
    val provider: String?,
    val model: String?,
    val voice: String?,
    val lineType: String?,
    val cacheHit: Boolean?,
    val cacheLookupMs: Long?,
    val playbackStartMs: Long?,
    val totalVoiceMs: Long?,
    val artificialDelayMs: Long?,
    val fallbackReason: String?,
    val sanitizedError: String?,
    val textHashShort: String?,
    val updatedAtMs: Long
) {
    companion object {
        /** Estado inicial: aun no se ha reproducido voz. */
        fun none(): VoicePlaybackDebugInfo = VoicePlaybackDebugInfo(
            source = VoicePlaybackSource.NONE,
            playbackMode = VoicePlaybackMode.UNKNOWN,
            provider = null,
            model = null,
            voice = null,
            lineType = null,
            cacheHit = null,
            cacheLookupMs = null,
            playbackStartMs = null,
            totalVoiceMs = null,
            artificialDelayMs = null,
            fallbackReason = null,
            sanitizedError = null,
            textHashShort = null,
            updatedAtMs = 0L
        )
    }
}

/**
 * Construye un [VoicePlaybackDebugInfo] a partir del resultado de la reproduccion.
 * Es codigo puro (sin Android) para poder probarse de forma aislada.
 */
object VoicePlaybackDebugMapper {

    private const val MAX_ERROR_LENGTH = 90

    fun fromOutcome(
        outcome: VoiceOutcome?,
        playbackMode: VoicePlaybackMode,
        artificialDelayMs: Long? = null,
        now: Long = System.currentTimeMillis()
    ): VoicePlaybackDebugInfo {
        val metric = outcome?.metric
        val lineType = lineTypeFromContext(metric?.voiceContext)
        val base = VoicePlaybackDebugInfo.none().copy(
            playbackMode = playbackMode,
            lineType = lineType,
            updatedAtMs = now
        )

        return when (outcome) {
            null -> base.copy(
                source = VoicePlaybackSource.ERROR,
                sanitizedError = "SIN_RESULTADO_O_TIMEOUT"
            )

            is VoiceOutcome.Completed -> {
                val provider = outcome.providerUsed
                val source = when {
                    outcome.fallbackUsed -> VoicePlaybackSource.FALLBACK_PROVIDER
                    provider == ToyVoiceProviderType.LOCAL -> VoicePlaybackSource.LOCAL_TTS
                    outcome.cacheHit == true -> VoicePlaybackSource.CACHE_HIT
                    outcome.cacheHit == false -> VoicePlaybackSource.NETWORK_SYNTHESIS
                    else -> VoicePlaybackSource.CACHE_MISS
                }
                base.copy(
                    source = source,
                    provider = providerShortName(provider),
                    model = metric?.model,
                    voice = metric?.voice,
                    cacheHit = outcome.cacheHit,
                    cacheLookupMs = outcome.cacheLookupLatencyMs ?: metric?.cacheLookupLatencyMs,
                    playbackStartMs = metric?.playbackStartLatencyMs,
                    totalVoiceMs = outcome.totalLatencyMs ?: metric?.totalVoiceLatencyMs,
                    artificialDelayMs = artificialDelayMs,
                    fallbackReason = if (outcome.fallbackUsed) {
                        safeReason(outcome.errorType)
                    } else {
                        null
                    },
                    textHashShort = metric?.textHash
                )
            }

            is VoiceOutcome.Failed -> base.copy(
                // En cache-only un fallo significa que la pieza no estaba preparada:
                // es un CACHE_MISS sin llamada de red, no un error de red.
                source = if (playbackMode == VoicePlaybackMode.CACHE_ONLY) {
                    VoicePlaybackSource.CACHE_MISS
                } else {
                    VoicePlaybackSource.ERROR
                },
                provider = providerShortName(outcome.providerUsed),
                fallbackReason = safeReason(outcome.errorType),
                sanitizedError = sanitizeError(outcome.errorMessage),
                textHashShort = metric?.textHash
            )

            is VoiceOutcome.SkippedInvalidText -> base.copy(
                source = VoicePlaybackSource.ERROR,
                sanitizedError = "TEXTO_INVALIDO",
                textHashShort = metric?.textHash
            )
        }
    }

    /** Tipo de linea reproducida a partir del contexto de voz. */
    fun lineTypeFromContext(context: VoiceContext?): String = when (context) {
        VoiceContext.GREETING -> "INTRO"
        VoiceContext.QUESTION -> "QUESTION"
        VoiceContext.FEEDBACK_CORRECT -> "POSITIVE_FEEDBACK"
        VoiceContext.FEEDBACK_INCORRECT,
        VoiceContext.NOT_INTERPRETABLE -> "SUPPORTIVE_FEEDBACK"
        VoiceContext.FEEDBACK_RETRY -> "RETRY"
        VoiceContext.RECAPTURE -> "GENERIC"
        VoiceContext.CLOSING -> "CLOSING"
        VoiceContext.TEST,
        VoiceContext.COUNTDOWN,
        VoiceContext.UNKNOWN,
        null -> "UNKNOWN"
    }

    private fun providerShortName(provider: ToyVoiceProviderType?): String = when (provider) {
        ToyVoiceProviderType.GEMINI_TTS -> "Gemini"
        ToyVoiceProviderType.OPENAI_TTS -> "OpenAI"
        ToyVoiceProviderType.AZURE_NEURAL -> "Azure"
        ToyVoiceProviderType.LOCAL -> "Android local"
        ToyVoiceProviderType.ELEVENLABS -> "ElevenLabs"
        null -> "Ninguno"
    }

    /** Codigo corto y seguro del motivo de fallback/error, sin exponer detalles. */
    private fun safeReason(errorType: VoiceErrorType?): String? = when (errorType) {
        null -> null
        VoiceErrorType.TIMEOUT -> "TIMEOUT"
        VoiceErrorType.RATE_LIMITED -> "RATE_LIMIT"
        VoiceErrorType.QUOTA_EXHAUSTED -> "QUOTA_EXHAUSTED"
        VoiceErrorType.HTTP_ERROR,
        VoiceErrorType.HTTP_401,
        VoiceErrorType.HTTP_403,
        VoiceErrorType.HTTP_429 -> "HTTP_ERROR"
        VoiceErrorType.RESPONSE_WITHOUT_AUDIO -> "EMPTY_AUDIO"
        VoiceErrorType.INVALID_AUDIO,
        VoiceErrorType.BASE64_INVALID,
        VoiceErrorType.WAV_WRITE_ERROR,
        VoiceErrorType.PLAYBACK_FAILED -> "INVALID_AUDIO"
        VoiceErrorType.INVALID_TTS_TEXT -> "TEXT_INVALID"
        VoiceErrorType.NOT_CONFIGURED -> "NOT_PREPARED"
        VoiceErrorType.NO_NETWORK,
        VoiceErrorType.NETWORK_ERROR -> "NETWORK_ERROR"
        VoiceErrorType.UNKNOWN -> "UNKNOWN"
    }

    /**
     * Recorta y limpia un mensaje de error para mostrarlo: sin saltos de linea, sin
     * credenciales y con largo acotado. Nunca incluye el texto hablado completo.
     */
    fun sanitizeError(message: String?): String? {
        val value = message?.takeIf { it.isNotBlank() } ?: return null
        return value
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("(?i)(api[_-]?key|bearer|token|secret)[^\\s]*"), "credential_redacted")
            .trim()
            .take(MAX_ERROR_LENGTH)
    }
}
