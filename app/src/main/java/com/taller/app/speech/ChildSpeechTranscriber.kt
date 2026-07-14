package com.taller.app.speech

import android.content.Context

/**
 * FINAL-CORE02: fachada unica de reconocimiento de voz infantil (STT) del modo
 * inteligente. Activacion ("Hola Seven"), conversacion inicial, ventana temprana
 * y respuestas evaluadas capturan a traves de esta clase, nunca instanciando el
 * reconocedor por su cuenta.
 *
 * Cadena de proveedores (ver [SttProviderResolver]):
 *  - GOOGLE_CLOUD (principal, requiere GOOGLE_SPEECH_API_KEY),
 *  - OPENAI_TRANSCRIPTION (respaldo, requiere OPENAI_API_KEY),
 *  - ANDROID_SYSTEM (respaldo tecnico local, siempre al final).
 *
 * Estado actual: el motor que EJECUTA la captura es Android SpeechRecognizer
 * ([SpeechToTextService]). Los motores remotos (Google/OpenAI) quedan
 * configurados y resueltos por la cadena, pero su captura por streaming/archivo
 * temporal aun no esta cableada; cuando se integren, esta fachada elegira el
 * primero disponible de la cadena sin que los flujos cambien. Asi los llamadores
 * ya dependen de la abstraccion y no del reconocedor concreto.
 *
 * Privacidad: no se guarda audio crudo ni archivos temporales; solo se entrega
 * texto transcrito en memoria.
 */
class ChildSpeechTranscriber(
    private val context: Context,
    private val settingsProvider: () -> SttSettings = { SttSettings.defaults() }
) {
    private val androidEngine = SpeechToTextService(context)

    /** Disponibilidad real de cada proveedor en este dispositivo/compilacion. */
    fun availability(): SttAvailability = SttAvailability(
        googleConfigured = SttProviderConfig.googleConfigured(),
        openAiConfigured = SttProviderConfig.openAiConfigured(),
        androidAvailable = SpeechToTextService.isAvailable(context)
    )

    /** Cadena configurada de proveedores, en orden de preferencia. */
    fun resolvedChain(): List<SttProviderType> =
        SttProviderResolver.resolveChain(settingsProvider(), availability())

    /**
     * Motor que ejecuta la captura HOY. Sera el primero de [resolvedChain]
     * cuando los motores remotos esten cableados; mientras tanto es el
     * reconocedor local del sistema.
     */
    fun activeEngine(): SttProviderType = SttProviderType.ANDROID_SYSTEM

    fun isAvailable(): Boolean = SpeechToTextService.isAvailable(context)

    fun startListening(
        onStateChange: (SttState) -> Unit,
        onReady: () -> Unit,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onStopped: (String) -> Unit,
        onError: (String) -> Unit,
        onSpeechStart: () -> Unit = {},
        onSpeechEnd: () -> Unit = {}
    ) {
        androidEngine.startListening(
            onStateChange = onStateChange,
            onReady = onReady,
            onPartialResult = onPartialResult,
            onFinalResult = onFinalResult,
            onStopped = onStopped,
            onError = onError,
            onSpeechStart = onSpeechStart,
            onSpeechEnd = onSpeechEnd
        )
    }

    fun stopListening() = androidEngine.stopListening()

    fun destroy() = androidEngine.destroy()

    companion object {
        fun providerLabel(type: SttProviderType): String = when (type) {
            SttProviderType.GOOGLE_CLOUD -> "Google Cloud Speech-to-Text"
            SttProviderType.OPENAI_TRANSCRIPTION -> "OpenAI Transcription"
            SttProviderType.ANDROID_SYSTEM -> "Android SpeechRecognizer"
        }
    }
}
