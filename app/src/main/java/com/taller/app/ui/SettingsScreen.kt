package com.taller.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.taller.app.attention.AttentionDebugSettings
import com.taller.app.attention.AttentionDebugSettingsRepository
import com.taller.app.gpt.GptClientImpl
import com.taller.app.gpt.GptConfig
import com.taller.app.gpt.GptPrompt
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.GptRuntimeSettings
import com.taller.app.gpt.GptSettingsRepository
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.StructuredGptResult
import com.taller.app.settings.AppSettings
import com.taller.app.settings.AppSettingsRepository
import com.taller.app.settings.MarkdownLimiterFile
import com.taller.app.settings.MarkdownLimiterRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFaceDetection: () -> Unit,
    onNavigateToSpeechTest: () -> Unit,
    onNavigateToSemanticTest: () -> Unit,
    onNavigateToToyVoiceSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appSettingsRepository = remember { AppSettingsRepository(context.applicationContext) }
    val appSettings by appSettingsRepository.settings.collectAsState(initial = AppSettings.defaults())
    val markdownLimiterRepository = remember {
        MarkdownLimiterRepository(context.applicationContext)
    }
    val gptSettingsRepository = remember { GptSettingsRepository(context.applicationContext) }
    val savedGptSettings by gptSettingsRepository.settings.collectAsState(initial = GptRuntimeSettings.defaults())
    val attentionDebugSettingsRepository = remember {
        AttentionDebugSettingsRepository(context.applicationContext)
    }
    val attentionDebugSettings by attentionDebugSettingsRepository.settings.collectAsState(
        initial = AttentionDebugSettings()
    )
    val gptConfig = GptConfig.fromBuild(savedGptSettings)

    fun isCameraGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun isAudioGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(isCameraGranted()) }
    var audioGranted by remember { mutableStateOf(isAudioGranted()) }
    var showSettingsHint by remember { mutableStateOf(false) }
    var gptJsonTesting by remember { mutableStateOf(false) }
    var gptJsonTestResult by remember { mutableStateOf<StructuredGptResult?>(null) }
    var aiMessage by remember { mutableStateOf<String?>(null) }
    var draftGptSettings by remember { mutableStateOf(savedGptSettings) }
    var classicTimeText by remember { mutableStateOf(appSettings.classicResponseTimeSeconds.toString()) }
    var recapturesText by remember { mutableStateOf(appSettings.intelligentMaxRecaptures.toString()) }
    var conversationTurnsText by remember {
        mutableStateOf(appSettings.initialConversationMaxChildTurns.toString())
    }
    var conversationDurationText by remember {
        mutableStateOf(appSettings.initialConversationMaxDurationSeconds.toString())
    }
    var earlyWindowText by remember { mutableStateOf(appSettings.earlyAnswerWindowMs.toString()) }
    var modeSettingsMessage by remember { mutableStateOf<String?>(null) }
    var limiterFiles by remember { mutableStateOf<List<MarkdownLimiterFile>>(emptyList()) }
    var limiterMessage by remember { mutableStateOf<String?>(null) }
    var limiterContentDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var limiterDeleteTarget by remember { mutableStateOf<MarkdownLimiterFile?>(null) }

    LaunchedEffect(savedGptSettings) {
        draftGptSettings = savedGptSettings
    }

    LaunchedEffect(appSettings) {
        classicTimeText = appSettings.classicResponseTimeSeconds.toString()
        recapturesText = appSettings.intelligentMaxRecaptures.toString()
        conversationTurnsText = appSettings.initialConversationMaxChildTurns.toString()
        conversationDurationText = appSettings.initialConversationMaxDurationSeconds.toString()
        earlyWindowText = appSettings.earlyAnswerWindowMs.toString()
    }

    fun refreshLimiters() {
        limiterFiles = markdownLimiterRepository.listFiles()
    }

    LaunchedEffect(Unit) {
        refreshLimiters()
    }

    val markdownImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = markdownLimiterRepository.importFromUri(uri)
            limiterMessage = result.message
            refreshLimiters()
        }
    }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = isCameraGranted()
                audioGranted = isAudioGranted()
                if (cameraGranted && audioGranted) showSettingsHint = false
                Log.d("SettingsScreen", "ON_RESUME - cámara: $cameraGranted, micrófono: $audioGranted")
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        cameraGranted = permissions[Manifest.permission.CAMERA] == true
        audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        showSettingsHint = !cameraGranted || !audioGranted
        Log.d("SettingsScreen", "Permisos - cámara: $cameraGranted, micrófono: $audioGranted")
    }

    val allGranted = cameraGranted && audioGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Configurar",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ajustes y pruebas técnicas del prototipo",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Volver",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            PermissionRow(label = "Cámara", granted = cameraGranted)
            PermissionRow(label = "Micrófono", granted = audioGranted)

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    showSettingsHint = false
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                    )
                },
                enabled = !allGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(text = if (allGranted) "Permisos concedidos" else "Solicitar permisos")
            }

            if (showSettingsHint) {
                Text(
                    text = when {
                        !cameraGranted && !audioGranted ->
                            "Cámara y micrófono denegados. Ve a Configuración para concederlos."
                        !cameraGranted ->
                            "Permiso de cámara denegado. Ve a Configuración para concederlo."
                        else ->
                            "Permiso de micrófono denegado. Ve a Configuración para concederlo."
                    },
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text("Ir a Configuración del sistema")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // FINAL-FLOW01: configuracion agrupada por secciones. Los interruptores
            // se guardan al instante; los valores numericos se validan y guardan con
            // el boton de la seccion del temporizador.
            InteractionSettingsSection(
                conversationEnabled = appSettings.initialConversationEnabled,
                conversationTurnsText = conversationTurnsText,
                conversationDurationText = conversationDurationText,
                earlyAnswerEnabled = appSettings.earlyAnswerCaptureEnabled,
                earlyWindowText = earlyWindowText,
                onConversationEnabledChange = { enabled ->
                    coroutineScope.launch {
                        appSettingsRepository.saveInitialConversationEnabled(enabled)
                    }
                },
                onConversationTurnsChange = {
                    conversationTurnsText = it.filter(Char::isDigit).take(1)
                },
                onConversationDurationChange = {
                    conversationDurationText = it.filter(Char::isDigit).take(3)
                },
                onEarlyAnswerEnabledChange = { enabled ->
                    coroutineScope.launch {
                        appSettingsRepository.saveEarlyAnswerCaptureEnabled(enabled)
                    }
                },
                onEarlyWindowChange = {
                    earlyWindowText = it.filter(Char::isDigit).take(4)
                }
            )

            IntelligentModeSettingsSection(
                attentionEnabled = appSettings.intelligentAttentionEnabled,
                recaptureEnabled = appSettings.intelligentRecaptureEnabled,
                recapturesText = recapturesText,
                onAttentionEnabledChange = { enabled ->
                    coroutineScope.launch {
                        appSettingsRepository.saveIntelligentAttentionEnabled(enabled)
                    }
                },
                onRecaptureEnabledChange = { enabled ->
                    coroutineScope.launch {
                        appSettingsRepository.saveIntelligentRecaptureEnabled(enabled)
                    }
                },
                onRecapturesChange = { recapturesText = it.filter(Char::isDigit).take(2) }
            )

            ClassicModeSettingsSection(
                classicTimeText = classicTimeText,
                message = modeSettingsMessage,
                onClassicTimeChange = { classicTimeText = it.filter(Char::isDigit).take(3) },
                onSave = {
                    val classicSeconds = classicTimeText.toIntOrNull()
                    val recaptures = recapturesText.toIntOrNull()
                    val turns = conversationTurnsText.toIntOrNull()
                    val durationSeconds = conversationDurationText.toIntOrNull()
                    val windowMs = earlyWindowText.toIntOrNull()
                    when {
                        turns == null ||
                            !AppSettings.isValidInitialConversationTurns(turns) ->
                            modeSettingsMessage =
                                "Los turnos de conversacion deben estar entre 0 y 3."
                        durationSeconds == null ||
                            !AppSettings.isValidInitialConversationDurationSeconds(durationSeconds) ->
                            modeSettingsMessage =
                                "La duracion de conversacion debe estar entre 15 y 120 segundos."
                        windowMs == null ||
                            !AppSettings.isValidEarlyAnswerWindowMs(windowMs) ->
                            modeSettingsMessage =
                                "La ventana temprana debe estar entre 500 y 4000 ms."
                        recaptures == null ||
                            !AppSettings.isValidIntelligentMaxRecaptures(recaptures) ->
                            modeSettingsMessage = "Las recapturas deben estar entre 0 y 10."
                        classicSeconds == null ||
                            !AppSettings.isValidClassicResponseTime(classicSeconds) ->
                            modeSettingsMessage = "El tiempo debe estar entre 5 y 120 segundos."
                        else -> coroutineScope.launch {
                            appSettingsRepository.save(
                                appSettings.copy(
                                    classicResponseTimeSeconds = classicSeconds,
                                    intelligentMaxRecaptures = recaptures,
                                    initialConversationMaxChildTurns = turns,
                                    initialConversationMaxDurationSeconds = durationSeconds,
                                    earlyAnswerWindowMs = windowMs
                                )
                            )
                            modeSettingsMessage = "Configuracion guardada."
                        }
                    }
                }
            )

            SettingsSectionHeader(
                title = "Voz",
                description = "Proveedores, voces y pruebas de la voz de Seven."
            )
            Button(
                onClick = onNavigateToToyVoiceSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB8B3DF),
                    contentColor = Color(0xFF3D2B8A)
                )
            ) {
                Text("Configurar voz del juguete")
            }

            MarkdownLimitersSection(
                files = limiterFiles,
                message = limiterMessage,
                onImport = {
                    markdownImportLauncher.launch(
                        arrayOf("text/markdown", "text/plain", "application/octet-stream")
                    )
                },
                onView = { file ->
                    val content = markdownLimiterRepository.readContent(file.id)
                    limiterContentDialog = file.displayName to (content ?: "No se pudo leer el archivo.")
                },
                onToggleEnabled = { file, enabled ->
                    markdownLimiterRepository.setEnabled(file.id, enabled)
                    refreshLimiters()
                },
                onDelete = { limiterDeleteTarget = it }
            )

            SettingsSectionHeader(
                title = "Depuracion",
                description = "Paneles tecnicos y pruebas de camara, voz y evaluacion. " +
                    "No afectan el flujo del nino."
            )

            AttentionDebugSettingsSection(
                attentionVisualDebugEnabled =
                    attentionDebugSettings.attentionVisualDebugEnabled,
                showTtsDebugInIntelligentMode =
                    attentionDebugSettings.showTtsDebugInIntelligentMode,
                showGptDebugInIntelligentMode =
                    attentionDebugSettings.showGptDebugInIntelligentMode,
                onAttentionVisualDebugChanged = { enabled ->
                    coroutineScope.launch {
                        attentionDebugSettingsRepository
                            .saveAttentionVisualDebugEnabled(enabled)
                        Log.d(
                            "SettingsScreen",
                            "eventType=ATTENTION_VISUAL_DEBUG_SETTING_CHANGED " +
                                "attentionVisualDebugEnabled=$enabled"
                        )
                    }
                },
                onTtsDebugInIntelligentModeChanged = { enabled ->
                    coroutineScope.launch {
                        attentionDebugSettingsRepository
                            .saveTtsDebugInIntelligentMode(enabled)
                        Log.d(
                            "SettingsScreen",
                            "eventType=TTS_DEBUG_SETTING_CHANGED " +
                                "showTtsDebugInIntelligentMode=$enabled"
                        )
                    }
                },
                onGptDebugInIntelligentModeChanged = { enabled ->
                    coroutineScope.launch {
                        attentionDebugSettingsRepository
                            .saveGptDebugInIntelligentMode(enabled)
                        Log.d(
                            "SettingsScreen",
                            "eventType=GPT_DEBUG_SETTING_CHANGED " +
                                "showGptDebugInIntelligentMode=$enabled"
                        )
                    }
                }
            )

            Button(
                onClick = onNavigateToFaceDetection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF96CEC6),
                    contentColor = Color(0xFF0D4D50)
                )
            ) {
                Text("Prueba de detección facial")
            }

            Button(
                onClick = onNavigateToSpeechTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF5D6F4),
                    contentColor = Color(0xFF7B065E)
                )
            ) {
                Text("Prueba de voz STT")
            }

            Button(
                onClick = onNavigateToSemanticTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFF0A6),
                    contentColor = Color(0xFF4A3D38)
                )
            ) {
                Text("Prueba de procesamiento semántico")
            }

            GptAiSettingsSection(
                config = gptConfig,
                draftSettings = draftGptSettings,
                message = aiMessage,
                testing = gptJsonTesting,
                result = gptJsonTestResult,
                onSettingsChange = {
                    draftGptSettings = it.sanitized()
                    aiMessage = when {
                        !it.enabled -> "GPT desactivado: se usara fallback local."
                        it.localFallbackEnabled -> "Fallback local activado."
                        else -> "Fallback local desactivado."
                    }
                },
                onSave = {
                    coroutineScope.launch {
                        val safe = draftGptSettings.sanitized()
                        gptSettingsRepository.save(safe)
                        Log.d(
                            "SettingsScreen",
                            "eventType=AI_SETTINGS_SAVED enabled=${safe.enabled} model=${safe.model} " +
                                "fallbackModel=${safe.fallbackModel} maxOutputTokens=${safe.maxOutputTokens} " +
                                "timeoutMs=${safe.timeoutMs} temperature=${safe.temperature} " +
                                "localFallbackEnabled=${safe.localFallbackEnabled} " +
                                "structuredOutputsEnabled=${safe.structuredOutputsEnabled} configured=${gptConfig.hasApiKey}"
                        )
                        aiMessage = "Configuracion IA guardada."
                    }
                },
                onReset = {
                    coroutineScope.launch {
                        val defaults = GptRuntimeSettings.defaults()
                        gptSettingsRepository.reset()
                        draftGptSettings = defaults
                        Log.d(
                            "SettingsScreen",
                            "eventType=AI_SETTINGS_RESET enabled=${defaults.enabled} model=${defaults.model} " +
                                "fallbackModel=${defaults.fallbackModel} maxOutputTokens=${defaults.maxOutputTokens} " +
                                "timeoutMs=${defaults.timeoutMs} temperature=${defaults.temperature} " +
                                "localFallbackEnabled=${defaults.localFallbackEnabled} " +
                                "structuredOutputsEnabled=${defaults.structuredOutputsEnabled} configured=${gptConfig.hasApiKey}"
                        )
                        aiMessage = "Valores por defecto restaurados."
                    }
                },
                onTest = {
                    gptJsonTesting = true
                    gptJsonTestResult = null
                    coroutineScope.launch {
                        val safe = draftGptSettings.sanitized()
                        gptSettingsRepository.save(safe)
                        val testConfig = GptConfig.fromBuild(safe)
                        aiMessage = when {
                            !testConfig.enabled && testConfig.localFallbackEnabled ->
                                "GPT desactivado: se usara fallback local."
                            !testConfig.hasApiKey ->
                                "GPT no configurado: falta API key local."
                            testConfig.localFallbackEnabled -> "Fallback local activado."
                            else -> "Fallback local desactivado."
                        }
                        Log.d(
                            "SettingsScreen",
                            "eventType=AI_GPT_TEST_REQUEST enabled=${safe.enabled} model=${safe.model} " +
                                "fallbackModel=${safe.fallbackModel} maxOutputTokens=${safe.maxOutputTokens} " +
                                "timeoutMs=${safe.timeoutMs} temperature=${safe.temperature} " +
                                "localFallbackEnabled=${safe.localFallbackEnabled} " +
                                "structuredOutputsEnabled=${safe.structuredOutputsEnabled} configured=${testConfig.hasApiKey}"
                        )
                        val testClient = GptClientImpl(configProvider = { GptConfig.fromBuild(safe) })
                        gptJsonTestResult = testClient.generateStructured(settingsJsonTestInput())
                        val eventType = if (gptJsonTestResult?.fallbackUsed == true) {
                            "AI_GPT_TEST_FALLBACK"
                        } else if (gptJsonTestResult?.errorType == null) {
                            "AI_GPT_TEST_SUCCESS"
                        } else {
                            "AI_GPT_TEST_FAILED"
                        }
                        Log.d(
                            "SettingsScreen",
                            "eventType=$eventType enabled=${safe.enabled} model=${safe.model} " +
                                "fallbackModel=${safe.fallbackModel} configured=${testConfig.hasApiKey} " +
                                "errorType=${gptJsonTestResult?.errorType?.name ?: "none"}"
                        )
                        gptJsonTesting = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    limiterContentDialog?.let { (title, content) ->
        AlertDialog(
            onDismissRequest = { limiterContentDialog = null },
            confirmButton = {
                TextButton(onClick = { limiterContentDialog = null }) {
                    Text("Cerrar")
                }
            },
            title = { Text(title) },
            text = {
                Text(
                    text = content.take(6_000),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        )
    }

    limiterDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { limiterDeleteTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = markdownLimiterRepository.delete(target.id)
                        limiterMessage = if (deleted) "Archivo eliminado." else "No se pudo eliminar."
                        refreshLimiters()
                        limiterDeleteTarget = null
                    }
                ) {
                    Text("Quitar")
                }
            },
            dismissButton = {
                TextButton(onClick = { limiterDeleteTarget = null }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Quitar limitador") },
            text = { Text("Se eliminara la copia interna de ${target.displayName}.") }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, description: String? = null) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
    if (description != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * FINAL-FLOW01: seccion "Interaccion". Agrupa la activacion por voz, la
 * conversacion inicial opcional y la captura temprana de respuestas.
 */
@Composable
private fun InteractionSettingsSection(
    conversationEnabled: Boolean,
    conversationTurnsText: String,
    conversationDurationText: String,
    earlyAnswerEnabled: Boolean,
    earlyWindowText: String,
    onConversationEnabledChange: (Boolean) -> Unit,
    onConversationTurnsChange: (String) -> Unit,
    onConversationDurationChange: (String) -> Unit,
    onEarlyAnswerEnabledChange: (Boolean) -> Unit,
    onEarlyWindowChange: (String) -> Unit
) {
    SettingsSectionHeader(
        title = "Interaccion",
        description = "Activacion por voz: el nino dice \"Hola Seven\" para empezar " +
            "(tambien se aceptan \"oye Seven\" y la frase antigua \"Seven empieza\")."
    )
    SettingsSwitchRow(
        label = "Conversacion inicial con Seven",
        description = "Antes de las preguntas, el nino puede decirle algo breve a Seven. " +
            "No cuenta como pregunta evaluada y usa solo respuestas locales seguras.",
        checked = conversationEnabled,
        onCheckedChange = onConversationEnabledChange
    )
    OutlinedTextField(
        value = conversationTurnsText,
        onValueChange = onConversationTurnsChange,
        label = { Text("Max. preguntas del nino en la conversacion") },
        supportingText = { Text("0 a 3; recomendado 1") },
        isError = conversationTurnsText.toIntOrNull()?.let {
            !AppSettings.isValidInitialConversationTurns(it)
        } ?: true,
        enabled = conversationEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
    OutlinedTextField(
        value = conversationDurationText,
        onValueChange = onConversationDurationChange,
        label = { Text("Duracion maxima de la conversacion (segundos)") },
        supportingText = { Text("15 a 120 segundos; recomendado 60") },
        isError = conversationDurationText.toIntOrNull()?.let {
            !AppSettings.isValidInitialConversationDurationSeconds(it)
        } ?: true,
        enabled = conversationEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
    SettingsSwitchRow(
        label = "Captura temprana de respuesta",
        description = "En los reintentos, Seven escucha un momento antes de repetir la " +
            "pregunta, para no perder respuestas anticipadas del nino.",
        checked = earlyAnswerEnabled,
        onCheckedChange = onEarlyAnswerEnabledChange
    )
    OutlinedTextField(
        value = earlyWindowText,
        onValueChange = onEarlyWindowChange,
        label = { Text("Ventana temprana (milisegundos)") },
        supportingText = { Text("500 a 4000 ms; recomendado 2000") },
        isError = earlyWindowText.toIntOrNull()?.let {
            !AppSettings.isValidEarlyAnswerWindowMs(it)
        } ?: true,
        enabled = earlyAnswerEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

/**
 * FINAL-FLOW01: seccion "Modo inteligente". La camara/atencion es opcional y viene
 * apagada; la recaptura es una opcion avanzada que requiere la atencion activa.
 */
@Composable
private fun IntelligentModeSettingsSection(
    attentionEnabled: Boolean,
    recaptureEnabled: Boolean,
    recapturesText: String,
    onAttentionEnabledChange: (Boolean) -> Unit,
    onRecaptureEnabledChange: (Boolean) -> Unit,
    onRecapturesChange: (String) -> Unit
) {
    SettingsSectionHeader(
        title = "Modo inteligente",
        description = "La sesion funciona sin camara. Estas opciones controlan el " +
            "comportamiento real; la depuracion se configura mas abajo."
    )
    SettingsSwitchRow(
        label = "Usar camara/atencion en modo inteligente",
        description = "Activa la camara solo para observar presencia/atencion. No es " +
            "obligatoria para la sesion y no guarda imagenes. Apagada, los reportes " +
            "muestran \"No aplica\".",
        checked = attentionEnabled,
        onCheckedChange = onAttentionEnabledChange
    )
    SettingsSwitchRow(
        label = "Usar recaptura por atencion",
        description = "Si esta activada, Seven puede intentar recuperar la atencion. " +
            "Recomendado solo para pruebas especificas. Requiere la camara/atencion activa.",
        checked = recaptureEnabled && attentionEnabled,
        onCheckedChange = onRecaptureEnabledChange,
        enabled = attentionEnabled
    )
    OutlinedTextField(
        value = recapturesText,
        onValueChange = onRecapturesChange,
        label = { Text("Max. recapturas por sesion") },
        supportingText = { Text("0 a 10 por sesion; recomendado 5") },
        isError = recapturesText.toIntOrNull()?.let {
            !AppSettings.isValidIntelligentMaxRecaptures(it)
        } ?: true,
        enabled = attentionEnabled && recaptureEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

/** FINAL-FLOW01: seccion "Modo temporizador" + guardado de los valores numericos. */
@Composable
private fun ClassicModeSettingsSection(
    classicTimeText: String,
    message: String?,
    onClassicTimeChange: (String) -> Unit,
    onSave: () -> Unit
) {
    SettingsSectionHeader(
        title = "Modo temporizador",
        description = "Valores generales para todas las actividades. No dependen del " +
            "formulario de sesion ni de pregunta."
    )
    OutlinedTextField(
        value = classicTimeText,
        onValueChange = onClassicTimeChange,
        label = { Text("Tiempo de respuesta en temporizador") },
        supportingText = { Text("5 a 120 segundos; recomendado 10") },
        isError = classicTimeText.toIntOrNull()?.let {
            !AppSettings.isValidClassicResponseTime(it)
        } ?: true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
    message?.let {
        Text(
            text = it,
            fontSize = 13.sp,
            color = if (it.contains("entre", ignoreCase = true)) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
    Button(
        onClick = onSave,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Text("Guardar configuracion de modos")
    }
}

@Composable
private fun MarkdownLimitersSection(
    files: List<MarkdownLimiterFile>,
    message: String?,
    onImport: () -> Unit,
    onView: (MarkdownLimiterFile) -> Unit,
    onToggleEnabled: (MarkdownLimiterFile, Boolean) -> Unit,
    onDelete: (MarkdownLimiterFile) -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Limitadores del modo inteligente (.md)",
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Estos archivos limitan el comportamiento de Seven y del juez. No reemplazan las reglas de seguridad de la app.",
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = "No subas datos personales de ninos.",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
    Button(
        onClick = onImport,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text("Importar archivo .md")
    }
    message?.let {
        Text(
            text = it,
            fontSize = 13.sp,
            color = if (it.contains("importado", ignoreCase = true) ||
                it.contains("eliminado", ignoreCase = true)
            ) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    if (files.isEmpty()) {
        Text(
            text = "No hay archivos .md cargados.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        files.forEach { file ->
            MarkdownLimiterRow(
                file = file,
                onView = { onView(file) },
                onToggleEnabled = { onToggleEnabled(file, it) },
                onDelete = { onDelete(file) }
            )
        }
    }
}

@Composable
private fun MarkdownLimiterRow(
    file: MarkdownLimiterFile,
    onView: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F6F6))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = file.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${formatBytes(file.sizeBytes)} | ${formatDate(file.importedAt)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activo", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = file.enabled, onCheckedChange = onToggleEnabled)
                }
                Row {
                    TextButton(onClick = onView) { Text("Ver") }
                    TextButton(onClick = onDelete) { Text("Quitar") }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else "${bytes / 1024} KB"

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))

@Composable
private fun AttentionDebugSettingsSection(
    attentionVisualDebugEnabled: Boolean,
    showTtsDebugInIntelligentMode: Boolean,
    showGptDebugInIntelligentMode: Boolean,
    onAttentionVisualDebugChanged: (Boolean) -> Unit,
    onTtsDebugInIntelligentModeChanged: (Boolean) -> Unit,
    onGptDebugInIntelligentModeChanged: (Boolean) -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Depuracion de atencion",
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Cuando esta activado, se muestra informacion tecnica de atencion y " +
            "reconocimiento de voz durante el modo inteligente. No modifica el flujo " +
            "de la sesion, ni la camara, ni la recaptura, ni la cara de Seven.",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        modifier = Modifier.fillMaxWidth()
    )
    SettingsSwitchRow(
        label = "Mostrar panel de atencion",
        description = "Solo muestra datos tecnicos de atencion y del reconocimiento de voz. " +
            "No cambia el comportamiento de Seven.",
        checked = attentionVisualDebugEnabled,
        onCheckedChange = onAttentionVisualDebugChanged
    )
    SettingsSwitchRow(
        label = "Mostrar TTS en modo inteligente",
        description = "Solo muestra datos tecnicos de voz (proveedor usado, fallback, latencia). " +
            "No cambia el comportamiento de Seven.",
        checked = showTtsDebugInIntelligentMode,
        onCheckedChange = onTtsDebugInIntelligentModeChanged
    )
    SettingsSwitchRow(
        label = "Mostrar GPT en modo inteligente",
        description = "Solo muestra datos tecnicos de evaluacion/GPT (juez, recaptura, mediacion). " +
            "No cambia el comportamiento de Seven.",
        checked = showGptDebugInIntelligentMode,
        onCheckedChange = onGptDebugInIntelligentModeChanged
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GptAiSettingsSection(
    config: GptConfig,
    draftSettings: GptRuntimeSettings,
    message: String?,
    testing: Boolean,
    result: StructuredGptResult?,
    onSettingsChange: (GptRuntimeSettings) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onTest: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Configuracion IA de Seven",
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    GptInfoRow(label = "GPT configurado", value = if (config.hasApiKey) "Si" else "No")
    GptInfoRow(label = "Structured Outputs", value = "Activo")

    SettingsSwitchRow(
        label = "IA activada",
        checked = draftSettings.enabled,
        onCheckedChange = { onSettingsChange(draftSettings.copy(enabled = it)) }
    )

    ModelSelector(
        label = "Modelo principal",
        selected = draftSettings.model,
        onSelected = { onSettingsChange(draftSettings.copy(model = it)) }
    )

    ModelSelector(
        label = "Modelo fallback",
        selected = draftSettings.fallbackModel,
        onSelected = { onSettingsChange(draftSettings.copy(fallbackModel = it)) }
    )

    if (draftSettings.model == draftSettings.fallbackModel) {
        Text(
            text = "Aviso: el modelo fallback es igual al modelo principal.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth()
        )
    }

    NumberSettingField(
        label = "Maximo de tokens",
        value = draftSettings.maxOutputTokens,
        rangeLabel = "80 a 400; recomendado 220",
        onValueChange = { value ->
            onSettingsChange(draftSettings.copy(maxOutputTokens = value))
        }
    )

    NumberSettingField(
        label = "Timeout (ms)",
        value = draftSettings.timeoutMs,
        rangeLabel = "3000 a 20000; recomendado 12000",
        onValueChange = { value ->
            onSettingsChange(draftSettings.copy(timeoutMs = value))
        }
    )

    DecimalSettingField(
        label = "Temperatura",
        value = draftSettings.temperature,
        rangeLabel = "0.0 a 1.0; recomendado 0.4",
        onValueChange = { value ->
            onSettingsChange(draftSettings.copy(temperature = value))
        }
    )

    SettingsSwitchRow(
        label = "Fallback local",
        checked = draftSettings.localFallbackEnabled,
        onCheckedChange = { onSettingsChange(draftSettings.copy(localFallbackEnabled = it)) }
    )

    message?.let {
        Text(
            text = it,
            fontSize = 13.sp,
            color = if (it.contains("no configurado", ignoreCase = true)) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp)
        ) {
            Text("Guardar configuracion IA")
        }

        Button(
            onClick = onReset,
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE7E0EC),
                contentColor = Color(0xFF3F2D4F)
            )
        ) {
            Text("Restaurar valores")
        }
    }

    Button(
        onClick = onTest,
        enabled = !testing,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFD7E8FF),
            contentColor = Color(0xFF173B63)
        )
    ) {
        Text(if (testing) "Probando GPT JSON..." else "Probar GPT JSON")
    }

    result?.let { GptJsonResultBlock(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    label: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            GptRuntimeSettings.ALLOWED_MODELS.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        expanded = false
                        onSelected(model)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    // Cuando la fila esta subordinada (enabled=false) se atenua para dejar claro que
    // depende de otra opcion superior (por ejemplo, la recaptura depende de la atencion).
    val contentAlpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f * contentAlpha)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun NumberSettingField(
    label: String,
    value: Int,
    rangeLabel: String,
    onValueChange: (Int) -> Unit
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            raw.filter(Char::isDigit).toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        supportingText = { Text(rangeLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun DecimalSettingField(
    label: String,
    value: Float,
    rangeLabel: String,
    onValueChange: (Float) -> Unit
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw ->
            raw.replace(',', '.').toFloatOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        supportingText = { Text(rangeLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

@Composable
private fun GptJsonResultBlock(result: StructuredGptResult) {
    val response = result.response
    val text = buildString {
        append("Intent: ").append(response.intent.wireValue).append('\n')
        append("Tipo: ").append(response.responseType.wireValue).append('\n')
        append("Texto: ").append(response.visibleText).append('\n')
        append("Seguridad: ").append(response.safetyLevel.wireValue).append('\n')
        append("safeForTts local: ").append(if (result.validation.effectiveSafeForTts) "Si" else "No").append('\n')
        append("Fallback usado: ").append(if (result.fallbackUsed) "Si" else "No").append('\n')
        append("Bloqueo: ").append(response.blockedReason.wireValue).append('\n')
        append("Reglas fallidas: ").append(result.validation.failedRules.joinToString().ifBlank { "Ninguna" }).append('\n')
        append("Modelo usado: ").append(result.modelUsed).append('\n')
        append("Latencia: ").append(result.latencyMs).append(" ms")
        result.safeMessage?.let { append('\n').append("Error seguro: ").append(it) }
    }

    Text(
        text = text,
        fontSize = 13.sp,
        color = if (result.validation.effectiveSafeForTts) {
            Color(0xFF1B5E20)
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp)
        Text(
            text = if (granted) "Concedido" else "Pendiente",
            color = if (granted) Color(0xFF2E7D32) else Color(0xFFC62828),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun GptSettingsSection(
    config: GptConfig,
    testing: Boolean,
    result: GptResult?,
    onTest: () -> Unit
) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "GPT — Cerebro de Seven",
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    GptInfoRow(label = "GPT habilitado", value = if (config.enabled) "Si" else "No")
    GptInfoRow(label = "GPT configurado", value = if (config.hasApiKey) "Si" else "No")
    GptInfoRow(label = "Modelo", value = config.model)
    GptInfoRow(label = "Modelo fallback", value = config.fallbackModel)
    GptInfoRow(label = "Timeout", value = "${config.timeoutMs} ms")
    GptInfoRow(label = "Maximo tokens", value = config.maxOutputTokens.toString())

    Button(
        onClick = onTest,
        enabled = !testing,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFBDE7D3),
            contentColor = Color(0xFF124735)
        )
    ) {
        Text(if (testing) "Probando GPT..." else "Probar GPT")
    }

    result?.let { GptResultBlock(it) }
}

@Composable
private fun GptInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp)
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun GptResultBlock(result: GptResult) {
    val text = when (result) {
        is GptResult.Success -> buildString {
            append("Resultado: ").append(result.text).append('\n')
            append("Modelo usado: ").append(result.modelUsed).append('\n')
            append("Latencia: ").append(result.latencyMs).append(" ms\n")
            append("Fallback usado: ").append(if (result.fallbackUsed) "Si" else "No")
        }
        is GptResult.Disabled -> buildString {
            append("Estado: GPT desactivado\n")
            append("Texto local: ").append(result.fallbackText)
        }
        is GptResult.Failure -> buildString {
            append("Error: ").append(result.safeMessage).append('\n')
            append("Tipo: ").append(result.errorType.name).append('\n')
            append("Modelo intentado: ").append(result.modelAttempted).append('\n')
            append("Texto local: ").append(result.fallbackText)
        }
    }

    Text(
        text = text,
        fontSize = 13.sp,
        color = when (result) {
            is GptResult.Success -> Color(0xFF1B5E20)
            is GptResult.Disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            is GptResult.Failure -> MaterialTheme.colorScheme.error
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
}

private fun settingsTestPrompt(): GptPrompt = GptPrompt(
    systemInstruction = "Eres Seven, un pequeno alien explorador amigable. Respondes en espanol latino, en 1-2 frases breves, sin emojis. No eres una IA, eres Seven.",
    userMessage = "Genera una frase de saludo breve para un nino explorador.",
    contextTag = "TEST_SETTINGS"
)

private fun settingsJsonTestInput(): SevenInputContract = SevenInputContract(
    intent = "greeting",
    topic = "Animales",
    questionText = "",
    localEvaluation = "not_applicable",
    attemptsRemaining = 3,
    expectedResponseType = "greet",
    canGiveHint = false,
    canGiveFinalAnswer = false,
    maxWords = 25,
    allowedHint = "",
    restrictions = listOf("no_personal_data", "no_ai_mention", "spanish_latin_only"),
    language = "es-419",
    tone = "friendly_curious_alien",
    contextTag = "settings_test",
    answerTokens = emptyList()
)
