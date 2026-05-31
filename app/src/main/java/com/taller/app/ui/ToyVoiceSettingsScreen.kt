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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.taller.app.voice.LocalToyVoiceProvider
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import com.taller.app.voice.ToyVoiceFallback
import com.taller.app.voice.ToyVoiceInfo
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.VoiceOutcome
import com.taller.app.voice.neural.ElevenLabsConfig
import com.taller.app.voice.neural.ElevenLabsVoiceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class PlaybackUi { IDLE, GENERATING, PLAYING }

private val TEST_PHRASES = listOf(
    "Saludo inicial" to "¡Hola! Vamos a jugar y aprender juntos.",
    "Atención" to "Escucha con atención esta pregunta.",
    "Tiempo agotado" to "Se terminó el tiempo. Pasemos a la siguiente pregunta.",
    "Fin de actividad" to "Terminamos la actividad. Gracias por participar."
)

@Composable
fun ToyVoiceSettingsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { ToySpeechService(context) }
    val repository = remember { ToyVoiceSettingsRepository(context) }

    val ttsStateFlow = remember { MutableStateFlow(ToySpeechState.UNINITIALIZED) }
    val ttsState by ttsStateFlow.collectAsState()
    var availableVoices by remember { mutableStateOf<List<ToyVoiceInfo>>(emptyList()) }

    var selectedVoiceName by remember { mutableStateOf<String?>(null) }
    var speechRate by remember { mutableStateOf(0.92f) }
    var pitch by remember { mutableStateOf(1.12f) }

    var providerType by remember { mutableStateOf(ToyVoiceProviderType.LOCAL) }
    var neuralVoiceId by remember { mutableStateOf("") }
    var fallbackEnabled by remember { mutableStateOf(true) }

    var fieldsLoaded by remember { mutableStateOf(false) }
    var voicesLoaded by remember { mutableStateOf(false) }

    var playbackUi by remember { mutableStateOf(PlaybackUi.IDLE) }
    var lastOutcome by remember { mutableStateOf<VoiceOutcome?>(null) }

    val savedSettings by repository.settings.collectAsState(initial = ToyVoiceSettings())

    fun buildCurrentSettings() = ToyVoiceSettings(
        selectedVoiceName = selectedVoiceName,
        speechRate = speechRate,
        pitch = pitch,
        provider = providerType,
        neuralVoiceId = neuralVoiceId.takeIf { it.isNotBlank() },
        fallbackToLocal = fallbackEnabled
    )

    val localProvider = remember {
        LocalToyVoiceProvider(service, ttsStateFlow) { buildCurrentSettings() }
    }
    val neuralProvider = remember {
        ElevenLabsVoiceProvider(context) { ElevenLabsConfig.from(neuralVoiceId) }
    }

    val apiKeyPresent = remember { ElevenLabsConfig.apiKeyFromBuild().isNotBlank() }
    val defaultVoiceId = remember { ElevenLabsConfig.defaultVoiceIdFromBuild() }
    val effectiveVoiceId = neuralVoiceId.ifBlank { defaultVoiceId }
    val neuralConfigured = apiKeyPresent && effectiveVoiceId.isNotBlank()

    DisposableEffect(Unit) {
        service.initialize { newState -> ttsStateFlow.value = newState }
        onDispose {
            service.shutdown()
            neuralProvider.release()
        }
    }

    LaunchedEffect(savedSettings) {
        if (!fieldsLoaded) {
            selectedVoiceName = savedSettings.selectedVoiceName
            speechRate = savedSettings.speechRate
            pitch = savedSettings.pitch
            providerType = savedSettings.provider
            neuralVoiceId = savedSettings.neuralVoiceId ?: ""
            fallbackEnabled = savedSettings.fallbackToLocal
            fieldsLoaded = true
        }
    }

    LaunchedEffect(ttsState, fieldsLoaded) {
        if (ttsState == ToySpeechState.READY && fieldsLoaded && !voicesLoaded) {
            service.applySettings(buildCurrentSettings())
            availableVoices = service.getAvailableVoices()
            voicesLoaded = true
        }
    }

    fun applyAndSave() {
        val s = buildCurrentSettings()
        service.applySettings(s)
        scope.launch { repository.save(s) }
    }

    fun playPhrase(text: String) {
        if (playbackUi != PlaybackUi.IDLE) return
        scope.launch {
            playbackUi = PlaybackUi.GENERATING
            lastOutcome = null
            val outcome = ToyVoiceFallback.speak(
                text = text,
                useNeural = providerType == ToyVoiceProviderType.NEURAL,
                allowFallback = fallbackEnabled,
                neural = neuralProvider,
                local = localProvider,
                onPlaybackStart = { playbackUi = PlaybackUi.PLAYING }
            )
            playbackUi = PlaybackUi.IDLE
            lastOutcome = outcome
        }
    }

    fun stopPlayback() {
        neuralProvider.stop()
        service.stop()
        playbackUi = PlaybackUi.IDLE
    }

    val localReady = ttsState == ToySpeechState.READY || ttsState == ToySpeechState.SPEAKING

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
            text = "Elige el proveedor de voz y ajústalo para que el juguete suene más amigable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        ProviderSelectionSection(
            selected = providerType,
            onSelected = {
                providerType = it
                applyAndSave()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        NeuralConfigSection(
            apiKeyPresent = apiKeyPresent,
            configured = neuralConfigured,
            voiceId = neuralVoiceId,
            defaultVoiceId = defaultVoiceId,
            fallbackEnabled = fallbackEnabled,
            onVoiceIdChange = { neuralVoiceId = it },
            onVoiceIdCommit = { applyAndSave() },
            onFallbackChange = {
                fallbackEnabled = it
                applyAndSave()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PlaybackStatusCard(
            providerType = providerType,
            playbackUi = playbackUi,
            outcome = lastOutcome,
            localState = ttsState
        )

        Spacer(modifier = Modifier.height(16.dp))

        TestPhrasesSection(
            enabled = playbackUi == PlaybackUi.IDLE && (providerType == ToyVoiceProviderType.NEURAL || localReady),
            isPlaying = playbackUi != PlaybackUi.IDLE,
            onSpeak = { text -> playPhrase(text) },
            onStop = { stopPlayback() }
        )

        if (localReady && voicesLoaded) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ajustes de la voz local",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            OutlinedButton(
                onClick = {
                    val defaults = ToyVoiceSettings()
                    selectedVoiceName = defaults.selectedVoiceName
                    speechRate = defaults.speechRate
                    pitch = defaults.pitch
                    service.applySettings(buildCurrentSettings())
                    scope.launch { repository.save(buildCurrentSettings()) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurar valores recomendados de la voz local")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Las voces locales disponibles dependen del motor TTS instalado en el dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderSelectionSection(
    selected: ToyVoiceProviderType,
    onSelected: (ToyVoiceProviderType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Proveedor de voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProviderOption(
                title = "Voz local (sin conexión)",
                description = "Usa el motor de voz del dispositivo. Funciona siempre, sin internet.",
                isSelected = selected == ToyVoiceProviderType.LOCAL,
                onClick = { onSelected(ToyVoiceProviderType.LOCAL) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            ProviderOption(
                title = "Voz neural (más natural)",
                description = "Genera audio más expresivo por internet. Requiere configuración.",
                isSelected = selected == ToyVoiceProviderType.NEURAL,
                onClick = { onSelected(ToyVoiceProviderType.NEURAL) }
            )
        }
    }
}

@Composable
private fun ProviderOption(
    title: String,
    description: String,
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
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
private fun NeuralConfigSection(
    apiKeyPresent: Boolean,
    configured: Boolean,
    voiceId: String,
    defaultVoiceId: String,
    fallbackEnabled: Boolean,
    onVoiceIdChange: (String) -> Unit,
    onVoiceIdCommit: () -> Unit,
    onFallbackChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Configuración de voz neural",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val statusLabel = if (configured) "Configurado" else "No configurado"
            val statusColor = if (configured) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            Text(
                text = "Estado del proveedor neural: $statusLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (apiKeyPresent) {
                    "Credencial detectada en la configuración local."
                } else {
                    "Falta la credencial. Configúrala localmente (local.properties o variable de entorno) y vuelve a compilar."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = voiceId,
                onValueChange = onVoiceIdChange,
                label = { Text("Identificador de voz (voiceId)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            val helperText = if (defaultVoiceId.isNotBlank() && voiceId.isBlank()) {
                "Se usará el voiceId por defecto de la configuración local."
            } else {
                "Déjalo vacío para usar el valor por defecto de la configuración local."
            }
            Text(
                text = helperText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onVoiceIdCommit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar identificador de voz")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Respaldo automático a voz local",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Si la voz neural falla, se usa la voz local.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = fallbackEnabled, onCheckedChange = onFallbackChange)
            }
        }
    }
}

@Composable
private fun PlaybackStatusCard(
    providerType: ToyVoiceProviderType,
    playbackUi: PlaybackUi,
    outcome: VoiceOutcome?,
    localState: ToySpeechState
) {
    val providerLabel = when (providerType) {
        ToyVoiceProviderType.LOCAL -> "Voz local"
        ToyVoiceProviderType.NEURAL -> "Voz neural"
    }

    val (statusLine, statusColor) = when (playbackUi) {
        PlaybackUi.GENERATING -> "Generando audio..." to MaterialTheme.colorScheme.tertiary
        PlaybackUi.PLAYING -> "Reproduciendo..." to MaterialTheme.colorScheme.tertiary
        PlaybackUi.IDLE -> when (outcome) {
            is VoiceOutcome.NeuralSuccess ->
                "Reproducido con voz neural." to MaterialTheme.colorScheme.primary
            is VoiceOutcome.LocalSuccess ->
                "Reproducido con voz local." to MaterialTheme.colorScheme.primary
            is VoiceOutcome.FallbackUsed ->
                "Se usó la voz local como respaldo. (${outcome.reason})" to MaterialTheme.colorScheme.tertiary
            is VoiceOutcome.Failed ->
                "No se pudo reproducir. (${outcome.reason})" to MaterialTheme.colorScheme.error
            null -> "Listo para probar." to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Proveedor activo: $providerLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
            if (localState == ToySpeechState.ERROR) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "El motor de voz local reportó un error en este dispositivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TestPhrasesSection(
    enabled: Boolean,
    isPlaying: Boolean,
    onSpeak: (String) -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Probar frases del juguete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            TEST_PHRASES.forEach { (label, phrase) ->
                TestPhraseButton(
                    label = label,
                    phrase = phrase,
                    enabled = enabled,
                    onClick = { onSpeak(phrase) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onStop,
                enabled = isPlaying,
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
                text = "Voz local disponible",
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
                text = "Ajuste de la voz local",
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
