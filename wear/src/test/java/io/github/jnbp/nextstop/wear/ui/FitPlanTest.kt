package io.github.jnbp.nextstop.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live-trip pages that never scroll (#180): what is dropped first, and the round widths. */
class FitPlanTest {
    // A small round watch (192 dp at density 1) and a large one.
    private val small = 192f
    private val large = 240f

    private fun plan(priorities: List<Int>, heights: List<Float>, size: Float, wrapBelow: Float = 0f) = planFit(
        priorities = priorities,
        // A line of text that needs two lines where it is narrower than [wrapBelow].
        heightAt = { index, width -> if (width < wrapBelow) heights[index] * 2 else heights[index] },
        spacing = 2f,
        top = 34f,
        bottom = size - 22f,
        screenWidth = size,
        screenHeight = size,
        round = true,
        edge = 8f,
        maxWidth = size - 40f,
    )

    // The ride page: number, label, station, arrival, next stop (#180).
    private val ride = listOf(100, 90, 80, 70, 60)

    @Test
    fun everythingStaysWhereItFits() {
        val fit = plan(ride, listOf(40f, 14f, 18f, 14f, 14f), large)
        assertEquals(listOf(0, 1, 2, 3, 4), fit.kept)
    }

    @Test
    fun theLeastImportantLinesGoFirstOnASmallScreen() {
        // 136 px of band: 140 with all five, so the next stop goes and the rest fits.
        val fit = plan(ride, listOf(56f, 16f, 20f, 20f, 20f), small)
        assertEquals(listOf(0, 1, 2, 3), fit.kept)
        val tight = plan(ride, listOf(80f, 16f, 20f, 20f, 20f), small)
        assertEquals(listOf(0, 1, 2), tight.kept)
        assertTrue(tight.tops.last() + tight.heights.last() <= small - 22f + 0.5f)
    }

    @Test
    fun linesThatMustStayStayAndStartAtTheTop() {
        val fit = plan(listOf(FIT_MUST, FIT_MUST, 10), listOf(80f, 80f, 10f), small)
        assertEquals(listOf(0, 1), fit.kept)
        assertEquals(34f, fit.tops.first())
    }

    @Test
    fun linesAwayFromTheMiddleAreNarrower() {
        val middle = lineWidth(96f, 96f, small, small, round = true, edge = 8f, maxWidth = 500f)
        val low = lineWidth(140f, 160f, small, small, round = true, edge = 8f, maxWidth = 500f)
        assertEquals(176f, middle, 0.01f)
        assertTrue(low < middle)
        // A square screen gives every line the same width.
        assertEquals(176f, lineWidth(140f, 160f, small, small, round = false, edge = 8f, maxWidth = 500f), 0.01f)
    }

    @Test
    fun aLineThatWrapsLowDownIsCountedWithItsTwoLines() {
        // Narrow low on the circle the station wraps: the plan counts that and drops more.
        val fit = plan(ride, listOf(44f, 16f, 20f, 16f, 16f), small, wrapBelow = 150f)
        val used = fit.heights.sum() + 2f * (fit.kept.size - 1)
        assertTrue(used <= small - 22f - 34f + 0.5f)
    }
}
