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
import com.taller.app.voice.InvalidToyVoiceTextReason
import com.taller.app.voice.SevenVoiceService
import com.taller.app.voice.ToyVoiceFallback
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import com.taller.app.voice.ToyVoiceInfo
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.VoiceContext
import com.taller.app.voice.VoiceOutcome
import com.taller.app.voice.VoiceMode
import com.taller.app.voice.buildVoiceProviderInfo
import com.taller.app.voice.neural.AzureSpeechConfig
import com.taller.app.voice.neural.AzureSpeechVoiceProvider
import com.taller.app.voice.neural.ElevenLabsConfig
import com.taller.app.voice.neural.ElevenLabsVoiceProvider
import com.taller.app.voice.neural.GeminiRateLimitGate
import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.GeminiTtsVoices
import com.taller.app.voice.neural.GeminiTtsVoiceProvider
import com.taller.app.voice.neural.OpenAiTtsAudioCache
import com.taller.app.voice.neural.OpenAiTtsConfig
import com.taller.app.voice.neural.OpenAiTtsVoiceProvider
import com.taller.app.voice.neural.VoiceCacheStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class PlaybackUi { IDLE, GENERATING, PLAYING }

private data class VoiceProfile(
    val name: String,
    val instructions: String
)

private val TEST_PHRASES = listOf(
    "Saludo" to "¡Hola! Soy Seven, tu amigo explorador. Hoy necesito tu ayuda para aprender cosas nuevas de la Tierra.",
    "Pregunta" to "Escucha con atención. Tengo un reto para ti: ¿qué sonido hace el perro?",
    "Feedback correcto" to "¡Muy bien! Mi nave acaba de registrar una respuesta genial.",
    "Reintento" to "Casi lo tenemos. Intentemos una vez más, explorador.",
    "Recaptura futura" to "¡Hey, explorador! Seven todavía necesita tu ayuda. Mira mi pantalla para continuar la misión.",
    "Cierre" to "¡Misión completada! Gracias por ayudarme a aprender más sobre la Tierra."
)

private val GEMINI_TEST_PHRASES = listOf(
    "Saludo" to "\u00a1Hola! Soy Seven, tu amigo explorador. Hoy necesito tu ayuda para aprender cosas nuevas de la Tierra.",
    "Pregunta" to "Escucha con atencion. Tengo un reto para ti: \u00bfque sonido hace el perro?",
    "Feedback correcto" to "\u00a1Muy bien! Mi nave registro una respuesta genial.",
    "Reintento" to "Casi lo tenemos, explorador. Intentemos una vez mas.",
    "No interpretable" to "No logre entender bien tu respuesta. Probemos otra vez.",
    "Recaptura futura" to "\u00a1Hey, explorador! Seven todavia necesita tu ayuda. Mira mi pantalla para continuar la mision.",
    "Cierre" to "\u00a1Mision completada! Gracias por ayudarme a aprender mas sobre la Tierra."
)

private val OPENAI_VOICE_PROFILES = listOf(
    VoiceProfile(
        name = "Base",
        instructions = OpenAiTtsConfig.DEFAULT_INSTRUCTIONS
    ),
    VoiceProfile(
        name = "Natural infantil",
        instructions = "Habla en español latino con voz cálida, clara y natural, como un compañero de juego amable para niños. Usa un ritmo moderado y una entonación alegre sin exagerar."
    ),
    VoiceProfile(
        name = "Más lúdica",
        instructions = "Habla en español latino con tono alegre, curioso y juguetón. Suena como un alien amigable que está emocionado por aprender con un niño. Mantén frases claras y ritmo natural."
    ),
    VoiceProfile(
        name = "Más calmada",
        instructions = "Habla en español latino con voz tranquila, cálida y paciente. Mantén una entonación amable, clara y suave, adecuada para acompañar a un niño pequeño."
    )
)

private val GEMINI_VOICE_PROFILES = listOf(
    VoiceProfile(
        name = "Seven natural",
        instructions = GeminiTtsConfig.DEFAULT_INSTRUCTIONS
    ),
    VoiceProfile(
        name = "Seven mas ludico",
        instructions = "Habla en espanol latino con tono alegre, curioso y jugueton. Suena como un pequeno alien amigable que esta emocionado por aprender con un nino. Manten frases claras y ritmo natural."
    ),
    VoiceProfile(
        name = "Seven calmado",
        instructions = "Habla en espanol latino con voz tranquila, calida y paciente. Manten una entonacion amable, clara y suave, adecuada para acompanar a un nino pequeno."
    ),
    VoiceProfile(
        name = "Seven explorador",
        instructions = "Habla en espanol latino con tono curioso, expresivo y aventurero. Suena como un alien explorador descubriendo cosas nuevas de la Tierra con ayuda de un nino."
    )
)

