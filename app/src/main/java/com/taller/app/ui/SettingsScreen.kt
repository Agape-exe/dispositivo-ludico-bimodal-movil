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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.taller.app.gpt.GptClientImpl
import com.taller.app.gpt.GptConfig
import com.taller.app.gpt.GptPrompt
import com.taller.app.gpt.GptResult
import com.taller.app.gpt.GptRuntimeSettings
import com.taller.app.gpt.GptSettingsRepository
import com.taller.app.gpt.SevenInputContract
import com.taller.app.gpt.StructuredGptResult
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
    val gptSettingsRepository = remember { GptSettingsRepository(context.applicationContext) }
    val savedGptSettings by gptSettingsRepository.settings.collectAsState(initial = GptRuntimeSettings.defaults())
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

    LaunchedEffect(savedGptSettings) {
        draftGptSettings = savedGptSettings
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

            Button(
                onClick = onNavigateToToyVoiceSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB8B3DF),
                    contentColor = Color(0xFF3D2B8A)
                )
            ) {
                Text("Configurar voz del juguete")
            }

            Button(
                onClick = onNavigateToFaceDetection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
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
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
