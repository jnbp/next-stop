package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The parts of a connection in its peek card (see peekParts). Made-up stops. */
class PeekPartsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T08:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    private fun leg(walking: Boolean, leaves: Long, arrives: Long, line: String = "X1") = Leg(
        if (walking) null else line, if (walking) "walking" else "bus", walking, "A", "B",
        at(leaves), at(leaves), at(arrives), at(arrives), null, null, null, false,
    )

    @Test
    fun ridesAndWalksInTheirOrder() {
        val journey = Journey(listOf(leg(true, 0, 6), leg(false, 6, 20, "X1"), leg(true, 20, 23), leg(false, 25, 40, "X2")))
        val parts = peekParts(journey)
        assertEquals(4, parts.size)
        assertNull(parts[0].ride)
        assertEquals(6L, parts[0].walkMinutes)
        assertEquals("X1", parts[1].ride?.line)
        assertEquals(3L, parts[2].walkMinutes)
        assertEquals("X2", parts[3].ride?.line)
    }

    @Test
    fun walksInARowAreOne() {
        val journey = Journey(listOf(leg(false, 0, 10), leg(true, 10, 12), leg(true, 12, 17)))
        val parts = peekParts(journey)
        assertEquals(2, parts.size)
        assertEquals(7L, parts[1].walkMinutes)
    }

    @Test
    fun walksOfNoMinuteAreLeftOut() {
        val journey = Journey(listOf(leg(false, 0, 10), leg(true, 10, 10), leg(false, 12, 20)))
        assertEquals(2, peekParts(journey).size)
    }

    @Test
    fun byBikeIsKept() {
        val bike = leg(true, 0, 9).copy(cycling = true)
        val parts = peekParts(Journey(listOf(bike, leg(false, 9, 20))))
        assertEquals(true, parts[0].cycling)
        assertEquals(9L, parts[0].walkMinutes)
    }
}
