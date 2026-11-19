package io.github.jnbp.nextstop.data

import io.github.jnbp.nextstop.ui.StepKind
import io.github.jnbp.nextstop.ui.tripStep
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #224: off the bus (whose live times said +11 while it was on time) and down to the platform of
 * the underground line, the train left with the traveller: its live times followed it to every
 * stop, but in the tunnel the position stayed at the platform (vague and cached fixes). The
 * companion kept "0′ take the U99 at Example Change", "got off · on to the U99", the change
 * "too late" with an offer to rescue it, and the bus big as the ride under way (by its wrong
 * live times it had not arrived yet). Made-up: bus "X1" into "Example Change" (timetable 21:50,
 * live 22:01), a 2 min walk, "U99" from there at 21:54 (live 21:56) to "Example End" at 22:06.
 */
class TunnelBoardingTest {
    private fun at(clock: String): OffsetDateTime = OffsetDateTime.parse("2026-11-18T$clock+01:00")
    private fun ms(clock: String): Long = at(clock).toInstant().toEpochMilli()

    private fun stop(name: String, lat: Double, lon: Double = 10.0) = Stop(name, name, lat, lon, null, setOf("bus", "subway"))

    private val start = stop("Example Start", 50.00)
    private val change = stop("Example Change", 50.06)
    private val platform = stop("Example Change", 50.06, 10.002)
    private val middle = stop("Example Middle", 50.08, 10.002)
    private val end = stop("Example End", 50.10, 10.002)

    private val bus = Leg(
        "X1", "bus", false, start.name, change.name, at("21:45:00"), at("21:34:00"), at("22:01:00"), at("21:50:00"), null, null, null, false,
        realtime = true, path = listOf(50.00 to 10.0, 50.06 to 10.0), fromPlace = start, toPlace = change, tripId = "x1",
    )
    private val walk = Leg(
        null, "walking", true, change.name, change.name, at("22:01:00"), at("21:50:00"), at("22:03:00"), at("21:52:00"), null, null, null, false,
        fromPlace = change, toPlace = platform,
    )
    private val subway = Leg(
        "U99", "subway", false, change.name, end.name, at("21:56:00"), at("21:54:00"), at("22:06:00"), at("22:04:00"), "2", null, null, false,
        realtime = true, path = listOf(50.06 to 10.002, 50.08 to 10.002, 50.10 to 10.002), fromPlace = platform, toPlace = end, tripId = "u99",
        stopovers = listOf(
            Stopover(platform.name, null, at("21:56:00"), place = platform),
            Stopover(middle.name, at("22:01:00"), at("22:01:00"), place = middle),
            Stopover(end.name, at("22:06:00"), null, place = end),
        ),
    )
    private val journey = Journey(listOf(bus, walk, subway))

    private fun sample(lat: Double, speed: Float?, clock: String, accuracy: Float = 10f, ageMillis: Long = 0L, lon: Double = 10.0021) =
        PositionSample(lat, lon, accuracy, speed, ms(clock), ageMillis)

    // Off the bus and on foot at the change, near the platform; then standing on it at 21:55.
    private val walking = PositionFix(journey.id, 0, FixKind.Alighted, 6_670.0, 0.0, -1, ms("21:54:00"), 1.2f, boardMeters = 120.0)
    private val onPlatform = matchPosition(journey, sample(50.0601, 0f, "21:55:00"), at("21:55:00"), walking)

    private fun fixFor(stored: PositionFix, clock: String): PositionFix? {
        CompanionPosition.fix.value = stored
        return CompanionPosition.fixFor(journey, ms(clock)).also { CompanionPosition.clear() }
    }

    @Test
    fun theScene() {
        assertEquals(FixKind.AtBoarding, onPlatform.kind)
        assertEquals(2, onPlatform.legIndex)
        // By the times alone the bus (live arrival 22:01) is the ride under way at 21:57.
        val timed = rideState(journey, at("21:57:00"), null)
        assertEquals(RideStage.OnBoard, timed.stage)
        assertEquals(0, timed.legIndex)
    }

    @Test
    fun vagueOrCachedFixesUndergroundChangeNothing() {
        assertSame(onPlatform, matchPosition(journey, sample(50.0600, null, "21:56:30", accuracy = 400f), at("21:56:30"), onPlatform))
        assertSame(onPlatform, matchPosition(journey, sample(50.0601, 0f, "21:57:00", ageMillis = 120_000), at("21:57:00"), onPlatform))
        assertSame(walking, matchPosition(journey, sample(50.0600, null, "21:55:00", accuracy = 900f), at("21:55:00"), walking))
        assertTrue(noEvidence(sample(50.06, null, "21:57:00", accuracy = 60f)))
        assertFalse(noEvidence(sample(50.06, 9f, "21:57:00", accuracy = 60f)))
        assertFalse(noEvidence(sample(50.06, null, "21:57:00", accuracy = 0f)))
    }

