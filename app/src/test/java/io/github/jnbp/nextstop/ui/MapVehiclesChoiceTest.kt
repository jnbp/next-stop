package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.ui.theme.MapVehiclesChoice
import io.github.jnbp.nextstop.ui.theme.mapVehiclesChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three levels of the setting "Vehicles on the map" and the move from the old switch. */
class MapVehiclesChoiceTest {
    @Test
    fun theOldSwitchIsTakenOver() {
        assertEquals(MapVehiclesChoice.All, mapVehiclesChoice(null, true))
        assertEquals(MapVehiclesChoice.Off, mapVehiclesChoice(null, false))
        // Never set: only close up, the default.
        assertEquals(MapVehiclesChoice.Dynamic, mapVehiclesChoice(null, null))
        // A chosen level stays.
        assertEquals(MapVehiclesChoice.All, mapVehiclesChoice("All", null))
        // A chosen level wins over the old switch.
        assertEquals(MapVehiclesChoice.Dynamic, mapVehiclesChoice("Dynamic", false))
        assertEquals(MapVehiclesChoice.Off, mapVehiclesChoice("Off", true))
        // Something unknown stored: as if nothing was.
        assertEquals(MapVehiclesChoice.Dynamic, mapVehiclesChoice("Many", null))
        assertEquals(MapVehiclesChoice.All, mapVehiclesChoice("Many", true))
    }

    @Test
    fun dynamicShowsVehiclesOnlyCloseUp() {
        assertTrue(mapVehiclesWanted(MapVehiclesChoice.All, close = false))
        assertTrue(mapVehiclesWanted(MapVehiclesChoice.Dynamic, close = true))
        assertFalse(mapVehiclesWanted(MapVehiclesChoice.Dynamic, close = false))
        assertFalse(mapVehiclesWanted(MapVehiclesChoice.Off, close = true))
        assertTrue(closeForVehicles(13.5))
        assertTrue(closeForVehicles(16.0))
        assertFalse(closeForVehicles(13.4))
    }
}
