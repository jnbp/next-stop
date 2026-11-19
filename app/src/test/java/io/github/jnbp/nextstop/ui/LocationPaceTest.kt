package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The start page and the map tab ask for a fix every second only while the user moves. */
class LocationPaceTest {
    private val lat = 52.40
    private val lon = 13.20
    private fun north(metres: Double) = lat + metres / 111_320.0

    @Test
    fun standingWithJitterStaysStanding() {
        val watch = MovementWatch()
        assertFalse(watch.fix(0L, lat, lon, 0.2f, 8f))
        assertFalse(watch.fix(5_000L, north(4.0), lon, 0.3f, 8f))
        assertFalse(watch.fix(10_000L, north(7.0), lon, Float.NaN, 8f))
    }

    @Test
    fun speedSwitchesToMovingAtOnce() {
        val watch = MovementWatch()
        watch.fix(0L, lat, lon, 0f, 5f)
        assertTrue(watch.fix(5_000L, lat, lon, 1.2f, 5f))
    }

    @Test
    fun displacementWithoutSpeedCountsAsMoving() {
        val watch = MovementWatch()
        watch.fix(0L, lat, lon, Float.NaN, 5f)
        assertTrue(watch.fix(5_000L, north(10.0), lon, Float.NaN, 5f))
    }

    @Test
    fun aWideAccuracyNeedsAWiderWay() {
        val watch = MovementWatch()
        watch.fix(0L, lat, lon, Float.NaN, 30f)
        assertFalse(watch.fix(5_000L, north(20.0), lon, Float.NaN, 30f))
    }

    @Test
    fun standsAgainAWhileAfterTheLastSignOfMoving() {
        val watch = MovementWatch()
        watch.fix(0L, lat, lon, 1.5f, 5f)
        assertTrue(watch.fix(1_000L, north(1.5), lon, 1.5f, 5f))
        assertTrue(watch.moving(1_000L + STANDING_AFTER_MILLIS - 1))
        assertFalse(watch.fix(1_000L + STANDING_AFTER_MILLIS, north(1.5), lon, 0.1f, 5f))
    }

    @Test
    fun theAskFollowsTheMovement() {
        val base = LocationAsk(1_000L, 0f, true)
        assertEquals(LocationAsk(1_000L, 0f, true), dotLocationAsk(true, true, 1_000L, true, base))
        assertEquals(LocationAsk(STANDING_INTERVAL_MILLIS, STANDING_MIN_METERS, false), dotLocationAsk(true, false, 1_000L, true, base))
        assertEquals(base, dotLocationAsk(false, false, 1_000L, true, base))
    }
}
