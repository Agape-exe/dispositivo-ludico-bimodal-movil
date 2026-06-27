package com.taller.app.recapture

enum class RecaptureState {
    IDLE,
    MONITORING,
    EVALUATING,
    PENDING,
    GENERATING_PHRASE,
    SPEAKING,
    WAITING_RETURN,
    CANCELLED,
    EXHAUSTED
}
