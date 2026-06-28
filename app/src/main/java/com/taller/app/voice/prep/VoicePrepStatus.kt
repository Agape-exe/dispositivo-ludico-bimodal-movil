package com.taller.app.voice.prep

/**
 * Estado de la voz preparada de una sesion. Se persiste a nivel de actividad para
 * saber si la sesion puede iniciarse sin llamadas de voz de red.
 */
enum class VoicePrepStatus(val storageValue: String) {
    /** Aun no se ha preparado la voz de la sesion. */
    NOT_PREPARED("NOT_PREPARED"),

    /** Preparacion en curso. */
    PREPARING("PREPARING"),

    /** Todas las lineas obligatorias estan cacheadas: la sesion puede iniciarse. */
    READY("READY"),

    /** Algunas lineas se cachearon y otras faltan: la sesion no esta lista. */
    PARTIAL("PARTIAL"),

    /** No se pudo preparar ninguna linea. */
    FAILED("FAILED");

    val isReady: Boolean get() = this == READY

    companion object {
        fun fromStorage(value: String?): VoicePrepStatus =
            entries.firstOrNull { it.storageValue == value } ?: NOT_PREPARED
    }
}
