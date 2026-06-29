package com.taller.app.ui.seven

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta de colores de Seven.
private val SevenSpaceBackground = Color(0xFF0D1B2A)
private val SevenEyeWhiteColor = Color(0xFFF8FAFF)
private val SevenPupilColor = Color(0xFF0D1B2A)
private val SevenCheekColor = Color(0xFFFF9DB4)
private val SevenMouthColor = Color(0xFF0D1B2A)
private val SevenTextLight = Color(0xFFE8F4FF)
private val SevenCountdownColor = Color(0xFFFF9A3C)

// Iris según estado
private fun irisColorFor(state: SevenFaceState): Color = when (state) {
    SevenFaceState.Correct -> Color(0xFFFFD166)
    SevenFaceState.Closing -> Color(0xFFFFD166)
    SevenFaceState.Supportive, SevenFaceState.Retry -> Color(0xFFB57BFF)
    SevenFaceState.Thinking -> Color(0xFF7EE8A2)
    SevenFaceState.AttentionLost, SevenFaceState.Recapturing -> Color(0xFFFFAA78)
    SevenFaceState.ErrorSoft -> Color(0xFFFFBB66)
    else -> Color(0xFF5CC8FF)
}

/**
 * Rostro visual de Seven con ojos animados a pantalla completa.
 *
 * Diseñado para modo paisaje. El contenedor padre debe proveer el fondo oscuro
 * (ej. degradado SevenSpaceBackground → SevenSpaceBackgroundAlt) y ocupar toda la pantalla.
 *
 * @param state estado visual actual de Seven
 * @param modifier modificador aplicado al Box contenedor (usar fillMaxSize para pantalla completa)
 * @param countdown si no es null, muestra "Tu turno · N" en la esquina inferior derecha
 */
