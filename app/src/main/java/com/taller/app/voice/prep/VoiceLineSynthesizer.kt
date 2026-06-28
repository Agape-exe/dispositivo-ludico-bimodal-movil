package com.taller.app.voice.prep

import com.taller.app.voice.ToyVoiceProviderType

/**
 * Resultado de pre-generar (o reutilizar de cache) una linea de voz, sin
 * reproducirla.
 */
sealed interface VoiceLinePrepResult {
    /**
     * La linea quedo disponible en cache.
     * @param fromCache true si ya existia en cache y no hubo llamada de red.
     */
    data class Prepared(
        val provider: ToyVoiceProviderType,
        val fromCache: Boolean
    ) : VoiceLinePrepResult

    /** No se pudo preparar la linea. El mensaje es seguro (sin claves ni payloads). */
    data class Failed(
        val safeMessage: String,
        val errorCode: String? = null
    ) : VoiceLinePrepResult
}

/**
 * Capa que sabe pre-generar una linea hacia la cache de voz sin reproducirla.
 *
 * Se abstrae de Android y de la cadena de proveedores para poder probar el
 * preparador de sesion con una implementacion falsa. La implementacion real la
 * provee [com.taller.app.voice.SevenVoiceService].
 */
interface VoiceLineSynthesizer {

    /** Indica si la linea ya esta cacheada por algun proveedor reutilizable. */
    fun isCached(text: String): Boolean

    /** Pre-genera la linea hacia cache (o la reutiliza). No reproduce audio. */
    suspend fun prepare(text: String): VoiceLinePrepResult
}
