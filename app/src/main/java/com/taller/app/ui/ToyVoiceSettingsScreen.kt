package com.taller.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taller.app.voice.ToyVoiceInfo
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import kotlinx.coroutines.launch

@Composable
fun ToyVoiceSettingsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { ToySpeechService(context) }
    val repository = remember { ToyVoiceSettingsRepository(context) }

    var ttsState by remember { mutableStateOf(ToySpeechState.UNINITIALIZED) }
    var availableVoices by remember { mutableStateOf<List<ToyVoiceInfo>>(emptyList()) }

    var selectedVoiceName by remember { mutableStateOf<String?>(null) }
    var speechRate by remember { mutableStateOf(0.92f) }
    var pitch by remember { mutableStateOf(1.12f) }
    var settingsLoaded by remember { mutableStateOf(false) }

    val savedSettings by repository.settings.collectAsState(initial = ToyVoiceSettings())

    DisposableEffect(Unit) {
        service.initialize { newState -> ttsState = newState }
        onDispose { service.shutdown() }
    }

    LaunchedEffect(ttsState, savedSettings) {
        if (ttsState == ToySpeechState.READY && !settingsLoaded) {
            selectedVoiceName = savedSettings.selectedVoiceName
            speechRate = savedSettings.speechRate
            pitch = savedSettings.pitch
            service.applySettings(savedSettings)
            availableVoices = service.getAvailableVoices()
            settingsLoaded = true
        }
    }

    fun buildCurrentSettings() = ToyVoiceSettings(
        selectedVoiceName = selectedVoiceName,
        speechRate = speechRate,
        pitch = pitch
    )

    fun applyAndSave() {
        val s = buildCurrentSettings()
        service.applySettings(s)
        scope.launch { repository.save(s) }
    }

    val canInteract = ttsState == ToySpeechState.READY || ttsState == ToySpeechState.SPEAKING

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
            text = "Configuración de voz del juguete",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Ajusta la voz local para que el juguete suene más amigable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        TtsStatusCard(ttsState = ttsState)

        if (canInteract) {
            Spacer(modifier = Modifier.height(16.dp))

            VoiceSelectionSection(
                availableVoices = availableVoices,
                selectedVoiceName = selectedVoiceName,
                onVoiceSelected = { name ->
                    selectedVoiceName = name
                    applyAndSave()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SpeechAdjustmentSection(
                speechRate = speechRate,
                pitch = pitch,
                onSpeechRateChange = {
                    speechRate = it
                    service.applySettings(buildCurrentSettings())
                },
                onSpeechRateChangeFinished = { applyAndSave() },
                onPitchChange = {
                    pitch = it
                    service.applySettings(buildCurrentSettings())
                },
                onPitchChangeFinished = { applyAndSave() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TestPhrasesSection(
                ttsState = ttsState,
                onSpeak = { text -> service.speak(text) },
                onStop = { service.stop() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val defaults = ToyVoiceSettings()
                    selectedVoiceName = defaults.selectedVoiceName
                    speechRate = defaults.speechRate
                    pitch = defaults.pitch
                    service.applySettings(defaults)
                    scope.launch { repository.reset() }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurar valores recomendados")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Las voces disponibles dependen del motor TTS instalado en el dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TtsStatusCard(ttsState: ToySpeechState) {
    val statusLabel = when (ttsState) {
        ToySpeechState.UNINITIALIZED -> "No inicializado"
        ToySpeechState.INITIALIZING -> "Inicializando motor de voz..."
        ToySpeechState.READY -> "Motor listo"
        ToySpeechState.SPEAKING -> "Reproduciendo..."
        ToySpeechState.ERROR -> "Error al inicializar la voz"
    }
    val statusColor = when (ttsState) {
        ToySpeechState.READY -> MaterialTheme.colorScheme.primary
        ToySpeechState.SPEAKING -> MaterialTheme.colorScheme.tertiary
        ToySpeechState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = "Estado: $statusLabel",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun VoiceSelectionSection(
    availableVoices: List<ToyVoiceInfo>,
    selectedVoiceName: String?,
    onVoiceSelected: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Voz disponible",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (availableVoices.isEmpty()) {
                Text(
                    text = "No se encontraron voces instaladas. El motor TTS usará la voz por defecto del dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                VoiceItem(
                    name = "Automática",
                    locale = "Idioma del sistema",
                    isNetworkRequired = false,
                    isSelected = selectedVoiceName == null,
                    onClick = { onVoiceSelected(null) }
                )

                availableVoices.forEach { voice ->
                    Spacer(modifier = Modifier.height(4.dp))
                    VoiceItem(
                        name = voice.name,
                        locale = voice.locale,
                        isNetworkRequired = voice.isNetworkRequired,
                        isSelected = voice.name == selectedVoiceName,
                        onClick = { onVoiceSelected(voice.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceItem(
    name: String,
    locale: String,
    isNetworkRequired: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = locale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isNetworkRequired) {
                    Text(
                        text = "Requiere conexión a red",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (isSelected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeechAdjustmentSection(
    speechRate: Float,
    pitch: Float,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechRateChangeFinished: () -> Unit,
    onPitchChange: (Float) -> Unit,
    onPitchChangeFinished: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ajuste de voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Velocidad: ${"%.2f".format(speechRate)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = speechRate,
                onValueChange = onSpeechRateChange,
                onValueChangeFinished = onSpeechRateChangeFinished,
                valueRange = 0.75f..1.20f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tono: ${"%.2f".format(pitch)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = pitch,
                onValueChange = onPitchChange,
                onValueChangeFinished = onPitchChangeFinished,
                valueRange = 0.85f..1.35f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TestPhrasesSection(
    ttsState: ToySpeechState,
    onSpeak: (String) -> Unit,
    onStop: () -> Unit
) {
    val buttonsEnabled = ttsState == ToySpeechState.READY

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Probar voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            TestPhraseButton(
                label = "Saludo inicial",
                phrase = "¡Hola! Vamos a jugar y aprender juntos.",
                enabled = buttonsEnabled,
                onClick = { onSpeak("¡Hola! Vamos a jugar y aprender juntos.") }
            )

            TestPhraseButton(
                label = "Atención",
                phrase = "Escucha con atención.",
                enabled = buttonsEnabled,
                onClick = { onSpeak("Escucha con atención.") }
            )

            TestPhraseButton(
                label = "Tiempo agotado",
                phrase = "Se terminó el tiempo. Pasemos a la siguiente pregunta.",
                enabled = buttonsEnabled,
                onClick = { onSpeak("Se terminó el tiempo. Pasemos a la siguiente pregunta.") }
            )

            TestPhraseButton(
                label = "Fin de actividad",
                phrase = "Terminamos la actividad. Gracias por participar.",
                enabled = buttonsEnabled,
                onClick = { onSpeak("Terminamos la actividad. Gracias por participar.") }
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onStop,
                enabled = ttsState == ToySpeechState.SPEAKING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Detener")
            }
        }
    }
}

@Composable
private fun TestPhraseButton(
    label: String,
    phrase: String,
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
                text = "\"$phrase\"",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
