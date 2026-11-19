package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The legs of a journey draw themselves in one after the other (#69, see RouteSnake.kt). */
class RouteSnakeTest {
    @Test
    fun legsFollowEachOtherByTheirLength() {
        val bounds = snakeBounds(doubleArrayOf(3_000.0, 1_000.0))
        assertEquals(0f, bounds[0], 0.0001f)
        assertEquals(0.75f, bounds[1], 0.0001f)
        assertEquals(0.75f, bounds[2], 0.0001f)
        assertEquals(1f, bounds[3], 0.0001f)
        // The first leg draws while the second waits; then the second one draws.
        assertEquals(0.5f, snakeShare(bounds, 0, 0.375f), 0.0001f)
        assertEquals(0f, snakeShare(bounds, 1, 0.375f), 0.0001f)
        assertEquals(1f, snakeShare(bounds, 0, 0.875f), 0.0001f)
        assertEquals(0.5f, snakeShare(bounds, 1, 0.875f), 0.0001f)
        assertEquals(1f, snakeShare(bounds, 1, 1f), 0.0001f)
        assertEquals(0f, snakeShare(bounds, 0, 0f), 0.0001f)
    }

    @Test
    fun aShortWalkStillGetsItsShare() {
        val bounds = snakeBounds(doubleArrayOf(50.0, 10_000.0, 30.0))
        for (index in 0..2) assertTrue(bounds[index * 2 + 1] - bounds[index * 2] >= SNAKE_MIN_SHARE * 0.9f)
        assertEquals(1f, bounds[5], 0.0f)
    }

    @Test
    fun theDrawInIsContinuousAndEndsWhole() {
        val bounds = snakeBounds(doubleArrayOf(400.0, 2_500.0, 0.0, 1_200.0, 90.0))
        var before = 0f
        for (index in 0 until 5) {
            assertEquals(before, bounds[index * 2], 0.0001f)
            before = bounds[index * 2 + 1]
        }
        assertEquals(1f, before, 0.0f)
        // The total share drawn only grows with the draw-in.
        var last = -1f
        var step = 0f
        while (step <= 1f) {
            val sum = (0 until 5).sumOf { snakeShare(bounds, it, step).toDouble() }.toFloat()
            assertTrue(sum >= last - 0.0001f)
            last = sum
            step += 0.01f
        }
        for (index in 0 until 5) assertEquals(1f, snakeShare(bounds, index, 1f), 0.0f)
    }

    @Test
    fun legsOfNoLengthEndTogether() {
        val bounds = snakeBounds(doubleArrayOf(0.0, 0.0))
        assertEquals(1f, snakeShare(bounds, 0, 1f), 0.0f)
        assertEquals(1f, snakeShare(bounds, 1, 1f), 0.0f)
        assertEquals(0, snakeBounds(DoubleArray(0)).size)
    }

    @Test
    fun aLongerJourneyTakesALittleLonger() {
        assertEquals(650, snakeMillis(650, 1))
        assertEquals(830, snakeMillis(650, 3))
        assertEquals(950, snakeMillis(650, 12))
    }

    @Test
    fun wayLengthIsAboutRight() {
        // A tenth of a degree north: about 11 km.
        val meters = wayMeters(listOf(52.0 to 13.0, 52.1 to 13.0))
        assertEquals(11_132.0, meters, 1.0)
    }
}
