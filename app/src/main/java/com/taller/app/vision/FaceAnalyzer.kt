package com.taller.app.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.taller.app.attention.AttentionEvidence
import com.taller.app.attention.isLookingAtDevice

/**
 * Analizador de frames de CameraX que delega la detección de rostros a ML Kit.
 * No almacena imágenes, frames ni datos biométricos; solo notifica la cantidad
 * de rostros detectados en cada frame o un error de análisis.
 */
class FaceAnalyzer(
    private val onFaceCount: (Int) -> Unit,
    private val onEvidence: (AttentionEvidence) -> Unit = {},
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        detector.process(input)
            .addOnSuccessListener { faces ->
                val timestampMs = System.currentTimeMillis()
                onFaceCount(faces.size)
                val face = faces.firstOrNull()
                val yaw = face?.headEulerAngleY
                val pitch = face?.headEulerAngleX
                val roll = face?.headEulerAngleZ
                val faceDetected = face != null
                val lookingAtDevice = isLookingAtDevice(
                    faceDetected = faceDetected,
                    headYawDegrees = yaw,
                    headPitchDegrees = pitch,
                    headRollDegrees = roll
                )
                onEvidence(
                    AttentionEvidence(
                        faceDetected = faceDetected,
                        lookingAtDevice = lookingAtDevice,
                        headYawDegrees = yaw,
                        headPitchDegrees = pitch,
                        headRollDegrees = roll,
                        confidence = if (faceDetected && lookingAtDevice) 1f else null,
                        timestampMs = timestampMs
                    )
                )
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Error desconocido en análisis facial")
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() {
        try {
            detector.close()
        } catch (_: Exception) {
            // Ignorar: el detector puede estar ya cerrado.
        }
    }
}
