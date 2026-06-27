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

class AzureSpeechVoiceProvider(
    private val context: Context,
    private val configProvider: () -> AzureSpeechConfig
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
                "eventType=TTS_SKIPPED_INVALID_TEXT providerRequested=AZURE_NEURAL providerUsed=NONE " +
                    "textLength=${text.length} reason=${validation.reason} timestamp=${System.currentTimeMillis()}"
            )
            return VoicePlaybackResult.Error(
                VoiceErrorType.INVALID_TTS_TEXT,
                VoiceOutcome.SAFE_INVALID_TEXT_MESSAGE
            )
        }
        val config = configProvider()
        if (!config.hasKey) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.NOT_CONFIGURED,
                "Falta la clave de Azure Speech. Configúrala en local.properties (AZURE_SPEECH_KEY) y recompila."
            )
        }
        if (!config.hasRegion) {
            return VoicePlaybackResult.Error(
                VoiceErrorType.NOT_CONFIGURED,
                "Falta la región de Azure Speech. Configúrala en local.properties (AZURE_SPEECH_REGION) y recompila."
            )
        }

        val startedAt = System.currentTimeMillis()
        val audioFile = when (val download = requestAudio(validation.normalizedText, config)) {
            is AudioResult.Failure -> return download.error
            is AudioResult.Ok -> download.file
        }

        return try {
            val playbackStartedAt = System.currentTimeMillis()
            val playback = playFile(audioFile, onPlaybackStart)
            val playbackLatencyMs = System.currentTimeMillis() - playbackStartedAt
            if (playback is VoicePlaybackResult.Success) {
                playback.copy(
                    cacheHit = false,
                    synthesisLatencyMs = playbackStartedAt - startedAt,
                    playbackLatencyMs = playbackLatencyMs,
                    totalLatencyMs = System.currentTimeMillis() - startedAt
                )
            } else {
                playback
            }
        } finally {
            audioFile.delete()
        }
    }

    private suspend fun requestAudio(text: String, config: AzureSpeechConfig): AudioResult =
        withContext(Dispatchers.IO) {
            val url = "https://${config.region}.tts.speech.microsoft.com/cognitiveservices/v1"
            val ssml = buildSsml(text, config.voiceName)
            val body = ssml.toRequestBody(SSML_MEDIA_TYPE)

            val request = Request.Builder()
                .url(url)
                .addHeader("Ocp-Apim-Subscription-Key", config.subscriptionKey)
                .addHeader("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext AudioResult.Failure(
                            VoicePlaybackResult.Error(
                                VoiceErrorType.HTTP_ERROR,
                                "Azure Speech respondió con error (código ${response.code})."
                            )
                        )
                    }
                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        return@withContext AudioResult.Failure(
                            VoicePlaybackResult.Error(
                                VoiceErrorType.INVALID_AUDIO,
                                "Azure Speech devolvió audio vacío."
                            )
                        )
                    }
                    val file = File.createTempFile("toy_azure_", ".mp3", context.cacheDir)
                    file.writeBytes(bytes)
                    AudioResult.Ok(file)
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout al contactar Azure Speech")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(VoiceErrorType.TIMEOUT, "Azure Speech tardó demasiado en responder.")
                )
            } catch (e: IOException) {
                Log.w(TAG, "Error de red con Azure Speech")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(VoiceErrorType.NO_NETWORK, "No hay conexión con Azure Speech.")
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error inesperado con Azure Speech")
                AudioResult.Failure(
                    VoicePlaybackResult.Error(VoiceErrorType.UNKNOWN, "Ocurrió un error inesperado con Azure Speech.")
                )
            }
        }

    private fun buildSsml(text: String, voiceName: String): String {
        val safe = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        return """<speak version="1.0" xml:lang="es-PE"><voice name="$voiceName">$safe</voice></speak>"""
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
            } catch (_: Exception) {}
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
                        "No se pudo reproducir el audio de Azure (código $what)."
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
                        "El audio recibido de Azure no es válido."
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
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun release() = stop()

    private sealed interface AudioResult {
        data class Ok(val file: File) : AudioResult
        data class Failure(val error: VoicePlaybackResult.Error) : AudioResult
    }

    companion object {
        private const val TAG = "AzureSpeechVoice"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private val SSML_MEDIA_TYPE = "application/ssml+xml; charset=utf-8".toMediaType()
    }
}
