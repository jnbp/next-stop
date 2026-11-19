package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** The companion map frames the next change while riding towards it (#137), and a fresh map at once (#122). */
class CompanionChangeFrameTest {
    private val base = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.ofHours(1))

    private fun leg(walking: Boolean, path: List<Pair<Double, Double>>) = Leg(
        line = if (walking) "" else "S1",
        product = if (walking) "" else "suburban",
        walking = walking,
        from = "A",
        to = "B",
        departure = base,
        plannedDeparture = null,
        arrival = base.plusMinutes(10),
        plannedArrival = null,
        departurePlatform = null,
        arrivalPlatform = null,
        direction = null,
        cancelled = false,
        path = path,
    )

    @Test
    fun riding_frames_the_change_walk_and_the_next_boarding() {
        val ride = leg(false, listOf(1.0 to 1.0, 1.5 to 1.5))
        val walk = leg(true, listOf(1.5 to 1.5, 1.51 to 1.52, 1.52 to 1.53))
        val next = leg(false, listOf(1.53 to 1.54, 2.0 to 2.0))
        val points = changeFramePoints(listOf(ride, walk, next), 0)
        assertEquals(listOf(1.5 to 1.5, 1.51 to 1.52, 1.52 to 1.53, 1.53 to 1.54), points)
    }

    @Test
    fun a_known_boarding_spot_wins_over_the_first_stop() {
        val ride = leg(false, listOf(1.0 to 1.0, 1.5 to 1.5))
        val next = leg(false, listOf(1.6 to 1.6, 2.0 to 2.0))
        val points = changeFramePoints(listOf(ride, next), 0) { 1.55 to 1.55 }
        assertEquals(listOf(1.5 to 1.5, 1.55 to 1.55), points)
    }

    @Test
    fun the_last_ride_and_walks_have_no_change() {
        val walk = leg(true, listOf(0.0 to 0.0, 1.0 to 1.0))
        val ride = leg(false, listOf(1.0 to 1.0, 1.5 to 1.5))
        val end = leg(true, listOf(1.5 to 1.5, 1.6 to 1.6))
        assertTrue(changeFramePoints(listOf(walk, ride, end), 1).isEmpty())
        assertTrue(changeFramePoints(listOf(walk, ride, end), 0).isEmpty())
        assertTrue(changeFramePoints(listOf(walk, ride, end), 7).isEmpty())
    }

    @Test
    fun a_long_change_walk_is_thinned_but_keeps_its_end() {
        val ride = leg(false, listOf(0.0 to 0.0, 1.0 to 1.0))
        val walk = leg(true, (0..200).map { 1.0 + it * 0.001 to 1.0 })
        val next = leg(false, listOf(1.2 to 1.0, 2.0 to 2.0))
        val points = changeFramePoints(listOf(ride, walk, next), 0)
        assertTrue(points.size <= 30)
        assertTrue(points.contains(1.2 to 1.0))
    }

    @Test
    fun a_new_map_facing_the_world_counts_as_fresh() {
        assertTrue(freshCamera(0.0))
        assertTrue(freshCamera(Double.NaN))
        assertFalse(freshCamera(11.5))
    }
}
