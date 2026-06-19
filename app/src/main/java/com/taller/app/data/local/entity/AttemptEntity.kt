package com.taller.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("questionId")]
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val questionId: Long,
    val questionOrder: Int = 0,
    val attemptNumber: Int,
    val operationMode: String = "",
    val questionText: String? = null,
    val transcription: String? = null,
    val semanticResult: String? = null,
    val classicResult: String? = null,
    val startedAtMs: Long = 0,
    val responseReceivedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val maxTimeMs: Long = 0,
    val realResponseTimeMs: Long? = null,
    val responseTimeMs: Long? = null,
    val usedSemanticEvaluation: Boolean = false,
    val usedSpeechToText: Boolean = false,
    val wasFinalAttempt: Boolean = false,
    val advancedFeedbackType: String? = null,
    val finalAttemptState: String = "UNKNOWN",
    val sttLatencyMs: Long? = null,
    val semanticLatencyMs: Long? = null,
    val visualFeedbackLatencyMs: Long? = null,
    val sttStartAtMs: Long? = null,
    val sttFinalAtMs: Long? = null,
    val semanticStartAtMs: Long? = null,
    val semanticEndAtMs: Long? = null,
    val logicalResponseAtMs: Long? = null,
    val feedbackStartAtMs: Long? = null,
    val totalResponseLatencyMs: Long? = null,
    val responseToFeedbackLatencyMs: Long? = null,
    val fullPipelineLatencyMs: Long? = null,
    val createdAt: Long
)
