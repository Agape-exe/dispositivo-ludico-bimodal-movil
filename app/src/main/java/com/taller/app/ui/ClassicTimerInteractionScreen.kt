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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.taller.app.bimodal.SevenStartCommand
import com.taller.app.classic.ClassicTimerRunner
import com.taller.app.classic.ClassicTimerScript
import com.taller.app.classic.ClassicTimerState
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.mapper.toDomain
import com.taller.app.model.LearningActivity
import com.taller.app.settings.AppSettings
import com.taller.app.settings.AppSettingsRepository
import com.taller.app.speech.SpeechToTextService
import com.taller.app.speech.SttState
import com.taller.app.ui.face.SevenDogFace
import com.taller.app.ui.face.SevenFaceScaffold
import com.taller.app.ui.face.SevenFaceState
import com.taller.app.ui.face.classicSevenFaceState
import com.taller.app.voice.LocalToyVoiceProvider
import com.taller.app.voice.SevenVoiceService
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.VoiceContext
import com.taller.app.voice.VoiceMode
import com.taller.app.voice.neural.AzureSpeechConfig
import com.taller.app.voice.neural.AzureSpeechVoiceProvider
import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.GeminiTtsVoiceProvider
import com.taller.app.voice.neural.OpenAiTtsConfig
import com.taller.app.voice.neural.OpenAiTtsVoiceProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull

private const val CLASSIC_LOG_TAG = "ClassicTimer"
private const val MAX_SPEECH_AFTER_START_MS = 10_000L
private const val SPEECH_TIMEOUT_MIN_MS = 12_000L
private const val SPEECH_TIMEOUT_MAX_MS = 45_000L
private const val SPEECH_TIMEOUT_PER_CHAR_MS = 120L

private val ClassicTimerOrange = Color(0xFFF29A00)
private val ClassicTimerCardYellow = Color(0xFFFFF0A6)
private val ClassicTimerTitleText = Color(0xFFF29A00)
private val ClassicTimerPrimaryText = Color(0xFF2E2535)
private val ClassicTimerSecondaryText = Color(0xFF3F3A4A)
private val ClassicTimerBackground = Color.White
private val SevenFaceBackground = Color(0xFFF6F1FF)
private val SevenFaceAccent = Color(0xFF7C4DFF)

private fun speechTimeoutMsFor(text: String): Long =
    (SPEECH_TIMEOUT_MIN_MS + text.length * SPEECH_TIMEOUT_PER_CHAR_MS)
        .coerceAtMost(SPEECH_TIMEOUT_MAX_MS)

