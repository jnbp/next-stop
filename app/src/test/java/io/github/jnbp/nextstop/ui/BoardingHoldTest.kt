package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PositionSample
import io.github.jnbp.nextstop.data.RideStage
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.boardingHolds
import io.github.jnbp.nextstop.data.heldProgress
import io.github.jnbp.nextstop.data.matchPosition
import io.github.jnbp.nextstop.data.rideState
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #184 on made-up places: the traveller changed to an S-Bahn at Example B and waits on its
 * platform. The S-Bahn has no live position and no live times; it is due at 16:16. At 16:16 its
 * marker left the stop by the clock while the train had not come, and the hero said
 * "0′ Warte auf S1 · kommt ca. 16:16".
 */
class BoardingHoldTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T$clock+01:00")

    private val lat = 50.7
    private fun spot(north: Double, east: Double) = lat + north to 7.1 + east
    private fun stop(name: String, north: Double, east: Double) = Stop(name, name, lat + north, 7.1 + east, null)

    private val a = stop("Example A", 0.0, 0.0)
    private val b = stop("Example B", 0.0, 0.05)
    private val c = stop("Example C", 0.04, 0.05)

    private val first = Leg(
        "S7", "suburban", false, "Example A", "Example B", at("15:50:00"), at("15:50:00"), at("16:05:00"), at("16:05:00"), null, null, null, false,
        path = listOf(spot(0.0, 0.0), spot(0.0, 0.025), spot(0.0, 0.05)),
        fromPlace = a, toPlace = b, tripId = "s7-run",
    )

    private fun sBahn(departure: String) = Leg(
        "S1", "suburban", false, "Example B", "Example C", at(departure), at("16:16:00"), at("16:28:00"), at("16:28:00"), null, null, null, false,
        realtime = false,
        path = listOf(spot(0.0, 0.05), spot(0.02, 0.05), spot(0.04, 0.05)),
        fromPlace = b, toPlace = c, tripId = "s1-run",
    )

    private val journey = Journey(listOf(first, sBahn("16:16:00")))

    // The whole run of the S1: it comes from upstream and goes on past the end of the ride.
    private val run = listOf(
        Stopover("Example Upstream", at("16:10:00"), at("16:10:00")),
        Stopover("Example B", at("16:16:00"), at("16:16:00")),
        Stopover("Example C", at("16:28:00"), at("16:28:00")),
        Stopover("Example Beyond", at("16:33:00"), at("16:33:00")),
    )

    private fun sample(north: Double, speed: Float, millis: Long) = PositionSample(lat + north, 7.1 + 0.05, 10f, speed, millis)

    // Off the S7 at 16:06 (at its end), then standing on the platform of the S1.
    private val alighted = matchPosition(journey, sample(0.0, 0f, 0), at("16:06:00"), null)
    private val waiting = matchPosition(journey, sample(0.0, 0f, 240_000), at("16:10:00"), alighted)

    @Test
    fun theTrainStaysAtTheStopWhileTheTravellerWaitsForIt() {
        assertEquals(FixKind.AtEnd, alighted.kind)
        assertEquals(FixKind.AtBoarding, waiting.kind)
        assertEquals(1, waiting.legIndex)
        // 16:16:20: due, not come. Waiting, past its time, not taken as missed.
        val now = at("16:16:20")
        val fix = matchPosition(journey, sample(0.00002, 0.3f, 380_000), now, waiting)
        assertEquals(FixKind.AtBoarding, fix.kind)
        val state = rideState(journey, now, fix)
        assertEquals(RideStage.Waiting, state.stage)
        assertTrue(state.overdue)
        assertFalse(state.missed)
        // Its marker is held at the boarding call of its run (index 1), not where the clock puts it.
        val holds = boardingHolds(journey, state) { if (it == "s1-run") run else null }
        assertEquals(mapOf("s1-run" to 1), holds)
        // By the clock at 16:17 it would be a twelfth of the way to Example C.
        assertEquals(1f, heldProgress(1f + 1f / 12f, holds.getValue("s1-run")), 0f)
        // Before it is due it comes on toward the stop as before.
        assertEquals(0.5f, heldProgress(0.5f, 1), 0f)
        // The hero: "S1 kommt jeden Moment", not "0′".
        val step = tripStep(journey, now, fix)
        assertTrue(step.waiting)
        assertEquals(WaitPhase.AnyMoment, step.wait)
    }

    @Test
    fun twoMinutesLateWithoutNewsItIsOverdueAndTheCountGrows() {
        val now = at("16:18:30")
        val fix = matchPosition(journey, sample(0.00002, 0.2f, 510_000), now, waiting)
        val step = tripStep(journey, now, fix)
        assertEquals(WaitPhase.Overdue, step.wait)
        assertEquals(2L, step.overdueMinutes)
        val later = tripStep(journey, at("16:20:10"), matchPosition(journey, sample(0.00002, 0.2f, 610_000), at("16:20:10"), fix))
        assertEquals(WaitPhase.Overdue, later.wait)
        assertEquals(4L, later.overdueMinutes)
        // Still held: nothing says it has gone.
        val state = rideState(journey, now, fix)
        assertEquals(1, boardingHolds(journey, state) { run }["s1-run"])
    }

    @Test
    fun movingWithTheTrainLetsItGo() {
        val now = at("16:19:00")
        val stand = matchPosition(journey, sample(0.00002, 0.2f, 540_000), now, waiting)
        val moving = matchPosition(journey, sample(0.006, 12f, 570_000), now.plusSeconds(30), stand)
        assertEquals(FixKind.OnRide, moving.kind)
        val state = rideState(journey, now.plusSeconds(30), moving)
        assertEquals(RideStage.OnBoard, state.stage)
        assertTrue(boardingHolds(journey, state) { run }.isEmpty())
    }

    @Test
    fun aRideTakenAsMissedIsNotHeld() {
        // Five minutes past its time without live times: "Verpasst?" and the clock decides again.
        val now = at("16:21:30")
        val fix = matchPosition(journey, sample(0.00002, 0.2f, 690_000), now, waiting)
        val state = rideState(journey, now, fix)
        assertTrue(state.missed)
        assertTrue(boardingHolds(journey, state) { run }.isEmpty())
        assertEquals(WaitPhase.Coming, tripStep(journey, now, fix).wait)
    }

    @Test
    fun aNewerForecastCountsDownAgain() {
        // A refresh moves the departure to 16:20: at 16:18:30 it is 1:30 away, no longer overdue.
        val later = Journey(listOf(first, sBahn("16:20:00")))
        val now = at("16:18:30")
        val fix = matchPosition(later, sample(0.00002, 0.2f, 510_000), now, waiting.copy(journeyId = later.id))
        val step = tripStep(later, now, fix)
        assertTrue(step.waiting)
        assertEquals(WaitPhase.Coming, step.wait)
        assertFalse(rideState(later, now, fix).overdue)
    }

    @Test
    fun withoutAPositionNothingIsHeld() {
        val state = rideState(journey, at("16:16:20"), null)
        assertTrue(boardingHolds(journey, state) { run }.isEmpty())
    }
}
