package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveAgeTest {
    private val at = 1_795_000_000_000L

    @Test
    fun stepsOfFiveSecondsThenMinutesThenStale() {
        assertNull(LiveAge.of(0L, at))
        assertEquals(LiveAge.JustNow, LiveAge.of(at, at + 4_900))
        assertEquals(LiveAge.Seconds(5), LiveAge.of(at, at + 5_000))
        assertEquals(LiveAge.Seconds(25), LiveAge.of(at, at + 29_999))
        assertEquals(LiveAge.Seconds(55), LiveAge.of(at, at + 59_000))
        assertEquals(LiveAge.Minutes(1), LiveAge.of(at, at + 61_000))
        assertEquals(LiveAge.Stale(1), LiveAge.of(at, at + 100_000))
        assertEquals(LiveAge.Stale(5), LiveAge.of(at, at + 300_000))
    }

    @Test
    fun clockBackIsJustNow() {
        assertEquals(LiveAge.JustNow, LiveAge.of(at, at - 20_000))
    }
}
