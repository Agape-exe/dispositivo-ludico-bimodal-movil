package com.taller.app.voice

enum class ToyVoiceProviderType {
    /** OpenAI Text-to-Speech. Proveedor principal recomendado para Seven. */
    OPENAI_TTS,

    /** Text-to-Speech local del dispositivo. Funciona sin conexion. */
    LOCAL,

    /** Microsoft Azure Cognitive Services Speech. Respaldo neural secundario. */
    AZURE_NEURAL,

    /** ElevenLabs Text-to-Speech. Proveedor neural opcional. Requiere suscripcion activa. */
    ELEVENLABS
}
