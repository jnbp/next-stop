package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.ChoiceMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #115: the marks on the tiles of the connection choice and the confirmation of a chosen tile. */
class ChoiceConfirmTest {
    @Test
    fun theMarksStandOnTheirTiles() {
        val marks = ChoiceMarks(earliest = 2, sprintEarlier = 1)
        assertEquals(TileMark.Earliest, tileMark(2, marks))
        assertEquals(TileMark.Sprint, tileMark(1, marks))
        assertNull(tileMark(0, marks))
        assertNull(tileMark(0, ChoiceMarks(null, null)))
    }

    @Test
    fun theConfirmationEndsAtRest() {
        // At rest (also with animations off: the value is 1 at once) the check is whole and nothing glows.
        assertEquals(1f, confirmCircle(1f), 0f)
        assertEquals(1f, confirmTick(1f), 0f)
        assertEquals(0f, confirmGlow(1f), 0f)
        // At the start nothing is drawn yet.
        assertEquals(0f, confirmCircle(0f), 0f)
        assertEquals(0f, confirmTick(0f), 0f)
        assertEquals(0f, confirmGlow(0f), 0f)
        // The circle overshoots a little before it settles, the tick follows it, the light peaks midway.
        assertTrue(confirmCircle(0.3f) > 1f)
        assertEquals(0f, confirmTick(0.25f), 0f)
        assertEquals(1f, confirmGlow(0.5f), 0.001f)
        var last = 0f
        for (step in 0..100) {
            val tick = confirmTick(step / 100f)
            assertTrue(tick >= last)
            last = tick
        }
    }
}
