package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The own dot's frame loop takes about 60 frames a second, evenly, on any display. */
class FramePaceTest {
    private fun taken(hz: Double, seconds: Double): Int {
        val pace = FramePace()
        val interval = (1e9 / hz).toLong()
        var count = 0
        var t = 1_000_000_000L
        repeat((hz * seconds).toInt()) {
            if (pace.due(t)) count++
            t += interval
        }
        return count
    }

    @Test
    fun sixtyHertzKeepsEveryFrame() {
        assertEquals(120, taken(60.0, 2.0))
    }

    @Test
    fun ninetyHertzKeepsEveryFrameRatherThanFortyFive() {
        assertEquals(180, taken(90.0, 2.0))
    }

    @Test
    fun hundredTwentyHertzTakesEverySecondFrame() {
        val count = taken(120.0, 2.0)
        assertTrue("$count", count in 119..122)
    }

    @Test
    fun hundredFortyFourHertzStaysNearSeventyTwo() {
        val count = taken(144.0, 2.0)
        assertTrue("$count", count in 143..146)
    }

    @Test
    fun aFrameAfterAPauseIsTakenAtOnce() {
        val pace = FramePace()
        var t = 0L
        repeat(10) { pace.due(t); t += 8_333_333L }
        assertTrue(pace.due(t + 500_000_000L))
    }

    @Test
    fun aLateFrameDoesNotChangeTheMeasuredDisplay() {
        val pace = FramePace()
        var t = 1L
        repeat(10) { pace.due(t); t += 8_333_333L }
        val before = pace.stride()
        pace.due(t + 33_000_000L)
        assertEquals(before, pace.stride())
    }
}
