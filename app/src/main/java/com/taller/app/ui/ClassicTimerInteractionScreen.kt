package com.taller.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.taller.app.voice.SevenVoiceService
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.VoiceOutcome
import com.taller.app.voice.neural.AzureSpeechConfig
import com.taller.app.voice.neural.AzureSpeechVoiceProvider
import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.GeminiTtsVoiceProvider
import com.taller.app.voice.neural.OpenAiTtsConfig
import com.taller.app.voice.neural.OpenAiTtsVoiceProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.taller.app.logger.InteractionDataLogger
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer

private const val CLASSIC_LOG_TAG = "ClassicTimer"

private val ClassicTimerOrange = Color(0xFFF29A00)
private val ClassicTimerCardYellow = Color(0xFFFFF0A6)
private val ClassicTimerTitleText = Color(0xFFF29A00)
private val ClassicTimerPrimaryText = Color(0xFF2E2535)
private val ClassicTimerSecondaryText = Color(0xFF3F3A4A)
private val ClassicTimerBackground = Color(0xFFFFFFFF)
private val SevenFaceBackground = Color(0xFFF6F1FF)
private val SevenFaceBackgroundAlt = Color(0xFFEAFBFA)
private val SevenFaceAccent = Color(0xFF7C4DFF)
private val SevenCountdownOrange = Color(0xFFFF8A00)
private val SevenEyeWhite = Color(0xFFFFFCF6)
private val SevenEyeIris = Color(0xFF4B74A8)
private val SevenEyePupil = Color(0xFF183247)
private val SevenEyeLid = Color(0xFFBFA8F4)
private val SevenCheek = Color(0xFFFFA7C2)

private enum class SevenVisualState {
    IDLE,
    WAITING_START_COMMAND,
    SPEAKING,
    COUNTDOWN,
    LISTENING,
    PAUSED,
    COMPLETED,
    ERROR
}

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

