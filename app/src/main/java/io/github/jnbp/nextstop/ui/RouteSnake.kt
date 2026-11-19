package io.github.jnbp.nextstop.ui

import kotlin.math.cos
import kotlin.math.sqrt

/*
 * The ways of one journey draw themselves in as one path (#69): the first leg from its start
 * to its end, then the next one from where that one ended, and so on, instead of all legs at
 * once. One animation runs from 0 to 1 over the whole journey; each leg takes the stretch of
 * it that matches its length, a short walk at least a small share, so it is seen to draw too.
 * Pure: worked out once per journey, read per frame without allocations.
 */

/** No leg takes less than this share of the draw-in, so a few steps of walking still show. */
const val SNAKE_MIN_SHARE = 0.08f

/**
 * Where each leg starts and ends within the draw-in, as fractions of it: [index] * 2 is the
 * start of leg [index], [index] * 2 + 1 its end. The legs follow each other in their order, each
 * as long as its share of [lengths] (at least [SNAKE_MIN_SHARE] of the whole, where there is
 * room for it). Legs of no length (one point) take none; all of no length end at the same time.
 */
fun snakeBounds(lengths: DoubleArray): FloatArray {
    val count = lengths.size
    val bounds = FloatArray(count * 2)
    if (count == 0) return bounds
    val total = lengths.sumOf { it.coerceAtLeast(0.0) }
    if (total <= 0.0) {
        for (index in 0 until count) bounds[index * 2 + 1] = 1f
        return bounds
    }
    val drawn = lengths.count { it > 0.0 }
    val floor = if (drawn * SNAKE_MIN_SHARE < 1f) SNAKE_MIN_SHARE.toDouble() else 0.0
    // The short legs take the least share, the others share the rest by their length.
    val short = BooleanArray(count) { lengths[it] > 0.0 && lengths[it] / total < floor }
    val shortCount = short.count { it }
    val longSum = (0 until count).sumOf { if (!short[it] && lengths[it] > 0.0) lengths[it] else 0.0 }
    val rest = 1.0 - floor * shortCount
    val weights = DoubleArray(count) {
        when {
            lengths[it] <= 0.0 -> 0.0
            short[it] -> floor
            else -> lengths[it] / longSum * rest
        }
    }
    var start = 0.0
    for (index in 0 until count) {
        val end = if (index == count - 1) 1.0 else (start + weights[index]).coerceAtMost(1.0)
        bounds[index * 2] = start.toFloat()
        bounds[index * 2 + 1] = end.toFloat()
        start = end
    }
    return bounds
}

/**
 * How much of leg [index] has drawn itself when [shown] of the whole draw-in has (see
 * [snakeBounds]): 0 before its turn, 1 once it is done, in between as far as its stretch went.
 */
fun snakeShare(bounds: FloatArray, index: Int, shown: Float): Float {
    if (shown >= 1f) return 1f
    val start = bounds[index * 2]
    val end = bounds[index * 2 + 1]
    if (shown <= start) return if (end <= start && shown >= end) 1f else 0f
    if (shown >= end || end <= start) return 1f
    return (shown - start) / (end - start)
}

/** The length of a way through [points] (latitude, longitude), in metres, good enough for a city. */
fun wayMeters(points: List<Pair<Double, Double>>): Double {
    var sum = 0.0
    for (index in 1 until points.size) {
        val (lat1, lon1) = points[index - 1]
        val (lat2, lon2) = points[index]
        val north = (lat2 - lat1) * METERS_PER_DEGREE
        val east = (lon2 - lon1) * METERS_PER_DEGREE * cos(Math.toRadians((lat1 + lat2) / 2))
        sum += sqrt(north * north + east * east)
    }
    return sum
}

/** How long the draw-in of a journey of [legs] legs takes: [baseMillis] for one, a little more for each further one. */
fun snakeMillis(baseMillis: Int, legs: Int): Int =
    (baseMillis + SNAKE_EXTRA_MILLIS_PER_LEG * (legs - 1).coerceAtLeast(0)).coerceAtMost(baseMillis + SNAKE_MAX_EXTRA_MILLIS)

// Per further leg the draw-in takes this much longer, at most SNAKE_MAX_EXTRA_MILLIS in all:
// about as long as before, so each leg is not rushed.
private const val SNAKE_EXTRA_MILLIS_PER_LEG = 90
private const val SNAKE_MAX_EXTRA_MILLIS = 300

private const val METERS_PER_DEGREE = 111_320.0
