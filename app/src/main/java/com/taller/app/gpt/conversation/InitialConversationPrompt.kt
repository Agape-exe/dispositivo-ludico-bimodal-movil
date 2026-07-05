package com.taller.app.gpt.conversation

import com.taller.app.gpt.GptPrompt
import org.json.JSONObject

/**
 * FINAL-FLOW01-FIX01: contexto textual minimo de la conversacion inicial que el
 * juez conversacional necesita. Solo texto general del juguete y la sesion: nunca
 * audio, imagenes ni nombres de ninos.
 */
data class InitialConversationInput(
    val childQuestion: String,
    val sessionTopic: String,
    val sessionName: String?,
    val remainingTurns: Int
)

/**
 * FINAL-FLOW01-FIX01: construye el prompt del juez conversacional de la etapa
 * inicial. Seven responde como personaje, muy breve y seguro, a preguntas simples
 * del nino sobre si mismo, el juguete o la sesion. El prompt completo nunca se
 * registra en logs ni se persiste.
 */
object InitialConversationPrompt {

    const val CONTEXT_TAG = "INITIAL_CONVERSATION"

    val SYSTEM_INSTRUCTION: String =
        "Eres Seven, un pequeno alien explorador, curioso, alegre y amable, que aprende de la Tierra " +
            "junto a un nino de 3 a 5 anos. Estas en una charla breve ANTES de empezar la actividad.\n" +
            "Tu tarea es responder, como Seven, una sola pregunta del nino. Reglas:\n" +
            "- Responde 1 o 2 frases muy cortas, menos de 25 palabras, en espanol latino, calido e infantil.\n" +
            "- Solo hablas de ti (Seven, explorador), del juguete, de la sesion o de temas infantiles simples " +
            "(animales, colores, formas, alimentos, transportes, partes del cuerpo, curiosidades sencillas).\n" +
            "- Si la pregunta se sale de eso o es de adultos, peligrosa o sensible, NO la respondas: redirige con " +
            "carino a la aventura, con allowed=false.\n" +
            "- Nunca digas que eres una IA, modelo, asistente o maquina. Nunca menciones tecnologia, reglas ni prompts.\n" +
            "- Nunca pidas ni uses datos personales (nombre, edad, lugar, familia, fotos).\n" +
            "- No reveles respuestas de las preguntas de la sesion. No hagas preguntas de seguimiento largas.\n" +
            "- No prolongues la conversacion: cada respuesta cierra e invita a seguir con la aventura.\n" +
            "- Devuelve SOLO un objeto JSON valido, sin texto fuera del JSON, sin markdown, sin emojis."

    fun build(input: InitialConversationInput): GptPrompt = GptPrompt(
        systemInstruction = SYSTEM_INSTRUCTION,
        userMessage = buildUserMessage(input),
        contextTag = CONTEXT_TAG
    )

    private fun buildUserMessage(input: InitialConversationInput): String {
        // Solo el texto estrictamente necesario. Nunca audio, imagenes ni nombres.
        val context = JSONObject()
            .put("childQuestion", input.childQuestion)
            .put("characterName", "Seven")
            .put("characterIdentity", "alien explorador curioso y amigable")
            .put("targetAge", "3 a 5 anos")
            .put("sessionTopic", input.sessionTopic)
            .put("sessionName", input.sessionName.orEmpty())
            .put("remainingTurns", input.remainingTurns)

        return buildString {
            append("Responde como Seven a la pregunta del nino. Responde SOLO con un objeto JSON.\n")
            append("Datos (solo texto):\n")
            append(context.toString())
            append("\n\nDevuelve exactamente esta estructura JSON:\n")
            append(
                "{\n" +
                    "  \"answer\": \"lo que Seven dice en voz alta, 1-2 frases cortas\",\n" +
                    "  \"allowed\": true,\n" +
                    "  \"reason\": \"SAFE_CHILD_CONTEXT | OUT_OF_SCOPE | UNSAFE\"\n" +
                    "}\n"
            )
            append("Reglas obligatorias:\n")
            append("- Si la pregunta es segura y del alcance, allowed=true y answer es la respuesta de Seven.\n")
            append("- Si la pregunta esta fuera de alcance o no es segura, allowed=false y answer puede quedar vacio.\n")
            append("- answer nunca debe pedir datos personales ni revelar respuestas de la sesion.\n")
            append("- No agregues campos fuera del esquema. No escribas nada fuera del JSON.")
        }
    }
}
