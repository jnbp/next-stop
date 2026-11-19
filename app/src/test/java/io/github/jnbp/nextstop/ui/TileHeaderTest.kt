package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The title of a place tile has priority over its travel time (#99). */
class TileHeaderTest {
    @Test
    fun theTimeStaysBesideATitleThatFits() {
        assertFalse(tileTimeBelowTitle(titleWidth = 120, timeWidth = 100, gap = 10, maxWidth = 300))
        assertFalse(tileTimeBelowTitle(titleWidth = 190, timeWidth = 100, gap = 10, maxWidth = 300))
    }

    @Test
    fun theTimeGoesBelowWhereTheTitleWouldBeCut() {
        // "Zuhause" with a long "4:27 h" on a narrow tile: the title stays whole.
        assertTrue(tileTimeBelowTitle(titleWidth = 191, timeWidth = 100, gap = 10, maxWidth = 300))
        assertTrue(tileTimeBelowTitle(titleWidth = 400, timeWidth = 10, gap = 10, maxWidth = 300))
    }

    @Test
    fun noTimeMovesNothing() {
        assertFalse(tileTimeBelowTitle(titleWidth = 400, timeWidth = 0, gap = 10, maxWidth = 300))
    }
}
