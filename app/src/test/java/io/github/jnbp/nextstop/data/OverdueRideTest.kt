package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #170: still on the long-distance train a few km before its last stop, past its live arrival
 * (late, or standing on the track before the station), the companion took the ride for over:
 * the train's way grey, its marker gone, the change "zu spät". The next ride leaves the same
 * station back along the same track, so the position matched that one too. Made-up line from
 * the south into "Berlin Hbf" (about 11 km), live arrival 15:30, the next ride at 15:36 back south.
 */
class OverdueRideTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T${clock}:00+01:00")

    private fun stop(name: String, lat: Double) = Stop(name, name, lat, 10.0, null, setOf("express"))

    private val south = stop("Example South", 50.00)
    private val mid = stop("Example Mid", 50.05)
    private val hbf = stop("Berlin Hbf", 50.10)
    private val back = stop("Example Back", 50.00)

    private val ice = Leg(
        "ICE 374", "express", false, south.name, hbf.name, at("14:50"), at("14:50"), at("15:30"), at("15:10"), null, "1", null, false,
        realtime = true, path = listOf(50.00 to 10.0, 50.05 to 10.0, 50.10 to 10.0), fromPlace = south, toPlace = hbf, tripId = "ice",
        stopovers = listOf(
            Stopover(south.name, null, at("14:50"), place = south), Stopover(mid.name, at("15:10"), at("15:11"), place = mid),
            Stopover(hbf.name, at("15:30"), null, place = hbf),
        ),
    )
    private val walk = Leg(
        null, "walking", true, hbf.name, hbf.name, at("15:30"), at("15:30"), at("15:33"), at("15:33"), null, null, null, false,
        fromPlace = hbf, toPlace = hbf,
    )

    // The next ride back south on a track 10 m beside (its feed's shape a little nearer to the position).
    private val re = Leg(
        "RE3", "regional", false, hbf.name, back.name, at("15:36"), at("15:36"), at("16:00"), at("16:00"), "2", null, null, false,
        realtime = true, path = listOf(50.10 to 10.00014, 50.05 to 10.00014, 50.00 to 10.00014), fromPlace = hbf, toPlace = back, tripId = "re",
    )

    private val journey = Journey(listOf(ice, walk, re))

    private fun sample(lat: Double, speed: Float, clock: String, lon: Double = 10.0001) =
        PositionSample(lat, lon, 10f, speed, at(clock).toInstant().toEpochMilli())

    private fun onIce(prev: PositionFix?, lat: Double, speed: Float, clock: String): PositionFix =
        matchPosition(journey, sample(lat, speed, clock), at(clock), prev)

    private val wasOnIce = PositionFix(journey.id, 0, FixKind.OnRide, 6_000.0, 5_000.0, 2, at("15:31").toInstant().toEpochMilli(), 20f)

    @Test
    fun movingInPastTheLiveArrivalIsStillOnTheTrain() {
        val fix = onIce(wasOnIce, 50.055, 15f, "15:32")
        assertEquals(FixKind.OnRide, fix.kind)
        assertEquals(0, fix.legIndex)
        val state = rideState(journey, at("15:32"), fix)
        assertEquals(RideStage.OnBoard, state.stage)
        assertEquals(0, state.legIndex)
        // The ride is not over: it arrives after now, later than its live time.
        assert(state.time!!.isAfter(at("15:32")))
    }

    @Test
    fun standingOnTheTrackTwoKilometresBeforeIsStillOnBoardEvenWithoutAFixBefore() {
        val fix = onIce(null, 50.082, 0f, "15:33")
        assertEquals(FixKind.OnRide, fix.kind)
        assertEquals(0, fix.legIndex)
        assertEquals(RideStage.OnBoard, rideState(journey, at("15:33"), fix).stage)
    }

    @Test
    fun standingCloseBeforeTheStationAfterBeingOnBoardStaysOnBoard() {
        // 250 m before the platform end of the way, the train waits for its track.
        val fix = onIce(wasOnIce.copy(alongMeters = 10_800.0, metersToEnd = 300.0), 50.0978, 0f, "15:34")
        assertEquals(FixKind.OnRide, fix.kind)
        assertEquals(0, fix.legIndex)
    }

    @Test
    fun wronglyOnTheNextRideCorrectsBack() {
        // A fix before took the traveller for on the RE (its way lies nearer), which has not left yet.
        val wrong = PositionFix(journey.id, 2, FixKind.OnRide, 5_000.0, 6_000.0, -1, at("15:31").toInstant().toEpochMilli(), 15f)
        val fix = onIce(wrong, 50.056, 15f, "15:32")
        assertEquals(0, fix.legIndex)
        assertEquals(FixKind.OnRide, fix.kind)
    }

    @Test
    fun aNearerWayOfAnotherRideDoesNotTakeOverWhileOnBoard() {
        val fix = onIce(wasOnIce, 50.060, 20f, "15:32")
        assertEquals(0, fix.legIndex)
    }

    @Test
    fun atTheStationAfterTheArrivalTheRideEnds() {
        val fix = onIce(wasOnIce, 50.0995, 0f, "15:35")
        assertNotEquals(FixKind.OnRide, fix.kind)
        assertNotEquals(RideStage.OnBoard, rideState(journey, at("15:35"), fix).stage)
    }

    @Test
    fun withoutAFreshFixTheTimesDecide() {
        val state = rideState(journey, at("15:32"), null)
        assertNotEquals(RideStage.OnBoard, state.stage)
    }

    @Test
    fun longAfterTheLiveArrivalStandingOnTheTrackIsNoLongerTheTrain() {
        // 40 min past the live arrival: no longer taken for the train by standing on its track.
        val fix = onIce(null, 50.082, 0f, "16:10")
        assertNotEquals(0 to FixKind.OnRide, fix.legIndex to fix.kind)
    }

    @Test
    fun onBoardHoldsThreeMinutesWithoutAFix() {
        // A tunnel before the station: the last fix said on board 2.5 min ago; still used.
        val last = wasOnIce.copy(atMillis = at("15:31").toInstant().toEpochMilli())
        CompanionPosition.fix.value = last
        val nowMillis = at("15:33").toInstant().toEpochMilli() + 30_000
        assertEquals(last, CompanionPosition.fixFor(journey, nowMillis))
        // A fix of another kind goes after the usual 90 s.
        CompanionPosition.fix.value = last.copy(kind = FixKind.AtBoarding)
        assertEquals(null, CompanionPosition.fixFor(journey, nowMillis))
        // Past 3 min the times decide again.
        CompanionPosition.fix.value = last
        assertEquals(null, CompanionPosition.fixFor(journey, at("15:34").toInstant().toEpochMilli() + 30_000))
        CompanionPosition.clear()
    }

    @Test
    fun theMapSplitFollowsTheRideState() {
        // On board past the live arrival: the ICE is ridden (coloured ahead of the train), the RE ahead.
        val fix = onIce(wasOnIce, 50.055, 15f, "15:32")
        val state = rideState(journey, at("15:32"), fix)
        assertEquals(io.github.jnbp.nextstop.ui.RoutePhase.Riding, io.github.jnbp.nextstop.ui.routePhase(0, ice, at("15:32"), state))
        assertEquals(io.github.jnbp.nextstop.ui.RoutePhase.Ahead, io.github.jnbp.nextstop.ui.routePhase(2, re, at("15:37"), state))
        // The split at the traveller, about 5.5 km along: not at the end of the way.
        val split = io.github.jnbp.nextstop.ui.ridePositionOnWay(ice.path, fix.alongMeters)!!
        assertTrue(split.first in 50.050..50.060)
        // Without a position the clock decides: the ICE behind.
        val timed = rideState(journey, at("15:32"), null)
        assertEquals(io.github.jnbp.nextstop.ui.RoutePhase.Behind, io.github.jnbp.nextstop.ui.routePhase(0, ice, at("15:32"), timed))
    }

    @Test
    fun movingAgainstARidesDirectionIsNotOnIt() {
        // Taken for on the RE (already left at 15:36) 5 km along its way; the next fix lies 600 m
        // back toward its start while moving fast: the traveller goes the other way, on the ICE.
        val onRe = PositionFix(journey.id, 2, FixKind.OnRide, 5_550.0, 5_550.0, -1, at("15:39").toInstant().toEpochMilli(), 20f)
        val fix = matchPosition(journey, sample(50.0555, 20f, "15:40", lon = 10.00013), at("15:40"), onRe)
        assertNotEquals(2, fix.legIndex)
    }
}
