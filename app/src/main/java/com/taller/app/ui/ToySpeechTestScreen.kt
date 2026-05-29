package com.taller.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taller.app.voice.ToySpeechPhrase
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState

@Composable
fun ToySpeechTestScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var ttsState by remember { mutableStateOf(ToySpeechState.UNINITIALIZED) }
    val service = remember { ToySpeechService(context) }

    DisposableEffect(Unit) {
        service.initialize { newState -> ttsState = newState }
        onDispose { service.shutdown() }
    }

    val statusLabel = when (ttsState) {
        ToySpeechState.UNINITIALIZED -> "No inicializado"
        ToySpeechState.INITIALIZING -> "Inicializando..."
        ToySpeechState.READY -> "Listo"
        ToySpeechState.SPEAKING -> "Hablando..."
        ToySpeechState.ERROR -> "Error al inicializar la voz"
    }

    val statusColor = when (ttsState) {
        ToySpeechState.READY -> MaterialTheme.colorScheme.primary
        ToySpeechState.SPEAKING -> MaterialTheme.colorScheme.tertiary
        ToySpeechState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val buttonsEnabled = ttsState == ToySpeechState.READY || ttsState == ToySpeechState.SPEAKING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("← Volver")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Prueba de voz del juguete",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Reproduce las frases predefinidas del juguete para verificar la voz.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "Estado: $statusLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        PhraseButton(
            label = "Inicio de actividad",
            subtext = ToySpeechPhrase.ACTIVITY_START.text,
            enabled = buttonsEnabled
        ) { service.speak(ToySpeechPhrase.ACTIVITY_START) }

        PhraseButton(
            label = "Introducción de pregunta",
            subtext = ToySpeechPhrase.QUESTION_INTRO.text,
            enabled = buttonsEnabled
        ) { service.speak(ToySpeechPhrase.QUESTION_INTRO) }

        PhraseButton(
            label = "Escucha / espera",
            subtext = ToySpeechPhrase.LISTENING.text,
            enabled = buttonsEnabled
        ) { service.speak(ToySpeechPhrase.LISTENING) }

        PhraseButton(
            label = "Tiempo agotado",
            subtext = ToySpeechPhrase.TIME_EXPIRED.text,
            enabled = buttonsEnabled
        ) { service.speak(ToySpeechPhrase.TIME_EXPIRED) }

        PhraseButton(
            label = "Siguiente pregunta",
            subtext = ToySpeechPhrase.NEXT_QUESTION.text,
            enabled = buttonsEnabled
        ) { service.speak(ToySpeechPhrase.NEXT_QUESTION) }

        PhraseButton(
            label = "Finalización de actividad",
            subtext = ToySpeechPhrase.ACTIVITY_FINISHED.text,
            enabled = buttonsEnabled
        ) { service.speak(ToySpeechPhrase.ACTIVITY_FINISHED) }

        PhraseButton(
            label = "Mensaje de apoyo",
            subtext = ToySpeechPhrase.NEUTRAL_ENCOURAGEMENT.text,
            enabled = buttonsEnabled
        ) { service.speak(ToySpeechPhrase.NEUTRAL_ENCOURAGEMENT) }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { service.stop() },
            enabled = ttsState == ToySpeechState.SPEAKING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Detener")
        }
    }
}

@Composable
private fun PhraseButton(
    label: String,
    subtext: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, fontWeight = FontWeight.SemiBold)
            Text(
                text = "\"$subtext\"",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
