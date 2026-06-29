package com.taller.app.voice.prep

import com.taller.app.voice.ToyVoiceProviderType

/** Avance de la preparacion de voz, apto para mostrar en la UI. */
data class VoicePrepProgress(
    val total: Int,
    val ready: Int,
    val failed: Int
) {
    val pending: Int get() = (total - ready - failed).coerceAtLeast(0)
    val isDone: Boolean get() = ready + failed >= total
}

/** Resultado final de preparar la voz de una sesion. */
data class VoicePrepOutcome(
    val status: VoicePrepStatus,
    val totalCount: Int,
    val readyCount: Int,
    val failedCount: Int,
    val providerUsed: ToyVoiceProviderType?,
    val lastError: String?,
    val report: VoicePrepReport? = null
) {
    val missingCount: Int get() = (totalCount - readyCount).coerceAtLeast(0)
}

/**
 * Prepara la voz de una sesion a partir de una lista finita de [VoiceLine].
 *
 * - Deduplica por texto normalizado (la cache trata igual textos identicos): cada
 *   frase se sintetiza una sola vez.
 * - Reutiliza la cache: si la linea ya existe no hay llamada de red (lo decide el
 *   [VoiceLineSynthesizer]).
 * - Reporta avance (total, listas, pendientes, fallidas) tras cada linea.
 * - Calcula el estado final: READY si todas estan listas, PARTIAL si faltan
 *   algunas, FAILED si no se logro ninguna.
 *
 * No depende de Android: el trabajo de red/cache lo hace el [VoiceLineSynthesizer]
 * inyectado, lo que permite probar la logica de forma aislada.
 */
class SessionVoicePreparer(
    private val synthesizer: VoiceLineSynthesizer
) {

    suspend fun prepare(
        lines: List<VoiceLine>,
        onProgress: (VoicePrepProgress) -> Unit = {}
    ): VoicePrepOutcome {
        val uniqueLines = uniqueLines(lines)
        val total = uniqueLines.size
        if (total == 0) {
            return VoicePrepOutcome(
                status = VoicePrepStatus.FAILED,
                totalCount = 0,
                readyCount = 0,
                failedCount = 0,
                providerUsed = null,
                lastError = "No hay frases para preparar en esta sesion.",
                report = VoicePrepReport(
                    total = 0,
                    ready = 0,
                    targetProviderReady = 0,
                    fallbackProviderReady = 0,
                    failed = 0,
                    providerMismatch = 0,
                    items = emptyList(),
                    recommendedStatus = VoicePrepRecommendedStatus.INCOMPLETE
                )
            )
        }

        var ready = 0
        var failed = 0
        var targetReady = 0
        var fallbackReady = 0
        var providerUsed: ToyVoiceProviderType? = null
        var lastError: String? = null
        val items = ArrayList<VoicePrepReportItem>(total)

        onProgress(VoicePrepProgress(total, ready, failed))
        for (line in uniqueLines) {
            when (val result = synthesizer.prepare(line.text)) {
                is VoiceLinePrepResult.Prepared -> {
                    ready++
                    if (providerUsed == null) providerUsed = result.provider
                    val target = VoicePrepReport.isTargetProvider(result.provider)
                    if (target) targetReady++ else fallbackReady++
                    items.add(
                        VoicePrepReportItem(
                            lineType = line.role.name,
                            status = when {
                                !target -> VoicePrepReport.STATUS_PROVIDER_MISMATCH
                                result.fromCache -> VoicePrepReport.STATUS_CACHED
                                else -> VoicePrepReport.STATUS_CREATED
                            },
                            provider = VoicePrepReport.providerLabel(result.provider),
                            model = result.model,
                            voice = result.voice,
                            isTargetProvider = target,
                            textHashShort = shortHash(line.text),
                            cacheKeyShort = result.cacheKeyShort,
                            error = null
                        )
                    )
                }
                is VoiceLinePrepResult.Failed -> {
                    failed++
                    lastError = result.safeMessage
                    items.add(
                        VoicePrepReportItem(
                            lineType = line.role.name,
                            status = VoicePrepReport.STATUS_FAILED,
                            provider = null,
                            model = null,
                            voice = null,
                            isTargetProvider = false,
                            textHashShort = shortHash(line.text),
                            cacheKeyShort = null,
                            error = result.safeMessage
                        )
                    )
                }
            }
            onProgress(VoicePrepProgress(total, ready, failed))
        }

        val recommended = when {
            failed > 0 || ready < total -> VoicePrepRecommendedStatus.INCOMPLETE
            fallbackReady > 0 -> VoicePrepRecommendedStatus.REVIEW_FALLBACK
            else -> VoicePrepRecommendedStatus.READY_IDEAL
        }
        val report = VoicePrepReport(
            total = total,
            ready = ready,
            targetProviderReady = targetReady,
            fallbackProviderReady = fallbackReady,
            failed = failed,
            providerMismatch = fallbackReady,
            items = items,
            recommendedStatus = recommended
        )
        return VoicePrepOutcome(
            status = recommended.toPersistedStatus(hasAnyReady = ready > 0),
            totalCount = total,
            readyCount = ready,
            failedCount = failed,
            providerUsed = providerUsed,
            lastError = lastError,
            report = report
        )
    }

    /** Lineas unicas por clave normalizada, conservando el orden y el primer rol. */
    private fun uniqueLines(lines: List<VoiceLine>): List<VoiceLine> {
        val seen = HashSet<String>()
        val result = ArrayList<VoiceLine>()
        for (line in lines) {
            val key = SessionVoiceLines.dedupeKey(line.text) ?: continue
            if (seen.add(key)) result.add(line)
        }
        return result
    }

    private fun shortHash(text: String): String =
        com.taller.app.voice.textHashForVoiceMetric(text) ?: "--"
}
