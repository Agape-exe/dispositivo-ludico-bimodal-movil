package com.taller.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.taller.app.bimodal.BimodalFlowOrchestrator
import com.taller.app.bimodal.BimodalInteractionResult
import com.taller.app.bimodal.BimodalInteractionState
import com.taller.app.bimodal.BimodalSessionSummary
import com.taller.app.bimodal.DEFAULT_MAX_TIME_SECONDS
import com.taller.app.bimodal.SemanticEvaluationAdapter
import com.taller.app.bimodal.SpeechCaptureEventMapper
import com.taller.app.bimodal.SpeechCaptureOutcome
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.mapper.toDomain
import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode
import com.taller.app.semantic.SemanticResult
import com.taller.app.speech.SpeechToTextService
import com.taller.app.speech.SttState
import com.taller.app.vision.FaceAnalyzer
import com.taller.app.vision.FacePresenceTracker
import com.taller.app.voice.LocalToyVoiceProvider
import com.taller.app.voice.ToySpeechPhrase
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import com.taller.app.voice.ToyVoiceFallback
import com.taller.app.voice.ToyVoiceProviderType
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.neural.AzureSpeechConfig
import com.taller.app.voice.neural.AzureSpeechVoiceProvider
import com.taller.app.voice.neural.ElevenLabsConfig
import com.taller.app.voice.neural.ElevenLabsVoiceProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Pantalla inicial del modo bimodal inteligente.
 *
 * Permite seleccionar una actividad con preguntas, cargarla en el orquestador de
 * estados y recorrer el flujo automatico real: la presencia facial (camara)
 * dispara la pregunta, la voz del juguete la lee, la captura de voz transcribe la
 * respuesta y la evaluacion semantica produce el resultado sin intervencion
 * manual. El docente solo inicia la interaccion y decide reintentar o avanzar.
 *
 * Los controles tecnicos de simulacion se conservan en una seccion plegable,
 * colapsada por defecto y claramente separada del flujo real, para depurar el
 * orquestador sin recurrir a sensores.
 *
 * @param activityId si es distinto de 0 se carga directamente esa actividad; si
 *        es 0 se muestra un selector con las actividades disponibles.
 */
