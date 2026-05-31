package com.taller.app.voice

enum class ToySpeechPhrase(val text: String) {
    ACTIVITY_START("Vamos a empezar."),
    FACE_DETECTED("Ya estoy listo para jugar contigo."),
    QUESTION_INTRO("Escucha con atención."),
    LISTENING("Tienes unos segundos para responder."),
    CORRECT_NEUTRAL("Muy bien, continuemos."),
    INCORRECT_RETRY("Intentemos una vez más."),
    INCORRECT_NEXT("Vamos con la siguiente pregunta."),
    NOT_INTERPRETABLE_RETRY("No pude entenderte bien. Repitamos."),
    NO_RESPONSE_RETRY("No escuché una respuesta. Intentemos otra vez."),
    TIME_EXPIRED("Se terminó el tiempo."),
    NEXT_QUESTION("Pasemos a la siguiente pregunta."),
    ACTIVITY_FINISHED("Terminamos la actividad. Gracias por participar."),
    NEUTRAL_ENCOURAGEMENT("Continuemos."),
    TECHNICAL_ERROR("Hubo un problema. Intentemos continuar.")
}
