package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ways of one journey on the same course are told apart; others stay where they are. */
class RouteOverlapTest {
    // About 1.4 km east along a street, in 15 points.
    private val east = (0..14).map { 50.94 to 6.95 + it * 0.0014 }

    @Test
    fun aWayInAndBackIsFound() {
        val back = east.reversed()
        val elsewhere = (0..14).map { 50.90 + it * 0.001 to 6.90 }
        val found = overlappingWays(listOf(east, back, elsewhere))
        assertTrue(found[0])
        assertTrue(found[1])
        assertFalse(found[2])
    }

    @Test
    fun waysThatOnlyMeetAtAStationStay() {
        // A way that starts where the first one ends and goes north.
        val north = (0..14).map { 50.94 + it * 0.001 to east.last().second }
        val found = overlappingWays(listOf(east, north))
        assertFalse(found[0])
        assertFalse(found[1])
    }

    @Test
    fun shiftedToTheRightOfTheDirection() {
        val out = FloatArray(6)
        // Eastward on the screen (y down): its right is below.
        shiftRight(floatArrayOf(0f, 0f, 100f, 0f, 200f, 0f), 3, 4f, out)
        assertEquals(4f, out[1], 1e-4f)
        assertEquals(4f, out[5], 1e-4f)
        // The way back, westward: its right is above, so in and back stand apart.
        shiftRight(floatArrayOf(200f, 0f, 100f, 0f, 0f, 0f), 3, 4f, out)
        assertEquals(-4f, out[1], 1e-4f)
    }

    @Test
    fun aBendKeepsTheDistanceOfBothStretches() {
        val out = FloatArray(6)
        // East, then south: the corner moves out along the diagonal by distance * sqrt(2).
        shiftRight(floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f), 3, 4f, out)
        assertEquals(96f, out[2], 1e-3f)
        assertEquals(4f, out[3], 1e-3f)
        // A repeated point takes the normal of the stretch before.
        shiftRight(floatArrayOf(0f, 0f, 100f, 0f, 100f, 0f), 3, 4f, out)
        assertEquals(4f, out[5], 1e-4f)
    }

    @Test
    fun aJourneyAcrossTheCountryStaysCheapAndIsFound() {
        // About 600 km north, in 200 points, there and back.
        val long = (0..199).map { 47.5 + it * 0.027 to 10.0 }
        val found = overlappingWays(listOf(long, long.reversed(), east))
        assertTrue(found[0])
        assertTrue(found[1])
        assertFalse(found[2])
    }
}
