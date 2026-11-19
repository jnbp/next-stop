package io.github.jnbp.nextstop.ui

import java.time.OffsetDateTime
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Test

/** The list of connections shows the ones that left once "Frühere Verbindungen" was tapped (#150). Made-up stops only. */
class ListEarlierTest {
    private val now: OffsetDateTime = OffsetDateTime.parse("2026-01-01T10:00:00+01:00")
    private val a = Stop("900000001", "Stop 1", 52.0, 13.0, null)
    private val b = Stop("900000002", "Stop 2", 52.0, 13.0, null)
    private fun journey(leaves: Long) = Journey(
        listOf(
            Leg(
                "U1", "subway", false, a.name, b.name, now.plusMinutes(leaves), now.plusMinutes(leaves),
                now.plusMinutes(leaves + 10), now.plusMinutes(leaves + 10), null, null, null, false,
                fromPlace = a, toPlace = b, tripId = "t$leaves",
            ),
        ),
    )

    private val left = journey(-10)
    private val leftToo = journey(-5)
    private val next = journey(3)
    private val all = listOf(left, leftToo, next)

    @Test
    fun startsWithTheNextDeparture() {
        assertEquals(listOf(next), listedJourneys(all, timed = false, earlierShown = false, now = now, currentId = null))
    }

    @Test
    fun thePickStaysAfterItLeft() {
        assertEquals(listOf(leftToo, next), listedJourneys(all, timed = false, earlierShown = false, now = now, currentId = leftToo.id))
    }

    @Test
    fun earlierOnesOnceAskedForOrAtAPickedTime() {
        assertEquals(all, listedJourneys(all, timed = false, earlierShown = true, now = now, currentId = null))
        assertEquals(all, listedJourneys(all, timed = true, earlierShown = false, now = now, currentId = null))
    }

    @Test
    fun theFirstRowStaysInPlace() {
        assertEquals(240, earlierScrollShift(anchorBefore = 8, anchorAfter = 248))
        assertEquals(0, earlierScrollShift(anchorBefore = 200, anchorAfter = 8))
    }
}
