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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    val gptConfig = remember { GptConfig.fromBuild() }
    val gptClient = remember { GptClientImpl(configProvider = { GptConfig.fromBuild() }) }

    fun isCameraGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun isAudioGranted() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(isCameraGranted()) }
    var audioGranted by remember { mutableStateOf(isAudioGranted()) }
    var showSettingsHint by remember { mutableStateOf(false) }
    var gptTesting by remember { mutableStateOf(false) }
    var gptTestResult by remember { mutableStateOf<GptResult?>(null) }

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

            GptSettingsSection(
                config = gptConfig,
                testing = gptTesting,
                result = gptTestResult,
                onTest = {
                    gptTesting = true
                    gptTestResult = null
                    coroutineScope.launch {
                        gptTestResult = gptClient.generate(settingsTestPrompt())
                        gptTesting = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
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
