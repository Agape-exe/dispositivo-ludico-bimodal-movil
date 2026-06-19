package com.taller.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.taller.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<SessionEntity>>

    @Query(
        "UPDATE sessions SET endedAt = :endedAt, durationSeconds = :durationSeconds, " +
        "finalStatus = :finalStatus, completed = :completed WHERE id = :id"
    )
    suspend fun updateEnd(
        id: Long,
        endedAt: Long,
        durationSeconds: Long,
        finalStatus: String,
        completed: Boolean
    )

    @Query(
        "UPDATE sessions SET endedAt = :endedAt, durationSeconds = :durationSeconds, " +
        "totalDurationMs = :totalDurationMs, finalStatus = :finalStatus, completed = :completed, " +
        "completedQuestions = :completedQuestions, totalAttempts = :totalAttempts, " +
        "correctCount = :correctCount, incorrectCount = :incorrectCount, " +
        "noResponseCount = :noResponseCount, notInterpretableCount = :notInterpretableCount, " +
        "timeoutCount = :timeoutCount, technicalErrorCount = :technicalErrorCount " +
        "WHERE id = :id"
    )
    suspend fun updateFinal(
        id: Long,
        endedAt: Long,
        durationSeconds: Long,
        totalDurationMs: Long,
        finalStatus: String,
        completed: Boolean,
        completedQuestions: Int,
        totalAttempts: Int,
        correctCount: Int?,
        incorrectCount: Int?,
        noResponseCount: Int,
        notInterpretableCount: Int?,
        timeoutCount: Int,
        technicalErrorCount: Int
    )
}
