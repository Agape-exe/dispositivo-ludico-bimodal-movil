package com.taller.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.taller.app.data.local.entity.AttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttemptDao {

    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getBySessionId(sessionId: Long): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getBySessionIdOnce(sessionId: Long): List<AttemptEntity>

    @Query("SELECT COUNT(*) FROM attempts")
    suspend fun count(): Int

    @Query(
        "UPDATE attempts SET finalAttemptState = :finalState, wasFinalAttempt = :wasFinal, " +
        "transcription = :transcript, semanticResult = :semanticResult, " +
        "classicResult = :classicResult, responseReceivedAtMs = :responseReceivedAtMs, " +
        "finishedAtMs = :finishedAtMs, realResponseTimeMs = :realResponseTimeMs, " +
        "advancedFeedbackType = :advancedFeedbackType, usedSpeechToText = :usedStt, " +
        "sttStartAtMs = :sttStart, sttFinalAtMs = :sttFinalAt, " +
        "semanticStartAtMs = :semStart, semanticEndAtMs = :semEnd, " +
        "logicalResponseAtMs = :logicAt, feedbackStartAtMs = :feedbackAt, " +
        "totalResponseLatencyMs = :responseLatency, " +
        "responseToFeedbackLatencyMs = :feedbackLatency, " +
        "fullPipelineLatencyMs = :pipelineLatency " +
        "WHERE id = :attemptId"
    )
    suspend fun updateFinished(
        attemptId: Long,
        finalState: String,
        wasFinal: Boolean,
        transcript: String?,
        semanticResult: String?,
        classicResult: String?,
        responseReceivedAtMs: Long?,
        finishedAtMs: Long,
        realResponseTimeMs: Long?,
        advancedFeedbackType: String?,
        usedStt: Boolean,
        sttStart: Long?,
        sttFinalAt: Long?,
        semStart: Long?,
        semEnd: Long?,
        logicAt: Long?,
        feedbackAt: Long?,
        responseLatency: Long?,
        feedbackLatency: Long?,
        pipelineLatency: Long?
    )

    @Query("DELETE FROM attempts")
    suspend fun deleteAll()
}
