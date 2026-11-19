package io.github.jnbp.nextstop.ui

import kotlin.math.cos
import kotlin.math.sqrt

/*
 * The grey of the part of a ride already travelled ends where the vehicle was at the last split
 * of the way (every two seconds, see tripRoutes). So that its end moves smoothly instead of in
 * steps, the short piece of the way from that split to where the vehicle is now (by the same
 * track the split went by) is drawn in grey over it, growing with the vehicle until the next
 * split takes it over exactly (see RouteSplitGlide): the ways themselves are not split more
 * often. Pure logic, no Android; nothing here allocates per frame.
 */

// Metres per degree of latitude.
private const val GLIDE_METRES_PER_DEGREE = 111_320.0

/**
 * A ride split into travelled and ahead: [key] names it (journey and leg), [argb] its line
 * colour, [latLon] its way (latitude, longitude in turn), [along] the metres along it at each
 * point (see [alongMetres]), [split] where the grey ends now (metres along).
 */
class RouteGlide(val key: String, val argb: Int, val latLon: DoubleArray, val along: DoubleArray, val split: Double)

/** The metres along [path] at each of its points, from 0 at the first. */
fun alongMetres(path: List<Pair<Double, Double>>): DoubleArray {
    val out = DoubleArray(path.size)
    for (index in 1 until path.size) {
        val a = path[index - 1]
        val b = path[index]
        val north = (b.first - a.first) * GLIDE_METRES_PER_DEGREE
        val east = (b.second - a.second) * GLIDE_METRES_PER_DEGREE * cos(Math.toRadians(a.first))
        out[index] = out[index - 1] + sqrt(north * north + east * east)
    }
    return out
}

/** The metres along a way at the point [share] of the way from its point [index] to the next ([along] from alongMetres). */
fun splitAlong(along: DoubleArray, index: Int, share: Double): Double {
    if (along.isEmpty()) return 0.0
    val at = index.coerceIn(0, along.lastIndex)
    if (at == along.lastIndex) return along[at]
    return along[at] + (along[at + 1] - along[at]) * share.coerceIn(0.0, 1.0)
}

/**
 * Writes into [out] (latitude, longitude in turn) the piece of the way [latLon] between [from]
 * and [to] metres along it ([along]): its two ends, exactly, and the points of the way between.
 * The number of points written; 0 where the piece is empty or [out] too small.
 */
fun glidePiece(latLon: DoubleArray, along: DoubleArray, from: Double, to: Double, out: DoubleArray): Int {
    val count = along.size
    if (count < 2 || to <= from) return 0
    val start = from.coerceIn(0.0, along[count - 1])
    val end = to.coerceIn(0.0, along[count - 1])
    if (end <= start) return 0
    var written = 0
    // The stretch the start lies on.
    var index = 0
    while (index < count - 2 && along[index + 1] <= start) index++
    if (out.size < 4) return 0
    written = putAlong(latLon, along, index, start, out, written)
    var next = index + 1
    while (next < count && along[next] < end) {
        if ((written + 1) * 2 > out.size) return written
        out[written * 2] = latLon[next * 2]
        out[written * 2 + 1] = latLon[next * 2 + 1]
        written++
        next++
    }
    if ((written + 1) * 2 > out.size) return written
    return putAlong(latLon, along, (next - 1).coerceIn(0, count - 2), end, out, written)
}

// The point [metres] along the stretch from point [index] to the next, written at [written] of [out].
private fun putAlong(latLon: DoubleArray, along: DoubleArray, index: Int, metres: Double, out: DoubleArray, written: Int): Int {
    val length = along[index + 1] - along[index]
    val share = if (length <= 0.0) 0.0 else ((metres - along[index]) / length).coerceIn(0.0, 1.0)
    out[written * 2] = latLon[index * 2] + (latLon[index * 2 + 2] - latLon[index * 2]) * share
    out[written * 2 + 1] = latLon[index * 2 + 1] + (latLon[index * 2 + 3] - latLon[index * 2 + 1]) * share
    return written + 1
}

/**
 * Where the vehicle at ([latitude], [longitude]) stands on the way [latLon] (metres along it, see
 * [along]), looked for only on the stretches from [fromMetres] to [reachMetres] beyond it (a
 * ring line passes a place more than once; the vehicle only goes on): its nearest point there,
 * never before [fromMetres]. NaN for a way without stretches there.
 */
fun alongNear(latLon: DoubleArray, along: DoubleArray, latitude: Double, longitude: Double, fromMetres: Double, reachMetres: Double): Double {
    val count = along.size
    if (count < 2) return Double.NaN
    val scale = cos(Math.toRadians(latitude))
    val until = fromMetres + reachMetres
    var best = Double.MAX_VALUE
    var bestAlong = Double.NaN
    for (index in 0 until count - 1) {
        if (along[index + 1] < fromMetres) continue
        if (along[index] > until) break
        val ax = (latLon[index * 2 + 1] - longitude) * scale
        val ay = latLon[index * 2] - latitude
        val dx = (latLon[index * 2 + 3] - latLon[index * 2 + 1]) * scale
        val dy = latLon[index * 2 + 2] - latLon[index * 2]
        val length = dx * dx + dy * dy
        val share = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        val px = ax + dx * share
        val py = ay + dy * share
        val distance = px * px + py * py
        if (distance < best) {
            best = distance
            bestAlong = along[index] + (along[index + 1] - along[index]) * share
        }
    }
    return if (bestAlong.isNaN()) bestAlong else maxOf(bestAlong, fromMetres)
}

/**
 * Where the markers of the followed trip's vehicles are drawn right now, by trip id (see
 * TrackMarkers): a live report or the traveller's own position on board can hold a marker
 * behind where the timetable's track puts the vehicle, and the grey of the way must never run
 * ahead of the marker (#119), so the split and its glide go by this place where there is one.
 * Main thread only; an update writes into the array kept for the trip.
 */
object DrawnVehicles {
    private val spots = HashMap<String, DoubleArray>()

    /** The marker of [tripId] is drawn at [latitude], [longitude] now. */
    fun put(tripId: String, latitude: Double, longitude: Double) {
        val spot = spots.getOrPut(tripId) { DoubleArray(2) }
        spot[0] = latitude
        spot[1] = longitude
    }

    /** The marker of [tripId] is not drawn (any more). */
    fun remove(tripId: String) {
        spots.remove(tripId)
    }

    /** Writes where the marker of [tripId] is drawn into [out]; false when it is not drawn. */
    fun locate(tripId: String, out: DoubleArray): Boolean {
        val spot = spots[tripId] ?: return false
        out[0] = spot[0]
        out[1] = spot[1]
        return true
    }
}
