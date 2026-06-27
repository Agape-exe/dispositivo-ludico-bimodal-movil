package com.taller.app.attention

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AttentionRepository {
    private const val TAG = "AttentionState"

    private val stateMachine = AttentionStateMachine()
    private val _snapshot = MutableStateFlow(stateMachine.currentSnapshot)

    val snapshot: StateFlow<AttentionSnapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun onFaceDetected(timestampMs: Long = System.currentTimeMillis()): AttentionSnapshot =
        update(AttentionInput.FaceDetected(timestampMs))

    @Synchronized
    fun onFaceNotDetected(timestampMs: Long = System.currentTimeMillis()): AttentionSnapshot =
        update(AttentionInput.FaceNotDetected(timestampMs))

    @Synchronized
    fun reset(timestampMs: Long = System.currentTimeMillis()): AttentionSnapshot =
        update(AttentionInput.Reset(timestampMs))

    private fun update(input: AttentionInput): AttentionSnapshot {
        val previous = _snapshot.value
        val next = stateMachine.onInput(input)
        _snapshot.value = next
        if (previous.state != next.state) {
            logStateChange(previous, next, input.timestampMs)
        }
        return next
    }

    private fun logStateChange(
        previous: AttentionSnapshot,
        next: AttentionSnapshot,
        timestampMs: Long
    ) {
        val event = when (next.state) {
            AttentionState.FACE_PRESENT -> {
                if (previous.isTemporarilyLost || previous.isAttentionLost) {
                    "ATTENTION_RECOVERED"
                } else {
                    "ATTENTION_FACE_PRESENT"
                }
            }
            AttentionState.TEMPORARILY_LOST -> "ATTENTION_TEMPORARILY_LOST"
            AttentionState.ATTENTION_LOST -> "ATTENTION_LOST"
            else -> "ATTENTION_STATE_CHANGED"
        }
        Log.d(
            TAG,
            "event=$event previousState=${previous.state} newState=${next.state} " +
                "timestampMs=$timestampMs stableDurationMs=${next.stableDurationMs} " +
                "lostDurationMs=${next.lostDurationMs}"
        )
    }
}
