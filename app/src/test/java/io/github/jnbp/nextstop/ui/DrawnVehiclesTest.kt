package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the followed trip's markers are drawn, for the grey of the way that must not run ahead of them (#119). */
class DrawnVehiclesTest {
    @Test
    fun aDrawnMarkerIsFoundUntilRemoved() {
        val out = DoubleArray(2)
        assertFalse(DrawnVehicles.locate("t-1", out))
        DrawnVehicles.put("t-1", 52.5, 13.3)
        assertTrue(DrawnVehicles.locate("t-1", out))
        assertEquals(52.5, out[0], 0.0)
        assertEquals(13.3, out[1], 0.0)
        DrawnVehicles.put("t-1", 52.6, 13.4)
        assertTrue(DrawnVehicles.locate("t-1", out))
        assertEquals(52.6, out[0], 0.0)
        DrawnVehicles.remove("t-1")
        assertFalse(DrawnVehicles.locate("t-1", out))
    }

    @Test
    fun theGlideStopsAtTheMarkerBehindTheTrack() {
        // A straight way of 1 km north; the split at 100 m, the track puts the vehicle at 300 m,
        // its marker (a live report) is drawn at 200 m: the grey goes to 200 m, not 300 m.
        val latLon = doubleArrayOf(52.0, 13.0, 52.0 + 1000.0 / 111_320.0, 13.0)
        val along = doubleArrayOf(0.0, 1000.0)
        val marker = alongNear(latLon, along, 52.0 + 200.0 / 111_320.0, 13.0, 100.0, 400.0)
        assertEquals(200.0, marker, 0.5)
    }
}
