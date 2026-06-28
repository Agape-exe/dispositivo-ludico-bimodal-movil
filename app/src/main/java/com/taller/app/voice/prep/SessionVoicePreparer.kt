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
    val lastError: String?
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
        val uniqueTexts = uniqueTexts(lines)
        val total = uniqueTexts.size
        if (total == 0) {
            return VoicePrepOutcome(
                status = VoicePrepStatus.FAILED,
                totalCount = 0,
                readyCount = 0,
                failedCount = 0,
                providerUsed = null,
                lastError = "No hay frases para preparar en esta sesion."
            )
        }

        var ready = 0
        var failed = 0
        var providerUsed: ToyVoiceProviderType? = null
        var lastError: String? = null

        onProgress(VoicePrepProgress(total, ready, failed))
        for (text in uniqueTexts) {
            when (val result = synthesizer.prepare(text)) {
                is VoiceLinePrepResult.Prepared -> {
                    ready++
                    if (providerUsed == null) providerUsed = result.provider
                }
                is VoiceLinePrepResult.Failed -> {
                    failed++
                    lastError = result.safeMessage
                }
            }
            onProgress(VoicePrepProgress(total, ready, failed))
        }

        val status = when {
            ready == total -> VoicePrepStatus.READY
            ready == 0 -> VoicePrepStatus.FAILED
            else -> VoicePrepStatus.PARTIAL
        }
        return VoicePrepOutcome(
            status = status,
            totalCount = total,
            readyCount = ready,
            failedCount = failed,
            providerUsed = providerUsed,
            lastError = lastError
        )
    }

    /** Textos unicos por clave normalizada, conservando el orden de aparicion. */
    private fun uniqueTexts(lines: List<VoiceLine>): List<String> {
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (line in lines) {
            val key = SessionVoiceLines.dedupeKey(line.text) ?: continue
            if (seen.add(key)) result.add(line.text)
        }
        return result
    }
}
