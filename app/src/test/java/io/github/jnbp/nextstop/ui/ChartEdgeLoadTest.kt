package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Loading earlier and later journeys at the edges of the chart (see ChartEdgeLoad.kt). */
class ChartEdgeLoadTest {
    @Test
    fun endIsTheLastTwoColumns() {
        assertTrue(chartAtEnd("c", "c", "b"))
        assertTrue(chartAtEnd("b", "c", "b"))
        assertFalse(chartAtEnd("a", "c", "b"))
        assertTrue(chartAtEnd("c", "c", null))
        assertFalse(chartAtEnd(null, null, null))
        assertFalse(chartAtEnd(null, "c", "b"))
    }

    @Test
    fun startIsTheFirstColumn() {
        assertTrue(chartAtStart("a", "a"))
        assertFalse(chartAtStart("b", "a"))
        assertFalse(chartAtStart(null, null))
    }

    @Test
    fun aPagePutInFrontIsNotTheStartReachedAgain() {
        // For a frame after earlier journeys came in front, the row is still laid out as before:
        // its first column is the old first journey, no longer the first one.
        val earlier = EdgeTrigger(onlyWhileScrolling = true)
        assertTrue(earlier.edge(chartAtStart("a", "a"), scrolling = true, edgeId = "a", primed = true))
        assertFalse(earlier.edge(chartAtStart("a", "x"), scrolling = true, edgeId = "x", primed = true))
        assertFalse(earlier.edge(chartAtStart("c", "x"), scrolling = true, edgeId = "x", primed = true))
    }

    @Test
    fun reachingTheEndLoadsOnceNotForEveryFrame() {
        val later = EdgeTrigger(onlyWhileScrolling = false)
        assertFalse(later.edge(at = false, scrolling = true, edgeId = "c"))
        assertTrue(later.edge(at = true, scrolling = true, edgeId = "c"))
        // The fling ends there: no second load.
        assertFalse(later.edge(at = true, scrolling = false, edgeId = "c"))
        assertFalse(later.edge(at = true, scrolling = false, edgeId = "c"))
    }

    @Test
    fun aNewEdgeAfterAPageLoadsAgainWhileStillThere() {
        val later = EdgeTrigger(onlyWhileScrolling = false)
        assertTrue(later.edge(at = true, scrolling = false, edgeId = "c"))
        // A page came in and its last column is still in view: that is a new end.
        assertTrue(later.edge(at = true, scrolling = false, edgeId = "e"))
        // A page that brought nothing new leaves the same end: no endless loading.
        assertFalse(later.edge(at = true, scrolling = false, edgeId = "e"))
    }

    @Test
    fun aPullAtTheEdgeLoadsTheNextPageOncePerGesture() {
        val later = EdgeTrigger(onlyWhileScrolling = false)
        assertTrue(later.edge(at = true, scrolling = false, edgeId = "c"))
        // A finished load that showed nothing: the user pulls at the edge again.
        assertFalse(later.edge(at = true, scrolling = true, edgeId = "c"))
        assertTrue(later.pull(scrolling = true))
        assertFalse(later.pull(scrolling = true))
        assertFalse(later.edge(at = true, scrolling = false, edgeId = "c"))
        // The next pull is a gesture of its own.
        assertFalse(later.edge(at = true, scrolling = true, edgeId = "c"))
        assertTrue(later.pull(scrolling = true))
    }

    @Test
    fun reachingAndPullingInOneGestureIsOneLoad() {
        val later = EdgeTrigger(onlyWhileScrolling = false)
        assertTrue(later.edge(at = true, scrolling = true, edgeId = "c"))
        assertFalse(later.pull(scrolling = true))
        assertFalse(later.edge(at = true, scrolling = false, edgeId = "c"))
        assertFalse(later.edge(at = true, scrolling = true, edgeId = "c"))
        assertTrue(later.pull(scrolling = true))
    }

