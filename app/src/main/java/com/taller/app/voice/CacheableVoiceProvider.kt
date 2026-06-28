package com.taller.app.voice

/**
 * Capacidad opcional de un proveedor de voz que escribe el audio en cache local
 * por archivo (Gemini y OpenAI). Permite pre-generar la voz antes de la sesion y
 * reproducir SOLO desde cache durante la sesion real, sin llamadas de red.
 *
 * Los proveedores que transmiten audio sin archivo (TTS local, Azure) no la
 * implementan.
 */
interface CacheableVoiceProvider {

    /** true si la frase ya tiene audio cacheado y valido para este proveedor. */
    fun isCached(text: String): Boolean

    /**
     * Genera el audio de la frase hacia la cache (o lo reutiliza si ya existe) SIN
     * reproducirlo. Es la operacion de preparacion previa. Puede usar la red.
     */
    suspend fun synthesizeToCache(text: String): VoicePlaybackResult

    /**
     * Reproduce la frase SOLO si existe audio cacheado y valido. Nunca llama a la
     * red. Devuelve null si no hay cache utilizable (para que la capa superior
     * intente otro proveedor o muestre un aviso). Si el archivo cacheado esta
     * corrupto lo descarta y devuelve null, sin sintesis de red.
     */
    suspend fun speakFromCacheOrNull(
        text: String,
        onPlaybackStart: () -> Unit = {}
    ): VoicePlaybackResult?
}
