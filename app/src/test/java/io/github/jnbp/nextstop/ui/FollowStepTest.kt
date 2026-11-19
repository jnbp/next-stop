package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The camera following the own dot moves only by steps that can be seen. */
class FollowStepTest {
    // About 0.6 m per pixel: a city street zoom.
    private val scale = 0.6

    @Test
    fun aStepBelowAThirdOfAPixelWaits() {
        // 0.1 m north: a sixth of a pixel.
        assertFalse(followMoveDue(0.1 / 111_320.0, 0.0, 52.52, scale))
        assertFalse(followMoveDue(0.0, 0.0, 52.52, scale))
    }

    @Test
    fun aStepOfAPixelMoves() {
        assertTrue(followMoveDue(0.6 / 111_320.0, 0.0, 52.52, scale))
        // East counts by the latitude: 0.6 m east is more degrees than 0.6 m north.
        assertTrue(followMoveDue(0.0, 0.6 / (111_320.0 * 0.6085), 52.52, scale))
    }

    @Test
    fun smallStepsGatheredMoveOnceTheyAddUp() {
        var gathered = 0.0
        var moves = 0
        repeat(10) {
            gathered += 0.05 / 111_320.0
            if (followMoveDue(gathered, 0.0, 52.52, scale)) {
                moves++
                gathered = 0.0
            }
        }
        // 0.5 m in ten frames of 0.05 m: moved about once per 0.18 m, not ten times.
        assertTrue("moves $moves", moves in 1..3)
    }

    @Test
    fun withoutAScaleItMovesAsBefore() {
        assertTrue(followMoveDue(1e-8, 0.0, 52.52, 0.0))
    }
}
