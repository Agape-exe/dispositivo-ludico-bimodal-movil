package com.taller.app.vision

import com.taller.app.vision.FacePresenceTracker.Transition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacePresenceTrackerTest {

    @Test
    fun appearance_isConfirmedOnlyAfterEnoughConsecutiveFrames() {
        val tracker = FacePresenceTracker(3)

        // Frames 1 y 2 con rostro: aun no se confirma la aparicion.
        assertEquals(Transition.NONE, tracker.onFaceCount(1))
        assertEquals(Transition.NONE, tracker.onFaceCount(2))
        assertFalse(tracker.isPresent)

        // Tercer frame consecutivo: se confirma la aparicion.
        assertEquals(Transition.APPEARED, tracker.onFaceCount(1))
        assertTrue(tracker.isPresent)
    }

    @Test
    fun appearance_emitsTransitionOnlyOnce() {
        val tracker = FacePresenceTracker(2)

        assertEquals(Transition.NONE, tracker.onFaceCount(1))
        assertEquals(Transition.APPEARED, tracker.onFaceCount(1))

        // Mientras el rostro siga presente no se repite la transicion.
        assertEquals(Transition.NONE, tracker.onFaceCount(1))
        assertEquals(Transition.NONE, tracker.onFaceCount(2))
        assertTrue(tracker.isPresent)
    }

    @Test
    fun disappearance_isConfirmedAfterEnoughEmptyFrames() {
        val tracker = FacePresenceTracker(2)

        // Confirmar presencia primero.
        tracker.onFaceCount(1)
        assertEquals(Transition.APPEARED, tracker.onFaceCount(1))

        // Un solo frame sin rostro no basta para confirmar la ausencia.
        assertEquals(Transition.NONE, tracker.onFaceCount(0))
        assertTrue(tracker.isPresent)

        // Segundo frame consecutivo sin rostro: se confirma la ausencia.
        assertEquals(Transition.DISAPPEARED, tracker.onFaceCount(0))
        assertFalse(tracker.isPresent)
    }

    @Test
    fun briefFlicker_doesNotChangeConfirmedPresence() {
        val tracker = FacePresenceTracker(3)

        // Confirmar presencia.
        tracker.onFaceCount(1)
        tracker.onFaceCount(1)
        assertEquals(Transition.APPEARED, tracker.onFaceCount(1))

        // Parpadeo: un par de frames sin rostro y vuelve a aparecer antes de
        // alcanzar el umbral. No debe perderse la presencia confirmada.
        assertEquals(Transition.NONE, tracker.onFaceCount(0))
        assertEquals(Transition.NONE, tracker.onFaceCount(0))
        assertEquals(Transition.NONE, tracker.onFaceCount(1))
        assertTrue(tracker.isPresent)
    }

    @Test
    fun interruptedDisappearance_restartsTheStreak() {
        val tracker = FacePresenceTracker(3)

        tracker.onFaceCount(1)
        tracker.onFaceCount(1)
        assertEquals(Transition.APPEARED, tracker.onFaceCount(1))

        // Dos frames sin rostro (faltaria uno), luego rostro reinicia la racha.
        assertEquals(Transition.NONE, tracker.onFaceCount(0))
        assertEquals(Transition.NONE, tracker.onFaceCount(0))
        assertEquals(Transition.NONE, tracker.onFaceCount(1))

        // Ahora se necesitan de nuevo 3 frames consecutivos sin rostro.
        assertEquals(Transition.NONE, tracker.onFaceCount(0))
        assertEquals(Transition.NONE, tracker.onFaceCount(0))
        assertEquals(Transition.DISAPPEARED, tracker.onFaceCount(0))
        assertFalse(tracker.isPresent)
    }

    @Test
    fun withThresholdOne_appearanceAndDisappearanceAreImmediate() {
        val tracker = FacePresenceTracker(1)

        assertEquals(Transition.APPEARED, tracker.onFaceCount(2))
        assertTrue(tracker.isPresent)
        assertEquals(Transition.DISAPPEARED, tracker.onFaceCount(0))
        assertFalse(tracker.isPresent)
    }

    @Test
    fun reset_clearsPresenceAndPendingCandidate() {
        val tracker = FacePresenceTracker(2)

        tracker.onFaceCount(1)
        assertEquals(Transition.APPEARED, tracker.onFaceCount(1))
        assertTrue(tracker.isPresent)

        tracker.reset()
        assertFalse(tracker.isPresent)

        // Tras el reset se requiere de nuevo el umbral completo para confirmar.
        assertEquals(Transition.NONE, tracker.onFaceCount(1))
        assertEquals(Transition.APPEARED, tracker.onFaceCount(1))
    }
}
