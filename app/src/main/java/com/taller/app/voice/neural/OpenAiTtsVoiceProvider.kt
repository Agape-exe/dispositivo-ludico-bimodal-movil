package com.taller.app.voice.neural

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.taller.app.voice.ToyVoiceProvider
import com.taller.app.voice.VoiceErrorType
import com.taller.app.voice.VoicePlaybackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class OpenAiTtsVoiceProvider(
    private val context: Context,
    private val configProvider: () -> OpenAiTtsConfig
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
        val config = configProvider()
        if (!config.hasApiKey) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.NOT_CONFIGURED,
                "OpenAI TTS no esta configurado."
            )
        }

        val audioFile = when (val download = requestAudio(text, config)) {
            is AudioResult.Failure -> return download.error
            is AudioResult.Ok -> download.file
        }

        return try {
            playFile(audioFile, onPlaybackStart)
        } finally {
            audioFile.delete()
        }
    }

    private suspend fun requestAudio(text: String, config: OpenAiTtsConfig): AudioResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", config.model)
                .put("voice", config.voice)
                .put("input", text)
                .put("instructions", config.instructions)
                .put("response_format", "mp3")
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(SPEECH_URL)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AudioResult.Failure(
                            VoicePlaybackResult.Error(
                                VoiceErrorType.HTTP_ERROR,
                                "OpenAI TTS respondio con error (codigo ${response.code})."
                            )
                        )
                    }
                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        return@withContext AudioResult.Failure(
                            VoicePlaybackResult.Error(
                                VoiceErrorType.INVALID_AUDIO,
                                "OpenAI TTS devolvio audio vacio."
                            )
                        )
                    }
                    val file = File.createTempFile("toy_openai_tts_", ".mp3", context.cacheDir)
                    file.writeBytes(bytes)
                    AudioResult.Ok(file)
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout al contactar OpenAI TTS")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(VoiceErrorType.TIMEOUT, "OpenAI TTS tardo demasiado en responder.")
                )
            } catch (e: IOException) {
                Log.w(TAG, "Error de red con OpenAI TTS")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(VoiceErrorType.NO_NETWORK, "No hay conexion con OpenAI TTS.")
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error inesperado con OpenAI TTS")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(VoiceErrorType.UNKNOWN, "Ocurrio un error inesperado con OpenAI TTS.")
                )
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
            if (continuation.isActive) continuation.resume(VoicePlaybackResult.Success)
        }
        player.setOnErrorListener { _, what, _ ->
            cleanup()
            if (continuation.isActive) {
                continuation.resume(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.PLAYBACK_FAILED,
                        "No se pudo reproducir el audio de OpenAI TTS (codigo $what)."
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
                        "El audio recibido de OpenAI TTS no es valido."
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
        data class Ok(val file: File) : AudioResult
        data class Failure(val error: VoicePlaybackResult.Error) : AudioResult
    }

    companion object {
        private const val TAG = "OpenAiTtsVoice"
        private const val SPEECH_URL = "https://api.openai.com/v1/audio/speech"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
