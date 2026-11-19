package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pace by bicycle: presets, limits, the parameter for the routing servers and minutes. */
class BikePaceTest {
    @Test
    fun presets() {
        assertEquals(0, BikePace.presetIndex(12))
        assertEquals(1, BikePace.presetIndex(16))
        assertEquals(2, BikePace.presetIndex(20))
        assertEquals(-1, BikePace.presetIndex(18))
    }

    @Test
    fun limits() {
        assertEquals(BikePace.MIN_KMH, BikePace.clamp(1))
        assertEquals(BikePace.MAX_KMH, BikePace.clamp(99))
        assertEquals(18, BikePace.clamp(18))
    }

    @Test
    fun speedParam() {
        assertEquals("3.3", BikePace.speedParam(12))
        assertEquals("4.4", BikePace.speedParam(16))
        assertEquals("5.6", BikePace.speedParam(20))
        assertEquals("11.1", BikePace.speedParam(500))
    }

    @Test
    fun minutes() {
        assertEquals(15, BikePace.minutesFor(4000.0, 16))
        assertEquals(12, BikePace.minutesFor(4000.0, 20))
        assertEquals(1, BikePace.minutesFor(10.0, 20))
        assertEquals(0, BikePace.minutesFor(0.0, 20))
    }
}
