package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Next stop: get off" on the watch only within the last minutes; a long hop shows the time left. */
class GetOffSoonTest {
    private val arrival = 10_000_000_000L

    @Test
    fun soonOnlyInTheLastTenMinutes() {
        assertFalse(getOffSoon(arrival, arrival - 178 * 60_000L))
        assertFalse(getOffSoon(arrival, arrival - GET_OFF_SOON_MILLIS - 1))
        assertTrue(getOffSoon(arrival, arrival - GET_OFF_SOON_MILLIS))
        assertTrue(getOffSoon(arrival, arrival - 60_000L))
        assertTrue(getOffSoon(arrival, arrival + 60_000L))
    }
}
