package com.taller.app.voice

import java.security.MessageDigest

enum class VoiceMetricEventType {
    VOICE_PLAYBACK_REQUESTED,
    VOICE_PLAYBACK_STARTED,
    VOICE_PLAYBACK_COMPLETED,
    VOICE_PLAYBACK_FAILED,
    VOICE_FALLBACK_USED,
    VOICE_CACHE_HIT,
    VOICE_CACHE_MISS,
    VOICE_SKIPPED_INVALID_TEXT
}

enum class VoiceMode {
    CONFIGURAR,
    INTELLIGENT,
    TIMER,
    UNKNOWN
}

enum class VoiceContext {
    TEST,
    GREETING,
    QUESTION,
    FEEDBACK_CORRECT,
    FEEDBACK_INCORRECT,
    FEEDBACK_RETRY,
    NOT_INTERPRETABLE,
    RECAPTURE,
    CLOSING,
    COUNTDOWN,
    UNKNOWN
}

data class VoiceProviderInfo(
    val model: String? = null,
    val voice: String? = null
)

data class VoicePlaybackMetric(
    val eventType: VoiceMetricEventType,
    val timestamp: Long,
    val mode: VoiceMode,
    val voiceContext: VoiceContext,
    val providerRequested: ToyVoiceProviderType,
    val providerUsed: ToyVoiceProviderType?,
    val fallbackUsed: Boolean,
    val fallbackFrom: ToyVoiceProviderType? = null,
    val fallbackTo: ToyVoiceProviderType? = null,
    val model: String? = null,
    val voice: String? = null,
    val cacheHit: Boolean? = null,
    val cacheProvider: ToyVoiceProviderType? = null,
    val cacheKey: String? = null,
    val textLength: Int = 0,
    val textHash: String? = null,
    val cacheLookupLatencyMs: Long? = null,
    val synthesisLatencyMs: Long? = null,
    val playbackStartLatencyMs: Long? = null,
    val playbackDurationMs: Long? = null,
    val totalVoiceLatencyMs: Long? = null,
    val errorType: VoiceErrorType? = null,
    val safeErrorMessage: String? = null,
    val skippedInvalidText: Boolean = false
) {
    fun toTechnicalMessage(): String = listOfNotNull(
        field("voiceProviderRequested", providerRequested.name),
        field("voiceProviderUsed", providerUsed?.name ?: "NONE"),
        field("voiceFallbackUsed", fallbackUsed),
        field("voiceFallbackFrom", fallbackFrom?.name),
        field("voiceFallbackTo", fallbackTo?.name),
        field("voiceProviderActive", providerUsed?.name ?: providerRequested.name),
        field("voiceModel", model),
        field("voiceName", voice),
        field("voiceCacheHit", cacheHit),
        field("voiceCacheProvider", cacheProvider?.name),
        field("voiceCacheKey", cacheKey),
        field("voiceTextLength", textLength),
        field("voiceTextHash", textHash),
        field("voiceCacheLookupLatencyMs", cacheLookupLatencyMs),
        field("voiceSynthesisLatencyMs", synthesisLatencyMs),
        field("voicePlaybackStartLatencyMs", playbackStartLatencyMs),
        field("voicePlaybackDurationMs", playbackDurationMs),
        field("voiceTotalLatencyMs", totalVoiceLatencyMs),
        field("voiceErrorType", errorType?.name),
        field("voiceSafeErrorMessage", safeErrorMessage?.sanitizeMetricValue()),
        field("voiceContext", voiceContext.name),
        field("voiceSkippedInvalidText", skippedInvalidText)
    ).joinToString(" ")

    private fun field(name: String, value: Any?): String? =
        value?.let { "$name=${it.toString().sanitizeMetricValue()}" }
}

object VoicePlaybackMetricParser {
    private val voiceKeys = listOf(
        "voiceProviderRequested",
        "voiceProviderUsed",
        "voiceFallbackUsed",
        "voiceModel",
        "voiceName",
        "voiceCacheHit",
        "voiceSynthesisLatencyMs",
        "voicePlaybackDurationMs",
        "voiceTotalLatencyMs",
        "voiceErrorType",
        "voiceContext"
    )

    fun parse(message: String?): Map<String, String?> {
        if (message.isNullOrBlank()) return emptyMap()
        val raw = message.split(' ')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
            }
            .toMap()
        return voiceKeys.associateWith { raw[it] }
    }
}

fun textHashForVoiceMetric(text: String?): String? {
    val value = text?.takeIf { it.isNotBlank() } ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.take(6).joinToString("") { "%02x".format(it) }
}

private fun String.sanitizeMetricValue(): String =
    replace(Regex("[\\r\\n\\t ]+"), "_")
        .replace(Regex("(?i)(api[_-]?key|bearer|token|secret)[^_ ]*"), "credential_redacted")
        .take(160)
