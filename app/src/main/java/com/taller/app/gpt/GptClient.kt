package com.taller.app.gpt

interface GptClient {
    suspend fun generate(prompt: GptPrompt): GptResult
    fun isEnabled(): Boolean
    fun isConfigured(): Boolean
}
