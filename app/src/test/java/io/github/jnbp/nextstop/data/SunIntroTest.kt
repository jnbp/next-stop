package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The explanation of the sunny side opens once, with the first hint (#90). */
class SunIntroTest {
    @Test
    fun opensWithTheFirstHintOnly() {
        assertTrue(sunIntroDue(seen = false, hintShown = true, open = false))
        assertFalse(sunIntroDue(seen = true, hintShown = true, open = false))
        assertFalse(sunIntroDue(seen = false, hintShown = false, open = false))
        // One at a time: a second row with a hint does not open another one.
        assertFalse(sunIntroDue(seen = false, hintShown = true, open = true))
    }

    @Test
    fun theSunWandersOverTheTrainAndBack() {
        val (startX, startY) = sunIntroSunPoint(0f)
        val (midX, midY) = sunIntroSunPoint(0.5f)
        val (endX, endY) = sunIntroSunPoint(1f)
        assertEquals(0.18f, startX, 1e-5f)
        assertEquals(0.5f, midX, 1e-5f)
        assertEquals(0.82f, endX, 1e-5f)
        // Highest in the middle, as high at both ends, always above the train (y < 0.5).
        assertTrue(midY < startY)
        assertEquals(startY, endY, 1e-5f)
        for (i in 0..20) assertTrue(sunIntroSunPoint(i / 20f).second < 0.5f)
        assertEquals(sunIntroSunPoint(0f), sunIntroSunPoint(-1f))
    }
}
