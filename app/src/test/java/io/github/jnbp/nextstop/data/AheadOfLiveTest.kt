package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #216: on the bus, several stops past the one where it was boarded, moving at the speed of a
 * vehicle, while the live times said +11 for a bus that was on time. The companion kept saying
 * "take the bus at its stop": a ride is never matched away from its start before its live
 * departure. Made-up bus line "X1" north from "Example Start" (about 6.7 km) to "Example Change",
 * then a short walk to the underground line "U99". Timetable 21:34 to 21:50, live (wrong) 21:45
 * to 22:01; the U99 leaves at 21:56.
 */
class AheadOfLiveTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T$clock+01:00")

    private fun stop(name: String, lat: Double) = Stop(name, name, lat, 10.0, null, setOf("bus"))

    private val start = stop("Example Start", 50.00)
    private val second = stop("Example Second", 50.02)
    private val third = stop("Example Third", 50.04)
    private val change = stop("Example Change", 50.06)
    private val end = stop("Example End", 50.10)

    private val bus = Leg(
        "X1", "bus", false, start.name, change.name, at("21:45:00"), at("21:34:00"), at("22:01:00"), at("21:50:00"), null, null, null, false,
        realtime = true, path = listOf(50.00 to 10.0, 50.03 to 10.0, 50.06 to 10.0), fromPlace = start, toPlace = change, tripId = "x1",
        stopovers = listOf(
            Stopover(start.name, null, at("21:45:00"), place = start, plannedDeparture = at("21:34:00")),
            Stopover(second.name, at("21:50:00"), at("21:50:00"), place = second, plannedArrival = at("21:39:00")),
            Stopover(third.name, at("21:55:00"), at("21:55:00"), place = third, plannedArrival = at("21:44:00")),
            Stopover(change.name, at("22:01:00"), null, place = change, plannedArrival = at("21:50:00")),
        ),
    )
    private val walk = Leg(
        null, "walking", true, change.name, change.name, at("22:01:00"), at("21:50:00"), at("22:03:00"), at("21:52:00"), null, null, null, false,
        fromPlace = change, toPlace = change,
    )
    private val subway = Leg(
        "U99", "subway", false, change.name, end.name, at("21:56:00"), at("21:56:00"), at("22:06:00"), at("22:06:00"), "2", null, null, false,
        realtime = true, path = listOf(50.06 to 10.002, 50.10 to 10.002), fromPlace = change.copy(longitude = 10.002), toPlace = end, tripId = "u99",
    )
    private val journey = Journey(listOf(bus, walk, subway))

    private fun sample(lat: Double, speed: Float?, clock: String) =
        PositionSample(lat, 10.0001, 10f, speed, at(clock).toInstant().toEpochMilli())

    // Waiting at the stop of the bus before it came: the fix before the ride.
    private val waited = matchPosition(journey, sample(50.0001, 0f, "21:33:00"), at("21:33:00"), null)

    /** The fixes of a ride on the bus from 21:36:00, every 15 s, [lats] along its way. */
    private fun ride(lats: List<Double>, speed: Float?, first: PositionFix = waited, from: String = "21:36:00"): List<PositionFix> {
        var prev = first
        var clock = at(from)
        return lats.map { lat ->
            val fix = matchPosition(journey, sample(lat, speed, clock.toLocalTime().toString().let { if (it.length == 5) "$it:00" else it }), clock, prev)
            prev = fix
            clock = clock.plusSeconds(15)
            fix
        }
    }

    @Test
    fun theScene() {
        assertEquals(FixKind.AtBoarding, waited.kind)
        // By the live times the bus leaves at 21:45: the change to the U99 at 21:56 is too late.
        assertFalse(changeTime(bus, subway, 2).possible)
    }

    @Test
    fun followingTheBusWayAtVehicleSpeedIsOnBoardAheadOfTheLiveTimes() {
        val fixes = ride(listOf(50.0045, 50.0057, 50.0069), 9f)
        // One fix alone or two do not decide it: the times still have the bus to come.
        assertNotEquals(FixKind.OnRide, fixes[0].kind)
        assertNotEquals(FixKind.OnRide, fixes[1].kind)
        assertEquals(2, fixes[1].aheadCount)
        val fix = fixes[2]
        assertEquals(FixKind.OnRide, fix.kind)
        assertEquals(0, fix.legIndex)
        assertTrue(fix.aheadOfLive)
        val now = at("21:36:30")
        val state = rideState(journey, now, fix)
        assertEquals(RideStage.OnBoard, state.stage)
        assertEquals(0, state.legIndex)
        // The arrival by the position, not by the wrong +11: about 21:50 (timetable), not 22:01.
        val arrival = state.time!!
        assertTrue("$arrival", arrival.isAfter(at("21:49:00")) && arrival.isBefore(at("21:52:00")))
        assertTrue(state.estimated)
        // So the change to the U99 works again, and its warning goes.
        val ridden = journey.plausible().byRide(state, now)
        assertTrue(changeTime(ridden.legs[0], ridden.legs[2], 2).possible)
    }

    @Test
    fun onBoardItStaysOnBoardAlongTheWay() {
        val fixes = ride(listOf(50.0045, 50.0057, 50.0069, 50.0085, 50.0100), 9f)
        assertTrue(fixes.drop(2).all { it.kind == FixKind.OnRide && it.aheadOfLive })
        // Standing at a stop on the way for a while does not drop it.
        val stand = matchPosition(journey, sample(50.0200, 0f, "21:39:30"), at("21:39:30"), fixes.last())
        assertEquals(FixKind.OnRide, stand.kind)
        assertTrue(stand.aheadOfLive)
        // Past the live departure (21:45) too, the arrival stays by the position: on time at
        // 21:46 a quarter of the way from the third stop on, so about 21:50, not 22:01.
        val later = matchPosition(journey, sample(50.0450, 9f, "21:46:00"), at("21:46:00"), stand)
        assertTrue(later.aheadOfLive)
        val arrival = rideState(journey, at("21:46:00"), later).time!!
        assertTrue("$arrival", arrival.isBefore(at("21:53:00")))
        // And the arrival on board over time (RideTracker) says the same.
        val eta = nextRideEta(null, journey.plausible().legs[0], later, at("21:46:00"))
        assertTrue("${eta.arrival}", eta.arrival.isBefore(at("21:53:00")))
    }

    @Test
    fun standingAtTheBoardingStopIsStillWaiting() {
        val fixes = ride(listOf(50.0001, 50.0002, 50.0001, 50.0002), 0f)
        assertTrue(fixes.none { it.kind == FixKind.OnRide })
        assertEquals(RideStage.Waiting, rideState(journey, at("21:36:45"), fixes.last()).stage)
    }

    @Test
    fun walkingAlongTheBusWayIsNotTheBus() {
        // On foot beyond the stop: 1.3 m/s, about 20 m per fix.
        val fixes = ride(listOf(50.0030, 50.0032, 50.0034, 50.0036), 1.3f)
        assertTrue(fixes.none { it.kind == FixKind.OnRide })
    }

    @Test
    fun withoutSpeedOfTheDeviceTheWayMadeCounts() {
        // No speed from the device: 130 m in 15 s between fixes (8.7 m/s) counts as a vehicle.
        val fixes = ride(listOf(50.0040, 50.0052, 50.0064, 50.0076), null)
        assertEquals(FixKind.OnRide, fixes.last().kind)
    }

    @Test
    fun longBeforeItsTimetableTimeTheWayIsNoEvidence() {
        // At 21:00 (the bus timetabled 21:34): a car or a bike on the same street, not the bus.
        val fixes = ride(listOf(50.0045, 50.0057, 50.0069, 50.0081), 9f, first = matchPosition(journey, sample(50.0001, 0f, "20:59:00"), at("20:59:00"), null), from = "21:00:00")
        assertTrue(fixes.none { it.kind == FixKind.OnRide })
    }
}
