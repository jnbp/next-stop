package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PositionFix
import io.github.jnbp.nextstop.data.PositionSample
import io.github.jnbp.nextstop.data.RideStage
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.WakeAlarmMode
import io.github.jnbp.nextstop.data.byRide
import io.github.jnbp.nextstop.data.changeTime
import io.github.jnbp.nextstop.data.matchPosition
import io.github.jnbp.nextstop.data.nextRideEta
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.rideState
import io.github.jnbp.nextstop.data.wakeTargets
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One ride state from position and live times, with the ride of 17.11. (replacement bus to a
 * change, then tram 66) on made-up places: the bus runs east from Example A to Example B
 * (~3.5 km), the 66 leaves from a stop 40 m north of Example B toward Example C. The bus has
 * a live arrival of 10:20 (+35) although the traveller is at the 66 stop from 10:02.
 */
class RideStateTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private val lat = 50.7
    private fun spot(north: Double, east: Double) = lat + north to 7.1 + east
    private fun stop(name: String, north: Double, east: Double) = Stop(name, name, lat + north, 7.1 + east, null)

    private val a = stop("Example A", 0.0, 0.0)
    private val b = stop("Example B", 0.0, 0.05)
    private val tramStop = stop("Example B (Tram)", 0.00036, 0.05)
    private val c = stop("Example C", 0.03, 0.05)

    private val replacementBus = Leg(
        "RE8X", "bus", false, "Example A", "Example B", at("09:30"), at("09:30"), at("10:20"), at("09:45"), null, null, null, false,
        realtime = true,
        path = listOf(spot(0.0, 0.0), spot(0.0, 0.025), spot(0.0, 0.05)),
        fromPlace = a, toPlace = b, tripId = "sev",
    )
    private val walk = Leg(
        null, "walking", true, "Example B", "Example B (Tram)", at("09:45"), at("09:45"), at("09:47"), at("09:47"), null, null, null, false,
        fromPlace = b, toPlace = tramStop,
    )

    private fun tram(departure: String, realtime: Boolean) = Leg(
        "66", "tram", false, "Example B (Tram)", "Example C", at(departure), at("10:00"), at("10:30"), at("10:22"), null, null, null, false,
        realtime = realtime,
        path = listOf(spot(0.00036, 0.05), spot(0.015, 0.05), spot(0.03, 0.05)),
        fromPlace = tramStop, toPlace = c, tripId = "66",
    )

    private val withLiveTram = Journey(listOf(replacementBus, walk, tram("10:08", realtime = true)))
    private val withoutLiveTram = Journey(listOf(replacementBus, walk, tram("10:00", realtime = false)))

    private fun sample(north: Double, east: Double, speed: Float?, millis: Long = 0) =
        PositionSample(lat + north, 7.1 + east, 10f, speed, millis)

    @Test
    fun atTheStopOfThe66TheTravellerWaitsAndTheBusIsOver() {
        val now = at("10:02")
        val fix = matchPosition(withLiveTram, sample(0.00036, 0.05, 0f), now, null)
        assertEquals(FixKind.AtBoarding, fix.kind)
        assertEquals(2, fix.legIndex)
        val state = rideState(withLiveTram, now, fix)
        assertEquals(RideStage.Waiting, state.stage)
        assertTrue(state.realtimeDiscarded)
        // The step: wait for the 66 at 10:08, never "get off the bus in 18 min".
        val step = tripStep(withLiveTram, now, fix)
        assertEquals(StepKind.Board, step.kind)
        assertTrue(step.waiting)
        assertSame(withLiveTram.legs[2], step.leg)
        assertEquals(at("10:08"), step.time)
        // Every part reads the same times: the bus is over, the 66 not yet ridden.
        val ridden = withLiveTram.plausible().byRide(state, now)
        assertFalse(ridden.legs[0].arrival.isAfter(now))
        assertTrue(ridden.legs[2].departure.isAfter(now))
        // The change is reachable ("Umstieg möglich", not "zu spät"); by the bus's live time it was not.
        assertTrue(changeTime(ridden.legs[0], ridden.legs[2], 2).possible)
        assertFalse(changeTime(withLiveTram.legs[0], withLiveTram.legs[2], 2).possible)
        // The id stays, so reminders and alarms keep their keys.
        assertEquals(withLiveTram.id, ridden.id)
    }

    @Test
    fun waitingPastTheDepartureWithoutLiveTimesIsNotRiding() {
        // The 66 has no live data and should have left at 10:00; at 10:12 the traveller still stands there.
        val first = matchPosition(withoutLiveTram, sample(0.00036, 0.05, 0f, 0), at("10:02"), null)
        assertEquals(FixKind.AtBoarding, first.kind)
        val now = at("10:12")
        val fix = matchPosition(withoutLiveTram, sample(0.00037, 0.05, 0.8f, 600_000), now, first)
        assertEquals(FixKind.AtBoarding, fix.kind)
        val state = rideState(withoutLiveTram, now, fix)
        assertEquals(RideStage.Waiting, state.stage)
        assertTrue(state.overdue)
        val step = tripStep(withoutLiveTram, now, fix)
        assertEquals(StepKind.Board, step.kind)
        assertTrue(step.waiting)
        // The timeline: the 66 is still to come, not under way ("Du fährst gerade" never by the clock alone).
        val ridden = withoutLiveTram.plausible().byRide(state, now)
        assertTrue(ridden.legs[2].departure.isAfter(now))
        // Walking about at the stop does not board it either.
        val strolling = matchPosition(withoutLiveTram, sample(0.0012, 0.05, 1.2f, 615_000), now.plusSeconds(15), fix)
        assertNotEquals(FixKind.OnRide, strolling.kind)
        // Once the position moves with the line, the traveller is on board.
        val moving = matchPosition(withoutLiveTram, sample(0.006, 0.05, 9f, 630_000), now.plusSeconds(30), fix)
        assertEquals(FixKind.OnRide, moving.kind)
        assertEquals(2, moving.legIndex)
        assertEquals(RideStage.OnBoard, rideState(withoutLiveTram, now.plusSeconds(30), moving).stage)
        assertEquals(StepKind.GetOff, tripStep(withoutLiveTram, now.plusSeconds(30), moving).kind)
    }

    @Test
    fun onTheBusAheadOfItsLiveTimeTheArrivalComesFromThePosition() {
        // 09:45 (Joel's screenshot): 2 km before the end at 15 m/s, the live time still says 10:20.
        val now = at("09:45")
        val fix = PositionFix(withLiveTram.id, 0, FixKind.OnRide, 1_500.0, 2_000.0, -1, 0L, speed = 15f)
        val state = rideState(withLiveTram, now, fix)
        assertEquals(RideStage.OnBoard, state.stage)
        assertTrue(state.estimated)
        val step = tripStep(withLiveTram, now, fix)
        assertEquals(StepKind.GetOff, step.kind)
        assertTrue(step.time.isBefore(now.plusMinutes(6)))
        // The change to the 66 is judged by that arrival.
        val ridden = withLiveTram.plausible().byRide(state, now)
        assertTrue(ridden.legs[0].arrival.isBefore(now.plusMinutes(6)))
        assertTrue(changeTime(ridden.legs[0], ridden.legs[2], 2).possible)
        // The wake-up alarm moves with it and keeps its key (rings once, confirmed once).
        val before = wakeTargets(withLiveTram, WakeAlarmMode.Always, 10).first()
        val after = wakeTargets(ridden, WakeAlarmMode.Always, 10, timesShown = true).first()
        assertEquals(before.key, after.key)
        assertTrue(after.at.isBefore(before.at))
    }

    @Test
    fun theBusInAJamStandsAndKeepsItsLiveTime() {
        val now = at("09:52")
        val fix = PositionFix(withLiveTram.id, 0, FixKind.OnRide, 2_000.0, 1_500.0, -1, 0L, speed = 0.3f)
        // One fix alone never stands: a stop at a light or a station is no news.
        val first = tripStep(withLiveTram, now, fix)
        assertEquals(StepKind.GetOff, first.kind)
        assertFalse(first.standing)
        // After 100 s below walking pace it stands (its stops have no positions: the longer wait).
        val bus = withLiveTram.plausible().legs[0]
        val eta = nextRideEta(nextRideEta(null, bus, fix, now), bus, fix.copy(atMillis = 100_000L), now.plusSeconds(100))
        val state = rideState(withLiveTram, now.plusSeconds(100), fix.copy(atMillis = 100_000L), eta)
        assertTrue(state.standing)
        assertEquals(0L, state.standingSinceMillis)
        val step = rideStep(withLiveTram, state, now.plusSeconds(100))!!
        assertEquals(StepKind.GetOff, step.kind)
        assertTrue(step.standing)
        assertEquals(at("10:20"), step.time)
    }

    @Test
    fun afterGettingOffAndWalkingOnTheNextStepIsThe66() {
        // At the end of the bus, then 250 m on foot away from every way.
        val end = matchPosition(withLiveTram, sample(0.0, 0.0498, 0.5f, 0), at("10:00"), null)
        assertEquals(FixKind.AtEnd, end.kind)
        assertEquals(StepKind.GetOff, tripStep(withLiveTram, at("10:00"), end).kind)
        val now = at("10:01")
        val walking = matchPosition(withLiveTram, sample(-0.002, 0.051, 1.3f, 60_000), now, end)
        assertEquals(FixKind.Alighted, walking.kind)
        val state = rideState(withLiveTram, now, walking)
        assertEquals(RideStage.Walking, state.stage)
        assertEquals(2, state.legIndex)
        val step = tripStep(withLiveTram, now, walking)
        assertEquals(StepKind.Board, step.kind)
        assertFalse(step.waiting)
        assertSame(withLiveTram.legs[2], step.leg)
    }

    @Test
    fun onFootPastTheDepartureTheNextRideIsStillToComeNotRidden() {
        // The 66 leaves at 10:00 by its times; at 10:01 the traveller is still on foot from the bus.
        val end = matchPosition(withoutLiveTram, sample(0.0, 0.0498, 0.5f, 0), at("10:00"), null)
        assertEquals(FixKind.AtEnd, end.kind)
        val now = at("10:01")
        val walking = matchPosition(withoutLiveTram, sample(-0.002, 0.051, 1.3f, 60_000), now, end)
        assertEquals(FixKind.Alighted, walking.kind)
        val state = rideState(withoutLiveTram, now, walking)
        assertEquals(RideStage.Walking, state.stage)
        assertTrue(state.overdue)
        val ridden = withoutLiveTram.plausible().byRide(state, now)
        // Not on the 66 by its times: it is expected shortly, marked as an estimate.
        assertTrue(ridden.legs[2].departure.isAfter(now))
        assertTrue(ridden.legs[2].arrivalEstimated)
        val step = tripStep(withoutLiveTram, now, walking)
        assertEquals(StepKind.Board, step.kind)
        assertTrue(step.estimated)
    }

    @Test
    fun withoutAPositionTheTimesDecideAsBefore() {
        val now = at("10:02")
        val state = rideState(withLiveTram, now, null)
        assertFalse(state.byPosition)
        assertSame(withLiveTram, withLiveTram.byRide(state, now))
        assertEquals(timedStep(withLiveTram, now), tripStep(withLiveTram, now, null))
    }
}
