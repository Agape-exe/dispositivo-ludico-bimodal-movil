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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.taller.app.bimodal.BimodalAutoAction
import com.taller.app.bimodal.BimodalFlowOrchestrator
import com.taller.app.bimodal.BimodalInteractionResult
import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.bimodal.BimodalLatencyStats
import com.taller.app.bimodal.BimodalLatencyTracker
import com.taller.app.bimodal.BimodalSessionSummary
import com.taller.app.bimodal.DEFAULT_MAX_TIME_SECONDS
import com.taller.app.bimodal.FacePausePhraseBank
import com.taller.app.bimodal.SemanticEvaluationAdapter
import com.taller.app.bimodal.SpeechCaptureEventMapper
import com.taller.app.bimodal.SpeechCaptureOutcome
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackContext
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackGenerator
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackMessage
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType
import com.taller.app.bimodal.feedback.AnimalMediationBank
import com.taller.app.bimodal.latencyMsLabel
import com.taller.app.bimodal.mediation.GenerativeMediationType
import com.taller.app.bimodal.mediation.MediationSource
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.mapper.toDomain
import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.LocalMediationKey
import com.taller.app.semantic.SemanticResult
import com.taller.app.speech.SpeechToTextService
import com.taller.app.speech.SttState
import com.taller.app.vision.FaceAnalyzer
import com.taller.app.vision.FacePresenceTracker
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
import com.taller.app.voice.neural.OpenAiTtsConfig
import com.taller.app.voice.neural.OpenAiTtsVoiceProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.text.Normalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.taller.app.logger.InteractionDataLogger
import kotlinx.coroutines.withTimeoutOrNull

/** Etiqueta de logs internos de latencia (solo numeros, sin datos del nino). */
private const val BIMODAL_LATENCY_TAG = "BimodalLatency"

/** Etiqueta de logs de la evaluacion semantica (solo resultado, sin transcripcion). */
private const val BIMODAL_SEMANTIC_TAG = "BimodalSemantic"

/** Etiqueta de logs de la voz del juguete (solo proveedor, nunca claves ni texto). */
private const val BIMODAL_VOICE_TAG = "BimodalVoice"

private val IntelligentModeBlue = Color(0xFF0095B8)
private val IntelligentModeCardTurquoise = Color(0xFF78C5CC)
private val IntelligentModeTitleText = Color(0xFF0087A8)
private val IntelligentModePrimaryText = Color(0xFF1F2733)
private val IntelligentModeSecondaryText = Color(0xFF3F3A4A)
private val IntelligentModeBackground = Color(0xFFFFFFFF)
private val IntelligentSevenBackground = Color(0xFFEAF9F2)
private val IntelligentSevenBackgroundAlt = Color(0xFFFFF7D7)

/**
 * Tope de seguridad para una sola reproduccion de voz. Es generoso para no cortar
 * una frase larga real, pero garantiza que el flujo nunca se quede congelado si un
 * proveedor de voz se cuelga (p. ej. la red de Azure no responde o el reproductor
 * nunca emite su callback de fin). Se calcula segun la longitud del texto y se
 * acota entre un minimo y un maximo.
 */
private const val SPEECH_TIMEOUT_MIN_MS = 12_000L
private const val SPEECH_TIMEOUT_MAX_MS = 45_000L
private const val SPEECH_TIMEOUT_PER_CHAR_MS = 120L

/** Tope de seguridad (ms) para reproducir [text], acotado a un rango razonable. */
private fun speechTimeoutMsFor(text: String): Long =
    (SPEECH_TIMEOUT_MIN_MS + text.length * SPEECH_TIMEOUT_PER_CHAR_MS)
        .coerceAtMost(SPEECH_TIMEOUT_MAX_MS)

/**
 * Divide un texto en segmentos cortos de voz por oraciones (puntuacion fuerte y
 * saltos de linea), conservando el signo final de cada oracion. Reproducir la
 * presentacion en segmentos cortos permite que el flujo reaccione rapido a una
 * perdida de rostro: Seven termina el segmento en curso (breve) en vez de leer toda
 * la introduccion antes de poder pausar. Si el texto no tiene puntuacion fuerte,
 * devuelve el texto completo como un unico segmento.
 */
private fun splitIntoSpeechSegments(text: String): List<String> {
    val segments = mutableListOf<String>()
    val current = StringBuilder()
    for (ch in text) {
        current.append(ch)
        if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
            val segment = current.toString().trim()
            if (segment.isNotEmpty()) segments.add(segment)
            current.setLength(0)
        }
    }
    val tail = current.toString().trim()
    if (tail.isNotEmpty()) segments.add(tail)
    return segments.ifEmpty {
        val whole = text.trim()
        if (whole.isEmpty()) emptyList() else listOf(whole)
    }
}

private fun normalizeIntelligentSevenCommand(text: String): String {
    val withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    return withoutMarks
        .lowercase()
        .replace("[^a-z0-9ñ ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

private fun isIntelligentSevenStartCommand(text: String): Boolean {
    val normalized = normalizeIntelligentSevenCommand(text)
    val words = normalized.split(" ").filter { it.isNotBlank() }
    if (!words.contains("seven")) return false
    return normalized.contains("seven empieza") ||
        normalized.contains("seven empezar") ||
        normalized.contains("seven comencemos") ||
        normalized.contains("seven empecemos")
}

/**
 * Tiempo maximo que el flujo puede permanecer en "preparando la pregunta" antes de
 * que la salvaguarda fuerce la apertura de la escucha. Se fija por encima del tope
 * maximo de una sola reproduccion ([SPEECH_TIMEOUT_MAX_MS]) para no interrumpir una
 * intro larga legitima: solo actua si la presentacion quedo realmente congelada.
 */
private const val PRESENTING_WATCHDOG_MS = 50_000L

/**
 * Pantalla inicial del modo bimodal inteligente.
 *
 * Selector inicial del modo inteligente. La pantalla infantil se abre en una ruta
 * separada con el id de actividad ya fijado, para que el cambio de orientacion no
 * pierda el estado ni regrese al selector.
 */
@Composable
fun BimodalInteractionScreen(onStartActivity: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val activityDao = remember { db.activityDao() }

    ActivitySelector(
        activityDao = activityDao,
        isLoading = false,
        infoMessage = null,
        onPick = { onStartActivity(it.id) },
        onBack = onBack
    )
}

@Composable
fun IntelligentSevenFaceScreen(
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
    IntelligentImmersiveSystemBarsEffect(context)

    // Carga una actividad real con sus preguntas desde Room y la adapta al modelo
    // de dominio que consume el orquestador.
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
                infoMessage = "La actividad \"${entity.name}\" no tiene preguntas. " +
                    "Agrega preguntas antes de iniciar el modo bimodal."
                isLoading = false
                return@launch
            }
            loadedActivity = entity.toDomain(questions.map { it.toDomain() })
            isLoading = false
        }
    }

    LaunchedEffect(activityId) {
        if (activityId != 0L) {
            load(activityId)
        } else {
            infoMessage = "No se recibio una actividad para iniciar."
            isLoading = false
        }
    }

    BackHandler {
        onChangeActivity()
    }

    val active = loadedActivity
    if (active != null) {
        BimodalSession(
            activity = active,
            dataLogger = dataLogger,
            onChangeActivity = onChangeActivity,
            onBack = onBack
        )
    } else {
        IntelligentSevenLoadingOrError(
            isLoading = isLoading,
            infoMessage = infoMessage,
            onBack = onChangeActivity
        )
    }
}

@Composable
private fun IntelligentSevenLoadingOrError(
    isLoading: Boolean,
    infoMessage: String?,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(IntelligentSevenBackground, IntelligentSevenBackgroundAlt)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IntelligentSevenFace(
                expression = IntelligentSevenExpression.READY,
                modifier = Modifier.fillMaxWidth(0.74f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading) {
                CircularProgressIndicator(color = IntelligentModeBlue)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Preparando a Seven",
                    color = IntelligentModePrimaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = infoMessage ?: "No se pudo abrir la actividad.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack) {
                    Text("Volver")
                }
            }
        }
    }
}

@Composable
private fun ActivitySelector(
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
            .background(IntelligentModeBackground)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Modo\nInteligente",
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            color = IntelligentModeTitleText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Selecciona una actividad\npara iniciar el flujo",
            fontSize = 17.sp,
            lineHeight = 23.sp,
            color = IntelligentModeSecondaryText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (infoMessage != null) {
            InfoBanner(infoMessage)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IntelligentModeBlue)
            }
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
                        color = IntelligentModePrimaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Crea una actividad desde el Panel Docente para iniciar el modo inteligente.",
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = IntelligentModeSecondaryText,
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
                    IntelligentActivityCard(
                        activity = activity,
                        onStart = { onPick(activity) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        IntelligentBottomBackButton(onBack = onBack)
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun IntelligentActivityCard(
    activity: ActivityEntity,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(0.86f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IntelligentModeCardTurquoise),
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
                color = IntelligentModePrimaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = activity.topic,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = IntelligentModeSecondaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            IntelligentStartButton(onStart = onStart)
        }
    }
}

