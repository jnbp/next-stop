package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #243: two chips under a change and one shared row of tiles. */
class ChangeTileRowTest {
    private fun at(time: String): OffsetDateTime = OffsetDateTime.parse("2026-11-19T${time}:00+01:00")

    @Test
    fun theChipsDecideWhatTheRowShows() {
        assertEquals(ChangeTileRow.Collapsed, changeTileRow(connections = false, ways = false))
        assertEquals(ChangeTileRow.Connections, changeTileRow(connections = true, ways = false))
        assertEquals(ChangeTileRow.Ways, changeTileRow(connections = false, ways = true))
        assertEquals(ChangeTileRow.Mixed, changeTileRow(connections = true, ways = true))
    }

    @Test
    fun theWaysChipSearchesFirstThenFoldsAndUnfolds() {
        assertEquals(WaysChipTap.Search, waysChipTap(WaysSearch.None, open = true))
        assertEquals(WaysChipTap.Fold, waysChipTap(WaysSearch.Loading, open = true))
        assertEquals(WaysChipTap.Fold, waysChipTap(WaysSearch.Done, open = true))
        assertEquals(WaysChipTap.Unfold, waysChipTap(WaysSearch.Done, open = false))
        assertEquals(WaysChipTap.Unfold, waysChipTap(WaysSearch.Loading, open = false))
        // A failure shows while on; folded and tapped again, it searches again.
        assertEquals(WaysChipTap.Fold, waysChipTap(WaysSearch.Failed, open = true))
        assertEquals(WaysChipTap.Search, waysChipTap(WaysSearch.Failed, open = false))
    }

    @Test
    fun theWaysChipIsOnWhileSomethingWasAskedForAndNotFolded() {
        assertFalse(waysChipOn(WaysSearch.None, open = true))
        assertTrue(waysChipOn(WaysSearch.Loading, open = true))
        assertTrue(waysChipOn(WaysSearch.Done, open = true))
        assertTrue(waysChipOn(WaysSearch.Failed, open = true))
        assertFalse(waysChipOn(WaysSearch.Done, open = false))
    }

    @Test
    fun mixedTilesGoByArrivalTheChosenOneAtItsPlace() {
        // Connections first (0 chosen, 1, 2), then two other ways (3, 4), as the row gets them.
        val arrivals = listOf(at("10:40"), at("10:50"), at("10:30"), at("10:35"), at("10:50"))
        val departures = listOf(at("10:05"), at("10:15"), at("10:01"), at("10:03"), at("10:10"))
        assertEquals(listOf(2, 3, 0, 4, 1), mixedTileOrder(arrivals, departures))
    }

    @Test
    fun equalTimesKeepTheGivenOrder() {
        val same = listOf(at("11:00"), at("11:00"), at("11:00"))
        assertEquals(listOf(0, 1, 2), mixedTileOrder(same, same))
    }

    @Test
    fun theRowIsPushedToTheChosenOne() {
        val order = listOf(2, 3, 0, 4, 1)
        assertEquals(2, tilesBeforeChosen(order, chosen = 0) { true })
        // A connection out of reach before it does not count.
        assertEquals(1, tilesBeforeChosen(order, chosen = 0) { it != 2 })
        assertEquals(0, tilesBeforeChosen(order, chosen = 2) { true })
    }

    @Test
    fun eachRowShowsItsKindsOfTiles() {
        assertEquals(ChangeTileShow(connections = false, ways = false), changeTileShow(ChangeTileRow.Collapsed))
        assertEquals(ChangeTileShow(connections = true, ways = false), changeTileShow(ChangeTileRow.Connections))
        assertEquals(ChangeTileShow(connections = false, ways = true), changeTileShow(ChangeTileRow.Ways))
        assertEquals(ChangeTileShow(connections = true, ways = true), changeTileShow(ChangeTileRow.Mixed))
    }

    @Test
    fun aConnectionTileShowsOnlyWithItsChip() {
        val both = ChangeTileShow(connections = true, ways = true)
        val waysOnly = ChangeTileShow(connections = false, ways = true)
        assertTrue(connectionTileShown(both, inReach = true, picked = false))
        assertTrue(connectionTileShown(both, inReach = false, picked = true))
        assertFalse(connectionTileShown(both, inReach = false, picked = false))
        // #257: "Anschluss" off hides even the chosen one (it leaves softly instead of the row being replaced).
        assertFalse(connectionTileShown(waysOnly, inReach = true, picked = true))
    }

    @Test
    fun theRowKeepsItsTilesWhicheverChipIsTurnedOff() {
        // Connections 0 (chosen), 1, 2 and the ways 3, 4, as in mixedTilesGoByArrivalTheChosenOneAtItsPlace.
        val connectionArrivals = listOf(at("10:40"), at("10:50"), at("10:30"))
        val connectionDepartures = listOf(at("10:05"), at("10:15"), at("10:01"))
        val wayArrivals = listOf(at("10:50"), at("10:35"))
        val wayDepartures = listOf(at("10:10"), at("10:03"))
        // Both on, and "Anschluss" off (the connections hidden): the same order, by arrival.
        assertEquals(
            listOf(2, 4, 0, 3, 1),
            changeRowOrder(connectionDepartures, connectionArrivals, wayArrivals, wayDepartures, waysShown = true),
        )
        // "Andere Wege" off: the connections by departure (#215), the leaving ways after them by arrival.
        assertEquals(
            listOf(2, 0, 1, 4, 3),
            changeRowOrder(connectionDepartures, connectionArrivals, wayArrivals, wayDepartures, waysShown = false),
        )
        // No ways at all: the connections alone, by departure.
        assertEquals(
            listOf(2, 0, 1),
            changeRowOrder(connectionDepartures, connectionArrivals, emptyList(), emptyList(), waysShown = true),
        )
    }

    @Test
    fun theRowIsPushedToTheChosenOneOnlyWhileTheConnectionsShow() {
        val order = listOf(2, 4, 0, 3, 1)
        val both = ChangeTileShow(connections = true, ways = true)
        assertEquals(2, rowTilesBeforeChosen(order, chosen = 0, connectionCount = 3, show = both) { true })
        // The ways hidden (leaving): only the connection before it counts.
        assertEquals(1, rowTilesBeforeChosen(order, chosen = 0, connectionCount = 3, show = both.copy(ways = false)) { true })
        // The ways alone: from the beginning.
        assertEquals(0, rowTilesBeforeChosen(order, chosen = 0, connectionCount = 3, show = ChangeTileShow(false, true)) { true })
    }

    @Test
    fun threeWaysShowAtFirstTheRestBehindMore() {
        assertEquals(2, wayRowsShown(2, expanded = false))
        assertEquals(3, wayRowsShown(5, expanded = false))
        assertEquals(5, wayRowsShown(5, expanded = true))
        assertFalse(wayRowsMore(3, expanded = false))
        assertTrue(wayRowsMore(4, expanded = false))
        assertFalse(wayRowsMore(4, expanded = true))
    }

    @Test
    fun theChainHasEveryRideAndOnlyLongWalks() {
        // Walk 2 min, S-Bahn, walk 6 min, U-Bahn, walk 4 min.
        val walking = listOf(true, false, true, false, true)
        val minutes = listOf(2L, 12L, 6L, 9L, 4L)
        assertEquals(
            listOf(WayChainStep.Ride(1), WayChainStep.Walk(6L), WayChainStep.Ride(3)),
            wayChain(walking, minutes),
        )
        assertEquals(listOf(WayChainStep.Ride(0)), wayChain(listOf(false), listOf(20L)))
    }
}
