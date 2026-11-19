package io.github.jnbp.nextstop.wear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundScreenTest {
    @Test
    fun chordAndBottomAgree() {
        // A round screen of 192 dp: 14 dp above its lowest point it is about 100 dp wide.
        assertEquals(99.8f, RoundScreen.chord(192f, 14f), 0.1f)
        assertEquals(14f, RoundScreen.bottomFor(192f, RoundScreen.chord(192f, 14f)), 0.01f)
        assertEquals(192f, RoundScreen.chord(192f, 96f), 0.001f)
        assertEquals(0f, RoundScreen.chord(192f, 0f), 0.001f)
    }

    @Test
    fun detailsFitsInHalfTheRowOnTheSmallestScreen() {
        // 192 dp: 10 dp list padding each side, 12 dp card padding, half of the row less the 4 dp
        // gap, 2 x 12 dp button padding leave 48 dp for "Details" (12 sp, about 46 dp).
        assertEquals(48f, RoundScreen.journeyCardLabelRoom(192f), 0.001f)
        val details = "Details".length * 0.55f * 12f
        assertTrue(RoundScreen.journeyCardLabelRoom(192f) >= details)
        // The start button keeps the 48 dp of a touch target in its width as well.
        assertTrue(RoundScreen.journeyCardLabelRoom(192f) + 2 * 12f >= 48f)
        // Larger screens only have more room.
        assertTrue(RoundScreen.journeyCardLabelRoom(225f) > RoundScreen.journeyCardLabelRoom(192f))
    }
}
