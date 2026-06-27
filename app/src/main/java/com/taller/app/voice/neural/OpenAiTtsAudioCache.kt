package com.taller.app.voice.neural

import android.content.Context
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.ToyVoiceTextValidator
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class OpenAiTtsAudioCache(context: Context) {
    private val unifiedCacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
    private val legacyOpenAiCacheDir = File(context.cacheDir, LEGACY_OPENAI_CACHE_DIR_NAME)

    fun entryFor(
        text: String,
        config: OpenAiTtsConfig,
        responseFormat: String = RESPONSE_FORMAT_MP3
    ): CacheEntry = entryFor(
        text = text,
        provider = ToyVoiceProviderType.OPENAI_TTS,
        model = config.model,
        voice = config.voice,
        instructions = config.instructions,
        responseFormat = responseFormat
    )

    fun entryFor(
        text: String,
        config: GeminiTtsConfig,
        responseFormat: String = RESPONSE_FORMAT_WAV
    ): CacheEntry = entryFor(
        text = text,
        provider = ToyVoiceProviderType.GEMINI_TTS,
        model = config.model,
        voice = config.voiceName,
        instructions = config.instructions,
        responseFormat = responseFormat
    )

    fun entryFor(
        text: String,
        provider: ToyVoiceProviderType,
        model: String,
        voice: String,
        instructions: String,
        responseFormat: String
    ): CacheEntry = VoiceCacheKey.entryFor(
        text = text,
        provider = provider,
        model = model,
        voice = voice,
        instructions = instructions,
        responseFormat = responseFormat,
        cacheDir = unifiedCacheDir
    )

    fun stats(): VoiceCacheStats {
        val files = cacheFiles()
        return VoiceCacheStats(
            audioCount = files.size,
            totalBytes = files.sumOf { it.length() },
            lastCacheHit = lastCacheHit,
            lastCacheProvider = lastCacheProvider,
            geminiAudioCount = files.count { it.name.startsWith("${VoiceCacheKey.providerToken(ToyVoiceProviderType.GEMINI_TTS)}-") },
            openAiAudioCount = files.count { it.name.startsWith("${VoiceCacheKey.providerToken(ToyVoiceProviderType.OPENAI_TTS)}-") }
        )
    }

    fun clear(): VoiceCacheStats {
        cacheFiles().forEach { file ->
            runCatching { file.delete() }
            runCatching { metadataFile(file).delete() }
        }
        legacyOpenAiCacheDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase(Locale.US) == RESPONSE_FORMAT_MP3 }
            ?.forEach { file ->
                runCatching { file.delete() }
            }
        if (legacyOpenAiCacheDir.exists()) {
            runCatching { legacyOpenAiCacheDir.delete() }
        }
        lastCacheHit = null
        lastCacheProvider = null
        return stats()
    }

    fun rememberCacheResult(provider: ToyVoiceProviderType, cacheHit: Boolean) {
        rememberLastCacheHit(cacheHit, provider)
    }

    fun writeMetadata(entry: CacheEntry) {
        val file = entry.file
        val metadata = listOf(
            "cacheVersion=${VoiceCacheKey.CACHE_VERSION}",
            "provider=${entry.provider.name}",
            "model=${entry.model}",
            "voice=${entry.voice}",
            "format=${entry.responseFormat}",
            "createdAt=${System.currentTimeMillis()}",
            "textHash=${entry.textHash}",
            "fileSize=${file.length()}"
        ).joinToString(separator = "\n")
        runCatching { metadataFile(file).writeText(metadata) }
    }

    private fun cacheFiles(): List<File> =
        unifiedCacheDir.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.US) in SUPPORTED_FORMATS
        }
            ?.toList()
            ?: emptyList()

    private fun metadataFile(audioFile: File): File =
        File(audioFile.parentFile, "${audioFile.name}.meta")

    data class CacheEntry(
        val key: String,
        val shortKey: String,
        val file: File,
        val provider: ToyVoiceProviderType,
        val model: String,
        val voice: String,
        val instructions: String,
        val responseFormat: String,
        val textHash: String
    )

    companion object {
        const val RESPONSE_FORMAT_MP3 = "mp3"
        const val RESPONSE_FORMAT_WAV = "wav"
        private const val CACHE_DIR_NAME = "seven_voice_cache"
        private const val LEGACY_OPENAI_CACHE_DIR_NAME = "seven_openai_tts_cache"
        private val SUPPORTED_FORMATS = setOf(RESPONSE_FORMAT_MP3, RESPONSE_FORMAT_WAV)

        @Volatile
        private var lastCacheHit: Boolean? = null

        @Volatile
        private var lastCacheProvider: ToyVoiceProviderType? = null

        fun rememberLastCacheHit(cacheHit: Boolean) {
            rememberLastCacheHit(cacheHit, ToyVoiceProviderType.OPENAI_TTS)
        }

        fun rememberLastCacheHit(cacheHit: Boolean, provider: ToyVoiceProviderType) {
            lastCacheHit = cacheHit
            lastCacheProvider = provider
        }
    }
}

