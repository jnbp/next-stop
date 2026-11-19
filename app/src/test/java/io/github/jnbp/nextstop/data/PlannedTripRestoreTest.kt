package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** "Undo" of a cancelled planned trip (see withRestored). Made-up places. */
class PlannedTripRestoreTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private fun trip(id: String, line: String, leaves: String, arrives: String): PlannedTrip {
        val ride = Leg(line, "tram", false, "Example A", "Example B", at(leaves), at(leaves), at(arrives), at(arrives), null, null, null, false, tripId = line + leaves)
        val journey = Journey(listOf(ride))
        return PlannedTrip(id, journey, "Example A", "Example B", plannedLeaveTime(journey), 10)
    }

    private val early = trip("p1", "M10", "08:00", "08:20")
    private val middle = trip("p2", "M13", "09:00", "09:20")
    private val late = trip("p3", "M17", "10:00", "10:20")

    @Test
    fun aCancelledTripComesBackInItsPlace() {
        assertEquals(listOf("p1", "p2", "p3"), withRestored(listOf(early, late), middle).map { it.id })
    }

    @Test
    fun aTripThereAlreadyIsNotTwice() {
        val trips = listOf(early, middle)
        assertSame(trips, withRestored(trips, middle))
        // Planned again meanwhile under another id: that plan stays.
        val again = middle.copy(id = "p9")
        assertSame(trips, withRestored(trips, again))
    }
}
