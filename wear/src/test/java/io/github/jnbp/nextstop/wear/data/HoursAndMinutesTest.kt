package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Long waits read as hours and minutes on the watch, never as one big number of minutes. */
class HoursAndMinutesTest {
    @Test
    fun belowAnHourMinutesAlone() {
        assertEquals(null to 0L, hoursAndMinutes(0))
        assertEquals(null to 59L, hoursAndMinutes(59))
        assertEquals(null to 0L, hoursAndMinutes(-3))
    }

    @Test
    fun fromAnHourHoursAndMinutes() {
        assertEquals(2L to 23L, hoursAndMinutes(143))
        assertEquals(1L to null, hoursAndMinutes(60))
        assertEquals(2L to null, hoursAndMinutes(120))
        assertEquals(1L to 1L, hoursAndMinutes(61))
    }
}
