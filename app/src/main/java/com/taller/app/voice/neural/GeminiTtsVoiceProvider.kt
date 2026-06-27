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
        if (!config.hasApiKey) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.NOT_CONFIGURED,
                "Gemini TTS no esta configurado."
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
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent"
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
                if (!response.isSuccessful) {
                    if (retryServerError && response.code >= 500) {
                        return executeAudioRequest(request, retryServerError = false)
                    }
                    return AudioResult.Failure(
                        VoicePlaybackResult.Error(
                            VoiceErrorType.HTTP_ERROR,
                            safeHttpMessage(response.code)
                        )
                    )
                }
                val responseBody = response.body?.string().orEmpty()
                val payload = try {
                    GeminiTtsProtocol.parseAudio(responseBody)
                } catch (e: GeminiTtsParseException) {
                    return AudioResult.Failure(
                        VoicePlaybackResult.Error(VoiceErrorType.INVALID_AUDIO, e.message ?: "Audio Gemini invalido.")
                    )
                }
                val bytes = if (GeminiTtsProtocol.shouldWrapAsWav(payload.mimeType)) {
                    GeminiWavWriter.wrapPcm16Mono24Khz(payload.bytes)
                } else {
                    payload.bytes
                }
                val suffix = if (GeminiTtsProtocol.shouldWrapAsWav(payload.mimeType)) ".wav" else audioSuffix(payload.mimeType)
                val file = File.createTempFile("toy_gemini_", suffix, context.cacheDir)
                file.writeBytes(bytes)
                return AudioResult.Ok(
                    file = file,
                    synthesisLatencyMs = System.currentTimeMillis() - startedAt
                )
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Timeout al contactar Gemini TTS")
            return AudioResult.Failure(
                VoicePlaybackResult.Error(VoiceErrorType.TIMEOUT, "Gemini TTS tardo demasiado en responder.")
            )
        } catch (e: IOException) {
            Log.w(TAG, "Error de red con Gemini TTS")
            return AudioResult.Failure(
                VoicePlaybackResult.Error(VoiceErrorType.NO_NETWORK, "No hay conexion con Gemini TTS.")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error inesperado con Gemini TTS")
            return AudioResult.Failure(
                VoicePlaybackResult.Error(VoiceErrorType.UNKNOWN, "Ocurrio un error inesperado con Gemini TTS.")
            )
        }
    }

    private fun safeHttpMessage(code: Int): String = when (code) {
        400, 401, 403 -> "Gemini TTS no pudo autorizar o procesar la solicitud."
        429 -> "Gemini TTS alcanzo un limite de cuota."
        else -> "Gemini TTS respondio con error (codigo $code)."
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
            cleanup()
            if (continuation.isActive) {
                continuation.resume(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.PLAYBACK_FAILED,
                        "No se pudo reproducir el audio de Gemini TTS (codigo $what)."
                    )
                )
            }
            true
        }
        player.setOnPreparedListener {
            onPlaybackStart()
            player.start()
        }

        continuation.invokeOnCancellation { cleanup() }

        try {
            player.setDataSource(file.absolutePath)
            player.prepareAsync()
        } catch (e: Exception) {
            cleanup()
            if (continuation.isActive) {
                continuation.resume(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.INVALID_AUDIO,
                        "El audio recibido de Gemini TTS no es valido."
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
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
