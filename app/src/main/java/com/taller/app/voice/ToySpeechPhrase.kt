package com.taller.app.voice

enum class ToySpeechPhrase(val text: String) {
    ACTIVITY_START("Vamos a empezar."),
    QUESTION_INTRO("Escucha con atención."),
    LISTENING("Tienes unos segundos para responder."),
    TIME_EXPIRED("Se terminó el tiempo."),
    NEXT_QUESTION("Pasemos a la siguiente pregunta."),
    ACTIVITY_FINISHED("Terminamos la actividad."),
    NEUTRAL_ENCOURAGEMENT("Continuemos.")
}
