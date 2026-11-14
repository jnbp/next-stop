package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ride ways cut where their stops lie beside them, so a way that starts before the platform
 * (the last point of the line's shape before the stop) draws no V with the walk to it; and
 * the retries of the foot router. Made-up coordinates only, no real places.
 */
class RideClipTest {
    private val origin = 52.0 to 13.0
    private val at0: OffsetDateTime = OffsetDateTime.parse("2026-11-13T12:00:00+01:00")

    private fun at(north: Double, east: Double) = BoardingGeometry.moved(origin, north, east)

    private fun assertNear(expected: Pair<Double, Double>, actual: Pair<Double, Double>) =
        assertTrue("$actual is not at $expected", PathJoin.meters(expected, actual) < 0.5)

    private fun sharpest(path: List<Pair<Double, Double>>): Double =
        (1 until path.lastIndex).maxOfOrNull { PathJoin.turnDegrees(path[it - 1], path[it], path[it + 1]) } ?: 0.0

    // A line running south-west: it comes from the north-east, the stop lies 60 m along.
    private val southWest = listOf(at(0.0, 0.0), at(-60.0, -60.0), at(-200.0, -200.0), at(-400.0, -400.0))

    @Test
    fun wayStartingBeforeTheStopIsCutBesideIt() {
        val stop = at(-45.0, -40.0)
        val clipped = PathJoin.clipStartAtStop(southWest, stop)
        assertTrue(PathJoin.meters(clipped.first(), stop) < 5.0)
        assertNear(at(-400.0, -400.0), clipped.last())
        // A walk from the east side to the stop now meets the way without a V; to the first
        // point of the uncut way, past the stop, it turned back sharply.
        val walkEnd = at(-60.0, 20.0)
        assertTrue(sharpest(listOf(walkEnd) + clipped) < 100.0)
        assertTrue(sharpest(listOf(walkEnd) + southWest) > 100.0)
    }

    @Test
    fun wayStartingAtTheStopStays() {
        assertSame(southWest, PathJoin.clipStartAtStop(southWest, at(0.0, 0.0)))
        // A stop off to the side of the start: the way is not cut either.
        assertSame(southWest, PathJoin.clipStartAtStop(southWest, at(20.0, -20.0)))
    }

    @Test
    fun stopFarFromTheWayLeavesItAlone() {
        assertSame(southWest, PathJoin.clipStartAtStop(southWest, at(500.0, 500.0)))
    }

    @Test
    fun wayRunningPastTheStopAndBackIsCutOnItsWayBack() {
        // Out 80 m to the west of the stop and back east through it.
        val spur = listOf(at(0.0, 0.0), at(0.0, -80.0), at(0.0, 0.0), at(0.0, 300.0))
        val clipped = PathJoin.clipStartAtStop(spur, at(0.0, 0.0))
        assertTrue(clipped.none { it.second < origin.second - 1e-7 })
        assertNear(at(0.0, 300.0), clipped.last())
    }

    @Test
    fun endIsCutBesideTheStopWhereTheRideIsLeft() {
        val stop = at(-340.0, -360.0)
        val clipped = PathJoin.clipEndAtStop(southWest, stop)
        assertNear(southWest.first(), clipped.first())
        assertTrue(PathJoin.meters(clipped.last(), stop) < 20.0)
    }

    @Test
    fun ridesAreClippedAndWalksAreNot() {
        val from = Stop("1", "From", at(-45.0, -40.0).first, at(-45.0, -40.0).second, null)
        val to = Stop("2", "To", at(-400.0, -400.0).first, at(-400.0, -400.0).second, null)
        val ride = Leg(
            "U3", "subway", false, from.name, to.name, at0, at0, at0.plusMinutes(5), at0.plusMinutes(5),
            null, null, null, false, path = southWest, fromPlace = from, toPlace = to,
        )
        val clipped = clippedRide(ride)
        assertTrue(PathJoin.meters(clipped.path.first(), from.position!!) < 5.0)
        val walk = ride.copy(walking = true, line = null, product = "")
        assertSame(walk, clippedRide(walk))
    }

    @Test
    fun footRouterIsTriedTwiceMoreThenGivenUp() {
        assertEquals(2_000L, retryDelayMillis(0))
        assertEquals(5_000L, retryDelayMillis(1))
        assertNull(retryDelayMillis(2))
    }
}
