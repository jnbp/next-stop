package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PositionSample
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.matchPosition
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The step of a followed trip by time and position together. Made-up places on a straight
 * line east: Example A (0 m), Example B (~1.1 km), Example C (~2.2 km) by train, then a bus.
 */
class PositionStepTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-01-01T${clock}:00+01:00")

    private val lat = 52.5
    private fun spot(east: Double) = lat to 13.3 + east
    private fun stop(name: String, east: Double) = Stop(name, name, lat, 13.3 + east, null)

    private val a = stop("Example A", 0.0)
    private val b = stop("Example B", 0.016)
    private val c = stop("Example C", 0.032)
    private val d = stop("Example D", 0.048)

    private val train = Leg(
        "S1", "suburban", false, "Example A", "Example C", at("10:00"), at("10:00"), at("10:06"), at("10:06"), null, null, null, false,
        path = listOf(spot(0.0), spot(0.016), spot(0.032)),
        fromPlace = a, toPlace = c,
        stopovers = listOf(
            Stopover("Example A", null, at("10:00"), a),
            Stopover("Example B", at("10:03"), at("10:03"), b),
            Stopover("Example C", at("10:06"), null, c),
        ),
        tripId = "s1",
    )
    private val bus = Leg(
        "100", "bus", false, "Example C", "Example D", at("10:10"), at("10:10"), at("10:20"), at("10:20"), null, null, null, false,
        path = listOf(spot(0.032), spot(0.048)), fromPlace = c, toPlace = d, tripId = "bus",
    )
    private val journey = Journey(listOf(train, bus))

    private fun sample(east: Double, speed: Float?, millis: Long = 0) = PositionSample(lat, 13.3 + east, 10f, speed, millis)

    @Test
    fun onTheTrainPastItsTimeTheStepStaysGettingOff() {
        // 10:08: by the times the train has arrived and the bus is next; the device is still between B and C.
        val now = at("10:08")
        val fix = matchPosition(journey, sample(0.024, 15f), now, null)
        assertEquals(FixKind.OnRide, fix.kind)
        assertEquals(0, fix.legIndex)
        assertEquals(2, fix.nextStop)
        assertEquals(StepKind.Board, tripStep(journey, now, null).kind)
        val step = tripStep(journey, now, fix)
        assertEquals(StepKind.GetOff, step.kind)
        assertSame(train, step.leg)
        assertTrue(step.estimated)
        assertTrue(step.time.isAfter(now))
    }

    @Test
    fun beforeTheStopBeforeTheNextStopIsThatStop() {
        val fix = matchPosition(journey, sample(0.008, 15f), at("10:02"), null)
        assertEquals(1, fix.nextStop)
    }

    @Test
    fun atTheEndItIsTimeToGetOffNow() {
        val now = at("10:04")
        val fix = matchPosition(journey, sample(0.0315, 2f), now, null)
        assertEquals(FixKind.AtEnd, fix.kind)
        val step = tripStep(journey, now, fix)
        assertEquals(StepKind.GetOff, step.kind)
        assertEquals(now, step.time)
    }

    @Test
    fun standingAtTheStopOfALateTrainIsBoardNow() {
        val now = at("10:02")
        val fix = matchPosition(journey, sample(0.0002, 0f), now, null)
        assertEquals(FixKind.AtBoarding, fix.kind)
        val step = tripStep(journey, now, fix)
        assertEquals(StepKind.Board, step.kind)
        assertSame(train, step.leg)
    }

    @Test
    fun walkingAlongTheLineBeforeTheDepartureIsNotRiding() {
        val now = at("09:50")
        val fix = matchPosition(journey, sample(0.01, 1.2f), now, null)
        assertEquals(StepKind.Board, tripStep(journey, now, fix).kind)
    }

    @Test
    fun aVehicleComingToTheBoardingStopIsBoardedEarly() {
        // 09:55, 5 min before the train: at 20 m/s toward Example A from the west, twice.
        val now = at("09:55")
        val first = matchPosition(journey, sample(-0.06, 20f, 0), now, null)
        assertEquals(FixKind.Off, first.kind)
        val second = matchPosition(journey, sample(-0.05, 20f, 30_000), now.plusSeconds(30), first)
        assertEquals(FixKind.OnBoardEarly, second.kind)
        val step = tripStep(journey, now.plusSeconds(30), second)
        assertEquals(StepKind.GetOff, step.kind)
        assertSame(train, step.leg)
    }

    @Test
    fun farOffTheWayTheTimesStand() {
        val now = at("10:02")
        val fix = matchPosition(journey, PositionSample(52.6, 13.4, 10f, 0f, 0), now, null)
        assertEquals(FixKind.Off, fix.kind)
        assertEquals(tripStep(journey, now, null), tripStep(journey, now, fix))
    }

    @Test
    fun onBoardPastTheArrivalTheTripGoesOnForAnHourAtMost() {
        val onBoard = io.github.jnbp.nextstop.data.PositionFix(journey.id, 1, FixKind.OnRide, 100.0, 900.0, -1, 0L)
        // The bus arrives 10:20.
        assertEquals(false, tripOver(journey, at("10:24"), null))
        assertEquals(true, tripOver(journey, at("10:26"), null))
        assertEquals(false, tripOver(journey, at("10:40"), onBoard))
        assertEquals(true, tripOver(journey, at("11:21"), onBoard))
    }

    @Test
    fun theIdOfAJourneyWithoutPlannedTimesStaysTheSameOverRefreshes() {
        val bare = train.copy(plannedDeparture = null, plannedArrival = null)
        val known = Journey(listOf(bare))
        val later = Journey(listOf(bare.copy(departure = at("10:02"), arrival = at("10:08"))))
        assertTrue(known.id != later.id)
        val kept = withKnownPlannedTimes(later, known)
        assertEquals(known.id, kept.id)
        assertEquals(at("10:02"), kept.legs[0].departure)
        // No planned time is made up from the first times seen: the ride shows no delay.
        assertEquals(null, kept.legs[0].plannedDeparture)
        assertEquals(0L, kept.legs[0].departureDelayMinutes)
        // And the id holds over the next refresh too.
        val again = withKnownPlannedTimes(Journey(listOf(bare.copy(departure = at("10:04"), arrival = at("10:10")))), kept)
        assertEquals(known.id, again.id)
        assertEquals(0L, again.legs[0].arrivalDelayMinutes)
        // With planned times of its own, the fresh journey stays as it is.
        val planned = Journey(listOf(train))
        assertSame(planned, withKnownPlannedTimes(planned, known))
    }
}
