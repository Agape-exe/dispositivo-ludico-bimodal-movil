package com.taller.app.speech

/**
 * Configuracion del reconocimiento de voz infantil (STT) del modo inteligente.
 *
 * Es independiente de la configuracion de la voz de Seven (TTS). El idioma por
 * defecto es espanol del Peru (es-PE); si un proveedor remoto no lo soporta se
 * usara la variante regional mas cercana (es-419 / es-ES) al integrarlo.
 */
data class SttSettings(
    val primaryProvider: SttProviderType = DEFAULT_PRIMARY,
    val fallbackProvider: SttProviderType = DEFAULT_FALLBACK,
    val languageTag: String = DEFAULT_LANGUAGE_TAG
) {
    companion object {
        val DEFAULT_PRIMARY = SttProviderType.GOOGLE_CLOUD
        val DEFAULT_FALLBACK = SttProviderType.OPENAI_TRANSCRIPTION
        const val DEFAULT_LANGUAGE_TAG = "es-PE"

        fun defaults(): SttSettings = SttSettings()
    }
}

/**
 * Disponibilidad real de cada proveedor STT en el dispositivo/compilacion.
 * Se calcula en tiempo de ejecucion (claves presentes, reconocedor del sistema).
 */
data class SttAvailability(
    val googleConfigured: Boolean,
    val openAiConfigured: Boolean,
    val androidAvailable: Boolean
)

/**
 * Resuelve la cadena efectiva de proveedores STT: primero el principal
 * configurado (si esta disponible), luego el respaldo y siempre el respaldo
 * tecnico local al final. Nunca devuelve una cadena vacia mientras exista el
 * reconocedor del sistema; si ni siquiera existe, devuelve una lista vacia y el
 * llamador debe mostrar un error claro de configuracion (sin crashear).
 *
 * Es logica pura sin Android para poder probarse de forma aislada.
 */
object SttProviderResolver {

    fun resolveChain(
        settings: SttSettings,
        availability: SttAvailability
    ): List<SttProviderType> {
        val chain = mutableListOf<SttProviderType>()
        fun addIfAvailable(provider: SttProviderType) {
            if (provider in chain) return
            val available = when (provider) {
                SttProviderType.GOOGLE_CLOUD -> availability.googleConfigured
                SttProviderType.OPENAI_TRANSCRIPTION -> availability.openAiConfigured
                SttProviderType.ANDROID_SYSTEM -> availability.androidAvailable
            }
            if (available) chain.add(provider)
        }
        addIfAvailable(settings.primaryProvider)
        addIfAvailable(settings.fallbackProvider)
        // Respaldo tecnico local: garantiza que la captura de voz siga
        // funcionando sin claves remotas o sin red.
        addIfAvailable(SttProviderType.ANDROID_SYSTEM)
        return chain
    }

    /** Proveedor que atendera el proximo turno de escucha, o null si no hay ninguno. */
    fun effectiveProvider(
        settings: SttSettings,
        availability: SttAvailability
    ): SttProviderType? = resolveChain(settings, availability).firstOrNull()
}
