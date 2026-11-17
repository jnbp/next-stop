package io.github.jnbp.nextstop.ui

/*
 * The pills of the stop positions at the changes of a journey on the map ("3", "A") stand on
 * their positions; at a stop of many positions close together they lay on top of each other and
 * none could be read. [placeMarkPills] gives each pill a place of its own: on its position where
 * that is free, else moved above, below, right or left of it (drawn with a short leader to its
 * dot), else none, and the position shows as its dot alone until the map is zoomed in.
 */

/**
 * Places the pills of marks at [xs], [ys] (screen pixels), each [halfWidths] wide on either side
 * (0: a mark without a pill, only its dot) and [halfHeight] high on either side, in the order
 * given, the most important first. Each pill keeps clear of the pills placed before it and of
 * the dots ([dotRadius]) of all other marks, with [gap] between. Writes into [out] the shift of
 * each pill from its position, dx and dy in turn: 0, 0 on the position, NaN where it found no
 * room (and for marks without a pill).
 */
internal fun placeMarkPills(
    xs: FloatArray,
    ys: FloatArray,
    halfWidths: FloatArray,
    halfHeight: Float,
    dotRadius: Float,
    gap: Float,
    out: FloatArray,
) {
    val count = xs.size
    // The boxes taken by placed pills: left, top, right, bottom in turn.
    val placed = FloatArray(count * 4)
    var placedCount = 0
    for (index in 0 until count) {
        out[index * 2] = Float.NaN
        out[index * 2 + 1] = Float.NaN
        val halfWidth = halfWidths[index]
        if (halfWidth <= 0f) continue
        val x = xs[index]
        val y = ys[index]
        for (candidate in 0 until 5) {
            val dx = when (candidate) {
                3 -> halfWidth + dotRadius + gap
                4 -> -(halfWidth + dotRadius + gap)
                else -> 0f
            }
            val dy = when (candidate) {
                1 -> -(halfHeight + dotRadius + gap)
                2 -> halfHeight + dotRadius + gap
                else -> 0f
            }
            val left = x + dx - halfWidth
            val top = y + dy - halfHeight
            val right = x + dx + halfWidth
            val bottom = y + dy + halfHeight
            var free = true
            for (other in 0 until placedCount) {
                if (left < placed[other * 4 + 2] + gap && right + gap > placed[other * 4] &&
                    top < placed[other * 4 + 3] + gap && bottom + gap > placed[other * 4 + 1]
                ) {
                    free = false
                    break
                }
            }
            if (free) {
                for (other in 0 until count) {
                    if (other == index) continue
                    val ox = xs[other]
                    val oy = ys[other]
                    if (ox + dotRadius > left && ox - dotRadius < right && oy + dotRadius > top && oy - dotRadius < bottom) {
                        free = false
                        break
                    }
                }
            }
            if (!free) continue
            out[index * 2] = dx
            out[index * 2 + 1] = dy
            placed[placedCount * 4] = left
            placed[placedCount * 4 + 1] = top
            placed[placedCount * 4 + 2] = right
            placed[placedCount * 4 + 3] = bottom
            placedCount++
            break
        }
    }
}
