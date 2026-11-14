package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartGlanceTest {
    private val now = 1_000_000_000L

    private fun departure(line: String, minutes: Double?, cancelled: Boolean = false) = Departure(
        line = line,
        product = "subway",
        direction = "Alexanderplatz",
        time = minutes?.let { now + (it * 60_000).toLong() },
        delayMinutes = null,
        platform = null,
        cancelled = cancelled,
    )

    @Test
    fun theCardShowsTheNextCatchableRunsSoonestFirst() {
        val board = listOf(
            departure("U2", 6.0),
            departure("U5", 2.0),
            departure("U8", 1.0, cancelled = true),
            departure("M4", null),
            departure("S5", -0.2),
            departure("S7", -2.0),
            departure("U1", 9.0),
        )
        // Cancelled runs, runs without a time and runs gone more than half a minute ago drop out.
        assertEquals(listOf("S5", "U5", "U2"), upcomingDepartures(board, now, 3).map { it.line })
        assertEquals(listOf("S5", "U5"), upcomingDepartures(board, now, 2).map { it.line })
        assertEquals(emptyList<Departure>(), upcomingDepartures(board, now, 0))
    }

    @Test
    fun aFavouriteStopShowsItsNextRun() {
        assertEquals("U5", nextCatchable(listOf(departure("U2", 4.0), departure("U5", 1.0)), now)?.line)
        assertNull(nextCatchable(listOf(departure("U2", 4.0, cancelled = true)), now))
        assertNull(nextCatchable(null, now))
    }

    @Test
    fun theCountdownRedrawsWhenItsMinutesChange() {
        // 5 min 10 s away shows "5 min" until 4 min 30 s are left: 40 s from now.
        val time = now + 5 * 60_000 + 10_000
        assertEquals(5L, minutesUntil(time, now))
        val wait = millisToNextMinuteChange(listOf(time), now)
        assertEquals(40_001L, wait)
        assertEquals(5L, minutesUntil(time, now + wait - 1))
        assertEquals(4L, minutesUntil(time, now + wait))
        // The soonest change of several wins; one at "now" changes no more.
        assertEquals(10_001L, millisToNextMinuteChange(listOf(time, now + 2 * 60_000 - 20_000), now))
        assertEquals(60_000L, millisToNextMinuteChange(listOf(now + 10_000), now))
        assertEquals(60_000L, millisToNextMinuteChange(emptyList(), now))
    }

    @Test
    fun aRideMovesStopByStop() {
        // Four stops, two minutes apart: boarding at now, getting off after six minutes.
        val stops = listOf(now, now + 120_000, now + 240_000, now + 360_000)
        assertEquals(0f, rideProgress(stops, now - 30_000)!!, 0.001f)
        assertEquals(0f, rideProgress(stops, now)!!, 0.001f)
        // Half way between the first and the second stop: half of one of three steps.
        assertEquals(1f / 6, rideProgress(stops, now + 60_000)!!, 0.001f)
        assertEquals(2f / 3, rideProgress(stops, now + 240_000)!!, 0.001f)
        assertEquals(1f, rideProgress(stops, now + 400_000)!!, 0.001f)
        // A stop without a time is passed by the next with one.
        assertEquals(1f / 3, rideProgress(listOf(now, 0L, now + 240_000, now + 360_000), now + 120_000)!!, 0.001f)
        assertNull(rideProgress(listOf(now), now))
        assertNull(rideProgress(listOf(0L, 0L), now))
    }

    @Test
    fun theStopsLeftComeFromThePhoneElseFromTheTimes() {
        val stops = listOf(now, now + 120_000, now + 240_000, now + 360_000)
        assertEquals(5, ridingStopsLeft(5, stops, now))
        assertEquals(3, ridingStopsLeft(-1, stops, now + 10_000))
        assertEquals(1, ridingStopsLeft(-1, stops, now + 250_000))
        // Past the last stop it still says one: the trip moves on when the phone or the watch says so.
        assertEquals(1, ridingStopsLeft(0, stops, now + 400_000))
        assertEquals(-1, ridingStopsLeft(-1, emptyList(), now))
    }

    @Test
    fun theUsualLinesAreTheRidesOfTheConnection() {
        fun leg(line: String?, walking: Boolean = false) =
            Leg(line, if (walking) "" else "suburban", walking, "Hauptbahnhof", "Friedrichstraße", now, now + 60_000, 0, 0, null, null, false)
        val journey = Journey(
            listOf(leg(null, walking = true), leg("S5"), leg(null, walking = true), leg("U6"), leg("S5"), leg("M1"), leg("100")),
            refreshToken = null,
        )
        assertEquals(listOf("S5", "U6", "M1"), usualLinesOf(journey).map { it.name })
        assertEquals(emptyList<StopLine>(), usualLinesOf(Journey(listOf(leg(null, walking = true)), null)))
    }
}
