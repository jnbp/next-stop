package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The connections offered as a choice at a change: by arrival, the chosen one among them. */
class OptionOrderTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T17:50:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun leg(line: String?, leaves: Long, arrives: Long) = Leg(
        line, if (line == null) "walking" else "subway", line == null, "Alexanderplatz", "Zoologischer Garten",
        at(leaves), at(leaves), at(arrives), at(arrives), null, null, null, false,
    )

    @Test
    fun theChosenOneSortsByItsArrivalLikeTheOthers() {
        // As offered: U2 arriving 18:08, U1 leaving earlier but arriving 18:17, and the chosen
        // U2 that leaves last but arrives 18:12.
        val early = Journey(listOf(leg("U2", 2, 18)))
        val slow = Journey(listOf(leg("U1", 4, 27)))
        val chosen = Journey(listOf(leg("U2", 6, 22)))
        val sorted = listOf(early, slow, chosen).sortedWith(optionOrder)
        assertEquals(listOf(early, chosen, slow), sorted)
    }

    @Test
    fun theSameArrivalGoesByDepartureThenRidesThenWalking() {
        val later = Journey(listOf(leg("U5", 5, 20)))
        val earlier = Journey(listOf(leg("U8", 3, 20)))
        val twoRides = Journey(listOf(leg("U2", 3, 10), leg("U6", 12, 20)))
        val walking = Journey(listOf(leg("U8", 3, 15), leg(null, 15, 20)))
        val sorted = listOf(later, twoRides, walking, earlier).sortedWith(optionOrder)
        assertEquals(listOf(earlier, walking, twoRides, later), sorted)
    }
}
