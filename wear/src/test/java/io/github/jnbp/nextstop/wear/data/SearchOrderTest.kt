package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The watch lists the journeys of a search in the order of the phone (SearchOrderTest there). */
class SearchOrderTest {
    // 17:50 on a whole minute, as epoch milliseconds.
    private val start = 1_763_035_800_000L
    private fun at(minutes: Long) = start + minutes * 60_000L

    private fun leg(line: String?, leaves: Long, arrives: Long) = Leg(
        line, if (line == null) "walking" else "subway", line == null, "Alexanderplatz", "Zoologischer Garten",
        at(leaves), at(arrives), 0, 0, null, null, false,
    )

    @Test
    fun byTheTimeToSetOffThenArrivalRidesAndWalking() {
        val walkFirst = Journey(listOf(leg(null, 0, 8), leg("U2", 8, 30)), null)
        val slow = Journey(listOf(leg("U1", 3, 30)), null)
        val fast = Journey(listOf(leg("U8", 3, 18)), null)
        val twoRides = Journey(listOf(leg("U2", 3, 10), leg("U6", 12, 20)), null)
        val oneRide = Journey(listOf(leg("U8", 3, 20)), null)
        val walking = Journey(listOf(leg("U5", 3, 15), leg(null, 15, 20)), null)
        val sorted = listOf(slow, twoRides, walking, oneRide, fast, walkFirst).sortedWith(searchOrder)
        assertEquals(listOf(walkFirst, fast, oneRide, walking, twoRides, slow), sorted)
    }
}
