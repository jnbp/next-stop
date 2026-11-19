package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/** The own dot after the map opens on a stale position: faded, then a glide to the first fresh fix (#159). */
class OwnCatchUpTest {
    private val baseLat = 52.40
    private val baseLon = 13.20
    private val perDegree = 111_320.0
    private val eastPerDegree = perDegree * cos(Math.toRadians(baseLat))

    private fun latOf(north: Double) = baseLat + north / perDegree
    private fun lonOf(east: Double) = baseLon + east / eastPerDegree
    private fun eastOf(motion: OwnMotion) = (motion.lon - baseLon) * eastPerDegree
    private fun northOf(motion: OwnMotion) = (motion.lat - baseLat) * perDegree

    @Test
    fun stale_without_a_fix_and_after_twenty_seconds() {
        assertTrue(ownPositionStale(null, 1_000L))
        assertFalse(ownPositionStale(1_000L, 15_000L))
        assertTrue(ownPositionStale(1_000L, 1_000L + OWN_STALE_MILLIS + 1))
    }

    @Test
    fun the_glide_eases_in_and_out_and_ends() {
        assertEquals(0.0, catchUpShare(0L, 750L), 1e-9)
        assertEquals(0.5, catchUpShare(375L, 750L), 1e-9)
        assertEquals(1.0, catchUpShare(750L, 750L), 1e-9)
        assertEquals(1.0, catchUpShare(10L, 0L), 1e-9)
        assertTrue(catchUpShare(100L, 750L) < 100.0 / 750.0)
    }

    @Test
    fun a_far_first_fix_glides_instead_of_jumping() {
        val motion = OwnMotion()
        // The page's last known position (stale), then the first fresh fix 3 km on (beyond the jump rule).
        motion.ease(latOf(0.0), lonOf(0.0), 0L)
        assertTrue(motion.staleAt(0L))
        motion.fix(latOf(0.0), lonOf(3_000.0), 10.0, Double.NaN, Double.NaN, 1_000L, 1_000L)
        assertFalse(motion.staleAt(1_000L))
        motion.evaluate(1_000L)
        assertEquals(0.0, eastOf(motion), 1.0)
        var last = eastOf(motion)
        var largest = 0.0
        var t = 1_016L
        while (t <= 1_000L + CATCH_UP_MILLIS) {
            motion.evaluate(t)
            largest = maxOf(largest, eastOf(motion) - last)
            assertTrue("never back", eastOf(motion) >= last - 1e-6)
            last = eastOf(motion)
            t += 16L
        }
        motion.evaluate(1_000L + CATCH_UP_MILLIS + 16L)
        assertEquals(3_000.0, eastOf(motion), 1.0)
        // About 750 ms of frames: no single frame takes the whole way.
        assertTrue("no teleport: $largest", largest < 300.0)
        assertFalse(motion.catchingUp)
    }

    @Test
    fun the_glide_follows_the_way_of_the_ride() {
        val motion = OwnMotion()
        // An L-shaped way: 1 km east, then 1 km north.
        val lats = doubleArrayOf(latOf(0.0), latOf(0.0), latOf(1_000.0))
        val lons = doubleArrayOf(lonOf(0.0), lonOf(1_000.0), lonOf(1_000.0))
        motion.setPath(lats, lons)
        motion.ease(latOf(0.0), lonOf(200.0), 0L)
        motion.fix(latOf(600.0), lonOf(1_000.0), 10.0, 0.0, Double.NaN, 1_000L, 1_000L)
        var t = 1_000L
        while (t <= 1_000L + CATCH_UP_MILLIS + 32L) {
            motion.evaluate(t)
            val onEastLeg = abs(northOf(motion)) < 1.0
            val onNorthLeg = abs(eastOf(motion) - 1_000.0) < 1.0
            assertTrue("on the rails at $t: ${eastOf(motion)}, ${northOf(motion)}", onEastLeg || onNorthLeg)
            t += 16L
        }
        assertEquals(600.0, northOf(motion), 1.0)
    }

    @Test
    fun the_glide_takes_the_animator_scale() {
        val motion = OwnMotion()
        motion.durationScale = 2f
        motion.ease(latOf(0.0), lonOf(0.0), 0L)
        motion.fix(latOf(0.0), lonOf(500.0), 10.0, Double.NaN, Double.NaN, 1_000L, 1_000L)
        motion.evaluate(1_000L + CATCH_UP_MILLIS)
        assertEquals(250.0, eastOf(motion), 1.0)
    }

    @Test
    fun fresh_fixes_after_it_ease_as_before() {
        val motion = OwnMotion()
        motion.fix(latOf(0.0), lonOf(0.0), 5.0, Double.NaN, Double.NaN, 0L, 0L)
        motion.fix(latOf(0.0), lonOf(20.0), 5.0, Double.NaN, Double.NaN, 1_000L, 1_000L)
        assertFalse(motion.catchingUp)
    }
}
