package com.taller.app.voice.neural

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class OpenAiTtsAudioCache(context: Context) {
    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }

    fun entryFor(
        text: String,
        config: OpenAiTtsConfig,
        responseFormat: String = RESPONSE_FORMAT_MP3
    ): CacheEntry {
        val normalizedText = normalizeText(text)
        val keyMaterial = listOf(
            config.model,
            config.voice,
            config.instructions,
            responseFormat,
            normalizedText
        ).joinToString(separator = "\u001F")
        val key = sha256(keyMaterial)
        return CacheEntry(
            key = key,
            shortKey = key.take(SHORT_KEY_LENGTH),
            file = File(cacheDir, "$key.$responseFormat")
        )
    }

    fun stats(): VoiceCacheStats {
        val files = cacheFiles()
        return VoiceCacheStats(
            audioCount = files.size,
            totalBytes = files.sumOf { it.length() },
            lastCacheHit = lastCacheHit
        )
    }

    fun clear(): VoiceCacheStats {
        cacheFiles().forEach { file ->
            runCatching { file.delete() }
        }
        lastCacheHit = null
        return stats()
    }

    private fun cacheFiles(): List<File> =
        cacheDir.listFiles { file -> file.isFile && file.extension.lowercase(Locale.US) == RESPONSE_FORMAT_MP3 }
            ?.toList()
            ?: emptyList()

    data class CacheEntry(
        val key: String,
        val shortKey: String,
        val file: File
    )

    companion object {
        const val RESPONSE_FORMAT_MP3 = "mp3"
        private const val CACHE_DIR_NAME = "seven_openai_tts_cache"
        private const val SHORT_KEY_LENGTH = 12

        @Volatile
        private var lastCacheHit: Boolean? = null

        fun rememberLastCacheHit(cacheHit: Boolean) {
            lastCacheHit = cacheHit
        }

        private fun normalizeText(text: String): String =
            text.trim().replace(Regex("\\s+"), " ")

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        }
    }
}

data class VoiceCacheStats(
    val audioCount: Int,
    val totalBytes: Long,
    val lastCacheHit: Boolean?
)
