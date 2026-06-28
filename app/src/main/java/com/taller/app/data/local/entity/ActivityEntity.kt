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
    val updatedAt: Long,
    // GEN01: guion de Seven a nivel de sesion, editable por la docente.
    val generatedIntroText: String? = null,
    val generatedClosingText: String? = null,
    val generatedToneNotes: String? = null,
    val generatedPedagogicalWarnings: String? = null,
    val scriptStatus: String = "NOT_GENERATED",
    val scriptUpdatedAt: Long? = null,
    // TTSV01: estado de la voz pre-generada y cacheada de la sesion.
    val voicePrepStatus: String = "NOT_PREPARED",
    val voicePrepUpdatedAt: Long? = null,
    val voicePrepProvider: String? = null,
    val voicePrepVoice: String? = null,
    val voicePrepReadyCount: Int = 0,
    val voicePrepTotalCount: Int = 0,
    val voicePrepLastError: String? = null
)
