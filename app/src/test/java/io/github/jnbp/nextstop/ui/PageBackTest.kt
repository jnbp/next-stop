package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The page a back gesture shows underneath (predictive back) is the page back leads to. */
class PageBackTest {
    private val start = PageState(
        courseView = false,
        searchMode = null,
        tab = Tab.Route,
        settingsOpen = false,
        tripOpen = false,
        showingConnections = false,
        planning = false,
    )

    @Test
    fun startPageLeavesBackToTheSystem() {
        assertEquals(Screen.Start, pageOf(start))
        assertNull(pageAfterBack(start, planFirst = true))
    }

    @Test
    fun connectionsGoBackToThePlanWhereItComesFirst() {
        val connections = start.copy(showingConnections = true)
        assertEquals(Screen.Connections, pageOf(connections))
        assertEquals(Screen.Plan, pageAfterBack(connections, planFirst = true))
        assertEquals(Screen.Start, pageAfterBack(connections, planFirst = false))
        assertEquals(Screen.Start, pageAfterBack(connections.copy(planning = true), planFirst = true))
    }

    @Test
    fun tripSettingsCourseAndTabsGoBack() {
        assertEquals(Screen.Start, pageAfterBack(start.copy(tripOpen = true), planFirst = true))
        assertEquals(Screen.Start, pageAfterBack(start.copy(settingsOpen = true), planFirst = true))
        assertEquals(Screen.Start, pageAfterBack(start.copy(tab = Tab.Departures), planFirst = true))
        // A line course opened over the departures goes back to them.
        assertEquals(Screen.Departures, pageAfterBack(start.copy(tab = Tab.Departures, courseView = true), planFirst = true))
        // The search goes back to the page under it: here the trip.
        assertEquals(Screen.Trip, pageAfterBack(start.copy(tripOpen = true, searchMode = SearchMode.TripTarget), planFirst = true))
    }

    @Test
    fun mapOpenedFromTheDeparturesGoesBackToThem() {
        val map = start.copy(tab = Tab.Map, backToDepartures = true)
        assertEquals(Screen.MapSheet, pageOf(map))
        assertEquals(Screen.Departures, pageAfterBack(map, planFirst = true))
        // A vehicle shown from a departure: its course goes straight back to the list.
        assertEquals(Screen.Departures, pageAfterBack(map.copy(courseView = true), planFirst = true))
        // The map tab opened by itself goes back to the start.
        assertEquals(Screen.Start, pageAfterBack(start.copy(tab = Tab.Map), planFirst = true))
        assertEquals(Screen.MapSheet, pageAfterBack(start.copy(tab = Tab.Map, courseView = true), planFirst = true))
    }

    @Test
    fun searchOfTheMapTabLiesOverTheMap() {
        val search = start.copy(tab = Tab.Map, searchMode = SearchMode.MapPlace)
        assertEquals(Screen.Search, pageOf(search))
        assertEquals(Screen.MapSheet, pageAfterBack(search, planFirst = true))
        assertEquals(Screen.MapSheet, pageAfterBack(search.copy(backToDepartures = true), planFirst = true))
    }
}