@Composable
private fun IntelligentStartButton(onStart: () -> Unit) {
    Button(
        onClick = onStart,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IntelligentModeBlue,
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
private fun IntelligentBottomBackButton(onBack: () -> Unit) {
    Button(
        onClick = onBack,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IntelligentModeBlue,
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
private fun BimodalSession(
    activity: LearningActivity,
    dataLogger: InteractionDataLogger,
    onChangeActivity: () -> Unit,
    onBack: () -> Unit
) {
    // Una sola instancia del orquestador por actividad cargada.
    val orchestrator = remember(activity) { BimodalFlowOrchestrator() }

    // Adaptador puro que conecta el evaluador semantico local con el protocolo
    // del orquestador. Una sola instancia por actividad cargada.
    val semanticAdapter = remember(activity) { SemanticEvaluationAdapter() }

    // Generador de retroalimentacion general tipo profesor: produce localmente
    // frases breves y variadas por categoria, sin IA ni servicios externos. Mantiene
    // estado (ultima frase por categoria) para no repetir, por lo que vive una sola
    // instancia por actividad cargada.
    val feedbackGenerator = remember(activity) { GeneralTeacherFeedbackGenerator() }

    // Banco local avanzado de mediacion ludica para la actividad de animales. No usa
    // IA generativa, no llama APIs externas y no depende de internet: a partir de la
    // clave de mediacion local de cada pregunta elige frases calidas y variadas,
    // evitando repetir la misma de forma consecutiva. Para claves generales o no
    // reconocidas recurre al banco general tipo profesor. Mantiene estado, por lo que
    // vive una sola instancia por actividad cargada.
    val animalBank = remember(activity) { AnimalMediationBank(generalFallback = feedbackGenerator) }

    // Frases de pausa facial: Seven anuncia que perdio el rostro, que lo recupero y
    // propone retomar la pregunta. No requiere red ni IA. Una instancia por actividad.
    val facePhraseBank = remember(activity) { FacePausePhraseBank() }

    // Diagnostico de la ultima mediacion para la interfaz tecnica: origen efectivo
    // (siempre local en esta actividad), latencia de seleccion, tipo y motivo del
    // respaldo, si lo hubo. No contiene texto del nino.
    var lastMediationSource by remember(activity) { mutableStateOf<MediationSource?>(null) }
    var lastMediationLatencyMs by remember(activity) { mutableStateOf<Long?>(null) }
    var lastMediationType by remember(activity) { mutableStateOf<GenerativeMediationType?>(null) }
    var lastMediationFallbackReason by remember(activity) { mutableStateOf<String?>(null) }

    // El orquestador es una maquina de estados plana; reflejamos sus valores en
    // estado Compose y los sincronizamos despues de cada evento.
    var state by remember(activity) { mutableStateOf(orchestrator.state) }
    var progress by remember(activity) { mutableStateOf(orchestrator.progress) }
    var lastResult by remember(activity) { mutableStateOf(orchestrator.lastResult) }
    var errorMessage by remember(activity) { mutableStateOf(orchestrator.errorMessage) }
    var summary by remember(activity) { mutableStateOf(orchestrator.summary) }

    // Origen del ultimo resultado semantico mostrado (evaluacion real vs. control
    // tecnico de simulacion) y latencia aproximada de la evaluacion real, para
    // diferenciar claramente en la UI lo real de lo simulado.
    var semanticSource by remember(activity) { mutableStateOf<SemanticSource?>(null) }
    var semanticLatencyMs by remember(activity) { mutableStateOf<Long?>(null) }

    // Ultimo mensaje de retroalimentacion general generado (categoria + texto +
    // latencia de generacion local) para mostrarlo en la tarjeta de feedback.
    var lastFeedbackMessage by remember(activity) { mutableStateOf<GeneralTeacherFeedbackMessage?>(null) }

    // Tracker de latencia de la sesion: mide cada ciclo de respuesta real (desde la
    // transcripcion disponible hasta la respuesta logica y el inicio del feedback) y
    // expone un resumen agregado para mostrarlo en pantalla y validar el objetivo
    // tecnico del charter (< 1.5 s). Solo guarda marcas de tiempo, nunca datos del nino.
    val latencyTracker = remember(activity) { BimodalLatencyTracker() }
    var latencyStats by remember(activity) { mutableStateOf(BimodalLatencyStats()) }

    val scope = rememberCoroutineScope()
    var logSessionId by remember(activity) { mutableStateOf(-1L) }
    var logAttemptId by remember(activity) { mutableStateOf(-1L) }

    // Version del job de pausa facial: se incrementa cada vez que el orquestador
    // entra a PAUSED_FACE_LOST durante una pregunta activa. Usar esta clave como
    // clave del LaunchedEffect correspondiente garantiza que el job de pausa se
    // reinicia en cada nueva perdida, pero NO se cancela cuando el rostro vuelve
    // (porque la clave no cambia al salir de PAUSED_FACE_LOST). Esto permite que
    // Seven termine de decir las frases de recuperacion sin ser interrumpido.
    var faceLostJobVersion by remember(activity) { mutableIntStateOf(0) }

    fun sync() {
        state = orchestrator.state
        progress = orchestrator.progress
        lastResult = orchestrator.lastResult
        errorMessage = orchestrator.errorMessage
        summary = orchestrator.summary
    }

    fun dispatch(action: () -> Unit) {
        action()
        sync()
    }

    val context = LocalContext.current
    val cameraGranted = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ----- Captura de voz real -------------------------------------------------
    // Reutiliza el servicio existente de reconocimiento de voz. Una sola instancia
    // por sesion; se libera al salir de la pantalla en el DisposableEffect.
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
    var sttError by remember(activity) { mutableStateOf("") }
    var startCommandDetected by remember(activity) { mutableStateOf(false) }
    var startCommandHint by remember(activity) { mutableStateOf("Di: Seven, empieza") }

    // Banderas internas del intento de captura en curso (no dirigen la UI).
    val capturedAnyText = remember(activity) { mutableStateOf(false) }
    val outcomeDelivered = remember(activity) { mutableStateOf(false) }

    // Traduce el resultado bruto de la captura al evento del orquestador. Solo se
    // entrega una vez por intento y solo si el flujo sigue en LISTENING (evita
    // eventos espurios si la presencia se perdio o el intento ya se resolvio).
    fun deliverCaptureOutcome(outcome: SpeechCaptureOutcome) {
        if (outcomeDelivered.value) return
        outcomeDelivered.value = true
        if (orchestrator.state == BimodalInteractionState.LISTENING) {
            dispatch { orchestrator.onEvent(SpeechCaptureEventMapper.toEvent(outcome)) }
        }
    }

    fun startVoiceCapture() {
        // No iniciar sin permiso de microfono ni fuera del momento de respuesta.
        if (!audioGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (orchestrator.state != BimodalInteractionState.PRESENTING_QUESTION) return
        if (sttState == SttState.LISTENING || sttState == SttState.STOPPING) return

        sttPartial = ""
        sttFinal = ""
        sttError = ""
        capturedAnyText.value = false
        outcomeDelivered.value = false

        // Abre la ventana de escucha en el orquestador antes de encender el microfono.
        dispatch { orchestrator.startListening() }

        // Inicia un nuevo ciclo de medicion de latencia al encender el microfono.
        latencyTracker.beginCapture()

        val sttSid = logSessionId
        val sttAid = logAttemptId
        if (sttSid > 0L) {
            scope.launch {
                runCatching {
                    dataLogger.logTechnicalEvent(
                        sessionId = sttSid,
                        questionId = progress?.currentQuestionId?.toLongOrNull(),
                        attemptId = if (sttAid > 0L) sttAid else null,
                        operationMode = "ADVANCED",
                        eventType = "STT_STARTED"
                    )
                }
            }
        }

        speechService.startListening(
            onStateChange = { newState -> sttState = newState },
            onReady = {},
            onPartialResult = { text ->
                sttPartial = text
                capturedAnyText.value = true
            },
            onFinalResult = { text ->
                sttFinal = text
                sttPartial = ""
                deliverCaptureOutcome(
                    if (text.isBlank()) SpeechCaptureOutcome.NoSpeech
                    else SpeechCaptureOutcome.Transcribed(text)
                )
            },
            onStopped = { textAtStop ->
                if (textAtStop.isNotBlank()) {
                    sttFinal = textAtStop
                    sttPartial = ""
                    deliverCaptureOutcome(SpeechCaptureOutcome.Transcribed(textAtStop))
                } else {
                    deliverCaptureOutcome(SpeechCaptureOutcome.NoSpeech)
                }
            },
            onError = { message ->
                sttError = message
                val errSid = logSessionId
                val errAid = logAttemptId
                if (errSid > 0L) {
                    scope.launch {
                        runCatching {
                            dataLogger.logTechnicalEvent(
                                sessionId = errSid,
                                questionId = progress?.currentQuestionId?.toLongOrNull(),
                                attemptId = if (errAid > 0L) errAid else null,
                                operationMode = "ADVANCED",
                                eventType = "STT_ERROR"
                            )
                        }
                    }
                }
                // Sin texto previo lo tratamos como ausencia de voz (sin respuesta);
                // con texto previo, como fallo no interpretable. Nunca como incorrecta.
                deliverCaptureOutcome(
                    if (capturedAnyText.value) SpeechCaptureOutcome.Failed(message)
                    else SpeechCaptureOutcome.NoSpeech
                )
            }
        )
    }

    fun startCommandListening() {
        if (!audioGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (startCommandDetected) return
        if (orchestrator.state != BimodalInteractionState.IDLE &&
            orchestrator.state != BimodalInteractionState.READY
        ) return
        if (sttState == SttState.LISTENING || sttState == SttState.STOPPING) return

        speechService.startListening(
            onStateChange = { sttState = it },
            onReady = { startCommandHint = "Di: Seven, empieza" },
            onPartialResult = { text ->
                if (isIntelligentSevenStartCommand(text)) {
                    startCommandDetected = true
                    speechService.stopListening()
                }
            },
            onFinalResult = { text ->
                if (isIntelligentSevenStartCommand(text)) {
                    startCommandDetected = true
                } else {
                    startCommandHint = "Di: Seven, empieza"
                }
            },
            onStopped = { textAtStop ->
                if (isIntelligentSevenStartCommand(textAtStop)) {
                    startCommandDetected = true
                }
            },
            onError = {
                startCommandHint = "Di: Seven, empieza"
            }
        )
    }

    fun stopVoiceCapture() {
        speechService.stopListening()
    }

    // Libera el reconocedor al salir de la pantalla (evita fugas y multiples
    // instancias). BimodalSession abandona la composicion al cambiar de actividad
    // o volver, por lo que un solo DisposableEffect basta.
    DisposableEffect(Unit) {
        onDispose { speechService.destroy() }
    }

    // Detiene el microfono si el flujo abandona LISTENING por otra via (p. ej. se
    // pierde la presencia facial) mientras la captura sigue activa.
    LaunchedEffect(state) {
        if (state != BimodalInteractionState.LISTENING && sttState == SttState.LISTENING) {
            speechService.stopListening()
        }
    }

    // Limpia la transcripcion y el resultado semantico mostrados al (re)iniciar
    // una pregunta o intento.
    LaunchedEffect(progress?.currentQuestionIndex, progress?.currentAttempt) {
        sttPartial = ""
        sttFinal = ""
        sttError = ""
        semanticSource = null
        semanticLatencyMs = null
        lastFeedbackMessage = null
    }

    // Tope de tiempo de respuesta: arranca al comenzar la escucha y usa el tiempo
    // maximo efectivo de la pregunta (con valor seguro por defecto si no define uno
    // valido). Al cumplirse, suprime el desenlace de captura, detiene la escucha y
    // marca TIME_EXPIRED en el orquestador, que decidira reintento o avance segun
    // los intentos disponibles. Un unico efecto por ventana de escucha: se re-lanza
    // al cambiar sttState (incluido cada reintento) y se cancela al salir de
    // LISTENING, al cambiar de pregunta o al abandonar la pantalla, evitando
    // temporizadores duplicados o colgados.
    LaunchedEffect(sttState) {
        if (sttState != SttState.LISTENING) return@LaunchedEffect
        val seconds = progress?.effectiveMaxTimeSeconds ?: DEFAULT_MAX_TIME_SECONDS
        delay(seconds * 1000L)
        // Evita que el desenlace de captura (onStopped/onError) tambien resuelva el
        // intento: el tiempo agotado tiene prioridad y se trata como tal.
        outcomeDelivered.value = true
        speechService.stopListening()
        if (orchestrator.state == BimodalInteractionState.LISTENING) {
            dispatch { orchestrator.onTimeExpired() }
        }
    }

    // Presencia facial confirmada que reporta la camara (con debounce aplicado).
    var facePresent by remember(activity) { mutableStateOf(false) }

    // Traduce un cambio real de presencia (evento de camara) en evento del
    // orquestador. Solo se invoca cuando la camara confirma una transicion, por
    // lo que no interfiere con los botones de simulacion (que nunca llaman aqui).
    fun onPresenceTransition(present: Boolean) {
        facePresent = present
        if (present) {
            when (orchestrator.state) {
                BimodalInteractionState.WAITING_FOR_FACE ->
                    dispatch { orchestrator.onFaceDetected() }
                BimodalInteractionState.PAUSED_FACE_LOST ->
                    // Rostro recuperado durante una pregunta activa: el orquestador
                    // retoma PRESENTING_QUESTION sin reiniciar el intento.
                    dispatch { orchestrator.onFaceDetected() }
                else -> Unit
            }
        } else {
            // Si la perdida ocurre durante una pregunta activa, incrementamos la
            // version del job de pausa ANTES del dispatch para que el LaunchedEffect
            // correspondiente arranque con la version correcta desde el principio.
            val pauseableStates = setOf(
                BimodalInteractionState.PRESENTING_QUESTION,
                BimodalInteractionState.WAITING_FOR_RESPONSE,
                BimodalInteractionState.LISTENING
            )
            if (orchestrator.state in pauseableStates) {
                faceLostJobVersion++
            }
            dispatch { orchestrator.onFaceLost() }
        }
    }

    // Si el rostro ya esta presente cuando el flujo vuelve a esperar rostro (por
    // ejemplo al pasar a la siguiente pregunta sin que el nino se retire),
    // avanzamos sin exigir una nueva aparicion. Solo hace avanzar, nunca revierte.
    LaunchedEffect(state) {
        if (state == BimodalInteractionState.WAITING_FOR_FACE && facePresent) {
            dispatch { orchestrator.onFaceDetected() }
        }
    }

    val currentQuestion: LearningQuestion? =
        progress?.let { activity.questions.getOrNull(it.currentQuestionIndex) }

    // Evaluacion semantica real: cuando una captura de voz real deja el flujo en
    // EVALUATING, toma la transcripcion final registrada en el orquestador y la
    // pregunta actual, invoca el evaluador semantico local (con medicion de
    // latencia) y entrega el resultado al orquestador. Los controles de
    // simulacion no pasan por aqui: resuelven EVALUATING en el mismo evento, por
    // lo que el estado nunca queda asentado en EVALUATING para este efecto.
    LaunchedEffect(state) {
        if (state != BimodalInteractionState.EVALUATING) return@LaunchedEffect
        val transcription = orchestrator.progress?.lastTranscription
        val question = currentQuestion
        if (transcription == null || question == null) return@LaunchedEffect

        // La transcripcion ya esta disponible: punto de partida del objetivo de
        // latencia del charter (desde la transcripcion hasta la respuesta logica).
        latencyTracker.markSttFinal()

        // Si el evaluador semantico fallara, no se cancela la sesion ni se marca la
        // respuesta como incorrecta: se reporta como error tecnico recuperable.
        latencyTracker.markSemanticStart()
        val outcome = runCatching { semanticAdapter.evaluate(transcription, question) }
            .getOrNull()
        latencyTracker.markSemanticEnd()
        if (outcome == null) {
            dispatch { orchestrator.reportRecoverableError("No se pudo evaluar la respuesta.") }
            return@LaunchedEffect
        }
        semanticSource = SemanticSource.REAL
        semanticLatencyMs = outcome.latencyMillis
        // Log seguro: solo el resultado semantico y la latencia, nunca la
        // transcripcion ni datos del nino.
        Log.d(
            BIMODAL_SEMANTIC_TAG,
            "evaluacion: resultadoCrudo=${outcome.result} latenciaMs=${outcome.latencyMillis}"
        )
        dispatch { orchestrator.onEvent(outcome.toEvent()) }
        Log.d(
            BIMODAL_SEMANTIC_TAG,
            "mapeo: estado=${orchestrator.state} " +
                "resultadoMapeado=${orchestrator.lastResult?.semanticResult} " +
                "resumen[correctas=${orchestrator.summary.correct} " +
                "incorrectas=${orchestrator.summary.incorrect} " +
                "noInterpretables=${orchestrator.summary.notInterpretable}]"
        )
    }

    // ----- Voz del juguete -----------------------------------------------------
    // El juguete lee la pregunta usando la voz oficial de Seven: OpenAI TTS como
    // proveedor principal, Azure como respaldo y voz local como ultimo fallback.
    // Reutiliza el motor TTS local (LocalToyVoiceProvider) para no duplicar
    // instancias. Los proveedores se liberan al salir de la pantalla.
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
    val sevenVoiceService = remember {
        SevenVoiceService(
            openAiProvider = openAiVoiceProvider,
            azureProvider = azureVoiceProvider,
            localProvider = localVoiceProvider
        )
    }

    DisposableEffect(Unit) {
        toySpeechService.initialize { newState -> ttsStateFlow.value = newState }
        onDispose {
            toySpeechService.shutdown()
            sevenVoiceService.release()
        }
    }

    // Indica si el juguete esta reproduciendo voz en este momento (para la UI).
    var toyVoiceSpeaking by remember(activity) { mutableStateOf(false) }

    // Ultima frase reproducida por el juguete (para mostrarla en la UI).
    var lastSpokenPhrase by remember(activity) { mutableStateOf<String?>(null) }

    // Diagnostico de voz: proveedor que realmente atendio la ultima reproduccion y
    // si hubo respaldo. Permiten verificar en pantalla que el flujo bimodal usa
    // OpenAI TTS como proveedor principal y fallback solo cuando corresponde.
    var lastVoiceProviderUsed by remember(activity) { mutableStateOf<String?>(null) }
    var lastVoiceFallbackUsed by remember(activity) { mutableStateOf<Boolean?>(null) }

    // Registra que proveedor termino reproduciendo la frase y si hubo fallback,
    // tanto en la UI como en un log seguro (solo nombres de proveedor, nunca
    // claves, tokens ni el texto reproducido).
    fun recordVoiceUsage(outcome: VoiceOutcome?) {
        val selectedLabel = providerLabel(ToyVoiceProviderType.OPENAI_TTS)
        val usedLabel: String
        val fallback: Boolean
        when (outcome) {
            is VoiceOutcome.Completed -> {
                usedLabel = providerLabel(outcome.providerUsed)
                fallback = outcome.fallbackUsed
            }
            is VoiceOutcome.Failed, null -> {
                usedLabel = "Ninguno"
                fallback = false
            }
            is VoiceOutcome.SkippedInvalidText -> {
                usedLabel = "Ninguno"
                fallback = false
            }
        }
        lastVoiceProviderUsed = usedLabel
        lastVoiceFallbackUsed = fallback
        Log.d(
            BIMODAL_VOICE_TAG,
            "reproduccion: seleccionado=$selectedLabel usado=$usedLabel fallback=$fallback " +
                "cacheHit=${outcome?.cacheHit} cacheKey=${outcome?.cacheKey} " +
                "synthesisLatencyMs=${outcome?.synthesisLatencyMs} " +
                "playbackLatencyMs=${outcome?.playbackLatencyMs} totalLatencyMs=${outcome?.totalLatencyMs}"
        )
    }

    // Reproduce una frase con la voz del juguete y SUSPENDE hasta que el audio
    // termina realmente: los proveedores (Azure/ElevenLabs/local) completan su
    // `speak` solo cuando el TTS o la red senalan el fin de la reproduccion. Es el
    // unico punto de reproduccion del flujo: delega en SevenVoiceService la cadena
    // OpenAI -> Azure -> local, mantiene el indicador "hablando" y el registro, y
    // garantiza que el flujo nunca avance, reintente ni cierre antes de que la voz
    // haya terminado. Nunca lanza: si la voz falla, el error se ignora y la
    // interaccion continua (la sesion jamas se cancela por un problema de audio).
    //
    // Si la corrutina que la invoca se cancela (por ejemplo, el docente fuerza una
    // accion manual), el bloque finally detiene el audio residual de forma ordenada.
    suspend fun speakAndAwait(text: String) {
        lastSpokenPhrase = text
        toyVoiceSpeaking = true
        try {
            // Tope de seguridad: si el proveedor de voz se cuelga (red caida, callback
            // de fin que nunca llega), withTimeoutOrNull cancela la reproduccion y
            // devuelve null en lugar de bloquear el flujo para siempre. El bloque
            // finally detiene el audio residual de forma ordenada. La interaccion
            // jamas se detiene por un problema de audio.
            val timeoutMs = speechTimeoutMsFor(text)
            val outcome = withTimeoutOrNull(timeoutMs) {
                runCatching {
                    sevenVoiceService.speak(text, source = "inteligente")
                }.getOrNull()
            }
            if (outcome == null) {
                Log.w(
                    BIMODAL_VOICE_TAG,
                    "voz: sin resultado tras ${timeoutMs}ms (timeout o fallo); el flujo continua"
                )
            }
            if (outcome is VoiceOutcome.SkippedInvalidText && logSessionId > 0L) {
                runCatching {
                    dataLogger.logTechnicalEvent(
                        sessionId = logSessionId,
                        questionId = progress?.currentQuestionId?.toLongOrNull(),
                        attemptId = logAttemptId.takeIf { it > 0L },
                        operationMode = "ADVANCED",
                        eventType = "TTS_SKIPPED_INVALID_TEXT",
                        message = "providerRequested=OPENAI_TTS providerUsed=NONE " +
                            "textLength=${outcome.textLength} reason=${outcome.reason}",
                        latencyMs = outcome.latencyMs
                    )
                }
            }
            recordVoiceUsage(outcome)
        } finally {
            sevenVoiceService.stop()
            toyVoiceSpeaking = false
        }
    }

    // Marca si Seven ya dio el saludo inicial de bienvenida en esta sesion. El saludo
    // suena una sola vez, al detectar el rostro por primera vez (en la primera
    // presentacion de pregunta), y nunca se repite aunque el rostro se pierda y vuelva.
    // Se reinicia al (re)iniciar la interaccion para que una sesion nueva pueda
    // saludar de nuevo.
    val initialGreetingSpoken = remember(activity) { booleanArrayOf(false) }

    // Cuando el flujo presenta una pregunta, el juguete la lee y, al terminar,
    // abre automaticamente la escucha si hay permiso de microfono. La clave cambia
    // en cada (re)presentacion (incluido un reintento) para releer la pregunta.
    // PAUSED_FACE_LOST tambien mantiene la clave activa para que la voz que ya
    // empezo no se corte a media frase cuando se pierde el rostro.
    val presentationKey = when (state) {
        BimodalInteractionState.PRESENTING_QUESTION,
        BimodalInteractionState.PAUSED_FACE_LOST ->
            progress?.let { "${it.currentQuestionIndex}:${it.currentAttempt}" }
        else -> null
    }
    LaunchedEffect(presentationKey) {
        if (presentationKey == null) return@LaunchedEffect

        val qIdLong = currentQuestion?.id?.toLongOrNull() ?: 0L
        val sid = logSessionId
        if (sid > 0L && qIdLong > 0L) {
            logAttemptId = runCatching {
                dataLogger.logAttemptStarted(
                    sessionId = sid,
                    questionId = qIdLong,
                    questionOrder = progress?.currentQuestionIndex ?: 0,
                    attemptNumber = progress?.currentAttempt ?: 1,
                    operationMode = "ADVANCED",
                    questionText = currentQuestion?.questionText,
                    maxTimeMs = (currentQuestion?.maxTimeSeconds ?: DEFAULT_MAX_TIME_SECONDS) * 1000L,
                    usedSemanticEvaluation = true,
                    usedSpeechToText = true
                )
            }.getOrElse { -1L }
        }

        val questionText = currentQuestion?.questionText ?: return@LaunchedEffect
        val mediationKey = currentQuestion?.mediationKey
        val keyName = LocalMediationKey.fromKey(mediationKey).name
        Log.d(
            BIMODAL_VOICE_TAG,
            "preparacion: inicio estado=${orchestrator.state} indice=${progress?.currentQuestionIndex} " +
                "intento=${progress?.currentAttempt} clave=$keyName"
        )

        // Construye la escena de presentacion del banco local. Si por cualquier motivo
        // fallara (lista vacia, clave inesperada, excepcion al construir la frase), no
        // se cancela la pregunta: se cae a una frase basica que solo enuncia la
        // pregunta, de modo que el flujo NUNCA se queda en "preparando la pregunta".
        //
        // Las introducciones especificas de animales ya incluyen el enunciado de la
        // pregunta; para una clave general la introduccion es generica y la pregunta
        // se concatena verbatim despues, por lo que su intencion nunca cambia.
        val presentationText = try {
            val introStart = System.nanoTime()
            val introText = animalBank.getQuestionIntroduction(mediationKey)
            lastMediationSource = MediationSource.LOCAL
            lastMediationLatencyMs = (System.nanoTime() - introStart) / 1_000_000
            lastMediationType = GenerativeMediationType.QUESTION_INTRODUCTION
            lastMediationFallbackReason = null
            val scenarioId = animalBank.currentScenarioId(mediationKey)
            Log.d(
                BIMODAL_VOICE_TAG,
                "preparacion: intro lista origen=local clave=$keyName escenario=${scenarioId ?: "general"}"
            )
            val sceneText =
                if (LocalMediationKey.fromKey(mediationKey) == LocalMediationKey.NONE) {
                    animalBank.getGenericIntroWithQuestion(questionText)
                } else {
                    introText
                }
            // Microdiálogo breve y ocasional antes de la escena (el banco decide si
            // incluirlo segun probabilidad interna, para no alargar la interaccion).
            val microDialogue = animalBank.getMicroDialogue()
            if (microDialogue != null) "$microDialogue $sceneText" else sceneText
        } catch (e: Exception) {
            // Frase basica de respaldo: nunca cancela la pregunta.
            lastMediationFallbackReason = "intro_local_fallida"
            Log.w(
                BIMODAL_VOICE_TAG,
                "preparacion: fallo al construir la intro, uso frase basica (clave=$keyName)"
            )
            "Ahora dime: $questionText"
        }

        // Saludo inicial: la primera vez que se presenta una pregunta tras detectar el
        // rostro (solo la primera pregunta y una unica vez por sesion), Seven saluda y
        // luego anuncia el inicio de la mision, ANTES de la introduccion de la pregunta.
        // Se marca como dado de inmediato para no repetirlo si el rostro se pierde y
        // vuelve durante la sesion.
        val playInitialGreeting =
            !initialGreetingSpoken[0] && (progress?.currentQuestionIndex ?: 0) == 0
        if (playInitialGreeting) initialGreetingSpoken[0] = true

        // Reproduce la presentacion en segmentos cortos: saludo inicial, inicio de
        // mision e introduccion divida por oraciones. Entre segmentos comprueba si hubo
        // una perdida de rostro (faceLostJobVersion cambia respecto del inicio): de ser
        // asi, deja de hablar y cede el control al job de pausa, que retomara la pregunta
        // cuando el rostro vuelva. Asi, si el nino sale del encuadre durante una intro
        // larga, Seven entra en pausa al terminar el segmento en curso en vez de leer
        // toda la introduccion antes de poder reaccionar. SUSPENDE en cada segmento hasta
        // que su audio termina, de modo que la voz nunca se solapa con la captura.
        val versionAtStart = faceLostJobVersion
        val presentationSegments = buildList {
            if (playInitialGreeting) {
                add(animalBank.getInitialFaceGreetingPhrase())
                add(animalBank.getSessionStartPhrase())
            }
            addAll(splitIntoSpeechSegments(presentationText))
        }
        Log.d(
            BIMODAL_VOICE_TAG,
            "preparacion: reproduccion intro inicio (${presentationSegments.size} segmentos)"
        )
        var interruptedByFaceLost = false
        for (segment in presentationSegments) {
            if (faceLostJobVersion != versionAtStart) {
                interruptedByFaceLost = true
                break
            }
            speakAndAwait(segment)
        }
        Log.d(BIMODAL_VOICE_TAG, "preparacion: reproduccion intro fin estado=${orchestrator.state}")

        // Garantiza la salida de "preparando la pregunta": si NO hubo perdida de rostro
        // (en ese caso el job de pausa reabre la escucha al retomar) y seguimos
        // presentando con permiso de microfono, abre la escucha. Si no, queda el boton
        // manual.
        if (!interruptedByFaceLost &&
            faceLostJobVersion == versionAtStart &&
            orchestrator.state == BimodalInteractionState.PRESENTING_QUESTION &&
            audioGranted
        ) {
            startVoiceCapture()
        }
    }

    // Salvaguarda contra bloqueos en "preparando la pregunta": si el flujo se queda en
    // PRESENTING_QUESTION sin avanzar a la escucha (p. ej. una reproduccion que no
    // termina y se queda colgada pese al tope de speakAndAwait), tras un tiempo
    // prudente se fuerza la apertura de la captura de voz. Nunca deja la interaccion
    // congelada de forma indefinida. Se re-lanza por (re)presentacion.
    LaunchedEffect(presentationKey) {
        if (presentationKey == null) return@LaunchedEffect
        delay(PRESENTING_WATCHDOG_MS)
        if (orchestrator.state == BimodalInteractionState.PRESENTING_QUESTION &&
            audioGranted &&
            sttState != SttState.LISTENING &&
            sttState != SttState.STOPPING
        ) {
            Log.w(
                BIMODAL_VOICE_TAG,
                "preparacion: watchdog disparado tras ${PRESENTING_WATCHDOG_MS}ms, abro la escucha"
            )
            if (toyVoiceSpeaking) {
                sevenVoiceService.stop()
            }
            startVoiceCapture()
        }
    }

    // ----- Pausa y reanudacion por perdida de rostro durante pregunta activa --------
    // Se dispara cada vez que faceLostJobVersion cambia (es decir, cada vez que el
    // rostro se pierde mientras hay una pregunta activa). La clave es un contador, no
    // un booleano, para que el job NO se cancele cuando el rostro vuelve (al retornar
    // a PRESENTING_QUESTION el contador no cambia). Esto permite que Seven termine la
    // frase de recuperacion sin ser interrumpido. Si el rostro se pierde de nuevo
    // durante la recuperacion, la version incrementa y el job anterior se cancela.
    LaunchedEffect(faceLostJobVersion) {
        if (faceLostJobVersion == 0) return@LaunchedEffect

        val sid = logSessionId
        val aid = logAttemptId
        val questionText = currentQuestion?.questionText ?: ""
        val qId = progress?.currentQuestionId?.toLongOrNull()

        // Evento tecnico: rostro perdido durante pregunta (sin datos del nino).
        if (sid > 0L) {
            runCatching {
                dataLogger.logTechnicalEvent(
                    sessionId = sid,
                    questionId = qId,
                    attemptId = if (aid > 0L) aid else null,
                    operationMode = "ADVANCED",
                    eventType = "FACE_LOST"
                )
            }
        }

        // Espera a que termine cualquier locución en curso para no cortar a Seven
        // a media frase (p. ej. si el rostro se pierde durante la introduccion).
        snapshotFlow { toyVoiceSpeaking }.first { !it }

        // Si el orquestador sigue en pausa, Seven avisa que no detecta el rostro.
        if (orchestrator.state == BimodalInteractionState.PAUSED_FACE_LOST) {
            speakAndAwait(facePhraseBank.getFaceLostPhrase())
        }

        // Espera a que el rostro vuelva (PAUSED_FACE_LOST → PRESENTING_QUESTION o
        // la sesion termine/cancele). La resolucion la hace onPresenceTransition,
        // que invoca orchestrator.onFaceDetected() al confirmar el rostro.
        if (orchestrator.state == BimodalInteractionState.PAUSED_FACE_LOST) {
            snapshotFlow { state }.first { it != BimodalInteractionState.PAUSED_FACE_LOST }
        }

        // Rostro recuperado: confirmar, retomar la misma pregunta y reabrir escucha.
        if (orchestrator.state == BimodalInteractionState.PRESENTING_QUESTION) {
            if (sid > 0L) {
                runCatching {
                    dataLogger.logTechnicalEvent(
                        sessionId = sid,
                        questionId = qId,
                        attemptId = if (aid > 0L) aid else null,
                        operationMode = "ADVANCED",
                        eventType = "FACE_RETURNED"
                    )
                }
            }
            speakAndAwait(facePhraseBank.getFaceReturnedPhrase())
            if (questionText.isNotBlank()) {
                speakAndAwait(facePhraseBank.getResumeQuestionPhrase(questionText))
            }
            // Reabre la escucha para el mismo intento.
            if (orchestrator.state == BimodalInteractionState.PRESENTING_QUESTION && audioGranted) {
                startVoiceCapture()
            }
        }
    }

    // El saludo inicial de bienvenida y el inicio de mision ya no se reproducen
    // mientras se espera el rostro: ahora suenan al detectar al nino por primera vez,
    // dentro de la presentacion de la primera pregunta (ver el efecto de presentacion
    // mas arriba). Asi Seven saluda cuando realmente ve al nino y respeta el orden
    // saludo -> inicio de mision -> introduccion de la pregunta. El paso entre
    // preguntas no agrega voz propia: cada presentacion lee su introduccion.

    // ----- Retroalimentacion de voz + avance automatico del flujo -----------------
    // Cuando la pregunta llega a un desenlace (feedback o tiempo agotado), este unico
    // efecto ejecuta TODA la secuencia de forma estrictamente secuencial y sin
    // solapes, de modo que el flujo nunca avanza, reintenta ni cierra antes de que la
    // voz haya terminado por completo:
    //   1) cierra la medicion de latencia del ciclo (hasta el inicio del feedback);
    //   2) construye la retroalimentacion local de la respuesta del nino y la
    //      reproduce, SUSPENDIENDO hasta que el audio termina (speakAndAwait);
    //   3) solo entonces aplica la accion que decide el orquestador:
    //        - reintentar la misma pregunta (quedan intentos), o
    //        - avanzar a la siguiente (sin intentos y no es la ultima), o
    //        - en la ultima pregunta, reproducir el cierre completo y solo despues
    //          marcar la sesion como completada.
    //
    // La clave dispara el efecto una sola vez por desenlace real (estado + pregunta +
    // intento). Si el docente fuerza una accion manual, el cambio de estado cancela
    // este efecto y speakAndAwait detiene el audio de forma ordenada.
    val autoFlowKey: String? = when (state) {
        BimodalInteractionState.FEEDBACK_CORRECT,
        BimodalInteractionState.FEEDBACK_INCORRECT,
        BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE,
        BimodalInteractionState.FEEDBACK_NO_RESPONSE,
        BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR,
        BimodalInteractionState.TIME_EXPIRED ->
            "${state.name}:${progress?.currentQuestionIndex ?: 0}:${progress?.currentAttempt ?: 0}"
        else -> null
    }
    LaunchedEffect(autoFlowKey) {
        if (autoFlowKey == null) return@LaunchedEffect

        // 1) Cierra la medicion de este ciclo: marca la respuesta logica y el inicio
        // del feedback, consolida la muestra (descarta las no validas) y actualiza
        // el resumen agregado. Solo se registran marcas de tiempo, nunca datos del
        // nino. La latencia se mide hasta el inicio del feedback: no depende de la
        // duracion del audio, que ahora se espera por completo.
        latencyTracker.markLogicalResponse()
        latencyTracker.markFeedbackStart()
        val capturedLatencySample = latencyTracker.current()
        latencyStats = latencyTracker.commit()

        val logAid = logAttemptId
        val logSid = logSessionId
        if (logAid > 0L && logSid > 0L) {
            runCatching {
                dataLogger.finishBimodalAttempt(
                    attemptId = logAid,
                    finalAttemptState = state.name,
                    wasFinalAttempt = !(lastResult?.canRetry ?: false),
                    transcript = lastResult?.transcription,
                    semanticResult = lastResult?.semanticResult?.name,
                    realResponseTimeMs = capturedLatencySample.totalResponseLatencyMs,
                    usedStt = sttFinal.isNotBlank(),
                    latencySample = capturedLatencySample
                )
            }
        }

        Log.d(
            BIMODAL_LATENCY_TAG,
            "latency_logical_ms=${latencyStats.lastResponseLatencyMs} " +
                "latency_feedback_ms=${latencyStats.lastFeedbackLatencyMs} " +
                "latency_pipeline_ms=${latencyStats.lastPipelineLatencyMs} " +
                "latency_average_ms=${latencyStats.averageResponseLatencyMs} " +
                "measurements_count=${latencyStats.validSamples}"
        )

        val qi = progress?.currentQuestionIndex ?: 0
        val canRetry = lastResult?.canRetry ?: false
        val isLast = lastResult?.isLastQuestion ?: false
        val mediationKey = currentQuestion?.mediationKey

        // 2) Construye y reproduce la retroalimentacion de la respuesta del nino.
        // La respuesta esperada solo se pasa como contexto interno: nunca se revela
        // si hay reintento. En la ultima pregunta el desenlace SI produce feedback
        // (el nino lo escucha antes del cierre); la regla de no anunciar un avance
        // inexistente la aplica el banco local al elegir la frase (isLast).
        val feedbackContext = GeneralTeacherFeedbackContext(
            state = state,
            questionIndex = qi,
            currentAttempt = progress?.currentAttempt ?: 1,
            maxAttempts = progress?.maxAttempts ?: 1,
            canRetry = canRetry,
            isLastQuestion = isLast,
            semanticResult = lastResult?.semanticResult,
            questionText = currentQuestion?.questionText,
            expectedAnswer = currentQuestion?.expectedAnswer
        )
        val category = feedbackGenerator.feedbackTypeFor(feedbackContext)
        if (category != null) {
            val feedbackStart = System.nanoTime()
            val spokenText = when (category) {
                GeneralTeacherFeedbackType.CORRECT ->
                    animalBank.getCorrectFeedback(mediationKey, isLast)
                GeneralTeacherFeedbackType.INCORRECT_RETRY ->
                    animalBank.getIncorrectRetryFeedback(mediationKey)
                GeneralTeacherFeedbackType.INCORRECT_NEXT ->
                    animalBank.getIncorrectNextFeedback(mediationKey, isLast)
                GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY,
                GeneralTeacherFeedbackType.NOT_INTERPRETABLE_NEXT ->
                    animalBank.getNotInterpretableFeedback()
                GeneralTeacherFeedbackType.NO_RESPONSE_RETRY,
                GeneralTeacherFeedbackType.NO_RESPONSE_NEXT,
                GeneralTeacherFeedbackType.TIME_EXPIRED_RETRY,
                GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT ->
                    animalBank.getNoResponseFeedback()
                GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY,
                GeneralTeacherFeedbackType.TECHNICAL_ERROR_NEXT ->
                    animalBank.getTechnicalErrorFeedback()
                GeneralTeacherFeedbackType.SESSION_COMPLETED ->
                    animalBank.getSessionCompletedPhrase()
                GeneralTeacherFeedbackType.SESSION_START,
                GeneralTeacherFeedbackType.QUESTION_INTRO ->
                    feedbackGenerator.message(category).text
            }
            lastMediationSource = MediationSource.LOCAL
            lastMediationLatencyMs = (System.nanoTime() - feedbackStart) / 1_000_000
            lastMediationType = GenerativeMediationType.CONTEXTUAL_FEEDBACK
            lastMediationFallbackReason = null
            // La tarjeta de feedback muestra la categoria fijada por el flujo y el
            // texto finalmente reproducido por el banco local.
            lastFeedbackMessage = GeneralTeacherFeedbackMessage(category, spokenText)
            Log.d(BIMODAL_VOICE_TAG, "feedback: categoria=$category mediacion=local")
            // Reproduce el feedback completo: SUSPENDE hasta que el audio termina.
            speakAndAwait(spokenText)
        }

        // 3) Solo despues de que la retroalimentacion termino por completo, decide el
        // avance. La voz nunca se interrumpe para avanzar, reintentar ni cerrar.
        when (orchestrator.resolveAutoAction()) {
            BimodalAutoAction.RETRY -> dispatch { orchestrator.retryQuestion() }
            BimodalAutoAction.ADVANCE -> dispatch { orchestrator.moveToNextQuestion() }
            BimodalAutoAction.COMPLETE -> {
                // Ultima pregunta: tras el feedback de la respuesta, reproduce el
                // cierre COMPLETO y solo entonces marca la sesion como completada.
                // Nunca se salta directamente a SESSION_COMPLETED.
                val closingStart = System.nanoTime()
                val closingText = animalBank.getSessionCompletedPhrase()
                lastMediationSource = MediationSource.LOCAL
                lastMediationLatencyMs = (System.nanoTime() - closingStart) / 1_000_000
                lastMediationType = GenerativeMediationType.CONTEXTUAL_FEEDBACK
                lastMediationFallbackReason = null
                Log.d(BIMODAL_VOICE_TAG, "cierre: mediacion=local")
                speakAndAwait(closingText)
                dispatch { orchestrator.moveToNextQuestion() }
            }
            BimodalAutoAction.NONE -> Unit
        }
    }

    // Registra el cierre de la sesion en Room cuando se alcanza un estado terminal.
    val terminalStateKey = when (state) {
        BimodalInteractionState.SESSION_COMPLETED,
        BimodalInteractionState.SESSION_CANCELLED,
        BimodalInteractionState.ERROR -> state.name
        else -> null
    }
    LaunchedEffect(terminalStateKey) {
        if (terminalStateKey == null) return@LaunchedEffect
        val sid = logSessionId
        if (sid <= 0L) return@LaunchedEffect
        val s = orchestrator.summary
        val startedMs = orchestrator.progress?.sessionStartedAt ?: 0L
        runCatching {
            dataLogger.finishBimodalSession(
                sessionId = sid,
                finalState = terminalStateKey,
                startedAtMs = startedMs,
                completedQuestions = s.resolvedQuestions,
                summary = s
            )
        }
        logSessionId = -1L
    }

    // Inicia la interaccion real en un solo paso: carga la actividad en el
    // orquestador, la confirma y arranca la sesion hasta quedar esperando rostro.
    fun startInteraction() {
        if (sttState == SttState.LISTENING) speechService.stopListening()
        sttPartial = ""
        sttFinal = ""
        sttError = ""
        // Empieza una sesion limpia: descarta las latencias de una corrida anterior.
        latencyTracker.reset()
        latencyStats = BimodalLatencyStats()
        logAttemptId = -1L
        // Una sesion nueva puede volver a dar el saludo inicial de bienvenida.
        initialGreetingSpoken[0] = false
        dispatch {
            orchestrator.loadActivity(activity)
            orchestrator.markActivityLoaded()
            orchestrator.startSession()
        }
        val activityIdLong = activity.id.toLongOrNull() ?: return
        scope.launch {
            runCatching {
                logSessionId = dataLogger.startSession(
                    activityId = activityIdLong,
                    activityName = activity.title,
                    operationMode = "ADVANCED",
                    totalQuestions = activity.questions.size
                )
            }
        }
    }

    LaunchedEffect(startCommandDetected) {
        if (startCommandDetected &&
            (orchestrator.state == BimodalInteractionState.IDLE ||
                orchestrator.state == BimodalInteractionState.READY)
        ) {
            startInteraction()
        }
    }

    LaunchedEffect(state, sttState, audioGranted, startCommandDetected) {
        val waitingForStartCommand =
            (state == BimodalInteractionState.IDLE || state == BimodalInteractionState.READY) &&
                !startCommandDetected
        if (waitingForStartCommand &&
            sttState != SttState.LISTENING &&
            sttState != SttState.STOPPING
        ) {
            delay(350L)
            startCommandListening()
        }
    }

    val targetSevenExpression = state.toIntelligentSevenExpression(
        facePresent = facePresent,
        toyVoiceSpeaking = toyVoiceSpeaking
    )
    var sevenExpression by remember(activity) {
        mutableStateOf(IntelligentSevenExpression.READY)
    }
    var sevenHoldUntilMs by remember(activity) { mutableStateOf(0L) }

    LaunchedEffect(targetSevenExpression) {
        val remainingHoldMs = sevenHoldUntilMs - System.currentTimeMillis()
        if (remainingHoldMs > 0L) {
            delay(remainingHoldMs)
        }
        sevenExpression = targetSevenExpression
        val holdMs = when (targetSevenExpression) {
            IntelligentSevenExpression.HAPPY -> 3_000L
            IntelligentSevenExpression.CONFUSED -> 2_000L
            IntelligentSevenExpression.ENCOURAGING -> 2_500L
            IntelligentSevenExpression.CELEBRATION -> 3_000L
            else -> 0L
        }
        sevenHoldUntilMs = if (holdMs > 0L) System.currentTimeMillis() + holdMs else 0L
    }

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

    IntelligentImmersiveSystemBarsEffect(context)

    BackHandler { onChangeActivity() }

    val childStatusText = when (state) {
        BimodalInteractionState.IDLE,
        BimodalInteractionState.READY ->
            if (audioGranted) startCommandHint else "Concede el microfono para empezar"
        BimodalInteractionState.WAITING_FOR_FACE,
        BimodalInteractionState.PAUSED_FACE_LOST -> "No te veo"
        BimodalInteractionState.FACE_DETECTED -> "Ya te veo"
        BimodalInteractionState.PRESENTING_QUESTION -> "Seven te habla"
        BimodalInteractionState.WAITING_FOR_RESPONSE,
        BimodalInteractionState.LISTENING -> "Tu turno"
        BimodalInteractionState.TRANSCRIBING,
        BimodalInteractionState.EVALUATING -> "Estoy pensando"
        BimodalInteractionState.FEEDBACK_CORRECT -> "Muy bien"
        BimodalInteractionState.FEEDBACK_INCORRECT,
        BimodalInteractionState.FEEDBACK_NO_RESPONSE,
        BimodalInteractionState.TIME_EXPIRED,
        BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR -> "Vamos otra vez"
        BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE -> "No entendi bien"
        BimodalInteractionState.SESSION_COMPLETED -> "Lo logramos"
        BimodalInteractionState.SESSION_CANCELLED -> "Actividad pausada"
        BimodalInteractionState.ERROR -> "Necesito ayuda"
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(IntelligentSevenBackground, IntelligentSevenBackgroundAlt)
                )
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .clipToBounds()
                .align(Alignment.TopStart)
        ) {
            FacePresenceCard(
                cameraGranted = cameraGranted,
                facePresent = facePresent,
                onPresenceChanged = { onPresenceTransition(it) }
            )
        }

        IntelligentSevenFace(
            expression = sevenExpression,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.94f)
                .padding(bottom = 18.dp),
            faceHeight = 372.dp,
            showTurnLabel = state == BimodalInteractionState.LISTENING
        )

        OutlinedButton(
            onClick = onChangeActivity,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(92.dp)
        ) {
            Text("Salir")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (childStatusText.isNotBlank()) {
                Text(
                    text = childStatusText,
                    color = IntelligentModePrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
            if (!audioGranted && (state == BimodalInteractionState.IDLE ||
                    state == BimodalInteractionState.READY)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                ) {
                    Text("Conceder microfono")
                }
            }
            if (state == BimodalInteractionState.SESSION_COMPLETED ||
                state == BimodalInteractionState.ERROR
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onChangeActivity) {
                    Text("Elegir otra actividad")
                }
            }
        }
    }
    return

    // Controla la visibilidad de la seccion tecnica de simulacion (colapsada por
    // defecto para no confundirla con el flujo real).
    var showTechnical by remember(activity) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IntelligentModeBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Modo Inteligente",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        IntelligentSevenFace(
            expression = sevenExpression,
            showTurnLabel = state == BimodalInteractionState.LISTENING
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Resumen del estado de la sesion.
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
                InfoRow("Estado", stateLabel(state))
                val p = progress
                InfoRow(
                    "Progreso",
                    if (p != null) "Pregunta ${p.questionNumber} de ${p.totalQuestions}" else "Sin iniciar"
                )
                InfoRow(
                    "Intento",
                    if (p != null) "${p.currentAttempt} de ${p.maxAttempts}" else "—"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pregunta actual.
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
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

        // Diagnostico tecnico de la voz del juguete: deja claro que proveedor esta
        // seleccionado y cual atendio realmente la ultima reproduccion, y si hubo
        // respaldo a la voz local. Permite verificar que el flujo bimodal usa Azure
        // cuando esta seleccionado y configurado.
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
                    text = "Voz del juguete",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                InfoRow("Proveedor seleccionado", providerLabel(voiceSettings.provider))
                InfoRow("Última reproducción", lastVoiceProviderUsed ?: "—")
                InfoRow(
                    "Fallback usado",
                    when (lastVoiceFallbackUsed) {
                        true -> "Sí"
                        false -> "No"
                        null -> "—"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Diagnostico de la mediacion ludica local: la frase siempre proviene del
        // banco local avanzado (sin IA, sin red). Muestra el origen efectivo, la
        // latencia de seleccion en milisegundos, el tipo de mediacion y el motivo del
        // respaldo, si lo hubo. No muestra ningun indicador de "cumple/no cumple".
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
                    text = "Mediación local",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                InfoRow("Estado", "Banco local de animales")
                InfoRow("Mediación usada", lastMediationSource?.let { mediationSourceLabel(it) } ?: "—")
                InfoRow(
                    "Tipo de mediación",
                    lastMediationType?.let { mediationTypeLabel(it) } ?: "—"
                )
                InfoRow(
                    "Latencia de mediación",
                    lastMediationLatencyMs?.let { "$it ms" } ?: "—"
                )
                InfoRow("Motivo de fallback", lastMediationFallbackReason ?: "—")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Presencia facial real desde la camara frontal.
        FacePresenceCard(
            cameraGranted = cameraGranted,
            facePresent = facePresent,
            onPresenceChanged = { onPresenceTransition(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Captura de voz real del nino mediante reconocimiento de voz.
        val isListeningVoice = sttState == SttState.LISTENING
        val isStoppingVoice = sttState == SttState.STOPPING
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
                Text(
                    text = "Captura de voz",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                if (!audioGranted) {
                    Text(
                        text = "Permiso de micrófono no concedido. Concédelo para capturar la " +
                            "respuesta hablada del niño.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Conceder micrófono")
                    }
                }

                // Estado visual de la escucha.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isListeningVoice) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isListeningVoice) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = sttStatusLabel(sttState),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                AnimatedVisibility(visible = sttPartial.isNotBlank()) {
                    Text(
                        text = "Parcial: $sttPartial",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = sttFinal.isNotBlank()) {
                    Text(
                        text = "Transcripción: $sttFinal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = sttError.isNotBlank()) {
                    Text(
                        text = "Error de voz: $sttError",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Evaluacion semantica: muestra la transcripcion final, la respuesta
        // esperada, las palabras clave, el resultado semantico y, cuando proviene
        // de la evaluacion real, su latencia aproximada y un distintivo de origen.
        val isEvaluating = state == BimodalInteractionState.EVALUATING
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Evaluación semántica",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                InfoRow(
                    "Transcripción final",
                    progress?.lastTranscription ?: sttFinal.ifBlank { "—" }
                )
                InfoRow("Respuesta esperada", currentQuestion?.expectedAnswer ?: "—")
                InfoRow(
                    "Palabras clave",
                    currentQuestion?.keywords
                        ?.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") ?: "—"
                )
                InfoRow(
                    "Resultado semántico",
                    when {
                        isEvaluating -> "Evaluando…"
                        lastResult?.semanticResult != null ->
                            resultLabel(lastResult!!.semanticResult!!) +
                                (semanticSource?.let { " (${it.label})" } ?: "")
                        else -> "—"
                    }
                )
                InfoRow(
                    "Latencia semántica",
                    semanticLatencyMs
                        ?.takeIf { semanticSource == SemanticSource.REAL }
                        ?.let { "≈ $it ms" }
                        ?: "—"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Metricas de latencia del sistema: se muestran en cuanto hay alguna
        // medicion valida o mientras la sesion esta activa, para validar el objetivo
        // tecnico (< 1.5 s) sin esperar al resumen final.
        val sessionStarted = state != BimodalInteractionState.IDLE
        if (sessionStarted || latencyStats.hasData) {
            LatencyMetricsCard(latencyStats)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Accion principal del flujo automatico: un unico control que cambia segun
        // el estado. El docente solo inicia la interaccion; el rostro dispara la
        // pregunta, el juguete la lee, la escucha se abre sola y la evaluacion es
        // automatica. Los estados de retroalimentacion los atiende la tarjeta de
        // mas abajo (reintentar / continuar).
        val sessionTerminal = state == BimodalInteractionState.SESSION_COMPLETED ||
            state == BimodalInteractionState.SESSION_CANCELLED ||
            state == BimodalInteractionState.ERROR
        val sessionActive = state != BimodalInteractionState.IDLE && !sessionTerminal
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
                    BimodalInteractionState.IDLE,
                    BimodalInteractionState.READY -> {
                        Button(
                            onClick = { startInteraction() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Iniciar interacción")
                        }
                    }

                    BimodalInteractionState.SESSION_COMPLETED,
                    BimodalInteractionState.SESSION_CANCELLED,
                    BimodalInteractionState.ERROR -> {
                        Text(
                            text = if (state == BimodalInteractionState.SESSION_COMPLETED) {
                                "La actividad terminó."
                            } else if (state == BimodalInteractionState.SESSION_CANCELLED) {
                                "La interacción se canceló."
                            } else {
                                "La interacción se detuvo por un error."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Button(
                            onClick = { startInteraction() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Iniciar de nuevo")
                        }
                    }

                    BimodalInteractionState.WAITING_FOR_FACE -> {
                        StatusLine("Esperando que el niño se ubique frente a la cámara…")
                    }

                    BimodalInteractionState.PAUSED_FACE_LOST -> {
                        StatusLine("Buscando el rostro del niño… la sesión sigue activa.")
                    }

                    BimodalInteractionState.FACE_DETECTED,
                    BimodalInteractionState.PRESENTING_QUESTION -> {
                        when {
                            !audioGranted -> {
                                Text(
                                    text = "Concede el permiso de micrófono para escuchar la " +
                                        "respuesta del niño.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Button(
                                    onClick = { startVoiceCapture() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Conceder micrófono")
                                }
                            }
                            toyVoiceSpeaking -> StatusLine("Preparando la pregunta…")
                            else -> {
                                Button(
                                    onClick = { startVoiceCapture() },
                                    enabled = state == BimodalInteractionState.PRESENTING_QUESTION &&
                                        !isListeningVoice && !isStoppingVoice,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Escuchar respuesta")
                                }
                            }
                        }
                    }

                    BimodalInteractionState.WAITING_FOR_RESPONSE,
                    BimodalInteractionState.LISTENING -> {
                        StatusLine("Escuchando la respuesta del niño…")
                        OutlinedButton(
                            onClick = { stopVoiceCapture() },
                            enabled = isListeningVoice,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Detener captura")
                        }
                    }

                    BimodalInteractionState.TRANSCRIBING,
                    BimodalInteractionState.EVALUATING -> {
                        StatusLine("Evaluando la respuesta…")
                    }

                    else -> Unit
                }

                if (sessionActive) {
                    OutlinedButton(
                        onClick = { dispatch { orchestrator.cancelSession() } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar interacción")
                    }
                }
            }
        }

        // Acciones del flujo real ante la retroalimentacion: delega siempre en el
        // orquestador (intentos y avance). El boton de reintento solo aparece si
        // el orquestador permite reintentar la pregunta actual.
        val isFeedbackState = state == BimodalInteractionState.FEEDBACK_CORRECT ||
            state == BimodalInteractionState.FEEDBACK_INCORRECT ||
            state == BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ||
            state == BimodalInteractionState.FEEDBACK_NO_RESPONSE ||
            state == BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR
        if (isFeedbackState || state == BimodalInteractionState.TIME_EXPIRED) {
            Spacer(modifier = Modifier.height(8.dp))
            val canRetry = lastResult?.canRetry == true
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
                    Text(
                        text = "Retroalimentación",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = feedbackMessage(state, canRetry),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Mensaje generado tipo profesor (lo que dice el juguete), su
                    // categoria, el resultado semantico, los intentos restantes, el
                    // proveedor de voz y la latencia de generacion local (valor
                    // numerico, no cumplimiento). La sintesis de voz se refleja aparte
                    // ("El juguete esta hablando…") y la latencia logica principal en
                    // la tarjeta de latencia del sistema.
                    val fb = lastFeedbackMessage
                    if (fb != null) {
                        HorizontalDivider()
                        Text(
                            text = "El juguete dice: “${fb.text}”",
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary
                        )
                        InfoRow("Categoría", feedbackTypeLabel(fb.type))
                        InfoRow(
                            "Resultado semántico",
                            lastResult?.semanticResult?.let { resultLabel(it) } ?: "—"
                        )
                        InfoRow(
                            "Intentos restantes",
                            progress?.let {
                                (it.maxAttempts - it.currentAttempt).coerceAtLeast(0).toString()
                            } ?: "—"
                        )
                        InfoRow("Voz", lastVoiceProviderUsed ?: "—")
                        InfoRow(
                            "Fallback de voz",
                            when (lastVoiceFallbackUsed) {
                                true -> "Sí"
                                false -> "No"
                                null -> "—"
                            }
                        )
                        InfoRow(
                            "Latencia de generación",
                            "≈ ${fb.generationLatencyMicros} µs"
                        )
                    }

                    if (state == BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR &&
                        !errorMessage.isNullOrBlank()
                    ) {
                        Text(
                            text = "Detalle técnico: $errorMessage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (canRetry) {
                        Button(
                            onClick = { dispatch { orchestrator.retryQuestion() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Escuchar de nuevo")
                        }
                    }
                    Button(
                        onClick = { dispatch { orchestrator.moveToNextQuestion() } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (lastResult?.isLastQuestion == true) "Finalizar actividad"
                            else "Avanzar a la siguiente"
                        )
                    }
                }
            }
        }

        if (state == BimodalInteractionState.ERROR && errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            InfoBanner("Error: $errorMessage")
        }

        // Resumen tecnico de la sesion al finalizar (solo conteos, sin datos del
        // nino ni multimedia). Se muestra al completar o cancelar la interaccion.
        if ((state == BimodalInteractionState.SESSION_COMPLETED ||
                state == BimodalInteractionState.SESSION_CANCELLED) &&
            summary.resolvedQuestions > 0
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SessionSummaryCard(summary)
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Seccion tecnica de simulacion: separada del flujo real y colapsada por
        // defecto. Permite forzar transiciones del orquestador sin sensores; no
        // forma parte de la interaccion real (la evaluacion ya es automatica).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTechnical = !showTechnical }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Controles técnicos de prueba",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (showTechnical) "Ocultar ▲" else "Mostrar ▼",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showTechnical) {
            TechnicalSimulationControls(
                state = state,
                orchestrator = orchestrator,
                activity = activity,
                currentQuestion = currentQuestion,
                lastResult = lastResult,
                dispatch = { action -> dispatch(action) },
                onSimulatedResult = {
                    semanticSource = SemanticSource.SIMULATED
                    semanticLatencyMs = null
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onChangeActivity,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cambiar de actividad")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ControlButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label)
    }
}

/** Linea de estado con indicador de progreso, para los pasos automaticos del flujo. */
@Composable
private fun StatusLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Controles tecnicos de simulacion del orquestador, separados del flujo real.
 *
 * Fuerzan transiciones del [BimodalFlowOrchestrator] sin sensores para depurar la
 * maquina de estados. La evaluacion semantica real ya es automatica tras la
 * transcripcion; estos botones solo existen para pruebas y marcan su resultado
 * como simulado a traves de [onSimulatedResult].
 */
@Composable
private fun TechnicalSimulationControls(
    state: BimodalInteractionState,
    orchestrator: BimodalFlowOrchestrator,
    activity: LearningActivity,
    currentQuestion: LearningQuestion?,
    lastResult: BimodalInteractionResult?,
    dispatch: (() -> Unit) -> Unit,
    onSimulatedResult: () -> Unit
) {
    val isTerminal = state == BimodalInteractionState.SESSION_COMPLETED ||
        state == BimodalInteractionState.SESSION_CANCELLED ||
        state == BimodalInteractionState.ERROR
    val isFeedback = state == BimodalInteractionState.FEEDBACK_CORRECT ||
        state == BimodalInteractionState.FEEDBACK_INCORRECT ||
        state == BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ||
        state == BimodalInteractionState.FEEDBACK_NO_RESPONSE
    val canAnswer = state == BimodalInteractionState.LISTENING
    // La evaluacion simulada tambien aplica tras una captura de voz real, que deja
    // el flujo en EVALUATING a la espera del resultado semantico.
    val canSimulateEval = canAnswer || state == BimodalInteractionState.EVALUATING
    val canMarkNoResponse = state == BimodalInteractionState.PRESENTING_QUESTION ||
        state == BimodalInteractionState.WAITING_FOR_RESPONSE ||
        state == BimodalInteractionState.LISTENING

    Text(
        text = "Botones de simulación para depurar el orquestador sin sensores. " +
            "Fuerzan un resultado concreto y se marcan como “simulada”; la " +
            "evaluación semántica real ya es automática tras la transcripción.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline
    )
    Spacer(modifier = Modifier.height(8.dp))

    ControlButton(
        label = "Iniciar sesión",
        enabled = state == BimodalInteractionState.IDLE || isTerminal
    ) {
        dispatch {
            orchestrator.loadActivity(activity)
            orchestrator.markActivityLoaded()
        }
    }
    ControlButton(
        label = "Iniciar pregunta",
        enabled = state == BimodalInteractionState.READY
    ) {
        dispatch { orchestrator.startSession() }
    }
    ControlButton(
        label = "Rostro detectado (simulado)",
        enabled = state == BimodalInteractionState.WAITING_FOR_FACE
    ) {
        dispatch { orchestrator.onFaceDetected() }
    }
    ControlButton(
        label = "Iniciar escucha (simulada)",
        enabled = state == BimodalInteractionState.PRESENTING_QUESTION
    ) {
        dispatch { orchestrator.startListening() }
    }
    ControlButton(
        label = "Evaluar como correcta (simulado)",
        enabled = canSimulateEval
    ) {
        onSimulatedResult()
        dispatch {
            // En LISTENING captura una transcripcion simulada; en EVALUATING (tras
            // una captura real) este paso es inocuo y conserva la transcripcion real.
            orchestrator.onSpeechCaptured(currentQuestion?.expectedAnswer ?: "respuesta correcta")
            orchestrator.onSemanticEvaluated(SemanticResult.CORRECT)
        }
    }
    ControlButton(
        label = "Evaluar como incorrecta (simulado)",
        enabled = canSimulateEval
    ) {
        onSimulatedResult()
        dispatch {
            orchestrator.onSpeechCaptured("respuesta incorrecta simulada")
            orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)
        }
    }
    ControlButton(
        label = "Evaluar como no interpretable (simulado)",
        enabled = canSimulateEval
    ) {
        onSimulatedResult()
        dispatch {
            orchestrator.onSpeechCaptured("mmm")
            orchestrator.onSemanticEvaluated(SemanticResult.NOT_INTERPRETABLE)
        }
    }
    ControlButton(
        label = "Simular sin respuesta",
        enabled = canMarkNoResponse
    ) {
        onSimulatedResult()
        dispatch { orchestrator.onNoResponse() }
    }
    ControlButton(
        label = "Simular tiempo agotado",
        enabled = canMarkNoResponse
    ) {
        onSimulatedResult()
        dispatch { orchestrator.onTimeExpired() }
    }
    ControlButton(
        label = "Simular error técnico",
        enabled = canMarkNoResponse || state == BimodalInteractionState.EVALUATING
    ) {
        onSimulatedResult()
        dispatch { orchestrator.reportRecoverableError("Error técnico simulado.") }
    }
    ControlButton(
        label = "Reintentar pregunta",
        enabled = isFeedback && lastResult?.canRetry == true
    ) {
        dispatch { orchestrator.retryQuestion() }
    }
    ControlButton(
        label = "Siguiente pregunta",
        enabled = isFeedback || state == BimodalInteractionState.TIME_EXPIRED
    ) {
        dispatch { orchestrator.moveToNextQuestion() }
    }
    ControlButton(
        label = "Finalizar sesión",
        enabled = !isTerminal && state != BimodalInteractionState.IDLE
    ) {
        dispatch { orchestrator.completeSession() }
    }
    ControlButton(
        label = "Cancelar sesión",
        enabled = !isTerminal
    ) {
        dispatch { orchestrator.cancelSession() }
    }
}

private enum class BimodalCameraStatus { INITIALIZING, READY, ERROR }

/**
 * Tarjeta que muestra la camara frontal y el estado de presencia facial.
 *
 * Reutiliza [FaceAnalyzer] (deteccion ML Kit) y [FacePresenceTracker] (debounce)
 * para informar al llamador, a traves de [onPresenceChanged], solo los cambios
 * confirmados de presencia. No almacena imagenes, frames ni datos biometricos:
 * la deteccion es temporal y unicamente conduce el flujo.
 *
 * El ciclo de vida de CameraX se gestiona con un unico binding creado en la
 * factoria de [AndroidView] y se libera en [DisposableEffect] al salir.
 */
@Composable
private fun FacePresenceCard(
    cameraGranted: Boolean,
    facePresent: Boolean,
    onPresenceChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    var cameraStatus by remember { mutableStateOf(BimodalCameraStatus.INITIALIZING) }
    var errorDetail by remember { mutableStateOf("") }

    val analyzerExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderHolder = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Umbrales asimetricos reducidos para una reaccion rapida: ~270-400 ms para
    // confirmar aparicion/recuperacion (8 frames) y ~400-600 ms para confirmar
    // desaparicion (12 frames), segun los frames efectivos que entregue ML Kit. Es un
    // debounce pequeno: detecta rapido la perdida y el regreso del rostro sin reaccionar
    // a parpadeos puntuales ni a falsos negativos por movimientos breves.
    val presenceTracker = remember {
        FacePresenceTracker(framesToConfirmPresent = 8, framesToConfirmAbsent = 12)
    }

    // Permite que el analizador (creado una sola vez) use siempre el callback
    // mas reciente sin necesidad de reconstruirlo en cada recomposicion.
    val currentOnPresenceChanged by rememberUpdatedState(onPresenceChanged)

    val faceAnalyzer = remember {
        FaceAnalyzer(
            onFaceCount = { count ->
                // onFaceCount llega siempre desde el hilo del analizador (un solo
                // hilo), por lo que el tracker se actualiza de forma segura.
                val transition = presenceTracker.onFaceCount(count)
                if (transition != FacePresenceTracker.Transition.NONE) {
                    mainExecutor.execute {
                        currentOnPresenceChanged(
                            transition == FacePresenceTracker.Transition.APPEARED
                        )
                    }
                }
            },
            onError = { msg ->
                mainExecutor.execute { errorDetail = msg }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderHolder.value?.unbindAll()
            } catch (_: Exception) {
                // Ignorar: el proveedor puede ya estar liberado.
            }
            faceAnalyzer.close()
            analyzerExecutor.shutdown()
        }
    }

    val statusText = when {
        !cameraGranted -> "Permiso de cámara no concedido"
        cameraStatus == BimodalCameraStatus.ERROR -> "Error de cámara"
        cameraStatus == BimodalCameraStatus.INITIALIZING -> "Cámara inicializando…"
        facePresent -> "Rostro detectado"
        else -> "Sin rostro detectado"
    }

    val statusContainerColor = when {
        !cameraGranted || cameraStatus == BimodalCameraStatus.ERROR ->
            MaterialTheme.colorScheme.errorContainer
        cameraStatus == BimodalCameraStatus.INITIALIZING ->
            MaterialTheme.colorScheme.surfaceVariant
        facePresent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val statusContentColor = when {
        !cameraGranted || cameraStatus == BimodalCameraStatus.ERROR ->
            MaterialTheme.colorScheme.onErrorContainer
        cameraStatus == BimodalCameraStatus.INITIALIZING ->
            MaterialTheme.colorScheme.onSurfaceVariant
        facePresent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

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
            Text(
                text = "Presencia facial",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            if (cameraGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                try {
                                    val provider = providerFuture.get()
                                    cameraProviderHolder.value = provider

                                    if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                                        cameraStatus = BimodalCameraStatus.ERROR
                                        errorDetail = "No se encontró cámara frontal disponible."
                                        return@addListener
                                    }

                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(
                                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                        )
                                        .build()
                                        .also {
                                            it.setAnalyzer(analyzerExecutor, faceAnalyzer)
                                        }

                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                    cameraStatus = BimodalCameraStatus.READY
                                } catch (e: Exception) {
                                    cameraStatus = BimodalCameraStatus.ERROR
                                    errorDetail = e.localizedMessage
                                        ?: "Error desconocido al inicializar cámara."
                                }
                            }, mainExecutor)
                            previewView
                        }
                    )
                }
            } else {
                Text(
                    text = "Concede el permiso de cámara desde la pantalla principal o " +
                        "desde Configuración para detectar la presencia del niño. Mientras " +
                        "tanto puedes usar el botón de simulación.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = statusContainerColor)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = statusContentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (errorDetail.isNotBlank() && cameraStatus == BimodalCameraStatus.ERROR) {
                Text(
                    text = errorDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
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

/**
 * Tarjeta con el resumen tecnico de la sesion (conteos por categoria de
 * desenlace y total de intentos). No muestra transcripciones, audios ni datos
 * del nino: solo metricas tecnicas del flujo.
 */
@Composable
private fun SessionSummaryCard(summary: BimodalSessionSummary) {
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
            Text(
                text = "Resumen de la sesión",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            InfoRow("Preguntas resueltas", summary.resolvedQuestions.toString())
            InfoRow("Correctas", summary.correct.toString())
            InfoRow("Incorrectas", summary.incorrect.toString())
            InfoRow("No interpretables", summary.notInterpretable.toString())
            InfoRow("Sin respuesta", summary.noResponse.toString())
            InfoRow("Tiempos agotados", summary.timeExpired.toString())
            InfoRow("Errores de reconocimiento", summary.sttErrors.toString())
            InfoRow("Errores técnicos", summary.technicalErrors.toString())
            InfoRow("Intentos usados", summary.totalAttempts.toString())
        }
    }
}

/**
 * Tarjeta con las metricas de latencia del sistema en la sesion actual: valores
 * numericos exactos en milisegundos (ultima respuesta logica, ultima hasta el
 * feedback, ultima del pipeline completo, promedios y mediciones validas) y el
 * umbral de referencia del charter como dato informativo.
 *
 * No muestra ningun indicador de cumplimiento ("cumple/no cumple"): el
 * cumplimiento del objetivo se interpreta manualmente a partir de los numeros.
 * Solo refleja marcas de tiempo: no contiene transcripciones, audios ni datos del
 * nino.
 */
@Composable
private fun LatencyMetricsCard(stats: BimodalLatencyStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Latencia del sistema",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            InfoRow("Última respuesta lógica", latencyMsLabel(stats.lastResponseLatencyMs))
            InfoRow("Última hasta feedback", latencyMsLabel(stats.lastFeedbackLatencyMs))
            InfoRow("Última pipeline completa", latencyMsLabel(stats.lastPipelineLatencyMs))
            InfoRow("Promedio de respuesta", latencyMsLabel(stats.averageResponseLatencyMs))
            InfoRow("Promedio hasta feedback", latencyMsLabel(stats.averageFeedbackLatencyMs))
            InfoRow("Mediciones válidas", stats.validSamples.toString())
            InfoRow("Umbral de referencia", latencyMsLabel(stats.targetMs))
        }
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

/** Etiqueta legible para el estado actual del orquestador. */
private fun stateLabel(state: BimodalInteractionState): String = when (state) {
    BimodalInteractionState.IDLE -> "Inactivo"
    BimodalInteractionState.LOADING_ACTIVITY -> "Cargando actividad"
    BimodalInteractionState.READY -> "Lista para iniciar"
    BimodalInteractionState.WAITING_FOR_FACE -> "Esperando rostro"
    BimodalInteractionState.PAUSED_FACE_LOST -> "Pausado (sin rostro)"
    BimodalInteractionState.FACE_DETECTED -> "Rostro detectado"
    BimodalInteractionState.PRESENTING_QUESTION -> "Presentando pregunta"
    BimodalInteractionState.WAITING_FOR_RESPONSE -> "Esperando respuesta"
    BimodalInteractionState.LISTENING -> "Escuchando"
    BimodalInteractionState.TRANSCRIBING -> "Transcribiendo"
    BimodalInteractionState.EVALUATING -> "Evaluando"
    BimodalInteractionState.FEEDBACK_CORRECT -> "Respuesta correcta"
    BimodalInteractionState.FEEDBACK_INCORRECT -> "Respuesta incorrecta"
    BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE -> "Respuesta no interpretable"
    BimodalInteractionState.FEEDBACK_NO_RESPONSE -> "Sin respuesta"
    BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR -> "Error técnico"
    BimodalInteractionState.TIME_EXPIRED -> "Tiempo agotado"
    BimodalInteractionState.NEXT_QUESTION -> "Siguiente pregunta"
    BimodalInteractionState.SESSION_COMPLETED -> "Sesión finalizada"
    BimodalInteractionState.SESSION_CANCELLED -> "Sesión cancelada"
    BimodalInteractionState.ERROR -> "Error"
}

/** Etiqueta legible para el estado de la captura de voz. */
private fun sttStatusLabel(state: SttState): String = when (state) {
    SttState.IDLE -> "Listo para escuchar"
    SttState.LISTENING -> "Escuchando…"
    SttState.STOPPING -> "Deteniendo captura…"
    SttState.SUCCESS -> "Transcripción obtenida"
    SttState.STOPPED -> "Captura detenida"
    SttState.ERROR -> "Error de reconocimiento"
}

/** Etiqueta legible y corta para un proveedor de voz. */
private fun providerLabel(type: ToyVoiceProviderType): String = when (type) {
    ToyVoiceProviderType.OPENAI_TTS -> "OpenAI TTS"
    ToyVoiceProviderType.LOCAL -> "Voz local"
    ToyVoiceProviderType.AZURE_NEURAL -> "Azure"
    ToyVoiceProviderType.ELEVENLABS -> "ElevenLabs"
}

/** Etiqueta legible para la categoria de retroalimentacion general. */
private fun feedbackTypeLabel(type: GeneralTeacherFeedbackType): String = when (type) {
    GeneralTeacherFeedbackType.CORRECT -> "Correcta"
    GeneralTeacherFeedbackType.INCORRECT_RETRY -> "Incorrecta (reintento)"
    GeneralTeacherFeedbackType.INCORRECT_NEXT -> "Incorrecta (avanza)"
    GeneralTeacherFeedbackType.NOT_INTERPRETABLE_RETRY -> "No interpretable (reintento)"
    GeneralTeacherFeedbackType.NOT_INTERPRETABLE_NEXT -> "No interpretable (avanza)"
    GeneralTeacherFeedbackType.NO_RESPONSE_RETRY -> "Sin respuesta (reintento)"
    GeneralTeacherFeedbackType.NO_RESPONSE_NEXT -> "Sin respuesta (avanza)"
    GeneralTeacherFeedbackType.TIME_EXPIRED_RETRY -> "Tiempo agotado (reintento)"
    GeneralTeacherFeedbackType.TIME_EXPIRED_NEXT -> "Tiempo agotado (avanza)"
    GeneralTeacherFeedbackType.TECHNICAL_ERROR_RETRY -> "Error técnico (reintento)"
    GeneralTeacherFeedbackType.TECHNICAL_ERROR_NEXT -> "Error técnico (avanza)"
    GeneralTeacherFeedbackType.SESSION_START -> "Inicio de sesión"
    GeneralTeacherFeedbackType.QUESTION_INTRO -> "Presentación de pregunta"
    GeneralTeacherFeedbackType.SESSION_COMPLETED -> "Sesión completada"
}

/** Etiqueta legible para el origen efectivo de la mediacion. */
private fun mediationSourceLabel(source: MediationSource): String = when (source) {
    MediationSource.GENERATIVE -> "Generativa"
    MediationSource.FALLBACK -> "Fallback"
    MediationSource.LOCAL -> "Local"
}

/** Etiqueta legible para el tipo de mediacion. */
private fun mediationTypeLabel(type: GenerativeMediationType): String = when (type) {
    GenerativeMediationType.QUESTION_INTRODUCTION -> "Introducción"
    GenerativeMediationType.CONTEXTUAL_FEEDBACK -> "Feedback"
}

/** Etiqueta legible para el resultado semantico. */
private fun resultLabel(result: SemanticResult): String = when (result) {
    SemanticResult.CORRECT -> "Correcta"
    SemanticResult.INCORRECT -> "Incorrecta"
    SemanticResult.NOT_INTERPRETABLE -> "No interpretable"
    SemanticResult.NO_RESPONSE -> "Sin respuesta"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun IntelligentImmersiveSystemBarsEffect(context: Context) {
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


/** Origen del ultimo resultado semantico mostrado en la pantalla. */
private enum class SemanticSource(val label: String) {
    /** Calculado por el evaluador semantico local a partir de la respuesta real. */
    REAL("real"),

    /** Forzado desde los controles tecnicos de simulacion. */
    SIMULATED("simulada")
}

/** Mensaje de retroalimentacion segun el estado de feedback y si se puede reintentar. */
private fun feedbackMessage(state: BimodalInteractionState, canRetry: Boolean): String =
    when (state) {
        BimodalInteractionState.FEEDBACK_CORRECT ->
            "¡Respuesta correcta! Puedes avanzar a la siguiente pregunta."
        BimodalInteractionState.FEEDBACK_INCORRECT ->
            if (canRetry) "Respuesta incorrecta. Aún quedan intentos: escucha de nuevo."
            else "Respuesta incorrecta. Sin intentos restantes: avanza a la siguiente."
        BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ->
            if (canRetry) "No se entendió la respuesta. Inténtalo otra vez."
            else "No se entendió la respuesta. Sin intentos restantes: avanza."
        BimodalInteractionState.FEEDBACK_NO_RESPONSE ->
            if (canRetry) "No se recibió respuesta. Escucha de nuevo."
            else "No se recibió respuesta. Sin intentos restantes: avanza."
        BimodalInteractionState.FEEDBACK_TECHNICAL_ERROR ->
            if (canRetry) "Ocurrió un problema técnico al procesar la respuesta. " +
                "No cuenta como error del niño: inténtalo de nuevo."
            else "Ocurrió un problema técnico al procesar la respuesta. " +
                "Sin intentos restantes: avanza a la siguiente."
        BimodalInteractionState.TIME_EXPIRED ->
            if (canRetry) "Se agotó el tiempo para responder. Aún quedan intentos: escucha de nuevo."
            else "Se agotó el tiempo para responder. Sin intentos restantes: avanza."
        else -> ""
    }

