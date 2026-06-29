package com.taller.app.ui.seven

import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.classic.ClassicTimerState
import com.taller.app.ui.IntelligentSevenExpression

fun BimodalInteractionState.toSevenFaceState(
    facePresent: Boolean,
    toyVoiceSpeaking: Boolean
): SevenFaceState = when {
    toyVoiceSpeaking -> SevenFaceState.Speaking

    this == BimodalInteractionState.FEEDBACK_CORRECT -> SevenFaceState.Correct

    this == BimodalInteractionState.FEEDBACK_INCORRECT ||
        this == BimodalInteractionState.FEEDBACK_NO_RESPONSE ||
        this == BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR -> SevenFaceState.Supportive

    this == BimodalInteractionState.TIME_EXPIRED -> SevenFaceState.Timeout

    this == BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE -> SevenFaceState.Retry

    this == BimodalInteractionState.SESSION_COMPLETED ||
        this == BimodalInteractionState.SESSION_CANCELLED -> SevenFaceState.Closing

    this == BimodalInteractionState.WAITING_FOR_FACE ||
        this == BimodalInteractionState.PAUSED_FACE_LOST ->
        if (facePresent) SevenFaceState.Idle else SevenFaceState.AttentionLost

    this == BimodalInteractionState.FACE_DETECTED ||
        this == BimodalInteractionState.READY ||
        this == BimodalInteractionState.NEXT_QUESTION ->
        if (facePresent) SevenFaceState.Intro else SevenFaceState.AttentionLost

    this == BimodalInteractionState.PRESENTING_QUESTION -> SevenFaceState.Speaking

    this == BimodalInteractionState.WAITING_FOR_RESPONSE ||
        this == BimodalInteractionState.LISTENING -> SevenFaceState.Listening

    this == BimodalInteractionState.TRANSCRIBING ||
        this == BimodalInteractionState.EVALUATING -> SevenFaceState.Thinking

    this == BimodalInteractionState.ERROR -> SevenFaceState.ErrorSoft

    else -> SevenFaceState.Idle
}

fun ClassicTimerState.toSevenFaceState(
    toyVoiceSpeaking: Boolean,
    isPaused: Boolean
): SevenFaceState = when {
    isPaused -> SevenFaceState.Idle

    toyVoiceSpeaking ||
        this == ClassicTimerState.SESSION_STARTING ||
        this == ClassicTimerState.PRESENTING_QUESTION ||
        this == ClassicTimerState.ANSWER_RECEIVED ||
        this == ClassicTimerState.TIME_EXPIRED -> SevenFaceState.Speaking

    this == ClassicTimerState.WAITING_FIXED_RESPONSE -> SevenFaceState.Listening

    this == ClassicTimerState.SESSION_COMPLETED -> SevenFaceState.Closing

    this == ClassicTimerState.ERROR -> SevenFaceState.ErrorSoft

    this == ClassicTimerState.IDLE || this == ClassicTimerState.READY -> SevenFaceState.Intro

    else -> SevenFaceState.Idle
}

// Puente desde la expresión interna del modo inteligente al estado unificado.
// Mantiene el mecanismo de hold-time existente en BimodalInteractionScreen sin cambios.
internal fun IntelligentSevenExpression.toSevenFaceState(): SevenFaceState = when (this) {
    IntelligentSevenExpression.SEARCHING_FACE -> SevenFaceState.AttentionLost
    IntelligentSevenExpression.READY -> SevenFaceState.Idle
    IntelligentSevenExpression.SPEAKING -> SevenFaceState.Speaking
    IntelligentSevenExpression.LISTENING -> SevenFaceState.Listening
    IntelligentSevenExpression.THINKING -> SevenFaceState.Thinking
    IntelligentSevenExpression.HAPPY -> SevenFaceState.Correct
    IntelligentSevenExpression.ENCOURAGING -> SevenFaceState.Supportive
    IntelligentSevenExpression.CONFUSED -> SevenFaceState.Retry
    IntelligentSevenExpression.CELEBRATION -> SevenFaceState.Closing
}
