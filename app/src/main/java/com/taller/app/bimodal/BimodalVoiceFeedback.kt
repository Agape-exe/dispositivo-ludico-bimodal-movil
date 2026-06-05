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
 * Distingue cuatro categorías de retroalimentación, de modo que una frase de
 * continuidad ("continuemos", "siguiente pregunta") nunca preceda al cierre:
 * resultado (correcto/incorrecto), reintento, paso a la siguiente pregunta y
 * cierre de sesión. En la última pregunta el desenlace usa solo frases de
 * resultado, sin continuidad, y el cierre lo aporta [SESSION_COMPLETED].
 *
 * @param state estado actual del flujo.
 * @param canRetry si el orquestador permite reintentar la pregunta actual.
 * @param questionIndex índice de la pregunta actual (cero-basado), para
 *   distinguir el inicio de sesión de las transiciones entre preguntas.
 * @param isLastQuestion si la pregunta actual es la última de la actividad; en
 *   ese caso no se reproducen frases de continuidad antes del cierre.
 * @return la frase a reproducir, o null si el estado no requiere voz.
 */
object BimodalVoiceFeedback {

    fun phraseFor(
        state: BimodalInteractionState,
        canRetry: Boolean = false,
        questionIndex: Int = 0,
        isLastQuestion: Boolean = false
    ): ToySpeechPhrase? = when (state) {
        BimodalInteractionState.WAITING_FOR_FACE ->
            if (questionIndex == 0) ToySpeechPhrase.ACTIVITY_START
            else ToySpeechPhrase.NEXT_QUESTION
        // Resultado correcto: en la última pregunta solo el resultado, sin invitar
        // a continuar; en preguntas intermedias se permite la continuidad.
        BimodalInteractionState.FEEDBACK_CORRECT ->
            if (isLastQuestion) ToySpeechPhrase.CORRECT_FINAL
            else ToySpeechPhrase.CORRECT_NEUTRAL
        // Incorrecta: si quedan intentos se reintenta; si no, frase de paso a la
        // siguiente (intermedia) o de solo resultado (última pregunta).
        BimodalInteractionState.FEEDBACK_INCORRECT -> when {
            canRetry -> ToySpeechPhrase.INCORRECT_RETRY
            isLastQuestion -> ToySpeechPhrase.INCORRECT_FINAL
            else -> ToySpeechPhrase.INCORRECT_NEXT
        }
        // No interpretable / sin respuesta: la frase de reintento solo aplica si
        // realmente se puede reintentar; en caso contrario, solo resultado.
        BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ->
            if (canRetry) ToySpeechPhrase.NOT_INTERPRETABLE_RETRY
            else ToySpeechPhrase.NOT_INTERPRETABLE_FINAL
        BimodalInteractionState.FEEDBACK_NO_RESPONSE ->
            if (canRetry) ToySpeechPhrase.NO_RESPONSE_RETRY
            else ToySpeechPhrase.NO_RESPONSE_FINAL
        BimodalInteractionState.TIME_EXPIRED ->
            ToySpeechPhrase.TIME_EXPIRED
        BimodalInteractionState.SESSION_COMPLETED ->
            ToySpeechPhrase.ACTIVITY_FINISHED
        BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR ->
            ToySpeechPhrase.TECHNICAL_ERROR
        else -> null
    }
}
