package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFramingTest {
    @Test
    fun withoutBandTheAreaIsCentred() {
        val framed = frameAboveBand(0.5, 0.5, 0.001, 0.001, 400, 400, 20.0)
        assertEquals(0.5, framed.y, 1e-12)
        assertEquals(360_000.0, framed.pixelsPerUnit, 1e-6)
    }

    @Test
    fun theAreaSitsAboveTheBand() {
        val width = 400
        val height = 400
        val pad = 20.0
        val band = 60.0
        val framed = frameAboveBand(0.5, 0.5, 0.001, 0.001, width, height, pad, band)
        // Smaller, so it fits into what the band leaves.
        assertEquals((height - 2 * pad - band) / 0.001, framed.pixelsPerUnit, 1e-6)
        // The foot of the area (y grows southwards) on screen stays above the band.
        val foot = height / 2.0 + (0.5 + 0.0005 - framed.y) * framed.pixelsPerUnit
        assertTrue("foot $foot", foot <= height - pad - band + 1e-6)
        val top = height / 2.0 + (0.5 - 0.0005 - framed.y) * framed.pixelsPerUnit
        assertEquals(pad, top, 1e-6)
    }

    @Test
    fun aBandTooHighShrinks() {
        val framed = frameAboveBand(0.5, 0.5, 0.001, 0.001, 200, 200, 20.0, 500.0)
        assertEquals(MIN_FRAMED_PIXELS / 0.001, framed.pixelsPerUnit, 1e-6)
    }
}
