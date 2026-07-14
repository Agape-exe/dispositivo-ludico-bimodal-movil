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

    @Query(
        "UPDATE questions SET " +
            "childFriendlyQuestionText = :childFriendlyQuestionText, " +
            "hintLevel1 = :hintLevel1, " +
            "hintLevel2 = :hintLevel2, " +
            "hintLevel3 = :hintLevel3, " +
            "positiveFeedbackText = :positiveFeedbackText, " +
            "supportiveFeedbackText = :supportiveFeedbackText, " +
            "retryPromptText = :retryPromptText, " +
            "answerReferenceWarning = :answerReferenceWarning, " +
            "suggestedReferenceAnswer = :suggestedReferenceAnswer, " +
            "scriptReviewed = :scriptReviewed, " +
            "scriptUpdatedAt = :scriptUpdatedAt " +
            "WHERE id = :id"
    )
    suspend fun updateScript(
        id: Long,
        childFriendlyQuestionText: String?,
        hintLevel1: String?,
        hintLevel2: String?,
        hintLevel3: String?,
        positiveFeedbackText: String?,
        supportiveFeedbackText: String?,
        retryPromptText: String?,
        answerReferenceWarning: String?,
        suggestedReferenceAnswer: String?,
        scriptReviewed: Boolean,
        scriptUpdatedAt: Long
    )

    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}
