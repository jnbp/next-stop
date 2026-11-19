package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** #164: other ways from a change, compared with the rest of the plan. */
class OtherWaysTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private fun ride(line: String, departure: String, arrival: String) = Leg(
        line, "regional", false, "Example Change", "Example End", at(departure), at(departure), at(arrival), at(arrival),
        null, null, null, false, tripId = "$line-$departure",
    )

    // The plan from the change: S7, then a bus, arriving 16:05.
    private val plan = Journey(listOf(ride("S7", "15:27", "15:45"), ride("X10", "15:50", "16:05")))

    @Test
    fun earlierAndSimplerWaysComeFirstThePlanAndWorseOnesNot() {
        val direct = Journey(listOf(ride("RE1", "15:30", "16:01")))
        val later = Journey(listOf(ride("RE7", "15:40", "16:12")))
        val worse = Journey(listOf(ride("S5", "15:28", "15:50"), ride("M1", "15:55", "16:00"), ride("100", "16:02", "16:15")))
        val again = Journey(listOf(ride("S7", "15:27", "15:45"), ride("X10", "15:50", "16:05")))
        val ways = otherWays(plan, listOf(worse, later, again, direct, direct))
        assertEquals(listOf("RE1", "RE7"), ways.map { it.first.legs[0].line })
        assertEquals(WayCompare(arrivalMinutes = -4, changes = -1), ways[0].second)
        assertEquals(WayCompare(arrivalMinutes = 7, changes = -1), ways[1].second)
    }

    @Test
    fun laterWithTheSameChangesIsStillShown() {
        val same = Journey(listOf(ride("S5", "15:35", "15:52"), ride("X10", "16:00", "16:15")))
        assertEquals(WayCompare(10, 0), otherWays(plan, listOf(same)).single().second)
    }
}
