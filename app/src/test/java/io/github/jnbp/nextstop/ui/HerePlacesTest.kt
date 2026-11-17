package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A saved place the user is at shows as "You are here", never as a failed search. Made-up places. */
class HerePlacesTest {
    private val home = 52.5000 to 13.4000
    // About 110 m, 330 m and 1.1 km north of it.
    private val near = 52.5010 to 13.4000
    private val street = 52.5030 to 13.4000
    private val far = 52.5100 to 13.4000

    @Test
    fun theDeviceAtThePlace() {
        assertTrue(atSavedPlace(home, "h", "s", start = far, device = near, noJourney = false))
        assertFalse(atSavedPlace(home, "h", "s", start = far, device = far, noJourney = false))
    }

    @Test
    fun theStartAtThePlace() {
        assertTrue(atSavedPlace(home, "h", "h", start = null, device = null, noJourney = false))
        assertTrue(atSavedPlace(home, "h", "s", start = near, device = null, noJourney = false))
    }

    @Test
    fun noJourneyFromAroundTheCornerMeansHere() {
        // The nearest stop is 330 m away and the provider finds nothing: start and destination are one.
        assertTrue(atSavedPlace(home, "h", "s", start = street, device = null, noJourney = true))
        assertFalse(atSavedPlace(home, "h", "s", start = street, device = null, noJourney = false))
        // From 1 km away nothing found is a real "no connection".
        assertFalse(atSavedPlace(home, "h", "s", start = far, device = null, noJourney = true))
    }

    @Test
    fun theTileGoesOnlyWithMoreThanTwoPlaces() {
        assertFalse(hideHereTile(1))
        assertFalse(hideHereTile(2))
        assertTrue(hideHereTile(3))
    }

    @Test
    fun distances() {
        assertTrue(metresBetween(home, near) in 100.0..120.0)
        assertTrue(metresBetween(home, far) in 1_100.0..1_120.0)
    }
}
