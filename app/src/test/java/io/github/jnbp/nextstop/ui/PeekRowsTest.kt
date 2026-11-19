package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rows of the vertical list of a peek card (see peekRows). Made-up stops. */
class PeekRowsTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T08:00:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    private fun leg(walking: Boolean, leaves: Long, arrives: Long, line: String = "X1") = Leg(
        if (walking) null else line, if (walking) "walking" else "bus", walking, "A", "B",
        at(leaves), at(leaves), at(arrives), at(arrives), null, null, null, false,
    )

    @Test
    fun ridesWithTheChangesBetweenThem() {
        val journey = Journey(listOf(leg(true, 0, 6), leg(false, 6, 20, "X1"), leg(true, 20, 23), leg(false, 26, 40, "X2"), leg(true, 40, 44)))
        val rows = peekRows(journey)
        assertEquals(5, rows.size)
        assertEquals(6L, (rows[0] as PeekRow.Walk).walkMinutes)
        assertEquals(14L, (rows[1] as PeekRow.Ride).minutes)
        val change = rows[2] as PeekRow.Change
        assertEquals(6L, change.minutes)
        assertEquals(3L, change.walkMinutes)
        assertEquals("X2", (rows[3] as PeekRow.Ride).leg.line)
        assertEquals(4L, (rows[4] as PeekRow.Walk).walkMinutes)
    }

    @Test
    fun aChangeOnTheSamePlatformStillShows() {
        val rows = peekRows(Journey(listOf(leg(false, 0, 10, "X1"), leg(false, 13, 20, "X2"))))
        assertEquals(3, rows.size)
        val change = rows[1] as PeekRow.Change
        assertEquals(3L, change.minutes)
        assertEquals(0L, change.walkMinutes)
    }

    @Test
    fun stayingOnBoardIsNoChange() {
        val rows = peekRows(Journey(listOf(leg(false, 0, 10, "X1"), leg(false, 10, 20, "X2").copy(stayOnBoard = true))))
        assertTrue(rows.all { it is PeekRow.Ride })
    }

    @Test
    fun onFootOnlyIsOneWalk() {
        val rows = peekRows(Journey(listOf(leg(true, 0, 12).copy(cycling = true))))
        val walk = rows.single() as PeekRow.Walk
        assertEquals(12L, walk.walkMinutes)
        assertTrue(walk.cycling)
    }
}
