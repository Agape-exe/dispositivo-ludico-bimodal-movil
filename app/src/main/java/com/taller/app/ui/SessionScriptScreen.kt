package com.taller.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taller.app.data.local.AppDatabase
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.QuestionEntity
import com.taller.app.gpt.GptClientImpl
import com.taller.app.gpt.GptConfig
import com.taller.app.gpt.GptRuntimeSettings
import com.taller.app.gpt.GptSettingsRepository
import com.taller.app.gpt.script.ScriptStatus
import com.taller.app.gpt.script.SessionScript
import com.taller.app.gpt.script.SessionScriptGenerator
import com.taller.app.gpt.script.SessionScriptInput
import com.taller.app.gpt.script.SessionScriptInputFactory
import com.taller.app.gpt.script.SessionScriptValidator
import com.taller.app.voice.SevenVoiceServiceFactory
import com.taller.app.voice.ToySpeechService
import com.taller.app.voice.ToySpeechState
import com.taller.app.voice.ToyVoiceSettings
import com.taller.app.voice.ToyVoiceSettingsRepository
import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.prep.SessionVoiceLines
import com.taller.app.voice.prep.SessionVoicePreparer
import com.taller.app.voice.prep.SessionVoiceSourceFactory
import com.taller.app.voice.prep.VoicePrepProgress
import com.taller.app.voice.prep.VoicePrepRecommendedStatus
import com.taller.app.voice.prep.VoicePrepReport
import com.taller.app.voice.prep.VoicePrepReportItem
import com.taller.app.voice.prep.VoicePrepStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Borrador editable del guion de una pregunta. */
private data class QuestionDraft(
    val questionId: Long,
    val orderIndex: Int,
    val originalQuestion: String,
    val referenceAnswer: String,
    val childFriendly: String,
    val hint1: String,
    val hint2: String,
    val hint3: String,
    val positive: String,
    val supportive: String,
    val retry: String,
    val warning: String,
    val suggestedReference: String
)

