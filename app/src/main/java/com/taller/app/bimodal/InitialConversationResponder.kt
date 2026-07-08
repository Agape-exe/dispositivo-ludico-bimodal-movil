package com.taller.app.bimodal

import android.util.Log
import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.conversation.InitialConversationInput
import com.taller.app.gpt.conversation.InitialConversationParser
import com.taller.app.gpt.conversation.InitialConversationPrompt
import kotlinx.coroutines.withTimeoutOrNull

/** Origen de la respuesta de la conversacion inicial (para metricas seguras). */
enum class InitialConversationSource { LOCAL, GPT, FALLBACK_LOCAL }

/**
 * Resultado de responder un turno de la conversacion inicial.
 *
 * @property text lo que Seven dice en voz alta.
 * @property source de donde salio la respuesta.
 * @property timedOut si el juez conversacional no respondio a tiempo.
 */
data class InitialConversationReply(
    val text: String,
    val source: InitialConversationSource,
    val timedOut: Boolean = false
)

/**
 * FINAL-FLOW01-FIX01: decide la respuesta de la conversacion inicial en tres
 * niveles, priorizando siempre lo local y seguro:
 *
 *  1. banco local ([InitialConversationBank.localAnswer]) para preguntas comunes
 *     (como se llama, quien es, que van a hacer);
 *  2. si no hay coincidencia local y el juez conversacional (GPT) esta configurado,
 *     una consulta limitada y con tope de tiempo, respondiendo como Seven y
 *     redirigiendo lo que quede fuera de alcance;
 *  3. respaldo local seguro si el juez no esta disponible, falla o tarda demasiado.
 *
 * Nunca bloquea la sesion: ante cualquier problema devuelve una frase local segura.
 * No registra el prompt completo, la pregunta del nino ni la respuesta cruda: la
 * capa que lo usa solo guarda metadatos (turno y origen).
 */
class InitialConversationResponder(
    private val gptClient: GptClient,
    private val gptTimeoutMs: Long = DEFAULT_GPT_TIMEOUT_MS,
    private val logSink: (String) -> Unit = { Log.d(TAG, it) }
) {

    /** Responde el turno actual. [remainingTurns] incluye el turno en curso. */
    suspend fun respond(
        childQuestion: String,
        sessionTopic: String,
        sessionName: String?,
        remainingTurns: Int
    ): InitialConversationReply {
        // Nivel 1: respuesta local prioritaria.
        InitialConversationBank.localAnswer(childQuestion, sessionTopic)?.let { local ->
            log("source=LOCAL")
            return InitialConversationReply(local, InitialConversationSource.LOCAL)
        }

        // Nivel 2: juez conversacional limitado, solo si esta configurado.
        val gptEnabled = runCatching { gptClient.isEnabled() && gptClient.isConfigured() }
            .getOrDefault(false)
        if (!gptEnabled) {
            log("source=FALLBACK_LOCAL reason=NOT_CONFIGURED")
            return InitialConversationReply(
                InitialConversationBank.SAFE_FALLBACK_ANSWER,
                InitialConversationSource.FALLBACK_LOCAL
            )
        }

        val prompt = InitialConversationPrompt.build(
            InitialConversationInput(
                childQuestion = childQuestion,
                sessionTopic = sessionTopic,
                sessionName = sessionName,
                remainingTurns = remainingTurns
            )
        )

        val result = withTimeoutOrNull(gptTimeoutMs) {
            runCatching { gptClient.generate(prompt) }.getOrNull()
        }
        if (result == null) {
            // Timeout o excepcion: respaldo local, nunca se bloquea la sesion.
            log("source=FALLBACK_LOCAL reason=TIMEOUT_OR_ERROR")
            return InitialConversationReply(
                InitialConversationBank.SAFE_FALLBACK_ANSWER,
                InitialConversationSource.FALLBACK_LOCAL,
                timedOut = true
            )
        }

        return when (result) {
            is GptResult.Success -> {
                val verdict = runCatching { InitialConversationParser.parse(result.text) }.getOrNull()
                when {
                    verdict == null -> {
                        log("source=FALLBACK_LOCAL reason=PARSE_ERROR")
                        InitialConversationReply(
                            InitialConversationBank.SAFE_FALLBACK_ANSWER,
                            InitialConversationSource.FALLBACK_LOCAL
                        )
                    }
                    verdict.allowed -> {
                        log("source=GPT reason=${verdict.reason}")
                        InitialConversationReply(verdict.answer, InitialConversationSource.GPT)
                    }
                    else -> {
                        // El juez decidio redirigir: frase local de redireccion suave.
                        log("source=GPT reason=${verdict.reason} redirected=true")
                        InitialConversationReply(
                            InitialConversationBank.REDIRECT_ANSWER,
                            InitialConversationSource.GPT
                        )
                    }
                }
            }

            is GptResult.Disabled -> {
                log("source=FALLBACK_LOCAL reason=DISABLED")
                InitialConversationReply(
                    InitialConversationBank.SAFE_FALLBACK_ANSWER,
                    InitialConversationSource.FALLBACK_LOCAL
                )
            }

            is GptResult.Failure -> {
                log("source=FALLBACK_LOCAL reason=${result.errorType.name}")
                InitialConversationReply(
                    InitialConversationBank.SAFE_FALLBACK_ANSWER,
                    InitialConversationSource.FALLBACK_LOCAL
                )
            }
        }
    }

    private fun log(message: String) {
        runCatching { logSink("eventType=INITIAL_CONVERSATION $message") }
    }

    companion object {
        private const val TAG = "InitialConversation"

        /** Tope de tiempo del juez conversacional (dentro del rango sugerido 5-8 s). */
        const val DEFAULT_GPT_TIMEOUT_MS = 6_000L
    }
}
