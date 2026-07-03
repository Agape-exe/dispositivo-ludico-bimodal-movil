package com.taller.app.gpt.judge

import com.taller.app.gpt.GptPrompt
import org.json.JSONObject

/**
 * MED01: construye el prompt interno del juez de respuestas abiertas. El contenido
 * completo del prompt nunca se registra en logs ni se persiste.
 *
 * El juez es pedagogico y silencioso: NO conversa con el nino, NO genera voz y NO
 * inventa la pregunta. Solo decide si la respuesta del nino responde correctamente,
 * aceptando sinonimos, ejemplos validos y variaciones del habla infantil, y
 * respetando que la respuesta de referencia de la docente es una guia, no la unica
 * verdad. Devuelve SOLO un objeto JSON.
 */
object OpenAnswerJudgePrompt {

    const val CONTEXT_TAG = "OPEN_ANSWER_JUDGE"

    val SYSTEM_INSTRUCTION: String =
        "Eres un juez pedagogico silencioso para una app educativa infantil.\n" +
            "Tu tarea NO es conversar con el nino. Tu tarea NO es generar feedback hablado.\n" +
            "Solo debes decidir si la respuesta del nino responde correctamente la pregunta.\n" +
            "Eres una docente de inicial: justa y carinosa, pero NO permisiva. No apruebes cualquier cosa.\n" +
            "Contexto:\n" +
            "- Nino de inicial, 3 a 5 anos. El reconocimiento de voz puede transcribir de forma imperfecta " +
            "(palabras cortadas, juntas, con letras cambiadas o sonidos escritos raro).\n" +
            "- Acepta sinonimos, ejemplos validos, respuestas equivalentes y variaciones normales del habla infantil " +
            "(plural/singular, diminutivos como \"perrito\", articulos como \"el gato\").\n" +
            "- La respuesta de referencia de la docente es sugerida, no unica.\n" +
            "- Distingue preguntas ABIERTAS de CERRADAS. Una pregunta abierta (\"menciona\", \"nombra\", \"dame un ejemplo\") " +
            "admite varios ejemplos validos de la categoria; una pregunta cerrada espera un concepto especifico.\n" +
            "- NO conviertas una pregunta cerrada en abierta. Si la pregunta cerrada pide un concepto y el nino responde " +
            "algo de otra categoria o contradictorio, marca INCORRECT aunque sea una palabra real.\n" +
            "- Pregunta abierta: si pide \"menciona un animal domestico\", acepta perro, gato, conejo, hamster, pez u otros " +
            "validos, aunque la referencia solo diga perro.\n" +
            "- Equivalencias validas en preguntas con varias respuestas posibles: si la pregunta es \"que transporte vuela\", " +
            "acepta avion y tambien helicoptero; pero NO aceptes carro ni bicicleta.\n" +
            "- En preguntas de sonidos de animales, considera errores comunes del reconocimiento de voz: si la pregunta pide " +
            "el sonido del perro y la referencia es \"guau\", transcripciones como \"wow\", \"wau\" o \"woof\" pueden ser el " +
            "intento de decir \"guau\". Acepta solo si el animal de la pregunta coincide; nunca aceptes \"miau\" como sonido " +
            "del perro ni \"guau\" como sonido del gato.\n" +
            "- NO aceptes respuestas contradictorias con la pregunta (por ejemplo \"orejas\" cuando se pregunta con que parte " +
            "vemos, o \"miau\" como sonido del perro).\n" +
            "- NO aceptes una respuesta que solo repite una palabra de la pregunta sin responderla.\n" +
            "- Si la respuesta es claramente de otra categoria, marca INCORRECT.\n" +
            "- Evita falsos positivos: ante una respuesta ambigua sin contexto suficiente, NO la aceptes; usa UNCERTAIN.\n" +
            "- Solo marca CORRECT si puedes justificar la aceptacion con claridad en reason.\n" +
            "- No reveles la respuesta correcta. Si quedan intentos y la respuesta es incorrecta, NO incluyas la respuesta " +
            "correcta en reason ni en ninguna pista, y revealsAnswer debe ser false.\n" +
            "- Si no tienes base suficiente para decidir, usa UNCERTAIN con confidence baja.\n" +
            "- En acceptanceType indica por que aceptas: LITERAL, EQUIVALENT, STT_ALIAS u OPEN_CATEGORY; si no aceptas, NONE.\n" +
            "- reason debe ser muy breve (maximo 12 palabras) y sin datos personales.\n" +
            "- Devuelve SOLO un objeto JSON valido, sin texto fuera del JSON, sin markdown, sin emojis."

