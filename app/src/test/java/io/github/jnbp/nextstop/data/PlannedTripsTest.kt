package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The schedule of a trip planned ahead: a reminder before leaving, then the start. Made-up places. */
class PlannedTripsTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private val walk = Leg(null, "walking", true, "Musterstraße 1", "Example A", at("08:05"), at("08:05"), at("08:12"), at("08:12"), null, null, null, false)
    private val ride = Leg("M10", "tram", false, "Example A", "Example B", at("08:12"), at("08:12"), at("08:30"), at("08:30"), null, null, null, false, tripId = "m10")
    private val journey = Journey(listOf(walk, ride))
    private val trip = PlannedTrip("p1", journey, "Home", "Office", plannedLeaveTime(journey), 10)

    @Test
    fun leavingIsTheStartOfTheWalk() {
        assertEquals(at("08:05"), trip.leaveAt)
        assertEquals(at("07:55"), trip.remindAt)
    }

    @Test
    fun firstTheReminderThenTheStart() {
        assertEquals(PlannedPhase.Remind to at("07:55"), plannedNext(trip, at("07:00")))
        assertEquals(PlannedPhase.Start to at("08:05"), plannedNext(trip.copy(reminded = true), at("07:56")))
        // A reminder whose time was missed while the device was off still comes before leaving.
        assertEquals(PlannedPhase.Remind to at("07:55"), plannedNext(trip, at("08:00")))
        assertEquals(PlannedPhase.Start to at("08:05"), plannedNext(trip, at("08:06")))
    }

    @Test
    fun aMissedStartIsDroppedAfterAWhile() {
        assertNull(plannedNext(trip, at("08:21")))
        assertEquals(at("07:55"), plannedWakeUp(listOf(trip), at("07:00")))
        assertNull(plannedWakeUp(listOf(trip), at("09:00")))
        // A start offered by a notification sets no more alarms.
        assertNull(plannedNext(trip.copy(reminded = true, offered = true), at("08:06")))
    }

    @Test
    fun keptAsJson() {
        val back = plannedTripsOf(plannedTripsJson(listOf(trip.copy(reminded = true))))
        assertEquals(1, back.size)
        assertEquals(trip.journey.id, back[0].journey.id)
        assertEquals(trip.leaveAt, back[0].leaveAt)
        assertEquals(true, back[0].reminded)
        assertEquals(emptyList<PlannedTrip>(), plannedTripsOf("not json"))
    }
}
