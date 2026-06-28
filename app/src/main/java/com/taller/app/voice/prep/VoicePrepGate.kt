package com.taller.app.voice.prep

/**
 * Compuerta de inicio de sesion: una sesion real (modo inteligente o temporizador
 * fijo) solo puede iniciarse si su voz ya esta preparada y cacheada, para no
 * depender de llamadas de voz de red durante la interaccion con los ninos.
 */
object VoicePrepGate {

    const val NOT_READY_MESSAGE =
        "Esta sesión todavía no tiene la voz de Seven preparada. " +
            "Prepárala desde el panel docente antes de usarla."

    /** true si el estado persistido indica que la voz esta lista (READY). */
    fun isReady(storedStatus: String?): Boolean =
        VoicePrepStatus.fromStorage(storedStatus).isReady
}
