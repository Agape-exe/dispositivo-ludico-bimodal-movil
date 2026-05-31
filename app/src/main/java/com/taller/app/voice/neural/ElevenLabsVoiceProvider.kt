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

/**
 * Proveedor de voz neural basado en la API REST de ElevenLabs Text-to-Speech.
 *
 * Lee la configuración (API key y voiceId) de una fuente local segura, envía el
 * texto, recibe audio MP3, lo reproduce desde un archivo temporal de [cacheDir]
 * y lo elimina al terminar. Maneja errores de red, credencial ausente, respuesta
 * inválida y timeout devolviendo un [VoicePlaybackResult.Error] tipado, sin
 * lanzar excepciones a la UI.
 */
class ElevenLabsVoiceProvider(
    private val context: Context,
    private val configProvider: () -> ElevenLabsConfig
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
                "Falta la credencial del proveedor neural."
            )
        }
        if (!config.hasVoiceId) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.NOT_CONFIGURED,
                "Falta el identificador de voz (voiceId)."
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

    private suspend fun requestAudio(text: String, config: ElevenLabsConfig): AudioResult =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/${config.voiceId}"
            val body = JSONObject()
                .put("text", text)
                .put("model_id", config.modelId)
                .put(
                    "voice_settings",
                    JSONObject()
                        .put("stability", 0.5)
                        .put("similarity_boost", 0.75)
                )
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", config.apiKey)
                .addHeader("accept", "audio/mpeg")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AudioResult.Failure(
                            VoicePlaybackResult.Error(
                                VoiceErrorType.HTTP_ERROR,
                                "El proveedor neural respondió con error (código ${response.code})."
                            )
                        )
                    }
                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        return@withContext AudioResult.Failure(
                            VoicePlaybackResult.Error(
                                VoiceErrorType.INVALID_AUDIO,
                                "El proveedor neural devolvió audio vacío."
                            )
                        )
                    }
                    val file = File.createTempFile("toy_neural_", ".mp3", context.cacheDir)
                    file.writeBytes(bytes)
                    AudioResult.Ok(file)
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout al contactar al proveedor neural")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.TIMEOUT,
                        "El proveedor neural tardó demasiado en responder."
                    )
                )
            } catch (e: IOException) {
                Log.w(TAG, "Error de red con el proveedor neural")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.NO_NETWORK,
                        "No hay conexión con el proveedor neural."
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error inesperado con el proveedor neural")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.UNKNOWN,
                        "Ocurrió un error inesperado con el proveedor neural."
                    )
                )
            }
        }

    private suspend fun playFile(
        file: File,
        onPlaybackStart: () -> Unit
    ): VoicePlaybackResult = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer()
        mediaPlayer = player

        fun cleanup() {
            try {
                player.reset()
                player.release()
            } catch (_: Exception) {
                // Ignorado: liberación best-effort.
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
                        "No se pudo reproducir el audio neural (código $what)."
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
                        "El audio recibido no es válido."
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
            // Ignorado: detención best-effort.
        }
        mediaPlayer = null
    }

    override fun release() {
        stop()
    }

    private sealed interface AudioResult {
        data class Ok(val file: File) : AudioResult
        data class Failure(val error: VoicePlaybackResult.Error) : AudioResult
    }

    companion object {
        private const val TAG = "ElevenLabsVoice"
        private const val BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
