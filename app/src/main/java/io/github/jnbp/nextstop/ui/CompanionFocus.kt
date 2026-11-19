package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import java.time.OffsetDateTime

/*
 * What the map of the companion looks at after its locate button (#104): the user's own
 * position up close, following it, or the overview of the route still to go, switched by a
 * chip; before the first tap the framing of the trip as before (#91). Pure logic, no Android.
 */

/** What the companion map frames. */
enum class CompanionMapFocus {
    /** The trip as it frames itself: traveller, the stop of the next step, the vehicle (#91). */
    Trip,

    /** The user's own position up close ([OWN_FOCUS_ZOOM]), the camera following it. */
    Own,

    /** The overview of the route still to go. */
    Route,
}

/** The zoom of the own position after the locate button: street level. */
const val OWN_FOCUS_ZOOM = 16.0

/** The locate button: the own position where one is known, else the trip's framing (the vehicle, the next stop). */
fun focusOnLocate(ownKnown: Boolean): CompanionMapFocus = if (ownKnown) CompanionMapFocus.Own else CompanionMapFocus.Trip

/** The chip ("Restliche Strecke" / "Mein Standort"): from the own position to the route and back; the trip's framing has no chip. */
fun CompanionMapFocus.toggled(): CompanionMapFocus = when (this) {
    CompanionMapFocus.Own -> CompanionMapFocus.Route
    CompanionMapFocus.Route -> CompanionMapFocus.Own
    CompanionMapFocus.Trip -> CompanionMapFocus.Trip
}

/**
 * The points of the route of [journey] still to go at [now] (#104, #166): the part of the
 * current ride from where the traveller is ([at]: the vehicle or the own position on it; else
 * where the timetable puts it) to its end, then the walks and rides after it to the
 * destination. Rides and walks over are left out, and so is the part of the current one
 * already behind. [own] (the user's fresh position, null when stale or unknown) comes first.
 * At most about [limit] points: long ways are thinned evenly (the frame needs the extent, not
 * every corner).
 */
fun remainingRoute(
    journey: Journey,
    now: OffsetDateTime,
    own: Pair<Double, Double>?,
    limit: Int = 400,
    at: Pair<Double, Double>? = own,
): List<Pair<Double, Double>> {
    val ahead = journey.legs.filter { now.isBefore(it.arrival) }
    val all = ArrayList<Pair<Double, Double>>()
    ahead.forEachIndexed { index, leg ->
        val way = leg.path.ifEmpty { listOfNotNull(leg.fromPlace?.position, leg.toPlace?.position) }
        // The ride under way: only from where the traveller is on it.
        val begun = index == 0 && !now.isBefore(leg.departure) && way.size >= 2
        if (begun) {
            val from = if (at != null) nearestIndex(way, at) else shareIndex(way, timeShare(leg.departure, leg.arrival, now))
            at?.let { all.add(it) }
            all.addAll(way.subList(from, way.size))
        } else {
            all.addAll(way)
        }
    }
    val step = if (all.size > limit) (all.size + limit - 1) / limit else 1
    val points = ArrayList<Pair<Double, Double>>(all.size / step + 2)
    own?.let { points.add(it) }
    for (index in all.indices step step) points.add(all[index])
    if (all.isNotEmpty() && (all.size - 1) % step != 0) points.add(all.last())
    return points
}

/** The share (0 to 1) of the time from [start] to [end] gone at [now]. */
private fun timeShare(start: OffsetDateTime, end: OffsetDateTime, now: OffsetDateTime): Double {
    val total = java.time.Duration.between(start, end).toMillis().coerceAtLeast(1L)
    return (java.time.Duration.between(start, now).toMillis().toDouble() / total).coerceIn(0.0, 1.0)
}

/** The index of the point of [way] nearest to [point] (flat degrees, scaled for the latitude). */
private fun nearestIndex(way: List<Pair<Double, Double>>, point: Pair<Double, Double>): Int {
    val k = kotlin.math.cos(Math.toRadians(point.first))
    var best = 0
    var bestDistance = Double.MAX_VALUE
    way.forEachIndexed { index, p ->
        val dy = p.first - point.first
        val dx = (p.second - point.second) * k
        val d = dx * dx + dy * dy
        if (d < bestDistance) {
            bestDistance = d
            best = index
        }
    }
    return best
}

/** The index of the point of [way] after [share] of its length (flat degrees, scaled for the latitude). */
private fun shareIndex(way: List<Pair<Double, Double>>, share: Double): Int {
    val k = kotlin.math.cos(Math.toRadians(way.first().first))
    val steps = way.zipWithNext { a, b -> kotlin.math.hypot(a.first - b.first, (a.second - b.second) * k) }
    var left = steps.sum() * share
    steps.forEachIndexed { index, length ->
        if (left <= length) return index
        left -= length
    }
    return way.lastIndex
}

/**
 * MapLibre makes a new map facing the whole world (zoom 0). A camera this far out has never
 * been framed: the companion's map, made anew whenever "Mit Karte" is chosen, is put on its
 * focus at once instead of flying in from space (#122).
 */
const val FRESH_MAP_ZOOM = 3.0

/** True for the camera of a map that was not framed yet (see [FRESH_MAP_ZOOM]). */
fun freshCamera(zoom: Double): Boolean = zoom.isNaN() || zoom < FRESH_MAP_ZOOM

// The points of one walk of a change taken for its framing, at most.
private const val CHANGE_WALK_POINTS = 24

/**
 * While riding [rideIndex] of [legs] towards a change (#137): what of that change the map
 * frames along with the ride: where the ride ends, the walks after it (their way, thinned) and
 * where the next ride is boarded ([boarding] of it where known, else its first stop). Empty for
 * a walk, and for the last ride (its end is framed anyway).
 */
fun changeFramePoints(
    legs: List<Leg>,
    rideIndex: Int,
    boarding: (Leg) -> Pair<Double, Double>? = { null },
): List<Pair<Double, Double>> {
    val ride = legs.getOrNull(rideIndex) ?: return emptyList()
    if (ride.walking) return emptyList()
    val nextIndex = (rideIndex + 1 until legs.size).firstOrNull { !legs[it].walking } ?: return emptyList()
    val points = ArrayList<Pair<Double, Double>>()
    (ride.toPlace?.position ?: ride.path.lastOrNull())?.let { points.add(it) }
    for (index in rideIndex + 1 until nextIndex) {
        val walk = legs[index]
        val way = walk.path.ifEmpty { listOfNotNull(walk.fromPlace?.position, walk.toPlace?.position) }
        val step = if (way.size > CHANGE_WALK_POINTS) (way.size + CHANGE_WALK_POINTS - 1) / CHANGE_WALK_POINTS else 1
        for (at in way.indices step step) points.add(way[at])
        way.lastOrNull()?.let { if (points.last() != it) points.add(it) }
    }
    val next = legs[nextIndex]
    (boarding(next) ?: next.fromPlace?.position ?: next.path.firstOrNull())?.let { points.add(it) }
    return points.distinct()
}
