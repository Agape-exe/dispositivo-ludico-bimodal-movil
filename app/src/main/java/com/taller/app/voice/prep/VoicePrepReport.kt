package com.taller.app.voice.prep

import com.taller.app.voice.ToyVoiceProviderType

/**
 * Estado recomendado de la voz preparada de una sesion, segun el proveedor con que
 * quedaron los audios. La voz objetivo para sesion real es Gemini/Puck.
 */
enum class VoicePrepRecommendedStatus {
    /** Todo listo y con el proveedor objetivo (Gemini/Puck): apta para sesion real. */
    READY_IDEAL,

    /** Todo cacheado, pero algun audio quedo con proveedor de respaldo: revisar. */
    REVIEW_FALLBACK,

    /** Faltan audios por preparar: incompleta. */
    INCOMPLETE;

    /** Estado persistido equivalente para Room/gate (READY solo si es ideal). */
    fun toPersistedStatus(hasAnyReady: Boolean): VoicePrepStatus = when (this) {
        READY_IDEAL -> VoicePrepStatus.READY
        REVIEW_FALLBACK -> VoicePrepStatus.PARTIAL
        INCOMPLETE -> if (hasAnyReady) VoicePrepStatus.PARTIAL else VoicePrepStatus.FAILED
    }
}

/** Detalle de una linea preparada, para el reporte por audio. */
data class VoicePrepReportItem(
    val lineType: String,
    val status: String,
    val provider: String?,
    val model: String?,
    val voice: String?,
    val isTargetProvider: Boolean,
    val textHashShort: String,
    val cacheKeyShort: String?,
    val error: String?
)

/** Reporte completo de la preparacion de voz de una sesion. */
data class VoicePrepReport(
    val total: Int,
    val ready: Int,
    val targetProviderReady: Int,
    val fallbackProviderReady: Int,
    val failed: Int,
    val providerMismatch: Int,
    val items: List<VoicePrepReportItem>,
    val recommendedStatus: VoicePrepRecommendedStatus
) {
    companion object {
        /** Proveedor objetivo de la voz de Seven para sesiones reales. */
        val TARGET_PROVIDER: ToyVoiceProviderType = ToyVoiceProviderType.GEMINI_TTS

        const val STATUS_CACHED = "CACHED"
        const val STATUS_CREATED = "CREATED"
        const val STATUS_PROVIDER_MISMATCH = "PROVIDER_MISMATCH"
        const val STATUS_FAILED = "FAILED"

        fun providerLabel(provider: ToyVoiceProviderType?): String = when (provider) {
            ToyVoiceProviderType.GEMINI_TTS -> "Gemini"
            ToyVoiceProviderType.OPENAI_TTS -> "OpenAI"
            ToyVoiceProviderType.AZURE_NEURAL -> "Azure"
            ToyVoiceProviderType.LOCAL -> "Android local"
            ToyVoiceProviderType.ELEVENLABS -> "ElevenLabs"
            null -> "Ninguno"
        }

        fun isTargetProvider(provider: ToyVoiceProviderType?): Boolean = provider == TARGET_PROVIDER
    }
}
