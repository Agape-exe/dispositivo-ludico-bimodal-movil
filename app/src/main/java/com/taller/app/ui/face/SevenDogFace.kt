package com.taller.app.ui.face

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

// Paleta original de Seven perrito explorador: cabeza blanca, orejas negras,
// rasgos pequenos y calidos. Sin assets externos ni referencias de marca.
private val SevenDogHead = Color(0xFFFFFEFB)
private val SevenDogHeadShadow = Color(0xFF9FB4C8)
private val SevenDogHeadOutline = Color(0xFFE2DCCF)
private val SevenDogEar = Color(0xFF26231F)
private val SevenDogEye = Color(0xFF262421)
private val SevenDogNose = Color(0xFF26231F)
private val SevenDogMouth = Color(0xFF54443C)
private val SevenDogCheek = Color(0xFFF9B4C4)

/**
 * Cara final de Seven: perrito blanco tierno y minimalista con orejas negras,
 * ojos pequenos, nariz negra y boca animable.
 *
 * Composable puro de dibujo (Canvas): recibe solo el [state] visual y no
 * depende de Room, STT, GPT, TTS ni camara. Todas las medidas son relativas
 * al tamano del canvas, por lo que funciona en vertical y horizontal.
 * Las animaciones son suaves y lentas, pensadas para ninos de inicial.
 */
@Composable
fun SevenDogFace(
    state: SevenFaceState,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "seven-dog-face")

    // Parpadeo ocasional: la mayor parte del ciclo los ojos estan abiertos.
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4600
                1f at 0
                1f at 3600
                0.15f at 3760
                1f at 3920
                1f at 4600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    // Apertura de boca al hablar, en un vaiven moderado.
    val talk by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420),
            repeatMode = RepeatMode.Reverse
        ),
        label = "talk"
    )

    // Vaiven lento para respiracion, mirada pensativa y espera calmada.
    val sway by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    // Rebote pequeno para estados positivos, sin exagerar.
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = modifier) {
        val headCenter = Offset(size.width / 2f, size.height * 0.56f)
        val rx = minOf(size.width * 0.33f, size.height * 0.42f)
        val ry = rx * 0.86f

        // Movimiento global suave de la cabeza segun el estado.
        val bobY = when (state) {
            SevenFaceState.HAPPY, SevenFaceState.CLOSING -> -bounce * ry * 0.05f
            SevenFaceState.SPEAKING, SevenFaceState.INTRO -> -talk * ry * 0.012f
            else -> (sway - 0.5f) * ry * 0.024f
        }
        val tiltDegrees = when (state) {
            SevenFaceState.LISTENING -> 2.4f
            SevenFaceState.THINKING -> 1.6f
            SevenFaceState.SUPPORTIVE, SevenFaceState.RETRY -> -2.0f
            SevenFaceState.ERROR_SOFT -> -3.0f
            else -> 0f
        }

        translate(top = bobY) {
            rotate(degrees = tiltDegrees, pivot = headCenter) {
                drawSevenDog(
                    state = state,
                    center = headCenter,
                    rx = rx,
                    ry = ry,
                    blink = blink,
                    talk = talk,
                    sway = sway
                )
            }
        }
    }
}

