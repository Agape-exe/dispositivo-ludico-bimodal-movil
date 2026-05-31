package com.taller.app.voice

enum class ToyVoiceProviderType {
    /** Text-to-Speech local del dispositivo. Funciona sin conexión. */
    LOCAL,

    /** Microsoft Azure Cognitive Services Speech. Proveedor neural principal. */
    AZURE_NEURAL,

    /** ElevenLabs Text-to-Speech. Proveedor neural opcional. Requiere suscripción activa. */
    ELEVENLABS
}
