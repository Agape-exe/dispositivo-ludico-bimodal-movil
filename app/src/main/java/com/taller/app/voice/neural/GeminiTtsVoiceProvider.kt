package com.taller.app.voice.neural

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.taller.app.voice.ToyVoiceProvider
import com.taller.app.voice.ToyVoiceTextValidator
import com.taller.app.voice.VoiceErrorType
import com.taller.app.voice.VoiceOutcome
import com.taller.app.voice.VoicePlaybackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private val configProvider: () -> GeminiTtsConfig
) : ToyVoiceProvider {

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
        if (!config.hasApiKey) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.NOT_CONFIGURED,
                "Gemini fallo: no configurado."
            )
        }

        val startedAt = System.currentTimeMillis()
        val audio = when (val download = requestAudio(validation.normalizedText, config)) {
            is AudioResult.Failure -> return download.error
            is AudioResult.Ok -> download
        }

        return try {
            val playbackStartedAt = System.currentTimeMillis()
            val playback = playFile(audio.file, onPlaybackStart)
            val playbackLatencyMs = System.currentTimeMillis() - playbackStartedAt
            val totalLatencyMs = System.currentTimeMillis() - startedAt
            if (playback is VoicePlaybackResult.Success) {
                playback.copy(
                    cacheHit = false,
                    synthesisLatencyMs = audio.synthesisLatencyMs,
                    playbackLatencyMs = playbackLatencyMs,
                    totalLatencyMs = totalLatencyMs
                )
            } else {
                playback
            }
        } finally {
            audio.file.delete()
        }
    }

    private suspend fun requestAudio(text: String, config: GeminiTtsConfig): AudioResult =
        withContext(Dispatchers.IO) {
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

            executeAudioRequest(request, retryServerError = true)?.let { return@withContext it }
            AudioResult.Failure(
                VoicePlaybackResult.Error(
                    VoiceErrorType.UNKNOWN,
                    "No se pudo generar audio con Gemini TTS."
                )
            )
        }

    private fun executeAudioRequest(request: Request, retryServerError: Boolean): AudioResult? {
        val startedAt = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "eventType=GEMINI_TTS_HTTP code=${response.code}")
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string().orEmpty()
                    val safeDetail = GeminiTtsProtocol.safeErrorMessage(responseBody)
                    Log.w(
                        TAG,
                        "eventType=GEMINI_TTS_HTTP_ERROR code=${response.code} " +
                            "message=${safeDetail ?: safeHttpMessage(response.code)}"
                    )
                    if (retryServerError && response.code >= 500) {
                        Log.w(TAG, "eventType=GEMINI_TTS_RETRY code=${response.code}")
                        return executeAudioRequest(request, retryServerError = false)
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
                val suffix = if (GeminiTtsProtocol.shouldWrapAsWav(payload.mimeType)) ".wav" else audioSuffix(payload.mimeType)
                val file = try {
                    File.createTempFile("toy_gemini_", suffix, context.cacheDir).also { tempFile ->
                        tempFile.writeBytes(bytes)
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "eventType=GEMINI_TTS_FILE_ERROR message=temporary_audio_write_failed")
                    return AudioResult.Failure(
                        VoicePlaybackResult.Error(
                            VoiceErrorType.INVALID_AUDIO,
                            "Gemini fallo: no se pudo preparar el audio."
                        )
                    )
                }
                Log.d(TAG, "eventType=GEMINI_TTS_FILE_READY suffix=$suffix fileBytes=${file.length()}")
                return AudioResult.Ok(
                    file = file,
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

    private fun safeHttpMessage(code: Int, detail: String? = null): String = when (code) {
        401, 403 -> "Gemini fallo: API key no autorizada (HTTP $code)."
        400 -> "Gemini fallo: solicitud invalida (HTTP 400)."
        429 -> "Gemini fallo: limite de cuota del plan (HTTP 429). Reintenta en ~1 min."
        else -> "Gemini fallo: error HTTP $code."
    }.let { base ->
        if (detail.isNullOrBlank()) base else "$base ${detail.take(MAX_SAFE_HTTP_DETAIL_LENGTH)}"
    }

    private fun audioSuffix(mimeType: String): String {
        val lower = mimeType.lowercase()
        return when {
            lower.contains("wav") -> ".wav"
            lower.contains("mpeg") || lower.contains("mp3") -> ".mp3"
            lower.contains("ogg") -> ".ogg"
            else -> ".audio"
        }
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
        data class Ok(val file: File, val synthesisLatencyMs: Long) : AudioResult
        data class Failure(val error: VoicePlaybackResult.Error) : AudioResult
    }

    companion object {
        private const val TAG = "GeminiTtsVoice"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val MAX_SAFE_HTTP_DETAIL_LENGTH = 120
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
