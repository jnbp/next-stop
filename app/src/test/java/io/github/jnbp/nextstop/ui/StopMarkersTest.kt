package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dots of the stops in a list of several stops: stable, and apart while colours are left. */
class StopMarkersTest {
    private val stops = listOf("de:11000:900000001", "de:11000:900000002", "de:11000:900000003", "Hauptstraße", "Am Markt")

    @Test
    fun everyStopGetsItsOwnColourWhileThereAreEnough() {
        val markers = stopMarkers(stops)
        assertEquals(stops.size, markers.size)
        assertEquals(stops.size, markers.values.toSet().size)
        assertTrue(markers.values.all { it in 0 until STOP_MARKER_COUNT })
    }

    @Test
    fun theOrderOfTheDeparturesDoesNotChangeTheColours() {
        val repeated = stops + stops.reversed() + stops.first()
        assertEquals(stopMarkers(stops), stopMarkers(repeated.shuffled(kotlin.random.Random(7))))
    }

    @Test
    fun moreStopsThanColoursStillGetOne() {
        val many = (1..25).map { "Bahnhofstraße $it" }
        val markers = stopMarkers(many)
        assertEquals(25, markers.size)
        assertTrue(markers.values.all { it in 0 until STOP_MARKER_COUNT })
        // The first ten (by id) take ten different colours.
        assertEquals(STOP_MARKER_COUNT, many.sorted().take(STOP_MARKER_COUNT).map { markers.getValue(it) }.toSet().size)
    }
}
