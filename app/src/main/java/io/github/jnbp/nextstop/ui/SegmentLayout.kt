package io.github.jnbp.nextstop.ui

/** How the parts of a choice stand where they have to fit their width (see SegmentedToggle). */
enum class SegmentLayout {
    /** Side by side, as always. */
    Row,

    /** Two columns: two or four parts that do not fit side by side. */
    Grid,

    /** One below the other, as radio rows: any other number that does not fit. */
    List,
}

/**
 * Where the labels of [labelWidths] (px, each on one line) stand in [available] px: side by
 * side where they fit with their [padding] on both sides and a [gap] between them (with
 * [evenly], every part as wide as the widest needs), else in a grid of two columns for two or
 * four parts, else in a list. An unbounded width always fits.
 */
fun segmentLayout(labelWidths: List<Int>, available: Int, padding: Int, gap: Int, evenly: Boolean): SegmentLayout {
    val count = labelWidths.size
    if (count <= 1 || available == Int.MAX_VALUE) return SegmentLayout.Row
    val parts = if (evenly) {
        (labelWidths.max() + 2 * padding) * count
    } else {
        labelWidths.sumOf { it + 2 * padding }
    }
    if (parts + gap * (count - 1) <= available) return SegmentLayout.Row
    return if (count == 2 || count == 4) SegmentLayout.Grid else SegmentLayout.List
}
