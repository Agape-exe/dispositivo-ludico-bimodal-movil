package com.taller.app.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.toyVoiceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "toy_voice_settings"
)

class ToyVoiceSettingsRepository(private val context: Context) {

    private object Keys {
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("pitch")
        val LOCALE_TAG = stringPreferencesKey("locale_tag")
        val PROVIDER = stringPreferencesKey("voice_provider")
        val NEURAL_VOICE_ID = stringPreferencesKey("neural_voice_id")
        val AZURE_VOICE_NAME = stringPreferencesKey("azure_voice_name")
        val FALLBACK_TO_LOCAL = booleanPreferencesKey("fallback_to_local")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val settings: Flow<ToyVoiceSettings> = context.toyVoiceDataStore.data.map { prefs ->
        ToyVoiceSettings(
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME],
            speechRate = prefs[Keys.SPEECH_RATE] ?: 0.92f,
            pitch = prefs[Keys.PITCH] ?: 1.12f,
            localeTag = prefs[Keys.LOCALE_TAG],
            provider = prefs[Keys.PROVIDER]?.let { name ->
                runCatching { ToyVoiceProviderType.valueOf(name) }.getOrNull()
            } ?: ToyVoiceProviderType.LOCAL,
            neuralVoiceId = prefs[Keys.NEURAL_VOICE_ID],
            azureVoiceName = prefs[Keys.AZURE_VOICE_NAME],
            fallbackToLocal = prefs[Keys.FALLBACK_TO_LOCAL] ?: true,
            updatedAt = prefs[Keys.UPDATED_AT] ?: 0L
        )
    }

    suspend fun readOnce(): ToyVoiceSettings = settings.first()

    suspend fun save(settings: ToyVoiceSettings) {
        context.toyVoiceDataStore.edit { prefs ->
            if (settings.selectedVoiceName != null) {
                prefs[Keys.SELECTED_VOICE_NAME] = settings.selectedVoiceName
            } else {
                prefs.remove(Keys.SELECTED_VOICE_NAME)
            }
            prefs[Keys.SPEECH_RATE] = settings.speechRate
            prefs[Keys.PITCH] = settings.pitch
            if (settings.localeTag != null) {
                prefs[Keys.LOCALE_TAG] = settings.localeTag
            } else {
                prefs.remove(Keys.LOCALE_TAG)
            }
            prefs[Keys.PROVIDER] = settings.provider.name
            if (settings.neuralVoiceId != null) {
                prefs[Keys.NEURAL_VOICE_ID] = settings.neuralVoiceId
            } else {
                prefs.remove(Keys.NEURAL_VOICE_ID)
            }
            if (settings.azureVoiceName != null) {
                prefs[Keys.AZURE_VOICE_NAME] = settings.azureVoiceName
            } else {
                prefs.remove(Keys.AZURE_VOICE_NAME)
            }
            prefs[Keys.FALLBACK_TO_LOCAL] = settings.fallbackToLocal
            prefs[Keys.UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun reset() {
        save(ToyVoiceSettings())
    }
}
