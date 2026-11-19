package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A sketch's loop goes on from where it stood and wraps around. */
class LoopPhaseTest {
    @Test
    fun movesByTheShareOfTheLoop() {
        assertEquals(0.4f, advanceLoop(0.3f, 700_000_000L, 7_000L), 1e-5f)
    }

    @Test
    fun wrapsAroundAtTheEnd() {
        assertEquals(0.1f, advanceLoop(0.9f, 1_400_000_000L, 7_000L), 1e-5f)
    }

    @Test
    fun noTimeOrNoLoopStandsStill() {
        assertEquals(0.3f, advanceLoop(0.3f, 0L, 7_000L), 0f)
        assertEquals(0.3f, advanceLoop(0.3f, 16_000_000L, 0L), 0f)
    }
}
