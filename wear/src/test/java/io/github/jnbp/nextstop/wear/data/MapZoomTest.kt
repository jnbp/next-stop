package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MapZoomTest {
    @Test
    fun stepsGoFromTheFramingToFourTimes() {
        assertEquals(1.0, MapZoom.factorOf(0), 1e-9)
        assertEquals(2.0, MapZoom.factorOf(4), 1e-9)
        assertEquals(8, MapZoom.maxStep)
        assertEquals(4.0, MapZoom.factorOf(MapZoom.maxStep), 1e-9)
        assertEquals(MapZoom.maxStep, MapZoom.stepBy(7, 5))
        assertEquals(0, MapZoom.stepBy(1, -3))
    }

    @Test
    fun crownPixelsAddUpToNotches() {
        val (none, left) = MapZoom.notches(MapZoom.PIXELS_PER_STEP * 0.5f)
        assertEquals(0, none)
        val (one, rest) = MapZoom.notches(left + MapZoom.PIXELS_PER_STEP * 0.75f)
        assertEquals(1, one)
        assertEquals(MapZoom.PIXELS_PER_STEP * 0.25f, rest, 1e-3f)
        assertEquals(-2, MapZoom.notches(-MapZoom.PIXELS_PER_STEP * 2.1f).first)
    }

    @Test
    fun sharperPictureOnlyAboveTwice() {
        assertEquals(1.0, MapZoom.requestFactor(1.0), 1e-9)
        assertEquals(1.0, MapZoom.requestFactor(2.0), 1e-9)
        assertEquals(Math.sqrt(8.0), MapZoom.requestFactor(MapZoom.factorOf(5)), 1e-9)
        assertEquals(Math.sqrt(8.0), MapZoom.requestFactor(MapZoom.factorOf(6)), 1e-9)
        assertEquals(4.0, MapZoom.requestFactor(MapZoom.factorOf(7)), 1e-9)
        assertEquals(4.0, MapZoom.requestFactor(4.0), 1e-9)
        // Few distinct requests over all notches: the crown does not ask on every one.
        val asked = (0..MapZoom.maxStep).map { MapZoom.requestFactor(MapZoom.factorOf(it)) }.toSet()
        assertEquals(3, asked.size)
    }

    @Test
    fun panStaysInsideTheFraming() {
        assertEquals(0.0 to 0.0, MapZoom.clampPan(5.0, -5.0, 1.0, 400, 400, 100.0))
        // At 2x the view's middle may move a quarter of the framing to each side.
        val (x, y) = MapZoom.clampPan(5.0, -5.0, 2.0, 400, 400, 100.0)
        assertEquals(1.0, x, 1e-9)
        assertEquals(-1.0, y, 1e-9)
        val inside = MapZoom.clampPan(0.3, 0.2, 4.0, 400, 400, 100.0)
        assertEquals(0.3, inside.first, 1e-9)
        assertEquals(0.2, inside.second, 1e-9)
    }
}
