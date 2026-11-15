package com.taller.app.speech

/**
 * Proveedores de reconocimiento de voz (STT) del modo inteligente.
 *
 * FINAL-CORE02: se separa formalmente el STT (voz del nino a texto) del TTS
 * (voz de Seven). El diseno oficial es:
 *  - GOOGLE_CLOUD como principal (Cloud Speech-to-Text, requiere credencial),
 *  - OPENAI_TRANSCRIPTION como respaldo remoto (requiere OPENAI_API_KEY),
 *  - ANDROID_SYSTEM como respaldo tecnico local (SpeechRecognizer del sistema,
 *    funciona sin claves y sin depender de la red propia de la app).
 *
 * Ningun proveedor guarda audio crudo: el audio del turno solo se usa para
 * transcribir y se descarta.
 */
enum class SttProviderType {
    /** Google Cloud Speech-to-Text. Proveedor principal cuando hay credencial. */
    GOOGLE_CLOUD,

    /** Transcripcion de OpenAI. Respaldo remoto cuando Google no esta disponible. */
    OPENAI_TRANSCRIPTION,

    /** Android SpeechRecognizer. Respaldo tecnico local, siempre disponible. */
    ANDROID_SYSTEM
}