@Composable
fun SevenEyesFace(
    state: SevenFaceState,
    modifier: Modifier = Modifier,
    countdown: Int? = null
) {
    val transition = rememberInfiniteTransition(label = "seven-eyes")

    // Parpadeo periódico suave cada ~4 s
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3200
                0.10f at 3330
                1f at 3490
                1f at 4200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    // Pulso general de 0→1→0 cada ~900 ms (habla, escucha, correcto)
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 920),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Barrido lateral para búsqueda de atención
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1380),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    Box(modifier = modifier) {

        Canvas(modifier = Modifier.fillMaxSize()) {

            // ── Estrellas de fondo ─────────────────────────────────────────
            val starAlpha = 0.46f + pulse * 0.10f
            val stars = listOf(
                Offset(0.06f, 0.10f) to 2.6f, Offset(0.93f, 0.08f) to 2.0f,
                Offset(0.14f, 0.86f) to 2.2f, Offset(0.89f, 0.82f) to 2.8f,
                Offset(0.03f, 0.50f) to 1.6f, Offset(0.97f, 0.45f) to 2.0f,
                Offset(0.21f, 0.04f) to 1.8f, Offset(0.78f, 0.94f) to 1.6f,
                Offset(0.50f, 0.05f) to 2.4f, Offset(0.10f, 0.66f) to 1.8f,
                Offset(0.90f, 0.60f) to 1.4f, Offset(0.46f, 0.96f) to 2.0f,
                Offset(0.32f, 0.07f) to 1.4f, Offset(0.68f, 0.91f) to 1.6f
            )
            stars.forEach { (frac, radius) ->
                drawCircle(
                    color = Color.White.copy(alpha = starAlpha * (0.55f + radius * 0.12f)),
                    radius = radius,
                    center = Offset(size.width * frac.x, size.height * frac.y)
                )
            }

            // ── Geometría de ojos ──────────────────────────────────────────
            val isHappyClosed = state == SevenFaceState.Closing || state == SevenFaceState.Correct

            val openBase = when (state) {
                SevenFaceState.Listening -> 1.10f
                SevenFaceState.Intro, SevenFaceState.Correct -> 1.06f
                SevenFaceState.Speaking -> 0.94f + pulse * 0.04f
                SevenFaceState.Thinking -> 0.76f
                SevenFaceState.Supportive, SevenFaceState.Retry -> 0.88f
                SevenFaceState.Timeout, SevenFaceState.ErrorSoft -> 0.82f
                SevenFaceState.AttentionLost, SevenFaceState.Recapturing -> 0.86f
                SevenFaceState.Closing -> 0f
                else -> 0.96f
            }.coerceIn(0f, 1f)

            val eyeOpenScale = openBase * blink.coerceIn(0.68f, 1f)

            val pupilShift = when (state) {
                SevenFaceState.Thinking ->
                    Offset(size.width * 0.014f, -size.height * 0.028f)
                SevenFaceState.AttentionLost, SevenFaceState.Recapturing ->
                    Offset(sweep * size.width * 0.022f, 0f)
                SevenFaceState.Speaking ->
                    Offset((pulse - 0.5f) * size.width * 0.010f, 0f)
                SevenFaceState.ErrorSoft ->
                    Offset(-size.width * 0.013f, size.height * 0.010f)
                else -> Offset.Zero
            }

            val irisColor = irisColorFor(state)

            // Centro de cada ojo: X en 32.5% y 67.5%, Y en 38%
            val eyeCenters = listOf(
                Offset(size.width * 0.325f, size.height * 0.38f),
                Offset(size.width * 0.675f, size.height * 0.38f)
            )
            val eyeW = size.width * 0.250f
            val fullEyeH = size.height * 0.48f
            val eyeH = fullEyeH * eyeOpenScale

            eyeCenters.forEach { center ->
                if (isHappyClosed) {
                    // Ojos cerrados en arco feliz
                    val arcW = eyeW * 0.88f
                    val arcH = fullEyeH * 0.20f
                    drawArc(
                        color = SevenPupilColor,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(center.x - arcW / 2f, center.y - arcH * 0.36f),
                        size = Size(arcW, arcH),
                        style = Stroke(width = size.minDimension * 0.022f)
                    )
                    // Brillo de mejilla bajo ojo cerrado
                    drawCircle(
                        color = SevenCheekColor.copy(alpha = 0.46f + pulse * 0.10f),
                        radius = eyeW * 0.26f,
                        center = center + Offset(0f, fullEyeH * 0.22f)
                    )
                } else {
                    // Sombra bajo el ojo
                    drawOval(
                        color = Color.Black.copy(alpha = 0.14f),
                        topLeft = Offset(
                            center.x - eyeW / 2f,
                            center.y - eyeH / 2f + eyeH * 0.08f
                        ),
                        size = Size(eyeW, eyeH)
                    )
                    // Blanco del ojo
                    drawOval(
                        color = SevenEyeWhiteColor,
                        topLeft = Offset(center.x - eyeW / 2f, center.y - eyeH / 2f),
                        size = Size(eyeW, eyeH)
                    )

                    val irisCenter = center + pupilShift
                    val irisR = eyeW * 0.310f
                    val irisGlow = when (state) {
                        SevenFaceState.Speaking -> pulse * 0.14f
                        SevenFaceState.Correct -> 0.18f + pulse * 0.12f
                        SevenFaceState.Listening -> 0.06f + pulse * 0.10f
                        else -> 0f
                    }

                    // Halo del iris
                    drawCircle(
                        color = irisColor.copy(alpha = 0.16f + irisGlow),
                        radius = irisR * 1.30f,
                        center = irisCenter
                    )
                    // Iris
                    drawCircle(irisColor, irisR, irisCenter)
                    // Capa interior del iris para profundidad
                    drawCircle(
                        color = irisColor.copy(alpha = 0.50f),
                        radius = irisR * 0.70f,
                        center = irisCenter
                    )
                    // Pupila
                    drawCircle(
                        color = SevenPupilColor.copy(alpha = 0.90f),
                        radius = irisR * 0.42f,
                        center = irisCenter
                    )
                    // Brillo interior translúcido
                    drawCircle(
                        color = Color(0xFFB8EEFF).copy(alpha = 0.58f + irisGlow),
                        radius = irisR * 0.48f,
                        center = irisCenter + Offset(irisR * 0.04f, irisR * 0.04f)
                    )
                    // Reflejo principal
                    drawCircle(
                        color = Color.White.copy(alpha = 0.90f),
                        radius = irisR * 0.22f,
                        center = irisCenter + Offset(-irisR * 0.30f, -irisR * 0.36f)
                    )
                    // Reflejo secundario
                    drawCircle(
                        color = Color.White.copy(alpha = 0.66f),
                        radius = irisR * 0.11f,
                        center = irisCenter + Offset(irisR * 0.34f, -irisR * 0.10f)
                    )
                    // Destello en cruz
                    val sparkle = irisCenter + Offset(-irisR * 0.04f, irisR * 0.32f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.66f + irisGlow),
                        start = sparkle + Offset(-irisR * 0.13f, 0f),
                        end = sparkle + Offset(irisR * 0.13f, 0f),
                        strokeWidth = 2.4f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.66f + irisGlow),
                        start = sparkle + Offset(0f, -irisR * 0.13f),
                        end = sparkle + Offset(0f, irisR * 0.13f),
                        strokeWidth = 2.4f
                    )
                    // Sombra de párpado
                    drawArc(
                        color = SevenPupilColor.copy(alpha = 0.20f),
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(
                            center.x - eyeW * 0.44f,
                            center.y - fullEyeH * 0.42f
                        ),
                        size = Size(eyeW * 0.88f, fullEyeH * 0.32f),
                        style = Stroke(width = 3f)
                    )
                }
            }

            // ── Mejillas ───────────────────────────────────────────────────
            if (!isHappyClosed) {
                val cheekAlpha = when (state) {
                    SevenFaceState.Correct -> 0.46f + pulse * 0.10f
                    SevenFaceState.Supportive, SevenFaceState.Retry -> 0.38f
                    SevenFaceState.Speaking -> 0.26f + pulse * 0.08f
                    SevenFaceState.Listening -> 0.30f
                    else -> 0.20f
                }
                drawOval(
                    color = SevenCheekColor.copy(alpha = cheekAlpha),
                    topLeft = Offset(size.width * 0.130f, size.height * 0.600f),
                    size = Size(size.width * 0.108f, size.height * 0.046f)
                )
                drawOval(
                    color = SevenCheekColor.copy(alpha = cheekAlpha),
                    topLeft = Offset(size.width * 0.762f, size.height * 0.600f),
                    size = Size(size.width * 0.108f, size.height * 0.046f)
                )
            }

            // ── Boca ───────────────────────────────────────────────────────
            val mouthCX = size.width * 0.50f
            val mouthY = size.height * 0.780f
            when (state) {
                SevenFaceState.Speaking -> {
                    val h = size.height * (0.026f + pulse * 0.022f)
                    drawOval(
                        color = SevenMouthColor.copy(alpha = 0.78f),
                        topLeft = Offset(mouthCX - size.width * 0.020f, mouthY - h / 2f),
                        size = Size(size.width * 0.040f, h)
                    )
                }
                SevenFaceState.Correct, SevenFaceState.Closing -> {
                    drawArc(
                        color = SevenMouthColor,
                        startAngle = 18f,
                        sweepAngle = 144f,
                        useCenter = false,
                        topLeft = Offset(
                            mouthCX - size.width * 0.096f,
                            mouthY - size.height * 0.026f
                        ),
                        size = Size(size.width * 0.192f, size.height * 0.078f),
                        style = Stroke(width = size.minDimension * 0.014f)
                    )
                }
                SevenFaceState.Supportive, SevenFaceState.Retry -> {
                    drawArc(
                        color = SevenMouthColor.copy(alpha = 0.78f),
                        startAngle = 22f,
                        sweepAngle = 136f,
                        useCenter = false,
                        topLeft = Offset(
                            mouthCX - size.width * 0.074f,
                            mouthY - size.height * 0.020f
                        ),
                        size = Size(size.width * 0.148f, size.height * 0.058f),
                        style = Stroke(width = size.minDimension * 0.011f)
                    )
                }
                SevenFaceState.ErrorSoft -> {
                    drawLine(
                        color = SevenMouthColor.copy(alpha = 0.58f),
                        start = Offset(mouthCX - size.width * 0.028f, mouthY),
                        end = Offset(mouthCX + size.width * 0.028f, mouthY),
                        strokeWidth = size.minDimension * 0.009f
                    )
                }
                else -> {
                    drawLine(
                        color = SevenMouthColor.copy(alpha = 0.34f),
                        start = Offset(mouthCX - size.width * 0.024f, mouthY),
                        end = Offset(mouthCX + size.width * 0.024f, mouthY),
                        strokeWidth = size.minDimension * 0.006f
                    )
                }
            }
        }

        // ── Conteo regresivo (modo temporizador) ───────────────────────────
        // Se muestra en la esquina inferior derecha para no tapar los ojos
        // que quedan centrados en el área superior de la pantalla.
        if (countdown != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tu turno",
                    color = SevenTextLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = countdown.coerceAtLeast(0).toString(),
                    color = SevenCountdownColor,
                    fontSize = 72.sp,
                    lineHeight = 76.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Color exportado para que los contenedores padre construyan el mismo degradado de fondo.
val SevenSpaceBackgroundDeep = SevenSpaceBackground
val SevenSpaceBackgroundAlt = Color(0xFF162642)
