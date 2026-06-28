package com.taller.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val topic: String,
    val description: String? = null,
    val objective: String? = null,
    val ageLevel: String? = null,
    val classContextNotes: String? = null,
    val operationMode: String,
    val maxAttempts: Int,
    val maxTimeSeconds: Int,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
