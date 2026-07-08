package com.taller.app.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Fondo suave y no distractor para la cara de Seven a pantalla completa.
private val SevenFaceSkyTop = Color(0xFFE7F4FB)
private val SevenFaceSkyBottom = Color(0xFFFFF6E6)
private val SevenFaceStatusText = Color(0xFF3C3A52)
private val SevenFaceCountdown = Color(0xFFF08A3C)
private val SevenFaceCountdownChip = Color(0xFFFFFFFF)

/**
 * Contenedor a pantalla completa para la cara final de Seven.
 *
 * Dibuja el fondo suave, centra [SevenDogFace] como elemento principal y deja
 * espacio para texto minimo ([statusText], por ejemplo "Tu turno"), un conteo
 * opcional ([countdownSeconds]) y contenido inferior/overlays de cada pantalla
 * (botones de salir/pausa y paneles de depuracion, que se dibujan encima).
 */
@Composable
fun SevenFaceScaffold(
    faceState: SevenFaceState,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    countdownSeconds: Int? = null,
    bottomContent: @Composable ColumnScope.() -> Unit = {},
    overlays: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SevenFaceSkyTop, SevenFaceSkyBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SevenDogFace(
                state = faceState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(0.92f)
            )
            if (countdownSeconds != null) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = SevenFaceCountdownChip.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = countdownSeconds.coerceAtLeast(0).toString(),
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 2.dp),
                        color = SevenFaceCountdown,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!statusText.isNullOrBlank()) {
                Text(
                    text = statusText,
                    color = SevenFaceStatusText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
            bottomContent()
        }

        overlays()
    }
}
