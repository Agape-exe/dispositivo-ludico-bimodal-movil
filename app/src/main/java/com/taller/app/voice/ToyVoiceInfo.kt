package com.taller.app.voice

data class ToyVoiceInfo(
    val name: String,
    val locale: String,
    val isNetworkRequired: Boolean,
    val quality: Int
)
