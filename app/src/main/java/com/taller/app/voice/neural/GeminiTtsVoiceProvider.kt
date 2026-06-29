package com.taller.app.voice.neural

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.taller.app.voice.CacheableVoiceProvider
import com.taller.app.voice.ToyVoiceProvider
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.ToyVoiceTextValidator
import com.taller.app.voice.VoiceErrorType
import com.taller.app.voice.VoiceOutcome
import com.taller.app.voice.VoicePlaybackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class GeminiTtsVoiceProvider(
    private val context: Context,
    private val rateLimitGate: GeminiRateLimitGate = GeminiRateLimitGate.shared,
    private val configProvider: () -> GeminiTtsConfig
) : ToyVoiceProvider, CacheableVoiceProvider {
    private val audioCache = OpenAiTtsAudioCache(context)
    private val synthesisLocks = mutableMapOf<String, Mutex>()
    private val synthesisLocksGuard = Any()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var mediaPlayer: MediaPlayer? = null

    override fun isConfigured(): Boolean = configProvider().isComplete

    override suspend fun speak(text: String, onPlaybackStart: () -> Unit): VoicePlaybackResult {
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) {
            Log.w(
                TAG,
                "eventType=TTS_SKIPPED_INVALID_TEXT providerRequested=GEMINI_TTS providerUsed=NONE " +
                    "textLength=${text.length} reason=${validation.reason} timestamp=${System.currentTimeMillis()}"
            )
            return VoicePlaybackResult.Error(
                VoiceErrorType.INVALID_TTS_TEXT,
                VoiceOutcome.SAFE_INVALID_TEXT_MESSAGE
            )
        }

        val config = configProvider()
        Log.d(
            TAG,
            "eventType=GEMINI_TTS_CONFIG configured=${config.isComplete} " +
                "apiKeyLength=${config.apiKey.length} model=${config.model} voice=${config.voiceName}"
        )
        val startedAt = System.currentTimeMillis()
        val cacheLookupStartedAt = System.currentTimeMillis()
        val cacheEntry = audioCache.entryFor(
            text = validation.normalizedText,
            config = config,
            responseFormat = RESPONSE_FORMAT
        )
        val cacheLookupLatencyMs = System.currentTimeMillis() - cacheLookupStartedAt
        val audio = when (val download = getOrCreateAudio(validation.normalizedText, config, cacheEntry)) {
            is AudioResult.Failure -> return download.error
            is AudioResult.Ok -> download
        }

        val playbackStartedAt = System.currentTimeMillis()
        val playback = playFile(audio.file, onPlaybackStart)
        val playbackLatencyMs = System.currentTimeMillis() - playbackStartedAt
        val totalLatencyMs = System.currentTimeMillis() - startedAt
        if (playback is VoicePlaybackResult.Success) {
            audioCache.rememberCacheResult(ToyVoiceProviderType.GEMINI_TTS, audio.cacheHit)
            return playback.copy(
                cacheHit = audio.cacheHit,
                cacheKey = audio.cacheShortKey,
                cacheLookupLatencyMs = cacheLookupLatencyMs,
                synthesisLatencyMs = audio.synthesisLatencyMs,
                playbackLatencyMs = playbackLatencyMs,
                totalLatencyMs = totalLatencyMs
            )
        }

        if (audio.cacheHit) {
            runCatching { audio.file.delete() }
            Log.w(TAG, "eventType=GEMINI_TTS_CACHE_CORRUPT cacheKey=${audio.cacheShortKey}")
            return speakWithoutCachedFile(validation.normalizedText, config, cacheEntry, onPlaybackStart, startedAt)
        }

        return playback
    }

    override fun isCached(text: String): Boolean {
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) return false
        val entry = runCatching {
            audioCache.entryFor(validation.normalizedText, configProvider(), RESPONSE_FORMAT)
        }.getOrNull() ?: return false
        return entry.file.isFile && entry.file.length() > 0L
    }

    override suspend fun synthesizeToCache(text: String): VoicePlaybackResult {
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.INVALID_TTS_TEXT,
                VoiceOutcome.SAFE_INVALID_TEXT_MESSAGE
            )
        }
        val config = configProvider()
        val cacheEntry = audioCache.entryFor(validation.normalizedText, config, RESPONSE_FORMAT)
        return when (val result = getOrCreateAudio(validation.normalizedText, config, cacheEntry)) {
            is AudioResult.Ok -> VoicePlaybackResult.Success(
                cacheHit = result.cacheHit,
                cacheKey = result.cacheShortKey,
                synthesisLatencyMs = result.synthesisLatencyMs
            )
            is AudioResult.Failure -> result.error
        }
    }

    override suspend fun speakFromCacheOrNull(
        text: String,
        onPlaybackStart: () -> Unit
    ): VoicePlaybackResult? {
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) return null
        val cacheEntry = audioCache.entryFor(validation.normalizedText, configProvider(), RESPONSE_FORMAT)
        if (!(cacheEntry.file.isFile && cacheEntry.file.length() > 0L)) return null
        val playback = playFile(cacheEntry.file, onPlaybackStart)
        if (playback is VoicePlaybackResult.Success) {
            audioCache.rememberCacheResult(ToyVoiceProviderType.GEMINI_TTS, true)
            return playback.copy(cacheHit = true, cacheKey = cacheEntry.shortKey)
        }
        // Cache corrupta: se descarta sin hacer llamada de red durante la sesion.
        runCatching { cacheEntry.file.delete() }
        Log.w(TAG, "eventType=GEMINI_TTS_CACHE_CORRUPT_PREPARED cacheKey=${cacheEntry.shortKey}")
        return null
    }

    private suspend fun getOrCreateAudio(
        text: String,
        config: GeminiTtsConfig,
        cacheEntry: OpenAiTtsAudioCache.CacheEntry
    ): AudioResult {
        if (cacheEntry.file.isFile && cacheEntry.file.length() > 0L) {
            return AudioResult.Ok(
                file = cacheEntry.file,
                cacheHit = true,
                cacheShortKey = cacheEntry.shortKey,
                synthesisLatencyMs = 0L
            )
        }

        // Enfriamiento por 429: si Gemini esta limitado y no hay audio cacheado, se
        // omite la llamada de red y se deja que la cadena use el respaldo (OpenAI).
        // Los aciertos de cache (arriba) siempre se permiten, aun en enfriamiento.
        if (rateLimitGate.isInCooldown()) {
            val reason = rateLimitGate.activeReason() ?: VoiceErrorType.RATE_LIMITED
            Log.w(TAG, "eventType=GEMINI_TTS_COOLDOWN_SKIP reason=$reason remainingMs=${rateLimitGate.remainingMs()}")
            return AudioResult.Failure(
                VoicePlaybackResult.Error(
                    reason,
                    "Gemini en enfriamiento por limite de cuota (HTTP 429)."
                )
            )
        }

        val lock = lockFor(cacheEntry.key)
        return lock.withLock {
            if (cacheEntry.file.isFile && cacheEntry.file.length() > 0L) {
                AudioResult.Ok(
                    file = cacheEntry.file,
                    cacheHit = true,
                    cacheShortKey = cacheEntry.shortKey,
                    synthesisLatencyMs = 0L
                )
            } else {
                requestAudio(text, config, cacheEntry)
            }
        }
    }

    private suspend fun speakWithoutCachedFile(
        text: String,
        config: GeminiTtsConfig,
        cacheEntry: OpenAiTtsAudioCache.CacheEntry,
        onPlaybackStart: () -> Unit,
        startedAt: Long
    ): VoicePlaybackResult {
        val audio = when (val result = lockFor(cacheEntry.key).withLock {
            requestAudio(text, config, cacheEntry)
        }) {
            is AudioResult.Failure -> return result.error
            is AudioResult.Ok -> result
        }
        val playbackStartedAt = System.currentTimeMillis()
        val playback = playFile(audio.file, onPlaybackStart)
        val playbackLatencyMs = System.currentTimeMillis() - playbackStartedAt
        val totalLatencyMs = System.currentTimeMillis() - startedAt
        return if (playback is VoicePlaybackResult.Success) {
            audioCache.rememberCacheResult(ToyVoiceProviderType.GEMINI_TTS, false)
            playback.copy(
                cacheHit = false,
                cacheKey = audio.cacheShortKey,
                synthesisLatencyMs = audio.synthesisLatencyMs,
                playbackLatencyMs = playbackLatencyMs,
                totalLatencyMs = totalLatencyMs
            )
        } else {
            playback
        }
    }

    private fun lockFor(cacheKey: String): Mutex = synchronized(synthesisLocksGuard) {
        synthesisLocks.getOrPut(cacheKey) { Mutex() }
    }

    private suspend fun requestAudio(
        text: String,
        config: GeminiTtsConfig,
        cacheEntry: OpenAiTtsAudioCache.CacheEntry
    ): AudioResult =
        withContext(Dispatchers.IO) {
            if (!config.hasApiKey) {
                return@withContext AudioResult.Failure(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.NOT_CONFIGURED,
                        "Gemini fallo: no configurado."
                    )
                )
            }
            val body = GeminiTtsProtocol.buildRequestJson(text, config).toRequestBody(JSON_MEDIA_TYPE)
            val url = GeminiTtsProtocol.endpointUrl(config.model)
            Log.d(
                TAG,
                "eventType=GEMINI_TTS_REQUEST configured=${config.isComplete} model=${config.model} " +
                    "voice=${config.voiceName} endpoint=$url textLength=${text.length}"
            )
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", config.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            executeAudioRequest(request, cacheEntry, retryServerError = true)?.let { return@withContext it }
            AudioResult.Failure(
                VoicePlaybackResult.Error(
                    VoiceErrorType.UNKNOWN,
                    "No se pudo generar audio con Gemini TTS."
                )
            )
        }

    private fun executeAudioRequest(
        request: Request,
        cacheEntry: OpenAiTtsAudioCache.CacheEntry,
        retryServerError: Boolean
    ): AudioResult? {
        val startedAt = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "eventType=GEMINI_TTS_HTTP code=${response.code}")
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string().orEmpty()
                    val safeDetail = GeminiTtsProtocol.safeErrorMessage(responseBody)
                    // HTTP 429: limite de ritmo / cuota del plan. Solo se respeta el
                    // tiempo de espera que Google indique (header Retry-After o campo
                    // retryDelay del cuerpo); la app no impone bloqueo propio. Si Google
                    // no pide esperar, solo cae al respaldo en esta frase y la siguiente
                    // vuelve a intentar Gemini con normalidad.
                    if (response.code == 429) {
                        val retryAfterMs = parseRetryAfterMs(response.header("Retry-After"))
                            ?: GeminiTtsProtocol.retryDelayMs(responseBody)
                        val category = classifyRateLimit(responseBody)
                        rateLimitGate.registerRateLimit(category, retryAfterMs)
                        Log.w(
                            TAG,
                            "eventType=GEMINI_TTS_RATE_LIMIT code=429 category=$category " +
                                "retryAfterMs=${retryAfterMs ?: "none"} cooldownRemainingMs=${rateLimitGate.remainingMs()}"
                        )
                        return AudioResult.Failure(
                            VoicePlaybackResult.Error(category, safeHttpMessage(429, safeDetail))
                        )
                    }
                    Log.w(
                        TAG,
                        "eventType=GEMINI_TTS_HTTP_ERROR code=${response.code} " +
                            "message=${safeDetail ?: safeHttpMessage(response.code)}"
                    )
                    if (retryServerError && response.code >= 500) {
                        Log.w(TAG, "eventType=GEMINI_TTS_RETRY code=${response.code}")
                        return executeAudioRequest(request, cacheEntry, retryServerError = false)
                    }
                    return AudioResult.Failure(
                        VoicePlaybackResult.Error(
                            VoiceErrorType.HTTP_ERROR,
                            safeHttpMessage(response.code, safeDetail)
                        )
                    )
                }
                val responseBody = response.body?.string().orEmpty()
                Log.d(TAG, "eventType=GEMINI_TTS_HTTP_SUCCESS bodyLength=${responseBody.length}")
                val payload = try {
                    GeminiTtsProtocol.parseAudio(responseBody)
                } catch (e: GeminiTtsParseException) {
                    Log.w(
                        TAG,
                        "eventType=GEMINI_TTS_PARSE_ERROR hasCandidates=${e.diagnostics.hasCandidates} " +
                            "candidateCount=${e.diagnostics.candidateCount} hasContent=${e.diagnostics.hasContent} " +
                            "hasParts=${e.diagnostics.hasParts} partCount=${e.diagnostics.partCount} " +
                            "hasInlineData=${e.diagnostics.hasInlineData} hasText=${e.diagnostics.hasText} " +
                            "mimeType=${e.diagnostics.mimeType} base64Chars=${e.diagnostics.base64Chars} " +
                            "audioBytes=${e.diagnostics.audioBytes} base64DecodeFailed=${e.diagnostics.base64DecodeFailed} " +
                            "finishReason=${e.diagnostics.finishReason} hasPromptFeedback=${e.diagnostics.hasPromptFeedback} " +
                            "message=${e.safeMessage}"
                    )
                    return AudioResult.Failure(
                        VoicePlaybackResult.Error(VoiceErrorType.INVALID_AUDIO, e.safeMessage)
                    )
                }
                val wrapAsWav = GeminiTtsProtocol.shouldWrapAsWav(payload.mimeType)
                val sampleRate = GeminiTtsProtocol.pcmSampleRate(payload.mimeType)
                Log.d(
                    TAG,
                    "eventType=GEMINI_TTS_AUDIO mimeType=${payload.mimeType} " +
                        "decodedBytes=${payload.bytes.size} wrapAsWav=$wrapAsWav sampleRate=$sampleRate"
                )
                val bytes = if (wrapAsWav) {
                    GeminiWavWriter.wrapPcm16Mono(payload.bytes, sampleRate)
                } else {
                    payload.bytes
                }
                val file = cacheEntry.file
                try {
                    val tempFile = File(file.parentFile, "${file.name}.tmp")
                    tempFile.writeBytes(bytes)
                    if (file.exists()) {
                        runCatching { file.delete() }
                    }
                    if (!tempFile.renameTo(file)) {
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                    audioCache.writeMetadata(cacheEntry)
                } catch (e: IOException) {
                    Log.w(TAG, "eventType=GEMINI_TTS_FILE_ERROR message=temporary_audio_write_failed")
                    return AudioResult.Failure(
                        VoicePlaybackResult.Error(
                            VoiceErrorType.INVALID_AUDIO,
                            "Gemini fallo: no se pudo preparar el audio."
                        )
                    )
                }
                Log.d(TAG, "eventType=GEMINI_TTS_FILE_READY suffix=.$RESPONSE_FORMAT fileBytes=${file.length()}")
                // Sintesis exitosa: limpia cualquier enfriamiento por 429 previo.
                rateLimitGate.registerSuccess()
                return AudioResult.Ok(
                    file = file,
                    cacheHit = false,
                    cacheShortKey = cacheEntry.shortKey,
                    synthesisLatencyMs = System.currentTimeMillis() - startedAt
                )
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "eventType=GEMINI_TTS_NETWORK_ERROR type=timeout")
            return AudioResult.Failure(
                VoicePlaybackResult.Error(VoiceErrorType.TIMEOUT, "Gemini fallo: tiempo de espera agotado.")
            )
        } catch (e: IOException) {
            Log.w(TAG, "eventType=GEMINI_TTS_NETWORK_ERROR type=io")
            return AudioResult.Failure(
                VoicePlaybackResult.Error(VoiceErrorType.NO_NETWORK, "Gemini fallo: sin conexion disponible.")
            )
        } catch (e: Exception) {
            Log.w(TAG, "eventType=GEMINI_TTS_UNKNOWN_ERROR")
            return AudioResult.Failure(
                VoicePlaybackResult.Error(VoiceErrorType.UNKNOWN, "Gemini fallo: error inesperado.")
            )
        }
    }

    /**
     * Clasifica un 429 de Gemini: si el cuerpo indica cuota agotada
     * (RESOURCE_EXHAUSTED / quota) se trata como [VoiceErrorType.QUOTA_EXHAUSTED];
     * en otro caso como [VoiceErrorType.RATE_LIMITED]. Solo inspecciona texto
     * tecnico del error, nunca datos del nino.
     */
    private fun classifyRateLimit(responseBody: String): VoiceErrorType {
        val lower = responseBody.lowercase()
        return if (lower.contains("resource_exhausted") || lower.contains("quota")) {
            VoiceErrorType.QUOTA_EXHAUSTED
        } else {
            VoiceErrorType.RATE_LIMITED
        }
    }

    /**
     * Convierte el header Retry-After (segundos enteros) a milisegundos. Si viene
     * vacio, como fecha HTTP o no parseable, devuelve null y el enfriamiento usara
     * su valor por defecto.
     */
    private fun parseRetryAfterMs(headerValue: String?): Long? {
        val seconds = headerValue?.trim()?.toLongOrNull() ?: return null
        if (seconds <= 0L) return null
        return seconds * 1000L
    }

    private fun safeHttpMessage(code: Int, detail: String? = null): String = when (code) {
        401, 403 -> "Gemini fallo: API key no autorizada (HTTP $code)."
        400 -> "Gemini fallo: solicitud invalida (HTTP 400)."
        429 -> "Gemini fallo: limite de cuota del plan (HTTP 429). Reintenta en ~1 min."
        else -> "Gemini fallo: error HTTP $code."
    }.let { base ->
        if (detail.isNullOrBlank()) base else "$base ${detail.take(MAX_SAFE_HTTP_DETAIL_LENGTH)}"
    }

    private suspend fun playFile(
        file: File,
        onPlaybackStart: () -> Unit
    ): VoicePlaybackResult = suspendCancellableCoroutine { continuation ->
        stop()
        val player = MediaPlayer()
        mediaPlayer = player

        fun cleanup() {
            try {
                player.reset()
                player.release()
            } catch (_: Exception) {
            }
            if (mediaPlayer === player) mediaPlayer = null
        }

        player.setOnCompletionListener {
            cleanup()
            if (continuation.isActive) continuation.resume(VoicePlaybackResult.Success())
        }
        player.setOnErrorListener { _, what, _ ->
            Log.w(TAG, "eventType=GEMINI_TTS_PLAYBACK_ERROR code=$what")
            cleanup()
            if (continuation.isActive) {
                continuation.resume(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.PLAYBACK_FAILED,
                        "Gemini fallo: reproduccion no disponible."
                    )
                )
            }
            true
        }
        player.setOnPreparedListener {
            Log.d(TAG, "eventType=GEMINI_TTS_PLAYBACK_START")
            onPlaybackStart()
            player.start()
        }

        continuation.invokeOnCancellation { cleanup() }

        try {
            player.setDataSource(file.absolutePath)
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "eventType=GEMINI_TTS_PLAYBACK_PREPARE_ERROR")
            cleanup()
            if (continuation.isActive) {
                continuation.resume(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.INVALID_AUDIO,
                        "Gemini fallo: audio invalido."
                    )
                )
            }
        }
    }

    override fun stop() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) player.stop()
            player.reset()
            player.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    override fun release() = stop()

    private sealed interface AudioResult {
        data class Ok(
            val file: File,
            val cacheHit: Boolean,
            val cacheShortKey: String,
            val synthesisLatencyMs: Long
        ) : AudioResult
        data class Failure(val error: VoicePlaybackResult.Error) : AudioResult
    }

    companion object {
        private const val TAG = "GeminiTtsVoice"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val MAX_SAFE_HTTP_DETAIL_LENGTH = 120
        private const val RESPONSE_FORMAT = OpenAiTtsAudioCache.RESPONSE_FORMAT_WAV
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
