package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** which rows of a journey are swapped when another connection is picked. */
class RideSwapTest {
    private val t0 = OffsetDateTime.parse("2026-11-19T08:00:00+01:00")

    private fun ride(line: String, from: String, minutes: Long, live: Long = 0) = Leg(
        line, "subway", false, from, "X", t0.plusMinutes(minutes + live), t0.plusMinutes(minutes),
        t0.plusMinutes(minutes + 10 + live), t0.plusMinutes(minutes + 10), null, null, null, false,
    )

    private val first = ride("U8", "Example Square", 0)
    private val second = ride("S41", "Gesundbrunnen", 12)
    private val later = ride("S42", "Gesundbrunnen", 17)

    @Test
    fun anotherConnectionAtTheChangeIsASwap() {
        assertTrue(swapsRest(listOf(first, second), listOf(first, later)))
        // Another journey altogether is read from its top.
        assertFalse(swapsRest(listOf(first, second), listOf(ride("U6", "Other Street", 3), second)))
        assertFalse(swapsRest(null, listOf(first, second)))
        // The same journey with live times is no swap.
        assertFalse(swapsRest(listOf(first, second), listOf(first, ride("S41", "Gesundbrunnen", 12, live = 2))))
    }

    @Test
    fun aRowKeepsItsKeyThroughLiveTimesAndChangesItWithItsRide() {
        val before = listOf(first, second)
        assertEquals(legSwapKey(before, 1), legSwapKey(listOf(first, ride("S41", "Gesundbrunnen", 12, live = 4)), 1))
        assertNotEquals(legSwapKey(before, 1), legSwapKey(listOf(first, later), 1))
        assertEquals(legSwapKey(before, 0), legSwapKey(listOf(first, later), 0))
        assertEquals("-", legSwapKey(before, 5))
    }

    @Test
    fun foldsAndPickersOfRidesNotSwappedStay() {
        val before = listOf(first, second, ride("M10", "Warschauer Str.", 30))
        val after = listOf(first, later)
        assertEquals(listOf(0), keptAfterSwap(listOf(0, 1, 2), before, after))
    }
}
