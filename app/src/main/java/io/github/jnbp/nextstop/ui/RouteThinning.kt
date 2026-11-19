package io.github.jnbp.nextstop.ui

import java.lang.ref.WeakReference
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * Thinning the ways drawn on the map. Moved out of AppRoot.kt unchanged, with a memo: the map of
 * a followed trip works its ways out again every two seconds (the grey behind the vehicle), and
 * the way of an ICE across the country has tens of thousands of points, through which
 * Douglas-Peucker ran up to six times on the main thread each time (#71, #72). The same way (the
 * same list) now gives its thinned way from the memo. Pure logic, no Android.
 */

// A way on the map is drawn with no more points than this: enough for a line that follows
// the rails at every zoom of a city, and few enough to draw it again at every frame.
internal const val MAX_ROUTE_POINTS = 1_200

// Points of a way that lie closer than this to the line through their neighbours are left out.
private const val ROUTE_TOLERANCE_METRES = 2.0

// Ways shorter than this are thinned anew each time: cheap, and their lists are made anew anyway (walks).
private const val ROUTE_MEMO_MIN_POINTS = 2_000

// The thinned ways kept: a followed journey has a few rides, the map shows a few journeys.
private const val ROUTE_MEMO_SIZE = 12

// The last ways thinned, by the identity of their list (a way does not change once made), newest
// last. The long way itself is held weakly: the memo never keeps a way alive that the app let go.
private val routeMemo = ArrayList<Pair<WeakReference<List<Pair<Double, Double>>>, List<Pair<Double, Double>>>>(ROUTE_MEMO_SIZE)

/**
 * [path] with the points left out that add nothing to its shape (Douglas-Peucker): a point
 * that lies closer than [ROUTE_TOLERANCE_METRES] to the straight line between the points kept
 * around it. Leaving points out evenly instead cut the corners of a curve, so the line ran
 * beside the rails. Where more than [MAX_ROUTE_POINTS] stay, the tolerance grows. A long way
 * thinned before gives the same thinned way again at once.
 */
internal fun thinnedRoute(path: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    if (path.size <= 2) return path
    if (path.size < ROUTE_MEMO_MIN_POINTS) return thinnedNow(path)
    synchronized(routeMemo) {
        routeMemo.firstOrNull { it.first.get() === path }?.let { return it.second }
    }
    val kept = thinnedNow(path)
    synchronized(routeMemo) {
        routeMemo.removeAll { it.first.get() == null }
        if (routeMemo.size >= ROUTE_MEMO_SIZE) routeMemo.removeAt(0)
        routeMemo.add(WeakReference(path) to kept)
    }
    return kept
}

private fun thinnedNow(path: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    var tolerance = ROUTE_TOLERANCE_METRES
    var kept = simplified(path, tolerance)
    while (kept.size > MAX_ROUTE_POINTS) {
        tolerance *= 2
        kept = simplified(path, tolerance)
    }
    return kept
}

/** The points of [path] Douglas-Peucker keeps with [tolerance] in metres; both ends stay. */
private fun simplified(path: List<Pair<Double, Double>>, tolerance: Double): List<Pair<Double, Double>> {
    val scale = cos(Math.toRadians(path[0].first))
    val keep = BooleanArray(path.size)
    keep[0] = true
    keep[path.lastIndex] = true
    val limit = tolerance / 111_320.0
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to path.lastIndex)
    while (stack.isNotEmpty()) {
        val (first, last) = stack.removeLast()
        if (last - first < 2) continue
        val ax = path[first].second * scale
        val ay = path[first].first
        val dx = path[last].second * scale - ax
        val dy = path[last].first - ay
        val length = dx * dx + dy * dy
        var far = -1.0
        var farIndex = -1
        for (index in first + 1 until last) {
            val px = path[index].second * scale - ax
            val py = path[index].first - ay
            val share = if (length <= 0.0) 0.0 else ((px * dx + py * dy) / length).coerceIn(0.0, 1.0)
            val ex = px - dx * share
            val ey = py - dy * share
            val distance = sqrt(ex * ex + ey * ey)
            if (distance > far) {
                far = distance
                farIndex = index
            }
        }
        if (far > limit && farIndex > 0) {
            keep[farIndex] = true
            stack.addLast(first to farIndex)
            stack.addLast(farIndex to last)
        }
    }
    return path.filterIndexed { index, _ -> keep[index] }
}
