package com.taller.app.bimodal

import com.taller.app.voice.VoicePlaybackDebugInfo
import com.taller.app.voice.VoicePlaybackMode
import com.taller.app.voice.VoicePlaybackSource

/**
 * Diagnostico de una evaluacion local de respuesta del nino, para el resumen
 * tecnico del Modo Inteligente. Solo guarda textos cortos y sanitizados: nunca
 * audio, ni datos biometricos, ni se envia a ningun servicio externo.
 */
data class AnswerEvaluationDebugInfo(
    val questionOrder: Int,
    val questionId: Long?,
    val questionTextShort: String?,
    val expectedAnswerShort: String?,
    val sttFinalTranscriptShort: String?,
    val localEvaluationResult: String,
    val localReason: String?,
    val evaluationLatencyMs: Long?,
    val attemptNumber: Int,
    // MED01: decision hibrida del juez de respuestas abiertas. Campos opcionales
    // para conservar compatibilidad con evaluaciones puramente locales.
    val decisionLayer: String? = null,
    val judgeDecision: String? = null,
    val finalResult: String? = null,
    val acceptedAsEquivalent: Boolean? = null,
    val confidence: Double? = null,
    val judgeReason: String? = null,
    val judgeLatencyMs: Long? = null,
    val usedFallback: Boolean? = null
)

/** Conteos agregados de las voces reproducidas durante una sesion inteligente. */
data class VoiceSessionStats(
    val total: Int,
    val cacheHit: Int,
    val cacheMiss: Int,
    val networkSynthesis: Int,
    val fallback: Int,
    val localTts: Int,
    val errors: Int,
    val cacheOnly: Int,
    val cacheOrSynthesize: Int,
    val gemini: Int,
    val openAi: Int,
    val azure: Int,
    val local: Int,
    val avgPlaybackStartMs: Long?,
    val maxPlaybackStartMs: Long?,
    val avgTotalVoiceMs: Long?,
    val anyNonCacheOnly: Boolean
)

/**
 * Construye los resumenes tecnicos del Modo Inteligente (voz + evaluacion local) a
 * partir de los eventos acumulados en memoria. Es codigo puro para poder probarse.
 */
object IntelligentSessionReport {

    private const val MAX_SHORT_LENGTH = 80

    fun summarizeVoice(events: List<VoicePlaybackDebugInfo>): VoiceSessionStats {
        val real = events.filter { it.source != VoicePlaybackSource.NONE }
        val starts = real.mapNotNull { it.playbackStartMs }
        val totals = real.mapNotNull { it.totalVoiceMs }
        return VoiceSessionStats(
            total = real.size,
            cacheHit = real.count { it.source == VoicePlaybackSource.CACHE_HIT },
            cacheMiss = real.count { it.source == VoicePlaybackSource.CACHE_MISS },
            networkSynthesis = real.count { it.source == VoicePlaybackSource.NETWORK_SYNTHESIS },
            fallback = real.count { it.source == VoicePlaybackSource.FALLBACK_PROVIDER },
            localTts = real.count { it.source == VoicePlaybackSource.LOCAL_TTS },
            errors = real.count { it.source == VoicePlaybackSource.ERROR },
            cacheOnly = real.count { it.playbackMode == VoicePlaybackMode.CACHE_ONLY },
            cacheOrSynthesize = real.count { it.playbackMode == VoicePlaybackMode.CACHE_OR_SYNTHESIZE },
            gemini = real.count { it.provider == "Gemini" },
            openAi = real.count { it.provider == "OpenAI" },
            azure = real.count { it.provider == "Azure" },
            local = real.count { it.provider == "Android local" },
            avgPlaybackStartMs = starts.averageOrNull(),
            maxPlaybackStartMs = starts.maxOrNull(),
            avgTotalVoiceMs = totals.averageOrNull(),
            anyNonCacheOnly = real.any { it.playbackMode != VoicePlaybackMode.CACHE_ONLY }
        )
    }

