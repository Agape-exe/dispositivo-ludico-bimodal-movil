package com.taller.app.ui.face

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.classic.ClassicTimerState
import com.taller.app.ui.IntelligentSevenExpression
import com.taller.app.ui.toIntelligentSevenExpression

/**
 * Traduccion de los estados de flujo de cada modo al estado visual de la cara
 * de Seven ([SevenFaceState]). Mantiene la logica visual separada de la logica
 * de negocio: aqui no se decide nada del flujo, solo como se ve Seven.
 */

/** Expresion interna del modo inteligente → estado visual de la cara final. */
internal fun IntelligentSevenExpression.toSevenFaceState(): SevenFaceState = when (this) {
    IntelligentSevenExpression.SEARCHING_FACE -> SevenFaceState.WAITING
    IntelligentSevenExpression.READY -> SevenFaceState.IDLE
    IntelligentSevenExpression.SPEAKING -> SevenFaceState.SPEAKING
    IntelligentSevenExpression.LISTENING -> SevenFaceState.LISTENING
    IntelligentSevenExpression.THINKING -> SevenFaceState.THINKING
    IntelligentSevenExpression.HAPPY -> SevenFaceState.HAPPY
    IntelligentSevenExpression.ENCOURAGING -> SevenFaceState.SUPPORTIVE
    IntelligentSevenExpression.CONFUSED -> SevenFaceState.RETRY
    IntelligentSevenExpression.CELEBRATION -> SevenFaceState.CLOSING
}

/**
 * Estado visual del modo inteligente a partir del estado de flujo y de la
 * expresion ya resuelta (que puede venir retenida por la logica de duracion
 * minima de expresiones de la pantalla). Los estados terminales tienen cara
 * propia: error tecnico → confusion suave, cancelacion → espera calmada.
 */
internal fun sevenFaceStateForIntelligentRender(
    interactionState: BimodalInteractionState,
    resolvedExpression: IntelligentSevenExpression
): SevenFaceState = when (interactionState) {
    BimodalInteractionState.ERROR -> SevenFaceState.ERROR_SOFT
    BimodalInteractionState.SESSION_CANCELLED -> SevenFaceState.WAITING
    else -> resolvedExpression.toSevenFaceState()
}

/**
 * Mapeo completo del modo inteligente: estado de flujo → cara de Seven.
 *
 * Reusa el resolutor de expresiones existente, que ya garantiza que:
 * - la expresion depende del flujo de la sesion, no de la camara;
 * - la atencion solo matiza estados de baja prioridad y solo si la atencion
 *   FUNCIONAL esta activa ([attentionDrivenVisualsEnabled]);
 * - el panel de depuracion de atencion nunca cambia la cara.
 */
internal fun intelligentSevenFaceState(
    interactionState: BimodalInteractionState,
    facePresent: Boolean,
    toyVoiceSpeaking: Boolean,
    attentionSnapshot: AttentionSnapshot? = null,
    attentionDrivenVisualsEnabled: Boolean = false
): SevenFaceState = sevenFaceStateForIntelligentRender(
    interactionState = interactionState,
    resolvedExpression = interactionState.toIntelligentSevenExpression(
        facePresent = facePresent,
        toyVoiceSpeaking = toyVoiceSpeaking,
        attentionSnapshot = attentionSnapshot,
        attentionDrivenVisualsEnabled = attentionDrivenVisualsEnabled
    )
)

/**
 * Mapeo del modo temporizador: solo estados neutrales.
 *
 * Nunca devuelve HAPPY, SUPPORTIVE, RETRY ni THINKING: el temporizador no
 * revela al nino si su respuesta fue correcta o incorrecta. El resultado
 * semantico interno no participa en este mapeo a proposito.
 */
fun classicSevenFaceState(
    state: ClassicTimerState,
    toyVoiceSpeaking: Boolean,
    pausedByTeacher: Boolean
): SevenFaceState = when {
    pausedByTeacher -> SevenFaceState.WAITING
    toyVoiceSpeaking -> SevenFaceState.SPEAKING
    else -> when (state) {
        ClassicTimerState.IDLE,
        ClassicTimerState.READY -> SevenFaceState.IDLE
        ClassicTimerState.LOADING_ACTIVITY -> SevenFaceState.WAITING
        ClassicTimerState.SESSION_STARTING -> SevenFaceState.INTRO
        ClassicTimerState.PRESENTING_QUESTION -> SevenFaceState.SPEAKING
        ClassicTimerState.WAITING_FIXED_RESPONSE -> SevenFaceState.LISTENING
        ClassicTimerState.RESPONSE_IN_PROGRESS -> SevenFaceState.LISTENING
        ClassicTimerState.ANSWER_RECEIVED -> SevenFaceState.WAITING
        ClassicTimerState.TIME_EXPIRED -> SevenFaceState.TIMEOUT_NEUTRAL
        ClassicTimerState.SESSION_COMPLETED -> SevenFaceState.CLOSING
        ClassicTimerState.SESSION_CANCELLED -> SevenFaceState.WAITING
        ClassicTimerState.ERROR -> SevenFaceState.ERROR_SOFT
    }
}
