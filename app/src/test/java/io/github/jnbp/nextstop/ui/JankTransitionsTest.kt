package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The jank meter's changes of page: first draw, longest frame, frames drawn, backdrop pictures. */
class JankTransitionsTest {
    private val ms = 1_000_000L

    @Test
    fun aSwallowedAnimationShowsAsOneLongFrame() {
        val run = TransitionRun("Plan", "Connections", startNanos = 1_000 * ms)
        // The frame before the change ended before it: not counted.
        run.frame(frameStartNanos = 990 * ms, workNanos = 5 * ms)
        // The frame that composes the new page began just before the change was noted.
        run.frame(frameStartNanos = 998 * ms, workNanos = 950 * ms)
        run.firstDrawNanos = 1_940 * ms
        run.frame(frameStartNanos = 1_950 * ms, workNanos = 4 * ms)
        run.backdrops = 1
        val stats = TransitionStats()
        stats.add(run, endNanos = 1_960 * ms)
        assertEquals(2, run.frames)
        assertEquals(
            "Plan→Connections: 1× · erstes Bild Ø 940 ms, max 940 ms · längstes Bild 950.0 ms · Bilder min 2, Ø 2.0 · Glas-Bild 1",
            transitionText(transitionName("Plan", "Connections"), stats, "erstes Bild", "längstes Bild", "Bilder", "Glas-Bild"),
        )
    }

    @Test
    fun severalRunsAreAveraged() {
        val stats = TransitionStats()
        for (first in listOf(100L, 300L)) {
            val run = TransitionRun("Connections", "Connections", 0L)
            repeat((first / 10).toInt()) { run.frame(it * 10 * ms, 8 * ms) }
            run.firstDrawNanos = first * ms
            stats.add(run, endNanos = 500 * ms)
        }
        assertEquals(2, stats.count)
        assertEquals(200.0, stats.firstSumMillis / stats.count, 1e-9)
        assertEquals(10, stats.framesMin)
        assertEquals(8.0, stats.longestMaxMillis, 1e-9)
    }

    @Test
    fun aPageNeverDrawnCountsItsEnd() {
        val stats = TransitionStats()
        stats.add(TransitionRun("A", "B", 0L), endNanos = 250 * ms)
        assertEquals(250.0, stats.firstMaxMillis, 1e-9)
        assertEquals(0, stats.framesMin)
    }
}
