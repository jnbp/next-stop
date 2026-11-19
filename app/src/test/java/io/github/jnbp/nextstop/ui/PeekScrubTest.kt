package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which connection the scrubbing finger is over (see scrubHit in PeekScrub.kt). */
class PeekScrubTest {
    // Three columns 100 wide with gaps of 8: 0–100, 108–208, 216–316.
    private val starts = floatArrayOf(0f, 108f, 216f)
    private val ends = floatArrayOf(100f, 208f, 316f)

    @Test
    fun insideAColumn() {
        assertEquals(0, scrubHit(0f, starts, ends))
        assertEquals(0, scrubHit(99.9f, starts, ends))
        assertEquals(1, scrubHit(150f, starts, ends))
        assertEquals(2, scrubHit(315f, starts, ends))
    }

    @Test
    fun gapsAndOutsideHoldNothing() {
        assertEquals(-1, scrubHit(100f, starts, ends))
        assertEquals(-1, scrubHit(104f, starts, ends))
        assertEquals(-1, scrubHit(-5f, starts, ends))
        assertEquals(-1, scrubHit(316f, starts, ends))
    }

    @Test
    fun anyOrderAndEmptyBounds() {
        // Rows registered out of order still answer by place.
        assertEquals(1, scrubHit(50f, floatArrayOf(200f, 0f), floatArrayOf(300f, 100f)))
        // A row of no height (not laid out) never holds the finger.
        assertEquals(-1, scrubHit(10f, floatArrayOf(10f), floatArrayOf(10f)))
        assertEquals(-1, scrubHit(10f, FloatArray(0), FloatArray(0)))
    }
}
