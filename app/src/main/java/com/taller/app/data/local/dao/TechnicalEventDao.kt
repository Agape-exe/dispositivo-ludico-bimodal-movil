package com.taller.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.taller.app.data.local.entity.TechnicalEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TechnicalEventDao {

    @Insert
    suspend fun insert(event: TechnicalEventEntity): Long

    @Query("SELECT * FROM technical_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: Long): Flow<List<TechnicalEventEntity>>

    @Query("SELECT * FROM technical_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionIdOnce(sessionId: Long): List<TechnicalEventEntity>

    @Query("SELECT COUNT(*) FROM technical_events")
    suspend fun count(): Int
}
