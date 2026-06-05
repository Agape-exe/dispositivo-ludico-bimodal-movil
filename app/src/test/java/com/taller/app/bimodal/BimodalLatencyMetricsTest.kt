package com.taller.app.bimodal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BimodalLatencyMetricsTest {

    @Test
    fun sample_derivesLatenciesFromMilestones() {
        val sample = BimodalLatencySample(
            sttStartAtMs = 100,
            sttFinalAtMs = 1_000,
            semanticStartAtMs = 1_050,
            semanticEndAtMs = 1_300,
            logicalResponseAtMs = 1_400,
            feedbackStartAtMs = 1_450
        )

        assertEquals(400L, sample.totalResponseLatencyMs)
        assertEquals(450L, sample.responseToFeedbackLatencyMs)
        assertEquals(250L, sample.semanticLatencyMs)
        assertEquals(1_350L, sample.fullPipelineLatencyMs)
        assertTrue(sample.isValid)
    }

    @Test
    fun sample_withoutLogicalResponse_isInvalid() {
        val sample = BimodalLatencySample(sttStartAtMs = 100, sttFinalAtMs = 1_000)

        assertNull(sample.totalResponseLatencyMs)
        assertFalse(sample.isValid)
    }

    @Test
    fun sample_discardsNegativeDifferences() {
        // Un hito posterior anterior al previo no produce latencias negativas.
        val sample = BimodalLatencySample(
            sttFinalAtMs = 1_000,
            logicalResponseAtMs = 900
        )

        assertNull(sample.totalResponseLatencyMs)
        assertFalse(sample.isValid)
    }

    @Test
    fun computeStats_averagesOnlyValidSamples() {
        val valid1 = BimodalLatencySample(sttFinalAtMs = 0, logicalResponseAtMs = 400)
        val invalid = BimodalLatencySample(sttFinalAtMs = 0) // sin respuesta logica
        val valid2 = BimodalLatencySample(
            sttFinalAtMs = 0,
            logicalResponseAtMs = 800,
            feedbackStartAtMs = 900
        )

        val stats = computeLatencyStats(listOf(valid1, invalid, valid2))

        assertEquals(2, stats.validSamples)
        assertEquals(600L, stats.averageResponseLatencyMs) // (400 + 800) / 2
        assertEquals(800L, stats.lastResponseLatencyMs)
        assertEquals(900L, stats.lastFeedbackLatencyMs)
        assertTrue(stats.hasData)
    }

    @Test
    fun computeStats_averagesFeedbackAndExposesPipeline() {
        val sample1 = BimodalLatencySample(
            sttStartAtMs = 0,
            sttFinalAtMs = 100,
            logicalResponseAtMs = 500,
            feedbackStartAtMs = 600
        )
        val sample2 = BimodalLatencySample(
            sttStartAtMs = 1_000,
            sttFinalAtMs = 1_100,
            logicalResponseAtMs = 1_900,
            feedbackStartAtMs = 2_000
        )

        val stats = computeLatencyStats(listOf(sample1, sample2))

        // Hasta feedback: (500 + 900) / 2 = 700.
        assertEquals(700L, stats.averageFeedbackLatencyMs)
        // Pipeline completo de la ultima muestra: 2000 - 1000 = 1000.
        assertEquals(1_000L, stats.lastPipelineLatencyMs)
    }

    @Test
    fun latencyMsLabel_formatsExactValueWithMs() {
        assertEquals("1234 ms", latencyMsLabel(1_234L))
        assertEquals("0 ms", latencyMsLabel(0L))
        assertTrue(latencyMsLabel(450L).endsWith("ms"))
    }

    @Test
    fun latencyMsLabel_withoutValue_returnsDash() {
        assertEquals("—", latencyMsLabel(null))
    }

    @Test
    fun computeStats_withoutValidSamples_hasNoData() {
        val stats = computeLatencyStats(listOf(BimodalLatencySample(sttFinalAtMs = 0)))

        assertEquals(0, stats.validSamples)
        assertNull(stats.averageResponseLatencyMs)
        assertFalse(stats.hasData)
        assertFalse(stats.meetsTarget)
    }

    @Test
    fun stats_meetsTarget_onlyWhenAverageBelowThreshold() {
        val below = computeLatencyStats(
            listOf(BimodalLatencySample(sttFinalAtMs = 0, logicalResponseAtMs = 1_200))
        )
        assertTrue(below.meetsTarget)

        val above = computeLatencyStats(
            listOf(BimodalLatencySample(sttFinalAtMs = 0, logicalResponseAtMs = 1_800))
        )
        assertFalse(above.meetsTarget)
    }

    @Test
    fun tracker_buildsAndCommitsValidCycle() {
        var clock = 0L
        val tracker = BimodalLatencyTracker(now = { clock })

        clock = 100; tracker.beginCapture()
        clock = 1_000; tracker.markSttFinal()
        clock = 1_050; tracker.markSemanticStart()
        clock = 1_300; tracker.markSemanticEnd()
        clock = 1_400; tracker.markLogicalResponse()
        clock = 1_450; tracker.markFeedbackStart()

        val stats = tracker.commit()

        assertEquals(1, stats.validSamples)
        assertEquals(400L, stats.lastResponseLatencyMs)
        assertEquals(450L, stats.lastFeedbackLatencyMs)
        assertTrue(stats.meetsTarget)
    }

    @Test
    fun tracker_discardsCycleWithoutLogicalResponse() {
        var clock = 0L
        val tracker = BimodalLatencyTracker(now = { clock })

        clock = 100; tracker.beginCapture()
        clock = 1_000; tracker.markSttFinal()
        // Sin respuesta logica (p. ej. tiempo agotado): la muestra se descarta.

        val stats = tracker.commit()

        assertEquals(0, stats.validSamples)
        assertFalse(stats.hasData)
    }

    @Test
    fun tracker_marksEachMilestoneOnce_perCycle() {
        var clock = 0L
        val tracker = BimodalLatencyTracker(now = { clock })

        tracker.beginCapture()
        clock = 500; tracker.markSttFinal()
        // Una segunda marca (callback duplicado) no debe alterar la medicion.
        clock = 900; tracker.markSttFinal()
        clock = 1_000; tracker.markLogicalResponse()

        assertEquals(500L, tracker.current().sttFinalAtMs)
        val stats = tracker.commit()
        assertEquals(500L, stats.lastResponseLatencyMs) // 1000 - 500, no 1000 - 900
    }

    @Test
    fun tracker_averagesAcrossMultipleCommits() {
        var clock = 0L
        val tracker = BimodalLatencyTracker(now = { clock })

        // Ciclo 1: 400 ms.
        tracker.beginCapture()
        clock = 0; tracker.markSttFinal()
        clock = 400; tracker.markLogicalResponse()
        tracker.commit()

        // Ciclo 2: 600 ms.
        tracker.beginCapture()
        clock = 1_000; tracker.markSttFinal()
        clock = 1_600; tracker.markLogicalResponse()
        val stats = tracker.commit()

        assertEquals(2, stats.validSamples)
        assertEquals(500L, stats.averageResponseLatencyMs)
        assertEquals(600L, stats.lastResponseLatencyMs)
    }

    @Test
    fun tracker_reset_clearsSamples() {
        var clock = 0L
        val tracker = BimodalLatencyTracker(now = { clock })
        tracker.beginCapture()
        clock = 0; tracker.markSttFinal()
        clock = 300; tracker.markLogicalResponse()
        tracker.commit()

        tracker.reset()

        assertEquals(0, tracker.stats().validSamples)
    }
}