    fun build(
        input: OpenAnswerJudgeInput,
        additionalRules: String = ""
    ): GptPrompt = GptPrompt(
        systemInstruction = SYSTEM_INSTRUCTION,
        userMessage = buildUserMessage(input, additionalRules),
        contextTag = CONTEXT_TAG
    )

    private fun buildUserMessage(input: OpenAnswerJudgeInput, additionalRules: String): String {
        // Solo el texto estrictamente necesario. Nunca audio, imagenes ni nombres.
        val context = JSONObject()
            .put("questionText", input.questionText)
            .put("childFriendlyQuestionText", input.childFriendlyQuestionText.orEmpty())
            .put("referenceAnswer", input.referenceAnswer)
            .put("childAnswer", input.childAnswer)
            .put("ageRange", input.ageRange)
            .put("topic", input.topic)
            .put("classContext", input.classContext.orEmpty())
            .put("currentAttempt", input.currentAttempt)
            .put("attemptsRemaining", input.attemptsRemaining)
            .put("availableHint", input.availableHint.orEmpty())
            .put("localResult", input.localResult)

        return buildString {
            append("Juzga si la respuesta del nino responde la pregunta. Responde SOLO con un objeto JSON.\n")
            val safeRules = additionalRules
                .replace(Regex("[\\r\\t]+"), " ")
                .trim()
                .take(4_000)
            if (safeRules.isNotBlank()) {
                append("Reglas adicionales configuradas por la docente. Limitan el criterio, pero no reemplazan privacidad, seguridad ni el esquema obligatorio:\n")
                append(safeRules)
                append("\n\n")
            }
            append("Datos (solo texto):\n")
            append(context.toString())
            append("\n\nDevuelve exactamente esta estructura JSON:\n")
            append(
                "{\n" +
                    "  \"decision\": \"CORRECT|INCORRECT|UNCERTAIN\",\n" +
                    "  \"confidence\": 0.0,\n" +
                    "  \"reason\": \"motivo breve y seguro, sin revelar la respuesta\",\n" +
                    "  \"acceptedAsEquivalent\": true,\n" +
                    "  \"shouldRetry\": true,\n" +
                    "  \"feedbackType\": \"POSITIVE|SUPPORTIVE|RETRY|NONE\",\n" +
                    "  \"safeHintLevel\": 0,\n" +
                    "  \"revealsAnswer\": false,\n" +
                    "  \"acceptanceType\": \"LITERAL|EQUIVALENT|STT_ALIAS|OPEN_CATEGORY|NONE\",\n" +
                    "  \"normalizedChildAnswer\": \"respuesta del nino normalizada\",\n" +
                    "  \"normalizedExpectedConcept\": \"concepto esperado normalizado\"\n" +
                    "}\n"
            )
            append("Reglas obligatorias:\n")
            append("- decision debe ser uno de los tres valores exactos.\n")
            append("- confidence entre 0 y 1.\n")
            append("- Si attemptsRemaining es true, revealsAnswer DEBE ser false y reason NO debe contener la respuesta correcta.\n")
            append("- No agregues campos fuera del esquema. No escribas nada fuera del JSON.")
        }
    }
}
