package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapPickCameraTest {
    private val own = 52.5200 to 13.3700

    @Test
    fun aPickStartsAtTheOwnPositionCloseUp() {
        assertEquals(MapPickCamera(own.first, own.second, MAP_PICK_ZOOM), mapPickCamera(own, ownIsReal = true, currentZoom = 0.0))
        assertEquals(MapPickCamera(own.first, own.second, MAP_PICK_ZOOM), mapPickCamera(own, ownIsReal = true, currentZoom = Double.NaN))
    }

    @Test
    fun aCloserZoomIsKept() {
        assertEquals(17.5, mapPickCamera(own, ownIsReal = true, currentZoom = 17.5)!!.zoom, 0.0)
    }

    @Test
    fun withoutARealPositionTheMapStays() {
        assertNull(mapPickCamera(null, ownIsReal = true, currentZoom = 14.0))
        assertNull(mapPickCamera(own, ownIsReal = false, currentZoom = 14.0))
    }
}
