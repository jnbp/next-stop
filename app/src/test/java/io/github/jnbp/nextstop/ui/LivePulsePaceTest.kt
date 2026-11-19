package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live pulse beats only for trains on the screen, and not again right after a frame of the vehicles carried it. */
class LivePulsePaceTest {
    @Test
    fun waitsOutTheRestOfAFrame() {
        assertEquals(0L, pulseWait(now = 1_000L, lastAt = 0L, frameMillis = 66L))
        assertEquals(46L, pulseWait(now = 1_020L, lastAt = 1_000L, frameMillis = 66L))
        assertEquals(0L, pulseWait(now = 1_066L, lastAt = 1_000L, frameMillis = 66L))
    }

    @Test
    fun aTrainInTheBoxOrJustBeyondItsEdgeCounts() {
        // A box of 0.1 by 0.2 degrees.
        assertTrue(inViewBox(52.05, 13.1, 52.0, 13.0, 52.1, 13.2))
        // 5 % beyond the north edge: inside the margin of 10 %.
        assertTrue(inViewBox(52.105, 13.1, 52.0, 13.0, 52.1, 13.2))
        assertFalse(inViewBox(52.2, 13.1, 52.0, 13.0, 52.1, 13.2))
        assertFalse(inViewBox(52.05, 13.5, 52.0, 13.0, 52.1, 13.2))
        assertFalse(inViewBox(Double.NaN, 13.1, 52.0, 13.0, 52.1, 13.2))
    }

    @Test
    fun aBoxAcrossTheDateLine() {
        assertTrue(inViewBox(0.0, 179.5, -1.0, 179.0, 1.0, -179.0))
        assertTrue(inViewBox(0.0, -179.5, -1.0, 179.0, 1.0, -179.0))
        assertFalse(inViewBox(0.0, 0.0, -1.0, 179.0, 1.0, -179.0))
    }
}
