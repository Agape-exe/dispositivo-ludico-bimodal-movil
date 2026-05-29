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
}