private fun DrawScope.drawSevenDog(
    state: SevenFaceState,
    center: Offset,
    rx: Float,
    ry: Float,
    blink: Float,
    talk: Float,
    sway: Float
) {
    val cx = center.x
    val cy = center.y

    // Orejas negras tipo perrito, caidas a los lados, detras de la cabeza.
    val earWiggle = when (state) {
        SevenFaceState.SPEAKING, SevenFaceState.INTRO -> (talk - 0.5f) * 3f
        SevenFaceState.HAPPY, SevenFaceState.CLOSING -> (talk - 0.5f) * 2f
        else -> 0f
    }
    val earWidth = rx * 0.56f
    val earHeight = ry * 1.30f
    listOf(-1f, 1f).forEach { side ->
        val pivot = Offset(cx + side * rx * 0.74f, cy - ry * 0.72f)
        rotate(degrees = side * (26f + earWiggle), pivot = pivot) {
            drawOval(
                color = SevenDogEar,
                topLeft = Offset(pivot.x - earWidth / 2f, pivot.y - earHeight * 0.10f),
                size = Size(earWidth, earHeight)
            )
        }
    }

    // Sombra y cabeza blanca redondeada con contorno muy sutil.
    drawOval(
        color = SevenDogHeadShadow.copy(alpha = 0.16f),
        topLeft = Offset(cx - rx, cy - ry + ry * 0.07f),
        size = Size(rx * 2f, ry * 2f)
    )
    drawOval(
        color = SevenDogHead,
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f)
    )
    drawOval(
        color = SevenDogHeadOutline,
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = rx * 0.024f)
    )

    // Ojos pequenos y expresivos.
    val happyEyes = state == SevenFaceState.HAPPY || state == SevenFaceState.CLOSING
    val eyeDx = rx * 0.36f
    val eyeY = cy - ry * 0.08f
    val eyeScale = when (state) {
        SevenFaceState.LISTENING -> 1.15f
        SevenFaceState.THINKING -> 0.90f
        else -> 1f
    }
    val eyeShift = when (state) {
        SevenFaceState.THINKING ->
            Offset(rx * 0.04f + (sway - 0.5f) * rx * 0.05f, -ry * 0.06f)
        SevenFaceState.WAITING, SevenFaceState.IDLE ->
            Offset((sway - 0.5f) * rx * 0.020f, 0f)
        else -> Offset.Zero
    }
    listOf(-1f, 1f).forEach { side ->
        val eyeCenter = Offset(cx + side * eyeDx, eyeY) + eyeShift
        if (happyEyes) {
            // Ojos felices en arco, como sonrisa cerrada.
            val arcW = rx * 0.30f
            val arcH = ry * 0.16f
            drawArc(
                color = SevenDogEye,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(eyeCenter.x - arcW / 2f, eyeCenter.y - arcH * 0.5f),
                size = Size(arcW, arcH),
                style = Stroke(width = rx * 0.055f, cap = StrokeCap.Round)
            )
        } else {
            val confusedFactor = if (state == SevenFaceState.ERROR_SOFT && side > 0f) 0.82f else 1f
            val openHeight = ry * 0.21f * eyeScale * confusedFactor
            val eyeHeight = openHeight * blink.coerceIn(0.15f, 1f)
            val eyeWidth = openHeight * 0.62f
            drawOval(
                color = SevenDogEye,
                topLeft = Offset(eyeCenter.x - eyeWidth / 2f, eyeCenter.y - eyeHeight / 2f),
                size = Size(eyeWidth, eyeHeight)
            )
            // Brillo pequeno para dar vida sin recargar.
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = eyeWidth * 0.18f,
                center = Offset(
                    eyeCenter.x - eyeWidth * 0.14f,
                    eyeCenter.y - eyeHeight * 0.22f
                )
            )
        }
    }

    // Nariz negra pequena al centro.
    val noseW = rx * 0.20f
    val noseH = ry * 0.12f
    val noseY = cy + ry * 0.16f
    drawOval(
        color = SevenDogNose,
        topLeft = Offset(cx - noseW / 2f, noseY - noseH / 2f),
        size = Size(noseW, noseH)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.30f),
        radius = noseW * 0.14f,
        center = Offset(cx - noseW * 0.16f, noseY - noseH * 0.18f)
    )

    // Mejillas suaves, mas visibles en estados calidos.
    val cheekAlpha = when (state) {
        SevenFaceState.HAPPY, SevenFaceState.CLOSING -> 0.45f
        SevenFaceState.SUPPORTIVE, SevenFaceState.RETRY -> 0.34f
        SevenFaceState.SPEAKING, SevenFaceState.INTRO -> 0.24f
        else -> 0.16f
    }
    listOf(-1f, 1f).forEach { side ->
        drawOval(
            color = SevenDogCheek.copy(alpha = cheekAlpha),
            topLeft = Offset(cx + side * rx * 0.55f - rx * 0.11f, cy + ry * 0.16f),
            size = Size(rx * 0.22f, ry * 0.11f)
        )
    }

    // Boca simple y animable bajo la nariz.
    val mouthY = cy + ry * 0.42f
    when (state) {
        SevenFaceState.SPEAKING, SevenFaceState.INTRO -> {
            val openH = ry * (0.05f + talk * 0.11f)
            val openW = rx * 0.18f
            drawOval(
                color = SevenDogMouth,
                topLeft = Offset(cx - openW / 2f, mouthY - openH / 2f),
                size = Size(openW, openH)
            )
        }
        SevenFaceState.HAPPY, SevenFaceState.CLOSING -> {
            drawArc(
                color = SevenDogMouth,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cx - rx * 0.26f, mouthY - ry * 0.17f),
                size = Size(rx * 0.52f, ry * 0.26f),
                style = Stroke(width = rx * 0.045f, cap = StrokeCap.Round)
            )
        }
        SevenFaceState.SUPPORTIVE, SevenFaceState.RETRY -> {
            drawArc(
                color = SevenDogMouth,
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(cx - rx * 0.17f, mouthY - ry * 0.13f),
                size = Size(rx * 0.34f, ry * 0.18f),
                style = Stroke(width = rx * 0.040f, cap = StrokeCap.Round)
            )
        }
        SevenFaceState.THINKING -> {
            drawCircle(
                color = SevenDogMouth,
                radius = rx * 0.055f,
                center = Offset(cx, mouthY),
                style = Stroke(width = rx * 0.035f)
            )
        }
        SevenFaceState.ERROR_SOFT -> {
            drawLine(
                color = SevenDogMouth,
                start = Offset(cx - rx * 0.13f, mouthY + ry * 0.02f),
                end = Offset(cx + rx * 0.13f, mouthY - ry * 0.035f),
                strokeWidth = rx * 0.040f,
                cap = StrokeCap.Round
            )
        }
        SevenFaceState.IDLE,
        SevenFaceState.LISTENING,
        SevenFaceState.WAITING,
        SevenFaceState.TIMEOUT_NEUTRAL -> {
            drawArc(
                color = SevenDogMouth.copy(alpha = 0.72f),
                startAngle = 30f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - rx * 0.12f, mouthY - ry * 0.10f),
                size = Size(rx * 0.24f, ry * 0.13f),
                style = Stroke(width = rx * 0.032f, cap = StrokeCap.Round)
            )
        }
    }
}
