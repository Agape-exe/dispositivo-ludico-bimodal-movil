package com.taller.app.voice.neural

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
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
                "eventType=TTS_SKIPPED_INVALID_TEXT providerRequested=OPENAI_TTS providerUsed=NONE " +
                    "textLength=${text.length} reason=${validation.reason} timestamp=${System.currentTimeMillis()}"
            )
            return VoicePlaybackResult.Error(
                VoiceErrorType.INVALID_TTS_TEXT,
                VoiceOutcome.SAFE_INVALID_TEXT_MESSAGE
            )
        }
        val config = configProvider()
        val startedAt = System.currentTimeMillis()
        val safeText = validation.normalizedText
        val cacheEntry = audioCache.entryFor(safeText, config, RESPONSE_FORMAT)
        val audio = when (val result = getOrCreateAudio(safeText, config, cacheEntry)) {
            is AudioResult.Failure -> return result.error
            is AudioResult.Ok -> result
        }

        val playbackStartedAt = System.currentTimeMillis()
        val playback = playFile(audio.file, onPlaybackStart)
        val playbackLatencyMs = System.currentTimeMillis() - playbackStartedAt
        val totalLatencyMs = System.currentTimeMillis() - startedAt

        if (playback is VoicePlaybackResult.Success) {
            audioCache.rememberCacheResult(ToyVoiceProviderType.OPENAI_TTS, audio.cacheHit)
            Log.d(
                TAG,
                "reproduccion: cacheHit=${audio.cacheHit} cacheKey=${audio.cacheShortKey} " +
                    "synthesisLatencyMs=${audio.synthesisLatencyMs} playbackLatencyMs=$playbackLatencyMs " +
                    "totalLatencyMs=$totalLatencyMs"
            )
            return playback.copy(
                cacheHit = audio.cacheHit,
                cacheKey = audio.cacheShortKey,
                synthesisLatencyMs = audio.synthesisLatencyMs,
                playbackLatencyMs = playbackLatencyMs,
                totalLatencyMs = totalLatencyMs
            )
        }

        if (audio.cacheHit) {
            runCatching { audio.file.delete() }
            Log.w(TAG, "Cache corrupta descartada: cacheKey=${audio.cacheShortKey}")
            return speakWithoutCachedFile(safeText, config, cacheEntry, onPlaybackStart, startedAt)
        }

        return playback
    }

    private suspend fun getOrCreateAudio(
        text: String,
        config: OpenAiTtsConfig,
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
        config: OpenAiTtsConfig,
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
            audioCache.rememberCacheResult(ToyVoiceProviderType.OPENAI_TTS, false)
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
        config: OpenAiTtsConfig,
        cacheEntry: OpenAiTtsAudioCache.CacheEntry
    ): AudioResult =
        withContext(Dispatchers.IO) {
            if (!config.hasApiKey) {
                return@withContext AudioResult.Failure(
                    VoicePlaybackResult.Error(
                        VoiceErrorType.NOT_CONFIGURED,
                        "OpenAI TTS no esta configurado."
                    )
                )
            }
            val startedAt = System.currentTimeMillis()
            val body = JSONObject()
                .put("model", config.model)
                .put("voice", config.voice)
                .put("input", text)
                .put("instructions", config.instructions)
                .put("response_format", RESPONSE_FORMAT)
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
                    val tempFile = File(cacheEntry.file.parentFile, "${cacheEntry.file.name}.tmp")
                    tempFile.writeBytes(bytes)
                    if (cacheEntry.file.exists()) {
                        runCatching { cacheEntry.file.delete() }
                    }
                    if (!tempFile.renameTo(cacheEntry.file)) {
                        tempFile.copyTo(cacheEntry.file, overwrite = true)
                        tempFile.delete()
                    }
                    audioCache.writeMetadata(cacheEntry)
                    AudioResult.Ok(
                        file = cacheEntry.file,
                        cacheHit = false,
                        cacheShortKey = cacheEntry.shortKey,
                        synthesisLatencyMs = System.currentTimeMillis() - startedAt
                    )
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
            if (continuation.isActive) continuation.resume(VoicePlaybackResult.Success())
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
        data class Ok(
            val file: File,
            val cacheHit: Boolean,
            val cacheShortKey: String,
            val synthesisLatencyMs: Long
        ) : AudioResult
        data class Failure(val error: VoicePlaybackResult.Error) : AudioResult
    }

    companion object {
        private const val TAG = "OpenAiTtsVoice"
        private const val SPEECH_URL = "https://api.openai.com/v1/audio/speech"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val RESPONSE_FORMAT = OpenAiTtsAudioCache.RESPONSE_FORMAT_MP3
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
