package com.taller.app.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.taller.app.vision.FaceDetectionService
import com.taller.app.vision.FaceRecognitionService

@Composable
fun VisionTestScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val statusMessage = remember { mutableStateOf("Esperando detección facial...") }

    val faceDetectionService = remember { FaceDetectionService() }
    val faceRecognitionService = remember { FaceRecognitionService() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val executor = ContextCompat.getMainExecutor(context)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                faceDetectionService.analyzeImage(
                                    imageProxy = imageProxy,
                                    onResult = { count, trackingIds ->
                                        val firstTrackingId = trackingIds.firstOrNull()
                                        val recognitionStatus =
                                            faceRecognitionService.recognizeChildPlaceholder(firstTrackingId)

                                        statusMessage.value = if (count > 0) {
                                            "Rostro detectado: $count | $recognitionStatus"
                                        } else {
                                            "No se detecta rostro"
                                        }
                                    },
                                    onError = { exception ->
                                        statusMessage.value = "Error de visión: ${exception.message}"
                                    }
                                )
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exception: Exception) {
                        statusMessage.value = "Error al iniciar cámara: ${exception.message}"
                    }
                }, executor)

                previewView
            }
        )

        Text(
            text = statusMessage.value,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(24.dp)
        )
    }
}