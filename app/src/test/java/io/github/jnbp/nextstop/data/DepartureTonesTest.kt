package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The marks of departures, the same cases on the phone and the watch (see DepartureTones.kt). */
class DepartureTonesTest {
    @Test
    fun minutes() {
        assertEquals(MinutesTone.Cancelled, minutesTone(2, cancelled = true))
        assertEquals(MinutesTone.Gone, minutesTone(-1, cancelled = false))
        // "now" is highlighted like any departure within three minutes, late or not.
        assertEquals(MinutesTone.Soon, minutesTone(0, cancelled = false))
        assertEquals(MinutesTone.Soon, minutesTone(3, cancelled = false))
        assertEquals(MinutesTone.Plain, minutesTone(4, cancelled = false))
        assertEquals(MinutesTone.Plain, minutesTone(null, cancelled = false))
    }

    @Test
    fun clockTimes() {
        assertEquals(TimeTone.Scheduled, timeTone(realtime = false, delayMinutes = 0))
        assertEquals(TimeTone.Late, timeTone(realtime = true, delayMinutes = 4))
        assertEquals(TimeTone.OnTime, timeTone(realtime = true, delayMinutes = 0))
        assertEquals(TimeTone.OnTime, timeTone(realtime = true, delayMinutes = -1))
    }

    @Test
    fun delays() {
        assertEquals(DelayTone.Late, delayTone(realtime = true, delayMinutes = 4))
        assertEquals(DelayTone.Early, delayTone(realtime = true, delayMinutes = -1))
        assertNull(delayTone(realtime = true, delayMinutes = 0))
        assertNull(delayTone(realtime = false, delayMinutes = 4))
    }
}
