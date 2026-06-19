package com.taller.app.bimodal

/**
 * Banco local de frases de Seven para manejar la perdida y recuperacion de
 * presencia facial durante una pregunta activa.
 *
 * Tres categorias:
 * - [FACE_LOST_PROMPT]: Seven informa que perdio la senal visual.
 * - [FACE_RETURNED_PROMPT]: Seven confirma que volvio a ver al nino.
 * - [RESUME_QUESTION_PROMPT]: Seven retoma la pregunta sin repetir la introduccion
 *   completa; usa {question} como marcador del texto de la pregunta.
 *
 * Seleccion anti-repeticion: nunca elige la misma frase que la ultima usada en
 * cada categoria, siempre que haya al menos dos opciones.
 */
class FacePausePhraseBank {

    private val lastUsed = mutableMapOf<String, Int>()

    private val faceLostPhrases = listOf(
        "¿Hola? Seven no puede verte por ahora.",
        "Mi cámara espacial perdió tu señal. ¿Sigues ahí?",
        "Seven no ve tu carita. Me quedaré esperando.",
        "Mi radar visual se quedó sin señal.",
        "No puedo verte desde mi nave. Avísame volviendo frente a mí.",
        "La señal de tu rostro desapareció un momento.",
        "Seven está buscando tu señal visual.",
        "Mi antena dice que te moviste fuera de vista.",
        "No te veo por ahora, pero Seven sigue aquí.",
        "Cuando vuelvas frente a mí, seguimos la misión."
    )

    private val faceReturnedPhrases = listOf(
        "¡Hey, ya te veo!",
        "¡Señal visual recuperada!",
        "¡Ahí estás! Seven volvió a encontrarte.",
        "¡Mi radar te detectó otra vez!",
        "¡Listo, ya apareciste en mi pantalla espacial!",
        "¡Seven ya puede verte de nuevo!",
        "¡La señal regresó a mi nave!",
        "¡Qué bueno, ya estás frente a mí!",
        "¡Te encontré otra vez, explorador!",
        "¡Perfecto, mi cámara espacial volvió a verte!"
    )

    private val resumeQuestionPhrases = listOf(
        "Retomemos el reto: {question}",
        "Sigamos con la misión. El reto era: {question}",
        "Volvamos a la exploración: {question}",
        "Seven continúa desde aquí: {question}",
        "Mi nave vuelve al reto pendiente: {question}",
        "Seguimos con este reto terrestre: {question}",
        "La misión continúa con la misma pregunta: {question}",
        "Seven estaba aprendiendo esto: {question}",
        "Volvamos al reto que quedó pendiente: {question}"
    )

    fun getFaceLostPhrase(): String = pick("face_lost", faceLostPhrases)
    fun getFaceReturnedPhrase(): String = pick("face_returned", faceReturnedPhrases)

    fun getResumeQuestionPhrase(question: String): String =
        pick("resume_question", resumeQuestionPhrases).replace("{question}", question.trim())

    private fun pick(bucket: String, list: List<String>): String {
        if (list.isEmpty()) return ""
        val last = lastUsed[bucket] ?: -1
        val candidates = list.indices.filter { it != last }
        val chosen = if (candidates.isNotEmpty()) candidates.random() else list.indices.random()
        lastUsed[bucket] = chosen
        return list[chosen]
    }
}
