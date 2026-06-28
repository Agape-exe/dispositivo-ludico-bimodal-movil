package com.taller.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.taller.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllOrderedByCreated(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllOrderedByUpdated(): Flow<List<ActivityEntity>>

    @Query("UPDATE activities SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query(
        "UPDATE activities SET " +
            "generatedIntroText = :introText, " +
            "generatedClosingText = :closingText, " +
            "generatedToneNotes = :toneNotes, " +
            "generatedPedagogicalWarnings = :pedagogicalWarnings, " +
            "scriptStatus = :scriptStatus, " +
            "scriptUpdatedAt = :scriptUpdatedAt " +
            "WHERE id = :id"
    )
    suspend fun updateScript(
        id: Long,
        introText: String?,
        closingText: String?,
        toneNotes: String?,
        pedagogicalWarnings: String?,
        scriptStatus: String,
        scriptUpdatedAt: Long
    )

    @Query(
        "UPDATE activities SET " +
            "voicePrepStatus = :status, " +
            "voicePrepUpdatedAt = :updatedAt, " +
            "voicePrepProvider = :provider, " +
            "voicePrepVoice = :voice, " +
            "voicePrepReadyCount = :readyCount, " +
            "voicePrepTotalCount = :totalCount, " +
            "voicePrepLastError = :lastError " +
            "WHERE id = :id"
    )
    suspend fun updateVoicePrep(
        id: Long,
        status: String,
        updatedAt: Long,
        provider: String?,
        voice: String?,
        readyCount: Int,
        totalCount: Int,
        lastError: String?
    )
}
