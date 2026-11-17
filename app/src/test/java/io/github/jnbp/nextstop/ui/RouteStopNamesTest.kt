package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A journey on the map names its important stations always, the stops between where there is room. */
class RouteStopNamesTest {
    // Start, four stops on the way, a change, two stops, the destination: in a row on the screen.
    private val key = booleanArrayOf(true, false, false, false, false, true, false, false, true)

    private fun named(step: Float): List<Int> {
        val x = FloatArray(key.size) { it * step }
        val y = FloatArray(key.size)
        return namedRouteStops(x, y, key, spacing = 100f).withIndex().filter { it.value }.map { it.index }
    }

    @Test
    fun zoomedOutOnlyStartChangesAndDestination() {
        assertEquals(listOf(0, 5, 8), named(20f))
    }

    @Test
    fun zoomedInTheStopsBetweenComeIn() {
        // Every second stop first, never one closer than the spacing to a named one.
        assertEquals(listOf(0, 2, 5, 8), named(50f))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8), named(100f))
    }
}
