package com.taller.app.voice

import android.content.Context
import com.taller.app.voice.neural.AzureSpeechConfig
import com.taller.app.voice.neural.AzureSpeechVoiceProvider
import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.GeminiTtsVoiceProvider
import com.taller.app.voice.neural.OpenAiTtsConfig
import com.taller.app.voice.neural.OpenAiTtsVoiceProvider
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Construye un [SevenVoiceService] con la cadena de proveedores estandar (Gemini
 * principal, OpenAI/Azure/local de respaldo). Centraliza el cableado para que la
 * pantalla de preparacion de voz y los modos no lo dupliquen.
 *
 * El [settingsProvider] permite leer la preferencia de voz vigente en cada
 * solicitud. La voz local se inicializa de forma perezosa al usarse.
 */
object SevenVoiceServiceFactory {

    fun create(
        context: Context,
        ttsStateFlow: MutableStateFlow<ToySpeechState>,
        toySpeechService: ToySpeechService,
        settingsProvider: () -> ToyVoiceSettings
    ): SevenVoiceService {
        val localProvider = LocalToyVoiceProvider(toySpeechService, ttsStateFlow) { settingsProvider() }
        val azureProvider = AzureSpeechVoiceProvider(context) {
            AzureSpeechConfig.fromBuild(settingsProvider().azureVoiceName)
        }
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
            azureProvider = azureProvider,
            localProvider = localProvider,
            preferredProvider = { settingsProvider().provider },
            providerInfo = { buildVoiceProviderInfo(settingsProvider(), it) }
        )
    }
}
