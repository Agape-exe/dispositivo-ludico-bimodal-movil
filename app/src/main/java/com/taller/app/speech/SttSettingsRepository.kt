package com.taller.app.speech

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sttSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "stt_settings"
)

/**
 * Persistencia de la configuracion STT en DataStore (fuera de Room: sobrevive
 * al vaciado de datos de prueba). Nunca guarda claves ni audio, solo el nombre
 * del proveedor elegido y el idioma.
 */
class SttSettingsRepository(private val context: Context) {

    private object Keys {
        val PRIMARY = stringPreferencesKey("stt_primary_provider")
        val FALLBACK = stringPreferencesKey("stt_fallback_provider")
        val LANGUAGE = stringPreferencesKey("stt_language_tag")
    }

    val settings: Flow<SttSettings> = context.sttSettingsDataStore.data.map { prefs ->
        SttSettings(
            primaryProvider = prefs[Keys.PRIMARY]?.let { name ->
                runCatching { SttProviderType.valueOf(name) }.getOrNull()
            } ?: SttSettings.DEFAULT_PRIMARY,
            fallbackProvider = prefs[Keys.FALLBACK]?.let { name ->
                runCatching { SttProviderType.valueOf(name) }.getOrNull()
            } ?: SttSettings.DEFAULT_FALLBACK,
            languageTag = prefs[Keys.LANGUAGE] ?: SttSettings.DEFAULT_LANGUAGE_TAG
        )
    }

    suspend fun readOnce(): SttSettings = settings.first()

    suspend fun save(settings: SttSettings) {
        context.sttSettingsDataStore.edit { prefs ->
            prefs[Keys.PRIMARY] = settings.primaryProvider.name
            prefs[Keys.FALLBACK] = settings.fallbackProvider.name
            prefs[Keys.LANGUAGE] = settings.languageTag
        }
    }
}