@Composable
fun ClassicTimerInteractionScreen(onStartActivity: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    ClassicActivitySelector(
        activityDao = remember { db.activityDao() },
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
    var loadedActivity by remember { mutableStateOf<LearningActivity?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(activityId) {
        isLoading = true
        val entity = activityDao.getById(activityId)
        val questions = questionDao.getByActivityIdOnce(activityId)
        when {
            activityId == 0L -> infoMessage = "No se recibio una actividad para iniciar."
            entity == null -> infoMessage = "La actividad no existe o fue eliminada."
            questions.isEmpty() -> infoMessage = "La actividad no tiene preguntas."
            else -> loadedActivity = entity.toDomain(questions.map { it.toDomain() })
        }
        isLoading = false
    }

    loadedActivity?.let {
        ClassicSession(it, onChangeActivity, onBack)
    } ?: ClassicSevenLoadingOrError(isLoading, infoMessage, onBack)
}

@Composable
private fun ClassicSevenLoadingOrError(
    isLoading: Boolean,
    infoMessage: String?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Box(
        modifier = Modifier.fillMaxSize().background(SevenFaceBackground).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SevenDogFace(
                state = if (isLoading) SevenFaceState.WAITING else SevenFaceState.ERROR_SOFT,
                modifier = Modifier.size(220.dp)
            )
            Spacer(Modifier.height(20.dp))
            if (isLoading) {
                CircularProgressIndicator(color = SevenFaceAccent)
                Spacer(Modifier.height(16.dp))
                Text("Cargando actividad...", fontSize = 18.sp)
            } else {
                Text(
                    infoMessage ?: "No se pudo abrir la actividad.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onBack) { Text("Volver") }
            }
        }
    }
}

@Composable
private fun ClassicActivitySelector(
    activityDao: com.taller.app.data.local.dao.ActivityDao,
    onPick: (ActivityEntity) -> Unit,
    onBack: () -> Unit
) {
    val activities by activityDao.getAllOrderedByUpdated().collectAsState(initial = emptyList())
    var gateMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(ClassicTimerBackground).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "Modo\nTemporizador",
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            color = ClassicTimerTitleText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Selecciona una actividad para presentar preguntas con tiempo",
            fontSize = 17.sp,
            color = ClassicTimerSecondaryText,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        gateMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        if (activities.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No hay actividades disponibles.", fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(activities, key = { it.id }) { activity ->
                    Card(
                        modifier = Modifier.fillMaxWidth(0.86f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ClassicTimerCardYellow)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(activity.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(activity.topic, color = ClassicTimerSecondaryText)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (com.taller.app.voice.prep.VoicePrepGate.isReady(activity.voicePrepStatus)) {
                                        onPick(activity)
                                    } else {
                                        gateMessage = com.taller.app.voice.prep.VoicePrepGate.NOT_READY_MESSAGE
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ClassicTimerOrange),
                                shape = RoundedCornerShape(18.dp)
                            ) { Text("Iniciar") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = ClassicTimerOrange),
            modifier = Modifier.width(148.dp)
        ) { Text("Volver") }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Temporizador ultra simple: usa STT solo como senal efimera de inicio/fin.
 * No crea sesiones Room, intentos, transcripciones, evaluaciones ni resumenes.
 */
@Composable
private fun ClassicSession(
    activity: LearningActivity,
    onChangeActivity: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { AppSettingsRepository(context.applicationContext) }
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings.defaults())
    val runner = remember(activity, settings.classicResponseTimeSeconds) {
        ClassicTimerRunner(settings.classicResponseTimeSeconds)
    }
    var state by remember(activity) { mutableStateOf(runner.state) }
    var progress by remember(activity) { mutableStateOf(runner.progress) }
    var errorMessage by remember(activity) { mutableStateOf(runner.errorMessage) }

    fun dispatch(action: () -> Unit) {
        action()
        state = runner.state
        progress = runner.progress
        errorMessage = runner.errorMessage
    }

    ClassicImmersiveSystemBarsEffect(context)

    val speechService = remember { SpeechToTextService(context) }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { audioGranted = it }
    var sttState by remember(activity) { mutableStateOf(SttState.IDLE) }
    var startCommandDetected by remember(activity) { mutableStateOf(false) }
    var isPausedByTeacher by remember(activity) { mutableStateOf(false) }

    fun finishResponse() {
        if (isPausedByTeacher) return
        if (runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            dispatch { runner.onResponseStarted() }
        }
        if (runner.state == ClassicTimerState.RESPONSE_IN_PROGRESS) {
            dispatch { runner.onAnswerReceived() }
        }
    }

    fun startResponseListening() {
        if (!audioGranted || isPausedByTeacher ||
            runner.state != ClassicTimerState.WAITING_FIXED_RESPONSE
        ) return
        if (sttState == SttState.LISTENING || sttState == SttState.STOPPING) return
        speechService.startListening(
            onStateChange = { sttState = it },
            onReady = {},
            onPartialResult = { partial ->
                if (partial.isNotBlank() && runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
                    dispatch { runner.onResponseStarted() }
                }
            },
            onFinalResult = { _ -> finishResponse() },
            onStopped = { stoppedText ->
                if (stoppedText.isNotBlank()) finishResponse()
            },
            onError = { _ ->
                if (runner.state == ClassicTimerState.RESPONSE_IN_PROGRESS) finishResponse()
            },
            onSpeechStart = {
                if (runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
                    dispatch { runner.onResponseStarted() }
                }
            },
            onSpeechEnd = { finishResponse() }
        )
    }

    fun startCommandListening() {
        if (!audioGranted || startCommandDetected || isPausedByTeacher) return
        if (runner.state != ClassicTimerState.IDLE && runner.state != ClassicTimerState.READY) return
        if (sttState == SttState.LISTENING || sttState == SttState.STOPPING) return
        speechService.startListening(
            onStateChange = { sttState = it },
            onReady = {},
            onPartialResult = { text ->
                if (SevenStartCommand.matches(text)) {
                    startCommandDetected = true
                    speechService.stopListening()
                }
            },
            onFinalResult = { text -> startCommandDetected = SevenStartCommand.matches(text) },
            onStopped = { text ->
                if (SevenStartCommand.matches(text)) startCommandDetected = true
            },
            onError = {}
        )
    }

    DisposableEffect(Unit) {
        onDispose { speechService.destroy() }
    }

    val ttsState = remember { MutableStateFlow(ToySpeechState.UNINITIALIZED) }
    val voiceRepository = remember { ToyVoiceSettingsRepository(context) }
    val voiceSettings by voiceRepository.settings.collectAsState(initial = ToyVoiceSettings())
    val toySpeechService = remember { ToySpeechService(context) }
    val localProvider = remember {
        LocalToyVoiceProvider(toySpeechService, ttsState) { voiceSettings }
    }
    val azureProvider = remember {
        AzureSpeechVoiceProvider(context) { AzureSpeechConfig.fromBuild(voiceSettings.azureVoiceName) }
    }
    val openAiProvider = remember {
        OpenAiTtsVoiceProvider(context) {
            OpenAiTtsConfig.fromBuild(voiceSettings.openAiVoiceName, voiceSettings.openAiInstructions)
        }
    }
    val geminiProvider = remember {
        GeminiTtsVoiceProvider(context) {
            GeminiTtsConfig.fromBuild(voiceSettings.geminiVoiceName, voiceSettings.geminiInstructions)
        }
    }
    val voiceService = remember {
        SevenVoiceService(
            geminiProvider = geminiProvider,
            openAiProvider = openAiProvider,
            azureProvider = azureProvider,
            localProvider = localProvider,
            preferredProvider = { voiceSettings.provider }
        )
    }
    var voiceSpeaking by remember(activity) { mutableStateOf(false) }

    DisposableEffect(Unit) {
        toySpeechService.initialize { ttsState.value = it }
        onDispose {
            toySpeechService.shutdown()
            voiceService.release()
        }
    }

    suspend fun speakAndAwait(text: String, voiceContext: VoiceContext) {
        voiceSpeaking = true
        try {
            withTimeoutOrNull(speechTimeoutMsFor(text)) {
                if (activity.voicePrepReady) {
                    voiceService.speakFromCacheOnly(
                        text = text,
                        source = "temporizador",
                        mode = VoiceMode.TIMER,
                        voiceContext = voiceContext
                    )
                } else {
                    voiceService.speak(
                        text = text,
                        source = "temporizador",
                        mode = VoiceMode.TIMER,
                        voiceContext = voiceContext
                    )
                }
            }
        } finally {
            voiceService.stop()
            voiceSpeaking = false
        }
    }

    fun startInteraction() {
        if (sttState == SttState.LISTENING) speechService.stopListening()
        isPausedByTeacher = false
        dispatch {
            runner.loadActivity(activity)
            runner.markActivityLoaded()
            runner.startSession()
        }
    }

    LaunchedEffect(startCommandDetected) {
        if (startCommandDetected && runner.state == ClassicTimerState.IDLE) startInteraction()
    }

    LaunchedEffect(state, sttState, audioGranted, startCommandDetected, isPausedByTeacher) {
        if (state == ClassicTimerState.IDLE && !startCommandDetected && !isPausedByTeacher &&
            sttState != SttState.LISTENING && sttState != SttState.STOPPING
        ) {
            delay(350L)
            startCommandListening()
        }
    }

    LaunchedEffect(state, isPausedByTeacher) {
        if (state == ClassicTimerState.SESSION_STARTING && !isPausedByTeacher) {
            speakAndAwait(ClassicTimerScript.intro(activity), VoiceContext.GREETING)
            if (runner.state == ClassicTimerState.SESSION_STARTING) {
                dispatch { runner.presentCurrentQuestion() }
            }
        }
    }

    val questionKey = progress?.currentQuestionIndex.takeIf {
        state == ClassicTimerState.PRESENTING_QUESTION
    }
    LaunchedEffect(questionKey, isPausedByTeacher) {
        if (questionKey == null || isPausedByTeacher) return@LaunchedEffect
        val question = activity.questions.getOrNull(questionKey) ?: return@LaunchedEffect
        speakAndAwait(ClassicTimerScript.questionText(question), VoiceContext.QUESTION)
        if (runner.state == ClassicTimerState.PRESENTING_QUESTION) {
            dispatch { runner.startResponseWindow() }
        }
    }

    var timerSecondsLeft by remember(activity) { mutableIntStateOf(0) }
    LaunchedEffect(state, progress?.currentQuestionIndex, isPausedByTeacher) {
        if (state != ClassicTimerState.WAITING_FIXED_RESPONSE || isPausedByTeacher) return@LaunchedEffect
        timerSecondsLeft = progress?.effectiveMaxTimeSeconds ?: 10
        startResponseListening()
        while (timerSecondsLeft > 0 && runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            delay(1_000L)
            if (!isPausedByTeacher && runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
                timerSecondsLeft -= 1
            }
        }
        if (!isPausedByTeacher && runner.state == ClassicTimerState.WAITING_FIXED_RESPONSE) {
            if (sttState == SttState.LISTENING) speechService.stopListening()
            dispatch { runner.onTimeExpired() }
            Log.d(CLASSIC_LOG_TAG, "Ventana de inicio agotada")
        }
    }

    LaunchedEffect(state == ClassicTimerState.RESPONSE_IN_PROGRESS, isPausedByTeacher) {
        if (state != ClassicTimerState.RESPONSE_IN_PROGRESS || isPausedByTeacher) return@LaunchedEffect
        delay(MAX_SPEECH_AFTER_START_MS)
        if (runner.state == ClassicTimerState.RESPONSE_IN_PROGRESS) {
            if (sttState == SttState.LISTENING) speechService.stopListening()
            finishResponse()
            Log.d(CLASSIC_LOG_TAG, "Tope de habla alcanzado")
        }
    }

    LaunchedEffect(state, isPausedByTeacher) {
        if ((state == ClassicTimerState.ANSWER_RECEIVED ||
                state == ClassicTimerState.TIME_EXPIRED) && !isPausedByTeacher
        ) {
            dispatch { runner.advanceQuestion() }
        }
    }

    LaunchedEffect(state == ClassicTimerState.SESSION_COMPLETED, isPausedByTeacher) {
        if (state == ClassicTimerState.SESSION_COMPLETED && !isPausedByTeacher) {
            speakAndAwait(ClassicTimerScript.closing(activity), VoiceContext.CLOSING)
        }
    }

    fun pauseByTeacher() {
        if (isPausedByTeacher) return
        isPausedByTeacher = true
        voiceService.stop()
        if (sttState == SttState.LISTENING) speechService.stopListening()
    }

    fun resumeByTeacher() {
        if (!isPausedByTeacher) return
        isPausedByTeacher = false
        if (runner.state == ClassicTimerState.RESPONSE_IN_PROGRESS) finishResponse()
    }

    fun finishByTeacher() {
        voiceService.stop()
        if (sttState == SttState.LISTENING) speechService.stopListening()
        dispatch { runner.cancelSession() }
        onBack()
    }

    val activityWindow = context.findActivity()
    DisposableEffect(activityWindow) {
        val previous = activityWindow?.requestedOrientation
        activityWindow?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { if (previous != null) activityWindow.requestedOrientation = previous }
    }

    BackHandler {
        if (state == ClassicTimerState.IDLE) onChangeActivity() else pauseByTeacher()
    }

    val faceState = classicSevenFaceState(state, voiceSpeaking, isPausedByTeacher)
    val showCountdown = state == ClassicTimerState.WAITING_FIXED_RESPONSE && !isPausedByTeacher

    SevenFaceScaffold(
        faceState = faceState,
        statusText = when {
            showCountdown -> "Tu turno"
            state == ClassicTimerState.RESPONSE_IN_PROGRESS -> "Te escucho"
            else -> null
        },
        countdownSeconds = timerSecondsLeft.takeIf { showCountdown },
        overlays = {
            OutlinedButton(
                onClick = { pauseByTeacher() },
                enabled = state != ClassicTimerState.IDLE && !isPausedByTeacher,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).width(96.dp)
            ) { Text("Pausa") }
        },
        bottomContent = {
            when {
                isPausedByTeacher -> {
                    Text("Actividad en pausa", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { resumeByTeacher() }) { Text("Reanudar") }
                        OutlinedButton(onClick = { finishByTeacher() }) { Text("Finalizar") }
                    }
                }
                state == ClassicTimerState.IDLE || state == ClassicTimerState.READY -> {
                    Text("Seven está listo", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(SevenStartCommand.PRIMARY_HINT, color = ClassicTimerSecondaryText)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { startInteraction() }) { Text("Iniciar") }
                        if (!audioGranted) {
                            OutlinedButton(onClick = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Text("Activar micrófono") }
                        }
                    }
                }
                state == ClassicTimerState.SESSION_COMPLETED -> {
                    Text("Actividad completada", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onChangeActivity) { Text("Elegir otra actividad") }
                }
                state == ClassicTimerState.ERROR -> {
                    Text(errorMessage ?: "La actividad se detuvo", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onChangeActivity) { Text("Volver") }
                }
            }
        }
    )
}

@Composable
private fun ClassicImmersiveSystemBarsEffect(context: Context) {
    val activity = context.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
