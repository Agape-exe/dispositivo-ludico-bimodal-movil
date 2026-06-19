package com.taller.app.classic

import com.taller.app.model.LearningActivity

sealed interface ClassicTimerEvent {
    data class LoadActivity(val activity: LearningActivity) : ClassicTimerEvent
    data object ActivityLoaded : ClassicTimerEvent
    data object StartSession : ClassicTimerEvent

    /** Frase de apertura terminó; presentar la pregunta actual. */
    data object PresentCurrentQuestion : ClassicTimerEvent

    /** Frase de pregunta terminó; abrir ventana de respuesta. */
    data object StartResponseWindow : ClassicTimerEvent

    /** Se detectó voz (STT obtuvo texto). */
    data object AnswerReceived : ClassicTimerEvent

    /**
     * El tiempo asignado se agotó.
     * @param hadPartialResponse indica si el STT captó texto parcial antes del timeout.
     */
    data class TimeExpired(val hadPartialResponse: Boolean = false) : ClassicTimerEvent

    /** Frase post-respuesta o post-timeout terminó; avanzar a siguiente pregunta o cerrar. */
    data object AdvanceQuestion : ClassicTimerEvent

    data object CancelSession : ClassicTimerEvent
    data class TechnicalError(val message: String) : ClassicTimerEvent
}
