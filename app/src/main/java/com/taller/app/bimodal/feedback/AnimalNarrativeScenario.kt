package com.taller.app.bimodal.feedback

/**
 * Mini historia coherente para una clave de mediacion de animales.
 *
 * Agrupa la introduccion y todas las variantes de retroalimentacion de un mismo
 * escenario narrativo, de modo que lo que el juguete dice al presentar la pregunta
 * y lo que dice al dar la retroalimentacion pertenezcan SIEMPRE a la misma historia
 * (por ejemplo, si la intro cuenta que el gatito perdio su voz, el feedback correcto
 * habla de que el gatito la recupero, nunca de "el gatito de la ventana").
 *
 * Reglas que respetan los conjuntos de cada escenario:
 *  - [intro] incluye el enunciado real de la pregunta (las claves de animales lo
 *    necesitan, porque la escena se reproduce en lugar de la pregunta).
 *  - [incorrectRetryFeedback] nunca revela la respuesta esperada: solo invita a
 *    pensar de nuevo dentro de la misma historia.
 *  - [incorrectNextFeedback] (sin intentos restantes) si puede mencionar la
 *    respuesta de forma amable.
 *  - Ningun conjunto culpa al nino ni sugiere acierto parcial.
 */
data class AnimalNarrativeScenario(
    val id: String,
    val intro: List<String>,
    val correctFeedback: List<String>,
    val incorrectRetryFeedback: List<String>,
    val incorrectNextFeedback: List<String>
)
