package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The last minute of a countdown in seconds, as on the watch, and when its label changes. */
class LastMinuteTest {
    @Test
    fun secondsOnlyWithinTheLastMinute() {
        assertNull(lastMinuteSeconds(60_000L))
        assertEquals(60, lastMinuteSeconds(59_001L))
        assertEquals(45, lastMinuteSeconds(44_200L))
        assertEquals(45, lastMinuteSeconds(45_000L))
        assertEquals(1, lastMinuteSeconds(1L))
        // The moment itself and after it: "now" or the minutes gone, as before.
        assertNull(lastMinuteSeconds(0L))
        assertNull(lastMinuteSeconds(-5_000L))
    }

    @Test
    fun theClockWakesWhenTheLabelChanges() {
        val target = 1_000_000L
        // Before the last minute: once, when it begins.
        assertEquals(target - 60_000L, lastMinuteWakeMillis(target - 300_000L, target))
        // Within it: when the rounded-up seconds go down by one.
        val now = target - 45_500L
        val wake = lastMinuteWakeMillis(now, target)
        assertEquals(now + 500L, wake)
        assertEquals(45, lastMinuteSeconds(target - wake))
        assertEquals(46, lastMinuteSeconds(target - (wake - 1L)))
        assertEquals(target, lastMinuteWakeMillis(target - 700L, target))
        // Past it: no wake of its own.
        assertEquals(Long.MAX_VALUE, lastMinuteWakeMillis(target, target))
    }
}
