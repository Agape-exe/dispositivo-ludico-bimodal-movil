package com.taller.app.gpt

data class GptPrompt(
    val systemInstruction: String,
    val userMessage: String,
    val contextTag: String = "GENERIC"
)
