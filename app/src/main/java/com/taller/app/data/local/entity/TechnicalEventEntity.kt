package com.taller.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "technical_events",
    indices = [Index("sessionId"), Index("questionId")]
)
data class TechnicalEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long? = null,
    val questionId: Long? = null,
    val eventType: String,
    val message: String? = null,
    val timestamp: Long
)