private fun normalizeSevenCommand(text: String): String {
    val withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    return withoutMarks
        .lowercase()
        .replace("[^a-z0-9ñ ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

private fun isSevenStartCommand(text: String): Boolean {
    val normalized = normalizeSevenCommand(text)
    if (!normalized.split(" ").contains("seven")) return false
    return normalized.contains("hora de empezar") ||
        normalized.contains("empieza") ||
        normalized.contains("iniciar") ||
        normalized.contains("comencemos") ||
        normalized.contains("empecemos")
}

private fun isSevenPauseCommand(text: String): Boolean {
    val normalized = normalizeSevenCommand(text)
    val words = normalized.split(" ").filter { it.isNotBlank() }
    if (!words.contains("seven")) return false
    val pauseWords = setOf("pausa", "pausar", "para", "parar", "detente", "alto")
    return words.any { it in pauseWords }
}

private fun isSevenResumeCommand(text: String): Boolean {
    val normalized = normalizeSevenCommand(text)
    if (!normalized.split(" ").contains("seven")) return false
    return normalized.contains("continua") ||
        normalized.contains("continuar") ||
        normalized.contains("seguimos")
}

/**
 * Pantalla del modo clásico con temporizador fijo.
 *
 * Presenta preguntas con tiempo máximo fijo, detecta si hubo respuesta mediante STT
 * (sin evaluar semánticamente el contenido), y usa frases neutras en lugar de
 * feedback adaptativo. No invoca SemanticEvaluator en ningún momento.
 */
@Composable
fun ClassicTimerInteractionScreen(onStartActivity: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val activityDao = remember { db.activityDao() }

    ClassicActivitySelector(
        activityDao = activityDao,
        infoMessage = null,
        onPick = { onStartActivity(it.id) },
        onBack = onBack
    )
}

@Composable
fun ClassicSevenFaceScreen(
    activityId: Long,
    onChangeActivity: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val activityDao = remember { db.activityDao() }
    val questionDao = remember { db.questionDao() }
    val dataLogger = remember {
        InteractionDataLogger(db.sessionDao(), db.attemptDao(), db.technicalEventDao())
    }
    val scope = rememberCoroutineScope()

    var loadedActivity by remember { mutableStateOf<LearningActivity?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            isLoading = true
            infoMessage = null
            loadedActivity = null
            val id = activityId
            if (id == 0L) {
                infoMessage = "No se recibio una actividad para iniciar."
                isLoading = false
                return@launch
            }
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
        load()
    }

    val active = loadedActivity
    when {
        active != null -> {
            ClassicSession(
                activity = active,
                dataLogger = dataLogger,
                onChangeActivity = onChangeActivity,
                onBack = onBack
            )
        }
        else -> {
            ClassicSevenLoadingOrError(
                isLoading = isLoading,
                infoMessage = infoMessage,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun ClassicSevenLoadingOrError(
    isLoading: Boolean,
    infoMessage: String?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SevenFaceBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SevenFace(
                state = SevenVisualState.ERROR,
                modifier = Modifier.size(220.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (isLoading) {
                CircularProgressIndicator(color = SevenFaceAccent)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Cargando actividad...",
                    color = ClassicTimerPrimaryText,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = infoMessage ?: "No se pudo abrir la actividad.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onBack) {
                    Text("Volver")
                }
            }
        }
    }
}

@Composable
private fun ClassicActivitySelector(
    activityDao: com.taller.app.data.local.dao.ActivityDao,
    infoMessage: String?,
    onPick: (ActivityEntity) -> Unit,
    onBack: () -> Unit
) {
    val activities by activityDao.getAllOrderedByUpdated().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ClassicTimerBackground)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Modo\nTemporizador",
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            color = ClassicTimerTitleText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Selecciona una actividad\npara iniciar el flujo",
            fontSize = 17.sp,
            lineHeight = 23.sp,
            color = ClassicTimerSecondaryText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (infoMessage != null) {
            InfoBanner(infoMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No hay actividades disponibles.",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClassicTimerPrimaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Crea una actividad desde el Panel Docente para iniciar el temporizador.",
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = ClassicTimerSecondaryText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(activities, key = { it.id }) { activity ->
                    ClassicActivityCard(
                        activity = activity,
                        onStart = { onPick(activity) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        BottomBackButton(onBack = onBack)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ClassicActivityCard(
    activity: ActivityEntity,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.86f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ClassicTimerCardYellow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = activity.name,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClassicTimerPrimaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = activity.topic,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = ClassicTimerSecondaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            ClassicStartButton(onStart = onStart)
        }
    }
}

@Composable
private fun ClassicStartButton(onStart: () -> Unit) {
    Button(
        onClick = onStart,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ClassicTimerOrange,
            contentColor = Color.White
        ),
        modifier = Modifier.width(128.dp)
    ) {
        Text(
            text = "Iniciar",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BottomBackButton(onBack: () -> Unit) {
    Button(
        onClick = onBack,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ClassicTimerOrange,
            contentColor = Color.White
        ),
        modifier = Modifier.width(148.dp)
    ) {
        Text(
            text = "Volver",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ClassicSession(
    activity: LearningActivity,
    dataLogger: InteractionDataLogger,
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

    val scope = rememberCoroutineScope()
    var logSessionId by remember(activity) { mutableStateOf(-1L) }
    var logAttemptId by remember(activity) { mutableStateOf(-1L) }
    var classicTotalAttempts by remember(activity) { mutableStateOf(0) }
    var classicNoResponse by remember(activity) { mutableStateOf(0) }
    var classicTimeouts by remember(activity) { mutableStateOf(0) }

    val context = LocalContext.current
    ClassicImmersiveSystemBarsEffect(context)

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
    var startCommandHint by remember(activity) { mutableStateOf("Di: Seven, hora de empezar") }
    var startCommandDetected by remember(activity) { mutableStateOf(false) }
    var isPausedByTeacher by remember(activity) { mutableStateOf(false) }
    var resumeToken by remember(activity) { mutableIntStateOf(0) }
    var startCommandLogPending by remember(activity) { mutableStateOf(false) }

    val answerDelivered = remember(activity) { mutableStateOf(false) }
    val hadPartialAtTimeout = remember(activity) { mutableStateOf(false) }

    fun logClassicTechnicalEvent(eventType: String) {
        val sid = logSessionId
        if (sid <= 0L) return
        scope.launch {
            runCatching {
                dataLogger.logTechnicalEvent(
                    sessionId = sid,
                    questionId = progress?.currentQuestionId?.toLongOrNull(),
                    attemptId = if (logAttemptId > 0L) logAttemptId else null,
                    operationMode = "CLASSIC",
                    eventType = eventType
                )
            }
        }
    }

    fun pauseByTeacher() {
        if (isPausedByTeacher) return
        isPausedByTeacher = true
        if (sttState == SttState.LISTENING) speechService.stopListening()
        logClassicTechnicalEvent("CLASSIC_PAUSED_BY_TEACHER")
    }

    fun resumeByTeacher() {
        if (!isPausedByTeacher) return
        isPausedByTeacher = false
        resumeToken += 1
        logClassicTechnicalEvent("CLASSIC_RESUMED_BY_TEACHER")
    }

    fun deliverAnswer(text: String) {
        if (answerDelivered.value) return
        if (isPausedByTeacher) return
        if (isSevenPauseCommand(text)) {
            pauseByTeacher()
            return
        }
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
        if (runner.state != ClassicTimerState.WAITING_FIXED_RESPONSE || isPausedByTeacher) return
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
                if (isSevenPauseCommand(text)) {
                    pauseByTeacher()
                } else {
                    hadPartialAtTimeout.value = text.isNotBlank()
                }
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

    fun startCommandListening() {
        if (!audioGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (startCommandDetected || isPausedByTeacher) return
        if (runner.state != ClassicTimerState.IDLE && runner.state != ClassicTimerState.READY) return
        if (sttState == SttState.LISTENING || sttState == SttState.STOPPING) return

        speechService.startListening(
            onStateChange = { sttState = it },
            onReady = {},
            onPartialResult = { text ->
                if (isSevenStartCommand(text)) {
                    startCommandDetected = true
                    startCommandLogPending = true
                    speechService.stopListening()
                }
            },
            onFinalResult = { text ->
                if (isSevenStartCommand(text)) {
                    startCommandDetected = true
                    startCommandLogPending = true
                } else {
                    startCommandHint = "Di: Seven, hora de empezar"
                }
            },
            onStopped = { textAtStop ->
                if (isSevenStartCommand(textAtStop)) {
                    startCommandDetected = true
                    startCommandLogPending = true
                }
            },
            onError = {
                startCommandHint = "Di: Seven, hora de empezar"
            }
        )
    }

    fun stopListening() {
        speechService.stopListening()
    }

    DisposableEffect(Unit) {
        onDispose { speechService.destroy() }
    }

    LaunchedEffect(state, isPausedByTeacher, startCommandDetected) {
        val waitingForStartCommand = (state == ClassicTimerState.IDLE || state == ClassicTimerState.READY) &&
            !startCommandDetected
        if (!waitingForStartCommand &&
            (state != ClassicTimerState.WAITING_FIXED_RESPONSE || isPausedByTeacher) &&
            sttState == SttState.LISTENING
        ) {
            speechService.stopListening()
        }
    }

    LaunchedEffect(progress?.currentQuestionIndex) {
        sttPartial = ""
        sttFinal = ""
        logAttemptId = -1L
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
    val openAiVoiceProvider = remember {
        OpenAiTtsVoiceProvider(context) {
            OpenAiTtsConfig.fromBuild(
                voiceSettings.openAiVoiceName,
                voiceSettings.openAiInstructions
            )
        }
    }
    val geminiVoiceProvider = remember {
        GeminiTtsVoiceProvider(context) { GeminiTtsConfig.fromBuild() }
    }
    val sevenVoiceService = remember {
        SevenVoiceService(
            geminiProvider = geminiVoiceProvider,
            openAiProvider = openAiVoiceProvider,
            azureProvider = azureVoiceProvider,
            localProvider = localVoiceProvider,
            preferredProvider = { voiceSettings.provider }
        )
    }

    DisposableEffect(Unit) {
        toySpeechService.initialize { ttsStateFlow.value = it }
        onDispose {
            toySpeechService.shutdown()
            sevenVoiceService.release()
        }
    }

    var toyVoiceSpeaking by remember(activity) { mutableStateOf(false) }
    var lastSpokenPhrase by remember(activity) { mutableStateOf<String?>(null) }
    var lastVoiceProvider by remember(activity) { mutableStateOf<String?>(null) }
    var lastVoiceFallback by remember(activity) { mutableStateOf<Boolean?>(null) }

    fun recordVoice(outcome: VoiceOutcome?) {
        val label: String
        val fallback: Boolean
        when (outcome) {
            is VoiceOutcome.Completed -> { label = providerLabel(outcome.providerUsed); fallback = outcome.fallbackUsed }
            is VoiceOutcome.Failed, null -> { label = "Ninguno"; fallback = false }
            is VoiceOutcome.SkippedInvalidText -> { label = "Ninguno"; fallback = false }
        }
        lastVoiceProvider = label
        lastVoiceFallback = fallback
        Log.d(
            CLASSIC_LOG_TAG,
            "voz: seleccionado=${providerLabel(voiceSettings.provider)} usado=$label fallback=$fallback " +
                "cacheHit=${outcome?.cacheHit} cacheKey=${outcome?.cacheKey} " +
                "synthesisLatencyMs=${outcome?.synthesisLatencyMs} " +
                "playbackLatencyMs=${outcome?.playbackLatencyMs} totalLatencyMs=${outcome?.totalLatencyMs}"
        )
    }

    suspend fun speakAndAwait(text: String) {
        lastSpokenPhrase = text
        toyVoiceSpeaking = true
        try {
            val timeoutMs = speechTimeoutMsFor(text)
            val outcome = withTimeoutOrNull(timeoutMs) {
                runCatching {
                    sevenVoiceService.speak(text, source = "temporizador")
                }.getOrNull()
            }
            if (outcome == null) {
                Log.w(CLASSIC_LOG_TAG, "voz: sin resultado tras ${timeoutMs}ms, flujo continúa")
            }
            if (outcome is VoiceOutcome.SkippedInvalidText && logSessionId > 0L) {
                runCatching {
                    dataLogger.logTechnicalEvent(
                        sessionId = logSessionId,
                        questionId = progress?.currentQuestionId?.toLongOrNull(),
                        attemptId = logAttemptId.takeIf { it > 0L },
                        operationMode = "CLASSIC",
                        eventType = "TTS_SKIPPED_INVALID_TEXT",
                        message = "providerRequested=${outcome.providerRequested} providerUsed=NONE " +
                            "textLength=${outcome.textLength} reason=${outcome.reason}",
                        latencyMs = outcome.latencyMs
                    )
                }
            }
            recordVoice(outcome)
        } finally {
            sevenVoiceService.stop()
            toyVoiceSpeaking = false
        }
    }

    LaunchedEffect(isPausedByTeacher) {
        if (isPausedByTeacher) {
            sevenVoiceService.stop()
            toyVoiceSpeaking = false
        }
    }

    // ----- Temporizador visual ---------------------------------------------------
    var timerSecondsLeft by remember(activity) { mutableIntStateOf(0) }
    var timerQuestionIndex by remember(activity) { mutableIntStateOf(-1) }

    // Cuenta regresiva visual + salvaguarda de timeout: si el contador llega a 0
    // y el flujo sigue en WAITING_FIXED_RESPONSE (p. ej. STT nunca inicio o fallo),
    // dispara onTimeExpired directamente para garantizar que el flujo avance.
    LaunchedEffect(state, progress?.currentQuestionIndex, resumeToken, isPausedByTeacher) {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE || isPausedByTeacher) return@LaunchedEffect
        val total = progress?.effectiveMaxTimeSeconds ?: 10
        val questionIndex = progress?.currentQuestionIndex ?: -1
        if (timerQuestionIndex != questionIndex || timerSecondsLeft <= 0) {
            timerSecondsLeft = total
            timerQuestionIndex = questionIndex
        }
        while (timerSecondsLeft > 0) {
            delay(1_000L)
            if (isPausedByTeacher || runner.state != ClassicTimerState.WAITING_FIXED_RESPONSE) {
                return@LaunchedEffect
            }
            timerSecondsLeft -= 1
        }
        // Tiempo agotado: notificar aunque STT no este activo o haya fallado.
        if (!answerDelivered.value && runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            answerDelivered.value = true
            if (sttState == SttState.LISTENING) speechService.stopListening()
            dispatch { runner.onTimeExpired(hadPartialAtTimeout.value) }
            Log.d(CLASSIC_LOG_TAG, "timeout-visual: hadPartial=${hadPartialAtTimeout.value}")
        }
    }

    // Temporizador de respuesta via STT: complementario al visual. Dispara
    // onTimeExpired cuando STT lleva el tiempo maximo escuchando. El flag
    // answerDelivered evita doble avance si el visual ya disparo primero.
    LaunchedEffect(sttState, resumeToken, isPausedByTeacher) {
        if (sttState != SttState.LISTENING || isPausedByTeacher) return@LaunchedEffect
        val seconds = progress?.effectiveMaxTimeSeconds ?: 10
        delay(seconds * 1000L)
        if (!answerDelivered.value && !isPausedByTeacher) {
            answerDelivered.value = true
            speechService.stopListening()
            if (runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
                dispatch { runner.onTimeExpired(hadPartialAtTimeout.value) }
                Log.d(CLASSIC_LOG_TAG, "timeout-stt: hadPartial=${hadPartialAtTimeout.value}")
            }
        }
    }

    // ----- Efectos del flujo automático ------------------------------------------

    // SESSION_STARTING: frase de apertura → presentar primera pregunta
    LaunchedEffect(state == ClassicTimerState.SESSION_STARTING, resumeToken, isPausedByTeacher) {
        if (state != ClassicTimerState.SESSION_STARTING || isPausedByTeacher) return@LaunchedEffect
        Log.d(CLASSIC_LOG_TAG, "SESSION_STARTING: frase apertura")
        speakAndAwait(phraseBank.getSessionStart())
        if (!isPausedByTeacher && runner.state == ClassicTimerState.SESSION_STARTING) {
            dispatch { runner.presentCurrentQuestion() }
        }
    }

    // PRESENTING_QUESTION: frase transición + pregunta → abrir ventana de respuesta
    val presentKey = if (state == ClassicTimerState.PRESENTING_QUESTION) {
        progress?.currentQuestionIndex ?: 0
    } else {
        null
    }
    LaunchedEffect(presentKey, resumeToken, isPausedByTeacher) {
        if (presentKey == null || isPausedByTeacher) return@LaunchedEffect
        val isLast = progress?.isLastQuestion ?: false
        val questionText = progress?.currentQuestionText ?: return@LaunchedEffect
        val round = progress?.questionNumber ?: (presentKey + 1)
        val mediationKey = LocalMediationKey.fromKey(progress?.currentQuestionMediationKey)
        Log.d(CLASSIC_LOG_TAG, "PRESENTING_QUESTION round=$round isLast=$isLast key=$mediationKey")
        // Transición + pregunta en una sola reproducción para reducir demora.
        speakAndAwait(phraseBank.getRoundPrompt(round, questionText, isLast, mediationKey))
        if (!isPausedByTeacher && runner.state == ClassicTimerState.PRESENTING_QUESTION) {
            dispatch { runner.startResponseWindow() }
        }
    }

    // WAITING_FIXED_RESPONSE: registrar intento iniciado + abrir STT
    LaunchedEffect(state == ClassicTimerState.WAITING_FIXED_RESPONSE, resumeToken, isPausedByTeacher) {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE || isPausedByTeacher) return@LaunchedEffect
        Log.d(CLASSIC_LOG_TAG, "WAITING_FIXED_RESPONSE: abriendo STT")

        val qIdLong = progress?.currentQuestionId?.toLongOrNull() ?: 0L
        val sid = logSessionId
        if (sid > 0L && qIdLong > 0L && logAttemptId <= 0L) {
            logAttemptId = runCatching {
                dataLogger.logAttemptStarted(
                    sessionId = sid,
                    questionId = qIdLong,
                    questionOrder = progress?.currentQuestionIndex ?: 0,
                    attemptNumber = 1,
                    operationMode = "CLASSIC",
                    questionText = progress?.currentQuestionText,
                    maxTimeMs = (progress?.effectiveMaxTimeSeconds ?: 10) * 1000L,
                    usedSemanticEvaluation = false,
                    usedSpeechToText = true
                )
            }.getOrElse { -1L }
        }

        startListening()
    }

    // ANSWER_RECEIVED: frase neutra → pausa → avanzar
    val answerKey = if (state == ClassicTimerState.ANSWER_RECEIVED) {
        progress?.currentQuestionIndex ?: 0
    } else {
        null
    }
    LaunchedEffect(answerKey, resumeToken, isPausedByTeacher) {
        if (answerKey == null || isPausedByTeacher) return@LaunchedEffect
        val isLast = progress?.isLastQuestion ?: false
        Log.d(CLASSIC_LOG_TAG, "ANSWER_RECEIVED idx=$answerKey isLast=$isLast")

        val classicAid = logAttemptId
        val classicSid = logSessionId
        val responseLatency = progress?.responseLatencyMs
        classicTotalAttempts += 1
        if (classicAid > 0L && classicSid > 0L) {
            runCatching {
                dataLogger.finishClassicAttempt(
                    attemptId = classicAid,
                    finalAttemptState = "ANSWER_RECEIVED",
                    classicResult = "ANSWERED",
                    transcript = sttFinal.ifBlank { null },
                    responseReceivedAtMs = progress?.questionStartedAt?.let { it + (responseLatency ?: 0L) },
                    realResponseTimeMs = responseLatency,
                    usedStt = sttFinal.isNotBlank()
                )
            }
        }

        // Última ronda: frase de cierre de participación sin anunciar otra pregunta,
        // y avance inmediato al cierre (sin transición intermedia).
        speakAndAwait(phraseBank.getAnswerReceived(isLast))
        if (!isLast) delay(ROUND_TRANSITION_DELAY_MS)
        if (!isPausedByTeacher && runner.state == ClassicTimerState.ANSWER_RECEIVED) {
            dispatch { runner.advanceQuestion() }
        }
    }

    // TIME_EXPIRED: frase neutra → avanzar
    val timeoutKey = if (state == ClassicTimerState.TIME_EXPIRED) {
        progress?.currentQuestionIndex ?: 0
    } else {
        null
    }
    LaunchedEffect(timeoutKey, resumeToken, isPausedByTeacher) {
        if (timeoutKey == null || isPausedByTeacher) return@LaunchedEffect
        val hadPartial = progress?.hadPartialResponseOnTimeout ?: false
        val isLast = progress?.isLastQuestion ?: false
        Log.d(CLASSIC_LOG_TAG, "TIME_EXPIRED idx=$timeoutKey hadPartial=$hadPartial isLast=$isLast")

        val timeoutAid = logAttemptId
        val timeoutSid = logSessionId
        classicTotalAttempts += 1
        if (hadPartial) classicTimeouts += 1 else classicNoResponse += 1
        if (timeoutAid > 0L && timeoutSid > 0L) {
            runCatching {
                dataLogger.finishClassicAttempt(
                    attemptId = timeoutAid,
                    finalAttemptState = "TIME_EXPIRED",
                    classicResult = if (hadPartial) "TIMEOUT_PARTIAL" else "TIMEOUT_NO_RESPONSE",
                    transcript = sttFinal.ifBlank { null },
                    usedStt = sttFinal.isNotBlank()
                )
            }
        }
        if (timeoutSid > 0L) {
            val eventType = if (hadPartial) "CLASSIC_TIMEOUT_HANDLED" else "CLASSIC_NO_RESPONSE_HANDLED"
            runCatching {
                dataLogger.logTechnicalEvent(
                    sessionId = timeoutSid,
                    questionId = progress?.currentQuestionId?.toLongOrNull(),
                    attemptId = if (timeoutAid > 0L) timeoutAid else null,
                    operationMode = "CLASSIC",
                    eventType = eventType
                )
            }
        }

        // En la última ronda la frase no anuncia otra pregunta; encadena al cierre.
        speakAndAwait(phraseBank.getTimeExpired(hadPartial, isLast))
        if (!isLast) delay(ROUND_TRANSITION_DELAY_MS)
        if (!isPausedByTeacher && runner.state == ClassicTimerState.TIME_EXPIRED) {
            dispatch { runner.advanceQuestion() }
        }
    }

    // SESSION_COMPLETED: frase de cierre
    LaunchedEffect(state == ClassicTimerState.SESSION_COMPLETED, isPausedByTeacher) {
        if (state != ClassicTimerState.SESSION_COMPLETED || isPausedByTeacher) return@LaunchedEffect
        Log.d(CLASSIC_LOG_TAG, "SESSION_COMPLETED")
        speakAndAwait(phraseBank.getSessionCompleted())
    }

    // Registra el cierre de la sesion clasica cuando se alcanza un estado terminal.
    val classicTerminalKey = when (state) {
        ClassicTimerState.SESSION_COMPLETED,
        ClassicTimerState.SESSION_CANCELLED,
        ClassicTimerState.ERROR -> state.name
        else -> null
    }
    LaunchedEffect(classicTerminalKey) {
        if (classicTerminalKey == null) return@LaunchedEffect
        val sid = logSessionId
        if (sid <= 0L) return@LaunchedEffect
        val startedMs = progress?.sessionStartedAt ?: 0L
        runCatching {
            dataLogger.finishClassicSession(
                sessionId = sid,
                finalState = classicTerminalKey,
                startedAtMs = startedMs,
                completedQuestions = progress?.currentQuestionIndex?.let {
                    if (state == ClassicTimerState.SESSION_COMPLETED) it + 1 else it
                } ?: 0,
                totalAttempts = classicTotalAttempts,
                noResponseCount = classicNoResponse,
                timeoutCount = classicTimeouts
            )
        }
        logSessionId = -1L
    }

    fun startInteraction() {
        if (sttState == SttState.LISTENING) speechService.stopListening()
        isPausedByTeacher = false
        logAttemptId = -1L
        classicTotalAttempts = 0
        classicNoResponse = 0
        classicTimeouts = 0
        dispatch {
            runner.loadActivity(activity)
            runner.markActivityLoaded()
            runner.startSession()
        }
        val activityIdLong = activity.id.toLongOrNull() ?: return
        scope.launch {
            runCatching {
                logSessionId = dataLogger.startSession(
                    activityId = activityIdLong,
                    activityName = activity.title,
                    operationMode = "CLASSIC",
                    totalQuestions = activity.questions.size
                )
                if (startCommandLogPending && logSessionId > 0L) {
                    dataLogger.logTechnicalEvent(
                        sessionId = logSessionId,
                        operationMode = "CLASSIC",
                        eventType = "CLASSIC_START_COMMAND_DETECTED"
                    )
                    startCommandLogPending = false
                }
            }
        }
    }

    fun finishByTeacher() {
        if (sttState == SttState.LISTENING) speechService.stopListening()
        sevenVoiceService.stop()
        val sid = logSessionId
        val startedMs = progress?.sessionStartedAt ?: System.currentTimeMillis()
        dispatch { runner.cancelSession() }
        scope.launch {
            if (sid > 0L) {
                runCatching {
                    dataLogger.logTechnicalEvent(
                        sessionId = sid,
                        operationMode = "CLASSIC",
                        eventType = "CLASSIC_CANCELLED_BY_TEACHER"
                    )
                    dataLogger.finishClassicSession(
                        sessionId = sid,
                        finalState = "SESSION_CANCELLED",
                        startedAtMs = startedMs,
                        completedQuestions = progress?.currentQuestionIndex ?: 0,
                        totalAttempts = classicTotalAttempts,
                        noResponseCount = classicNoResponse,
                        timeoutCount = classicTimeouts
                    )
                }
                logSessionId = -1L
            }
            onBack()
        }
    }

    LaunchedEffect(startCommandDetected) {
        if (startCommandDetected && state == ClassicTimerState.IDLE) {
            startInteraction()
        }
    }

    LaunchedEffect(state, sttState, audioGranted, startCommandDetected, isPausedByTeacher) {
        if (state == ClassicTimerState.IDLE &&
            !startCommandDetected &&
            !isPausedByTeacher &&
            sttState != SttState.LISTENING &&
            sttState != SttState.STOPPING
        ) {
            delay(350L)
            startCommandListening()
        }
    }

    // ----- UI --------------------------------------------------------------------
    val activityForOrientation = context.findActivity()
    DisposableEffect(activityForOrientation) {
        val previous = activityForOrientation?.requestedOrientation
        activityForOrientation?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            if (previous != null) {
                activityForOrientation.requestedOrientation = previous
            }
        }
    }

    BackHandler {
        if (state == ClassicTimerState.IDLE) onChangeActivity() else pauseByTeacher()
    }

    val visualState = when {
        isPausedByTeacher -> SevenVisualState.PAUSED
        state == ClassicTimerState.WAITING_FIXED_RESPONSE -> SevenVisualState.COUNTDOWN
        toyVoiceSpeaking ||
            state == ClassicTimerState.SESSION_STARTING ||
            state == ClassicTimerState.PRESENTING_QUESTION ||
            state == ClassicTimerState.ANSWER_RECEIVED ||
            state == ClassicTimerState.TIME_EXPIRED -> SevenVisualState.SPEAKING
        state == ClassicTimerState.SESSION_COMPLETED -> SevenVisualState.COMPLETED
        state == ClassicTimerState.ERROR -> SevenVisualState.ERROR
        state == ClassicTimerState.IDLE || state == ClassicTimerState.READY -> SevenVisualState.WAITING_START_COMMAND
        else -> SevenVisualState.IDLE
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(SevenFaceBackground, SevenFaceBackgroundAlt)
                )
            )
            .padding(20.dp)
    ) {
        OutlinedButton(
            onClick = { pauseByTeacher() },
            enabled = state != ClassicTimerState.IDLE && !isPausedByTeacher,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(96.dp)
        ) {
            Text("Pausa")
        }

        if (visualState == SevenVisualState.COUNTDOWN) {
            SevenCountdownView(secondsLeft = timerSecondsLeft)
        } else {
            SevenFace(
                state = visualState,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                isPausedByTeacher -> {
                    Text(
                        text = "Actividad en pausa",
                        color = ClassicTimerPrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { resumeByTeacher() }) {
                            Text("Reanudar")
                        }
                        OutlinedButton(onClick = { finishByTeacher() }) {
                            Text("Finalizar")
                        }
                    }
                }
                state == ClassicTimerState.IDLE || state == ClassicTimerState.READY -> {
                    Text(
                        text = "Seven está listo",
                        color = ClassicTimerPrimaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (audioGranted) startCommandHint else "Concede el micrófono para empezar",
                        color = ClassicTimerSecondaryText,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    if (!audioGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        ) {
                            Text("Conceder micrófono")
                        }
                    }
                }
                state == ClassicTimerState.SESSION_COMPLETED -> {
                    Text(
                        text = "Actividad completada",
                        color = ClassicTimerPrimaryText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onChangeActivity) {
                        Text("Elegir otra actividad")
                    }
                }
                state == ClassicTimerState.ERROR -> {
                    Text(
                        text = errorMessage ?: "La actividad se detuvo",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onChangeActivity) {
                        Text("Volver")
                    }
                }
            }
        }
    }
    return

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
private fun SevenFace(
    state: SevenVisualState,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "seven-eyes")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                1f at 0
                1f at 2780
                0.24f at 2900
                1f at 3040
                1f at 3600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val speakingPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking-pulse"
    )
    val eyeOpen = when (state) {
        SevenVisualState.SPEAKING -> (0.92f + speakingPulse * 0.03f) * blink.coerceIn(0.72f, 1f)
        SevenVisualState.PAUSED -> 0.74f * blink.coerceIn(0.76f, 1f)
        SevenVisualState.COMPLETED -> 0.90f * blink.coerceIn(0.78f, 1f)
        SevenVisualState.ERROR -> 0.82f * blink.coerceIn(0.82f, 1f)
        else -> blink.coerceIn(0.80f, 1f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .height(260.dp)
    ) {
        val faceCenter = Offset(size.width / 2f, size.height * 0.52f)
        val faceRadius = size.minDimension * 0.45f
        drawCircle(
            color = Color.White.copy(alpha = 0.26f),
            radius = faceRadius,
            center = faceCenter
        )
        if (state == SevenVisualState.SPEAKING) {
            val accentAlpha = 0.12f + speakingPulse * 0.16f
            drawCircle(
                color = SevenFaceAccent.copy(alpha = accentAlpha),
                radius = size.minDimension * 0.032f,
                center = Offset(size.width * 0.16f, size.height * 0.30f)
            )
            drawCircle(
                color = SevenCountdownOrange.copy(alpha = accentAlpha),
                radius = size.minDimension * 0.026f,
                center = Offset(size.width * 0.84f, size.height * 0.38f)
            )
            drawCircle(
                color = SevenEyeIris.copy(alpha = accentAlpha),
                radius = size.minDimension * 0.018f,
                center = Offset(size.width * 0.78f, size.height * 0.23f)
            )
        }

        val eyeWidth = size.width * 0.35f
        val fullEyeHeight = size.height * 0.62f
        val eyeHeight = fullEyeHeight * eyeOpen
        val eyeCenters = listOf(
            Offset(size.width * 0.30f, size.height * 0.45f),
            Offset(size.width * 0.70f, size.height * 0.45f)
        )
        val eyeSize = Size(eyeWidth, eyeHeight)
        val irisRadius = eyeWidth * when (state) {
            SevenVisualState.PAUSED -> 0.27f
            SevenVisualState.COMPLETED -> 0.33f
            else -> 0.31f
        }
        val pupilRadius = irisRadius * 0.42f
        val irisGlow = if (state == SevenVisualState.SPEAKING) speakingPulse * 0.14f else 0f
        val irisNudgeX = if (state == SevenVisualState.SPEAKING) {
            (speakingPulse - 0.5f) * size.minDimension * 0.010f
        } else {
            0f
        }

        eyeCenters.forEach { eyeCenter ->
            val eyeOffset = Offset(eyeCenter.x - eyeWidth / 2f, eyeCenter.y - eyeHeight / 2f)
            drawOval(
                color = SevenFaceAccent.copy(alpha = 0.08f),
                topLeft = eyeOffset + Offset(0f, fullEyeHeight * 0.04f),
                size = eyeSize
            )
            drawOval(
                color = SevenEyeWhite,
                topLeft = eyeOffset,
                size = eyeSize
            )
            val irisCenter = eyeCenter + Offset(
                x = irisNudgeX,
                y = if (state == SevenVisualState.PAUSED) eyeHeight * 0.04f else 0f
            )
            drawCircle(
                color = SevenEyeIris.copy(alpha = 0.18f + irisGlow),
                radius = irisRadius * 1.24f,
                center = irisCenter
            )
            drawCircle(
                color = SevenEyeIris,
                radius = irisRadius,
                center = irisCenter
            )
            drawCircle(
                color = SevenEyePupil.copy(alpha = 0.92f),
                radius = pupilRadius,
                center = irisCenter
            )
            drawCircle(
                color = Color(0xFF7DE7F2).copy(alpha = 0.78f + irisGlow),
                radius = irisRadius * 0.54f,
                center = irisCenter + Offset(irisRadius * 0.06f, irisRadius * 0.04f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.90f),
                radius = irisRadius * 0.23f,
                center = irisCenter + Offset(-irisRadius * 0.34f, -irisRadius * 0.42f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.70f + irisGlow),
                radius = irisRadius * 0.12f,
                center = irisCenter + Offset(irisRadius * 0.34f, -irisRadius * 0.12f)
            )
            val sparkleCenter = irisCenter + Offset(-irisRadius * 0.03f, irisRadius * 0.36f)
            drawLine(
                color = Color.White.copy(alpha = 0.74f + irisGlow),
                start = sparkleCenter + Offset(-irisRadius * 0.16f, 0f),
                end = sparkleCenter + Offset(irisRadius * 0.16f, 0f),
                strokeWidth = 3f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.74f + irisGlow),
                start = sparkleCenter + Offset(0f, -irisRadius * 0.16f),
                end = sparkleCenter + Offset(0f, irisRadius * 0.16f),
                strokeWidth = 3f
            )
            drawArc(
                color = SevenEyeLid.copy(alpha = 0.34f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(eyeCenter.x - eyeWidth * 0.43f, eyeCenter.y - fullEyeHeight * 0.44f),
                size = Size(eyeWidth * 0.86f, fullEyeHeight * 0.38f),
                style = Stroke(width = 4f)
            )
        }

        val cheekAlpha = when (state) {
            SevenVisualState.ERROR -> 0.18f
            SevenVisualState.PAUSED -> 0.22f
            SevenVisualState.SPEAKING -> 0.30f + speakingPulse * 0.08f
            else -> 0.34f
        }
        val cheekY = size.height * 0.68f
        drawOval(
            color = SevenCheek.copy(alpha = cheekAlpha),
            topLeft = Offset(size.width * 0.18f, cheekY),
            size = Size(size.width * 0.13f, size.height * 0.052f)
        )
        drawOval(
            color = SevenCheek.copy(alpha = cheekAlpha),
            topLeft = Offset(size.width * 0.69f, cheekY),
            size = Size(size.width * 0.13f, size.height * 0.052f)
        )
    }
}

@Composable
private fun SevenCountdownView(secondsLeft: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(238.dp)) {
            drawCircle(
                color = Color.White.copy(alpha = 0.58f),
                radius = size.minDimension * 0.46f,
                center = center
            )
            drawRoundRect(
                color = SevenFaceAccent.copy(alpha = 0.13f),
                topLeft = Offset(size.width * 0.19f, size.height * 0.12f),
                size = Size(size.width * 0.62f, size.height * 0.16f),
                cornerRadius = CornerRadius(36f, 36f)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Tu turno",
                color = ClassicTimerPrimaryText,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = secondsLeft.coerceAtLeast(0).toString(),
                color = SevenCountdownOrange,
                fontSize = 132.sp,
                lineHeight = 136.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ClassicImmersiveSystemBarsEffect(context: Context) {
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())

            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
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
    ToyVoiceProviderType.OPENAI_TTS -> "OpenAI TTS"
    ToyVoiceProviderType.LOCAL -> "Voz local"
    ToyVoiceProviderType.AZURE_NEURAL -> "Azure"
    ToyVoiceProviderType.GEMINI_TTS -> "Gemini"
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
