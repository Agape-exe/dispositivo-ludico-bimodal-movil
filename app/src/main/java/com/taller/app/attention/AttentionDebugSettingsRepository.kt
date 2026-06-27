package com.taller.app.attention

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.attentionDebugSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "attention_debug_settings"
)

class AttentionDebugSettingsRepository(private val context: Context) {

    private object Keys {
        val ATTENTION_VISUAL_DEBUG_ENABLED =
            booleanPreferencesKey("attention_visual_debug_enabled")
        val SHOW_TTS_DEBUG_IN_INTELLIGENT_MODE =
            booleanPreferencesKey("show_tts_debug_in_intelligent_mode")
        val SHOW_GPT_DEBUG_IN_INTELLIGENT_MODE =
            booleanPreferencesKey("show_gpt_debug_in_intelligent_mode")
    }

    val settings: Flow<AttentionDebugSettings> =
        context.attentionDebugSettingsDataStore.data.map { prefs ->
            AttentionDebugSettings(
                attentionVisualDebugEnabled =
                    prefs[Keys.ATTENTION_VISUAL_DEBUG_ENABLED] ?: false,
                showTtsDebugInIntelligentMode =
                    prefs[Keys.SHOW_TTS_DEBUG_IN_INTELLIGENT_MODE] ?: false,
                showGptDebugInIntelligentMode =
                    prefs[Keys.SHOW_GPT_DEBUG_IN_INTELLIGENT_MODE] ?: false
            )
        }

    suspend fun readOnce(): AttentionDebugSettings = settings.first()

    suspend fun save(settings: AttentionDebugSettings) {
        context.attentionDebugSettingsDataStore.edit { prefs ->
            prefs[Keys.ATTENTION_VISUAL_DEBUG_ENABLED] =
                settings.attentionVisualDebugEnabled
            prefs[Keys.SHOW_TTS_DEBUG_IN_INTELLIGENT_MODE] =
                settings.showTtsDebugInIntelligentMode
            prefs[Keys.SHOW_GPT_DEBUG_IN_INTELLIGENT_MODE] =
                settings.showGptDebugInIntelligentMode
        }
    }

    suspend fun saveAttentionVisualDebugEnabled(enabled: Boolean) {
        save(readOnce().copy(attentionVisualDebugEnabled = enabled))
    }

    suspend fun saveTtsDebugInIntelligentMode(enabled: Boolean) {
        save(readOnce().copy(showTtsDebugInIntelligentMode = enabled))
    }

    suspend fun saveGptDebugInIntelligentMode(enabled: Boolean) {
        save(readOnce().copy(showGptDebugInIntelligentMode = enabled))
    }
}