@Composable
fun SessionScriptScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val activityDao = remember { db.activityDao() }
    val questionDao = remember { db.questionDao() }
    val gptSettingsRepository = remember { GptSettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // Voz de Seven para la preparacion previa (TTSV01). Se reutiliza el motor local
    // y la cadena de proveedores estandar; se libera al salir de la pantalla.
    val voiceRepository = remember { ToyVoiceSettingsRepository(context) }
    val voiceSettings by voiceRepository.settings.collectAsState(initial = ToyVoiceSettings())
    val ttsStateFlow = remember { MutableStateFlow(ToySpeechState.UNINITIALIZED) }
    val toySpeechService = remember { ToySpeechService(context) }
    val sevenVoiceService = remember {
        SevenVoiceServiceFactory.create(
            context = context,
            ttsStateFlow = ttsStateFlow,
            toySpeechService = toySpeechService,
            settingsProvider = { voiceSettings }
        )
    }
    DisposableEffect(Unit) {
        toySpeechService.initialize { newState -> ttsStateFlow.value = newState }
        onDispose {
            toySpeechService.shutdown()
            sevenVoiceService.release()
        }
    }

    var activity by remember { mutableStateOf<ActivityEntity?>(null) }
    var questions by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var notFound by remember { mutableStateOf(false) }

    var intro by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf("") }
    var toneNotes by remember { mutableStateOf("") }
    var pedagogicalWarnings by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<QuestionDraft>() }

    var status by remember { mutableStateOf(ScriptStatus.NOT_GENERATED) }
    var working by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var issues by remember { mutableStateOf<List<String>>(emptyList()) }

    // Estado de la preparacion de voz (TTSV01).
    var voicePrepStatus by remember { mutableStateOf(VoicePrepStatus.NOT_PREPARED) }
    var voicePrepWorking by remember { mutableStateOf(false) }
    var voicePrepProgress by remember { mutableStateOf<VoicePrepProgress?>(null) }
    var voicePrepMessage by remember { mutableStateOf<String?>(null) }
    // Reporte por audio de la ultima preparacion (TTSV01-FIX03).
    var voicePrepReport by remember { mutableStateOf<VoicePrepReport?>(null) }

    fun loadDraftsFrom(qs: List<QuestionEntity>) {
        drafts.clear()
        qs.sortedBy { it.orderIndex }.forEach { q ->
            drafts.add(
                QuestionDraft(
                    questionId = q.id,
                    orderIndex = q.orderIndex,
                    originalQuestion = q.questionText,
                    referenceAnswer = q.expectedAnswer,
                    childFriendly = q.childFriendlyQuestionText.orEmpty(),
                    hint1 = q.hintLevel1.orEmpty(),
                    hint2 = q.hintLevel2.orEmpty(),
                    hint3 = q.hintLevel3.orEmpty(),
                    positive = q.positiveFeedbackText.orEmpty(),
                    supportive = q.supportiveFeedbackText.orEmpty(),
                    retry = q.retryPromptText.orEmpty(),
                    warning = q.answerReferenceWarning.orEmpty(),
                    suggestedReference = q.suggestedReferenceAnswer.orEmpty()
                )
            )
        }
    }

    fun applyGenerated(script: SessionScript) {
        intro = script.intro
        closing = script.closing
        toneNotes = script.toneNotes
        pedagogicalWarnings = script.pedagogicalWarnings
        val byId = script.questions.associateBy { it.questionId }
        val updated = drafts.map { draft ->
            val gen = byId[draft.questionId] ?: return@map draft
            draft.copy(
                childFriendly = gen.childFriendlyQuestionText,
                hint1 = gen.hintLevel1,
                hint2 = gen.hintLevel2,
                hint3 = gen.hintLevel3,
                positive = gen.positiveFeedbackText,
                supportive = gen.supportiveFeedbackText,
                retry = gen.retryPromptText,
                warning = gen.answerReferenceWarning,
                suggestedReference = gen.suggestedReferenceAnswer
            )
        }
        drafts.clear()
        drafts.addAll(updated)
    }

    fun currentScript(): SessionScript = SessionScript(
        intro = intro.trim(),
        closing = closing.trim(),
        toneNotes = toneNotes.trim(),
        pedagogicalWarnings = pedagogicalWarnings.trim(),
        questions = drafts.map { d ->
            com.taller.app.gpt.script.QuestionScript(
                questionId = d.questionId,
                orderIndex = d.orderIndex,
                childFriendlyQuestionText = d.childFriendly.trim(),
                hintLevel1 = d.hint1.trim(),
                hintLevel2 = d.hint2.trim(),
                hintLevel3 = d.hint3.trim(),
                positiveFeedbackText = d.positive.trim(),
                supportiveFeedbackText = d.supportive.trim(),
                retryPromptText = d.retry.trim(),
                answerReferenceWarning = d.warning.trim(),
                suggestedReferenceAnswer = d.suggestedReference.trim()
            )
        }
    )

    fun scriptInput(): SessionScriptInput? {
        val act = activity ?: return null
        return SessionScriptInputFactory.from(act, questions)
    }

    // TTSV01: pre-genera y cachea la voz de la sesion. Exige guion revisado, evita
    // duplicados (la cache reutiliza audios existentes) y persiste el estado.
    fun prepareVoice() {
        if (voicePrepWorking) return
        voicePrepMessage = null
        scope.launch {
            val act = activityDao.getById(activityId)
            val qs = questionDao.getByActivityIdOnce(activityId)
            if (act == null) {
                voicePrepMessage = "La sesión no existe o fue desactivada."
                return@launch
            }
            val reviewed = ScriptStatus.fromStorage(act.scriptStatus) == ScriptStatus.REVIEWED &&
                qs.isNotEmpty() && qs.all { it.scriptReviewed }
            if (!reviewed) {
                voicePrepMessage = "Primero revisa y aprueba el guion de Seven."
                return@launch
            }
            val lines = SessionVoiceLines.collect(SessionVoiceSourceFactory.from(act, qs))
            if (lines.isEmpty()) {
                voicePrepMessage = "No hay frases para preparar en esta sesión."
                return@launch
            }

            voicePrepWorking = true
            voicePrepStatus = VoicePrepStatus.PREPARING
            voicePrepProgress = VoicePrepProgress(total = lines.size, ready = 0, failed = 0)
            val now = System.currentTimeMillis()
            val voiceName = GeminiTtsConfig.fromBuild(voiceSettings.geminiVoiceName).voiceName
            activityDao.updateVoicePrep(
                id = activityId,
                status = VoicePrepStatus.PREPARING.storageValue,
                updatedAt = now,
                provider = null,
                voice = voiceName,
                readyCount = 0,
                totalCount = lines.size,
                lastError = null
            )

            val preparer = SessionVoicePreparer(sevenVoiceService.asLineSynthesizer())
            val outcome = preparer.prepare(lines) { progress ->
                voicePrepProgress = progress
            }

            voicePrepStatus = outcome.status
            voicePrepWorking = false
            voicePrepReport = outcome.report
            voicePrepMessage = when (outcome.report?.recommendedStatus) {
                VoicePrepRecommendedStatus.READY_IDEAL ->
                    "Voz de Seven lista con Gemini/Puck. Ya puedes usar esta sesión."
                VoicePrepRecommendedStatus.REVIEW_FALLBACK ->
                    "La voz quedó cacheada, pero ${outcome.report?.providerMismatch} audio(s) usaron un " +
                        "proveedor de respaldo (no Gemini/Puck). Vuelve a preparar para reemplazarlos."
                VoicePrepRecommendedStatus.INCOMPLETE ->
                    "Faltan ${outcome.missingCount} audios por preparar. Vuelve a intentar para completarlos."
                null ->
                    outcome.lastError?.let { "No se pudo preparar la voz. $it" }
                        ?: "No se pudo preparar la voz. Revisa la conexión e inténtalo otra vez."
            }
            activityDao.updateVoicePrep(
                id = activityId,
                status = outcome.status.storageValue,
                updatedAt = System.currentTimeMillis(),
                provider = outcome.providerUsed?.name,
                voice = voiceName,
                readyCount = outcome.readyCount,
                totalCount = outcome.totalCount,
                lastError = outcome.lastError?.take(200)
            )
        }
    }

    LaunchedEffect(activityId) {
        val found = activityDao.getById(activityId)
        if (found == null) {
            notFound = true
            loading = false
            return@LaunchedEffect
        }
        activity = found
        status = ScriptStatus.fromStorage(found.scriptStatus)
        voicePrepStatus = VoicePrepStatus.fromStorage(found.voicePrepStatus)
        if (found.voicePrepTotalCount > 0) {
            voicePrepProgress = VoicePrepProgress(
                total = found.voicePrepTotalCount,
                ready = found.voicePrepReadyCount,
                failed = (found.voicePrepTotalCount - found.voicePrepReadyCount).coerceAtLeast(0)
            )
        }
        intro = found.generatedIntroText.orEmpty()
        closing = found.generatedClosingText.orEmpty()
        toneNotes = found.generatedToneNotes.orEmpty()
        pedagogicalWarnings = found.generatedPedagogicalWarnings.orEmpty()
        val qs = questionDao.getByActivityIdOnce(activityId)
        questions = qs
        loadDraftsFrom(qs)
        loading = false
    }

    BackHandler { onBack() }

    when {
        loading -> CenteredLoader()
        notFound -> SessionScriptMissing(onBack)
        else -> {
            val act = activity!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .imePadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp)
            ) {
                Spacer(modifier = Modifier.height(34.dp))
                SectionTitle(text = "Guion de Seven", centered = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = act.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TeacherSecondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                ScriptStatusBadge(status)
                Spacer(modifier = Modifier.height(18.dp))

                if (questions.isEmpty()) {
                    Text(
                        text = "Esta sesión todavía no tiene preguntas. " +
                            "Agrega preguntas para poder preparar el guion de Seven.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TeacherSecondaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    PastelActionButton(
                        text = if (working) "Preparando..." else "Generar guion de Seven",
                        onClick = {
                            val input = scriptInput()
                            if (!working && input != null) {
                                working = true
                                infoMessage = null
                                issues = emptyList()
                                scope.launch {
                                    val safe = gptSettingsRepository.readOnce().sanitized()
                                    val client = GptClientImpl(configProvider = { GptConfig.fromBuild(safe) })
                                    val generator = SessionScriptGenerator(client)
                                    val outcome = withContext(Dispatchers.IO) { generator.generate(input) }
                                    applyGenerated(outcome.script)
                                    infoMessage = when (outcome) {
                                        is SessionScriptGenerator.Outcome.FromModel ->
                                            "Guion preparado. Revísalo y edítalo antes de guardar."
                                        is SessionScriptGenerator.Outcome.FromLocal -> outcome.message
                                    }
                                    // Persiste como pendiente de revisión para no perder el trabajo.
                                    persistScript(
                                        scope = scope,
                                        activityDao = activityDao,
                                        questionDao = questionDao,
                                        activityId = activityId,
                                        script = currentScript(),
                                        reviewed = false
                                    )
                                    status = ScriptStatus.GENERATED_PENDING_REVIEW
                                    working = false
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                infoMessage?.let { message ->
                    Spacer(modifier = Modifier.height(14.dp))
                    InfoCard(message)
                }

                if (issues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    IssuesCard(issues)
                }

                Spacer(modifier = Modifier.height(22.dp))

                SessionLevelSection(
                    intro = intro,
                    onIntroChange = { intro = it },
                    closing = closing,
                    onClosingChange = { closing = it },
                    toneNotes = toneNotes,
                    onToneChange = { toneNotes = it },
                    warnings = pedagogicalWarnings,
                    onWarningsChange = { pedagogicalWarnings = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                drafts.forEachIndexed { index, draft ->
                    QuestionScriptSection(
                        draft = draft,
                        onChange = { updated -> drafts[index] = updated }
                    )
                }

                if (questions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    PastelActionButton(
                        text = if (working) "Guardando..." else "Guardar y aprobar guion",
                        onClick = {
                            val input = scriptInput()
                            if (!working && input != null) {
                                val validation = SessionScriptValidator.validate(currentScript(), input)
                                if (!validation.isValid) {
                                    issues = validation.issues
                                    infoMessage = "Revisa los puntos marcados antes de aprobar el guion."
                                } else {
                                    issues = emptyList()
                                    working = true
                                    persistScript(
                                        scope = scope,
                                        activityDao = activityDao,
                                        questionDao = questionDao,
                                        activityId = activityId,
                                        script = currentScript(),
                                        reviewed = true,
                                        onDone = {
                                            status = ScriptStatus.REVIEWED
                                            infoMessage = "Guion guardado y aprobado."
                                            working = false
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                if (questions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    VoicePreparationSection(
                        status = voicePrepStatus,
                        working = voicePrepWorking,
                        progress = voicePrepProgress,
                        message = voicePrepMessage,
                        report = voicePrepReport,
                        onPrepare = { prepareVoice() }
                    )
                }

                BottomBackButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 22.dp)
                        .width(150.dp)
                )
            }
        }
    }
}

private fun persistScript(
    scope: kotlinx.coroutines.CoroutineScope,
    activityDao: com.taller.app.data.local.dao.ActivityDao,
    questionDao: com.taller.app.data.local.dao.QuestionDao,
    activityId: Long,
    script: SessionScript,
    reviewed: Boolean,
    onDone: () -> Unit = {}
) {
    scope.launch {
        val now = System.currentTimeMillis()
        val statusValue = if (reviewed) {
            ScriptStatus.REVIEWED.storageValue
        } else {
            ScriptStatus.GENERATED_PENDING_REVIEW.storageValue
        }
        activityDao.updateScript(
            id = activityId,
            introText = script.intro.ifBlank { null },
            closingText = script.closing.ifBlank { null },
            toneNotes = script.toneNotes.ifBlank { null },
            pedagogicalWarnings = script.pedagogicalWarnings.ifBlank { null },
            scriptStatus = statusValue,
            scriptUpdatedAt = now
        )
        script.questions.forEach { q ->
            questionDao.updateScript(
                id = q.questionId,
                childFriendlyQuestionText = q.childFriendlyQuestionText.ifBlank { null },
                hintLevel1 = q.hintLevel1.ifBlank { null },
                hintLevel2 = q.hintLevel2.ifBlank { null },
                hintLevel3 = q.hintLevel3.ifBlank { null },
                positiveFeedbackText = q.positiveFeedbackText.ifBlank { null },
                supportiveFeedbackText = q.supportiveFeedbackText.ifBlank { null },
                retryPromptText = q.retryPromptText.ifBlank { null },
                answerReferenceWarning = q.answerReferenceWarning.ifBlank { null },
                suggestedReferenceAnswer = q.suggestedReferenceAnswer.ifBlank { null },
                scriptReviewed = reviewed,
                scriptUpdatedAt = now
            )
        }
        onDone()
    }
}

@Composable
private fun ScriptStatusBadge(status: ScriptStatus) {
    val (label, color) = when (status) {
        ScriptStatus.NOT_GENERATED -> "Sin guion" to TeacherSecondaryTextColor
        ScriptStatus.GENERATED_PENDING_REVIEW -> "Guion generado, pendiente de revisión" to TeacherPrimaryPurple
        ScriptStatus.REVIEWED -> "Guion revisado" to TeacherDarkPurple
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TeacherCardLavender),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = "Estado: $label",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun InfoCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TeacherPastelPink),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TeacherPrimaryPurple,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun IssuesCard(issues: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4E4)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Para aprobar el guion:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(6.dp))
            issues.forEach { issue ->
                Text(
                    text = "• $issue",
                    style = MaterialTheme.typography.bodySmall,
                    color = TeacherTextColor
                )
            }
        }
    }
}

@Composable
private fun VoicePreparationSection(
    status: VoicePrepStatus,
    working: Boolean,
    progress: VoicePrepProgress?,
    message: String?,
    report: VoicePrepReport?,
    onPrepare: () -> Unit
) {
    Text(
        text = "Voz de Seven",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TeacherPrimaryPurple
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Prepara y guarda la voz de Seven para esta sesión. Durante la sesión " +
            "con los niños se reproduce desde la memoria del dispositivo, sin conexión.",
        style = MaterialTheme.typography.bodySmall,
        color = TeacherSecondaryTextColor
    )
    Spacer(modifier = Modifier.height(12.dp))

    val (label, color) = when (status) {
        VoicePrepStatus.NOT_PREPARED -> "Voz sin preparar" to TeacherSecondaryTextColor
        VoicePrepStatus.PREPARING -> "Preparando voz…" to TeacherPrimaryPurple
        VoicePrepStatus.READY -> "Voz lista" to TeacherDarkPurple
        VoicePrepStatus.PARTIAL -> "Voz incompleta" to TeacherPrimaryPurple
        VoicePrepStatus.FAILED -> "No se pudo preparar la voz" to MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TeacherCardLavender),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Estado: $label",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            if (progress != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Audios listos: ${progress.ready} de ${progress.total} · " +
                        "Pendientes: ${progress.pending}" +
                        if (progress.failed > 0) " · Con problema: ${progress.failed}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TeacherSecondaryTextColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    val buttonLabel = when {
        working -> {
            val count = progress?.let { " (${it.ready + it.failed}/${it.total})" } ?: ""
            "Preparando voz…$count"
        }
        status == VoicePrepStatus.READY -> "Volver a preparar voz de Seven"
        status == VoicePrepStatus.PARTIAL || status == VoicePrepStatus.FAILED ->
            "Reintentar preparación de voz"
        else -> "Preparar voz de Seven"
    }
    PastelActionButton(
        text = buttonLabel,
        onClick = { if (!working) onPrepare() },
        modifier = Modifier.fillMaxWidth()
    )

    message?.let {
        Spacer(modifier = Modifier.height(12.dp))
        InfoCard(it)
    }

    report?.takeIf { it.items.isNotEmpty() }?.let { rep ->
        Spacer(modifier = Modifier.height(14.dp))
        VoicePrepReportCard(rep)
    }
}

@Composable
private fun VoicePrepReportCard(report: VoicePrepReport) {
    val recommendedLabel = when (report.recommendedStatus) {
        VoicePrepRecommendedStatus.READY_IDEAL -> "LISTA para prueba real (Gemini/Puck)"
        VoicePrepRecommendedStatus.REVIEW_FALLBACK -> "REVISAR (hay proveedor de respaldo)"
        VoicePrepRecommendedStatus.INCOMPLETE -> "INCOMPLETA (faltan audios)"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TeacherCardLavender),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Reporte de audios",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TeacherPrimaryPurple
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Estado recomendado: $recommendedLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TeacherDarkPurple
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Total: ${report.total} · Listos: ${report.ready} · " +
                    "Gemini/Puck: ${report.targetProviderReady} · " +
                    "Respaldo: ${report.fallbackProviderReady} · Fallidos: ${report.failed}",
                style = MaterialTheme.typography.bodySmall,
                color = TeacherSecondaryTextColor
            )
            Spacer(modifier = Modifier.height(10.dp))
            report.items.forEach { item ->
                VoicePrepReportRow(item)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun VoicePrepReportRow(item: VoicePrepReportItem) {
    val statusColor = when {
        item.status == "FAILED" -> MaterialTheme.colorScheme.error
        !item.isTargetProvider -> TeacherPrimaryPurple
        else -> TeacherDarkPurple
    }
    Column {
        Text(
            text = "${item.lineType} · ${item.status}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor
        )
        val providerLine = buildString {
            append(item.provider ?: "Ninguno")
            item.voice?.takeIf { it.isNotBlank() }?.let { append(" / $it") }
            append(if (item.isTargetProvider) " · objetivo: Sí" else " · objetivo: No")
        }
        Text(
            text = providerLine,
            style = MaterialTheme.typography.bodySmall,
            color = TeacherSecondaryTextColor
        )
        Text(
            text = "Pieza: ${item.textHashShort}" +
                (item.cacheKeyShort?.let { " · clave: $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = TeacherSecondaryTextColor
        )
        item.error?.let {
            Text(
                text = "Error: ${it.take(80)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SessionLevelSection(
    intro: String,
    onIntroChange: (String) -> Unit,
    closing: String,
    onClosingChange: (String) -> Unit,
    toneNotes: String,
    onToneChange: (String) -> Unit,
    warnings: String,
    onWarningsChange: (String) -> Unit
) {
    Text(
        text = "Presentación y cierre",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TeacherPrimaryPurple
    )
    Spacer(modifier = Modifier.height(12.dp))
    PastelTextField(
        value = intro,
        onValueChange = onIntroChange,
        label = "Presentación de Seven",
        singleLine = false,
        minLines = 2
    )
    Spacer(modifier = Modifier.height(12.dp))
    PastelTextField(
        value = closing,
        onValueChange = onClosingChange,
        label = "Despedida de Seven",
        singleLine = false,
        minLines = 2
    )
    Spacer(modifier = Modifier.height(12.dp))
    PastelTextField(
        value = toneNotes,
        onValueChange = onToneChange,
        label = "Tono general de la sesión",
        singleLine = false,
        minLines = 2
    )
    Spacer(modifier = Modifier.height(12.dp))
    PastelTextField(
        value = warnings,
        onValueChange = onWarningsChange,
        label = "Advertencias pedagógicas",
        supportingText = "Observaciones sobre el contenido de la sesión, si las hay.",
        singleLine = false,
        minLines = 2
    )
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun QuestionScriptSection(
    draft: QuestionDraft,
    onChange: (QuestionDraft) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TeacherCardLavender),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Pregunta ${draft.orderIndex}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TeacherPrimaryPurple
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Original: ${draft.originalQuestion}",
                style = MaterialTheme.typography.bodySmall,
                color = TeacherSecondaryTextColor
            )
            Text(
                text = "Referencia: ${draft.referenceAnswer}",
                style = MaterialTheme.typography.bodySmall,
                color = TeacherSecondaryTextColor
            )
            Spacer(modifier = Modifier.height(14.dp))

            ScriptField(draft.childFriendly, "Pregunta amigable de Seven", minLines = 2) {
                onChange(draft.copy(childFriendly = it))
            }
            ScriptField(draft.hint1, "Pista 1") { onChange(draft.copy(hint1 = it)) }
            ScriptField(draft.hint2, "Pista 2") { onChange(draft.copy(hint2 = it)) }
            ScriptField(draft.hint3, "Pista 3") { onChange(draft.copy(hint3 = it)) }
            ScriptField(draft.positive, "Feedback positivo") { onChange(draft.copy(positive = it)) }
            ScriptField(draft.supportive, "Feedback de apoyo") { onChange(draft.copy(supportive = it)) }
            ScriptField(draft.retry, "Mensaje de reintento") { onChange(draft.copy(retry = it)) }
            ScriptField(
                draft.warning,
                "Observación sobre la referencia",
                minLines = 2
            ) { onChange(draft.copy(warning = it)) }
            ScriptField(
                draft.suggestedReference,
                "Referencia sugerida"
            ) { onChange(draft.copy(suggestedReference = it)) }
        }
    }
}

@Composable
private fun ScriptField(
    value: String,
    label: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    PastelTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = minLines == 1,
        minLines = minLines
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun CenteredLoader() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = TeacherPrimaryPurple)
    }
}

@Composable
private fun SessionScriptMissing(onBack: () -> Unit) {
    TeacherPanelContainer {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "La sesión no existe o fue desactivada.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                BottomBackButton(onClick = onBack, modifier = Modifier.width(150.dp))
            }
        }
    }
}
