package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Disruption
import io.github.jnbp.nextstop.data.Stop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The logic of "My routes": region, relevance of notices, edge arrows, the grid of tiles. */
class WaysLogicTest {
    private val home = 52.50 to 13.30
    private val near = Stop("n", "Musterstraße", 52.502, 13.301, null)
    private val town = Stop("t", "Example Mitte", 52.60, 13.45, null)
    private val far = Stop("f", "Example Hauptbahnhof", 51.05, 13.74, null)

    private fun notice(id: String, stops: List<Stop> = emptyList(), lines: Set<String> = emptySet()) =
        Disruption(id = id, summary = "", text = "Hinweis", lines = lines, products = emptySet(), stops = stops)

    @Test
    fun regionKeepsNearAndUnplacedNotices() {
        assertTrue(inRegion(notice("1", listOf(town)), listOf(home)))
        assertFalse(inRegion(notice("2", listOf(far)), listOf(home)))
        // Without any position the notice cannot be placed: it counts.
        assertTrue(inRegion(notice("3"), listOf(home)))
        // Around the user's position too, and a watched one anywhere.
        assertTrue(inRegion(notice("2", listOf(far)), listOf(home, 51.06 to 13.73)))
        assertTrue(inRegion(notice("2", listOf(far)), listOf(home), always = setOf("2")))
        assertTrue(inRegion(notice("2", listOf(far)), emptyList()))
    }

    @Test
    fun relevantNoticesAreOnMyLinesOrNearMyPlaces() {
        val items = listOf(
            notice("line", lines = setOf("U 7")),
            notice("near", listOf(near)),
            notice("town", listOf(town)),
            notice("other", lines = setOf("M27")),
        )
        val found = relevantDisruptions(items, lines = setOf("U7"), spots = listOf(home))
        assertEquals(listOf("line", "near"), found.map { it.id })
        assertEquals(emptyList<String>(), relevantDisruptions(items, emptySet(), emptyList()).map { it.id })
        // 500 m: the stop about 230 m away counts, at 100 m it does not.
        assertEquals(listOf("near"), relevantDisruptions(items, emptySet(), listOf(home)).map { it.id })
        assertEquals(emptyList<String>(), relevantDisruptions(items, emptySet(), listOf(home), nearMeters = 100).map { it.id })
    }

    @Test
    fun noArrowForAPlaceOnThePicture() {
        assertNull(edgeArrow(50.0 to 40.0, 300.0, 160.0, 14.0))
    }

    @Test
    fun arrowSitsOnTheEdgeTowardsThePlace() {
        // Straight below: at the bottom, pointing down (90°).
        val down = edgeArrow(150.0 to 2000.0, 300.0, 160.0, 14.0)
        assertNotNull(down)
        assertEquals(150.0, down!!.x, 1e-6)
        assertEquals(146.0, down.y, 1e-6)
        assertEquals(90.0, down.degrees, 1e-6)
        // Far to the left and a little up: on the left edge.
        val left = edgeArrow(-5000.0 to 0.0, 300.0, 160.0, 14.0)!!
        assertEquals(14.0, left.x, 1e-6)
        assertTrue(left.y in 70.0..80.0)
        assertTrue(left.degrees in 180.0..181.0)
        // Up and right: the corner side reached first holds it.
        val corner = edgeArrow(1000.0 to -1000.0, 300.0, 160.0, 14.0)!!
        assertEquals(14.0, corner.y, 1e-6)
        assertTrue(corner.x in 150.0..286.0)
        assertTrue(corner.degrees in 300.0..320.0)
    }

    @Test
    fun gridSlotIsTheNearestCell() {
        // 2 × 2, cells 100 × 80, gap 8.
        assertEquals(0, gridDropSlot(0, 10f, 10f, 100f, 80f, 8f, 4))
        assertEquals(1, gridDropSlot(0, 90f, 0f, 100f, 80f, 8f, 4))
        assertEquals(3, gridDropSlot(0, 120f, 100f, 100f, 80f, 8f, 4))
        assertEquals(2, gridDropSlot(1, -300f, 300f, 100f, 80f, 8f, 4))
        // Three tiles: the empty fourth cell is not a place.
        assertEquals(2, gridDropSlot(0, 120f, 100f, 100f, 80f, 8f, 3))
        assertEquals(1 to 1, gridCell(3))
    }
}
