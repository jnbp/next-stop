package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackUnderGrowTest {
    @Test
    fun startsSmallAndHiddenAndEndsWhole() {
        assertEquals(BACK_UNDER_START_SCALE, backUnderScale(0f), 1e-6f)
        assertEquals(1f, backUnderScale(1f), 1e-6f)
        assertEquals(0f, backUnderAlpha(0f), 1e-6f)
        assertEquals(0f, backUnderAlpha(BACK_UNDER_FADE_FROM), 1e-6f)
        assertEquals(1f, backUnderAlpha(1f), 1e-6f)
        assertEquals(0.5f, backUnderAlpha((1f + BACK_UNDER_FADE_FROM) / 2f), 1e-6f)
    }

    @Test
    fun curvesNeverGoBackwards() {
        var scale = 0f
        var alpha = -1f
        for (i in 0..100) {
            val t = i / 100f
            assertTrue("scale at $t", backUnderScale(t) >= scale)
            assertTrue("alpha at $t", backUnderAlpha(t) >= alpha)
            scale = backUnderScale(t)
            alpha = backUnderAlpha(t)
        }
    }

    @Test
    fun followsTheGestureAndSettlesWithTheFade() {
        // Held: the progress alone.
        assertEquals(0.4f, backUnderShown(0.4f, 1f), 1e-6f)
        // Let go at 0.4: whole once the page in front has faded.
        assertEquals(0.7f, backUnderShown(0.4f, 0.5f), 1e-6f)
        assertEquals(1f, backUnderShown(0.4f, 0f), 1e-6f)
        // Called off: back to nothing with the progress.
        assertEquals(0f, backUnderShown(0f, 1f), 1e-6f)
    }

    @Test
    fun pivotLiesAtTheCardCentre() {
        assertEquals(3f, backUnderPivot(300f, 100f), 1e-6f)
        assertEquals(0.5f, backUnderPivot(300f, 0f), 1e-6f)
    }
}
