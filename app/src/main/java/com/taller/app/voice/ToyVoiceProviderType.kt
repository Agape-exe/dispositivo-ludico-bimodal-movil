package com.taller.app.voice

/**
 * Proveedores de voz (TTS) del juguete.
 *
 * FINAL-CORE02: la voz oficial de Seven usa solo GEMINI_TTS (principal) y
 * OPENAI_TTS (respaldo). Los demas valores quedan RETIRADOS: ya no son
 * seleccionables ni participan de la cadena de reproduccion, pero se conservan
 * en el enum para poder leer configuraciones antiguas y normalizarlas a Gemini
 * sin crashear.
 */
enum class ToyVoiceProviderType {
    /** OpenAI Text-to-Speech. Respaldo oficial cuando Gemini no esta disponible. */
    OPENAI_TTS,

    /** RETIRADO. Text-to-Speech local del dispositivo; se normaliza a Gemini. */
    LOCAL,

    /** RETIRADO. Microsoft Azure Speech; se normaliza a Gemini. */
    AZURE_NEURAL,

    /** Gemini Text-to-Speech. Proveedor principal oficial de la voz de Seven. */
    GEMINI_TTS,

    /** RETIRADO. ElevenLabs; se normaliza a Gemini. */
    ELEVENLABS;

    /** True solo para los proveedores vigentes de la voz de Seven. */
    val isActiveProvider: Boolean
        get() = this == GEMINI_TTS || this == OPENAI_TTS
}
