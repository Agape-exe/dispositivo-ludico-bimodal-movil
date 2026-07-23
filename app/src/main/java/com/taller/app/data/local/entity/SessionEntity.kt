package com.taller.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("activityId")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val activityName: String? = null,
    val operationMode: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSeconds: Long? = null,
    val totalDurationMs: Long? = null,
    val finalStatus: String? = null,
    val completed: Boolean,
    val totalQuestions: Int = 0,
    val completedQuestions: Int = 0,
    val totalAttempts: Int = 0,
    val correctCount: Int? = null,
    val incorrectCount: Int? = null,
    val noResponseCount: Int = 0,
    val notInterpretableCount: Int? = null,
    val timeoutCount: Int = 0,
    val technicalErrorCount: Int = 0,
    /**
     * FINAL-CORE02: cantidad de respuestas de voz VALIDAS emitidas por el nino
     * durante las preguntas evaluadas del modo inteligente (transcripcion no
     * vacia dentro de la ventana de respuesta). Excluye la activacion
     * "Hola Seven", la conversacion inicial, los timeouts, las capturas vacias
     * y el modo temporizador. Nunca contiene audio, solo el conteo.
     */
    val validVoiceResponseCount: Int = 0
)
