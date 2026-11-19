package io.github.jnbp.nextstop.data

/*
 * Where the small chips of a compact ride card go (#236): beside the main chip (the boarding
 * hint) where both fit in full, else on a line of their own below it, never by cutting the
 * hint. The small chips always end at the right edge. The same rule places the rating badge
 * beside the head of a change card (#242). Since #247 the ride card's hint wraps beside the
 * chips instead (hintChipArrangement, #264); the change card keeps chipRowPlacement.
 */

/** Beside the main chip, or on a line of their own below it. */
enum class ChipRowPlacement { Beside, Below }

/**
 * Where chips [chipsWidth] wide go next to a main chip that needs [mainWidth] on one line, in a
 * row [maxWidth] wide with [gap] between them: beside where both fit in full (or where one of
 * them is empty), else below.
 */
fun chipRowPlacement(mainWidth: Int, chipsWidth: Int, gap: Int, maxWidth: Int): ChipRowPlacement = when {
    mainWidth <= 0 || chipsWidth <= 0 -> ChipRowPlacement.Beside
    mainWidth + gap + chipsWidth <= maxWidth -> ChipRowPlacement.Beside
    else -> ChipRowPlacement.Below
}

/** #264: how the small chips of a compact ride card stand beside the boarding hint. */
enum class HintChipArrangement {
    /** Side by side at the right end of the hint's row. */
    Row,

    /** One above the other, right-aligned, in a narrow column at the right of the hint. */
    Stacked,

    /** Side by side on a line of their own below the hint, right-aligned. */
    Below,
}

/**
 * #264 (replaces the 40 % rule of #247): where the small chips [chipWidths] wide go next to a
 * boarding hint that needs [mainWidth] on one line, in a row [maxWidth] wide, with [gap] between
 * the hint and the chips (and between chips side by side). The hint keeps at least two thirds of
 * the row ([HINT_MIN_SHARE]) and wraps there, never cut:
 * - both fit on one line in full (or there is no hint or no chip): [HintChipArrangement.Row];
 * - the chips side by side fit in the rest beside two thirds: [HintChipArrangement.Row];
 * - else each chip fits in the right quarter ([HINT_STACKED_SHARE] for the hint): stacked there;
 * - else the chips go on a line of their own below the hint.
 * Chips 0 wide (a sun chip still fading in) count not.
 */
fun hintChipArrangement(
    mainWidth: Int,
    chipWidths: List<Int>,
    gap: Int,
    maxWidth: Int,
    minShare: Float = HINT_MIN_SHARE,
    stackedShare: Float = HINT_STACKED_SHARE,
): HintChipArrangement {
    val chips = chipWidths.filter { it > 0 }
    if (mainWidth <= 0 || chips.isEmpty()) return HintChipArrangement.Row
    val row = chips.sum() + gap * (chips.size - 1)
    if (mainWidth + gap + row <= maxWidth) return HintChipArrangement.Row
    if (row <= chipRoom(maxWidth, gap, minShare)) return HintChipArrangement.Row
    if (chips.max() <= chipRoom(maxWidth, gap, stackedShare)) return HintChipArrangement.Stacked
    return HintChipArrangement.Below
}

/** The width left for the chips at the right of a row [maxWidth] wide where the hint keeps [hintShare] of it. */
fun chipRoom(maxWidth: Int, gap: Int, hintShare: Float): Int =
    (maxWidth - gap - kotlin.math.ceil(maxWidth * hintShare.toDouble() - 0.001).toInt()).coerceAtLeast(0)

/** The least share of the row the boarding hint keeps beside the small chips side by side (#264). */
const val HINT_MIN_SHARE = 2f / 3f

/** The share of the row the boarding hint keeps beside the chips stacked in a column (#264). */
const val HINT_STACKED_SHARE = 0.75f

/** Where chips [chipsWidth] wide start in a row [rowWidth] wide: at its right edge (never left of 0). */
fun chipsStartX(rowWidth: Int, chipsWidth: Int): Int = (rowWidth - chipsWidth).coerceAtLeast(0)
