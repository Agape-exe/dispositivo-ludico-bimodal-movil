package com.taller.app.attention

import com.taller.app.bimodal.BimodalInteractionState

enum class SevenAttentionVisualExpression {
    WAITING,
    SEARCHING,
    CURIOUS,
    ATTENTIVE,
    SOFT_CONFUSED,
    WAITING_PATIENTLY
}

fun AttentionState.toSevenAttentionVisualExpression(): SevenAttentionVisualExpression =
    when (this) {
        AttentionState.UNKNOWN -> SevenAttentionVisualExpression.WAITING
        AttentionState.FACE_ABSENT -> SevenAttentionVisualExpression.SEARCHING
        AttentionState.FACE_PRESENT -> SevenAttentionVisualExpression.CURIOUS
        AttentionState.ATTENTION_STABLE -> SevenAttentionVisualExpression.ATTENTIVE
        AttentionState.TEMPORARILY_LOST -> SevenAttentionVisualExpression.SOFT_CONFUSED
        AttentionState.ATTENTION_LOST -> SevenAttentionVisualExpression.WAITING_PATIENTLY
    }

fun AttentionSnapshot?.toSevenAttentionVisualExpression(): SevenAttentionVisualExpression =
    this?.state?.toSevenAttentionVisualExpression() ?: SevenAttentionVisualExpression.WAITING

fun resolveSevenAttentionVisualExpression(
    interactionState: BimodalInteractionState,
    attentionSnapshot: AttentionSnapshot?,
    toyVoiceSpeaking: Boolean,
    fixedTimerMode: Boolean = false,
    attentionVisualDebugEnabled: Boolean = false
): SevenAttentionVisualExpression? {
    if (fixedTimerMode) {
        return null
    }
    if (attentionVisualDebugEnabled) {
        return attentionSnapshot.toSevenAttentionVisualExpression()
    }
    if (toyVoiceSpeaking || interactionState.hasHighPriorityFlowVisual()) {
        return null
    }
    return attentionSnapshot.toSevenAttentionVisualExpression()
}

private fun BimodalInteractionState.hasHighPriorityFlowVisual(): Boolean =
    when (this) {
        BimodalInteractionState.PRESENTING_QUESTION,
        BimodalInteractionState.WAITING_FOR_RESPONSE,
        BimodalInteractionState.LISTENING,
        BimodalInteractionState.TRANSCRIBING,
        BimodalInteractionState.EVALUATING,
        BimodalInteractionState.FEEDBACK_CORRECT,
        BimodalInteractionState.FEEDBACK_INCORRECT,
        BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE,
        BimodalInteractionState.FEEDBACK_NO_RESPONSE,
        BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR,
        BimodalInteractionState.TIME_EXPIRED,
        BimodalInteractionState.SESSION_COMPLETED -> true
        BimodalInteractionState.IDLE,
        BimodalInteractionState.LOADING_ACTIVITY,
        BimodalInteractionState.READY,
        BimodalInteractionState.WAITING_FOR_FACE,
        BimodalInteractionState.PAUSED_FACE_LOST,
        BimodalInteractionState.FACE_DETECTED,
        BimodalInteractionState.NEXT_QUESTION,
        BimodalInteractionState.SESSION_CANCELLED,
        BimodalInteractionState.ERROR -> false
    }
