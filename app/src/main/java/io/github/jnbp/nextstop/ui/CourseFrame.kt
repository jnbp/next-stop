package io.github.jnbp.nextstop.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.tan

/*
 * The line course opened from a tapped vehicle: where the camera looks first and where the names
 * of its ends stand. Pure logic, no Android.
 */

/** A course opened from a vehicle first shows the vehicle and this many stops ahead of it. */
const val AHEAD_STOPS = 5

/**
 * The points to frame for a vehicle at [vehicle] that has come [progress] stops along its
 * course (as StopClock.progress counts: 2.5 is halfway from the third stop to the fourth) of
 * [stops] (their positions, null where unknown): the vehicle and the next [count] stops with a
 * position. Near the end of the line only those still ahead (never the stops behind, which
 * would turn the frame against the direction of travel); at the end the last stop. Without a
 * progress the whole line.
 */
fun aheadFrame(
    stops: List<Pair<Double, Double>?>,
    progress: Float?,
    vehicle: Pair<Double, Double>?,
    count: Int = AHEAD_STOPS,
): List<Pair<Double, Double>> {
    val known = stops.filterNotNull()
    if (progress == null || stops.isEmpty()) return known
    val passed = floor(progress).toInt().coerceIn(0, stops.size - 1)
    val ahead = ArrayList<Pair<Double, Double>>(count + 1)
    var index = passed + 1
    while (index < stops.size && ahead.size < count) {
        stops[index]?.let { ahead.add(it) }
        index++
    }
    // Close to the end only the stops still ahead: never the stops behind, so the frame keeps
    // looking the way the vehicle goes (the zoom cap of the camera keeps a short way readable).
    // At the end itself (nothing ahead) the last stop with a position.
    if (ahead.isEmpty()) {
        index = passed
        while (index >= 0 && ahead.isEmpty()) {
            stops[index]?.let { ahead.add(it) }
            index--
        }
    }
    return listOfNotNull(vehicle) + ahead
}

/**
 * The top left corner, in pixels, of a name [width] × [height] beside the ring of a stop at
 * ([x], [y]) on a map whose free part spans [left]..[right] and [top]..[bottom]: right of the
 * ring by [gap] where it fits, else left of it, else pushed inside the free part; never across
 * its edges (by [margin]), so the name of an end of the line is never cut off.
 */
fun mapLabelSpot(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    gap: Float,
    margin: Float,
): Pair<Float, Float> {
    val spot = FloatArray(2)
    mapLabelSpot(x, y, width, height, left, top, right, bottom, gap, margin, spot)
    return spot[0] to spot[1]
}

/** As [mapLabelSpot], with the place written into [out] (x, y): nothing allocated for each frame. */
fun mapLabelSpot(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    gap: Float,
    margin: Float,
    out: FloatArray,
) {
    val minX = left + margin
    val maxX = right - margin - width
    val minY = top + margin
    val maxY = bottom - margin - height
    val onRight = x + gap
    val onLeft = x - gap - width
    val labelX = when {
        onRight in minX..maxX -> onRight
        onLeft in minX..maxX -> onLeft
        else -> onRight.coerceAtMost(maxX).coerceAtLeast(minX)
    }
    val labelY = (y - height / 2f).let { if (maxY >= minY) it.coerceIn(minY, maxY) else it }
    out[0] = labelX
    out[1] = labelY
}

/**
 * Where a stop at ([x], [y]) outside the free part of a map ([left]..[right], [top]..[bottom])
 * is pointed at from: the point on the edge of that part, [inset] inside it, on the way from
 * its middle toward the stop, written into [out] as x, y and the angle toward the stop (degrees
 * clockwise from straight up). False, with nothing written, where the stop lies inside the free
 * part (its own name is shown there) or its place is not known.
 */
fun edgePointer(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    inset: Float,
    out: FloatArray,
): Boolean {
    if (x.isNaN() || y.isNaN()) return false
    if (x in left..right && y in top..bottom) return false
    val middleX = (left + right) / 2f
    val middleY = (top + bottom) / 2f
    val halfWidth = maxOf(0f, (right - left) / 2f - inset)
    val halfHeight = maxOf(0f, (bottom - top) / 2f - inset)
    val dx = x - middleX
    val dy = y - middleY
    val reachX = if (dx != 0f) halfWidth / abs(dx) else Float.MAX_VALUE
    val reachY = if (dy != 0f) halfHeight / abs(dy) else Float.MAX_VALUE
    val share = minOf(reachX, reachY, 1f)
    out[0] = middleX + dx * share
    out[1] = middleY + dy * share
    out[2] = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    return true
}

/** Where a camera looks: its middle and zoom, straight north and flat. */
data class CourseView(val latitude: Double, val longitude: Double, val zoom: Double)

// The map's world is this many dp wide at zoom 0 (512 px tiles, as MapLibre counts).
private const val WORLD_DP = 512.0

private fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

private fun mercatorY(latitude: Double): Double {
    val rad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
    return (1.0 - ln(tan(PI / 4.0 + rad / 2.0)) / PI) / 2.0
}

private fun latitudeOf(y: Double): Double = Math.toDegrees(2.0 * atan(exp(PI * (1.0 - 2.0 * y))) - PI / 2.0)

/**
 * The camera that shows [points] whole in the part of a map [width] × [height] (dp) left free
 * by [left], [top], [right] and [bottom] (dp), centred in it, never closer than [maxZoom].
 * For a vehicle, [points] are the vehicle and its next stops (see aheadFrame): framed as they
 * are, never mirrored around the vehicle, so the vehicle stands near the edge it comes from
 * and the stops ahead fill the free part. [zoom] keeps that zoom instead of fitting one. Null
 * where nothing is left free or there is nothing to show.
 */
fun courseView(
    points: List<Pair<Double, Double>>,
    width: Double,
    height: Double,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
    maxZoom: Double,
    zoom: Double? = null,
): CourseView? {
    val freeWidth = width - left - right
    val freeHeight = height - top - bottom
    if (freeWidth <= 1.0 || freeHeight <= 1.0) return null
    if (points.isEmpty()) return null
    var minX = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    for (point in points) {
        val x = mercatorX(point.second)
        val y = mercatorY(point.first)
        minX = min(minX, x)
        maxX = maxOf(maxX, x)
        minY = min(minY, y)
        maxY = maxOf(maxY, y)
    }
    val centreX = (minX + maxX) / 2.0
    val centreY = (minY + maxY) / 2.0
    val spanX = maxX - minX
    val spanY = maxY - minY
    val fitX = if (spanX > 0.0) log2(freeWidth / (spanX * WORLD_DP)) else maxZoom
    val fitY = if (spanY > 0.0) log2(freeHeight / (spanY * WORLD_DP)) else maxZoom
    val shownZoom = zoom ?: min(min(fitX, fitY), maxZoom)
    // The middle of the free part lies this far (dp) from the middle of the map.
    val world = WORLD_DP * Math.pow(2.0, shownZoom)
    val targetX = centreX - (left - right) / 2.0 / world
    val targetY = centreY - (top - bottom) / 2.0 / world
    return CourseView(latitudeOf(targetY), targetX * 360.0 - 180.0, shownZoom)
}
