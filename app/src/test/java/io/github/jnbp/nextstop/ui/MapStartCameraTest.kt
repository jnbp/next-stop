package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapStartCameraTest {
    private val stop = 52.5251 to 13.3694
    private val own = 52.5200 to 13.3700
    private val pin = 52.5300 to 13.4000

    @Test
    fun freshMapUnderAHeldStopCardGoesToTheStop() {
        // The case of #228: the map tab opened with a stop card, the view held, the map new.
        val place = mapStartCamera(0.0, follow = false, framesItself = false, markedStop = stop, pinned = null, own = own)
        assertEquals(StartCameraPlace(stop.first, stop.second, atStop = true), place)
    }

    @Test
    fun freshMapWithAPinnedPlaceGoesThere() {
        val place = mapStartCamera(0.0, follow = false, framesItself = false, markedStop = null, pinned = pin, own = own)
        assertEquals(StartCameraPlace(pin.first, pin.second, atStop = true), place)
    }

    @Test
    fun freshHeldMapWithoutAPlaceGoesToTheOwnArea() {
        val place = mapStartCamera(Double.NaN, follow = false, framesItself = false, markedStop = null, pinned = null, own = own)
        assertEquals(StartCameraPlace(own.first, own.second, atStop = false), place)
    }

    @Test
    fun aFollowingMapIsPlacedByTheMapItself() {
        assertNull(mapStartCamera(0.0, follow = true, framesItself = false, markedStop = stop, pinned = null, own = own))
    }

    @Test
    fun pagesThatFrameThemselvesAreLeftAlone() {
        assertNull(mapStartCamera(0.0, follow = false, framesItself = true, markedStop = stop, pinned = null, own = own))
    }

    @Test
    fun aPlacedMapIsNotMoved() {
        assertNull(mapStartCamera(14.5, follow = false, framesItself = false, markedStop = stop, pinned = null, own = own))
        assertNull(mapStartCamera(FRESH_MAP_ZOOM, follow = false, framesItself = false, markedStop = stop, pinned = null, own = own))
    }
}
