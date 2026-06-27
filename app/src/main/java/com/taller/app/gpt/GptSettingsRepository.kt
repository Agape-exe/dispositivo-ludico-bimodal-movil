package com.taller.app.gpt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gptSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gpt_runtime_settings"
)

class GptSettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val MODEL = stringPreferencesKey("model")
        val FALLBACK_MODEL = stringPreferencesKey("fallback_model")
        val MAX_OUTPUT_TOKENS = intPreferencesKey("max_output_tokens")
        val TIMEOUT_MS = intPreferencesKey("timeout_ms")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val LOCAL_FALLBACK_ENABLED = booleanPreferencesKey("local_fallback_enabled")
        val STRUCTURED_OUTPUTS_ENABLED = booleanPreferencesKey("structured_outputs_enabled")
    }

    val settings: Flow<GptRuntimeSettings> = context.gptSettingsDataStore.data.map { prefs ->
        GptRuntimeSettings(
            enabled = prefs[Keys.ENABLED] ?: GptRuntimeSettings.defaults().enabled,
            model = prefs[Keys.MODEL] ?: GptRuntimeSettings.defaults().model,
            fallbackModel = prefs[Keys.FALLBACK_MODEL] ?: GptRuntimeSettings.defaults().fallbackModel,
            maxOutputTokens = prefs[Keys.MAX_OUTPUT_TOKENS] ?: GptRuntimeSettings.defaults().maxOutputTokens,
            timeoutMs = prefs[Keys.TIMEOUT_MS] ?: GptRuntimeSettings.defaults().timeoutMs,
            temperature = prefs[Keys.TEMPERATURE] ?: GptRuntimeSettings.defaults().temperature,
            localFallbackEnabled = prefs[Keys.LOCAL_FALLBACK_ENABLED] ?: true,
            structuredOutputsEnabled = prefs[Keys.STRUCTURED_OUTPUTS_ENABLED] ?: true
        ).sanitized()
    }

    suspend fun readOnce(): GptRuntimeSettings = settings.first()

    suspend fun save(settings: GptRuntimeSettings) {
        val safe = settings.sanitized()
        context.gptSettingsDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = safe.enabled
            prefs[Keys.MODEL] = safe.model
            prefs[Keys.FALLBACK_MODEL] = safe.fallbackModel
            prefs[Keys.MAX_OUTPUT_TOKENS] = safe.maxOutputTokens
            prefs[Keys.TIMEOUT_MS] = safe.timeoutMs
            prefs[Keys.TEMPERATURE] = safe.temperature
            prefs[Keys.LOCAL_FALLBACK_ENABLED] = safe.localFallbackEnabled
            prefs[Keys.STRUCTURED_OUTPUTS_ENABLED] = true
        }
    }

    suspend fun saveEnabled(enabled: Boolean) {
        save(readOnce().copy(enabled = enabled))
    }

    suspend fun saveModel(model: String) {
        save(readOnce().copy(model = model))
    }

    suspend fun saveFallbackModel(fallbackModel: String) {
        save(readOnce().copy(fallbackModel = fallbackModel))
    }

    suspend fun saveMaxOutputTokens(maxOutputTokens: Int) {
        save(readOnce().copy(maxOutputTokens = maxOutputTokens))
    }

    suspend fun saveTimeoutMs(timeoutMs: Int) {
        save(readOnce().copy(timeoutMs = timeoutMs))
    }

    suspend fun saveTemperature(temperature: Float) {
        save(readOnce().copy(temperature = temperature))
    }

    suspend fun saveLocalFallbackEnabled(enabled: Boolean) {
        save(readOnce().copy(localFallbackEnabled = enabled))
    }

    suspend fun reset() {
        save(GptRuntimeSettings.defaults())
    }
}
