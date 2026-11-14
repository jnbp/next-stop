package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Joins of ways without spikes. Made-up coordinates only, no real places. */
class PathJoinTest {
    private val origin = 52.0 to 13.0

    /** The point [north] and [east] metres from the origin. */
    private fun at(north: Double, east: Double) = BoardingGeometry.moved(origin, north, east)

    /** The sharpest turn anywhere along [path], in degrees. */
    private fun sharpest(path: List<Pair<Double, Double>>): Double =
        (1 until path.lastIndex).maxOfOrNull { PathJoin.turnDegrees(path[it - 1], path[it], path[it + 1]) } ?: 0.0

    private fun assertNear(expected: Pair<Double, Double>, actual: Pair<Double, Double>) =
        assertTrue("$actual is not at $expected", PathJoin.meters(expected, actual) < 0.5)

    // A way running east from the origin for 300 m.
    private val east = listOf(at(0.0, 0.0), at(0.0, 100.0), at(0.0, 200.0), at(0.0, 300.0))

    @Test
    fun turns() {
        assertEquals(0.0, PathJoin.turnDegrees(at(0.0, 0.0), at(0.0, 10.0), at(0.0, 20.0)), 0.5)
        assertEquals(90.0, PathJoin.turnDegrees(at(0.0, 0.0), at(0.0, 10.0), at(10.0, 10.0)), 0.5)
        assertEquals(180.0, PathJoin.turnDegrees(at(0.0, 0.0), at(0.0, 10.0), at(0.0, 0.0)), 0.5)
    }

    @Test
    fun spotAheadOnTheWayCutsItThere() {
        // The vehicle's way starts 40 m before the stop position: no V back.
        val joined = PathJoin.attachStart(east, at(0.0, 40.0))
        assertNear(at(0.0, 40.0), joined.first())
        assertNear(at(0.0, 100.0), joined[1])
        assertTrue(sharpest(joined) < 1.0)
    }

    @Test
    fun spotBesideTheWayIsJoinedAcross() {
        val joined = PathJoin.attachStart(east, at(12.0, 40.0))
        assertNear(at(12.0, 40.0), joined.first())
        assertNear(at(0.0, 40.0), joined[1])
        assertTrue(sharpest(joined) < PathJoin.MAX_TURN_DEGREES)
    }

    @Test
    fun spotBehindTheStartExtendsIt() {
        val joined = PathJoin.attachStart(east, at(0.0, -30.0))
        assertEquals(east.size + 1, joined.size)
        assertNear(at(0.0, -30.0), joined.first())
        assertTrue(sharpest(joined) < 1.0)
    }

    @Test
    fun spotAtTheEndIsJoinedTheSameWay() {
        val joined = PathJoin.attachEnd(east, at(0.0, 260.0))
        assertNear(at(0.0, 260.0), joined.last())
        assertNear(at(0.0, 200.0), joined[joined.lastIndex - 1])
        assertTrue(sharpest(joined) < 1.0)
    }

    @Test
    fun sharpJoinIsLeftOutUnlessRequired() {
        // A way that starts with a hook: the spot beside its first point would need a sharp turn.
        val hook = listOf(at(0.0, 0.0), at(0.0, 50.0), at(50.0, 50.0))
        val spot = at(-5.0, 60.0)
        val joined = PathJoin.attachStart(hook, spot, maxTurn = 30.0)
        assertTrue(joined.none { PathJoin.meters(it, spot) < 0.5 })
        val forced = PathJoin.attachStart(hook, spot, maxTurn = 30.0, always = true)
        assertNear(spot, forced.first())
    }

    @Test
    fun farPartsOfTheWayAreNotCut() {
        // A loop back past the stop position 1 km on is not where the way is cut.
        val loop = listOf(at(0.0, 0.0), at(0.0, 600.0), at(5.0, 600.0), at(5.0, 10.0))
        val joined = PathJoin.attachStart(loop, at(4.0, 10.0))
        assertEquals(loop.size + 1, joined.size)
    }

    @Test
    fun zigzagsAtTheEndsGo() {
        val zig = listOf(at(0.0, 0.0), at(0.0, -8.0), at(0.0, 100.0), at(0.0, 200.0), at(0.0, 205.0), at(0.0, 195.0))
        val smooth = PathJoin.smoothEnds(zig)
        assertNear(at(0.0, 0.0), smooth.first())
        assertNear(at(0.0, 195.0), smooth.last())
        assertEquals(3, smooth.size)
        assertTrue(sharpest(smooth) < 1.0)
        // A long stretch back is a real way and stays.
        val long = listOf(at(0.0, 0.0), at(0.0, -40.0), at(0.0, 100.0))
        assertEquals(3, PathJoin.smoothEnds(long).size)
    }

    @Test
    fun walkBetweenRidesIsClipped() {
        // The way on foot from station centre to station centre runs 60 m past both rides' ends.
        val centreA = at(0.0, -60.0)
        val centreB = at(0.0, 360.0)
        val way = listOf(centreA, at(0.0, 0.0), at(0.0, 150.0), at(0.0, 300.0), centreB)
        val arrivingEnd = at(8.0, 20.0)
        val nextStart = at(-8.0, 280.0)
        val walk = PathJoin.clipWalk(way, arrivingEnd, nextStart)
        assertNear(arrivingEnd, walk.first())
        assertNear(nextStart, walk.last())
        assertNear(at(0.0, 20.0), walk[1])
        assertNear(at(0.0, 280.0), walk[walk.lastIndex - 1])
        assertTrue(walk.none { PathJoin.meters(it, centreA) < 1.0 || PathJoin.meters(it, centreB) < 1.0 })
        assertTrue(sharpest(walk) < PathJoin.MAX_TURN_DEGREES)
    }

    @Test
    fun walkGoingBackwardsIsStraight() {
        val way = listOf(at(0.0, 0.0), at(0.0, 100.0), at(0.0, 200.0))
        val walk = PathJoin.clipWalk(way, at(5.0, 150.0), at(5.0, 50.0))
        assertEquals(2, walk.size)
    }

    @Test
    fun walkWithOneOpenEndKeepsTheWayThere() {
        val way = listOf(at(0.0, 0.0), at(0.0, 100.0), at(0.0, 200.0))
        val walk = PathJoin.clipWalk(way, null, at(6.0, 150.0))
        assertNear(at(0.0, 0.0), walk.first())
        assertNear(at(6.0, 150.0), walk.last())
        assertNear(at(0.0, 150.0), walk[walk.lastIndex - 1])
    }
}
