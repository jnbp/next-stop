package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Where to board, estimated from the map. Made-up coordinates only, no real places. */
class BoardingGeometryTest {
    private val change = 10.0 to 20.0

    private fun at(north: Double, east: Double) = BoardingGeometry.moved(change, north, east)

    private fun part(previous: Pair<Double, Double>, departure: Pair<Double, Double>, product: String = "subway"): Int? =
        BoardingGeometry.estimate(previous, change, departure, product).estimate?.part

    @Test
    fun bearings() {
        assertEquals(0.0, BoardingGeometry.bearing(at(-300.0, 0.0), change), 0.1)
        assertEquals(90.0, BoardingGeometry.bearing(at(0.0, -300.0), change), 0.1)
        assertEquals(180.0, BoardingGeometry.bearing(at(300.0, 0.0), change), 0.1)
        assertEquals(270.0, BoardingGeometry.bearing(at(0.0, 300.0), change), 0.1)
        assertEquals(45.0, BoardingGeometry.bearing(at(-200.0, -200.0), change), 0.5)
        assertEquals(300.0, BoardingGeometry.distance(change, at(300.0, 0.0)), 0.5)
    }

    @Test
    fun thresholds() {
        assertEquals(30.0, BoardingGeometry.threshold("subway"), 0.01)
        assertEquals(44.1, BoardingGeometry.threshold("suburban"), 0.01)
        assertEquals(48.0, BoardingGeometry.threshold("regional"), 0.01)
    }

    @Test
    fun runningNorth() {
        val previous = at(-400.0, 0.0)
        assertEquals(2, part(previous, at(100.0, 0.0)))
        assertEquals(0, part(previous, at(-100.0, 0.0)))
        assertEquals(1, part(previous, at(20.0, 0.0)))
        // Far to the side, hardly ahead: the middle.
        assertEquals(1, part(previous, at(10.0, 150.0)))
        // A longer S-Bahn needs more distance for an end.
        assertEquals(1, part(previous, at(40.0, 0.0), "suburban"))
        assertEquals(2, part(previous, at(70.0, 5.0), "suburban"))
    }

    @Test
    fun runningEastAndSouthWest() {
        assertEquals(2, part(at(0.0, -400.0), at(0.0, 80.0)))
        assertEquals(0, part(at(0.0, -400.0), at(20.0, -80.0)))
        val fromNorthEast = at(300.0, 300.0)
        assertEquals(2, part(fromNorthEast, at(-70.0, -70.0)))
        assertEquals(0, part(fromNorthEast, at(70.0, 70.0)))
        assertEquals(1, part(fromNorthEast, at(-50.0, 50.0)))
    }

    @Test
    fun noEstimate() {
        val previous = at(-400.0, 0.0)
        assertNull(part(previous, at(3.0, 4.0)))
        assertNull(part(previous, at(800.0, 0.0)))
        assertNull(part(at(-20.0, 0.0), at(100.0, 0.0)))
    }

    @Test
    fun tangentOfACurve() {
        // From the west, then turning north into the change.
        val path = listOf(at(-400.0, -300.0), at(-400.0, -150.0), at(-400.0, 0.0), at(-300.0, 0.0), at(-200.0, 0.0), at(-100.0, 0.0), change)
        val tangent = BoardingGeometry.tangentPoint(path, change)
        assertNotNull(tangent)
        assertEquals(0.0, BoardingGeometry.bearing(tangent!!, change), 0.5)
        val departure = at(40.0, -80.0)
        assertEquals(2, part(tangent, departure))
        // Taken from the boarding stop, the direction is off and the hint would be wrong.
        assertEquals(1, part(path.first(), departure))
        assertNull(BoardingGeometry.tangentPoint(listOf(change), change))
        assertNull(BoardingGeometry.tangentPoint(listOf(at(2000.0, 0.0), at(2500.0, 0.0)), change))
    }
}
