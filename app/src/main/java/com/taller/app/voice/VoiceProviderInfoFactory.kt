package com.taller.app.voice

import com.taller.app.voice.neural.AzureSpeechConfig
import com.taller.app.voice.neural.GeminiTtsConfig
import com.taller.app.voice.neural.OpenAiTtsConfig

fun buildVoiceProviderInfo(settings: ToyVoiceSettings, provider: ToyVoiceProviderType): VoiceProviderInfo =
    when (provider) {
        ToyVoiceProviderType.GEMINI_TTS -> {
            val config = GeminiTtsConfig.fromBuild(settings.geminiVoiceName, settings.geminiInstructions)
            VoiceProviderInfo(model = config.model, voice = config.voiceName)
        }
        ToyVoiceProviderType.OPENAI_TTS -> {
            val config = OpenAiTtsConfig.fromBuild(settings.openAiVoiceName, settings.openAiInstructions)
            VoiceProviderInfo(model = config.model, voice = config.voice)
        }
        ToyVoiceProviderType.AZURE_NEURAL -> {
            val config = AzureSpeechConfig.fromBuild(settings.azureVoiceName)
            VoiceProviderInfo(model = "azure-speech-tts", voice = config.voiceName)
        }
        ToyVoiceProviderType.LOCAL -> VoiceProviderInfo(
            model = "android-tts",
            voice = settings.selectedVoiceName ?: settings.localeTag ?: "device-default"
        )
        ToyVoiceProviderType.ELEVENLABS -> VoiceProviderInfo(
            model = "elevenlabs-tts",
            voice = settings.neuralVoiceId
        )
    }
