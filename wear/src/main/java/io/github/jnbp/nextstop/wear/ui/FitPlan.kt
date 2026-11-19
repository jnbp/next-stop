package io.github.jnbp.nextstop.wear.ui

import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Which lines of a live-trip page fit the screen, and how wide each may be (#180): the pages
 * never scroll. The lines stand centred between the time at the top and the page indicator;
 * on a round screen a line lower down or higher up gets only the width of the circle there.
 * Where they do not all fit, the line of the lowest priority goes first (on the ride page the
 * "next stop" before the arrival, before the station, before the label, before the number),
 * until the rest fits. Lines of [FIT_MUST] stay whatever happens.
 */

/** The priority of a line that never goes (the default of a line without one). */
const val FIT_MUST = Int.MAX_VALUE

/** The lines kept ([kept], indices in the order of the page), each with its top ([tops]) and width ([widths]), in px. */
data class FitPlan(val kept: List<Int>, val tops: List<Float>, val widths: List<Float>, val heights: List<Float>)

/**
 * The plan for lines of [priorities] (higher is more important) in the band [top]..[bottom] of a
 * screen [screenWidth] by [screenHeight] (round: [round]), [spacing] apart. [heightAt] gives the
 * height of a line at a width; [edge] keeps lines that far inside the round edge, [maxWidth]
 * caps every line (a square screen: the screen less its margins). Lines of no height take no
 * spacing. Where even the lines of [FIT_MUST] do not fit, they stand from the top of the band.
 */
fun planFit(
    priorities: List<Int>,
    heightAt: (index: Int, width: Float) -> Float,
    spacing: Float,
    top: Float,
    bottom: Float,
    screenWidth: Float,
    screenHeight: Float,
    round: Boolean,
    edge: Float,
    maxWidth: Float,
): FitPlan {
    val kept = priorities.indices.toMutableList()
    while (true) {
        val plan = arrange(kept, heightAt, spacing, top, bottom, screenWidth, screenHeight, round, edge, maxWidth)
        val used = plan.usedHeight(spacing)
        if (used <= bottom - top + FIT_SLACK) return plan
        // The least important line goes; of equal ones the lowest on the page.
        val drop = kept.filter { priorities[it] != FIT_MUST }.minWithOrNull(compareBy<Int>({ priorities[it] }, { -it })) ?: return plan
        kept.remove(drop)
    }
}

/** The width a line from [y0] to [y1] has on the screen: the narrower chord of the circle at its two edges. */
fun lineWidth(y0: Float, y1: Float, screenWidth: Float, screenHeight: Float, round: Boolean, edge: Float, maxWidth: Float): Float {
    val cap = minOf(maxWidth, screenWidth - 2 * edge)
    if (!round) return cap.coerceAtLeast(MIN_LINE_SHARE * screenWidth)
    val radius = minOf(screenWidth, screenHeight) / 2f
    val centre = screenHeight / 2f
    val far = maxOf(abs(y0 - centre), abs(y1 - centre))
    val chord = if (far >= radius) 0f else 2f * sqrt(radius * radius - far * far)
    return minOf(cap, chord - 2 * edge).coerceAtLeast(MIN_LINE_SHARE * screenWidth)
}

private fun FitPlan.usedHeight(spacing: Float): Float {
    val shown = heights.count { it > 0f }
    return heights.sum() + spacing * (shown - 1).coerceAtLeast(0)
}

// Lines placed, widths taken where they stand, heights again for those widths: a few rounds settle it.
private fun arrange(
    kept: List<Int>,
    heightAt: (Int, Float) -> Float,
    spacing: Float,
    top: Float,
    bottom: Float,
    screenWidth: Float,
    screenHeight: Float,
    round: Boolean,
    edge: Float,
    maxWidth: Float,
): FitPlan {
    val middle = lineWidth(screenHeight / 2f, screenHeight / 2f, screenWidth, screenHeight, round, edge, maxWidth)
    var widths = kept.map { middle }
    var heights = kept.mapIndexed { k, index -> heightAt(index, widths[k]) }
    var tops = place(heights, spacing, top, bottom)
    repeat(ARRANGE_ROUNDS) {
        widths = kept.indices.map { k -> lineWidth(tops[k], tops[k] + heights[k], screenWidth, screenHeight, round, edge, maxWidth) }
        heights = kept.mapIndexed { k, index -> heightAt(index, widths[k]) }
        tops = place(heights, spacing, top, bottom)
    }
    return FitPlan(kept.toList(), tops, widths, heights)
}

// The tops of lines of [heights], centred in the band (from its top where they do not fit).
private fun place(heights: List<Float>, spacing: Float, top: Float, bottom: Float): List<Float> {
    val shown = heights.count { it > 0f }
    val total = heights.sum() + spacing * (shown - 1).coerceAtLeast(0)
    var y = top + ((bottom - top - total) / 2f).coerceAtLeast(0f)
    return heights.map { height ->
        val at = y
        if (height > 0f) y += height + spacing
        at
    }
}

private const val ARRANGE_ROUNDS = 3

// A line is never narrower than this share of the screen (a line at the very edge of a circle).
private const val MIN_LINE_SHARE = 0.3f

// Rounding between planning and laying out.
private const val FIT_SLACK = 0.5f
