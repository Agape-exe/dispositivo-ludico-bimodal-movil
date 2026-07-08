package com.taller.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        val INTELLIGENT_ATTENTION_ENABLED =
            booleanPreferencesKey("intelligent_attention_enabled")
        val INTELLIGENT_RECAPTURE_ENABLED =
            booleanPreferencesKey("intelligent_recapture_enabled")
        val INITIAL_CONVERSATION_ENABLED =
            booleanPreferencesKey("initial_conversation_enabled")
        val INITIAL_CONVERSATION_MAX_TURNS =
            intPreferencesKey("initial_conversation_max_turns")
        val INITIAL_CONVERSATION_MAX_DURATION_SECONDS =
            intPreferencesKey("initial_conversation_max_duration_seconds")
        val EARLY_ANSWER_CAPTURE_ENABLED =
            booleanPreferencesKey("early_answer_capture_enabled")
        val EARLY_ANSWER_WINDOW_MS =
            intPreferencesKey("early_answer_window_ms")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { prefs ->
        AppSettings(
            classicResponseTimeSeconds = prefs[Keys.CLASSIC_RESPONSE_TIME_SECONDS]
                ?: AppSettings.DEFAULT_CLASSIC_RESPONSE_TIME_SECONDS,
            intelligentMaxRecaptures = prefs[Keys.INTELLIGENT_MAX_RECAPTURES]
                ?: AppSettings.DEFAULT_INTELLIGENT_MAX_RECAPTURES,
            intelligentAttentionEnabled = prefs[Keys.INTELLIGENT_ATTENTION_ENABLED]
                ?: AppSettings.DEFAULT_INTELLIGENT_ATTENTION_ENABLED,
            intelligentRecaptureEnabled = prefs[Keys.INTELLIGENT_RECAPTURE_ENABLED]
                ?: AppSettings.DEFAULT_INTELLIGENT_RECAPTURE_ENABLED,
            initialConversationEnabled = prefs[Keys.INITIAL_CONVERSATION_ENABLED]
                ?: AppSettings.DEFAULT_INITIAL_CONVERSATION_ENABLED,
            initialConversationMaxChildTurns = prefs[Keys.INITIAL_CONVERSATION_MAX_TURNS]
                ?: AppSettings.DEFAULT_INITIAL_CONVERSATION_MAX_TURNS,
            initialConversationMaxDurationSeconds =
                prefs[Keys.INITIAL_CONVERSATION_MAX_DURATION_SECONDS]
                    ?: AppSettings.DEFAULT_INITIAL_CONVERSATION_MAX_DURATION_SECONDS,
            earlyAnswerCaptureEnabled = prefs[Keys.EARLY_ANSWER_CAPTURE_ENABLED]
                ?: AppSettings.DEFAULT_EARLY_ANSWER_CAPTURE_ENABLED,
            earlyAnswerWindowMs = prefs[Keys.EARLY_ANSWER_WINDOW_MS]
                ?: AppSettings.DEFAULT_EARLY_ANSWER_WINDOW_MS,
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
        require(
            AppSettings.isValidInitialConversationTurns(settings.initialConversationMaxChildTurns)
        ) {
            "Turnos de conversacion inicial fuera de rango"
        }
        require(
            AppSettings.isValidInitialConversationDurationSeconds(
                settings.initialConversationMaxDurationSeconds
            )
        ) {
            "Duracion de conversacion inicial fuera de rango"
        }
        require(AppSettings.isValidEarlyAnswerWindowMs(settings.earlyAnswerWindowMs)) {
            "Ventana de respuesta temprana fuera de rango"
        }
        val safe = settings.sanitized(nowMs = System.currentTimeMillis())
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.CLASSIC_RESPONSE_TIME_SECONDS] = safe.classicResponseTimeSeconds
            prefs[Keys.INTELLIGENT_MAX_RECAPTURES] = safe.intelligentMaxRecaptures
            prefs[Keys.INTELLIGENT_ATTENTION_ENABLED] = safe.intelligentAttentionEnabled
            prefs[Keys.INTELLIGENT_RECAPTURE_ENABLED] = safe.intelligentRecaptureEnabled
            prefs[Keys.INITIAL_CONVERSATION_ENABLED] = safe.initialConversationEnabled
            prefs[Keys.INITIAL_CONVERSATION_MAX_TURNS] = safe.initialConversationMaxChildTurns
            prefs[Keys.INITIAL_CONVERSATION_MAX_DURATION_SECONDS] =
                safe.initialConversationMaxDurationSeconds
            prefs[Keys.EARLY_ANSWER_CAPTURE_ENABLED] = safe.earlyAnswerCaptureEnabled
            prefs[Keys.EARLY_ANSWER_WINDOW_MS] = safe.earlyAnswerWindowMs
            prefs[Keys.UPDATED_AT] = safe.updatedAt
        }
    }

    suspend fun saveClassicResponseTimeSeconds(value: Int) {
        save(readOnce().copy(classicResponseTimeSeconds = value))
    }

    suspend fun saveIntelligentMaxRecaptures(value: Int) {
        save(readOnce().copy(intelligentMaxRecaptures = value))
    }

    suspend fun saveIntelligentAttentionEnabled(value: Boolean) {
        save(readOnce().copy(intelligentAttentionEnabled = value))
    }

    suspend fun saveIntelligentRecaptureEnabled(value: Boolean) {
        save(readOnce().copy(intelligentRecaptureEnabled = value))
    }

    suspend fun saveInitialConversationEnabled(value: Boolean) {
        save(readOnce().copy(initialConversationEnabled = value))
    }

    suspend fun saveEarlyAnswerCaptureEnabled(value: Boolean) {
        save(readOnce().copy(earlyAnswerCaptureEnabled = value))
    }
}
