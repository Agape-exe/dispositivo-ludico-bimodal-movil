package com.taller.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taller.app.speech.SpeechToTextService
import com.taller.app.speech.SttState

private const val NO_START_TIME = 0L

@Composable
fun SpeechTestScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current

    var statusMessage by remember { mutableStateOf("Listo para capturar voz.") }
    var transcription by remember { mutableStateOf("Sin transcripción.") }
    var latencyMessage by remember { mutableStateOf("Latencia no medida.") }
    var sttState by remember { mutableStateOf(SttState.IDLE) }
    val startTime = remember { mutableLongStateOf(NO_START_TIME) }

    val speechToTextService = remember { SpeechToTextService(context) }

    DisposableEffect(Unit) {
        onDispose {
            speechToTextService.destroy()
        }
    }

    fun elapsedSinceStart(): Long? {
        val start = startTime.longValue
        return if (start == NO_START_TIME) null else System.currentTimeMillis() - start
    }

    fun resetForNewCapture() {
        startTime.longValue = System.currentTimeMillis()
        statusMessage = "Escuchando respuesta verbal..."
        transcription = "Esperando voz..."
        latencyMessage = "Midiendo latencia..."
    }

    fun startCapture() {
        resetForNewCapture()
        speechToTextService.startListening(
            onStateChange = { newState -> sttState = newState },
            onReady = {
                statusMessage = "Micrófono listo. Puede hablar."
            },
            onPartialResult = { partialText ->
                transcription = partialText
                statusMessage = "Transcripción parcial recibida."
            },
            onFinalResult = { finalText ->
                transcription = finalText
                statusMessage = "Transcripción final recibida."
                val elapsed = elapsedSinceStart()
                latencyMessage = if (elapsed != null) {
                    "Latencia STT (final): $elapsed ms"
                } else {
                    "No se pudo medir la latencia final."
                }
            },
            onStopped = { partialTextAtStop ->
                if (partialTextAtStop.isBlank()) {
                    transcription = "Sin transcripción."
                    statusMessage = "Captura detenida sin resultado."
                } else {
                    transcription = partialTextAtStop
                    statusMessage = "Captura detenida. Se conservó la transcripción parcial."
                }
                val elapsed = elapsedSinceStart()
                latencyMessage = if (elapsed != null) {
                    "Latencia STT (detención manual): $elapsed ms"
                } else {
                    "No se pudo medir la latencia final."
                }
            },
            onError = { error ->
                statusMessage = error
                val elapsed = elapsedSinceStart()
                latencyMessage = if (elapsed != null) {
                    "Latencia STT (error): $elapsed ms"
                } else {
                    "No se pudo medir la latencia final."
                }
            }
        )
    }

    val isListening = sttState == SttState.LISTENING || sttState == SttState.STOPPING
    val canStop = sttState == SttState.LISTENING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("← Volver")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Prueba de Speech-to-Text",
            fontSize = 20.sp
        )

        Text(
            text = statusMessage,
            modifier = Modifier.padding(top = 16.dp)
        )

        Text(
            text = "Transcripción: $transcription",
            modifier = Modifier.padding(top = 16.dp)
        )

        Text(
            text = latencyMessage,
            modifier = Modifier.padding(top = 16.dp)
        )

        Button(
            onClick = { startCapture() },
            enabled = !isListening,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(text = if (sttState == SttState.IDLE) "Iniciar captura de voz" else "Iniciar nueva captura")
        }

        Button(
            onClick = { speechToTextService.stopListening() },
            enabled = canStop,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(text = "Detener captura")
        }
    }
}
