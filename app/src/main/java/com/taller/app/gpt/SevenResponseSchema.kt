package com.taller.app.gpt

import org.json.JSONArray
import org.json.JSONObject

object SevenResponseSchema {
    const val NAME = "SevenResponse"

    fun asJsonObject(): JSONObject = JSONObject()
        .put("type", "object")
        .put("additionalProperties", false)
        .put(
            "required",
            JSONArray(
                listOf(
                    "intent",
                    "responseType",
                    "visibleText",
                    "safetyLevel",
                    "fallbackUsed",
                    "canGiveHint",
                    "canGiveFinalAnswer",
                    "shouldAskRepeat",
                    "shouldRecaptureAttention",
                    "topic",
                    "localEvaluation",
                    "attemptsRemaining",
                    "maxWords",
                    "blockedReason",
                    "safeForTts",
                    "validationNotes"
                )
            )
        )
        .put(
            "properties",
            JSONObject()
                .put("intent", stringEnum(SevenIntent.entries.map { it.wireValue }))
                .put("responseType", stringEnum(SevenResponseType.entries.map { it.wireValue }))
                .put("visibleText", JSONObject().put("type", "string"))
                .put("safetyLevel", stringEnum(SevenSafetyLevel.entries.map { it.wireValue }))
                .put("fallbackUsed", JSONObject().put("type", "boolean"))
                .put("canGiveHint", JSONObject().put("type", "boolean"))
                .put("canGiveFinalAnswer", JSONObject().put("type", "boolean"))
                .put("shouldAskRepeat", JSONObject().put("type", "boolean"))
                .put("shouldRecaptureAttention", JSONObject().put("type", "boolean"))
                .put("topic", JSONObject().put("type", "string"))
                .put("localEvaluation", stringEnum(SevenLocalEvaluation.entries.map { it.wireValue }))
                .put("attemptsRemaining", JSONObject().put("type", "integer"))
                .put("maxWords", JSONObject().put("type", "integer"))
                .put("blockedReason", stringEnum(SevenBlockedReason.entries.map { it.wireValue }))
                .put("safeForTts", JSONObject().put("type", "boolean"))
                .put("validationNotes", JSONObject().put("type", "string"))
        )

    private fun stringEnum(values: List<String>): JSONObject = JSONObject()
        .put("type", "string")
        .put("enum", JSONArray(values))
}
