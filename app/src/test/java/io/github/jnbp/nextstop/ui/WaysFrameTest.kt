package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The map of "My routes" frames the home region, not a far-off place as well. */
class WaysFrameTest {
    private val home = 52.5163 to 13.3777
    private val work = 52.5450 to 13.3500
    private val gym = 52.4800 to 13.4300
    private val dresden = 51.0504 to 13.7373

    @Test
    fun farPlaceIsLeftOutOfTheFrame() {
        val places = listOf(home, work, dresden, gym)
        val anchor = homeRegionAnchor(places, home, null)
        assertEquals(home, anchor)
        assertEquals(listOf(home, work, gym), places.filter { inHomeRegion(it, anchor) })
    }

    @Test
    fun withoutHomeTheDensestClusterCounts() {
        // Dresden first in the list: still the Berlin places make the region.
        val places = listOf(dresden, work, gym)
        val anchor = homeRegionAnchor(places, null, dresden)
        assertTrue(inHomeRegion(work, anchor))
        assertTrue(inHomeRegion(gym, anchor))
        assertFalse(inHomeRegion(dresden, anchor))
    }

    @Test
    fun withoutPlacesWhereTheUserIs() {
        assertEquals(dresden, homeRegionAnchor(emptyList(), null, dresden))
        assertNull(homeRegionAnchor(emptyList(), null, null))
        assertTrue(inHomeRegion(dresden, null))
        // A single far place alone is its own region.
        assertEquals(dresden, homeRegionAnchor(listOf(dresden), null, home))
    }
}
