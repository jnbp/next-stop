package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Next stop: get off" only within the last minutes before the arrival: on a long last hop (a
 * night coach, an ICE) the reminder waits until then. The stops are made up.
 */
class GetOffSoonTest {
    private val base: OffsetDateTime = OffsetDateTime.parse("2026-11-14T00:00:00+01:00")
    private fun at(minutes: Int) = base.plusMinutes(minutes.toLong())

    private fun call(name: String, minutes: Int) = Stopover(
        name = name,
        arrival = at(minutes),
        departure = at(minutes),
        plannedArrival = at(minutes),
        plannedDeparture = at(minutes),
    )

    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)

    private fun ride(stops: List<Stopover>) = Leg(
        "FLX 123", "bus", false, "Stop A", "Stop C", at(0), at(0), at(191), at(191),
        null, null, null, false, realtime = false, fromPlace = stop("A"), toPlace = stop("C"), stopovers = stops, tripId = "flx",
    )

    @Test
    fun soonOnlyInTheLastTenMinutes() {
        assertFalse(getOffSoon(at(191), at(0)))
        assertFalse(getOffSoon(at(191), at(180)))
        assertTrue(getOffSoon(at(191), at(181)))
        assertTrue(getOffSoon(at(191), at(191)))
        assertTrue(getOffSoon(at(191), at(200)))
    }

    @Test
    fun longLastHopRemindsTenMinutesBefore() {
        // The stop before leaves at 00:13, the arrival is at 03:11.
        val journey = Journey(listOf(ride(listOf(call("A", 0), call("B", 13), call("C", 191)))))
        val early = alightEvents(journey).single { it.early }
        assertEquals(at(191).minusSeconds(GET_OFF_SOON_SECONDS), early.at)
    }

    @Test
    fun shortLastHopKeepsTheStopBefore() {
        val journey = Journey(listOf(ride(listOf(call("A", 0), call("B", 186), call("C", 191)))))
        val early = alightEvents(journey).single { it.early }
        assertEquals(at(186), early.at)
    }
}
