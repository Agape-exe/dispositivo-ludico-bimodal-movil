package com.taller.app.voice

/** Proveedor de voz seleccionado por el usuario. */
enum class ToyVoiceProviderType {
    /** Text-to-Speech local del dispositivo. Funciona sin conexión. */
    LOCAL,

    /** Proveedor neural por red, con voz más natural. Requiere configuración e internet. */
    NEURAL
}
