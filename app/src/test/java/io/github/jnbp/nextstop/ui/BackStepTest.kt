package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What one back does (the table goBack and the predictive back of AppRoot share), and that
 * every back that changes what is on screen has a preview: either a page underneath
 * ([pageAfterBack]) or one within the page ([BackStep.inPage]).
 */
class BackStepTest {
    private val start = PageState(
        courseView = false,
        searchMode = null,
        tab = Tab.Route,
        settingsOpen = false,
        tripOpen = false,
        showingConnections = false,
        planning = false,
    )

    private fun step(facts: BackFacts, planFirst: Boolean = true) = backStep(facts, planFirst)

    @Test
    fun startPageLeavesBackToTheSystem() {
        assertEquals(BackStep.System, step(BackFacts(start)))
    }

    @Test
    fun whatLiesOnThePageGoesBeforeThePage() {
        // A walk on the big map goes first, whatever page it was opened from.
        assertEquals(BackStep.CloseWalk, step(BackFacts(start.copy(tripOpen = true), walk = true, pin = true)))
        assertEquals(BackStep.Unpin, step(BackFacts(start, pin = true)))
        // The card of a stop tapped on the start page's map goes before the app is left.
        assertEquals(BackStep.ClosePick, step(BackFacts(start, pickedStop = true)))
        val map = start.copy(tab = Tab.Map)
        // On the map tab the card of a stop position goes before that of its stop.
        assertEquals(BackStep.ClosePosition, step(BackFacts(map, positionCard = true, stopCard = true)))
        assertEquals(BackStep.UnmarkStop, step(BackFacts(map, stopCard = true)))
        // A course lies over a marked place: the course goes first.
        assertEquals(BackStep.LeaveCourse, step(BackFacts(map.copy(courseView = true), pin = true)))
        val favorites = start.copy(tab = Tab.Favorites)
        assertEquals(BackStep.FavoritesPage, step(BackFacts(favorites, favoritesSub = true)))
        // The search for a favourite place closes before the page of "My routes" under it.
        assertEquals(BackStep.CloseSearch, step(BackFacts(favorites.copy(searchMode = SearchMode.Place), favoritesSub = true)))
    }

    @Test
    fun thePagesGoBackInTheirOrder() {
        assertEquals(BackStep.CloseSettings, step(BackFacts(start.copy(settingsOpen = true))))
        assertEquals(BackStep.MapToDepartures, step(BackFacts(start.copy(tab = Tab.Map, backToDepartures = true))))
        assertEquals(BackStep.RouteTab, step(BackFacts(start.copy(tab = Tab.Departures))))
        // Settings opened on another tab: back goes to the route tab, which is under them.
        assertEquals(BackStep.RouteTab, step(BackFacts(start.copy(tab = Tab.Favorites, settingsOpen = true))))
        assertEquals(BackStep.CloseTrip, step(BackFacts(start.copy(tripOpen = true, showingConnections = true))))
        val connections = start.copy(showingConnections = true)
        assertEquals(BackStep.ToPlan, step(BackFacts(connections), planFirst = true))
        assertEquals(BackStep.CloseConnections, step(BackFacts(connections), planFirst = false))
        assertEquals(BackStep.CloseConnections, step(BackFacts(connections.copy(planning = true)), planFirst = true))
    }

    @Test
    fun myRoutesOpenedFromTheMapGoesBackToTheMap() {
        val fromMap = start.copy(tab = Tab.Favorites, backToMap = true)
        // Its own page (all disruptions) leads back to the map, not to the first page of "My routes".
        assertEquals(BackStep.FavoritesToMap, step(BackFacts(fromMap)))
        assertEquals(Screen.MapSheet, pageAfterBack(fromMap, planFirst = true))
        // A page opened on top of it goes first, within the tab.
        assertEquals(BackStep.FavoritesPage, step(BackFacts(fromMap, favoritesSub = true)))
        // Reached any other way, "My routes" goes back to the route tab.
        assertEquals(BackStep.RouteTab, step(BackFacts(fromMap.copy(backToMap = false))))
    }

    @Test
    fun backWithinThePageIsPreviewedByThePage() {
        val inPage = setOf(
            BackStep.CloseWalk, BackStep.Unpin, BackStep.ClosePick, BackStep.ClosePosition, BackStep.UnmarkStop, BackStep.FavoritesPage,
        )
        BackStep.entries.forEach { step -> assertEquals(step.name, step in inPage, step.inPage) }
    }

    @Test
    fun everyBackToAnotherPageShowsThatPageUnderneath() {
        val states = listOf(
            start.copy(courseView = true),
            start.copy(tab = Tab.Departures, courseView = true),
            start.copy(tab = Tab.Map, backToDepartures = true, courseView = true),
            start.copy(searchMode = SearchMode.Destination),
            start.copy(searchMode = SearchMode.Origin),
            start.copy(tripOpen = true, searchMode = SearchMode.TripTarget),
            start.copy(tab = Tab.Departures, searchMode = SearchMode.DepartureStop),
            start.copy(tab = Tab.Map, searchMode = SearchMode.MapPlace),
            start.copy(tab = Tab.Favorites, searchMode = SearchMode.Place),
            start.copy(settingsOpen = true),
            start.copy(tab = Tab.Departures),
            start.copy(tab = Tab.Map),
            start.copy(tab = Tab.Favorites),
            start.copy(tab = Tab.Map, backToDepartures = true),
            start.copy(tab = Tab.Favorites, backToMap = true),
            start.copy(tripOpen = true),
            start.copy(showingConnections = true),
            start.copy(showingConnections = true, planning = true),
            start.copy(showingConnections = true, settingsOpen = true),
            start.copy(showingConnections = true, tripOpen = true),
        )
        for (state in states) {
            for (planFirst in listOf(true, false)) {
                val step = backStep(BackFacts(state), planFirst)
                assertFalse("$state goes back within the page", step.inPage)
                assertFalse("$state leaves back to the system", step == BackStep.System)
                val after = pageAfterBack(state, planFirst)
                assertTrue("$state has no page underneath", after != null && after != pageOf(state))
            }
        }
    }
}
