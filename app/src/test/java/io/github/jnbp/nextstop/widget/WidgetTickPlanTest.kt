package io.github.jnbp.nextstop.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** The minute redraw of the widgets under the battery rule (see widgetTickPlan). */
class WidgetTickPlanTest {
    private val now = 10_000_000L

    @Test
    fun screenOffDoesNothing() {
        assertEquals(WidgetTickPlan(redraw = false, load = false), widgetTickPlan(false, 0L, now, saving = false, stale = true))
    }

    @Test
    fun screenJustOnLoadsStaleData() {
        // No redraw with the screen on before, or the last one long ago: the screen just came on.
        assertEquals(WidgetTickPlan(redraw = true, load = true), widgetTickPlan(true, 0L, now, saving = false, stale = true))
        assertEquals(WidgetTickPlan(redraw = true, load = true), widgetTickPlan(true, now - SCREEN_GAP_MILLIS - 1, now, false, true))
    }

    @Test
    fun screenStayingOnOnlyRedraws() {
        assertEquals(WidgetTickPlan(redraw = true, load = false), widgetTickPlan(true, now - 60_000L, now, false, stale = true))
    }

    @Test
    fun freshDataIsNotLoaded() {
        assertEquals(WidgetTickPlan(redraw = true, load = false), widgetTickPlan(true, 0L, now, false, stale = false))
    }

    @Test
    fun theSaverNeverLoadsByItself() {
        assertEquals(WidgetTickPlan(redraw = true, load = false), widgetTickPlan(true, 0L, now, saving = true, stale = true))
    }
}