@Composable
fun BimodalInteractionScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val activityDao = remember { db.activityDao() }
    val questionDao = remember { db.questionDao() }
    val scope = rememberCoroutineScope()

    var loadedActivity by remember { mutableStateOf<LearningActivity?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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
        ActivitySelector(
            activityDao = activityDao,
            isLoading = isLoading,
            infoMessage = infoMessage,
            onPick = { load(it.id) },
            onUseSampleData = { loadedActivity = sampleActivity() },
            onBack = onBack
        )
    } else {
        BimodalSession(
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
private fun ActivitySelector(
    activityDao: com.taller.app.data.local.dao.ActivityDao,
    isLoading: Boolean,
    infoMessage: String?,
    onPick: (ActivityEntity) -> Unit,
    onUseSampleData: () -> Unit,
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
                text = "Modo bimodal inteligente",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Selecciona una actividad con preguntas para iniciar el flujo de prueba.",
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No hay actividades disponibles.\nCrea una actividad con preguntas " +
                            "desde el panel docente para probar el modo bimodal.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onUseSampleData) {
                        Text("Usar datos de prueba (no se guardan)")
                    }
                    Text(
                        text = "Respaldo temporal solo para validar el flujo; no se inserta en la base de datos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
                                text = "Toca para iniciar el modo bimodal",
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
private fun BimodalSession(
    activity: LearningActivity,
    onChangeActivity: () -> Unit,
    onBack: () -> Unit
) {
    // Una sola instancia del orquestador por actividad cargada.
    val orchestrator = remember(activity) { BimodalFlowOrchestrator() }

    // Adaptador puro que conecta el evaluador semantico local con el protocolo
    // del orquestador. Una sola instancia por actividad cargada.
    val semanticAdapter = remember(activity) { SemanticEvaluationAdapter() }

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
                // Sin texto previo lo tratamos como ausencia de voz (sin respuesta);
                // con texto previo, como fallo no interpretable. Nunca como incorrecta.
                deliverCaptureOutcome(
                    if (capturedAnyText.value) SpeechCaptureOutcome.Failed(message)
                    else SpeechCaptureOutcome.NoSpeech
                )
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
            if (orchestrator.state == BimodalInteractionState.WAITING_FOR_FACE) {
                dispatch { orchestrator.onFaceDetected() }
            }
        } else {
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

        // Si el evaluador semantico fallara, no se cancela la sesion ni se marca la
        // respuesta como incorrecta: se reporta como error tecnico recuperable.
        val outcome = runCatching { semanticAdapter.evaluate(transcription, question) }
            .getOrNull()
        if (outcome == null) {
            dispatch { orchestrator.reportRecoverableError("No se pudo evaluar la respuesta.") }
            return@LaunchedEffect
        }
        semanticSource = SemanticSource.REAL
        semanticLatencyMs = outcome.latencyMillis
        dispatch { orchestrator.onEvent(outcome.toEvent()) }
    }

    // ----- Voz del juguete -----------------------------------------------------
    // El juguete lee la pregunta usando el mismo servicio de voz de la app: el
    // proveedor neural si esta configurado, con respaldo automatico a la voz local.
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
    val elevenLabsVoiceProvider = remember {
        ElevenLabsVoiceProvider(context) { ElevenLabsConfig.from(voiceSettings.neuralVoiceId) }
    }

    DisposableEffect(Unit) {
        toySpeechService.initialize { newState -> ttsStateFlow.value = newState }
        onDispose {
            toySpeechService.shutdown()
            azureVoiceProvider.release()
            elevenLabsVoiceProvider.release()
        }
    }

    // Indica si el juguete esta leyendo la pregunta en este momento (para la UI).
    var toyVoiceSpeaking by remember(activity) { mutableStateOf(false) }

    // Cuando el flujo presenta una pregunta, el juguete la lee y, al terminar,
    // abre automaticamente la escucha si hay permiso de microfono. La clave cambia
    // en cada (re)presentacion (incluido un reintento) para releer la pregunta.
    val presentationKey = if (state == BimodalInteractionState.PRESENTING_QUESTION) {
        progress?.let { "${it.currentQuestionIndex}:${it.currentAttempt}" }
    } else {
        null
    }
    LaunchedEffect(presentationKey) {
        if (presentationKey == null) return@LaunchedEffect
        val questionText = currentQuestion?.questionText ?: return@LaunchedEffect
        val neuralProvider = when (voiceSettings.provider) {
            ToyVoiceProviderType.AZURE_NEURAL -> azureVoiceProvider
            ToyVoiceProviderType.ELEVENLABS -> elevenLabsVoiceProvider
            ToyVoiceProviderType.LOCAL -> localVoiceProvider
        }
        toyVoiceSpeaking = true
        try {
            // Si la voz no esta disponible no se interrumpe el flujo: el resultado
            // se ignora y la interaccion continua sin audio (nunca crashea).
            runCatching {
                ToyVoiceFallback.speak(
                    text = "${ToySpeechPhrase.QUESTION_INTRO.text} $questionText",
                    useNeural = voiceSettings.provider != ToyVoiceProviderType.LOCAL,
                    allowFallback = voiceSettings.fallbackToLocal,
                    neural = neuralProvider,
                    local = localVoiceProvider
                )
            }
            // Tras la lectura, abre la escucha si seguimos en la misma pregunta y
            // hay permiso de microfono. Sin permiso, el docente usa el boton.
            if (orchestrator.state == BimodalInteractionState.PRESENTING_QUESTION && audioGranted) {
                startVoiceCapture()
            }
        } finally {
            // Si el efecto se cancela (cambio de estado/pregunta) corta el audio
            // pendiente para no solaparlo con la escucha o la siguiente pregunta.
            azureVoiceProvider.stop()
            elevenLabsVoiceProvider.stop()
            toySpeechService.stop()
            toyVoiceSpeaking = false
        }
    }

    // Inicia la interaccion real en un solo paso: carga la actividad en el
    // orquestador, la confirma y arranca la sesion hasta quedar esperando rostro.
    fun startInteraction() {
        dispatch {
            orchestrator.loadActivity(activity)
            orchestrator.markActivityLoaded()
            orchestrator.startSession()
        }
    }

    // Controla la visibilidad de la seccion tecnica de simulacion (colapsada por
    // defecto para no confundirla con el flujo real).
    var showTechnical by remember(activity) { mutableStateOf(false) }

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
                text = "Modo bimodal inteligente",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                            text = "El juguete está leyendo la pregunta…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
    val presenceTracker = remember { FacePresenceTracker() }

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
            InfoRow("Errores técnicos", summary.technicalErrors.toString())
            InfoRow("Intentos usados", summary.totalAttempts.toString())
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

/** Etiqueta legible para el resultado semantico. */
private fun resultLabel(result: SemanticResult): String = when (result) {
    SemanticResult.CORRECT -> "Correcta"
    SemanticResult.INCORRECT -> "Incorrecta"
    SemanticResult.NOT_INTERPRETABLE -> "No interpretable"
    SemanticResult.NO_RESPONSE -> "Sin respuesta"
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

/** Actividad de respaldo en memoria usada solo cuando no hay actividades en la base. */
private fun sampleActivity(): LearningActivity = LearningActivity(
    id = "sample",
    title = "Datos de prueba (no guardados)",
    mode = OperationMode.ADVANCED,
    questions = listOf(
        LearningQuestion("s1", "¿De qué color es el cielo?", "azul", listOf("azul", "celeste"), 30, 3),
        LearningQuestion("s2", "¿Cuánto es 2 + 2?", "4", listOf("cuatro", "4"), 30, 3),
        LearningQuestion("s3", "¿Qué animal hace miau?", "gato", listOf("gato", "minino"), 30, 3)
    )
)
