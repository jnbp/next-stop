package io.github.jnbp.nextstop.ui

import kotlin.math.cos
import kotlin.math.sqrt

/** A following camera is moved once the dot has gone at least this far on the screen since its last move (px). */
const val FOLLOW_MIN_STEP_PX = 0.3

/**
 * True when the camera that follows the own dot is due a move by ([north], [east]) degrees,
 * gathered since its last move, at [latitude] where a pixel spans [metresPerPixel]: from
 * [FOLLOW_MIN_STEP_PX] on. Less is not to be seen; the map would be drawn again for nothing
 * in every frame of the dot's last slow easing (#71). Without a scale (0 or less) it is always due.
 */
fun followMoveDue(north: Double, east: Double, latitude: Double, metresPerPixel: Double): Boolean {
    if (metresPerPixel <= 0.0 || metresPerPixel.isNaN()) return north != 0.0 || east != 0.0
    val dy = north * 111_320.0
    val dx = east * 111_320.0 * cos(Math.toRadians(latitude))
    return sqrt(dx * dx + dy * dy) / metresPerPixel >= FOLLOW_MIN_STEP_PX
}

/**
 * True when [a] and [b] are the same way: the same list, or the same points in the same order.
 * A refresh of the followed trip brings its ways as new lists of the same points; the dot keeps
 * the arrays it made of the way then, instead of copying it again (#71).
 */
fun sameWay(a: List<Pair<Double, Double>>?, b: List<Pair<Double, Double>>?): Boolean {
    if (a === b) return true
    if (a == null || b == null || a.size != b.size) return false
    for (i in a.indices) {
        val p = a[i]
        val q = b[i]
        if (p.first != q.first || p.second != q.second) return false
    }
    return true
}
