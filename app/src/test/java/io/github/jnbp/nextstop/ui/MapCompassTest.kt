package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the compass of the map shows (see MapCompass.kt). */
class MapCompassTest {
    @Test
    fun aMapFacingNorthHasNoCompass() {
        assertFalse(compassShown(0.0))
        assertFalse(compassShown(1.0))
        assertFalse(compassShown(-1.0))
        assertFalse(compassShown(359.0))
        assertFalse(compassShown(360.0))
        assertFalse(compassShown(720.5))
    }

    @Test
    fun aTurnedMapShowsIt() {
        assertTrue(compassShown(2.0))
        assertTrue(compassShown(-3.0))
        assertTrue(compassShown(357.0))
        assertTrue(compassShown(90.0))
        assertTrue(compassShown(180.0))
    }

    @Test
    fun theThresholdIsTheTolerance() {
        assertFalse(compassShown(NORTH_TOLERANCE_DEGREES))
        assertTrue(compassShown(NORTH_TOLERANCE_DEGREES + 0.01))
        assertTrue(compassShown(360.0 - NORTH_TOLERANCE_DEGREES - 0.01))
    }

    @Test
    fun noBearingNoCompass() {
        assertFalse(compassShown(Double.NaN))
        assertFalse(compassShown(Double.POSITIVE_INFINITY))
    }

    @Test
    fun theDistanceFromNorthTakesTheShorterWay() {
        assertEquals(10.0, northApart(370.0), 1e-9)
        assertEquals(10.0, northApart(350.0), 1e-9)
        assertEquals(10.0, northApart(-10.0), 1e-9)
        assertEquals(180.0, northApart(180.0), 1e-9)
        assertEquals(0.0, northApart(0.0), 1e-9)
    }
}
