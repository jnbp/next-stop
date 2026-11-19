package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderReturnTest {
    private val room = 96

    @Test
    fun overviewStandsStillWhileTheChipsComeBack() {
        // #249: back from the page of a journey, the overview is where it ends from the first frame on.
        for (step in 0..10) {
            assertEquals(room, headerRestOffset(room, out = 1f - step / 10f, collapsed = false))
        }
    }

    @Test
    fun overviewSlidesUpWhileTheChipsFoldAway() {
        assertEquals(room, headerRestOffset(room, out = 0f, collapsed = true))
        assertEquals(room / 2, headerRestOffset(room, out = 0.5f, collapsed = true))
        assertEquals(0, headerRestOffset(room, out = 1f, collapsed = true))
    }

    @Test
    fun closingPageGoesTheWayItWentBefore() {
        // Before #249 the overview (and the page in its box) lay at round(room * (1 - out)) while
        // the chips came back; the page alone keeps that way now.
        for (step in 0..20) {
            val out = 1f - step / 20f
            val before = kotlin.math.round(room * (1f - out))
            val now = headerRestOffset(room, out, collapsed = false) + headerReturnLift(room, out, closing = true)
            assertEquals(before, now, 0.5f)
        }
    }

    @Test
    fun noLiftWhenNotClosing() {
        assertEquals(0f, headerReturnLift(room, out = 0.7f, closing = false), 0f)
        assertEquals(0f, headerReturnLift(room, out = 0f, closing = true), 0f)
    }
}
