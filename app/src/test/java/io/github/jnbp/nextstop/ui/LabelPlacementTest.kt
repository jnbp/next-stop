package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The names and pills of a journey are placed anew only for a zoom, a turn, a tilt or a rest. */
class LabelPlacementTest {
    @Test
    fun nothingPlacedYetPlaces() {
        assertFalse(LabelPlacement().panOnly(14.0, 0.0, 0.0, 3))
    }

    @Test
    fun aPanMovesWhatIsPlaced() {
        val placement = LabelPlacement()
        placement.placed(14.0, 0.0, 0.0, 3)
        assertTrue(placement.panOnly(14.0, 0.0, 0.0, 3))
    }

    @Test
    fun zoomTurnTiltOrRestPlaceAnew() {
        val placement = LabelPlacement()
        placement.placed(14.0, 0.0, 0.0, 3)
        assertFalse(placement.panOnly(14.01, 0.0, 0.0, 3))
        assertFalse(placement.panOnly(14.0, 2.0, 0.0, 3))
        assertFalse(placement.panOnly(14.0, 0.0, 20.0, 3))
        assertFalse(placement.panOnly(14.0, 0.0, 0.0, 4))
    }

    @Test
    fun aResetPlacesAnew() {
        val placement = LabelPlacement()
        placement.placed(14.0, 0.0, 0.0, 3)
        placement.reset()
        assertFalse(placement.panOnly(14.0, 0.0, 0.0, 3))
    }

    @Test
    fun shiftMovesEveryPlace() {
        val xs = floatArrayOf(1f, 2f, 3f)
        val ys = floatArrayOf(10f, 20f, 30f)
        shiftPlaces(xs, ys, 3, 5f, -2f)
        assertEquals(listOf(6f, 7f, 8f), xs.toList())
        assertEquals(listOf(8f, 18f, 28f), ys.toList())
    }
}
