package com.taller.app.bimodal

import com.taller.app.model.LearningActivity
import com.taller.app.semantic.SemanticResult

/**
 * Eventos que dirigen el flujo del modo bimodal inteligente.
 *
 * El orquestador es una maquina de estados pura: recibe estos eventos a traves
 * de [BimodalFlowOrchestrator.onEvent] y decide la transicion correspondiente.
 * Los sensores y servicios reales (camara, reconocimiento de voz, voz del
 * juguete) traduciran sus resultados a estos eventos en iteraciones futuras.
 */
sealed class BimodalInteractionEvent {

    /** Solicita cargar una actividad con sus preguntas. */
    data class LoadActivity(val activity: LearningActivity) : BimodalInteractionEvent()

    /** Confirma que la actividad solicitada termino de cargar. */
    object ActivityLoaded : BimodalInteractionEvent()

    /** Inicia el ciclo de la pregunta actual (queda a la espera de presencia). */
    object StartQuestion : BimodalInteractionEvent()

    /** Se detecto la presencia (rostro) del nino. */
    object FaceDetected : BimodalInteractionEvent()

    /** Se perdio la presencia previamente detectada. */
    object FaceLost : BimodalInteractionEvent()

    /** Comienza la captura de voz para la respuesta. */
    object StartListening : BimodalInteractionEvent()

    /** Se capturo una transcripcion de la respuesta del nino. */
    data class SpeechCaptured(val transcription: String) : BimodalInteractionEvent()

    /** Fallo la captura o el reconocimiento de voz. */
    data class SpeechFailed(val reason: String? = null) : BimodalInteractionEvent()

    /** No se recibio ninguna respuesta. */
    object NoResponse : BimodalInteractionEvent()

    /** Se agoto el tiempo maximo para responder. */
    object TimeExpired : BimodalInteractionEvent()

    /** Llego el resultado de la evaluacion semantica de la transcripcion. */
    data class SemanticEvaluated(val result: SemanticResult) : BimodalInteractionEvent()

    /** Reintentar la pregunta actual (solo si quedan intentos). */
    object RetryQuestion : BimodalInteractionEvent()

    /** Avanzar a la siguiente pregunta o finalizar si era la ultima. */
    object MoveToNextQuestion : BimodalInteractionEvent()

    /** Finaliza la sesion de forma anticipada y normal. */
    object CompleteSession : BimodalInteractionEvent()

    /** Cancela la sesion en curso. */
    object CancelSession : BimodalInteractionEvent()

    /** Reporta un error tecnico fatal que interrumpe el flujo (estado terminal). */
    data class TechnicalError(val message: String) : BimodalInteractionEvent()

    /**
     * Reporta un error tecnico recuperable ocurrido durante la pregunta actual
     * (fallo de voz, de evaluacion semantica o de la voz del juguete). No clasifica
     * la respuesta como incorrecta ni cancela la sesion: permite reintentar si
     * quedan intentos o avanzar en caso contrario.
     */
    data class RecoverableError(val message: String) : BimodalInteractionEvent()
}
