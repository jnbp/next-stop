package io.github.jnbp.nextstop.ui.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pinned status line of the map view (#225): shown while the sheet covers the blue box, without blinking at the edge. */
class HeroPinTest {
    // A blue box from y = 100 to y = 200.
    private val top = 100f
    private val bottom = 200f

    @Test
    fun coveredShareFollowsTheTopOfTheSheet() {
        assertEquals(0f, heroCovered(top, bottom, 400f), 0.001f)
        assertEquals(0.5f, heroCovered(top, bottom, 150f), 0.001f)
        assertEquals(1f, heroCovered(top, bottom, 20f), 0.001f)
        // Not measured yet: nothing covered.
        assertEquals(0f, heroCovered(0f, 0f, 20f), 0.001f)
    }

    @Test
    fun showsOnceTheBoxIsMostlyCoveredAndGoesOnceItIsMostlyFree() {
        assertFalse(heroPinShown(heroCovered(top, bottom, 400f), shown = false))
        assertTrue(heroPinShown(heroCovered(top, bottom, 130f), shown = false))
        assertTrue(heroPinShown(heroCovered(top, bottom, 20f), shown = false))
        assertFalse(heroPinShown(heroCovered(top, bottom, 180f), shown = true))
    }

    @Test
    fun keepsWhatItShowedInBetween() {
        // Half covered: no change either way, so a sheet resting there does not make it blink.
        val half = heroCovered(top, bottom, 150f)
        assertTrue(heroPinShown(half, shown = true))
        assertFalse(heroPinShown(half, shown = false))
    }

    @Test
    fun aSheetMovingUpAndDownShowsAndHidesItOnce() {
        var shown = false
        val changes = mutableListOf<Boolean>()
        for (sheet in listOf(400f, 250f, 190f, 160f, 140f, 120f, 60f, 120f, 150f, 175f, 190f, 300f)) {
            val next = heroPinShown(heroCovered(top, bottom, sheet), shown)
            if (next != shown) changes += next
            shown = next
        }
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun theBoxIsDrawnOnlyAboveTheSheet() {
        // Hero corners (Joel 19.11.): the sheet's top edge at 400: the whole box (100..200) and its shadow are drawn.
        assertEquals(300f, heroClipBottom(heroTop = 100f, sheetTop = 400f)!!, 0.0001f)
        // Pulled up over half of the box: drawn down to the sheet's edge only.
        assertEquals(50f, heroClipBottom(heroTop = 100f, sheetTop = 150f)!!, 0.0001f)
        // Fully over it (the top stage): nothing of it, not even at the sheet's rounded corners.
        assertEquals(0f, heroClipBottom(heroTop = 100f, sheetTop = 40f)!!, 0.0001f)
        // The sheet not placed yet: nothing clipped.
        assertEquals(null, heroClipBottom(heroTop = 100f, sheetTop = Float.MAX_VALUE))
    }
}
