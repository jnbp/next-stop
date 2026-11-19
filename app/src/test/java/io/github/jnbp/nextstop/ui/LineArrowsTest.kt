package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chevrons along a way: evenly spaced, in its direction, only on screen and only where it is drawn. */
class LineArrowsTest {
    private fun arrows(places: FloatArray, reach: Float, width: Float = 1000f, height: Float = 1000f): List<FloatArray> {
        val found = ArrayList<FloatArray>()
        walkArrows(places, places.size / 2, reach, 100f, width, height, 10f) { x, y, dx, dy -> found.add(floatArrayOf(x, y, dx, dy)) }
        return found
    }

    @Test
    fun evenlySpacedAcrossCorners() {
        // Right 300 px, then down 300 px.
        val found = arrows(floatArrayOf(0f, 0f, 300f, 0f, 300f, 300f), reach = 2f)
        assertEquals(6, found.size)
        assertEquals(50f, found[0][0], 0.01f)
        assertEquals(250f, found[2][0], 0.01f)
        // The fourth lies 50 px down the second stretch and points down.
        assertEquals(300f, found[3][0], 0.01f)
        assertEquals(50f, found[3][1], 0.01f)
        assertEquals(0f, found[3][2], 0.001f)
        assertEquals(1f, found[3][3], 0.001f)
    }

    @Test
    fun onlyAsFarAsTheWayHasDrawnItself() {
        val way = floatArrayOf(0f, 0f, 1000f, 0f)
        assertEquals(0, arrows(way, reach = 0f).size)
        // 40 % of the way: chevrons at 50, 150, 250, 350.
        assertEquals(4, arrows(way, reach = 0.4f).size)
    }

    @Test
    fun offScreenStretchesAreSkippedButKeepTheSpacing() {
        // From far left onto the screen: the chevrons on screen stand where they would anyway.
        val found = arrows(floatArrayOf(-5000f, 500f, -100f, 500f, 400f, 500f), reach = 2f)
        assertTrue(found.isNotEmpty())
        for (arrow in found) {
            assertTrue(arrow[0] >= -10f)
            // 50 + k * 100 from the start at -5000.
            val along = arrow[0] + 5000f
            assertEquals(50f, ((along - 50f) % 100f + 50f), 0.05f)
        }
        assertEquals(4, found.size)
    }

    @Test
    fun inkStandsOutFromTheLine() {
        assertEquals(ARROW_LIGHT_INK, arrowInkArgb(0xFF0664AB.toInt()))
        assertEquals(ARROW_LIGHT_INK, arrowInkArgb(0xFFBE1414.toInt()))
        assertEquals(ARROW_DARK_INK, arrowInkArgb(0xFFFFD800.toInt()))
        assertEquals(ARROW_DARK_INK, arrowInkArgb(0xFFFFFFFF.toInt()))
    }

    @Test
    fun appearsWithTheEndOfTheLine() {
        assertEquals(0f, arrowAppear(0.3f), 0f)
        assertEquals(1f, arrowAppear(1f), 0f)
        assertTrue(arrowAppear(0.8f) in 0.1f..0.9f)
    }
}
