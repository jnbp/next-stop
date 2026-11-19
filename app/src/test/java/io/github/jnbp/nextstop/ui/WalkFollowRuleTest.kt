package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** The big map of a walk follows the traveller only on the walk, else it frames the walk and stands still (#276). */
class WalkFollowRuleTest {
    private val base = OffsetDateTime.of(2026, 3, 2, 8, 0, 0, 0, ZoneOffset.ofHours(1))

    private fun leg(walking: Boolean, fromMinute: Long, toMinute: Long, line: String? = null) = Leg(
        line = line,
        product = if (walking) "walking" else "regional",
        walking = walking,
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

    private val train = leg(false, 0, 120, "RJ 1")
    private val walk = leg(true, 125, 131)
    private val journey = Journey(legs = listOf(train, walk))

    @Test
    fun notTheStepWhileTheTrainBeforeIsStillGoing() {
        assertFalse(walkIsCurrent(journey, walk, base.plusMinutes(60)))
        assertTrue(walkIsCurrent(journey, walk, base.plusMinutes(121)))
    }

    @Test
    fun aWalkLongOverIsNotTheStep() {
        assertFalse(walkIsCurrent(journey, walk, base.plusMinutes(131 + 31)))
    }

    @Test
    fun aFirstWalkIsTheStepFromTheStart() {
        val first = leg(true, 10, 15)
        assertTrue(walkIsCurrent(Journey(legs = listOf(first, train)), first, base))
        assertTrue(walkIsCurrent(null, first, base))
    }

    @Test
    fun followsOnlyOnTheWalkAndNearIt() {
        assertTrue(walkFollowsOwn(current = true, metresToWalk = 40.0))
        assertFalse(walkFollowsOwn(current = true, metresToWalk = 900.0))
        assertFalse(walkFollowsOwn(current = false, metresToWalk = 40.0))
        assertFalse(walkFollowsOwn(current = true, metresToWalk = null))
    }

    @Test
    fun theDistanceIsToTheWayNotOnlyItsCorners() {
        val way = listOf(51.0 to 13.70, 51.0 to 13.72)
        // Beside the middle of the segment, about 111 m north.
        assertEquals(111.0, metresToWay(way, 51.001 to 13.71)!!, 2.0)
        // Beyond its end: to the end.
        assertEquals(0.0, metresToWay(way, 51.0 to 13.72)!!, 0.5)
        assertNull(metresToWay(emptyList(), 51.0 to 13.7))
        assertEquals(111.0, metresToWay(listOf(51.0 to 13.7), 51.001 to 13.7)!!, 1.0)
    }
}
