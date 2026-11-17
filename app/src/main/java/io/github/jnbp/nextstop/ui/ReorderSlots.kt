package io.github.jnbp.nextstop.ui

// The arithmetic of dragging a row of a list to a new place in it, apart from Compose so it can
// be tested. [heights] are the rows' heights in their order, [gap] the space between two rows,
// both in pixels; none of these functions allocates, so they can run on every frame of a drag.

/**
 * How far the row at [from] moves to land in slot [to]: the rows it passes, each with its gap;
 * negative upwards.
 */
internal fun slotDelta(from: Int, to: Int, heights: IntArray, gap: Float): Float {
    var delta = 0f
    if (to > from) {
        for (i in from + 1..to) delta += heights[i] + gap
    } else {
        for (i in to until from) delta -= heights[i] + gap
    }
    return delta
}

/**
 * The slot the row at [from], moved by [offset], would drop into: it takes the place of a
 * neighbour once its leading edge covers half of that neighbour.
 */
internal fun dropSlot(from: Int, offset: Float, heights: IntArray, gap: Float): Int {
    var slot = from
    var passed = 0f
    if (offset > 0f) {
        while (slot < heights.lastIndex) {
            val next = heights[slot + 1] + gap
            if (offset < passed + next / 2f) break
            passed += next
            slot++
        }
    } else {
        while (slot > 0) {
            val next = heights[slot - 1] + gap
            if (-offset < passed + next / 2f) break
            passed += next
            slot--
        }
    }
    return slot
}

/**
 * How far the row at [index] steps aside while the row at [from] is held over slot [to]: the
 * rows it passed move one place towards where it came from. Nothing while no row is held
 * ([from] below 0).
 */
internal fun stepAside(index: Int, from: Int, to: Int, heights: IntArray, gap: Float): Float = when {
    from < 0 || index == from -> 0f
    index in from + 1..to -> -(heights[from] + gap)
    index in to until from -> heights[from] + gap
    else -> 0f
}

/** The list with the item at [from] taken out and put back in at [to]. */
internal fun <T> List<T>.movedTo(from: Int, to: Int): List<T> {
    val list = toMutableList()
    list.add(to, list.removeAt(from))
    return list
}
