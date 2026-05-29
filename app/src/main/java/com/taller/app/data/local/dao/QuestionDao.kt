package com.taller.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.taller.app.data.local.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("SELECT * FROM questions WHERE activityId = :activityId ORDER BY orderIndex ASC")
    fun getByActivityId(activityId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE activityId = :activityId ORDER BY orderIndex ASC")
    suspend fun getByActivityIdOnce(activityId: Long): List<QuestionEntity>

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteById(questionId: Long)

    @Query("DELETE FROM questions WHERE activityId = :activityId")
    suspend fun deleteByActivityId(activityId: Long)
}
