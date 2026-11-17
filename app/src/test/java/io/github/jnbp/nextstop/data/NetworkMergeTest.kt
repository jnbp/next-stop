package io.github.jnbp.nextstop.data

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kept network map only grows: a build with fewer lines (at night) removes none. */
class NetworkMergeTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100L * day

    /** A straight way of [km] kilometres eastwards from a point in the city. */
    private fun way(name: String, km: Double): NetworkLine {
        val east = km * 1000 / (111_320.0 * Math.cos(Math.toRadians(52.5)))
        return NetworkLine(name, listOf(52.5 to 13.3, 52.5 to 13.3 + east / 2, 52.5 to 13.3 + east))
    }

    private fun kept(name: String, km: Double, seen: Long) = KeptNetworkLine(way(name, km), seen, seen)

    @Test
    fun aNightSampleWithFewerLinesRemovesNone() {
        val old = listOf("S1", "S2", "U1", "U2", "U8", "M10").map { kept(it, 10.0, now - day) }
        val night = listOf(way("S1", 10.0), way("U8", 10.0))
        val merged = mergeNetwork(old, night, now)
        assertEquals(listOf("M10", "S1", "S2", "U1", "U2", "U8"), merged.map { it.line.line })
        assertEquals(now, merged.first { it.line.line == "S1" }.seen)
        assertEquals(now - day, merged.first { it.line.line == "U1" }.seen)
    }

    @Test
    fun anEmptySampleKeepsEverything() {
        val old = listOf(kept("S7", 20.0, now - 2 * day), kept("U2", 10.0, now - day))
        assertEquals(old, mergeNetwork(old, emptyList(), now))
    }

    @Test
    fun newLinesAreAdded() {
        val merged = mergeNetwork(listOf(kept("U2", 10.0, now - day)), listOf(way("U5", 8.0)), now)
        assertEquals(listOf("U2", "U5"), merged.map { it.line.line })
    }

    @Test
    fun aShortTurningTripDoesNotCutALineShort() {
        val old = listOf(kept("U6", 20.0, now - day))
        val merged = mergeNetwork(old, listOf(way("U6", 8.0)), now).single()
        assertEquals(20.0, wayMetres(merged.line.points) / 1000, 0.1)
        assertEquals(now, merged.seen)
        assertEquals(now - day, merged.wayAt)
    }

    @Test
    fun aWayOfAboutTheSameLengthReplacesTheKeptOne() {
        val merged = mergeNetwork(listOf(kept("S5", 20.0, now - day)), listOf(way("S5", 19.0)), now).single()
        assertEquals(19.0, wayMetres(merged.line.points) / 1000, 0.1)
        assertEquals(now, merged.wayAt)
    }

    @Test
    fun aLineNotSeenForThirtyDaysDropsOut() {
        val old = listOf(kept("U2", 10.0, now - 31 * day), kept("U3", 10.0, now - 29 * day))
        assertEquals(listOf("S1", "U3"), mergeNetwork(old, listOf(way("S1", 5.0)), now).map { it.line.line })
    }

    @Test
    fun anOldWayIsReplacedEvenByAShorterOne() {
        val old = listOf(KeptNetworkLine(way("U1", 10.0), now - day, now - 31 * day))
        val merged = mergeNetwork(old, listOf(way("U1", 6.0)), now).single()
        assertEquals(6.0, wayMetres(merged.line.points) / 1000, 0.1)
    }

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 3, 10, hour, minute, 0, 0, berlin).toInstant().toEpochMilli()

    @Test
    fun byDayTheNetworkIsSampledAsItRunsNow() {
        assertNull(networkSampleTime(at(14, 0), berlin))
        assertNull(networkSampleTime(at(7, 0), berlin))
    }

    @Test
    fun atNightTheNetworkIsSampledAtTheNextMidday() {
        val small = networkSampleTime(at(1, 45), berlin)!!
        assertEquals(10, small.dayOfMonth)
        assertEquals(12, small.hour)
        val late = networkSampleTime(at(23, 30), berlin)!!
        assertEquals(11, late.dayOfMonth)
        assertEquals(12, late.hour)
        assertTrue(late.toInstant().toEpochMilli() > at(23, 30))
    }
}
