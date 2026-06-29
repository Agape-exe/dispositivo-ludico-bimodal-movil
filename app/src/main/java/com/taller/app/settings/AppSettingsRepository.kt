package com.taller.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

class AppSettingsRepository(private val context: Context) {

    private object Keys {
        val CLASSIC_RESPONSE_TIME_SECONDS =
            intPreferencesKey("classic_response_time_seconds")
        val INTELLIGENT_MAX_RECAPTURES =
            intPreferencesKey("intelligent_max_recaptures")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            classicResponseTimeSeconds = prefs[Keys.CLASSIC_RESPONSE_TIME_SECONDS]
                ?: AppSettings.DEFAULT_CLASSIC_RESPONSE_TIME_SECONDS,
            intelligentMaxRecaptures = prefs[Keys.INTELLIGENT_MAX_RECAPTURES]
                ?: AppSettings.DEFAULT_INTELLIGENT_MAX_RECAPTURES,
            updatedAt = prefs[Keys.UPDATED_AT] ?: 0L
        ).sanitized()
    }

    suspend fun readOnce(): AppSettings = settings.first()

    suspend fun save(settings: AppSettings) {
        require(AppSettings.isValidClassicResponseTime(settings.classicResponseTimeSeconds)) {
            "Tiempo de temporizador fuera de rango"
        }
        require(AppSettings.isValidIntelligentMaxRecaptures(settings.intelligentMaxRecaptures)) {
            "Maximo de recapturas fuera de rango"
        }
        val safe = settings.sanitized(nowMs = System.currentTimeMillis())
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CLASSIC_RESPONSE_TIME_SECONDS] = safe.classicResponseTimeSeconds
            prefs[Keys.INTELLIGENT_MAX_RECAPTURES] = safe.intelligentMaxRecaptures
            prefs[Keys.UPDATED_AT] = safe.updatedAt
        }
    }

    suspend fun saveClassicResponseTimeSeconds(value: Int) {
        save(readOnce().copy(classicResponseTimeSeconds = value))
    }

    suspend fun saveIntelligentMaxRecaptures(value: Int) {
        save(readOnce().copy(intelligentMaxRecaptures = value))
    }
}
