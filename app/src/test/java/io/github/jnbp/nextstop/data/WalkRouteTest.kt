package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leaving the way of a walk (see OffRouteWatch). The way runs east along 52.5° N from
 * 13.400° to 13.410° E, about 680 m; 0.0001° of latitude is about 11 m.
 */
class WalkRouteTest {
    private val route = listOf(52.5 to 13.400, 52.5 to 13.405, 52.5 to 13.410)
    private val second = 1_000L

    // A point [north] metres north of the way, at the share [along] of it.
    private fun point(along: Double, north: Double = 0.0) =
        (52.5 + north / 111_320.0) to (13.400 + 0.010 * along)

    @Test
    fun fixMeasuresBesideAndAhead() {
        val fix = routeFix(route, point(0.25, north = 50.0))!!
        assertEquals(50.0, fix.offMeters, 1.0)
        assertEquals(routeMeters(route) * 0.75, fix.remainingMeters, 2.0)
        assertEquals(678.0, routeMeters(route), 5.0)
        assertNull(routeFix(listOf(52.5 to 13.4), point(0.0)))
    }

    @Test
    fun onTheWayNothingHappens() {
        val watch = OffRouteWatch()
        for (step in 0..60) {
            assertFalse(watch.update(route, point(step / 100.0, north = 15.0), 10f, step * second))
        }
    }

    @Test
    fun besideTheWayForTwentySecondsAsksForANewOne() {
        val watch = OffRouteWatch()
        assertFalse(watch.update(route, point(0.2), 10f, 0))
        // 60 m beside it from second 1 on: not yet at 1 or 20, at 21 (20 s off).
        assertFalse(watch.update(route, point(0.2, north = 60.0), 10f, 1 * second))
        assertFalse(watch.update(route, point(0.2, north = 60.0), 10f, 20 * second))
        assertTrue(watch.update(route, point(0.2, north = 60.0), 10f, 21 * second))
    }

    @Test
    fun backOnTheWayClearsTheCount() {
        val watch = OffRouteWatch()
        assertFalse(watch.update(route, point(0.2, north = 60.0), 10f, 0))
        assertFalse(watch.update(route, point(0.2, north = 60.0), 10f, 15 * second))
        assertFalse(watch.update(route, point(0.22, north = 5.0), 10f, 16 * second))
        assertFalse(watch.update(route, point(0.24, north = 60.0), 10f, 17 * second))
        assertFalse(watch.update(route, point(0.24, north = 60.0), 10f, 30 * second))
        assertTrue(watch.update(route, point(0.24, north = 60.0), 10f, 37 * second))
    }

    @Test
    fun poorAccuracyCountsForNothing() {
        val watch = OffRouteWatch()
        assertFalse(watch.update(route, point(0.2, north = 60.0), 10f, 0))
        // 25 s of wide fixes far off: ignored, and they do not end the count either.
        assertFalse(watch.update(route, point(0.2, north = 200.0), 50f, 10 * second))
        assertFalse(watch.update(route, point(0.2, north = 200.0), 31f, 19 * second))
        assertFalse(watch.update(route, point(0.2, north = 200.0), null, 25 * second))
        assertTrue(watch.update(route, point(0.2, north = 60.0), 30f, 26 * second))
    }

    @Test
    fun walkingTheWrongWayAsksForANewOne() {
        val watch = OffRouteWatch()
        assertFalse(watch.update(route, point(0.5), 8f, 0))
        // Back along the way itself, never beside it: from 0.5 to 0.4 is about 68 m back.
        assertFalse(watch.update(route, point(0.45), 8f, 5 * second))
        assertFalse(watch.update(route, point(0.4), 8f, 10 * second))
        assertFalse(watch.update(route, point(0.35), 8f, 29 * second))
        assertTrue(watch.update(route, point(0.3), 8f, 31 * second))
    }

    @Test
    fun atTheEndNothingCountsAsOff() {
        val watch = OffRouteWatch()
        assertFalse(watch.update(route, point(0.98, north = 20.0), 8f, 0))
        assertFalse(watch.update(route, point(1.0, north = 30.0), 8f, 30 * second))
    }

    @Test
    fun atMostOneRerouteEveryThirtySeconds() {
        val watch = OffRouteWatch()
        val far = point(0.2, north = 80.0)
        assertFalse(watch.update(route, far, 10f, 0))
        assertTrue(watch.update(route, far, 10f, 20 * second))
        // The new way is not there yet: still off, 20 s more, but only 25 s since the last one.
        assertFalse(watch.update(route, far, 10f, 21 * second))
        assertFalse(watch.update(route, far, 10f, 45 * second))
        assertTrue(watch.update(route, far, 10f, 50 * second))
        // A new way (reset) does not lift the pause.
        watch.reset()
        assertFalse(watch.update(route, far, 10f, 60 * second))
        assertFalse(watch.update(route, far, 10f, 79 * second))
        assertTrue(watch.update(route, far, 10f, 80 * second))
    }

    @Test
    fun minutesForTheRestOfAWay() {
        assertEquals(0L, walkMinutesFor(0.0))
        assertEquals(1L, walkMinutesFor(10.0))
        assertEquals(4L, walkMinutesFor(300.0))
        assertEquals(5L, walkMinutesFor(301.0))
    }

    private val start: OffsetDateTime = OffsetDateTime.parse("2026-01-01T13:40:00+01:00")
    private fun at(minutes: Long) = start.plusMinutes(minutes)
    private fun leg(walking: Boolean, leaves: Long, arrives: Long) = Leg(
        if (walking) null else "U7", if (walking) "walking" else "subway", walking, "A", "B",
        at(leaves), at(leaves), at(arrives), at(arrives), null, null, null, false,
    )

    // Walk 0–5, U7 8–20, walk 20–24, U8 27–35, walk 35–40.
    private val journey = Journey(
        listOf(leg(true, 0, 5), leg(false, 8, 20), leg(true, 20, 24), leg(false, 27, 35), leg(true, 35, 40)),
    )

    @Test
    fun theWalkUnderWay() {
        assertEquals(0, activeWalkIndex(journey, at(2)))
        // Later than the provider thinks, still on the way to the U7.
        assertEquals(0, activeWalkIndex(journey, at(6)))
        assertNull(activeWalkIndex(journey, at(10)))
        assertEquals(2, activeWalkIndex(journey, at(21)))
        assertEquals(2, activeWalkIndex(journey, at(25)))
        assertEquals(4, activeWalkIndex(journey, at(37)))
        assertNull(activeWalkIndex(journey, at(41)))
    }

    @Test
    fun aLongerWalkMissesTheRideAfterIt() {
        assertFalse(walkMissesRide(journey, 2, at(27)))
        assertTrue(walkMissesRide(journey, 2, at(28)))
        // No ride after the last walk.
        assertFalse(walkMissesRide(journey, 4, at(60)))
    }
}
