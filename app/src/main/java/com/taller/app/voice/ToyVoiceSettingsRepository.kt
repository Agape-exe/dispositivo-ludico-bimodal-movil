package com.taller.app.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val settings: Flow<ToyVoiceSettings> = context.toyVoiceDataStore.data.map { prefs ->
        ToyVoiceSettings(
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME],
            speechRate = prefs[Keys.SPEECH_RATE] ?: 0.92f,
            pitch = prefs[Keys.PITCH] ?: 1.12f,
            localeTag = prefs[Keys.LOCALE_TAG],
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
            prefs[Keys.UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun reset() {
        save(ToyVoiceSettings())
    }
}
