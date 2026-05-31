package com.taller.app.bimodal

import com.taller.app.voice.ToySpeechPhrase

/**
 * Mapea un estado del flujo bimodal a la frase predefinida que el juguete debe
 * reproducir al entrar en ese estado.
 *
 * Es independiente de Android y no accede a servicios de voz: solo decide qué
 * frase corresponde. El componente de UI que llama a este objeto es responsable
 * de reproducirla mediante [com.taller.app.voice.ToyVoiceFallback].
 *
 * @param state estado actual del flujo.
 * @param canRetry si el orquestador permite reintentar la pregunta actual.
 * @param questionIndex índice de la pregunta actual (cero-basado), para
 *   distinguir el inicio de sesión de las transiciones entre preguntas.
 * @return la frase a reproducir, o null si el estado no requiere voz.
 */
object BimodalVoiceFeedback {

    fun phraseFor(
        state: BimodalInteractionState,
        canRetry: Boolean = false,
        questionIndex: Int = 0
    ): ToySpeechPhrase? = when (state) {
        BimodalInteractionState.WAITING_FOR_FACE ->
            if (questionIndex == 0) ToySpeechPhrase.ACTIVITY_START
            else ToySpeechPhrase.NEXT_QUESTION
        BimodalInteractionState.FEEDBACK_CORRECT ->
            ToySpeechPhrase.CORRECT_NEUTRAL
        BimodalInteractionState.FEEDBACK_INCORRECT ->
            if (canRetry) ToySpeechPhrase.INCORRECT_RETRY else ToySpeechPhrase.INCORRECT_NEXT
        BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ->
            ToySpeechPhrase.NOT_INTERPRETABLE_RETRY
        BimodalInteractionState.FEEDBACK_NO_RESPONSE ->
            ToySpeechPhrase.NO_RESPONSE_RETRY
        BimodalInteractionState.TIME_EXPIRED ->
            ToySpeechPhrase.TIME_EXPIRED
        BimodalInteractionState.SESSION_COMPLETED ->
            ToySpeechPhrase.ACTIVITY_FINISHED
        BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR ->
            ToySpeechPhrase.TECHNICAL_ERROR
        else -> null
    }
}