    @Test
    fun earlierLoadsOnceUpFrontThenOnlyWhileTheUserScrolls() {
        val earlier = EdgeTrigger(onlyWhileScrolling = true)
        // Up front, once.
        assertTrue(earlier.edge(at = true, scrolling = false, edgeId = "a", primed = false))
        // Earlier journeys came in front and the first is in view without a scroll: the
        // chart does not walk into the past by itself.
        assertFalse(earlier.edge(at = true, scrolling = false, edgeId = "z", primed = true))
        assertFalse(earlier.edge(at = false, scrolling = true, edgeId = "z", primed = true))
        // Dragged or flung back to the start: one load.
        assertTrue(earlier.edge(at = true, scrolling = true, edgeId = "z", primed = true))
        assertFalse(earlier.edge(at = true, scrolling = false, edgeId = "z", primed = true))
        // A pull at the start asks for the next page.
        assertTrue(earlier.pull(scrolling = true))
    }

    @Test
    fun aPullIsCountedOnceAfterEnoughUnconsumedDrag() {
        val pull = EdgePull(threshold = 30f)
        assertEquals(0, pull.drag(0f))
        assertEquals(0, pull.drag(20f))
        assertEquals(1, pull.drag(15f))
        assertEquals(0, pull.drag(40f))
        pull.release()
        assertEquals(0, pull.drag(-20f))
        assertEquals(-1, pull.drag(-20f))
        // Turning round within a gesture starts anew.
        assertEquals(0, pull.drag(10f))
        assertEquals(1, pull.drag(25f))
    }

    @Test
    fun theOutcomeOfAPage() {
        val before = mapOf("normal" to "r1")
        assertEquals(PageOutcome.Added, pageOutcome(found = 1, failed = 0, added = 3, before, mapOf("normal" to "r2")))
        assertEquals(PageOutcome.Failed, pageOutcome(found = 0, failed = 1, added = 0, before, before))
        // Found, but each of them was shown already: the next page may bring more.
        assertEquals(PageOutcome.Nothing, pageOutcome(found = 1, failed = 0, added = 0, before, mapOf("normal" to "r2")))
        // No reference left, or the same one again: asking again would bring the same page.
        assertEquals(PageOutcome.End, pageOutcome(found = 1, failed = 0, added = 0, before, emptyMap<String, String>()))
        assertEquals(PageOutcome.End, pageOutcome(found = 1, failed = 0, added = 0, before, before))
        // Nothing found anywhere (no failure): the end.
        assertEquals(PageOutcome.End, pageOutcome(found = 0, failed = 0, added = 0, before, before))
        // One level failed, another one brought journeys: they count.
        assertEquals(PageOutcome.Added, pageOutcome(found = 1, failed = 1, added = 2, before, before))
    }

    @Test
    fun addedCountsOnlyNewJourneys() {
        assertEquals(0, addedCount(listOf("a", "b"), listOf("a", "b")))
        assertEquals(2, addedCount(listOf("a", "b"), listOf("z", "a", "b", "c")))
        // Past journeys dropped while new ones came: still the new ones.
        assertEquals(1, addedCount(listOf("a", "b"), listOf("b", "c")))
    }

    @Test
    fun theNoteAtTheEdgeAfterALoad() {
        assertNull(edgeNoteOf(PageOutcome.Added, edgeBefore = "a", edgeNow = "z"))
        // Added, but the filters hide every new one: nothing changed at the edge.
        assertEquals(EdgeNote.Hidden, edgeNoteOf(PageOutcome.Added, edgeBefore = "a", edgeNow = "a"))
        assertEquals(EdgeNote.Nothing, edgeNoteOf(PageOutcome.Nothing, "a", "a"))
        assertEquals(EdgeNote.Failed, edgeNoteOf(PageOutcome.Failed, "a", "a"))
        assertEquals(EdgeNote.End, edgeNoteOf(PageOutcome.End, "a", "a"))
    }
}
