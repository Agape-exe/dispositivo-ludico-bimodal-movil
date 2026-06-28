package com.taller.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
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
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val questionText: String,
    val expectedAnswer: String,
    val keywords: String,
    val orderIndex: Int,
    val maxAttempts: Int,
    val maxTimeSeconds: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val mediationKey: String? = null,
    // GEN01: guion de Seven a nivel de pregunta, editable por la docente.
    val childFriendlyQuestionText: String? = null,
    val hintLevel1: String? = null,
    val hintLevel2: String? = null,
    val hintLevel3: String? = null,
    val positiveFeedbackText: String? = null,
    val supportiveFeedbackText: String? = null,
    val retryPromptText: String? = null,
    val answerReferenceWarning: String? = null,
    val suggestedReferenceAnswer: String? = null,
    val scriptReviewed: Boolean = false,
    val scriptUpdatedAt: Long? = null
)
