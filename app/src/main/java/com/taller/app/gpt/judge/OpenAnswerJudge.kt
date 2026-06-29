package com.taller.app.gpt.judge

import android.util.Log
import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptResult

/**
 * MED01: orquesta la consulta al juez de respuestas abiertas. "El modelo propone,
 * el orquestador dispone".
 *
 * Pide el veredicto al modelo de texto (GPT-mini por configuracion), lo valida y
 * sanea localmente y, si algo falla (sin clave, sin red, JSON invalido, etc.),
 * entrega un respaldo claro para que el flujo nunca se bloquee. Nunca registra el
 * prompt completo, payloads crudos ni credenciales: solo metadatos seguros.
 */
class OpenAnswerJudge(
    private val gptClient: GptClient,
    private val additionalRulesProvider: () -> String = { "" },
    private val logSink: (String) -> Unit = { Log.d(TAG, it) }
) {

    sealed interface Outcome {
        /** Latencia aproximada de la consulta en milisegundos. */
        val latencyMs: Long

        /** Veredicto emitido por el modelo y validado localmente. */
        data class FromModel(
            val verdict: OpenAnswerJudgeVerdict,
            val modelUsed: String,
            override val latencyMs: Long
        ) : Outcome

        /**
         * El juez no pudo emitir un veredicto utilizable; el flujo debe usar su
         * respaldo local seguro. [reason] es un codigo corto para diagnostico.
         */
        data class Unavailable(
            val reason: String,
            override val latencyMs: Long
        ) : Outcome
    }

    fun isConfigured(): Boolean = runCatching { gptClient.isEnabled() && gptClient.isConfigured() }
        .getOrDefault(false)

    suspend fun judge(input: OpenAnswerJudgeInput): Outcome {
        val start = System.nanoTime()
        val prompt = OpenAnswerJudgePrompt.build(
            input = input,
            additionalRules = additionalRulesProvider()
        )
        val result = runCatching { gptClient.generate(prompt) }.getOrElse {
            log("layer=FALLBACK_LOCAL reason=EXCEPTION")
            return Outcome.Unavailable("EXCEPTION", elapsedMs(start))
        }

        return when (result) {
            is GptResult.Success -> {
                val verdict = runCatching { OpenAnswerJudgeParser.parse(result.text, input) }.getOrNull()
                if (verdict == null) {
                    log("layer=FALLBACK_LOCAL reason=PARSE_ERROR model=${result.modelUsed}")
                    Outcome.Unavailable("PARSE_ERROR", elapsedMs(start))
                } else {
                    log(
                        "layer=JUDGE decision=${verdict.decision} confidence=${verdict.confidence} " +
                            "equivalent=${verdict.acceptedAsEquivalent} model=${result.modelUsed} " +
                            "latencyMs=${result.latencyMs}"
                    )
                    Outcome.FromModel(verdict, result.modelUsed, result.latencyMs)
                }
            }

            is GptResult.Disabled -> {
                log("layer=FALLBACK_LOCAL reason=DISABLED")
                Outcome.Unavailable("DISABLED", elapsedMs(start))
            }

            is GptResult.Failure -> {
                log("layer=FALLBACK_LOCAL reason=${result.errorType.safeCode()}")
                Outcome.Unavailable(result.errorType.safeCode(), elapsedMs(start))
            }
        }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun GptErrorType.safeCode(): String = name

    private fun log(message: String) {
        runCatching { logSink("eventType=OPEN_ANSWER_JUDGE $message") }
    }

    companion object {
        private const val TAG = "OpenAnswerJudge"
    }
}
