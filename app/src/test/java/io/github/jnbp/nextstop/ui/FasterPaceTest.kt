package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** The watch for a faster way searches often only while a change is near and the app is in front. */
class FasterPaceTest {
    private val base = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.ofHours(1))
    private val baseMillis = base.toInstant().toEpochMilli()

    private fun leg(fromMinute: Long, toMinute: Long, line: String?) = Leg(
        line = line,
        product = if (line == null) "walk" else "suburban",
        walking = line == null,
        from = "A",
        to = "B",
        departure = base.plusMinutes(fromMinute),
        plannedDeparture = null,
        arrival = base.plusMinutes(toMinute),
        plannedArrival = null,
        departurePlatform = null,
        arrivalPlatform = null,
        direction = null,
        cancelled = false,
    )

    private fun journey(vararg legs: Leg) = Journey(legs = legs.toList())

    @Test
    fun aChangeWithinFifteenMinutesIsNear() {
        val trip = journey(leg(0, 20, "S1"), leg(24, 40, "U1"))
        assertTrue(changeNear(trip, baseMillis + 10 * 60_000L))
        assertFalse(changeNear(trip, baseMillis))
    }

    @Test
    fun aSingleRideHasNoChange() {
        assertFalse(changeNear(journey(leg(0, 20, "S1")), baseMillis + 10 * 60_000L))
    }

    @Test
    fun aPastChangeIsNotNear() {
        val trip = journey(leg(0, 20, "S1"), leg(24, 80, "U1"))
        assertFalse(changeNear(trip, baseMillis + 30 * 60_000L))
    }

    @Test
    fun theShortPaceOnlyNearAChangeInFront() {
        assertEquals(FASTER_NEAR_ROUND_MILLIS, fasterRoundMillis(true, true))
        assertEquals(FASTER_CALM_ROUND_MILLIS, fasterRoundMillis(true, false))
        assertEquals(FASTER_CALM_ROUND_MILLIS, fasterRoundMillis(false, true))
        assertTrue(FASTER_CALM_ROUND_MILLIS in 120_000L..180_000L)
    }
}
