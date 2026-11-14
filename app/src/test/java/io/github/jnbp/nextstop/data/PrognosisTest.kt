package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Implausible prognoses: stretches where the provider lets a late vehicle catch up faster
 * than it can are found, and get an estimate that carries the delay on. The M41 case is the
 * one from a real screenshot; the other stops are made up.
 */
class PrognosisTest {
    private val base: OffsetDateTime = OffsetDateTime.parse("2026-11-13T16:00:00+01:00")
    private fun at(minutes: Int) = base.plusMinutes(minutes.toLong())

    /** A call at [planned] minutes after 16:00 with [delay] minutes of delay; null delay: no live data. */
    private fun call(name: String, planned: Int, delay: Int?) = Stopover(
        name = name,
        arrival = at(planned + (delay ?: 0)),
        departure = at(planned + (delay ?: 0)),
        delaySeconds = delay?.let { it * 60 },
        plannedArrival = at(planned),
        plannedDeparture = at(planned),
    )

    // The bus M41 at 16:37: up to Wildenbruchplatz +24, then five stops at 16:43.
    private val m41 = listOf(
        call("Hermannplatz", 15, 24),
        call("Pannierstr.", 16, 24),
        call("Reuterstr.", 18, 24),
        call("Wildenbruchplatz", 19, 24),
        call("Weigandufer", 21, 22),
        call("Treptower Str.", 22, 21),
        call("Elsensteg", 24, 19),
        call("S Sonnenallee", 26, 17),
    )

    private fun minutesOf(stop: Stopover) = Duration.between(base, stop.time).seconds / 60.0

    @Test
    fun m41StretchIsEstimated() {
        val shown = plausibleStops(m41)
        // Up to Wildenbruchplatz the prognosis holds and stays as it came.
        (0..3).forEach { assertFalse(shown[it].estimated); assertEquals(m41[it], shown[it]) }
        // From Weigandufer on the stops are estimated, each later than the one before.
        (4..7).forEach { assertTrue(shown[it].estimated) }
        shown.zipWithNext().forEach { (a, b) -> assertTrue(b.time!!.isAfter(a.time!!)) }
        // The delay of 24 minutes shrinks by at most 30 % of each planned run time.
        assertEquals(44.4, minutesOf(shown[4]), 0.01) // 16:21 + 24 − 0.6
        assertEquals(45.1, minutesOf(shown[5]), 0.01) // 16:22 + 23.4 − 0.3
        assertEquals(46.5, minutesOf(shown[6]), 0.01) // 16:24 + 23.1 − 0.6
        assertEquals(47.9, minutesOf(shown[7]), 0.01) // 16:26 + 22.5 − 0.6
        // Never earlier than the provider's own time, and the delay is the estimated one.
        shown.indices.forEach { assertFalse(shown[it].time!!.isBefore(m41[it].time)) }
        assertEquals((21.9 * 60).toInt(), shown[7].delaySeconds)
    }

    @Test
    fun plausiblePrognosisStaysAsItCame() {
        val stops = listOf(call("A", 0, 5), call("B", 2, 5), call("C", 4, 4), call("D", 9, 3), call("E", 12, 3))
        assertTrue(plausibleStops(stops) === stops)
    }

    @Test
    fun oneMinuteAtTheSameMinuteIsRounding() {
        // Planned one minute apart, the delay drops by one minute: both at 10 past. Rounding, not an error.
        val stops = listOf(call("A", 0, 5), call("B", 1, 4), call("C", 4, 4))
        assertTrue(plausibleStops(stops) === stops)
    }

    @Test
    fun minuteDropsAtEveryStopAddUp() {
        // At every stop one minute less, one minute apart: the bus stands still on paper.
        val stops = listOf(call("A", 0, 6), call("B", 1, 5), call("C", 2, 4), call("D", 3, 3), call("E", 4, 2))
        val shown = plausibleStops(stops)
        assertFalse(shown[1].estimated)
        assertTrue(shown.drop(2).all { it.estimated })
        // A and B stay at the same minute (rounding); from B on the times move on.
        shown.drop(1).zipWithNext().forEach { (a, b) -> assertTrue(b.time!!.isAfter(a.time!!)) }
    }

    @Test
    fun timeGoingBackwardsIsEstimated() {
        val stops = listOf(call("A", 0, 10), call("B", 3, 5), call("C", 6, 5))
        val shown = plausibleStops(stops)
        assertTrue(shown[1].estimated)
        assertTrue(shown[1].time!!.isAfter(shown[0].time))
        assertEquals(12.1, minutesOf(shown[1]), 0.01) // 16:03 + 10 − 0.9
    }

    @Test
    fun slowCatchUpIsTrusted() {
        // Two minutes made up over a planned run of ten: a vehicle can do that.
        val stops = listOf(call("A", 0, 8), call("B", 10, 6), call("C", 20, 4))
        assertTrue(plausibleStops(stops) === stops)
    }

    @Test
    fun stretchEndsWhereTheProviderIsPlausibleAgain() {
        // B drops 6 minutes over 2 planned; from C on the delay is back near what it was.
        val stops = listOf(call("A", 0, 8), call("B", 2, 2), call("C", 10, 7), call("D", 12, 7))
        val shown = plausibleStops(stops)
        assertTrue(shown[1].estimated)
        assertFalse(shown[2].estimated)
        assertFalse(shown[3].estimated)
        assertEquals(stops[2], shown[2])
    }

