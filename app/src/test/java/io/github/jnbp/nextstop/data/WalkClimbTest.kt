package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which way the lifts and escalators of a walk go, from the levels along it (see climbs). */
class WalkClimbTest {
    @Test
    fun escalatorsWithoutLevelsGoTowardsTheNextKnownLevel() {
        // From a platform at level 1 down to a tram stop in the street.
        val steps = listOf(LevelStep(0.3, null, emptyList()), LevelStep(0.5, null, emptyList()))
        assertEquals(listOf(-1, -1), climbs(1.0, steps, 0.0))
    }

    @Test
    fun aSpanGoesFromTheSideTheWalkIsOn() {
        val steps = listOf(LevelStep(0.2, null, listOf(-2.0, -1.0)), LevelStep(0.4, null, listOf(-1.0, 0.0)))
        assertEquals(listOf(1, 1), climbs(-2.0, steps, 0.0))
    }

    @Test
    fun aDoorSetsTheLevel() {
        val steps = listOf(LevelStep(0.2, null, emptyList()), LevelStep(0.5, 0.0, null), LevelStep(0.8, null, emptyList()))
        assertEquals(listOf(1, 0, -1), climbs(-1.0, steps, -1.0))
    }

    @Test
    fun unknownLevelsSayNothing() {
        assertEquals(listOf(0), climbs(null, listOf(LevelStep(0.5, null, emptyList())), 0.0))
    }

    @Test
    fun levelTags() {
        assertEquals(listOf(-1.0, 0.0), levelNumbers("-1;0"))
        assertEquals(emptyList<Double>(), levelNumbers(null))
        assertEquals(emptyList<Double>(), levelNumbers("roof"))
    }
}
