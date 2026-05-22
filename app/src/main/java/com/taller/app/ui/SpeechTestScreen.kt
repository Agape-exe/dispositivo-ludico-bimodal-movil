package com.taller.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.taller.app.speech.SpeechToTextService

@Composable
fun SpeechTestScreen() {
    val context = LocalContext.current

    val statusMessage = remember { mutableStateOf("Listo para capturar voz.") }
    val transcription = remember { mutableStateOf("Sin transcripción.") }
    val latencyMessage = remember { mutableStateOf("Latencia no medida.") }
    val startTime = remember { mutableLongStateOf(0L) }

    val speechToTextService = remember {
        SpeechToTextService(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            speechToTextService.destroy()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Prueba de Speech-to-Text")

        Text(
            text = statusMessage.value,
            modifier = Modifier.padding(top = 16.dp)
        )

        Text(
            text = "Transcripción: ${transcription.value}",
            modifier = Modifier.padding(top = 16.dp)
        )

        Text(
            text = latencyMessage.value,
            modifier = Modifier.padding(top = 16.dp)
        )

        Button(
            onClick = {
                startTime.longValue = System.currentTimeMillis()
                statusMessage.value = "Escuchando respuesta verbal..."
                transcription.value = "Esperando voz..."
                latencyMessage.value = "Midiendo latencia..."

                speechToTextService.startListening(
                    onReady = {
                        statusMessage.value = "Micrófono listo. Puede hablar."
                    },
                    onPartialResult = { partialText ->
                        transcription.value = partialText
                        statusMessage.value = "Transcripción parcial recibida."
                    },
                    onFinalResult = { finalText ->
                        val elapsedTime = System.currentTimeMillis() - startTime.longValue

                        transcription.value = finalText
                        statusMessage.value = "Transcripción final recibida."
                        latencyMessage.value = "Latencia estimada STT: ${elapsedTime} ms"
                    },
                    onError = { error ->
                        statusMessage.value = error
                        latencyMessage.value = "No se pudo medir latencia final."
                    }
                )
            },
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(text = "Iniciar captura de voz")
        }

        Button(
            onClick = {
                speechToTextService.stopListening()
                statusMessage.value = "Captura de voz detenida."
            },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(text = "Detener captura")
        }
    }
}