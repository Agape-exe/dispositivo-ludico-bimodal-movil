package com.taller.app.gpt.script

import android.util.Log
import com.taller.app.gpt.GptClient
import com.taller.app.gpt.GptErrorType
import com.taller.app.gpt.GptResult

/**
 * GEN01 / GEN01-FIX01: orquesta la generacion del guion. "El modelo propone, el
 * orquestador dispone".
 *
 * Pide el guion al modelo de texto existente, lo interpreta de forma tolerante y lo
 * combina con un guion local de respaldo campo por campo:
 *  - si el modelo entrega un guion completo y valido, se usa tal cual;
 *  - si entrega un guion parcialmente util, se conserva lo valido y se completan los
 *    huecos con el guion local (recuperacion parcial);
 *  - solo si no hay nada recuperable se usa el guion local completo.
 *
 * Nunca registra el prompt, la respuesta completa ni credenciales: solo un motivo
 * tecnico corto y conteos.
 */
class SessionScriptGenerator(
    private val gptClient: GptClient,
    private val logSink: (String) -> Unit = { Log.d(TAG, it) }
) {

    /** Motivo tecnico corto y seguro para depuracion y para mostrar a la docente. */
    object Reason {
        const val EMPTY_RESPONSE = "EMPTY_RESPONSE"
        const val INVALID_JSON = "INVALID_JSON"
        const val PARSE_ERROR = "PARSE_ERROR"
        const val MISSING_FIELD = "MISSING_FIELD"
        const val VALIDATION_ERROR = "VALIDATION_ERROR"
        const val TRUNCATED_RESPONSE = "TRUNCATED_RESPONSE"
        const val PARTIAL_RECOVERY = "PARTIAL_RECOVERY"
        const val DISABLED = "DISABLED"
        const val UNKNOWN = "UNKNOWN"
    }

    sealed interface Outcome {
        val script: SessionScript

        /** Guion propuesto por el modelo y validado localmente, sin ajustes. */
        data class FromModel(override val script: SessionScript) : Outcome

        /**
         * Guion del modelo en el que algunas partes se completaron automaticamente
         * con el guion local. Sigue siendo util y editable por la docente.
         */
        data class FromModelPartial(
            override val script: SessionScript,
            val message: String,
            val reason: String
        ) : Outcome

        /** Guion local de respaldo; [message] es un texto claro y no tecnico para la docente. */
        data class FromLocal(
            override val script: SessionScript,
            val message: String,
            val reason: String
        ) : Outcome
    }

    suspend fun generate(input: SessionScriptInput): Outcome {
        val localScript = SessionScriptLocalFallback.build(input)

        if (input.questions.isEmpty()) {
            return Outcome.FromLocal(
                localScript,
                "Agrega al menos una pregunta para preparar el guion.",
                Reason.MISSING_FIELD
            )
        }

        val prompt = SessionScriptPrompt.build(input)

        return when (val result = gptClient.generate(prompt)) {
            is GptResult.Success -> handleSuccess(result, input, localScript)

            is GptResult.Disabled -> {
                log("source=LOCAL reason=${Reason.DISABLED} questions=${input.questions.size}")
                Outcome.FromLocal(
                    localScript,
                    "El asistente está desactivado. Se preparó un guion básico que puedes editar.",
                    Reason.DISABLED
                )
            }

            is GptResult.Failure -> {
                log("source=LOCAL reason=${result.errorType.name} questions=${input.questions.size}")
                Outcome.FromLocal(localScript, friendlyMessageFor(result.errorType), result.errorType.name)
            }
        }
    }

    private fun handleSuccess(
        result: GptResult.Success,
        input: SessionScriptInput,
        localScript: SessionScript
    ): Outcome {
        if (result.text.isBlank()) {
            log("source=LOCAL reason=${Reason.EMPTY_RESPONSE} questions=${input.questions.size}")
            return Outcome.FromLocal(localScript, totalFallbackMessage, Reason.EMPTY_RESPONSE)
        }

        val parsed = runCatching { SessionScriptParser.parse(result.text, input) }.getOrNull()
        if (parsed == null) {
            val reason = classifyParseFailure(result.text)
            log("source=LOCAL reason=$reason questions=${input.questions.size}")
            return Outcome.FromLocal(localScript, totalFallbackMessage, reason)
        }

        // Recuperacion parcial: conserva lo valido del modelo y completa lo demas.
        val recovery = SessionScriptRecovery.recover(parsed, localScript, input)

        if (!recovery.usedModelContent) {
            // El modelo no aporto nada utilizable: respaldo local completo.
            log("source=LOCAL reason=${Reason.VALIDATION_ERROR} repaired=${recovery.repairedFields} questions=${input.questions.size}")
            return Outcome.FromLocal(localScript, totalFallbackMessage, Reason.VALIDATION_ERROR)
        }

        // Verificacion final del guion combinado antes de entregarlo.
        val validation = SessionScriptValidator.validate(recovery.script, input)
        if (!validation.isValid) {
            log("source=LOCAL reason=${Reason.VALIDATION_ERROR} issues=${validation.issues.size} questions=${input.questions.size}")
            return Outcome.FromLocal(localScript, totalFallbackMessage, Reason.VALIDATION_ERROR)
        }

        if (recovery.repairedFields == 0) {
            log("source=MODEL questions=${input.questions.size} model=${result.modelUsed}")
            return Outcome.FromModel(recovery.script)
        }

        log(
            "source=MODEL_PARTIAL repaired=${recovery.repairedFields} kept=${recovery.modelFieldsKept} " +
                "questions=${input.questions.size} model=${result.modelUsed}"
        )
        return Outcome.FromModelPartial(
            recovery.script,
            "Se generó el guion, pero algunas partes fueron completadas automáticamente. Revísalo antes de guardar.",
            Reason.PARTIAL_RECOVERY
        )
    }

    /** Clasifica por que no se pudo interpretar el texto, sin exponer su contenido. */
    private fun classifyParseFailure(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Reason.EMPTY_RESPONSE
        val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("```") || trimmed.contains("{")
        val looksClosed = trimmed.endsWith("}") || trimmed.endsWith("```")
        return if (looksLikeJson && !looksClosed) Reason.TRUNCATED_RESPONSE else Reason.INVALID_JSON
    }

    private val totalFallbackMessage =
        "El guion recibido no se pudo interpretar. Se preparó un guion básico que puedes editar."

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
