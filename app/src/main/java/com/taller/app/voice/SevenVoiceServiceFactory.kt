package com.taller.app.voice

import android.content.Context
import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.GeminiTtsVoiceProvider
import com.taller.app.voice.neural.OpenAiTtsConfig
import com.taller.app.voice.neural.OpenAiTtsVoiceProvider

/**
 * Construye un [SevenVoiceService] con la cadena de proveedores oficial
 * (Gemini principal, OpenAI de respaldo). Centraliza el cableado para que la
 * pantalla de preparacion de voz y los modos no lo dupliquen.
 *
 * El [settingsProvider] permite leer la preferencia de voz vigente en cada
 * solicitud.
 */
object SevenVoiceServiceFactory {

    fun create(
        context: Context,
        settingsProvider: () -> ToyVoiceSettings
    ): SevenVoiceService {
        val openAiProvider = OpenAiTtsVoiceProvider(context) {
            val settings = settingsProvider()
            OpenAiTtsConfig.fromBuild(settings.openAiVoiceName, settings.openAiInstructions)
        }
        val geminiProvider = GeminiTtsVoiceProvider(context) {
            val settings = settingsProvider()
            GeminiTtsConfig.fromBuild(settings.geminiVoiceName, settings.geminiInstructions)
        }
        return SevenVoiceService(
            geminiProvider = geminiProvider,
            openAiProvider = openAiProvider,
            preferredProvider = { settingsProvider().provider },
            providerInfo = { buildVoiceProviderInfo(settingsProvider(), it) }
        )
    }
}
