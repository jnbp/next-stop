package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the map engine may be given (#284): no NaN, no point beyond the poles, no line of one point. */
class MapGuardsTest {
    @Test
    fun placesOnTheEarthOnly() {
        assertTrue(mapPointOk(52.5251, 13.3694))
        assertTrue(mapPointOk(-90.0, 180.0))
        assertFalse(mapPointOk(Double.NaN, 13.0))
        assertFalse(mapPointOk(52.0, Double.POSITIVE_INFINITY))
        assertFalse(mapPointOk(91.0, 13.0))
        assertFalse(mapPointOk(52.0, -180.5))
    }

    @Test
    fun aLineKeepsItsPlacesWithoutRepeats() {
        val berlin = 52.5251 to 13.3694
        val spandau = 52.5345 to 13.1968
        val hamburg = 53.5530 to 10.0066
        assertEquals(listOf(berlin, spandau, hamburg), mapLine(listOf(berlin, berlin, Double.NaN to 13.0, spandau, hamburg, hamburg)))
        // One place, or one place twice, is no line.
        assertNull(mapLine(listOf(berlin)))
        assertNull(mapLine(listOf(berlin, berlin)))
        assertNull(mapLine(listOf(berlin, Double.NaN to Double.NaN)))
        assertNull(mapLine(emptyList()))
    }

    @Test
    fun numbersThatAreNoneFallBack() {
        assertEquals(0.0, mapNumber(Double.NaN), 0.0)
        assertEquals(1.0, mapNumber(Double.NEGATIVE_INFINITY, 1.0), 0.0)
        assertEquals(-3.5, mapNumber(-3.5), 0.0)
        assertEquals(15.0, mapZoom(Double.NaN, 15.0), 0.0)
        assertEquals(MAP_GUARD_MAX_ZOOM, mapZoom(40.0, 15.0), 0.0)
        assertEquals(0.0, mapZoom(-2.0, 15.0), 0.0)
        assertEquals(12.4, mapZoom(12.4, 15.0), 0.0)
    }

    @Test
    fun aCameraWithAValueThatIsNoNumberStays() {
        assertTrue(cameraOk(52.5, 13.4, 12.0, 0.0, 0.0))
        assertTrue(cameraOk(52.5, 13.4, 12.0, 350.0, 30.0, doubleArrayOf(0.0, 230.0, 0.0, 340.0)))
        // newLatLng keeps zoom and tilt (MapLibre's -1); a camera that keeps its target has none.
        assertTrue(cameraOk(52.5, 13.4, MAP_GUARD_KEEP, MAP_GUARD_KEEP, MAP_GUARD_KEEP))
        assertTrue(cameraOk(Double.NaN, Double.NaN, 10.0, 0.0, 0.0))
        // A frame worked out for no room: a NaN zoom (#284).
        assertFalse(cameraOk(52.5, 13.4, Double.NaN, 0.0, 0.0))
        assertFalse(cameraOk(52.5, 13.4, Double.NEGATIVE_INFINITY, 0.0, 0.0))
        assertFalse(cameraOk(52.5, 13.4, 30.0, 0.0, 0.0))
        assertFalse(cameraOk(52.5, 13.4, 12.0, Double.NaN, 0.0))
        assertFalse(cameraOk(52.5, 13.4, 12.0, 0.0, 75.0))
        assertFalse(cameraOk(52.5, Double.NaN, 12.0, 0.0, 0.0))
        assertFalse(cameraOk(95.0, 13.4, 12.0, 0.0, 0.0))
        assertFalse(cameraOk(52.5, 13.4, 12.0, 0.0, 0.0, doubleArrayOf(0.0, Double.NaN, 0.0, 0.0)))
        assertFalse(cameraOk(52.5, 13.4, 12.0, 0.0, 0.0, doubleArrayOf(0.0, -20.0, 0.0, 0.0)))
    }
}
