package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #194: a page put away for a walk on the big map leaves nothing of itself in the window. */
class PageAwayTest {
    @Test
    fun fullyAwayTheTopEdgeIsBelowTheWindow() {
        // Pages from 80 to 2200 in a window 2340 high: 140 of navigation bar below them.
        val top = 80f
        val height = 2120f
        val window = 2340f
        val shadow = 48f
        val moved = pageAwayOffset(height, belowPages = window - (top + height), shadow = shadow, away = 1f)
        // What is drawn of it starts at its top, less the shadow above that edge.
        assertTrue(top + moved - shadow >= window)
    }

    @Test
    fun theOldShiftLeftTheNavigationBarStrip() {
        // Moved by its own height only, the top stopped where the navigation bar begins.
        val top = 80f
        val height = 2120f
        assertTrue(top + pageAwayOffset(height, belowPages = 0f, shadow = 0f, away = 1f) < 2340f)
    }

    @Test
    fun inPlaceAndHalfway() {
        assertEquals(0f, pageAwayOffset(2000f, 100f, 50f, 0f), 0f)
        assertEquals(1075f, pageAwayOffset(2000f, 100f, 50f, 0.5f), 0.001f)
        // Odd numbers (a window not yet measured) never pull it up.
        assertEquals(2000f, pageAwayOffset(2000f, -30f, -5f, 1f), 0f)
    }
}
