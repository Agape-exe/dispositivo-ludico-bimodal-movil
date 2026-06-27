package com.taller.app.voice

enum class ToyVoiceProviderType {
    /** OpenAI Text-to-Speech. Respaldo principal cuando Gemini no esta disponible. */
    OPENAI_TTS,

    /** Text-to-Speech local del dispositivo. Funciona sin conexion. */
    LOCAL,

    /** Microsoft Azure Cognitive Services Speech. Respaldo neural secundario. */
    AZURE_NEURAL,

    /** Gemini Text-to-Speech. Proveedor principal recomendado para Seven. */
    GEMINI_TTS,

    /** ElevenLabs Text-to-Speech. Proveedor neural opcional. Requiere suscripcion activa. */
    ELEVENLABS
}
