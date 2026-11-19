package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Whether the map writes a station's name itself is asked of its own label layers only. */
class MapLabelLayersTest {
    @Test
    fun theAppsOwnLayersAreLeftOut() {
        val layers = listOf(
            "poi_transit" to "openmaptiles",
            "ns-vehicle-badges" to "ns-vehicles",
            "place_label" to "openmaptiles",
            "ns-stop-names" to "ns-stops",
        )
        assertEquals(listOf("poi_transit", "place_label"), mapLabelLayers(layers).toList())
    }

    @Test
    fun noLayersNoQuery() {
        assertEquals(emptyList<String>(), mapLabelLayers(emptyList()).toList())
    }
}
