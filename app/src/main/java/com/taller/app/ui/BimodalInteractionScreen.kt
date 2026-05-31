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
import com.taller.app.bimodal.BimodalInteractionState
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla inicial del modo bimodal inteligente.
 *
 * Permite seleccionar una actividad con preguntas, cargarla en el orquestador de
 * estados y avanzar por el flujo avanzado. La presencia facial (camara), la
 * captura de voz (reconocimiento de voz) y la evaluacion semantica de la
 * respuesta son reales y alimentan al orquestador; solo la voz del juguete sigue
 * pendiente. Conserva controles tecnicos de simulacion, claramente separados del
 * flujo real, para validar el comportamiento del orquestador.
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

    // Tope de tiempo simple: si la pregunta define maxTimeSeconds, detiene la
    // escucha al cumplirse para no dejar el microfono abierto indefinidamente.
    // El efecto se cancela solo al cambiar sttState (incluido el fin de la escucha).
    LaunchedEffect(sttState) {
        if (sttState == SttState.LISTENING) {
            val seconds = progress?.maxTimeSeconds ?: 0
            if (seconds in 1..600) {
                delay(seconds * 1000L)
                speechService.stopListening()
            }
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

        val outcome = semanticAdapter.evaluate(transcription, question)
        semanticSource = SemanticSource.REAL
        semanticLatencyMs = outcome.latencyMillis
        dispatch { orchestrator.onEvent(outcome.toEvent()) }
    }

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
                Text(
                    text = "Respuesta esperada: ${currentQuestion?.expectedAnswer ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Palabras clave: " +
                        (currentQuestion?.keywords
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(", ") ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

                Button(
                    onClick = { startVoiceCapture() },
                    enabled = audioGranted &&
                        state == BimodalInteractionState.PRESENTING_QUESTION &&
                        !isListeningVoice && !isStoppingVoice,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Escuchar respuesta")
                }
                OutlinedButton(
                    onClick = { stopVoiceCapture() },
                    enabled = isListeningVoice,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Detener captura")
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

        // Acciones del flujo real ante la retroalimentacion: delega siempre en el
        // orquestador (intentos y avance). El boton de reintento solo aparece si
        // el orquestador permite reintentar la pregunta actual.
        val isFeedbackState = state == BimodalInteractionState.FEEDBACK_CORRECT ||
            state == BimodalInteractionState.FEEDBACK_INCORRECT ||
            state == BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ||
            state == BimodalInteractionState.FEEDBACK_NO_RESPONSE
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

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Controles técnicos / Simulación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Botones temporales para simular el flujo. La presencia facial, la " +
                "captura de voz y la evaluación semántica ya son reales: tras una " +
                "captura de voz el resultado semántico se calcula automáticamente. " +
                "Estos botones forzan un resultado concreto y se marcan como " +
                "“simulada” para distinguirlos de la evaluación real.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))

        val isTerminal = state == BimodalInteractionState.SESSION_COMPLETED ||
            state == BimodalInteractionState.SESSION_CANCELLED ||
            state == BimodalInteractionState.ERROR
        val isFeedback = state == BimodalInteractionState.FEEDBACK_CORRECT ||
            state == BimodalInteractionState.FEEDBACK_INCORRECT ||
            state == BimodalInteractionState.FEEDBACK_NOT_INTERPRETABLE ||
            state == BimodalInteractionState.FEEDBACK_NO_RESPONSE
        val canAnswer = state == BimodalInteractionState.LISTENING
        // La evaluacion simulada tambien aplica tras una captura de voz real, que
        // deja el flujo en EVALUATING a la espera del resultado semantico.
        val canSimulateEval = canAnswer || state == BimodalInteractionState.EVALUATING
        val canMarkNoResponse = state == BimodalInteractionState.PRESENTING_QUESTION ||
            state == BimodalInteractionState.WAITING_FOR_RESPONSE ||
            state == BimodalInteractionState.LISTENING

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
            semanticSource = SemanticSource.SIMULATED
            semanticLatencyMs = null
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
            semanticSource = SemanticSource.SIMULATED
            semanticLatencyMs = null
            dispatch {
                orchestrator.onSpeechCaptured("respuesta incorrecta simulada")
                orchestrator.onSemanticEvaluated(SemanticResult.INCORRECT)
            }
        }
        ControlButton(
            label = "Evaluar como no interpretable (simulado)",
            enabled = canSimulateEval
        ) {
            semanticSource = SemanticSource.SIMULATED
            semanticLatencyMs = null
            dispatch {
                orchestrator.onSpeechCaptured("mmm")
                orchestrator.onSemanticEvaluated(SemanticResult.NOT_INTERPRETABLE)
            }
        }
        ControlButton(
            label = "Simular sin respuesta",
            enabled = canMarkNoResponse
        ) {
            semanticSource = SemanticSource.SIMULATED
            semanticLatencyMs = null
            dispatch { orchestrator.onNoResponse() }
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
        BimodalInteractionState.TIME_EXPIRED ->
            "Se agotó el tiempo para responder. Avanza a la siguiente pregunta."
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
