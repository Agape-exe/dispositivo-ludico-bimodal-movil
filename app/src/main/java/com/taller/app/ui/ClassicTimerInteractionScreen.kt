package com.taller.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.taller.app.classic.ClassicTimerProgress
import com.taller.app.classic.ClassicTimerRunner
import com.taller.app.classic.ClassicTimerState
import com.taller.app.classic.FixedTimerNeutralPhraseBank
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.mapper.toDomain
import com.taller.app.model.LearningActivity
import com.taller.app.model.LocalMediationKey
import com.taller.app.speech.SpeechToTextService
import com.taller.app.speech.SttState
import com.taller.app.voice.LocalToyVoiceProvider
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import com.taller.app.voice.ToyVoiceFallback
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.VoiceOutcome
import com.taller.app.voice.neural.AzureSpeechConfig
import com.taller.app.voice.neural.AzureSpeechVoiceProvider
import com.taller.app.voice.neural.ElevenLabsConfig
import com.taller.app.voice.neural.ElevenLabsVoiceProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val CLASSIC_LOG_TAG = "ClassicTimer"

private const val SPEECH_TIMEOUT_MIN_MS = 12_000L
private const val SPEECH_TIMEOUT_MAX_MS = 45_000L
private const val SPEECH_TIMEOUT_PER_CHAR_MS = 120L

private fun speechTimeoutMsFor(text: String): Long =
    (SPEECH_TIMEOUT_MIN_MS + text.length * SPEECH_TIMEOUT_PER_CHAR_MS)
        .coerceAtMost(SPEECH_TIMEOUT_MAX_MS)

/**
 * Transición fija corta (ms) entre rondas, tras la frase de respuesta o de
 * tiempo agotado. Mantiene el ritmo ágil sin pausas largas. No se aplica en la
 * última ronda, donde el cierre encadena directamente.
 */
private const val ROUND_TRANSITION_DELAY_MS = 800L

/**
 * Pantalla del modo clásico con temporizador fijo.
 *
 * Presenta preguntas con tiempo máximo fijo, detecta si hubo respuesta mediante STT
 * (sin evaluar semánticamente el contenido), y usa frases neutras en lugar de
 * feedback adaptativo. No invoca SemanticEvaluator en ningún momento.
 */
@Composable
fun ClassicTimerInteractionScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val activityDao = remember { db.activityDao() }
    val questionDao = remember { db.questionDao() }
    val scope = rememberCoroutineScope()

    var loadedActivity by remember { mutableStateOf<LearningActivity?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun load(id: Long) {
        scope.launch {
            isLoading = true
            infoMessage = null
            val entity = activityDao.getById(id)
            if (entity == null) {
                infoMessage = "La actividad no existe o fue eliminada."
                isLoading = false
                return@launch
            }
            val questions = questionDao.getByActivityIdOnce(id)
            if (questions.isEmpty()) {
                infoMessage = "La actividad \"${entity.name}\" no tiene preguntas."
                isLoading = false
                return@launch
            }
            loadedActivity = entity.toDomain(questions.map { it.toDomain() })
            isLoading = false
        }
    }

    LaunchedEffect(activityId) {
        if (activityId != 0L) load(activityId)
    }

    BackHandler {
        if (loadedActivity != null) {
            loadedActivity = null
            infoMessage = null
        } else {
            onBack()
        }
    }

    val active = loadedActivity
    if (active == null) {
        ClassicActivitySelector(
            activityDao = activityDao,
            isLoading = isLoading,
            infoMessage = infoMessage,
            onPick = { load(it.id) },
            onBack = onBack
        )
    } else {
        ClassicSession(
            activity = active,
            onChangeActivity = {
                loadedActivity = null
                infoMessage = null
            },
            onBack = onBack
        )
    }
}

