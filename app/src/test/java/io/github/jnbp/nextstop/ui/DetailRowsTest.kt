package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plan of the connection detail in the Live-Fahrt look (#129). Example journey (made up):
 * walk 4 min, S-Bahn, walk 6 min, bus, walk 3 min.
 */
class DetailRowsTest {
    private val walking = listOf(true, false, true, false, true)
    private val minutes = listOf(4L, 20L, 6L, 12L, 3L)

    @Test
    fun storedStyleFallsBackToTheTimeline() {
        assertEquals(DetailStyle.Timeline, DetailStyle.fromStored(null))
        assertEquals(DetailStyle.Timeline, DetailStyle.fromStored("Unknown"))
        assertEquals(DetailStyle.Classic, DetailStyle.fromStored("Classic"))
    }

    @Test
    fun foldedRideShowsItsEndsAndTheMiddleInOneRow() {
        assertEquals(
            listOf(DetailStopRow.Stop(0), DetailStopRow.Folded(1, 6), DetailStopRow.Stop(7)),
            detailStopRows(8, known = true, expanded = false, failed = false),
        )
        assertEquals((0..7).map { DetailStopRow.Stop(it) }, detailStopRows(8, known = true, expanded = true, failed = false))
    }

    @Test
    fun oneStopBetweenIsShownNotFolded() {
        assertEquals((0..2).map { DetailStopRow.Stop(it) }, detailStopRows(3, known = true, expanded = false, failed = false))
    }

    @Test
    fun unknownStopsShowLoadingOrFailedOnlyWhenUnfolded() {
        val ends = listOf(DetailStopRow.Stop(0), DetailStopRow.Stop(1))
        assertEquals(ends, detailStopRows(2, known = false, expanded = false, failed = false))
        assertEquals(
            listOf(DetailStopRow.Stop(0), DetailStopRow.Note(StopsNote.Loading), DetailStopRow.Stop(1)),
            detailStopRows(2, known = false, expanded = true, failed = false),
        )
        assertEquals(StopsNote.Failed, (detailStopRows(2, known = false, expanded = true, failed = true)[1] as DetailStopRow.Note).kind)
        assertEquals(StopsNote.None, (detailStopRows(2, known = true, expanded = true, failed = false)[1] as DetailStopRow.Note).kind)
    }

    @Test
    fun everyElementOfTheJourneyHasItsItemInOrder() {
        val items = detailItems(walking, minutes, stayAfter = null) { detailStopRows(4, known = true, expanded = false, failed = false) }
        val keys = items.map { it.key }
        assertEquals(
            listOf(
                "top", "walk0",
                "pre1", "head1", "info1", "stop1:s0", "stop1:f1-2", "stop1:s3", "foot1",
                "change2",
                "pre3", "head3", "info3", "stop3:s0", "stop3:f1-2", "stop3:s3", "foot3",
                "walk4", "end",
            ),
            keys,
        )
        // Keys are unique, as the lazy list needs.
        assertEquals(keys.size, keys.toSet().size)
        val change = items.filterIsInstance<DetailItem.Change>().single()
        assertEquals(2, change.walkIndex)
        assertEquals(2, change.keepLegs)
    }

    @Test
    fun stopRowsKnowTheEndsOfTheirRail() {
        val stops = detailItems(walking, minutes, stayAfter = null) { detailStopRows(4, known = true, expanded = false, failed = false) }
            .filterIsInstance<DetailItem.RideStop>().filter { it.legIndex == 1 }
        assertTrue(stops.first().first)
        assertFalse(stops.first().last)
        assertTrue(stops.last().last)
        assertFalse(stops[1].first || stops[1].last)
    }

    @Test
    fun twoRidesInARowGetTheirChangeWithoutAWalk() {
        val items = detailItems(listOf(false, false), listOf(10L, 10L), stayAfter = null) { listOf(DetailStopRow.Stop(0), DetailStopRow.Stop(1)) }
        val change = items.filterIsInstance<DetailItem.Change>().single()
        assertEquals(0, change.arrivingIndex)
        assertEquals(1, change.nextIndex)
        assertEquals(null, change.walkIndex)
    }

    @Test
    fun aStayBetweenTwoRidesTakesThePlaceOfTheirChange() {
        val items = detailItems(walking, minutes, stayAfter = 3) { listOf(DetailStopRow.Stop(0), DetailStopRow.Stop(1)) }
        assertTrue(items.none { it is DetailItem.Change })
        val keys = items.map { it.key }
        assertEquals(keys.indexOf("foot1") + 1, keys.indexOf("stay"))
        // The walk of the change stays, after the stay.
        assertEquals(keys.indexOf("stay") + 1, keys.indexOf("walk2"))
        assertTrue(keys.indexOf("walk2") < keys.indexOf("head3"))
    }

    @Test
    fun aStayBeforeTheLastWalkStandsBeforeIt() {
        val items = detailItems(walking, minutes, stayAfter = 4) { listOf(DetailStopRow.Stop(0), DetailStopRow.Stop(1)) }
        val keys = items.map { it.key }
        assertEquals(keys.indexOf("walk4") - 1, keys.indexOf("stay"))
        assertEquals(1, keys.count { it == "stay" })
    }

    @Test
    fun aWalkOnlyJourneyHasNoRides() {
        val items = detailItems(listOf(true), listOf(12L), stayAfter = null) { emptyList() }
        assertEquals(listOf("top", "walk0", "end"), items.map { it.key })
    }

    // #186: only the stops where the traveller gets on or off show a ⋮; the stops between open
    // their menu from the row itself, on a tap or a long press (never a long press alone).
    @Test
    fun onlyTheEndsShowTheDots() {
        assertEquals(StopMenuOpening.Dots, stopMenuOpening(0, 5))
        assertEquals(StopMenuOpening.Dots, stopMenuOpening(5, 5))
        for (index in 1..4) assertEquals(StopMenuOpening.Row, stopMenuOpening(index, 5))
        // A ride of two stops: both ends.
        assertEquals(StopMenuOpening.Dots, stopMenuOpening(1, 1))
    }
}
