package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The floating "centre on me" button above what covers the bottom of the map (#165). */
class MapLocatePlaceTest {
    @Test
    fun stands_just_above_the_sheet_and_moves_with_it() {
        assertEquals(2000f - 30f - 130f, locateButtonTop(2000f, 400f, 2400f, 130f, 30f)!!, 0.01f)
        assertEquals(1500f - 30f - 130f, locateButtonTop(1500f, 400f, 2400f, 130f, 30f)!!, 0.01f)
    }

    @Test
    fun without_a_bottom_cover_it_stands_at_the_bottom() {
        assertEquals(2400f - 160f, locateButtonTop(Float.NaN, Float.NaN, 2400f, 130f, 30f)!!, 0.01f)
        assertEquals(2400f - 160f, locateButtonTop(5000f, Float.NaN, 2400f, 130f, 30f)!!, 0.01f)
    }

    @Test
    fun a_sheet_pulled_fully_up_hides_it() {
        assertNull(locateButtonTop(500f, 400f, 2400f, 130f, 30f))
        assertNull(locateButtonTop(100f, Float.NaN, 2400f, 130f, 30f))
    }

    @Test
    fun waits_for_the_map_to_come_in() {
        // At the start: the logo is up until the map begins to come in.
        assertTrue(mapStillCovered(visible = true, entering = false, resting = false, waking = false))
        assertFalse(mapStillCovered(visible = true, entering = true, resting = false, waking = false))
        // A map that comes back from a rest is under its plain ground until it has drawn its place.
        assertTrue(mapStillCovered(visible = true, entering = true, resting = true, waking = false))
        assertTrue(mapStillCovered(visible = true, entering = true, resting = false, waking = true))
        // A hidden map covers nothing (the button goes with the map anyway).
        assertFalse(mapStillCovered(visible = false, entering = false, resting = true, waking = false))
    }
}