@Composable
private fun ClassicActivitySelector(
    activityDao: com.taller.app.data.local.dao.ActivityDao,
    isLoading: Boolean,
    infoMessage: String?,
    onPick: (ActivityEntity) -> Unit,
    onBack: () -> Unit
) {
    val activities by activityDao.getAllOrderedByUpdated().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modo temporizador fijo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Selecciona una actividad para iniciar el modo de temporizador fijo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (infoMessage != null) {
            InfoBanner(infoMessage)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay actividades disponibles.\nCrea una actividad desde el panel docente.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activities, key = { it.id }) { activity ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(activity) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = activity.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tema: ${activity.topic}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Toca para iniciar el temporizador fijo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicSession(
    activity: LearningActivity,
    onChangeActivity: () -> Unit,
    onBack: () -> Unit
) {
    val runner = remember(activity) { ClassicTimerRunner() }
    val phraseBank = remember(activity) { FixedTimerNeutralPhraseBank() }

    var state by remember(activity) { mutableStateOf(runner.state) }
    var progress by remember(activity) { mutableStateOf(runner.progress) }
    var errorMessage by remember(activity) { mutableStateOf(runner.errorMessage) }

    fun sync() {
        state = runner.state
        progress = runner.progress
        errorMessage = runner.errorMessage
    }

    fun dispatch(action: () -> Unit) {
        action()
        sync()
    }

    val context = LocalContext.current

    // ----- STT -------------------------------------------------------------------
    val speechService = remember { SpeechToTextService(context) }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> audioGranted = granted }

    var sttState by remember(activity) { mutableStateOf(SttState.IDLE) }
    var sttPartial by remember(activity) { mutableStateOf("") }
    var sttFinal by remember(activity) { mutableStateOf("") }

    val answerDelivered = remember(activity) { mutableStateOf(false) }
    val hadPartialAtTimeout = remember(activity) { mutableStateOf(false) }

    fun deliverAnswer(text: String) {
        if (answerDelivered.value) return
        answerDelivered.value = true
        if (runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            dispatch { runner.onAnswerReceived() }
        }
    }

    fun startListening() {
        if (!audioGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (runner.state != ClassicTimerState.WAITING_FIXED_RESPONSE) return
        if (sttState == SttState.LISTENING || sttState == SttState.STOPPING) return

        sttPartial = ""
        sttFinal = ""
        answerDelivered.value = false
        hadPartialAtTimeout.value = false

        speechService.startListening(
            onStateChange = { sttState = it },
            onReady = {},
            onPartialResult = { text ->
                sttPartial = text
                hadPartialAtTimeout.value = text.isNotBlank()
            },
            onFinalResult = { text ->
                sttFinal = text
                sttPartial = ""
                if (text.isNotBlank()) deliverAnswer(text)
            },
            onStopped = { textAtStop ->
                if (textAtStop.isNotBlank()) {
                    sttFinal = textAtStop
                    sttPartial = ""
                    deliverAnswer(textAtStop)
                }
            },
            onError = { _ ->
                // Error de STT: no es respuesta del niño, se trata como sin respuesta
            }
        )
    }

    fun stopListening() {
        speechService.stopListening()
    }

    DisposableEffect(Unit) {
        onDispose { speechService.destroy() }
    }

    LaunchedEffect(state) {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE && sttState == SttState.LISTENING) {
            speechService.stopListening()
        }
    }

    LaunchedEffect(progress?.currentQuestionIndex) {
        sttPartial = ""
        sttFinal = ""
    }

    // ----- Voz -------------------------------------------------------------------
    val ttsStateFlow = remember { MutableStateFlow(ToySpeechState.UNINITIALIZED) }
    val voiceRepository = remember { ToyVoiceSettingsRepository(context) }
    val voiceSettings by voiceRepository.settings.collectAsState(initial = ToyVoiceSettings())
    val toySpeechService = remember { ToySpeechService(context) }
    val localVoiceProvider = remember {
        LocalToyVoiceProvider(toySpeechService, ttsStateFlow) { voiceSettings }
    }
    val azureVoiceProvider = remember {
        AzureSpeechVoiceProvider(context) { AzureSpeechConfig.fromBuild(voiceSettings.azureVoiceName) }
    }
    val elevenLabsVoiceProvider = remember {
        ElevenLabsVoiceProvider(context) { ElevenLabsConfig.from(voiceSettings.neuralVoiceId) }
    }

    DisposableEffect(Unit) {
        toySpeechService.initialize { ttsStateFlow.value = it }
        onDispose {
            toySpeechService.shutdown()
            azureVoiceProvider.release()
            elevenLabsVoiceProvider.release()
        }
    }

    var toyVoiceSpeaking by remember(activity) { mutableStateOf(false) }
    var lastSpokenPhrase by remember(activity) { mutableStateOf<String?>(null) }
    var lastVoiceProvider by remember(activity) { mutableStateOf<String?>(null) }
    var lastVoiceFallback by remember(activity) { mutableStateOf<Boolean?>(null) }

    fun recordVoice(selected: ToyVoiceProviderType, outcome: VoiceOutcome?) {
        val label: String
        val fallback: Boolean
        when (outcome) {
            is VoiceOutcome.NeuralSuccess -> { label = providerLabel(selected); fallback = false }
            is VoiceOutcome.LocalSuccess -> { label = providerLabel(ToyVoiceProviderType.LOCAL); fallback = false }
            is VoiceOutcome.FallbackUsed -> { label = providerLabel(ToyVoiceProviderType.LOCAL); fallback = true }
            is VoiceOutcome.Failed, null -> { label = "Ninguno"; fallback = false }
        }
        lastVoiceProvider = label
        lastVoiceFallback = fallback
    }

    suspend fun speakAndAwait(text: String) {
        val neural = when (voiceSettings.provider) {
            ToyVoiceProviderType.AZURE_NEURAL -> azureVoiceProvider
            ToyVoiceProviderType.ELEVENLABS -> elevenLabsVoiceProvider
            ToyVoiceProviderType.LOCAL -> localVoiceProvider
        }
        lastSpokenPhrase = text
        toyVoiceSpeaking = true
        try {
            val timeoutMs = speechTimeoutMsFor(text)
            val outcome = withTimeoutOrNull(timeoutMs) {
                runCatching {
                    ToyVoiceFallback.speak(
                        text = text,
                        useNeural = voiceSettings.provider != ToyVoiceProviderType.LOCAL,
                        allowFallback = voiceSettings.fallbackToLocal,
                        neural = neural,
                        local = localVoiceProvider
                    )
                }.getOrNull()
            }
            if (outcome == null) {
                Log.w(CLASSIC_LOG_TAG, "voz: sin resultado tras ${timeoutMs}ms, flujo continúa")
            }
            recordVoice(voiceSettings.provider, outcome)
        } finally {
            azureVoiceProvider.stop()
            elevenLabsVoiceProvider.stop()
            toySpeechService.stop()
            toyVoiceSpeaking = false
        }
    }

    // ----- Temporizador visual ---------------------------------------------------
    var timerSecondsLeft by remember(activity) { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE) return@LaunchedEffect
        val total = progress?.effectiveMaxTimeSeconds ?: 10
        timerSecondsLeft = total
        while (timerSecondsLeft > 0) {
            delay(1_000L)
            timerSecondsLeft -= 1
        }
    }

    // Temporizador de respuesta: agota el tiempo y notifica al runner
    LaunchedEffect(sttState) {
        if (sttState != SttState.LISTENING) return@LaunchedEffect
        val seconds = progress?.effectiveMaxTimeSeconds ?: 10
        delay(seconds * 1000L)
        answerDelivered.value = true
        speechService.stopListening()
        if (runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            dispatch { runner.onTimeExpired(hadPartialAtTimeout.value) }
            Log.d(CLASSIC_LOG_TAG, "timeout: hadPartial=${hadPartialAtTimeout.value}")
        }
    }

    // ----- Efectos del flujo automático ------------------------------------------

    // SESSION_STARTING: frase de apertura → presentar primera pregunta
    LaunchedEffect(state == ClassicTimerState.SESSION_STARTING) {
        if (state != ClassicTimerState.SESSION_STARTING) return@LaunchedEffect
        Log.d(CLASSIC_LOG_TAG, "SESSION_STARTING: frase apertura")
        speakAndAwait(phraseBank.getSessionStart())
        if (runner.state == ClassicTimerState.SESSION_STARTING) {
            dispatch { runner.presentCurrentQuestion() }
        }
    }

    // PRESENTING_QUESTION: frase transición + pregunta → abrir ventana de respuesta
    val presentKey = if (state == ClassicTimerState.PRESENTING_QUESTION) {
        progress?.currentQuestionIndex ?: 0
    } else {
        null
    }
    LaunchedEffect(presentKey) {
        if (presentKey == null) return@LaunchedEffect
        val isLast = progress?.isLastQuestion ?: false
        val questionText = progress?.currentQuestionText ?: return@LaunchedEffect
        val round = progress?.questionNumber ?: (presentKey + 1)
        val mediationKey = LocalMediationKey.fromKey(progress?.currentQuestionMediationKey)
        Log.d(CLASSIC_LOG_TAG, "PRESENTING_QUESTION round=$round isLast=$isLast key=$mediationKey")
        // Transición + pregunta en una sola reproducción para reducir demora.
        speakAndAwait(phraseBank.getRoundPrompt(round, questionText, isLast, mediationKey))
        if (runner.state == ClassicTimerState.PRESENTING_QUESTION) {
            dispatch { runner.startResponseWindow() }
        }
    }

    // WAITING_FIXED_RESPONSE: abrir STT
    LaunchedEffect(state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE) return@LaunchedEffect
        Log.d(CLASSIC_LOG_TAG, "WAITING_FIXED_RESPONSE: abriendo STT")
        startListening()
    }

    // ANSWER_RECEIVED: frase neutra → pausa → avanzar
    val answerKey = if (state == ClassicTimerState.ANSWER_RECEIVED) {
        progress?.currentQuestionIndex ?: 0
    } else {
        null
    }
    LaunchedEffect(answerKey) {
        if (answerKey == null) return@LaunchedEffect
        val isLast = progress?.isLastQuestion ?: false
        Log.d(CLASSIC_LOG_TAG, "ANSWER_RECEIVED idx=$answerKey isLast=$isLast")
        // Última ronda: frase de cierre de participación sin anunciar otra pregunta,
        // y avance inmediato al cierre (sin transición intermedia).
        speakAndAwait(phraseBank.getAnswerReceived(isLast))
        if (!isLast) delay(ROUND_TRANSITION_DELAY_MS)
        if (runner.state == ClassicTimerState.ANSWER_RECEIVED) {
            dispatch { runner.advanceQuestion() }
        }
    }

    // TIME_EXPIRED: frase neutra → avanzar
    val timeoutKey = if (state == ClassicTimerState.TIME_EXPIRED) {
        progress?.currentQuestionIndex ?: 0
    } else {
        null
    }
    LaunchedEffect(timeoutKey) {
        if (timeoutKey == null) return@LaunchedEffect
        val hadPartial = progress?.hadPartialResponseOnTimeout ?: false
        val isLast = progress?.isLastQuestion ?: false
        Log.d(CLASSIC_LOG_TAG, "TIME_EXPIRED idx=$timeoutKey hadPartial=$hadPartial isLast=$isLast")
        // En la última ronda la frase no anuncia otra pregunta; encadena al cierre.
        speakAndAwait(phraseBank.getTimeExpired(hadPartial, isLast))
        if (!isLast) delay(ROUND_TRANSITION_DELAY_MS)
        if (runner.state == ClassicTimerState.TIME_EXPIRED) {
            dispatch { runner.advanceQuestion() }
        }
    }

    // SESSION_COMPLETED: frase de cierre
    LaunchedEffect(state == ClassicTimerState.SESSION_COMPLETED) {
        if (state != ClassicTimerState.SESSION_COMPLETED) return@LaunchedEffect
        Log.d(CLASSIC_LOG_TAG, "SESSION_COMPLETED")
        speakAndAwait(phraseBank.getSessionCompleted())
    }

    fun startInteraction() {
        dispatch {
            runner.loadActivity(activity)
            runner.markActivityLoaded()
            runner.startSession()
        }
    }

    // ----- UI --------------------------------------------------------------------
    val sessionTerminal = state == ClassicTimerState.SESSION_COMPLETED ||
        state == ClassicTimerState.SESSION_CANCELLED ||
        state == ClassicTimerState.ERROR
    val sessionActive = state != ClassicTimerState.IDLE &&
        state != ClassicTimerState.READY &&
        !sessionTerminal

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modo temporizador fijo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Información de sesión
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InfoRow("Actividad", activity.title)
                InfoRow("Modo", "Temporizador fijo")
                InfoRow("Estado", classicStateLabel(state))
                val p = progress
                InfoRow(
                    "Progreso",
                    if (p != null) "Pregunta ${p.questionNumber} de ${p.totalQuestions}" else "Sin iniciar"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pregunta actual
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Pregunta actual",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = progress?.currentQuestionText ?: "—",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (toyVoiceSpeaking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "El juguete está hablando…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (lastSpokenPhrase != null) {
                    Text(
                        text = "Última frase: $lastSpokenPhrase",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Temporizador visual
        if (state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            val total = progress?.effectiveMaxTimeSeconds ?: 10
            val fraction = timerSecondsLeft.toFloat() / total.coerceAtLeast(1)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tiempo restante", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "$timerSecondsLeft s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (timerSecondsLeft <= 3) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Métricas técnicas
        val p = progress
        if (p != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Métricas técnicas", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    InfoRow("Tiempo asignado", "${p.effectiveMaxTimeSeconds} s")
                    InfoRow(
                        "Respuesta recibida",
                        when {
                            state == ClassicTimerState.ANSWER_RECEIVED ||
                            (state == ClassicTimerState.PRESENTING_QUESTION && p.answerReceived) -> "Sí"
                            state == ClassicTimerState.TIME_EXPIRED -> "No"
                            else -> "—"
                        }
                    )
                    InfoRow(
                        "Latencia respuesta",
                        p.responseLatencyMs?.let { "$it ms" } ?: "—"
                    )
                    InfoRow("Voz usada", lastVoiceProvider ?: "—")
                    InfoRow("Fallback de voz", when (lastVoiceFallback) {
                        true -> "Sí"; false -> "No"; null -> "—"
                    })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Transcripción (solo debug; no se usa para decidir feedback)
        if (audioGranted && (sttPartial.isNotBlank() || sttFinal.isNotBlank())) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Transcripción (depuración)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedVisibility(sttPartial.isNotBlank()) {
                        Text(
                            text = "Parcial: $sttPartial",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(sttFinal.isNotBlank()) {
                        Text(
                            text = "Final: $sttFinal",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Control principal
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Interacción",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                when (state) {
                    ClassicTimerState.IDLE,
                    ClassicTimerState.READY -> {
                        Button(
                            onClick = { startInteraction() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Iniciar actividad") }
                    }

                    ClassicTimerState.SESSION_COMPLETED,
                    ClassicTimerState.SESSION_CANCELLED,
                    ClassicTimerState.ERROR -> {
                        Text(
                            text = when (state) {
                                ClassicTimerState.SESSION_COMPLETED -> "Actividad completada."
                                ClassicTimerState.SESSION_CANCELLED -> "La interacción se canceló."
                                else -> "La interacción se detuvo por un error."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Button(
                            onClick = { startInteraction() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Iniciar de nuevo") }
                    }

                    ClassicTimerState.SESSION_STARTING,
                    ClassicTimerState.PRESENTING_QUESTION -> {
                        ClassicStatusLine(
                            "Preparando la pregunta…",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    ClassicTimerState.WAITING_FIXED_RESPONSE -> {
                        ClassicStatusLine(
                            if (audioGranted) "Escuchando la respuesta del niño…"
                            else "Temporizador activo (sin micrófono).",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (!audioGranted) {
                            OutlinedButton(
                                onClick = {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Conceder micrófono") }
                        }
                    }

                    ClassicTimerState.ANSWER_RECEIVED -> {
                        ClassicStatusLine(
                            "Respuesta recibida. Pasando a la siguiente pregunta…",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    ClassicTimerState.TIME_EXPIRED -> {
                        ClassicStatusLine(
                            "Tiempo agotado. Pasando a la siguiente pregunta…",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    ClassicTimerState.LOADING_ACTIVITY -> {
                        ClassicStatusLine("Cargando actividad…", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                if (sessionActive) {
                    OutlinedButton(
                        onClick = { dispatch { runner.cancelSession() } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar actividad") }
                }
            }
        }

        if (state == ClassicTimerState.ERROR && errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            InfoBanner("Error: $errorMessage")
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onChangeActivity,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cambiar de actividad") }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ClassicStatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun providerLabel(type: ToyVoiceProviderType): String = when (type) {
    ToyVoiceProviderType.LOCAL -> "Voz local"
    ToyVoiceProviderType.AZURE_NEURAL -> "Azure"
    ToyVoiceProviderType.ELEVENLABS -> "ElevenLabs"
}

private fun classicStateLabel(state: ClassicTimerState): String = when (state) {
    ClassicTimerState.IDLE -> "Sin iniciar"
    ClassicTimerState.LOADING_ACTIVITY -> "Cargando actividad"
    ClassicTimerState.READY -> "Lista para iniciar"
    ClassicTimerState.SESSION_STARTING -> "Iniciando sesión"
    ClassicTimerState.PRESENTING_QUESTION -> "Preparando pregunta"
    ClassicTimerState.WAITING_FIXED_RESPONSE -> "Escuchando respuesta"
    ClassicTimerState.ANSWER_RECEIVED -> "Respuesta recibida"
    ClassicTimerState.TIME_EXPIRED -> "Tiempo agotado"
    ClassicTimerState.SESSION_COMPLETED -> "Actividad completada"
    ClassicTimerState.SESSION_CANCELLED -> "Cancelado"
    ClassicTimerState.ERROR -> "Error"
}
