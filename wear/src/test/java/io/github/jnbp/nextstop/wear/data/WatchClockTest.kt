package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #148b: the watch's countdown turns into the clock time from an hour on, as on the phone. */
class WatchClockTest {
    private val minute = 60_000L

    @Test
    fun clockFromAnHourOn() {
        assertTrue(watchShowsClock(179 * minute))
        assertTrue(watchShowsClock(60 * minute))
        // 59 min 40 s are 59 full minutes, as the phone counts them.
        assertFalse(watchShowsClock(59 * minute + 40_000))
        assertFalse(watchShowsClock(59 * minute))
        assertFalse(watchShowsClock(45 * minute))
    }

    @Test
    fun noFlickerAtTheBoundary() {
        assertTrue(watchShowsClock(59 * minute, showingClock = true))
        assertFalse(watchShowsClock(58 * minute, showingClock = true))
    }

    @Test
    fun theMinutesAreTheFullMinutesLeftAsOnThePhone() {
        // The phone counts minutesBetween (whole minutes, cut): 4 min 50 s is "4", the last minute "now".
        assertEquals(4L, watchMinutesLeft(4 * minute + 50_000))
        assertEquals(4L, minutesUntil(1_000_000L + 4 * minute + 50_000, 1_000_000L))
        assertEquals(0L, minutesUntil(1_000_000L + 59_000, 1_000_000L))
        assertEquals(1L, minutesUntil(1_000_000L + minute, 1_000_000L))
        assertEquals(0L, minutesUntil(1_000_000L - minute, 1_000_000L))
    }
}
