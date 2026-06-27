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
    }

    val settings: Flow<AttentionDebugSettings> =
        context.attentionDebugSettingsDataStore.data.map { prefs ->
            AttentionDebugSettings(
                attentionVisualDebugEnabled =
                    prefs[Keys.ATTENTION_VISUAL_DEBUG_ENABLED] ?: false
            )
        }

    suspend fun readOnce(): AttentionDebugSettings = settings.first()

    suspend fun save(settings: AttentionDebugSettings) {
        context.attentionDebugSettingsDataStore.edit { prefs ->
            prefs[Keys.ATTENTION_VISUAL_DEBUG_ENABLED] =
                settings.attentionVisualDebugEnabled
        }
    }

    suspend fun saveAttentionVisualDebugEnabled(enabled: Boolean) {
        save(readOnce().copy(attentionVisualDebugEnabled = enabled))
    }
}
