package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** A change between the pages of two tabs slides along the tab bar; any other change does not. */
class TabSlideTest {
    @Test
    fun tabsSlideInTheirOrder() {
        assertEquals(1, tabSlideOf(Screen.Start, Screen.Departures))
        assertEquals(1, tabSlideOf(Screen.Departures, Screen.MapSheet))
        assertEquals(1, tabSlideOf(Screen.Connections, Screen.Favorites))
        assertEquals(-1, tabSlideOf(Screen.Favorites, Screen.Start))
        assertEquals(-1, tabSlideOf(Screen.MapSheet, Screen.Departures))
    }

    @Test
    fun pagesWithinATabOrOverAllTabsDoNotSlide() {
        assertEquals(0, tabSlideOf(Screen.Start, Screen.Settings))
        assertEquals(0, tabSlideOf(Screen.Plan, Screen.Connections))
        assertEquals(0, tabSlideOf(Screen.Departures, Screen.Course))
        assertEquals(0, tabSlideOf(Screen.Search, Screen.MapSheet))
        assertEquals(0, tabSlideOf(Screen.Favorites, Screen.Search))
    }
}
