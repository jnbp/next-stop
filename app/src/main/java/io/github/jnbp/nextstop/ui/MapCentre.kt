package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.tan

/*
 * Centring the map on the traveller (#273): the position goes to the middle of the part of the
 * map that the page leaves visible (below the blue box and the chips on top, above the sheet at
 * the bottom, right of a side panel), not to the middle of the screen, with a zoom that shows
 * the next stop ahead where it is near. Pure logic, no Android: pixels of the map, Web Mercator.
 */

/** The part of the map not covered by the page, in pixels of the map. */
data class VisibleRoom(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centreX: Float get() = (left + right) / 2f
    val centreY: Float get() = (top + bottom) / 2f
}

/**
 * The visible room of a map [width] by [height] pixels: below [topCover] (the lower edge of what
 * lies over its top), above [bottomCover] (the upper edge of what lies over its bottom), right of
 * [startCover] (a side panel); an edge that is unknown (NaN) or outside the map counts as none.
 * Where less than [minHeight] is left between top and bottom (a sheet pulled up), the whole
 * height counts. Null for a map without a size.
 */
fun visibleRoom(
    width: Float,
    height: Float,
    topCover: Float,
    bottomCover: Float,
    startCover: Float = Float.NaN,
    minHeight: Float = 0f,
): VisibleRoom? {
    if (!(width > 0f) || !(height > 0f)) return null
    val top = if (topCover.isFinite() && topCover > 0f && topCover < height) topCover else 0f
    val bottom = if (bottomCover.isFinite() && bottomCover > top && bottomCover < height) bottomCover else height
    val left = if (startCover.isFinite() && startCover > 0f && startCover < width * 0.8f) startCover else 0f
    if (bottom - top < minHeight) return VisibleRoom(left, 0f, width, height)
    return VisibleRoom(left, top, width, bottom)
}

/** Web Mercator x of [longitude] as a share of the world's width (0 to 1). */
internal fun centreMercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

/** Web Mercator y of [latitude] as a share of the world's height (0 at the north edge, 1 at the south). */
internal fun centreMercatorY(latitude: Double): Double {
    val phi = Math.toRadians(latitude.coerceIn(-85.0511, 85.0511))
    return (1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0
}

/** The latitude of the Web Mercator [y] (see [centreMercatorY]). */
internal fun centreLatitudeOf(y: Double): Double = Math.toDegrees(2.0 * atan(exp(PI * (1.0 - 2.0 * y))) - PI / 2.0)

/** The world's width in pixels at [zoom]: MapLibre draws tiles of 512 dp. */
internal fun worldPixels(zoom: Double, density: Float): Double = 512.0 * density * Math.pow(2.0, zoom)

/**
 * The zoom that keeps [ahead] in view with [own] in the middle of [room]: the way from the one
 * to the other, turned as the map is turned ([bearing], degrees), fits into half the room less
 * [margin] on each side. Without a point ahead (or one on top of the position) [maxZoom]; never
 * further out than [minZoom], so a far stop leaves the position close up instead of the whole
 * way.
 */
fun centreZoom(
    own: Pair<Double, Double>,
    ahead: Pair<Double, Double>?,
    room: VisibleRoom,
    density: Float,
    bearing: Double,
    margin: Float,
    minZoom: Double,
    maxZoom: Double,
): Double {
    if (ahead == null) return maxZoom
    val dx = centreMercatorX(ahead.second) - centreMercatorX(own.second)
    val dy = centreMercatorY(ahead.first) - centreMercatorY(own.first)
    if (hypot(dx, dy) < 1e-12) return maxZoom
    val b = Math.toRadians(bearing)
    // On the screen: the map turned by the bearing (the bearing's direction points up).
    val sx = abs(dx * cos(b) + dy * sin(b))
    val sy = abs(-dx * sin(b) + dy * cos(b))
    val halfWidth = (room.width / 2f - margin).coerceAtLeast(1f).toDouble()
    val halfHeight = (room.height / 2f - margin).coerceAtLeast(1f).toDouble()
    val world = minOf(if (sx > 0.0) halfWidth / sx else Double.MAX_VALUE, if (sy > 0.0) halfHeight / sy else Double.MAX_VALUE)
    val zoom = log2(world / (512.0 * density))
    if (zoom.isNaN()) return maxZoom
    return zoom.coerceIn(minZoom, maxZoom)
}

/**
 * Where the camera goes (its target, the middle of the map of [width] by [height] pixels) so that
 * [own] shows in the middle of [room] at [zoom] with the map turned by [bearing] (degrees).
 */
fun centreTarget(
    own: Pair<Double, Double>,
    room: VisibleRoom,
    width: Float,
    height: Float,
    zoom: Double,
    density: Float,
    bearing: Double,
): Pair<Double, Double> {
    // From the position to the middle of the map, on the screen (pixels, y down).
    val sx = (width / 2f - room.centreX).toDouble()
    val sy = (height / 2f - room.centreY).toDouble()
    if (abs(sx) < 0.5 && abs(sy) < 0.5) return own
    val b = Math.toRadians(bearing)
    // Back from the turned screen to the map (the inverse of the turn in centreZoom).
    val wx = sx * cos(b) - sy * sin(b)
    val wy = sx * sin(b) + sy * cos(b)
    val world = worldPixels(zoom, density)
    val x = centreMercatorX(own.second) + wx / world
    val y = centreMercatorY(own.first) + wy / world
    var longitude = x * 360.0 - 180.0
    if (longitude > 180.0) longitude -= 360.0
    if (longitude < -180.0) longitude += 360.0
    return centreLatitudeOf(y) to longitude
}

/** Metres between two points (flat, scaled for the latitude; fine for the short ways of a ride). */
internal fun flatMetres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val k = cos(Math.toRadians((a.first + b.first) / 2.0))
    return hypot(a.first - b.first, (a.second - b.second) * k) * 111_195.0
}

