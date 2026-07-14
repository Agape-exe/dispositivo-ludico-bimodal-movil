package com.taller.app.classic

import com.taller.app.model.LearningActivity

sealed interface ClassicTimerEvent {
    data class LoadActivity(val activity: LearningActivity) : ClassicTimerEvent
    data object ActivityLoaded : ClassicTimerEvent
    data object StartSession : ClassicTimerEvent
    data object PresentCurrentQuestion : ClassicTimerEvent
    data object StartResponseWindow : ClassicTimerEvent

    /** Se detecto que el nino empezo a hablar dentro de la ventana. */
    data object ResponseStarted : ClassicTimerEvent

    /** Termino el habla; cualquier texto reconocido se descarta. */
    data object AnswerReceived : ClassicTimerEvent

    /** La ventana termino sin detectar inicio de voz. */
    data object TimeExpired : ClassicTimerEvent

    /** Avanzar sin feedback a la siguiente pregunta o al cierre. */
    data object AdvanceQuestion : ClassicTimerEvent

    data object CancelSession : ClassicTimerEvent
    data class TechnicalError(val message: String) : ClassicTimerEvent
}