    @Test
    fun callsWithoutLiveDataAreKept() {
        val stops = listOf(call("A", 0, 8), call("B", 2, null), call("C", 4, null))
        assertTrue(plausibleStops(stops) === stops)
    }

    @Test
    fun plannedTimesFromTheDelayWhereTheProviderSendsNone() {
        // As some sources give it: live times and a delay, no planned times.
        val stops = m41.map { it.copy(plannedArrival = null, plannedDeparture = null) }
        val shown = plausibleStops(stops)
        assertTrue((4..7).all { shown[it].estimated })
        assertEquals(47.9, minutesOf(shown[7]), 0.01)
    }

    @Test
    fun estimateIsStable() {
        val once = plausibleStops(m41)
        assertTrue(plausibleStops(once) === once)
    }

    private fun stop(id: String) = Stop("90000000$id", "Stop $id", 52.0, 13.0, null)

    private fun ride(from: String, to: String, leaves: Int, arrives: Int, plannedArrives: Int, stops: List<Stopover>, trip: String) = Leg(
        "M41", "bus", false, "Stop $from", "Stop $to", at(leaves), at(leaves), at(arrives), at(plannedArrives),
        null, null, null, false, realtime = true, fromPlace = stop(from), toPlace = stop(to), stopovers = stops, tripId = trip,
    )

    private fun walk(from: String, to: String, leaves: Int, arrives: Int) = Leg(
        null, "walking", true, "Stop $from", "Stop $to", at(leaves), null, at(arrives), null,
        null, null, null, false, fromPlace = stop(from), toPlace = stop(to),
    )

    // The M41 from Hermannplatz to S Sonnenallee: the provider says 16:43 there.
    private val m41Ride = ride("1", "2", 39, 43, 26, m41, "m41")

    @Test
    fun legArrivalIsTheEstimate() {
        val shown = m41Ride.plausible()
        assertTrue(shown.arrivalEstimated)
        assertEquals(shown.stopovers.last().time, shown.arrival)
        assertEquals(at(26), shown.plannedArrival)
        assertTrue(m41Ride.plausible().plausible() == shown)
    }

    @Test
    fun journeyShiftsTheWalkAfterAndKeepsItsId() {
        val next = ride("3", "4", 49, 60, 60, emptyList(), "next")
        val journey = Journey(listOf(m41Ride, walk("2", "3", 43, 45), next))
        val shown = journey.plausible()
        assertEquals(journey.id, shown.id)
        assertEquals(shown.legs[0].arrival, shown.legs[1].departure)
        assertEquals(Duration.ofMinutes(2), Duration.between(shown.legs[1].departure, shown.legs[1].arrival))
        // The next ride keeps the provider's departure.
        assertTrue(shown.legs[2] === next)
    }

    @Test
    fun changeIsRatedByTheEstimateAndNeverSafe() {
        // By the provider: off at 16:43, 3 minutes' walk, the next bus at 16:52, 9 minutes: relaxed.
        // By the estimate: off at about 16:48, 4 minutes left for a walk of 3: not safe.
        val next = ride("3", "4", 52, 60, 60, emptyList(), "next")
        val journey = Journey(listOf(m41Ride, walk("2", "3", 43, 46), next))
        val change = journeyChangesOf(journey).single()
        assertTrue(change.uncertain)
        assertTrue(change.tightness.ordinal >= Tightness.Risky.ordinal)
        // Without the implausible stretch the same change is relaxed and certain.
        val plain = Journey(listOf(m41Ride.copy(stopovers = emptyList()), walk("2", "3", 43, 46), next))
        val plainChange = journeyChangesOf(plain).single()
        assertFalse(plainChange.uncertain)
        assertEquals(Tightness.Comfortable, plainChange.tightness)
    }

    @Test
    fun changeMissedByTheEstimateIsVeryRisky() {
        val next = ride("3", "4", 47, 60, 60, emptyList(), "next")
        val journey = Journey(listOf(m41Ride, walk("2", "3", 43, 44), next))
        assertEquals(Tightness.VeryRisky, journeyChangesOf(journey).single().tightness)
    }

    @Test
    fun alightRemindersFollowTheEstimate() {
        val journey = Journey(listOf(m41Ride))
        val events = alightEvents(journey)
        val shown = m41Ride.plausible()
        val approach = events.single { !it.early }
        // A minute before the estimated arrival (about 16:48), not the provider's 16:43.
        assertEquals(shown.arrival.minusSeconds(ALIGHT_APPROACH_SECONDS), approach.at)
        assertTrue(approach.estimated)
        // Every reminder of this ride rests on the estimate.
        assertTrue(events.all { it.estimated })
        // The keys are those of the provider's times: a reminder is never sent twice.
        assertTrue(approach.key.endsWith("|" + at(26)))
        // Without an estimate nothing is said to be one.
        val calm = Journey(listOf(m41Ride.copy(stopovers = m41.take(4))))
        assertFalse(alightEvents(calm).any { it.estimated })
    }

    @Test
    fun courseIsEstimatedLikeAnyList() {
        val course = TripCourse("m41", "M41", "bus", "Hauptbahnhof", m41, emptyList())
        assertTrue(course.plausible().stops.drop(4).all { it.estimated })
        val calm = course.copy(stops = m41.take(4))
        assertTrue(calm.plausible() === calm)
    }
}
