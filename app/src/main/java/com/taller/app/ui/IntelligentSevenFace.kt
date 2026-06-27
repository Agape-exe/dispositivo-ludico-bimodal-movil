package com.taller.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.SevenAttentionVisualExpression
import com.taller.app.attention.resolveSevenAttentionVisualExpression
import com.taller.app.bimodal.BimodalInteractionState

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

private val SevenAlienBackdrop = Color(0xFFEAF9F2)
private val SevenAlienFace = Color(0xFFFFF1B8)
private val SevenAlienFaceShade = Color(0xFFFFE58A)
private val SevenAlienDot = Color(0xFFA7E768)
private val SevenAlienDotAlt = Color(0xFF7EDDD5)
private val SevenAlienEyeWhite = Color(0xFFFFFCF4)
private val SevenAlienIris = Color(0xFF8CE05F)
private val SevenAlienPupil = Color(0xFF172B27)
private val SevenAlienMouth = Color(0xFF6B4D36)
private val SevenAlienCheek = Color(0xFFFFA9B8)

@Composable
internal fun IntelligentSevenFace(
    expression: IntelligentSevenExpression,
    modifier: Modifier = Modifier,
    faceHeight: Dp = 292.dp,
    showTurnLabel: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "intelligent-seven")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3180
                0.18f at 3300
                1f at 3440
                1f at 4200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val search by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "search"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(faceHeight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(faceHeight)
        ) {
            drawRoundRect(
                color = SevenAlienBackdrop,
                topLeft = Offset(size.width * 0.01f, size.height * 0.03f),
                size = Size(size.width * 0.98f, size.height * 0.90f),
                cornerRadius = CornerRadius(size.height * 0.18f, size.height * 0.18f)
            )

            val faceTop = size.height * 0.10f
            val faceSize = Size(size.width * 0.90f, size.height * 0.70f)
            val faceLeft = (size.width - faceSize.width) / 2f
            drawRoundRect(
                color = SevenAlienFaceShade.copy(alpha = 0.42f),
                topLeft = Offset(faceLeft, faceTop + size.height * 0.025f),
                size = faceSize,
                cornerRadius = CornerRadius(size.height * 0.17f, size.height * 0.17f)
            )
            drawRoundRect(
                color = SevenAlienFace,
                topLeft = Offset(faceLeft, faceTop),
                size = faceSize,
                cornerRadius = CornerRadius(size.height * 0.17f, size.height * 0.17f)
            )

            drawCircle(SevenAlienDot, size.minDimension * 0.035f, Offset(size.width * 0.18f, size.height * 0.22f))
            drawCircle(SevenAlienDotAlt, size.minDimension * 0.026f, Offset(size.width * 0.24f, size.height * 0.29f))
            drawCircle(SevenAlienDotAlt, size.minDimension * 0.03f, Offset(size.width * 0.77f, size.height * 0.57f))
            drawCircle(SevenAlienDot, size.minDimension * 0.022f, Offset(size.width * 0.83f, size.height * 0.47f))

            val closedHappy = expression == IntelligentSevenExpression.CELEBRATION
            val openScale = when (expression) {
                IntelligentSevenExpression.LISTENING -> 1.08f
                IntelligentSevenExpression.THINKING -> 0.82f
                IntelligentSevenExpression.ENCOURAGING -> 0.90f
                IntelligentSevenExpression.HAPPY -> 1.00f
                IntelligentSevenExpression.CONFUSED -> 0.88f
                IntelligentSevenExpression.SPEAKING -> 0.96f + pulse * 0.04f
                else -> 0.98f
            } * blink.coerceIn(0.70f, 1f)

            val pupilShift = when (expression) {
                IntelligentSevenExpression.SEARCHING_FACE -> Offset(search * size.width * 0.020f, 0f)
                IntelligentSevenExpression.SPEAKING -> Offset((pulse - 0.5f) * size.width * 0.010f, 0f)
                IntelligentSevenExpression.THINKING -> Offset(size.width * 0.012f, -size.height * 0.026f)
                IntelligentSevenExpression.CONFUSED -> Offset(-size.width * 0.012f, size.height * 0.010f)
                else -> Offset.Zero
            }

            val eyeCenters = listOf(
                Offset(size.width * 0.38f, size.height * 0.42f),
                Offset(size.width * 0.62f, size.height * 0.42f)
            )
            val eyeWidth = size.width * 0.185f
            val eyeHeight = size.height * 0.225f * openScale

            eyeCenters.forEachIndexed { index, center ->
                if (closedHappy) {
                    val arcSize = Size(eyeWidth * 0.94f, size.height * 0.12f)
                    drawArc(
                        color = SevenAlienPupil,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcSize.width / 2f, center.y - arcSize.height * 0.45f),
                        size = arcSize,
                        style = Stroke(width = size.minDimension * 0.016f)
                    )
                } else {
                    drawOval(
                        color = SevenAlienEyeWhite,
                        topLeft = Offset(center.x - eyeWidth / 2f, center.y - eyeHeight / 2f),
                        size = Size(eyeWidth, eyeHeight)
                    )
                    val sideBias = if (expression == IntelligentSevenExpression.CONFUSED && index == 1) {
                        Offset(size.width * 0.016f, -size.height * 0.006f)
                    } else {
                        Offset.Zero
                    }
                    val irisCenter = center + pupilShift + sideBias
                    val irisRadius = eyeWidth * when (expression) {
                        IntelligentSevenExpression.LISTENING -> 0.27f
                        IntelligentSevenExpression.HAPPY -> 0.30f
                        else -> 0.285f
                    }
                    drawCircle(
                        color = SevenAlienIris.copy(alpha = 0.24f + if (expression == IntelligentSevenExpression.SPEAKING) pulse * 0.14f else 0f),
                        radius = irisRadius * 1.32f,
                        center = irisCenter
                    )
                    drawCircle(SevenAlienIris, irisRadius, irisCenter)
                    drawCircle(SevenAlienPupil, irisRadius * 0.48f, irisCenter)
                    drawCircle(
                        Color.White.copy(alpha = 0.94f),
                        irisRadius * 0.22f,
                        irisCenter + Offset(-irisRadius * 0.32f, -irisRadius * 0.40f)
                    )
                    drawCircle(
                        Color.White.copy(alpha = 0.74f),
                        irisRadius * 0.11f,
                        irisCenter + Offset(irisRadius * 0.34f, -irisRadius * 0.08f)
                    )
                }
            }

            val cheekAlpha = when (expression) {
                IntelligentSevenExpression.HAPPY,
                IntelligentSevenExpression.CELEBRATION -> 0.42f
                IntelligentSevenExpression.ENCOURAGING -> 0.30f
                else -> 0.20f
            }
            drawOval(
                color = SevenAlienCheek.copy(alpha = cheekAlpha),
                topLeft = Offset(size.width * 0.30f, size.height * 0.55f),
                size = Size(size.width * 0.095f, size.height * 0.035f)
            )
            drawOval(
                color = SevenAlienCheek.copy(alpha = cheekAlpha),
                topLeft = Offset(size.width * 0.605f, size.height * 0.55f),
                size = Size(size.width * 0.095f, size.height * 0.035f)
            )

            val mouthCenter = Offset(size.width * 0.50f, size.height * 0.61f)
            when (expression) {
                IntelligentSevenExpression.SPEAKING -> {
                    val mouthHeight = size.height * (0.030f + pulse * 0.020f)
                    drawOval(
                        color = SevenAlienMouth,
                        topLeft = Offset(mouthCenter.x - size.width * 0.024f, mouthCenter.y - mouthHeight / 2f),
                        size = Size(size.width * 0.048f, mouthHeight)
                    )
                }
                IntelligentSevenExpression.HAPPY,
                IntelligentSevenExpression.CELEBRATION,
                IntelligentSevenExpression.ENCOURAGING -> {
                    drawArc(
                        color = SevenAlienMouth,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(mouthCenter.x - size.width * 0.042f, mouthCenter.y - size.height * 0.022f),
                        size = Size(size.width * 0.084f, size.height * 0.060f),
                        style = Stroke(width = size.minDimension * 0.010f)
                    )
                }
                IntelligentSevenExpression.CONFUSED -> {
                    drawLine(
                        color = SevenAlienMouth,
                        start = mouthCenter + Offset(-size.width * 0.028f, size.height * 0.006f),
                        end = mouthCenter + Offset(size.width * 0.028f, -size.height * 0.006f),
                        strokeWidth = size.minDimension * 0.009f
                    )
                }
                IntelligentSevenExpression.READY,
                IntelligentSevenExpression.LISTENING,
                IntelligentSevenExpression.THINKING,
                IntelligentSevenExpression.SEARCHING_FACE -> {
                    drawLine(
                        color = SevenAlienMouth.copy(alpha = 0.50f),
                        start = mouthCenter + Offset(-size.width * 0.022f, 0f),
                        end = mouthCenter + Offset(size.width * 0.022f, 0f),
                        strokeWidth = size.minDimension * 0.006f
                    )
                }
            }
        }

        if (showTurnLabel) {
            Text(
                text = "Tu turno",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

internal fun BimodalInteractionState.toIntelligentSevenExpression(
    facePresent: Boolean,
    toyVoiceSpeaking: Boolean,
    attentionSnapshot: AttentionSnapshot? = null,
    attentionVisualDebugEnabled: Boolean = false
): IntelligentSevenExpression = when {
    attentionVisualDebugEnabled -> resolveSevenAttentionVisualExpression(
        interactionState = this,
        attentionSnapshot = attentionSnapshot,
        toyVoiceSpeaking = toyVoiceSpeaking,
        attentionVisualDebugEnabled = true
    )?.toIntelligentSevenExpression() ?: IntelligentSevenExpression.READY

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
