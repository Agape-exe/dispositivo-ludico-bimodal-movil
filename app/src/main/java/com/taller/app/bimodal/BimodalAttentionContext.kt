package com.taller.app.bimodal

import com.taller.app.attention.AttentionSnapshot
import com.taller.app.attention.AttentionState

/**
 * Estado interno de atencion observado por el modo inteligente.
 *
 * No contiene imagenes, coordenadas, landmarks, audio, texto del nino ni datos
 * biometricos. Solo conserva la senal local efimera y contadores tecnicos para
 * que una politica futura de recaptura pueda decidir si actuar.
 */
data class BimodalAttentionContext(
    val snapshot: AttentionSnapshot? = null,
    val lastAttentionState: AttentionState = AttentionState.UNKNOWN,
    val attentionLostSinceMs: Long? = null,
    val temporarilyLostSinceMs: Long? = null,
    val attentionLostEventCount: Int = 0,
    val attentionRecoveredEventCount: Int = 0,
    val shouldConsiderRecapture: Boolean = false
) {
    val hasStableAttention: Boolean
        get() = lastAttentionState == AttentionState.ATTENTION_STABLE

    val isTemporarilyLost: Boolean
        get() = lastAttentionState == AttentionState.TEMPORARILY_LOST

    val isAttentionLost: Boolean
        get() = lastAttentionState == AttentionState.ATTENTION_LOST
}
