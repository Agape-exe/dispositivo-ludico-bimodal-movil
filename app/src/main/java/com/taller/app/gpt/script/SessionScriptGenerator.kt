package com.taller.app.gpt.script

import android.util.Log
import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptResult

/**
 * GEN01: orquesta la generacion del guion. "El modelo propone, el orquestador dispone".
 *
 * Pide el guion al modelo de texto existente, lo valida localmente y, si algo falla
 * (sin clave, sin red, respuesta invalida, etc.), entrega un guion local basico para
 * que la docente nunca quede bloqueada. Nunca registra el prompt ni credenciales.
 */
class SessionScriptGenerator(
    private val gptClient: GptClient,
    private val logSink: (String) -> Unit = { Log.d(TAG, it) }
) {

    sealed interface Outcome {
        val script: SessionScript

        /** Guion propuesto por el modelo y validado localmente. */
        data class FromModel(override val script: SessionScript) : Outcome

        /** Guion local de respaldo; [message] es un texto claro y no tecnico para la docente. */
        data class FromLocal(override val script: SessionScript, val message: String) : Outcome
    }

    suspend fun generate(input: SessionScriptInput): Outcome {
        val localScript = SessionScriptLocalFallback.build(input)

        if (input.questions.isEmpty()) {
            return Outcome.FromLocal(localScript, "Agrega al menos una pregunta para preparar el guion.")
        }

        val prompt = SessionScriptPrompt.build(input)
        val result = gptClient.generate(prompt)

        return when (result) {
            is GptResult.Success -> {
                val parsed = runCatching { SessionScriptParser.parse(result.text, input) }.getOrNull()
                if (parsed == null) {
                    log("source=LOCAL reason=PARSE_ERROR questions=${input.questions.size}")
                    return Outcome.FromLocal(
                        localScript,
                        "El guion recibido no se pudo interpretar. Se preparó un guion básico que puedes editar."
                    )
                }
                val validation = SessionScriptValidator.validate(parsed, input)
                if (!validation.isValid) {
                    log("source=LOCAL reason=VALIDATION_FAILED issues=${validation.issues.size} questions=${input.questions.size}")
                    Outcome.FromLocal(
                        localScript,
                        "El guion recibido no pasó la revisión local. Se preparó un guion básico que puedes editar."
                    )
                } else {
                    log("source=MODEL questions=${input.questions.size} model=${result.modelUsed}")
                    Outcome.FromModel(parsed)
                }
            }

            is GptResult.Disabled -> {
                log("source=LOCAL reason=DISABLED questions=${input.questions.size}")
                Outcome.FromLocal(
                    localScript,
                    "El asistente está desactivado. Se preparó un guion básico que puedes editar."
                )
            }

            is GptResult.Failure -> {
                log("source=LOCAL reason=${result.errorType.name} questions=${input.questions.size}")
                Outcome.FromLocal(localScript, friendlyMessageFor(result.errorType))
            }
        }
    }

    private fun friendlyMessageFor(errorType: GptErrorType): String = when (errorType) {
        GptErrorType.NOT_CONFIGURED ->
            "El servicio no está configurado. Se preparó un guion básico que puedes editar."
        GptErrorType.NO_NETWORK ->
            "No hay conexión disponible. Se preparó un guion básico que puedes editar."
        GptErrorType.TIMEOUT ->
            "El servicio tardó demasiado. Se preparó un guion básico que puedes editar."
        GptErrorType.HTTP_429 ->
            "El servicio está ocupado por ahora. Se preparó un guion básico que puedes editar."
        else ->
            "No se pudo preparar el guion con el servicio. Se preparó un guion básico que puedes editar."
    }

    private fun log(message: String) {
        runCatching { logSink("eventType=SESSION_SCRIPT_GENERATED $message") }
    }

    companion object {
        private const val TAG = "SessionScript"
    }
}