data class VoiceCacheStats(
    val audioCount: Int,
    val totalBytes: Long,
    val lastCacheHit: Boolean?,
    val lastCacheProvider: ToyVoiceProviderType? = null,
    val geminiAudioCount: Int = 0,
    val openAiAudioCount: Int = 0
)

object VoiceCacheKey {
    const val CACHE_VERSION = "voice-cache-v2"
    private const val SHORT_KEY_LENGTH = 12
    private const val SEPARATOR = "\u001F"

    fun entryFor(
        text: String,
        provider: ToyVoiceProviderType,
        model: String,
        voice: String,
        instructions: String,
        responseFormat: String,
        cacheDir: File
    ): OpenAiTtsAudioCache.CacheEntry {
        val normalizedText = normalizedValidText(text)
            ?: throw IllegalArgumentException("Texto de voz invalido para cache.")
        val normalizedFormat = responseFormat.trim().lowercase(Locale.US)
        val textHash = sha256(normalizedText)
        val key = keyForValidatedText(
            provider = provider,
            model = model,
            voice = voice,
            instructions = instructions,
            responseFormat = normalizedFormat,
            normalizedText = normalizedText
        )
        return OpenAiTtsAudioCache.CacheEntry(
            key = key,
            shortKey = key.take(SHORT_KEY_LENGTH),
            file = File(cacheDir, "${providerToken(provider)}-$key.$normalizedFormat"),
            provider = provider,
            model = model.trim(),
            voice = voice.trim(),
            instructions = instructions.trim(),
            responseFormat = normalizedFormat,
            textHash = textHash
        )
    }

    fun keyFor(
        text: String?,
        provider: ToyVoiceProviderType,
        model: String,
        voice: String,
        instructions: String,
        responseFormat: String
    ): String? {
        val normalizedText = normalizedValidText(text) ?: return null
        return keyForValidatedText(
            provider = provider,
            model = model,
            voice = voice,
            instructions = instructions,
            responseFormat = responseFormat.trim().lowercase(Locale.US),
            normalizedText = normalizedText
        )
    }

    fun providerToken(provider: ToyVoiceProviderType): String = when (provider) {
        ToyVoiceProviderType.GEMINI_TTS -> "gemini"
        ToyVoiceProviderType.OPENAI_TTS -> "openai"
        else -> provider.name.lowercase(Locale.US)
    }

    private fun keyForValidatedText(
        provider: ToyVoiceProviderType,
        model: String,
        voice: String,
        instructions: String,
        responseFormat: String,
        normalizedText: String
    ): String {
        val keyMaterial = listOf(
            CACHE_VERSION,
            provider.name,
            model.trim(),
            voice.trim(),
            instructions.trim(),
            responseFormat,
            normalizedText
        ).joinToString(separator = SEPARATOR)
        return sha256(keyMaterial)
    }

    private fun normalizedValidText(text: String?): String? {
        val validation = ToyVoiceTextValidator.validate(text)
        if (!validation.isValid) return null
        return validation.normalizedText.trim().replace(Regex("\\s+"), " ")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
