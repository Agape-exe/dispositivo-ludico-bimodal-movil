package com.taller.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taller.app.speech.SpeechToTextService
import com.taller.app.speech.SttState

private const val NO_START_TIME = 0L

@Composable
fun SpeechTestScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current

    var sttState by remember { mutableStateOf(SttState.IDLE) }
    var partialText by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var latencyMessage by remember { mutableStateOf("") }
    val startTime = remember { mutableLongStateOf(NO_START_TIME) }

    val speechService = remember { SpeechToTextService(context) }

    DisposableEffect(Unit) {
        onDispose { speechService.destroy() }
    }

    fun elapsedMs(): Long? {
        val t = startTime.longValue
        return if (t == NO_START_TIME) null else System.currentTimeMillis() - t
    }

    fun startCapture() {
        partialText = ""
        finalText = ""
        errorMessage = ""
        latencyMessage = ""
        startTime.longValue = System.currentTimeMillis()

        speechService.startListening(
            onStateChange = { newState -> sttState = newState },
            onReady = {},
            onPartialResult = { text -> partialText = text },
            onFinalResult = { text ->
                finalText = text
                partialText = ""
                val ms = elapsedMs()
                latencyMessage = if (ms != null) "Latencia final: $ms ms" else ""
            },
            onStopped = { textAtStop ->
                if (textAtStop.isNotBlank()) {
                    partialText = textAtStop
                }
                val ms = elapsedMs()
                latencyMessage = if (ms != null) "Latencia (detención manual): $ms ms" else ""
            },
            onError = { msg ->
                errorMessage = msg
                val ms = elapsedMs()
                latencyMessage = if (ms != null) "Latencia (error): $ms ms" else ""
            }
        )
    }

    fun clearResults() {
        partialText = ""
        finalText = ""
        errorMessage = ""
        latencyMessage = ""
        sttState = SttState.IDLE
    }

    val isListening = sttState == SttState.LISTENING
    val isStopping = sttState == SttState.STOPPING
    val isActive = isListening || isStopping
    val hasResults = partialText.isNotBlank() || finalText.isNotBlank()
        || errorMessage.isNotBlank() || latencyMessage.isNotBlank()
    val canClear = !isActive && hasResults

    val statusLabel = when (sttState) {
        SttState.IDLE -> "Listo para iniciar"
        SttState.LISTENING -> "Escuchando..."
        SttState.STOPPING -> "Deteniendo captura..."
        SttState.SUCCESS -> "Transcripción completada"
        SttState.STOPPED -> if (hasResults) "Captura detenida" else "Captura detenida sin resultado"
        SttState.ERROR -> "Error de reconocimiento"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Botón volver
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("← Volver")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Título
        Text(
            text = "Prueba de voz STT",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Descripción
        Text(
            text = "Usa esta pantalla para verificar si el micrófono captura voz y si el sistema transcribe correctamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Tarjeta de estado
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(visible = isListening) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjeta de transcripción parcial
        AnimatedVisibility(visible = partialText.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transcripción parcial",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Tarjeta de transcripción final
        AnimatedVisibility(visible = finalText.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transcripción final",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = finalText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Tarjeta de latencia
        AnimatedVisibility(visible = latencyMessage.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Text(
                    text = latencyMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Tarjeta de error
        AnimatedVisibility(visible = errorMessage.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón principal dinámico
        Button(
            onClick = {
                if (isListening) {
                    speechService.stopListening()
                } else {
                    startCapture()
                }
            },
            enabled = !isStopping,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    isStopping -> "Deteniendo..."
                    isListening -> "Detener captura"
                    else -> "Iniciar captura"
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botón limpiar resultado
        OutlinedButton(
            onClick = { clearResults() },
            enabled = canClear,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Limpiar resultado")
        }
    }
}
