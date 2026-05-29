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
    val updatedAt: Long
)
