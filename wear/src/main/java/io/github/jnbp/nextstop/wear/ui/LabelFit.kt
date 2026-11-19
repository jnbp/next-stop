package io.github.jnbp.nextstop.wear.ui

/*
 * How the label of a watch button fits its width without splitting a word (#179): at large font
 * scales or low on the round screen "Übersicht" broke into "Übersich" and "t", because a word
 * wider than the line is cut where the line ends. The label shrinks until its widest word fits
 * (down to [MIN_LABEL_SCALE]); two lines are fine, but only at word boundaries.
 */

/** The smallest share of its style's size a button label shrinks to before it ends in an ellipsis instead. */
const val MIN_LABEL_SCALE = 0.7f

// A little room for rounding between measuring the word and laying it out.
private const val LABEL_FIT_SLACK = 0.98f

/** The words of a label, where it may break into a second line. */
fun labelWords(text: String): List<String> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/**
 * The share of its font size a label needs so that its widest word ([widestWord], measured at
 * the full size) fits [available]: 1 where it fits as it is, less down to [minScale], null where
 * even that is too wide (then the label stays on one line and ends in an ellipsis, never cut
 * inside a word). Unknown widths (0 or less) leave the label as it is.
 */
fun wordFitScale(widestWord: Float, available: Float, minScale: Float = MIN_LABEL_SCALE): Float? {
    if (widestWord <= 0f || available <= 0f || widestWord <= available) return 1f
    val scale = available / widestWord * LABEL_FIT_SLACK
    return if (scale >= minScale) scale else null
}

/** True where pieces of [widths], [gap] apart, fit [available] on one line (unknown width: no). */
fun fitsOnOneLine(widths: List<Float>, gap: Float, available: Float): Boolean {
    if (available <= 0f) return false
    val total = widths.sum() + gap * (widths.size - 1).coerceAtLeast(0)
    return total <= available
}
