package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The end of the grey of a ride glides between two splits of its way. */
class RouteGlideTest {
    // A way going north from 52.4, 13.2: points 100 m apart.
    private val path = (0..20).map { (52.4 + it * 100.0 / 111_320.0) to 13.2 }
    private val latLon = DoubleArray(path.size * 2) { if (it % 2 == 0) path[it / 2].first else path[it / 2].second }
    private val along = alongMetres(path)

    @Test
    fun metresAlongTheWay() {
        assertEquals(0.0, along[0], 0.0)
        assertEquals(2_000.0, along.last(), 1e-6)
        assertEquals(350.0, splitAlong(along, 3, 0.5), 1e-6)
    }

    @Test
    fun aPieceHasItsEndsAndTheCornersBetween() {
        val out = DoubleArray(16)
        val count = glidePiece(latLon, along, 150.0, 420.0, out)
        // 150 m, the points at 200, 300 and 400 m, 420 m.
        assertEquals(5, count)
        assertEquals(52.4 + 150.0 / 111_320.0, out[0], 1e-9)
        assertEquals(52.4 + 300.0 / 111_320.0, out[4], 1e-9)
        assertEquals(52.4 + 420.0 / 111_320.0, out[8], 1e-9)
    }

    @Test
    fun aPieceWithinOneStretchIsTwoPoints() {
        val out = DoubleArray(16)
        assertEquals(2, glidePiece(latLon, along, 110.0, 130.0, out))
        assertEquals(0, glidePiece(latLon, along, 130.0, 130.0, out))
    }

    @Test
    fun theVehicleIsFoundAheadOfTheSplitOnly() {
        // 20 m east of the way at 730 m along it.
        val lat = 52.4 + 730.0 / 111_320.0
        val lon = 13.2 + 20.0 / (111_320.0 * Math.cos(Math.toRadians(lat)))
        assertEquals(730.0, alongNear(latLon, along, lat, lon, 600.0, 500.0), 0.5)
        // Behind the split it stays at the split: the grey never goes back.
        assertEquals(800.0, alongNear(latLon, along, lat, lon, 800.0, 500.0), 0.0)
        // Out of reach: the end of the last stretch looked at (the one that begins at the reach).
        assertEquals(500.0, alongNear(latLon, along, lat, lon, 100.0, 300.0), 0.5)
    }

    @Test
    fun anUpdateCostsMicroseconds() {
        // The work of one update of the glide: the piece of the way (the projection of its few
        // points and the drawing follow on the device). Warm up, then measure.
        val long = (0..2_000).map { (52.4 + it * 10.0 / 111_320.0) to 13.2 }
        val ll = DoubleArray(long.size * 2) { if (it % 2 == 0) long[it / 2].first else long[it / 2].second }
        val al = alongMetres(long)
        val out = DoubleArray(64)
        var sink = 0
        repeat(20_000) { sink += glidePiece(ll, al, 15_000.0 + it % 100, 15_040.0 + it % 100, out) }
        val runs = 100_000
        val start = System.nanoTime()
        repeat(runs) { sink += glidePiece(ll, al, 15_000.0 + it % 100, 15_040.0 + it % 100, out) }
        val perPiece = (System.nanoTime() - start) / runs
        var found = 0.0
        val lat = 52.4 + 15_030.0 / 111_320.0
        repeat(20_000) { found += alongNear(ll, al, lat, 13.2, 15_000.0, 500.0) }
        val startNear = System.nanoTime()
        repeat(runs) { found += alongNear(ll, al, lat, 13.2, 15_000.0 + it % 10, 500.0) }
        val perNear = (System.nanoTime() - startNear) / runs
        println("way of ${long.size} points: alongNear $perNear ns, glidePiece $perPiece ns per update (sink $sink $found)")
        assertTrue(perPiece < 50_000 && perNear < 50_000)
    }
}
