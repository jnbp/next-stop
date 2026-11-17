package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pills of stop positions at a change never lie on top of each other (see placeMarkPills). */
class MarkPillsTest {
    private fun place(xs: FloatArray, ys: FloatArray, halfWidths: FloatArray): FloatArray =
        FloatArray(xs.size * 2).also { placeMarkPills(xs, ys, halfWidths, 8f, 4f, 2f, it) }

    private fun box(xs: FloatArray, ys: FloatArray, halfWidths: FloatArray, out: FloatArray, i: Int): FloatArray {
        val x = xs[i] + out[i * 2]
        val y = ys[i] + out[i * 2 + 1]
        return floatArrayOf(x - halfWidths[i], y - 8f, x + halfWidths[i], y + 8f)
    }

    private fun overlap(a: FloatArray, b: FloatArray) = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

    @Test
    fun aLonePillStaysOnItsPosition() {
        val out = place(floatArrayOf(100f), floatArrayOf(100f), floatArrayOf(12f))
        assertEquals(0f, out[0])
        assertEquals(0f, out[1])
    }

    @Test
    fun pillsFarApartStayOnTheirPositions() {
        val out = place(floatArrayOf(100f, 300f), floatArrayOf(100f, 100f), floatArrayOf(12f, 12f))
        assertEquals(0f, out[2])
        assertEquals(0f, out[3])
    }

    @Test
    fun crowdedPillsNeverOverlap() {
        // Four positions of one stop a few pixels apart, as "Pos. 3", "2", "A" and "F" were.
        val xs = floatArrayOf(100f, 106f, 103f, 110f)
        val ys = floatArrayOf(100f, 103f, 108f, 98f)
        val widths = floatArrayOf(12f, 12f, 12f, 12f)
        val out = place(xs, ys, widths)
        // The first pill always finds a place (moved off the dots of the others).
        assertFalse(out[0].isNaN())
        assertFalse(out[1].isNaN())
        val shown = xs.indices.filter { !out[it * 2].isNaN() }
        assertTrue(shown.size >= 2)
        for (a in shown) for (b in shown) {
            if (a < b) assertFalse(overlap(box(xs, ys, widths, out, a), box(xs, ys, widths, out, b)))
        }
    }

    @Test
    fun aMarkWithoutPillPlacesNothing() {
        val out = place(floatArrayOf(100f), floatArrayOf(100f), floatArrayOf(0f))
        assertTrue(out[0].isNaN())
    }
}
