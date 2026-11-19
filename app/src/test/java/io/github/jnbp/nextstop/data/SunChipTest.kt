package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sun chip of the compact ride card (#220): large while new, counted once per ride. */
class SunChipTest {
    @Test
    fun largeForTheFirstTenRides() {
        assertTrue(sunChipProminent(0))
        assertTrue(sunChipProminent(9))
        assertFalse(sunChipProminent(10))
        assertFalse(sunChipProminent(25))
    }

    @Test
    fun eachRideCountsOnce() {
        val key = sunChipRideKey("trip-1", "a", "b", 100)
        assertEquals(4, sunChipCount(emptySet(), key, 3))
        assertEquals(3, sunChipCount(setOf(key), key, 3))
        // Learnt: no more counting.
        assertEquals(SUN_CHIP_LEARN_RIDES, sunChipCount(emptySet(), key, SUN_CHIP_LEARN_RIDES))
    }

    @Test
    fun aRideWithoutTripIsKeyedByItsEnds() {
        assertEquals("trip-1", sunChipRideKey("trip-1", "a", "b", 100))
        assertEquals("a|b|100", sunChipRideKey(null, "a", "b", 100))
    }
}
