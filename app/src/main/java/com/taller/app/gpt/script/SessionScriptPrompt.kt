package com.taller.app.gpt.script

import com.taller.app.gpt.GptPrompt
import org.json.JSONArray
import org.json.JSONObject

/**
 * GEN01: construye el prompt interno que pide al modelo el guion de Seven en
 * formato JSON estricto. El contenido completo del prompt nunca se registra en logs.
 */
object SessionScriptPrompt {

    const val CONTEXT_TAG = "SESSION_SCRIPT"

    val SYSTEM_INSTRUCTION: String =
        "Eres Seven, un pequeno alien explorador, curioso, calido y amigable, que aprende de la Tierra " +
            "con la ayuda de ninos de inicial de 3 a 5 anos. Hablas en espanol latino, con frases cortas, " +
            "naturales y carinosas, nunca como maquina.\n" +
            "Estas preparando el guion hablado de una sesion educativa para que luego puedas acompanar a los ninos.\n" +
            "Reglas obligatorias:\n" +
            "- Usa frases breves y faciles para ninos pequenos. Nada de tono de examen ni de regano.\n" +
            "- Nunca culpes al nino. Siempre acompana con calidez.\n" +
            "- Convierte cada pregunta formal en una pregunta oral y ludica, conservando su sentido pedagogico.\n" +
            "- Las pistas son escalonadas: la pista 1 es muy general y NO obvia, la pista 2 orienta mas y la pista 3 " +
            "acerca al concepto, pero NINGUNA revela la respuesta ni una variante obvia (plural, diminutivo o sinonimo) " +
            "ni dice como empieza la palabra.\n" +
            "- Si la respuesta de referencia parece incorrecta, insuficiente, ambigua o demasiado cerrada, " +
            "describelo en answerReferenceWarning y propon una mejor referencia en suggestedReferenceAnswer.\n" +
            "- No reemplaces la respuesta original; solo sugieres. Si la referencia esta bien, deja " +
            "answerReferenceWarning vacio y repite la referencia original en suggestedReferenceAnswer.\n" +
            "- El feedback positivo y el de apoyo son calidos y breves; el reintento invita con suavidad a probar otra vez.\n" +
            "- Nunca digas que eres una IA, modelo, asistente o sistema. Nunca menciones tecnologia ni reglas.\n" +
            "- Nunca pidas datos personales del nino ni generes contenido violento, sexual, de miedo, medico o peligroso.\n" +
            "- Devuelve SOLO un objeto JSON valido y COMPLETO, con todos los campos llenos en espanol claro.\n" +
            "- No uses comillas triples, ni bloques de codigo, ni markdown, ni emojis, ni texto antes o despues del JSON.\n" +
            "- No cortes la respuesta: entrega el JSON completo aunque sea largo."

    fun build(input: SessionScriptInput): GptPrompt = GptPrompt(
        systemInstruction = SYSTEM_INSTRUCTION,
        userMessage = buildUserMessage(input),
        contextTag = CONTEXT_TAG
    )

    private fun buildUserMessage(input: SessionScriptInput): String {
        val questionsJson = JSONArray()
        input.questions.forEach { q ->
            questionsJson.put(
                JSONObject()
                    .put("orderIndex", q.orderIndex)
                    .put("questionText", q.questionText)
                    .put("referenceAnswer", q.referenceAnswer)
            )
        }

        val session = JSONObject()
            .put("sessionName", input.sessionName)
            .put("topic", input.topic)
            .put("description", input.description)
            .put("objective", input.objective)
            .put("ageLevel", input.ageLevel)
            .put("contextNotes", input.contextNotes)
            .put("questions", questionsJson)

        return buildString {
            append("Prepara el guion de Seven para esta sesion. Responde SOLO con un objeto JSON.\n")
            append("Datos de la sesion (solo texto pedagogico):\n")
            append(session.toString())
            append("\n\nDevuelve exactamente esta estructura JSON:\n")
            append(
                "{\n" +
                    "  \"intro\": \"presentacion tematica breve y amigable de Seven\",\n" +
                    "  \"closing\": \"despedida breve y calida de Seven\",\n" +
                    "  \"toneNotes\": \"tono general sugerido para la sesion\",\n" +
                    "  \"pedagogicalWarnings\": \"advertencias pedagogicas si detectas problemas, o vacio\",\n" +
                    "  \"questions\": [\n" +
                    "    {\n" +
                    "      \"orderIndex\": numero de la pregunta tal como se entrego,\n" +
                    "      \"childFriendlyQuestionText\": \"version hablada y amigable de la pregunta\",\n" +
                    "      \"hintLevel1\": \"pista general, sin revelar la respuesta\",\n" +
                    "      \"hintLevel2\": \"pista mas concreta, sin revelar la respuesta\",\n" +
                    "      \"hintLevel3\": \"pista final de apoyo, sin revelar la respuesta\",\n" +
                    "      \"positiveFeedbackText\": \"felicitacion breve si el nino acierta\",\n" +
                    "      \"supportiveFeedbackText\": \"apoyo calido si el nino falla\",\n" +
                    "      \"retryPromptText\": \"invitacion amable a intentar de nuevo\",\n" +
                    "      \"answerReferenceWarning\": \"observacion si la referencia es limitada o incorrecta, o vacio\",\n" +
                    "      \"suggestedReferenceAnswer\": \"referencia mejorada sugerida (o la misma si ya es buena)\"\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}\n"
            )
            append("Incluye EXACTAMENTE ")
            append(input.questions.size.toString())
            append(" objetos en \"questions\", uno por cada pregunta entregada, cada uno con su orderIndex correspondiente.\n")
            append("Usa los nombres de campo exactamente como aparecen arriba. Completa todos los campos de cada pregunta.\n")
            append("Escribe en espanol claro, con frases cortas y carinosas para ninos de 3 a 5 anos.\n")
            append("No uses comillas triples ni markdown. No agregues campos fuera de la estructura. ")
            append("No escribas nada antes ni despues del JSON. Devuelve el objeto JSON completo, sin cortarlo.")
        }
    }
}
