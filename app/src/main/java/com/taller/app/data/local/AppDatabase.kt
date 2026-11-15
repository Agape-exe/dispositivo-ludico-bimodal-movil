package com.taller.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.taller.app.data.local.dao.ActivityDao
import com.taller.app.data.local.dao.AttemptDao
import com.taller.app.data.local.dao.QuestionDao
import com.taller.app.data.local.dao.SessionDao
import com.taller.app.data.local.dao.TechnicalEventDao
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.AttemptEntity
import com.taller.app.data.local.entity.QuestionEntity
import com.taller.app.data.local.entity.SessionEntity
import com.taller.app.data.local.entity.TechnicalEventEntity

@Database(
    entities = [
        ActivityEntity::class,
        QuestionEntity::class,
        SessionEntity::class,
        AttemptEntity::class,
        TechnicalEventEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao
    abstract fun questionDao(): QuestionDao
    abstract fun sessionDao(): SessionDao
    abstract fun attemptDao(): AttemptDao
    abstract fun technicalEventDao(): TechnicalEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE questions ADD COLUMN mediationKey TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // sessions: nuevos campos de resumen y conteos
                database.execSQL("ALTER TABLE sessions ADD COLUMN activityName TEXT")
                database.execSQL("ALTER TABLE sessions ADD COLUMN totalQuestions INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN completedQuestions INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN totalAttempts INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN correctCount INTEGER")
                database.execSQL("ALTER TABLE sessions ADD COLUMN incorrectCount INTEGER")
                database.execSQL("ALTER TABLE sessions ADD COLUMN noResponseCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN notInterpretableCount INTEGER")
                database.execSQL("ALTER TABLE sessions ADD COLUMN timeoutCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN technicalErrorCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sessions ADD COLUMN totalDurationMs INTEGER")

                // attempts: nuevos campos de intento, resultado y latencias
                database.execSQL("ALTER TABLE attempts ADD COLUMN questionOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempts ADD COLUMN operationMode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE attempts ADD COLUMN questionText TEXT")
                database.execSQL("ALTER TABLE attempts ADD COLUMN classicResult TEXT")
                database.execSQL("ALTER TABLE attempts ADD COLUMN startedAtMs INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempts ADD COLUMN responseReceivedAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN finishedAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN maxTimeMs INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempts ADD COLUMN realResponseTimeMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN usedSemanticEvaluation INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempts ADD COLUMN usedSpeechToText INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempts ADD COLUMN wasFinalAttempt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE attempts ADD COLUMN advancedFeedbackType TEXT")
                database.execSQL("ALTER TABLE attempts ADD COLUMN finalAttemptState TEXT NOT NULL DEFAULT 'UNKNOWN'")
                database.execSQL("ALTER TABLE attempts ADD COLUMN sttStartAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN sttFinalAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN semanticStartAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN semanticEndAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN logicalResponseAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN feedbackStartAtMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN totalResponseLatencyMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN responseToFeedbackLatencyMs INTEGER")
                database.execSQL("ALTER TABLE attempts ADD COLUMN fullPipelineLatencyMs INTEGER")

                // technical_events: nuevos campos de trazabilidad
                database.execSQL("ALTER TABLE technical_events ADD COLUMN attemptId INTEGER")
                database.execSQL("ALTER TABLE technical_events ADD COLUMN operationMode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE technical_events ADD COLUMN latencyMs INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE activities ADD COLUMN classContextNotes TEXT")
                database.execSQL("ALTER TABLE activities ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // activities: guion de Seven a nivel de sesion (GEN01)
                database.execSQL("ALTER TABLE activities ADD COLUMN generatedIntroText TEXT")
                database.execSQL("ALTER TABLE activities ADD COLUMN generatedClosingText TEXT")
                database.execSQL("ALTER TABLE activities ADD COLUMN generatedToneNotes TEXT")
                database.execSQL("ALTER TABLE activities ADD COLUMN generatedPedagogicalWarnings TEXT")
                database.execSQL("ALTER TABLE activities ADD COLUMN scriptStatus TEXT NOT NULL DEFAULT 'NOT_GENERATED'")
                database.execSQL("ALTER TABLE activities ADD COLUMN scriptUpdatedAt INTEGER")

                // questions: guion de Seven a nivel de pregunta (GEN01)
                database.execSQL("ALTER TABLE questions ADD COLUMN childFriendlyQuestionText TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN hintLevel1 TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN hintLevel2 TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN hintLevel3 TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN positiveFeedbackText TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN supportiveFeedbackText TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN retryPromptText TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN answerReferenceWarning TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN suggestedReferenceAnswer TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN scriptReviewed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE questions ADD COLUMN scriptUpdatedAt INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // activities: estado de la voz pre-generada y cacheada (TTSV01).
                database.execSQL("ALTER TABLE activities ADD COLUMN voicePrepStatus TEXT NOT NULL DEFAULT 'NOT_PREPARED'")
                database.execSQL("ALTER TABLE activities ADD COLUMN voicePrepUpdatedAt INTEGER")
                database.execSQL("ALTER TABLE activities ADD COLUMN voicePrepProvider TEXT")
                database.execSQL("ALTER TABLE activities ADD COLUMN voicePrepVoice TEXT")
                database.execSQL("ALTER TABLE activities ADD COLUMN voicePrepReadyCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE activities ADD COLUMN voicePrepTotalCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE activities ADD COLUMN voicePrepLastError TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // sessions: conteo oficial de respuestas de voz validas del nino
                // en el modo inteligente (FINAL-CORE02).
                database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN validVoiceResponseCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taller_app_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build().also { INSTANCE = it }
            }
        }
    }
}
