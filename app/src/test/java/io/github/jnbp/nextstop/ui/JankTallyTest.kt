package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The jank meter counts frames per screen without allocating per frame. */
class JankTallyTest {
    @Test
    fun countsFramesJankAndMax() {
        val tally = JankTally()
        repeat(98) { tally.record(8_000_000L, false) }
        tally.record(40_000_000L, true)
        tally.record(120_500_000L, true)
        assertEquals(100L, tally.frames)
        assertEquals(2.0, tally.jankPercent(), 1e-9)
        assertEquals(120_500_000L, tally.maxNanos)
    }

    @Test
    fun p95IsTheEdgeOfItsBucket() {
        val tally = JankTally()
        repeat(95) { tally.record(8_400_000L, false) }
        repeat(5) { tally.record(30_000_000L, true) }
        assertEquals(9, tally.p95Millis())
        tally.record(30_000_000L, true)
        assertEquals(31, tally.p95Millis())
    }

    @Test
    fun veryLongFramesLandInTheLastBucket() {
        val tally = JankTally()
        tally.record(2_000_000_000L, true)
        assertEquals(JANK_BUCKETS + 1, tally.p95Millis())
    }

    @Test
    fun resetEmptiesEverything() {
        val tally = JankTally()
        tally.record(8_000_000L, true)
        tally.reset()
        assertEquals(0L, tally.frames)
        assertEquals(0, tally.p95Millis())
        assertEquals(0.0, tally.jankPercent(), 0.0)
    }

    @Test
    fun textAndReport() {
        val tally = JankTally()
        repeat(9) { tally.record(8_000_000L, false) }
        tally.record(48_300_000L, true)
        val line = jankLine("Start", tally)
        assertEquals("10 Bilder · 10.0 % ruckelnd · p95 49 ms · max 48.3 ms", jankText(line, "Bilder", "ruckelnd"))
        val report = jankReport("Next Stop", listOf(line), "Bilder", "ruckelnd", "Karte: 2.0")
        assertTrue(report.startsWith("Next Stop\nStart: 10 Bilder"))
        assertTrue(report.endsWith("Karte: 2.0"))
    }

    @Test
    fun idleFpsNeedsASecondOfRest() {
        assertTrue(idleFps(3, 500).isNaN())
        assertEquals(2.0, idleFps(10, 5_000), 1e-9)
    }

    @Test
    fun lateFramesAreCountedApart() {
        val tally = JankTally()
        repeat(3) { tally.record(4_000_000L, false) }
        tally.record(6_000_000L, false, late = true)
        assertEquals(25.0, tally.latePercent(), 1e-9)
        assertEquals(0.0, tally.jankPercent(), 0.0)
        val line = jankLine("Start", tally)
        assertEquals("4 Bilder · 0.0 % ruckelnd · 25.0 % verspätet · p95 7 ms · max 6.0 ms", jankText(line, "Bilder", "ruckelnd", "verspätet"))
        tally.reset()
        assertEquals(0L, tally.late)
    }

    @Test
    fun mapFramesAtRestByCause() {
        val causes = MapRestCauses()
        repeat(10) { causes.record(MAP_CAUSE_PULSE) }
        repeat(4) { causes.record(MAP_CAUSE_VEHICLES or MAP_CAUSE_PULSE) }
        causes.record(0)
        assertEquals(10L, causes.pulse)
        assertEquals(4L, causes.vehicles)
        assertEquals(1L, causes.other)
        assertEquals("Fahrzeuge 2.0 · Puls 5.0 · sonst 0.5", mapCauseText(causes, 2_000L, "Fahrzeuge", "Puls", "sonst"))
        assertEquals("", mapCauseText(causes, 999L, "a", "b", "c"))
    }
}
