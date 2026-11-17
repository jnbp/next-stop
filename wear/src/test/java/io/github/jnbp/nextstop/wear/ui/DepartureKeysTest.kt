package io.github.jnbp.nextstop.wear.ui

import io.github.jnbp.nextstop.wear.data.Departure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The keys that keep a live departure row of the start page with its departure. */
class DepartureKeysTest {
    private fun dep(line: String, direction: String, planned: Long?, time: Long? = planned) =
        Departure(line, "suburban", direction, time, 0, null, false, plannedTime = planned)

    @Test
    fun aDepartureKeepsItsKeyWhenTheOneAboveHasGone() {
        val a = dep("S1", "Wannsee", 1_000L)
        val b = dep("S1", "Oranienburg", 2_000L)
        val c = dep("U9", "Osloer Str.", 3_000L)
        val before = departureKeys(listOf(a, b, c))
        val after = departureKeys(listOf(b, c))
        assertEquals(before[1], after[0])
        assertEquals(before[2], after[1])
    }

    @Test
    fun aDelayDoesNotChangeTheKey() {
        assertEquals(departureKeys(listOf(dep("M10", "Hbf", 5_000L, 5_000L))), departureKeys(listOf(dep("M10", "Hbf", 5_000L, 65_000L))))
    }

    @Test
    fun twoAlikeDeparturesStillGetTwoKeys() {
        val keys = departureKeys(listOf(dep("X", "Y", null, null), dep("X", "Y", null, null)))
        assertEquals(2, keys.toSet().size)
        assertNotEquals(keys[0], keys[1])
    }
}