/** The index of the point of [way] nearest to [point]. */
private fun closestIndex(way: List<Pair<Double, Double>>, point: Pair<Double, Double>): Int {
    var best = 0
    var bestDistance = Double.MAX_VALUE
    way.forEachIndexed { index, p ->
        val d = flatMetres(p, point)
        if (d < bestDistance) {
            bestDistance = d
            best = index
        }
    }
    return best
}

/**
 * The next stop of a ride ahead of [own]: of its [stops] in their order, the first that lies
 * further along its [path] than the position and more than [minMetres] from it. Without a path
 * the nearest stop, or the one after it once the position has passed it. Null where none is ahead.
 */
fun nextStopAhead(
    stops: List<Pair<Double, Double>>,
    path: List<Pair<Double, Double>>,
    own: Pair<Double, Double>,
    minMetres: Double = 80.0,
): Pair<Double, Double>? {
    if (stops.isEmpty()) return null
    if (path.size >= 2) {
        val at = closestIndex(path, own)
        return stops.firstOrNull { closestIndex(path, it) > at && flatMetres(it, own) > minMetres }
    }
    val nearest = closestIndex(stops, own)
    // Past the nearest stop already: nearer to the stop after it than that stop is.
    val following = stops.getOrNull(nearest + 1)
    val past = following != null && flatMetres(own, following) < flatMetres(stops[nearest], following)
    return stops.drop(if (past) nearest + 1 else nearest).firstOrNull { flatMetres(it, own) > minMetres }
}

/**
 * The point the centred map keeps in view ahead of [own] at the traveller's [step] of
 * [journey]: riding, the next stop of the ride (else where it is left); before boarding, where
 * the ride is boarded; on the way to the end, the destination. Null where none is known.
 */
fun centreAhead(step: TripStep, journey: Journey, own: Pair<Double, Double>): Pair<Double, Double>? {
    val leg = step.leg
    return when (step.kind) {
        StepKind.GetOff -> {
            if (leg == null) return null
            val end = leg.toPlace?.position ?: leg.path.lastOrNull()
            val stops = leg.stopovers.mapNotNull { it.place?.position }.ifEmpty { listOfNotNull(end) }
            nextStopAhead(stops, leg.path, own) ?: end
        }
        StepKind.Board -> leg?.boardingSpot?.position ?: leg?.fromPlace?.position ?: leg?.path?.firstOrNull()
        StepKind.WalkToEnd, StepKind.Arrived -> journey.legs.lastOrNull()?.let { it.toPlace?.position ?: it.path.lastOrNull() }
    }
}

/** The zoom of the position centred: never further out than this (a far stop is not all framed). */
const val CENTRE_MIN_ZOOM = 12.5