@Composable
fun ToyVoiceSettingsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { ToySpeechService(context) }
    val repository = remember { ToyVoiceSettingsRepository(context) }
    val voiceCache = remember { OpenAiTtsAudioCache(context) }

    val ttsStateFlow = remember { MutableStateFlow(ToySpeechState.UNINITIALIZED) }
    val ttsState by ttsStateFlow.collectAsState()
    var availableVoices by remember { mutableStateOf<List<ToyVoiceInfo>>(emptyList()) }

    var selectedVoiceName by remember { mutableStateOf<String?>(null) }
    var speechRate by remember { mutableStateOf(0.92f) }
    var pitch by remember { mutableStateOf(1.12f) }

    var providerType by remember { mutableStateOf(ToyVoiceProviderType.GEMINI_TTS) }
    var openAiVoiceName by remember { mutableStateOf("") }
    var openAiInstructions by remember { mutableStateOf(OpenAiTtsConfig.DEFAULT_INSTRUCTIONS) }
    var geminiVoiceName by remember { mutableStateOf(GeminiTtsConfig.DEFAULT_VOICE) }
    var geminiInstructions by remember { mutableStateOf(GeminiTtsConfig.DEFAULT_INSTRUCTIONS) }
    var neuralVoiceId by remember { mutableStateOf("") }
    var azureVoiceName by remember { mutableStateOf("") }
    var fallbackEnabled by remember { mutableStateOf(true) }

    var fieldsLoaded by remember { mutableStateOf(false) }
    var voicesLoaded by remember { mutableStateOf(false) }

    var playbackUi by remember { mutableStateOf(PlaybackUi.IDLE) }
    var lastOutcome by remember { mutableStateOf<VoiceOutcome?>(null) }
    var lastGeminiOutcome by remember { mutableStateOf<VoiceOutcome?>(null) }
    var geminiMessage by remember { mutableStateOf<String?>(null) }
    var selectedTestPhrase by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedGeminiTestPhrase by remember { mutableStateOf(GEMINI_TEST_PHRASES.first()) }
    var lastTestedVoice by remember { mutableStateOf<String?>(null) }
    var lastGeminiVoiceUsed by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var cacheStats by remember { mutableStateOf(voiceCache.stats()) }

    val savedSettings by repository.settings.collectAsState(initial = ToyVoiceSettings())

    fun buildCurrentSettings() = ToyVoiceSettings(
        selectedVoiceName = selectedVoiceName,
        speechRate = speechRate,
        pitch = pitch,
        provider = providerType,
        neuralVoiceId = neuralVoiceId.takeIf { it.isNotBlank() },
        openAiVoiceName = openAiVoiceName.takeIf { it.isNotBlank() },
        openAiInstructions = openAiInstructions.takeIf { it.isNotBlank() },
        geminiVoiceName = GeminiTtsVoices.normalizeId(geminiVoiceName),
        geminiInstructions = geminiInstructions.takeIf { it.isNotBlank() },
        azureVoiceName = azureVoiceName.takeIf { it.isNotBlank() },
        fallbackToLocal = fallbackEnabled
    )

    val localProvider = remember {
        LocalToyVoiceProvider(service, ttsStateFlow) { buildCurrentSettings() }
    }
    val azureProvider = remember {
        AzureSpeechVoiceProvider(context) { AzureSpeechConfig.fromBuild(azureVoiceName) }
    }
    val openAiProvider = remember {
        OpenAiTtsVoiceProvider(context) {
            OpenAiTtsConfig.fromBuild(openAiVoiceName, openAiInstructions)
        }
    }
    val geminiProvider = remember {
        GeminiTtsVoiceProvider(context) { GeminiTtsConfig.fromBuild(geminiVoiceName, geminiInstructions) }
    }
    val savedGeminiProvider = remember {
        GeminiTtsVoiceProvider(context) {
            GeminiTtsConfig.fromBuild(
                savedSettings.geminiVoiceName,
                savedSettings.geminiInstructions
            )
        }
    }
    val elevenLabsProvider = remember {
        ElevenLabsVoiceProvider(context) { ElevenLabsConfig.from(neuralVoiceId) }
    }
    val sevenVoiceService = remember {
        SevenVoiceService(
            geminiProvider = savedGeminiProvider,
            openAiProvider = openAiProvider,
            azureProvider = azureProvider,
            localProvider = localProvider,
            preferredProvider = { buildCurrentSettings().provider },
            providerInfo = { buildVoiceProviderInfo(buildCurrentSettings(), it) }
        )
    }

    val openAiApiKeyPresent = remember { OpenAiTtsConfig.apiKeyFromBuild().isNotBlank() }
    val effectiveOpenAiConfig = OpenAiTtsConfig.fromBuild(openAiVoiceName, openAiInstructions)
    val openAiConfigured = openAiApiKeyPresent

    val azureKeyPresent = remember { AzureSpeechConfig.keyFromBuild().isNotBlank() }
    val azureRegionPresent = remember { AzureSpeechConfig.regionFromBuild().isNotBlank() }
    val azureConfigured = azureKeyPresent && azureRegionPresent

    val effectiveGeminiConfig = GeminiTtsConfig.fromBuild(geminiVoiceName, geminiInstructions)
    val savedGeminiConfig = GeminiTtsConfig.fromBuild(
        savedSettings.geminiVoiceName,
        savedSettings.geminiInstructions
    )
    val geminiConfigured = remember { GeminiTtsConfig.apiKeyFromBuild().isNotBlank() }

    val elevenLabsApiKeyPresent = remember { ElevenLabsConfig.apiKeyFromBuild().isNotBlank() }
    val elevenLabsDefaultVoiceId = remember { ElevenLabsConfig.defaultVoiceIdFromBuild() }
    val effectiveElevenLabsVoiceId = neuralVoiceId.ifBlank { elevenLabsDefaultVoiceId }
    val elevenLabsConfigured = elevenLabsApiKeyPresent && effectiveElevenLabsVoiceId.isNotBlank()

    DisposableEffect(Unit) {
        service.initialize { newState -> ttsStateFlow.value = newState }
        onDispose {
            service.shutdown()
            sevenVoiceService.release()
            geminiProvider.release()
            savedGeminiProvider.release()
            elevenLabsProvider.release()
        }
    }

    LaunchedEffect(savedSettings) {
        if (!fieldsLoaded) {
            selectedVoiceName = savedSettings.selectedVoiceName
            speechRate = savedSettings.speechRate
            pitch = savedSettings.pitch
            providerType = savedSettings.provider
            openAiVoiceName = savedSettings.openAiVoiceName ?: ""
            openAiInstructions = savedSettings.openAiInstructions ?: OpenAiTtsConfig.DEFAULT_INSTRUCTIONS
            geminiVoiceName = GeminiTtsVoices.normalizeId(savedSettings.geminiVoiceName)
            geminiInstructions = savedSettings.geminiInstructions ?: GeminiTtsConfig.DEFAULT_INSTRUCTIONS
            neuralVoiceId = savedSettings.neuralVoiceId ?: ""
            azureVoiceName = savedSettings.azureVoiceName ?: ""
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

    fun saveSevenVoice() {
        val s = buildCurrentSettings()
        service.applySettings(s)
        scope.launch {
            repository.save(s)
            saveMessage = "Proveedor de voz guardado: ${providerLabel(s.provider)}"
        }
    }

    fun saveGeminiVoice() {
        val s = savedSettings.copy(
            geminiVoiceName = GeminiTtsVoices.normalizeId(geminiVoiceName),
            geminiInstructions = geminiInstructions.takeIf { it.isNotBlank() }
        )
        scope.launch {
            repository.save(s)
            saveMessage = "Voz Gemini guardada: ${GeminiTtsVoices.normalizeId(s.geminiVoiceName)}"
        }
    }

    fun playPhrase(text: String?) {
        if (playbackUi != PlaybackUi.IDLE) return
        if (text.isNullOrBlank()) {
            lastOutcome = VoiceOutcome.SkippedInvalidText(
                providerRequested = providerType,
                reason = InvalidToyVoiceTextReason.EMPTY_TEXT,
                textLength = text?.length ?: 0,
                latencyMs = 0L
            )
            return
        }
        scope.launch {
            playbackUi = PlaybackUi.GENERATING
            lastOutcome = null
            val sevenSettings = buildCurrentSettings()
            lastTestedVoice = OpenAiTtsConfig.fromBuild(
                sevenSettings.openAiVoiceName,
                sevenSettings.openAiInstructions
            ).voice
            if (sevenSettings.provider == ToyVoiceProviderType.GEMINI_TTS) {
                lastGeminiVoiceUsed = savedGeminiConfig.voiceName
            }
            val outcome = sevenVoiceService.speak(
                text = text,
                source = "configurar",
                mode = VoiceMode.CONFIGURAR,
                voiceContext = VoiceContext.TEST,
                providerOverride = sevenSettings.provider,
                onPlaybackStart = { playbackUi = PlaybackUi.PLAYING }
            )
            playbackUi = PlaybackUi.IDLE
            lastOutcome = outcome
            cacheStats = voiceCache.stats()
        }
    }

    fun playGeminiTest() {
        if (playbackUi != PlaybackUi.IDLE) return
        if (!geminiConfigured) {
            lastGeminiOutcome = VoiceOutcome.Failed(
                providerRequested = ToyVoiceProviderType.GEMINI_TTS,
                errorMessage = "Gemini TTS no esta configurado.",
                latencyMs = 0L
            )
            geminiMessage = "Falta GEMINI_API_KEY en local.properties."
            return
        }
        scope.launch {
            playbackUi = PlaybackUi.GENERATING
            lastGeminiOutcome = null
            geminiMessage = null
            lastGeminiVoiceUsed = effectiveGeminiConfig.voiceName
            val outcome = ToyVoiceFallback.speakWithFallback(
                text = selectedGeminiTestPhrase.second,
                providerRequested = ToyVoiceProviderType.GEMINI_TTS,
                providers = listOf(
                    ToyVoiceProviderType.GEMINI_TTS to geminiProvider,
                ToyVoiceProviderType.OPENAI_TTS to openAiProvider,
                ToyVoiceProviderType.AZURE_NEURAL to azureProvider,
                ToyVoiceProviderType.LOCAL to localProvider
                ),
                mode = VoiceMode.CONFIGURAR,
                voiceContext = VoiceContext.TEST,
                providerInfo = { buildVoiceProviderInfo(buildCurrentSettings(), it) },
                onPlaybackStart = { playbackUi = PlaybackUi.PLAYING }
            )
            playbackUi = PlaybackUi.IDLE
            lastGeminiOutcome = outcome
            lastOutcome = outcome
            geminiMessage = when (outcome) {
                is VoiceOutcome.Completed -> if (outcome.providerUsed == ToyVoiceProviderType.GEMINI_TTS) {
                    "Gemini TTS reproducido correctamente."
                } else {
                    val reason = safeGeminiStatusMessage(outcome.errorMessage)
                    "$reason Se uso ${providerLabel(outcome.providerUsed)} como respaldo."
                }
                is VoiceOutcome.Failed -> safeGeminiStatusMessage(outcome.errorMessage)
                is VoiceOutcome.SkippedInvalidText -> VoiceOutcome.SAFE_INVALID_TEXT_MESSAGE
            }
            cacheStats = voiceCache.stats()
        }
    }

    fun stopPlayback() {
        sevenVoiceService.stop()
        geminiProvider.stop()
        savedGeminiProvider.stop()
        elevenLabsProvider.stop()
        playbackUi = PlaybackUi.IDLE
    }

    fun clearVoiceCache() {
        stopPlayback()
        cacheStats = voiceCache.clear()
        lastOutcome = null
        lastGeminiOutcome = null
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
            saved = savedSettings.provider,
            geminiConfigured = geminiConfigured,
            openAiConfigured = openAiConfigured,
            azureConfigured = azureConfigured,
            localReady = localReady,
            geminiVoiceName = savedGeminiConfig.voiceName,
            openAiVoiceName = effectiveOpenAiConfig.voice,
            saveMessage = saveMessage,
            onSelected = {
                providerType = it
                saveMessage = null
            },
            onSave = { saveSevenVoice() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (providerType) {
            ToyVoiceProviderType.OPENAI_TTS -> OpenAiConfigSection(
                configured = openAiConfigured,
                model = effectiveOpenAiConfig.model,
                voiceName = effectiveOpenAiConfig.voice,
                currentVoice = savedSettings.openAiVoiceName ?: OpenAiTtsConfig.fromBuild().voice,
                selectedVoice = openAiVoiceName.ifBlank { effectiveOpenAiConfig.voice },
                selectedInstructions = openAiInstructions,
                lastTestedVoice = lastTestedVoice,
                lastOutcome = lastOutcome,
                cacheStats = cacheStats,
                saveMessage = saveMessage,
                fallbackEnabled = fallbackEnabled,
                onVoiceSelected = {
                    openAiVoiceName = it
                    saveMessage = null
                },
                onInstructionsSelected = {
                    openAiInstructions = it
                    saveMessage = null
                },
                onSaveVoice = { saveSevenVoice() },
                onClearCache = { clearVoiceCache() },
                onFallbackChange = {
                    fallbackEnabled = it
                    applyAndSave()
                }
            )
            ToyVoiceProviderType.AZURE_NEURAL -> AzureConfigSection(
                keyPresent = azureKeyPresent,
                regionPresent = azureRegionPresent,
                configured = azureConfigured,
                voiceName = azureVoiceName,
                fallbackEnabled = fallbackEnabled,
                onVoiceNameChange = { azureVoiceName = it },
                onVoiceNameCommit = { applyAndSave() },
                onFallbackChange = {
                    fallbackEnabled = it
                    applyAndSave()
                }
            )
            ToyVoiceProviderType.ELEVENLABS -> ElevenLabsConfigSection(
                apiKeyPresent = elevenLabsApiKeyPresent,
                configured = elevenLabsConfigured,
                voiceId = neuralVoiceId,
                defaultVoiceId = elevenLabsDefaultVoiceId,
                fallbackEnabled = fallbackEnabled,
                onVoiceIdChange = { neuralVoiceId = it },
                onVoiceIdCommit = { applyAndSave() },
                onFallbackChange = {
                    fallbackEnabled = it
                    applyAndSave()
                }
            )
            ToyVoiceProviderType.GEMINI_TTS -> Unit
            ToyVoiceProviderType.LOCAL -> Unit
        }

        if (providerType != ToyVoiceProviderType.LOCAL) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        GeminiTestSection(
            configured = geminiConfigured,
            model = effectiveGeminiConfig.model,
            currentVoice = savedGeminiConfig.voiceName,
            selectedVoice = effectiveGeminiConfig.voiceName,
            selectedInstructions = geminiInstructions,
            selectedPhrase = selectedGeminiTestPhrase,
            lastVoiceUsed = lastGeminiVoiceUsed,
            lastOutcome = lastGeminiOutcome,
            cacheStats = cacheStats,
            message = geminiMessage,
            saveMessage = saveMessage,
            enabled = playbackUi == PlaybackUi.IDLE,
            cooldownRemainingMs = lastGeminiOutcome.let { GeminiRateLimitGate.shared.remainingMs() },
            onVoiceSelected = {
                geminiVoiceName = it
                saveMessage = null
            },
            onInstructionsSelected = {
                geminiInstructions = it
                saveMessage = null
            },
            onPhraseSelected = { selectedGeminiTestPhrase = it },
            onSaveVoice = { saveGeminiVoice() },
            onClearCache = { clearVoiceCache() },
            onTest = { playGeminiTest() },
            onRetryGemini = {
                GeminiRateLimitGate.shared.clear()
                playGeminiTest()
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
            enabled = playbackUi == PlaybackUi.IDLE && (providerType != ToyVoiceProviderType.LOCAL || localReady),
            isPlaying = playbackUi != PlaybackUi.IDLE,
            selectedPhrase = selectedTestPhrase,
            onPhraseSelected = { selectedTestPhrase = it },
            onSpeak = { playPhrase(selectedTestPhrase?.second) },
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
    saved: ToyVoiceProviderType,
    geminiConfigured: Boolean,
    openAiConfigured: Boolean,
    azureConfigured: Boolean,
    localReady: Boolean,
    geminiVoiceName: String,
    openAiVoiceName: String,
    saveMessage: String?,
    onSelected: (ToyVoiceProviderType) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Proveedor de voz de Seven",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Proveedor activo actual: ${providerLabel(saved)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Seleccionado para guardar/probar: ${providerLabel(selected)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProviderStatusLine("Estado Gemini", geminiConfigured)
            ProviderStatusLine("Estado OpenAI", openAiConfigured)
            ProviderStatusLine("Estado Azure", azureConfigured)
            Text(
                text = "Estado Android local: ${if (localReady) "configurado" else "inicializando"}",
                style = MaterialTheme.typography.bodySmall,
                color = if (localReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Voz Gemini actual: $geminiVoiceName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Voz OpenAI actual: $openAiVoiceName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            ProviderOption(
                title = "Gemini TTS" + if (geminiConfigured) " - configurado" else " - no configurado",
                description = "Voz principal recomendada para Seven. Si falla, usa OpenAI, Azure y Android local.",
                isSelected = selected == ToyVoiceProviderType.GEMINI_TTS,
                onClick = { onSelected(ToyVoiceProviderType.GEMINI_TTS) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            ProviderOption(
                title = "OpenAI TTS" + if (openAiConfigured) " - configurado" else " - no configurado",
                description = "Respaldo principal de Gemini. Si falla, usa Azure y Android local.",
                isSelected = selected == ToyVoiceProviderType.OPENAI_TTS,
                onClick = { onSelected(ToyVoiceProviderType.OPENAI_TTS) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            ProviderOption(
                title = "Azure Speech" + if (azureConfigured) " - configurado" else " - no configurado",
                description = "Proveedor neural de respaldo. Si falla, usa Android local.",
                isSelected = selected == ToyVoiceProviderType.AZURE_NEURAL,
                onClick = { onSelected(ToyVoiceProviderType.AZURE_NEURAL) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            ProviderOption(
                title = "Android local",
                description = "Usa directamente el motor TTS del dispositivo, sin proveedores en red.",
                isSelected = selected == ToyVoiceProviderType.LOCAL,
                onClick = { onSelected(ToyVoiceProviderType.LOCAL) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar proveedor de voz")
            }

            if (saveMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = saveMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProviderStatusLine(label: String, configured: Boolean) {
    Text(
        text = "$label: ${if (configured) "configurado" else "no configurado"}",
        style = MaterialTheme.typography.bodySmall,
        color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
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
private fun OpenAiConfigSection(
    configured: Boolean,
    model: String,
    voiceName: String,
    currentVoice: String,
    selectedVoice: String,
    selectedInstructions: String,
    lastTestedVoice: String?,
    lastOutcome: VoiceOutcome?,
    cacheStats: VoiceCacheStats,
    saveMessage: String?,
    fallbackEnabled: Boolean,
    onVoiceSelected: (String) -> Unit,
    onInstructionsSelected: (String) -> Unit,
    onSaveVoice: () -> Unit,
    onClearCache: () -> Unit,
    onFallbackChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Voz de Seven",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (configured) "OpenAI configurado." else "OpenAI no configurado.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = if (configured) {
                    "Credencial detectada en la configuracion local."
                } else {
                    "Falta OPENAI_API_KEY en local.properties. Se usara Azure o voz local como respaldo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Proveedor principal: OpenAI TTS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Modelo: $model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Voz actual: $currentVoice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Voz seleccionada para prueba: $voiceName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Ultima voz probada: ${lastTestedVoice ?: "Ninguna"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Ultima reproduccion: ${lastOutcome?.providerUsed?.let { providerLabel(it) } ?: "Ninguna"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Fallback usado: ${lastOutcome?.let { if (it.fallbackUsed) "Si" else "No" } ?: "No"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            VoiceCacheInfoSection(
                stats = cacheStats,
                onClearCache = onClearCache
            )

            Spacer(modifier = Modifier.height(12.dp))

            OpenAiTtsConfig.SUPPORTED_VOICES.forEach { voice ->
                ProviderOption(
                    title = voice,
                    description = if (voice == OpenAiTtsConfig.DEFAULT_VOICE) {
                        "Voz recomendada inicial."
                    } else {
                        "Voz disponible para Seven."
                    },
                    isSelected = selectedVoice == voice,
                    onClick = { onVoiceSelected(voice) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Instrucciones de voz",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            OPENAI_VOICE_PROFILES.forEach { profile ->
                ProviderOption(
                    title = profile.name,
                    description = profile.instructions,
                    isSelected = selectedInstructions == profile.instructions,
                    onClick = { onInstructionsSelected(profile.instructions) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSaveVoice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar como voz de Seven")
            }

            if (saveMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = saveMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            FallbackSwitch(enabled = fallbackEnabled, onChanged = onFallbackChange)
        }
    }
}

@Composable
private fun GeminiTestSection(
    configured: Boolean,
    model: String,
    currentVoice: String,
    selectedVoice: String,
    selectedInstructions: String,
    selectedPhrase: Pair<String, String>,
    lastVoiceUsed: String?,
    lastOutcome: VoiceOutcome?,
    cacheStats: VoiceCacheStats,
    message: String?,
    saveMessage: String?,
    enabled: Boolean,
    cooldownRemainingMs: Long,
    onVoiceSelected: (String) -> Unit,
    onInstructionsSelected: (String) -> Unit,
    onPhraseSelected: (Pair<String, String>) -> Unit,
    onSaveVoice: () -> Unit,
    onClearCache: () -> Unit,
    onTest: () -> Unit,
    onRetryGemini: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Voces Gemini TTS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (configured) "Estado Gemini: configurado" else "Estado Gemini: no configurado",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = "Modelo Gemini: $model",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Voz Gemini actual: $currentVoice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Voz seleccionada: $selectedVoice - ${GeminiTtsVoices.descriptionFor(selectedVoice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Frase seleccionada: ${selectedPhrase.first}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Ultima reproduccion Gemini: ${geminiPlaybackLabel(lastOutcome)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Proveedor solicitado: ${lastOutcome?.providerRequested?.let { providerLabel(it) } ?: "Gemini TTS"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Ultimo proveedor usado: ${lastOutcome?.providerUsed?.let { providerLabel(it) } ?: "Ninguno"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Fallback usado: ${lastOutcome?.let { if (it.fallbackUsed) "Si" else "No" } ?: "No"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Voz usada: ${lastVoiceUsed ?: "Ninguna"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Desde cache: ${lastOutcome?.cacheHit?.let { if (it) "Si" else "No" } ?: "No"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (message != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastOutcome is VoiceOutcome.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } else if (!configured) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Falta GEMINI_API_KEY en local.properties.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            VoiceCacheInfoSection(
                stats = cacheStats,
                onClearCache = onClearCache
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Selector de voz Gemini",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            GeminiTtsVoices.supported.forEach { voice ->
                ProviderOption(
                    title = "${voice.displayName} - ${voice.description}",
                    description = "voiceName: ${voice.id}",
                    isSelected = selectedVoice == voice.id,
                    onClick = { onVoiceSelected(voice.id) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Perfil de estilo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            GEMINI_VOICE_PROFILES.forEach { profile ->
                ProviderOption(
                    title = profile.name,
                    description = profile.instructions,
                    isSelected = selectedInstructions == profile.instructions,
                    onClick = { onInstructionsSelected(profile.instructions) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Frase de prueba",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            GEMINI_TEST_PHRASES.forEach { phrase ->
                TestPhraseOption(
                    label = phrase.first,
                    phrase = phrase.second,
                    isSelected = selectedPhrase.first == phrase.first,
                    enabled = enabled,
                    onClick = { onPhraseSelected(phrase) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSaveVoice,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar voz Gemini")
            }

            if (saveMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = saveMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onTest,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar voz Gemini")
            }

            if (cooldownRemainingMs > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gemini en enfriamiento por limite de cuota (HTTP 429). " +
                        "Reintenta en ${(cooldownRemainingMs + 999L) / 1000L} s o pulsa el boton.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onRetryGemini,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reintentar Gemini TTS")
                }
            }
        }
    }
}

@Composable
private fun VoiceCacheInfoSection(
    stats: VoiceCacheStats,
    onClearCache: () -> Unit
) {
    Text(
        text = "Cache de voz",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Cache de voz: Activa",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Audios cacheados: ${stats.audioCount}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Tamano aproximado: ${formatCacheBytes(stats.totalBytes)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Ultima reproduccion desde cache: ${stats.lastCacheHit?.let { if (it) "Si" else "No" } ?: "No"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Proveedor de cache usado: ${stats.lastCacheProvider?.let { providerLabel(it) } ?: "Ninguno"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Audios Gemini cacheados: ${stats.geminiAudioCount}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Audios OpenAI cacheados: ${stats.openAiAudioCount}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onClearCache,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Limpiar cache de voz")
    }
}

@Composable
private fun AzureConfigSection(
    keyPresent: Boolean,
    regionPresent: Boolean,
    configured: Boolean,
    voiceName: String,
    fallbackEnabled: Boolean,
    onVoiceNameChange: (String) -> Unit,
    onVoiceNameCommit: () -> Unit,
    onFallbackChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Configuración de Azure Speech",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val statusColor = if (configured) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            Text(
                text = if (configured) "Configurado correctamente." else "Faltan credenciales.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (keyPresent) "Clave detectada en la configuración local." else "Falta AZURE_SPEECH_KEY en local.properties.",
                style = MaterialTheme.typography.bodySmall,
                color = if (keyPresent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
            Text(
                text = if (regionPresent) "Región detectada en la configuración local." else "Falta AZURE_SPEECH_REGION en local.properties.",
                style = MaterialTheme.typography.bodySmall,
                color = if (regionPresent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )

            if (!configured) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Agrega las credenciales en local.properties y recompila. Sin credenciales se usará la voz local como respaldo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = voiceName,
                onValueChange = onVoiceNameChange,
                label = { Text("Nombre de voz (opcional)") },
                placeholder = { Text("es-PE-AlexNeural") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Déjalo vacío para usar el valor de AZURE_SPEECH_VOICE o es-PE-AlexNeural por defecto.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onVoiceNameCommit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar nombre de voz")
            }

            Spacer(modifier = Modifier.height(12.dp))

            FallbackSwitch(enabled = fallbackEnabled, onChanged = onFallbackChange)
        }
    }
}

@Composable
private fun ElevenLabsConfigSection(
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
                text = "Configuración de ElevenLabs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val statusColor = if (configured) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            Text(
                text = if (configured) "Configurado." else "No configurado.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (apiKeyPresent) {
                    "Credencial detectada. Asegúrate de tener créditos disponibles."
                } else {
                    "Falta ELEVENLABS_API_KEY en local.properties."
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

            FallbackSwitch(enabled = fallbackEnabled, onChanged = onFallbackChange)
        }
    }
}

@Composable
private fun FallbackSwitch(enabled: Boolean, onChanged: (Boolean) -> Unit) {
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
        Switch(checked = enabled, onCheckedChange = onChanged)
    }
}

private fun providerLabel(type: ToyVoiceProviderType): String = when (type) {
    ToyVoiceProviderType.OPENAI_TTS -> "OpenAI TTS"
    ToyVoiceProviderType.LOCAL -> "Android local"
    ToyVoiceProviderType.AZURE_NEURAL -> "Azure Speech"
    ToyVoiceProviderType.GEMINI_TTS -> "Gemini TTS"
    ToyVoiceProviderType.ELEVENLABS -> "ElevenLabs"
}

private fun geminiPlaybackLabel(outcome: VoiceOutcome?): String = when (outcome) {
    is VoiceOutcome.Completed -> if (outcome.providerUsed == ToyVoiceProviderType.GEMINI_TTS) "exitosa" else "fallida"
    is VoiceOutcome.Failed -> "fallida"
    is VoiceOutcome.SkippedInvalidText -> "fallida"
    null -> "sin prueba"
}

private fun safeGeminiStatusMessage(message: String?): String =
    message?.takeIf { it.isNotBlank() } ?: "Gemini fallo: causa no disponible."

private fun formatCacheBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024.0)
}

@Composable
private fun PlaybackStatusCard(
    providerType: ToyVoiceProviderType,
    playbackUi: PlaybackUi,
    outcome: VoiceOutcome?,
    localState: ToySpeechState
) {
    val activeProviderLabel = when (providerType) {
        ToyVoiceProviderType.OPENAI_TTS -> "OpenAI TTS"
        ToyVoiceProviderType.LOCAL -> "Android local"
        ToyVoiceProviderType.AZURE_NEURAL -> "Azure Speech"
        ToyVoiceProviderType.GEMINI_TTS -> "Gemini TTS"
        ToyVoiceProviderType.ELEVENLABS -> "ElevenLabs"
    }

    val (statusLine, statusColor) = when (playbackUi) {
        PlaybackUi.GENERATING -> "Generando audio..." to MaterialTheme.colorScheme.tertiary
        PlaybackUi.PLAYING -> "Reproduciendo..." to MaterialTheme.colorScheme.tertiary
        PlaybackUi.IDLE -> when (outcome) {
            is VoiceOutcome.Completed -> if (outcome.fallbackUsed) {
                "Se uso ${providerLabel(outcome.providerUsed)} como respaldo." to MaterialTheme.colorScheme.tertiary
            } else {
                "Reproducido con ${providerLabel(outcome.providerUsed)}." to MaterialTheme.colorScheme.primary
            }
            is VoiceOutcome.Failed ->
                "No se pudo reproducir. (${outcome.errorMessage})" to MaterialTheme.colorScheme.error
            is VoiceOutcome.SkippedInvalidText ->
                "Selecciona una frase de prueba." to MaterialTheme.colorScheme.error
            null -> "Listo para probar." to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Proveedor activo: $activeProviderLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
            if (outcome != null && playbackUi == PlaybackUi.IDLE) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Proveedor solicitado: ${providerLabel(outcome.providerRequested)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Proveedor usado: ${outcome.providerUsed?.let { providerLabel(it) } ?: "Ninguno"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Fallback usado: ${if (outcome.fallbackUsed) "Si" else "No"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (
                    outcome.providerUsed in listOf(
                        ToyVoiceProviderType.GEMINI_TTS,
                        ToyVoiceProviderType.OPENAI_TTS
                    ) && outcome.cacheHit != null
                ) {
                    Text(
                        text = "Desde cache: ${if (outcome.cacheHit == true) "Si" else "No"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                outcome.metric?.model?.let { model ->
                    Text(
                        text = "Modelo: $model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                outcome.metric?.voice?.let { voice ->
                    Text(
                        text = "Voz: $voice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                outcome.synthesisLatencyMs?.let { synthesis ->
                    Text(
                        text = "Sintesis: $synthesis ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Latencia total: ${outcome.totalLatencyMs ?: outcome.latencyMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                outcome.errorType?.let { errorType ->
                    Text(
                        text = "Ultimo error seguro: ${errorType.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    selectedPhrase: Pair<String, String>?,
    onPhraseSelected: (Pair<String, String>) -> Unit,
    onSpeak: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Probar voz de Seven",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            TEST_PHRASES.forEach { (label, phrase) ->
                TestPhraseOption(
                    label = label,
                    phrase = phrase,
                    isSelected = selectedPhrase?.first == label,
                    enabled = enabled,
                    onClick = { onPhraseSelected(label to phrase) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onSpeak,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar voz de Seven")
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
private fun TestPhraseOption(
    label: String,
    phrase: String,
    isSelected: Boolean,
    enabled: Boolean,
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
            .padding(bottom = 8.dp)
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = phrase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
