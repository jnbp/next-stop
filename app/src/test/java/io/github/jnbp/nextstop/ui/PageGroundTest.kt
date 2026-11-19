package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** What each page lies on while a back gesture shrinks it: the map, fog over it, or the plain ground. */
class PageGroundTest {
    private val withoutMap = listOf(Screen.Search, Screen.Settings, Screen.Connections, Screen.Departures, Screen.Favorites)

    @Test
    fun pagesWithoutTheMapLieOnFogWhenTheStartMapIsOn() {
        for (page in withoutMap) assertEquals(page.name, PageGround.Fog, pageGround(page, startMap = true))
    }

    @Test
    fun pagesWithoutTheMapLieOnThePlainGroundWhenTheStartMapIsOff() {
        for (page in withoutMap) assertEquals(page.name, PageGround.Plain, pageGround(page, startMap = false))
    }

    @Test
    fun pagesAboutTheMapLieOnItEitherWay() {
        for (page in listOf(Screen.Plan, Screen.Trip, Screen.Course, Screen.MapSheet)) {
            assertEquals(page.name, PageGround.Map, pageGround(page, startMap = true))
            assertEquals(page.name, PageGround.Map, pageGround(page, startMap = false))
        }
    }

    @Test
    fun theStartPageLiesOnTheMapOnlyWithTheStartMap() {
        assertEquals(PageGround.Map, pageGround(Screen.Start, startMap = true))
        assertEquals(PageGround.Plain, pageGround(Screen.Start, startMap = false))
    }

    @Test
    fun thePlanningPageLiesOnThePlainGroundWithTheBatterySaver() {
        assertEquals(PageGround.Plain, pageGround(Screen.Plan, startMap = false, planMap = false))
        // Only the planning page: the trip and the course keep their map.
        assertEquals(PageGround.Map, pageGround(Screen.Trip, startMap = false, planMap = false))
        assertEquals(PageGround.Map, pageGround(Screen.Course, startMap = false, planMap = false))
        // From it to the map tab the plain ground goes last, as from the start page.
        assertEquals(true, groundLast(Screen.Plan, Screen.MapSheet, startMap = false, planMap = false) || tabSlideOf(Screen.Plan, Screen.MapSheet) != 0)
    }

    @Test
    fun everyPageHasAGround() {
        for (page in Screen.values()) {
            pageGround(page, startMap = true)
            pageGround(page, startMap = false)
        }
    }
}
