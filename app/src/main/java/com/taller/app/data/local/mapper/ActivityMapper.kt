package com.taller.app.data.local.mapper

import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.QuestionEntity
import com.taller.app.model.LearningActivity
import com.taller.app.model.LearningQuestion
import com.taller.app.model.OperationMode

fun LearningActivity.toEntity(): ActivityEntity = ActivityEntity(
    id = id.toLongOrNull() ?: 0L,
    name = title,
    topic = "",
    operationMode = mode.name,
    maxAttempts = questions.maxOfOrNull { it.maxAttempts } ?: 3,
    maxTimeSeconds = questions.maxOfOrNull { it.maxTimeSeconds } ?: 60,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)

fun ActivityEntity.toDomain(questions: List<LearningQuestion> = emptyList()): LearningActivity =
    LearningActivity(
        id = id.toString(),
        title = name,
        mode = runCatching { OperationMode.valueOf(operationMode) }.getOrDefault(OperationMode.CLASSIC),
        questions = questions
    )

fun LearningQuestion.toEntity(activityId: Long, orderIndex: Int): QuestionEntity = QuestionEntity(
    id = id.toLongOrNull() ?: 0L,
    activityId = activityId,
    questionText = questionText,
    expectedAnswer = expectedAnswer,
    keywords = keywords.joinToString(","),
    orderIndex = orderIndex,
    maxAttempts = maxAttempts,
    maxTimeSeconds = maxTimeSeconds,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)

fun QuestionEntity.toDomain(): LearningQuestion = LearningQuestion(
    id = id.toString(),
    questionText = questionText,
    expectedAnswer = expectedAnswer,
    keywords = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    maxTimeSeconds = maxTimeSeconds,
    maxAttempts = maxAttempts
)
