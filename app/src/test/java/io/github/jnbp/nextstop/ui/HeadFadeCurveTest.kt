package io.github.jnbp.nextstop.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #238: what scrolls beneath a pinned ride head fades out before it is behind the head. */
class HeadFadeCurveTest {
    // A head from 0 to 60 px, opaque again 10 px below it.
    private fun alpha(y: Float) = underHeadAlpha(y, headTop = 0f, headBottom = 60f, clear = 10f)

    @Test
    fun transparentFromTheMiddleOfTheHeadUp() {
        assertEquals(0f, alpha(30f), 0.0001f)
        assertEquals(0f, alpha(5f), 0.0001f)
        assertEquals(0f, alpha(-40f), 0.0001f)
    }

    @Test
    fun opaqueFromALittleBelowTheHead() {
        assertEquals(1f, alpha(70f), 0.0001f)
        assertEquals(1f, alpha(400f), 0.0001f)
    }

    @Test
    fun linearInBetweenAndAlreadyFadingAtTheBottomEdge() {
        assertEquals(0.5f, alpha(50f), 0.0001f)
        assertEquals(0.75f, alpha(60f), 0.0001f)
        assertTrue(alpha(45f) < alpha(55f))
    }

    @Test
    fun aHeadPushedUpTakesItsFadeAlong() {
        // Pushed 20 px above the top by the next ride's head: the fade moves with it.
        assertEquals(0f, underHeadAlpha(10f, headTop = -20f, headBottom = 40f, clear = 10f), 0.0001f)
        assertEquals(1f, underHeadAlpha(50f, headTop = -20f, headBottom = 40f, clear = 10f), 0.0001f)
    }

    @Test
    fun aHeadWithoutHeightFadesNothing() {
        assertEquals(1f, underHeadAlpha(0f, headTop = 0f, headBottom = 0f, clear = 10f), 0.0001f)
    }

    @Test
    fun onlyAPinnedHeadWithSomethingBeneathCovers() {
        // Resting at the very top: the rows under it stay as they are.
        assertFalse(headCoversContent(headIndex = 2, headOffset = 0, firstIndex = 2, firstScrolled = 0))
        // Partly scrolled away and held at the top, or other items beneath it.
        assertTrue(headCoversContent(headIndex = 2, headOffset = 0, firstIndex = 2, firstScrolled = 12))
        assertTrue(headCoversContent(headIndex = 2, headOffset = 0, firstIndex = 4, firstScrolled = 0))
        // Pushed up by the next head.
        assertTrue(headCoversContent(headIndex = 2, headOffset = -14, firstIndex = 6, firstScrolled = 3))
        // Further down the list: not pinned.
        assertFalse(headCoversContent(headIndex = 5, headOffset = 120, firstIndex = 1, firstScrolled = 0))
    }

    @Test
    fun theBandOfTheMaskIsTheBandOfTheCurve() {
        // The masks draw a linear gradient from underHeadZero to underHeadOne: the curve's 0 and 1.
        assertEquals(30f, underHeadZero(0f, 60f), 0.0001f)
        assertEquals(70f, underHeadOne(60f, 10f), 0.0001f)
        assertEquals(0f, alpha(underHeadZero(0f, 60f)), 0.0001f)
        assertEquals(1f, alpha(underHeadOne(60f, 10f)), 0.0001f)
        // A negative clear counts as none.
        assertEquals(60f, underHeadOne(60f, -5f), 0.0001f)
    }

    @Test
    fun aPinnedLineFadesOnlyScrolledContent() {
        // #245: the status line of the Live-Fahrt sheet, 2..44 px; content at rest starts 10 px below it.
        assertFalse(pinCoversContent(pinBottom = 44f, scrolled = 0))
        assertTrue(pinCoversContent(pinBottom = 44f, scrolled = 1))
        // No line shown (or gone): nothing fades, however far the content is scrolled.
        assertFalse(pinCoversContent(pinBottom = 0f, scrolled = 300))
        // The first content at rest is fully visible.
        assertEquals(1f, underHeadAlpha(54f, headTop = 2f, headBottom = 44f, clear = 10f), 0.0001f)
        // Scrolled up to the line's middle it is gone.
        assertEquals(0f, underHeadAlpha(23f, headTop = 2f, headBottom = 44f, clear = 10f), 0.0001f)
    }

    @Test
    fun nothingDrawsBetweenTheHeaderAndThePinnedLine() {
        // #258: the status line 2..44 px below the top of the sheet's list: above its middle the
        // list is clipped, at rest as well as scrolled, so no strip shows above the line.
        assertEquals(23f, pinClipTop(pinTop = 2f, pinBottom = 44f)!!, 0.0001f)
        // The calm view's blue box, 4..180 px.
        assertEquals(92f, pinClipTop(pinTop = 4f, pinBottom = 180f)!!, 0.0001f)
        // No line (or one not measured yet): nothing is clipped.
        assertNull(pinClipTop(pinTop = 2f, pinBottom = 0f))
        assertNull(pinClipTop(pinTop = 2f, pinBottom = 2f))
        // The first content at rest (10 px below the line) lies below the clip.
        assertTrue(54f > pinClipTop(pinTop = 2f, pinBottom = 44f)!!)
    }

    @Test
    fun thePinnedHeadRoundsItsBottomOverTheLastFortyDp() {
        // Gap 8, ramp 40, card radius 24 (px for dp).
        assertEquals(0f, pushCornerRadius(distance = 300f, gap = 8f, ramp = 40f, radius = 24f), 0.0001f)
        assertEquals(0f, pushCornerRadius(distance = 48f, gap = 8f, ramp = 40f, radius = 24f), 0.0001f)
        assertEquals(12f, pushCornerRadius(distance = 28f, gap = 8f, ramp = 40f, radius = 24f), 0.0001f)
        assertEquals(24f, pushCornerRadius(distance = 8f, gap = 8f, ramp = 40f, radius = 24f), 0.0001f)
        // Pushed by the list itself: fully round.
        assertEquals(24f, pushCornerRadius(distance = 0f, gap = 8f, ramp = 40f, radius = 24f), 0.0001f)
        assertEquals(0f, pushCornerRadius(distance = 0f, gap = 8f, ramp = 40f, radius = 0f), 0.0001f)
    }

    @Test
    fun theNextHeadPushesKeepingTheGap() {
        assertEquals(0f, pushLift(distance = 30f, gap = 8f), 0.0001f)
        assertEquals(0f, pushLift(distance = 8f, gap = 8f), 0.0001f)
        assertEquals(3f, pushLift(distance = 5f, gap = 8f), 0.0001f)
        assertEquals(8f, pushLift(distance = 0f, gap = 8f), 0.0001f)
        // The gap seen stays the same from the touch on: distance + lift.
        for (d in listOf(0f, 2f, 5f, 8f)) assertEquals(8f, d + pushLift(d, 8f), 0.0001f)
    }
}
