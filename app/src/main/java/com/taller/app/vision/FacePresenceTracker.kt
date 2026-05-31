package com.taller.app.vision

/**
 * Convierte el flujo de conteos de rostros que entrega [FaceAnalyzer] en
 * transiciones de presencia estables.
 *
 * ML Kit puede informar la presencia o ausencia de un rostro muchas veces por
 * segundo y con parpadeos puntuales. Esta clase aplica un pequeño debounce: solo
 * confirma un cambio de presencia cuando se observa el mismo resultado durante
 * [framesToConfirm] frames consecutivos, y emite una unica transicion en ese
 * momento. Mientras la presencia no cambia, devuelve [Transition.NONE].
 *
 * Es logica pura, independiente de Android y de la camara, para poder probarse
 * con pruebas unitarias. El llamador debe invocar [onFaceCount] siempre desde el
 * mismo hilo (el ejecutor del analizador), ya que mantiene estado interno.
 *
 * @param framesToConfirm cantidad de frames consecutivos necesarios para
 *        confirmar un cambio de presencia. Debe ser >= 1.
 */
class FacePresenceTracker(
    private val framesToConfirm: Int = 3
) {

    /** Resultado de procesar un frame. */
    enum class Transition {
        /** La presencia confirmada no cambio en este frame. */
        NONE,

        /** Se confirmo la aparicion de un rostro frente al dispositivo. */
        APPEARED,

        /** Se confirmo que ya no hay un rostro frente al dispositivo. */
        DISAPPEARED
    }

    init {
        require(framesToConfirm >= 1) { "framesToConfirm debe ser >= 1" }
    }

    /** Presencia confirmada actual (con debounce ya aplicado). */
    var isPresent: Boolean = false
        private set

    // Candidato a nuevo estado distinto del confirmado y cuantos frames lleva.
    private var candidatePresent: Boolean = false
    private var candidateStreak: Int = 0

    /**
     * Procesa el conteo de rostros de un frame y devuelve la transicion de
     * presencia confirmada, si la hubo.
     */
    fun onFaceCount(count: Int): Transition {
        val detected = count > 0

        // El frame coincide con el estado ya confirmado: no hay cambio en curso.
        if (detected == isPresent) {
            candidateStreak = 0
            return Transition.NONE
        }

        // El frame apunta a un cambio respecto del estado confirmado.
        if (detected == candidatePresent) {
            candidateStreak += 1
        } else {
            candidatePresent = detected
            candidateStreak = 1
        }

        if (candidateStreak >= framesToConfirm) {
            isPresent = detected
            candidateStreak = 0
            return if (detected) Transition.APPEARED else Transition.DISAPPEARED
        }

        return Transition.NONE
    }

    /** Reinicia el seguimiento a "sin presencia", descartando candidatos. */
    fun reset() {
        isPresent = false
        candidatePresent = false
        candidateStreak = 0
    }
}
