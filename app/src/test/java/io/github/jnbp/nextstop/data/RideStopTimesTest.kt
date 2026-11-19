package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #226: the ends of a ride show the ride's own live times, a stop between its departure. */
class RideStopTimesTest {
    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T21:40:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)

    // The ride leaves 3 minutes late (21:51 -> 21:54) and arrives 2 minutes late.
    private fun leg(stops: List<Stopover>, realtime: Boolean = true) = Leg(
        "S5", "suburban", false, "Alexanderplatz", "Zoologischer Garten",
        at(14), at(11), at(30), at(28), "2", "3", "Westkreuz", false,
        realtime = realtime, stopovers = stops,
    )

    // The stops as the journey brought them, before the live departure was shared into the ride.
    private val stale = listOf(
        Stopover("Alexanderplatz", null, at(11), plannedDeparture = at(11)),
        Stopover("Friedrichstr.", at(17), at(18), delaySeconds = 180, plannedArrival = at(15), plannedDeparture = at(16)),
        Stopover("Zoologischer Garten", at(28), null, plannedArrival = at(28)),
    )

    @Test
    fun boardingRowShowsTheLiveDepartureOfTheRide() {
        val time = rideStopTime(leg(stale), stale, 0)!!
        assertEquals(at(14), time.actual)
        assertEquals(at(11), time.planned)
        assertTrue(time.realtime)
        assertEquals(3L, time.delayMinutes)
    }

    @Test
    fun alightingRowShowsTheLiveArrivalOfTheRide() {
        val time = rideStopTime(leg(stale), stale, 2)!!
        assertEquals(at(30), time.actual)
        assertEquals(at(28), time.planned)
        assertEquals(2L, time.delayMinutes)
    }

    @Test
    fun stopBetweenShowsItsDepartureWithThatDelay() {
        val time = rideStopTime(leg(stale), stale, 1)!!
        assertEquals(at(18), time.actual)
        assertEquals(at(16), time.planned)
        assertTrue(time.realtime)
        assertEquals(2L, time.delayMinutes)
    }

    @Test
    fun stopBetweenWithoutDepartureShowsItsArrival() {
        val time = stopTimeBetween(Stopover("Friedrichstr.", at(17), null, delaySeconds = 60, plannedArrival = at(16)))!!
        assertEquals(at(17), time.actual)
        assertEquals(at(16), time.planned)
        assertEquals(1L, time.delayMinutes)
    }

    @Test
    fun stopWithoutLiveDataIsTimetableTime() {
        val time = stopTimeBetween(Stopover("Friedrichstr.", at(15), at(16), plannedArrival = at(15), plannedDeparture = at(16)))!!
        assertFalse(time.realtime)
        assertEquals(0L, time.delayMinutes)
        assertNull(stopTimeBetween(Stopover("Friedrichstr.", null, null)))
    }

    @Test
    fun rideWithoutLiveDataKeepsItsEndsPlain() {
        val time = rideStopTime(leg(stale, realtime = false), stale, 0)!!
        assertFalse(time.realtime)
        assertNull(rideStopTime(leg(stale), stale, 3))
    }
}
