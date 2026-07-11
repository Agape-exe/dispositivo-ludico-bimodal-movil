package com.taller.app.ui

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.SevenAttentionVisualExpression
import com.taller.app.attention.resolveSevenAttentionVisualExpression
import com.taller.app.bimodal.BimodalInteractionState

/**
 * Expresion logica de Seven en el modo inteligente.
 *
 * UI02-FINAL: este enum modela la expresion segun el FLUJO de la sesion (con la
 * prioridad y el matiz de atencion ya resueltos). El dibujo final de la cara
 * (perrito blanco) vive en com.taller.app.ui.face.SevenDogFace, y la
 * conversion a estado visual en com.taller.app.ui.face.SevenFaceMapper.
 */
internal enum class IntelligentSevenExpression {
    SEARCHING_FACE,
    READY,
    SPEAKING,
    LISTENING,
    THINKING,
    HAPPY,
    ENCOURAGING,
    CONFUSED,
    CELEBRATION
}

/**
 * Expresion visual de Seven para el estado actual.
 *
 * FINAL-FLOW01-FIX02: la expresion depende del FLUJO de la sesion (base) y, solo si
 * la atencion funcional esta activa ([attentionDrivenVisualsEnabled] = configuracion
 * de camara/atencion real), la atencion observada puede matizar la expresion en los
 * estados de baja prioridad (esperar rostro, listo, etc.). El panel de depuracion de
 * atencion NUNCA influye aqui: mostrar/ocultar el panel tecnico no cambia la cara de
 * Seven, la camara, la recaptura ni el flujo.
 */
internal fun BimodalInteractionState.toIntelligentSevenExpression(
    facePresent: Boolean,
    toyVoiceSpeaking: Boolean,
    attentionSnapshot: AttentionSnapshot? = null,
    attentionDrivenVisualsEnabled: Boolean = false
): IntelligentSevenExpression {
    // Solo cuando la atencion FUNCIONAL esta activa, la atencion real puede matizar la
    // expresion. Se respeta la prioridad del flujo: mientras Seven habla, escucha,
    // piensa o da feedback, la atencion no pisa esa expresion (guard interno).
    if (attentionDrivenVisualsEnabled) {
        resolveSevenAttentionVisualExpression(
            interactionState = this,
            attentionSnapshot = attentionSnapshot,
            toyVoiceSpeaking = toyVoiceSpeaking,
            attentionVisualDebugEnabled = false
        )?.toIntelligentSevenExpression()?.let { return it }
    }
    return flowSevenExpression(facePresent, toyVoiceSpeaking)
}

/** Expresion segun el flujo de la sesion, independiente de camara y depuracion. */
private fun BimodalInteractionState.flowSevenExpression(
    facePresent: Boolean,
    toyVoiceSpeaking: Boolean
): IntelligentSevenExpression = when {
    toyVoiceSpeaking -> IntelligentSevenExpression.SPEAKING

    this == BimodalInteractionState.FEEDBACK_CORRECT -> IntelligentSevenExpression.HAPPY

    this == BimodalInteractionState.FEEDBACK_INCORRECT ||
        this == BimodalInteractionState.FEEDBACK_NO_RESPONSE ||
        this == BimodalInteractionState.TIME_EXPIRED ||
        this == BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR -> IntelligentSevenExpression.ENCOURAGING

    this == BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE -> IntelligentSevenExpression.CONFUSED

    this == BimodalInteractionState.SESSION_COMPLETED -> IntelligentSevenExpression.CELEBRATION

    this == BimodalInteractionState.READY ||
        this == BimodalInteractionState.WAITING_FOR_FACE ||
        this == BimodalInteractionState.PAUSED_FACE_LOST ||
        this == BimodalInteractionState.FACE_DETECTED ||
        this == BimodalInteractionState.NEXT_QUESTION ->
        if (facePresent) IntelligentSevenExpression.READY
        else IntelligentSevenExpression.SEARCHING_FACE

    this == BimodalInteractionState.PRESENTING_QUESTION -> IntelligentSevenExpression.SPEAKING

    this == BimodalInteractionState.WAITING_FOR_RESPONSE ||
        this == BimodalInteractionState.LISTENING -> IntelligentSevenExpression.LISTENING

    !facePresent && (
        this == BimodalInteractionState.IDLE ||
            this == BimodalInteractionState.LOADING_ACTIVITY
        ) -> IntelligentSevenExpression.SEARCHING_FACE

    this == BimodalInteractionState.TRANSCRIBING ||
        this == BimodalInteractionState.EVALUATING -> IntelligentSevenExpression.THINKING

    else -> IntelligentSevenExpression.READY
}

private fun SevenAttentionVisualExpression.toIntelligentSevenExpression(): IntelligentSevenExpression =
    when (this) {
        SevenAttentionVisualExpression.WAITING -> IntelligentSevenExpression.READY
        SevenAttentionVisualExpression.SEARCHING -> IntelligentSevenExpression.SEARCHING_FACE
        SevenAttentionVisualExpression.CURIOUS -> IntelligentSevenExpression.READY
        SevenAttentionVisualExpression.ATTENTIVE -> IntelligentSevenExpression.HAPPY
        SevenAttentionVisualExpression.SOFT_CONFUSED -> IntelligentSevenExpression.CONFUSED
        SevenAttentionVisualExpression.WAITING_PATIENTLY -> IntelligentSevenExpression.SEARCHING_FACE
    }
