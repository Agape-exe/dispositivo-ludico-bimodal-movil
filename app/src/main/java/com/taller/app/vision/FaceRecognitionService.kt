package com.taller.app.vision

class FaceRecognitionService {

    fun recognizeChildPlaceholder(
        detectedFaceTrackingId: Int?
    ): String {
        // Pendiente: implementar reconocimiento básico del niño.
        // Posible enfoque futuro:
        // 1. Detectar rostro con ML Kit.
        // 2. Recortar rostro detectado.
        // 3. Generar embedding facial con TensorFlow Lite / FaceNet / MobileFaceNet.
        // 4. Comparar embedding con registros previamente guardados.
        // 5. Retornar el nombre o código del niño si supera un umbral de confianza.

        return if (detectedFaceTrackingId != null) {
            "Rostro detectado - reconocimiento pendiente"
        } else {
            "No se detectó rostro"
        }
    }
}