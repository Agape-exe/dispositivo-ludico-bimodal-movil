package com.taller.app.gpt

interface GptClient {
    suspend fun generate(prompt: GptPrompt): GptResult
    suspend fun generateStructured(input: SevenInputContract): StructuredGptResult
    fun isEnabled(): Boolean
    fun isConfigured(): Boolean
}
