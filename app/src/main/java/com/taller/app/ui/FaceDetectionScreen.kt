package com.taller.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.taller.app.attention.AttentionRepository
import com.taller.app.vision.FaceAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private enum class CameraStatus { INITIALIZING, READY, ERROR }
private enum class AnalysisStatus { PENDING, OK, ERROR }

@Composable
fun FaceDetectionScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraGranted = remember {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    var cameraStatus by remember { mutableStateOf(CameraStatus.INITIALIZING) }
    var analysisStatus by remember { mutableStateOf(AnalysisStatus.PENDING) }
    var faceCount by remember { mutableIntStateOf(0) }
    var errorDetail by remember { mutableStateOf("") }

    val analyzerExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderHolder = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val faceAnalyzer = remember {
        FaceAnalyzer(
            onFaceCount = { count ->
                if (count > 0) {
                    AttentionRepository.onFaceDetected()
                } else {
                    AttentionRepository.onFaceNotDetected()
                }
                faceCount = count
                if (analysisStatus != AnalysisStatus.OK) analysisStatus = AnalysisStatus.OK
            },
            onError = { msg ->
                analysisStatus = AnalysisStatus.ERROR
                errorDetail = msg
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
            AttentionRepository.reset()
            faceAnalyzer.close()
            analyzerExecutor.shutdown()
        }
    }

    val statusLabel = when {
        !cameraGranted -> "Permiso de cámara no concedido"
        cameraStatus == CameraStatus.ERROR -> "Error de cámara"
        analysisStatus == AnalysisStatus.ERROR -> "Error de análisis facial"
        cameraStatus == CameraStatus.INITIALIZING -> "Cámara inicializando"
        analysisStatus == AnalysisStatus.PENDING -> "Cámara lista"
        faceCount > 0 -> "Persona detectada"
        else -> "No se detecta persona"
    }

    val statusContainerColor = when {
        !cameraGranted -> MaterialTheme.colorScheme.errorContainer
        cameraStatus == CameraStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        analysisStatus == AnalysisStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        cameraStatus == CameraStatus.INITIALIZING -> MaterialTheme.colorScheme.surfaceVariant
        analysisStatus == AnalysisStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
        faceCount > 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val statusContentColor = when {
        !cameraGranted -> MaterialTheme.colorScheme.onErrorContainer
        cameraStatus == CameraStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        analysisStatus == AnalysisStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        cameraStatus == CameraStatus.INITIALIZING -> MaterialTheme.colorScheme.onSurfaceVariant
        analysisStatus == AnalysisStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        faceCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("← Volver")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Prueba de detección facial",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Usa esta pantalla para verificar si la cámara frontal detecta un rostro frente al dispositivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Área de cámara (o mensaje si no hay permiso)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (cameraGranted) {
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
                                        cameraStatus = CameraStatus.ERROR
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
                                    cameraStatus = CameraStatus.READY
                                } catch (e: Exception) {
                                    cameraStatus = CameraStatus.ERROR
                                    errorDetail = e.localizedMessage
                                        ?: "Error desconocido al inicializar cámara."
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )
                } else {
                    Text(
                        text = "Concede el permiso de cámara desde la pantalla principal o desde Configuración para iniciar la prueba.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjeta de estado
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = statusContainerColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Estado",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusContentColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusContentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Tarjeta de conteo de rostros
        AnimatedVisibility(
            visible = cameraGranted
                && cameraStatus == CameraStatus.READY
                && analysisStatus == AnalysisStatus.OK
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rostros detectados",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = faceCount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Detalle de error (si lo hubiera)
        AnimatedVisibility(
            visible = errorDetail.isNotBlank()
                && (cameraStatus == CameraStatus.ERROR
                    || analysisStatus == AnalysisStatus.ERROR)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detalle del error",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