    /**
     * Reporte tecnico en texto plano y sanitizado, apto para copiar/compartir. No
     * incluye claves, payloads, audio ni textos largos. Las transcripciones y
     * respuestas se recortan.
     */
    fun buildReportText(
        voiceEvents: List<VoicePlaybackDebugInfo>,
        evaluations: List<AnswerEvaluationDebugInfo>,
        maxVoiceRows: Int = 20
    ): String {
        val stats = summarizeVoice(voiceEvents)
        return buildString {
            appendLine("REPORTE TECNICO - MODO INTELIGENTE")
            appendLine()
            appendLine("== Resumen de voz ==")
            appendLine("Total voces: ${stats.total}")
            appendLine("CACHE_HIT: ${stats.cacheHit}")
            appendLine("CACHE_MISS: ${stats.cacheMiss}")
            appendLine("NETWORK_SYNTHESIS: ${stats.networkSynthesis}")
            appendLine("Modo CACHE_ONLY: ${stats.cacheOnly}")
            appendLine("Modo CACHE_OR_SYNTHESIZE: ${stats.cacheOrSynthesize}")
            appendLine("Proveedores -> Gemini: ${stats.gemini}, OpenAI: ${stats.openAi}, Azure: ${stats.azure}, Local: ${stats.local}")
            appendLine("Fallbacks: ${stats.fallback}")
            appendLine("Errores: ${stats.errors}")
            appendLine("Inicio audio prom: ${stats.avgPlaybackStartMs ?: "—"} ms, max: ${stats.maxPlaybackStartMs ?: "—"} ms")
            appendLine("Total voz prom: ${stats.avgTotalVoiceMs ?: "—"} ms")
            appendLine("Alguna voz no cache-only: ${if (stats.anyNonCacheOnly) "Si" else "No"}")
            appendLine()
            appendLine("== Historial de voces (ultimas $maxVoiceRows) ==")
            val rows = voiceEvents.filter { it.source != VoicePlaybackSource.NONE }.takeLast(maxVoiceRows)
            if (rows.isEmpty()) {
                appendLine("(sin voces)")
            } else {
                rows.forEachIndexed { index, e ->
                    appendLine(
                        "${index + 1}. ${e.lineType ?: "UNKNOWN"} | ${e.source.name} | ${e.playbackMode.name} | " +
                            "${e.provider ?: "Ninguno"} | inicio:${e.playbackStartMs ?: "—"}ms | " +
                            "total:${e.totalVoiceMs ?: "—"}ms | hash:${e.textHashShort ?: "—"}" +
                            (e.fallbackReason?.let { " | fallback:$it" } ?: "") +
                            (e.sanitizedError?.let { " | error:$it" } ?: "")
                    )
                }
            }
            appendLine()
            appendLine("== Evaluacion hibrida ==")
            if (evaluations.isEmpty()) {
                appendLine("(sin respuestas evaluadas)")
            } else {
                evaluations.forEach { ev ->
                    appendLine("P${ev.questionOrder} intento ${ev.attemptNumber}")
                    appendLine("- STT: ${ev.sttFinalTranscriptShort ?: "—"}")
                    appendLine("- Ref: ${ev.expectedAnswerShort ?: "—"}")
                    appendLine("- Local: ${ev.localEvaluationResult}")
                    appendLine("- Juez: ${ev.judgeDecision ?: "—"}")
                    appendLine("- Capa final: ${ev.decisionLayer ?: "LOCAL"}")
                    appendLine("- Resultado final: ${ev.finalResult ?: ev.localEvaluationResult}")
                    appendLine("- Equivalente: ${boolLabel(ev.acceptedAsEquivalent)}")
                    appendLine("- Confianza: ${ev.confidence?.let { formatConfidence(it) } ?: "—"}")
                    appendLine("- Latencia local: ${ev.evaluationLatencyMs ?: "—"}ms")
                    appendLine("- Latencia juez: ${ev.judgeLatencyMs?.let { "$it ms" } ?: "—"}")
                    appendLine("- Fallback: ${boolLabel(ev.usedFallback)}")
                    appendLine("- Motivo: ${ev.judgeReason ?: ev.localReason ?: "—"}")
                }
            }
        }.trim()
    }

    /** Recorta y limpia un texto para mostrarlo corto y seguro en el reporte. */
    fun shortSafe(text: String?, maxLength: Int = MAX_SHORT_LENGTH): String? {
        val value = text?.takeIf { it.isNotBlank() } ?: return null
        return value
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("(?i)(api[_-]?key|bearer|token|secret)[^\\s]*"), "credential_redacted")
            .trim()
            .take(maxLength)
    }

    private fun boolLabel(value: Boolean?): String = when (value) {
        true -> "Si"
        false -> "No"
        null -> "—"
    }

    /** Confianza con dos decimales y sin depender de la configuracion regional. */
    private fun formatConfidence(value: Double): String {
        val clamped = value.coerceIn(0.0, 1.0)
        val rounded = Math.round(clamped * 100.0)
        return "0.${rounded.toString().padStart(2, '0')}".let {
            if (rounded >= 100L) "1.00" else it
        }
    }

    private fun List<Long>.averageOrNull(): Long? =
        if (isEmpty()) null else (sum() / size)
}