    @Test
    fun theTrainLeftByItsLiveTimesWithTheTravellerOnIt() {
        // Within the grace after its live departure (21:56): still waiting for it.
        assertSame(onPlatform, fixFor(onPlatform, "21:56:30"))
        // Then on board by the times, until a usable fix says otherwise.
        val fix = fixFor(onPlatform, "21:57:00")
        assertNotNull(fix)
        fix!!
        assertEquals(FixKind.OnRide, fix.kind)
        assertEquals(2, fix.legIndex)
        assertTrue("$fix", fix.byTimes)
        assertEquals(1, fix.nextStop)
        val now = at("21:57:00")
        val state = rideState(journey, now, fix)
        assertEquals(RideStage.OnBoard, state.stage)
        assertEquals(2, state.legIndex)
        assertEquals(2, state.doneBefore)
        assertEquals(at("22:06:00"), state.time)
        // The step: get off the U99, not "take the U99", and not the bus.
        val step = tripStep(journey, now, fix)
        assertEquals(StepKind.GetOff, step.kind)
        assertSame(subway, step.leg)
        // The bus and the walk ended before the U99 left: the change was made, nothing to rescue.
        val ridden = journey.plausible().byRide(state, now)
        assertFalse(ridden.legs[0].arrival.isAfter(ridden.legs[1].departure))
        assertFalse(ridden.legs[1].arrival.isAfter(ridden.legs[2].departure))
        val time = changeTime(ridden.legs[0], ridden.legs[2], 2)
        assertTrue("$time ${ridden.legs.map { it.departure.toLocalTime().toString() + '-' + it.arrival.toLocalTime() }}", time.possible)
        // A knapp change that worked: the change card turns green ("Umstieg geschafft", see changeMade).
        assertTrue("$time", time.tightness == Tightness.Risky || time.tightness == Tightness.VeryRisky)
        // Possible and its ride left: no longer a change ahead (see changeAhead), so no rescue offer.
        assertFalse(now.isBefore(ridden.legs[2].departure))
        // Following the times: the next stop moves on with them.
        assertEquals(2, fixFor(onPlatform, "22:02:00")!!.nextStop)
        // At its arrival the times decide again.
        assertNull(fixFor(onPlatform, "22:06:30")?.takeIf { it.byTimes })
    }

    @Test
    fun noFixAtAllOnTheWayDownHoldsTheWalkThenBoards() {
        // No fix since 21:54 (on foot near the platform): the walk holds, not the bus by its times.
        val held = fixFor(walking, "21:56:00")
        assertSame(walking, held)
        assertEquals(RideStage.Walking, rideState(journey, at("21:56:00"), held).stage)
        val fix = fixFor(walking, "21:57:00")!!
        assertTrue(fix.byTimes)
        assertEquals(2, fix.legIndex)
    }

    @Test
    fun aUsableFixOnThePlatformAfterTheDepartureWins() {
        // The train left without the traveller: a good fix, standing on the platform.
        val stand = matchPosition(journey, sample(50.0601, 0f, "21:57:00"), at("21:57:00"), onPlatform)
        assertEquals(FixKind.AtBoarding, stand.kind)
        val fix = fixFor(stand, "21:57:10")
        assertFalse(fix!!.byTimes)
        assertEquals(RideStage.Waiting, rideState(journey, at("21:57:10"), fix).stage)
    }

    @Test
    fun aUsableFixOutOfTheTunnelKeepsTheTrain() {
        // Above ground again, the train standing between stations: still on it.
        val fix = matchPosition(journey, sample(50.0700, 0f, "21:59:00"), at("21:59:00"), onPlatform)
        assertEquals(FixKind.OnRide, fix.kind)
        assertEquals(2, fix.legIndex)
        assertFalse(fix.byTimes)
    }

    @Test
    fun farFromThePlatformTheTimesDecide() {
        // On foot 900 m from the platform when the fixes stopped: no evidence of boarding.
        val far = walking.copy(boardMeters = 900.0)
        assertNull(boardedByTimes(journey, far, ms("21:57:00")))
        // A last fix long before the departure says nothing either.
        assertNull(boardedByTimes(journey, onPlatform.copy(atMillis = ms("21:30:00")), ms("21:57:00")))
    }
}
