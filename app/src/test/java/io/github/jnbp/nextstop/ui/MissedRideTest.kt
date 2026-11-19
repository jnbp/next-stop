package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PositionFix
import io.github.jnbp.nextstop.data.RideStage
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.byRide
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.rideMissed
import io.github.jnbp.nextstop.data.rideState
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No endless "due now": a ride waited for (or walked to) past its departure is only "due now"
 * for a while. Without live times, 5 minutes after its planned departure (or twice the headway
 * of the line) it has probably gone; with live times, once they say it left. Then the step asks
 * "Missed it?", and nothing is planned anew without a tap. Made-up places: a bus from Example A
 * to Example B, then tram 66 from a stop next to Example B at 10:00 to Example C.
 */
class MissedRideTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")
    private val lat = 50.7
    private fun spot(north: Double, east: Double) = lat + north to 7.1 + east
    private fun stop(name: String, north: Double, east: Double) = Stop(name, name, lat + north, 7.1 + east, null)
    private val a = stop("Example A", 0.0, 0.0)
    private val b = stop("Example B", 0.0, 0.05)
    private val tramStop = stop("Example B (Tram)", 0.00036, 0.05)
    private val c = stop("Example C", 0.03, 0.05)

    private val bus = Leg(
        "100", "bus", false, a.name, b.name, at("09:30"), at("09:30"), at("09:45"), at("09:45"), null, null, null, false,
        path = listOf(spot(0.0, 0.0), spot(0.0, 0.05)), fromPlace = a, toPlace = b, tripId = "bus",
    )
    private val walk = Leg(
        null, "walking", true, b.name, tramStop.name, at("09:45"), at("09:45"), at("09:47"), at("09:47"), null, null, null, false,
        fromPlace = b, toPlace = tramStop,
    )

    private fun tram(departure: String, realtime: Boolean) = Leg(
        "66", "tram", false, tramStop.name, c.name, at(departure), at("10:00"), at("10:30"), at("10:22"), null, null, null, false,
        realtime = realtime,
        path = listOf(spot(0.00036, 0.05), spot(0.03, 0.05)),
        fromPlace = tramStop, toPlace = c, tripId = "66",
    )

    private val timetableOnly = Journey(listOf(bus, walk, tram("10:00", realtime = false)))
    private val live = Journey(listOf(bus, walk, tram("10:08", realtime = true)))

    private fun waiting(journey: Journey) = PositionFix(journey.id, 2, FixKind.AtBoarding, 5.0, 3_300.0, -1, 0L, speed = 0f)

    @Test
    fun withoutLiveTimesAWaitBecomesAQuestionAfterFiveMinutes() {
        val early = rideState(timetableOnly, at("10:04"), waiting(timetableOnly))
        assertEquals(RideStage.Waiting, early.stage)
        assertTrue(early.overdue)
        assertFalse(early.missed)
        assertFalse(tripStep(timetableOnly, at("10:04"), waiting(timetableOnly)).missed)
        val now = at("10:06")
        val late = rideState(timetableOnly, now, waiting(timetableOnly))
        assertTrue(late.missed)
        val step = tripStep(timetableOnly, now, waiting(timetableOnly))
        assertEquals(StepKind.Board, step.kind)
        assertTrue(step.missed)
        assertTrue(step.waiting)
        // Only a question: the legs and the id stay, nothing is planned anew by itself.
        val ridden = timetableOnly.plausible().byRide(late, now)
        assertEquals(timetableOnly.id, ridden.id)
        assertEquals(timetableOnly.legs.size, ridden.legs.size)
    }

    @Test
    fun aLineThatRunsRarelyIsWaitedForTwiceItsHeadway() {
        val leg = timetableOnly.legs[2]
        assertTrue(rideMissed(leg, at("10:06")))
        // Every 10 minutes: a late vehicle is still worth 20 minutes.
        assertFalse(rideMissed(leg, at("10:06"), headwaySeconds = 600))
        assertFalse(rideMissed(leg, at("10:19"), headwaySeconds = 600))
        assertTrue(rideMissed(leg, at("10:20"), headwaySeconds = 600))
        // A short headway never asks before five minutes.
        assertFalse(rideMissed(leg, at("10:04"), headwaySeconds = 60))
    }

    @Test
    fun withLiveTimesTheLiveDepartureDecides() {
        // Live at 10:08: at 10:06 it has not gone, though the timetable said 10:00.
        assertFalse(rideState(live, at("10:06"), waiting(live)).overdue)
        // Just past its live time it is still "due now" (a refresh may move it on) ...
        val due = rideState(live, at("10:09"), waiting(live))
        assertTrue(due.overdue)
        assertFalse(due.missed)
        // ... until the live times keep saying it left.
        assertTrue(rideState(live, at("10:10"), waiting(live)).missed)
    }

    @Test
    fun onFootToARideLongGoneTheStepAsks() {
        val walking = PositionFix(timetableOnly.id, 0, FixKind.Alighted, 3_500.0, 0.0, -1, 0L, speed = 1.3f)
        val soon = rideState(timetableOnly, at("10:01"), walking)
        assertEquals(RideStage.Walking, soon.stage)
        assertTrue(soon.overdue)
        assertFalse(soon.missed)
        val step = tripStep(timetableOnly, at("10:07"), walking)
        assertEquals(StepKind.Board, step.kind)
        assertTrue(step.missed)
        assertFalse(step.waiting)
    }
}
