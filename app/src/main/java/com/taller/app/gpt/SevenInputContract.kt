package com.taller.app.gpt

import org.json.JSONArray
import org.json.JSONObject

data class SevenInputContract(
    val intent: String,
    val topic: String,
    val questionText: String,
    val localEvaluation: String,
    val attemptsRemaining: Int,
    val expectedResponseType: String,
    val canGiveHint: Boolean,
    val canGiveFinalAnswer: Boolean,
    val maxWords: Int,
    val allowedHint: String,
    val restrictions: List<String>,
    val language: String,
    val tone: String,
    val contextTag: String,
    val answerTokens: List<String> = emptyList(),
    val recaptureAttemptNumber: Int? = null,
    val recaptureMaxAttempts: Int? = null,
    val recaptureKind: String? = null
) {
    fun toJsonString(): String = JSONObject()
        .put("intent", intent)
        .put("topic", topic)
        .put("questionText", questionText)
        .put("localEvaluation", localEvaluation)
        .put("attemptsRemaining", attemptsRemaining)
        .put("expectedResponseType", expectedResponseType)
        .put("canGiveHint", canGiveHint)
        .put("canGiveFinalAnswer", canGiveFinalAnswer)
        .put("maxWords", maxWords)
        .put("allowedHint", allowedHint)
        .put("restrictions", JSONArray(restrictions))
        .put("language", language)
        .put("tone", tone)
        .put("contextTag", contextTag)
        .put("answerTokens", JSONArray(answerTokens))
        .apply {
            recaptureAttemptNumber?.let { put("recaptureAttemptNumber", it) }
            recaptureMaxAttempts?.let { put("recaptureMaxAttempts", it) }
            recaptureKind?.let { put("recaptureKind", it) }
        }
        .toString()
}
