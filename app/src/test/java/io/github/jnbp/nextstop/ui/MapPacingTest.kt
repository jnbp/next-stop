package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How often the glass picture is taken and the vehicles are put into their layer. */
class MapPacingTest {
    @Test
    fun aDotBesideAStopStandsRightOfItsPointOnTheScreen() {
        val into = DoubleArray(2)
        // North up: 20 dp to the right is to the east, the same latitude, 20 dp away.
        offsetByDp(52.5, 13.4, 20f, 0f, 13.0, 0.0, into)
        assertEquals(52.5, into[0], 1e-9)
        assertTrue(into[1] > 13.4)
        assertEquals(20.0, distanceDp(52.5, 13.4, into[0], into[1], 13.0), 1e-3)
        // Down on the screen is to the south.
        offsetByDp(52.5, 13.4, 0f, 10f, 13.0, 0.0, into)
        assertTrue(into[0] < 52.5)
        assertEquals(13.4, into[1], 1e-9)
        // The map turned so east is up: right on the screen is to the south.
        offsetByDp(52.5, 13.4, 20f, 0f, 13.0, 90.0, into)
        assertTrue(into[0] < 52.5)
        assertEquals(13.4, into[1], 1e-9)
        assertEquals(20.0, distanceDp(52.5, 13.4, into[0], into[1], 13.0), 1e-3)
    }

    @Test
    fun aDotMovedByItsPointDriftsWithTheZoom() {
        assertEquals(0.0, dotDriftDp(0f, 13.0, 14.0), 0.0)
        assertEquals(0.0, dotDriftDp(20f, Double.NaN, 14.0), 0.0)
        assertEquals(0.0, dotDriftDp(20f, 13.0, 13.0), 1e-9)
        assertEquals(20.0, dotDriftDp(20f, 13.0, 14.0), 1e-9)
        assertEquals(10.0, dotDriftDp(20f, 13.0, 12.0), 1e-9)
    }

    @Test
    fun distanceDpGrowsWithZoom() {
        // 100 m to the north at latitude 52.5.
        val north = 100.0 / 111_320.0
        val at13 = distanceDp(52.5, 13.4, 52.5 + north, 13.4, 13.0)
        val at15 = distanceDp(52.5, 13.4, 52.5 + north, 13.4, 15.0)
        assertEquals(at13 * 4, at15, 1e-6)
        // About 1.45 m per dp at zoom 15 there.
        assertEquals(100.0 / metersPerDp(15.0, 52.5), at15, 1e-6)
        assertEquals(0.0, distanceDp(52.5, 13.4, 52.5, 13.4, 15.0), 1e-9)
    }

    @Test
    fun bearingsApartAcrossNorth() {
        assertEquals(2.0, bearingApart(359.0, 1.0), 1e-9)
        assertEquals(180.0, bearingApart(0.0, 180.0), 1e-9)
        assertEquals(10.0, bearingApart(-5.0, 5.0), 1e-9)
    }

    @Test
    fun vehiclesNotDueForInvisibleSteps() {
        assertFalse(vehiclesDue(force = false, changed = false, movedDp = 0.2, cameraMoving = false, sinceLastMillis = 33))
        assertTrue(vehiclesDue(force = false, changed = false, movedDp = 0.6, cameraMoving = false, sinceLastMillis = 33))
        assertTrue(vehiclesDue(force = false, changed = true, movedDp = 0.0, cameraMoving = false, sinceLastMillis = 0))
    }

    @Test
    fun vehiclesSpacedWhileCameraMoves() {
        assertFalse(vehiclesDue(force = false, changed = true, movedDp = 5.0, cameraMoving = true, sinceLastMillis = 50))
        assertTrue(vehiclesDue(force = false, changed = true, movedDp = 5.0, cameraMoving = true, sinceLastMillis = 120))
        // A new style or the layer shown again: always.
        assertTrue(vehiclesDue(force = true, changed = false, movedDp = 0.0, cameraMoving = true, sinceLastMillis = 0))
    }

    @Test
    fun vehiclesSpacedMoreWhileTheMapIsFresh() {
        val fresh = VEHICLE_FRESH_CAMERA_MILLIS
        assertFalse(vehiclesDue(force = false, changed = true, movedDp = 5.0, cameraMoving = true, sinceLastMillis = 120, movingEveryMillis = fresh))
        assertTrue(vehiclesDue(force = false, changed = true, movedDp = 5.0, cameraMoving = true, sinceLastMillis = fresh, movingEveryMillis = fresh))
        // At rest the spacing does not apply.
        assertTrue(vehiclesDue(force = false, changed = true, movedDp = 5.0, cameraMoving = false, sinceLastMillis = 0, movingEveryMillis = fresh))
    }

    @Test
    fun backdropFastAfterRestThenCalm() {
        assertEquals(BACKDROP_FAST_MILLIS, backdropPause(0))
        assertEquals(BACKDROP_FAST_MILLIS, backdropPause(BACKDROP_SETTLE_MILLIS - 1))
        assertEquals(BACKDROP_CALM_MILLIS, backdropPause(BACKDROP_SETTLE_MILLIS))
    }

    @Test
    fun backdropFollowsOnlyFlatAndUnturned() {
        assertTrue(backdropCanFollow(tilt = 0.0, bearing = 30.0, takenTilt = 0.0, takenBearing = 30.0))
        assertFalse(backdropCanFollow(tilt = 0.0, bearing = 31.0, takenTilt = 0.0, takenBearing = 30.0))
        assertFalse(backdropCanFollow(tilt = 40.0, bearing = 0.0, takenTilt = 40.0, takenBearing = 0.0))
        assertTrue(backdropCanFollow(tilt = 0.0, bearing = 359.99, takenTilt = 0.0, takenBearing = 0.0))
    }

    @Test
    fun backdropGrowsWithZoom() {
        assertEquals(1.0, backdropGrow(15.0, 15.0).toDouble(), 1e-6)
        assertEquals(2.0, backdropGrow(16.0, 15.0).toDouble(), 1e-6)
        assertEquals(0.5, backdropGrow(14.0, 15.0).toDouble(), 1e-6)
    }
}
