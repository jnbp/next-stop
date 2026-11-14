package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * Leaving the way of a walk: while a walk is followed with live positions, the way it is
 * drawn along is compared with where the traveller is. Someone more than 40 m beside it,
 * or walking away from its end, for 20 seconds and more, gets a new way from where they are
 * (see OffRouteWatch); positions less accurate than 30 m count for nothing either way.
 */

/** Where a position lies against a way: [offMeters] beside it, [remainingMeters] still to go along it. */
data class RouteFix(val offMeters: Double, val remainingMeters: Double)

// Metres per degree of latitude; good enough for the few hundred metres of a walk.
private const val METERS_PER_DEGREE = 111_320.0

// The pace the remaining minutes of a re-routed walk are worked out with, as the change times use it.
private const val WALK_METERS_PER_MINUTE = 75.0

/**
 * Where [position] lies against [route] (latitude, longitude): the distance to its nearest
 * point and the length of the way from there to its end. Null for a way of fewer than two points.
 */
fun routeFix(route: List<Pair<Double, Double>>, position: Pair<Double, Double>): RouteFix? {
    if (route.size < 2) return null
    // A flat plane around the position, in metres.
    val east = METERS_PER_DEGREE * cos(Math.toRadians(position.first))
    fun x(point: Pair<Double, Double>) = (point.second - position.second) * east
    fun y(point: Pair<Double, Double>) = (point.first - position.first) * METERS_PER_DEGREE
    var walked = 0.0
    var bestOff = Double.MAX_VALUE
    var bestAlong = 0.0
    for (index in 0 until route.size - 1) {
        val ax = x(route[index])
        val ay = y(route[index])
        val dx = x(route[index + 1]) - ax
        val dy = y(route[index + 1]) - ay
        val length = sqrt(dx * dx + dy * dy)
        // The point of this piece nearest to the position (which lies at 0, 0).
        val t = if (length == 0.0) 0.0 else ((-ax * dx - ay * dy) / (length * length)).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        val off = sqrt(px * px + py * py)
        if (off < bestOff) {
            bestOff = off
            bestAlong = walked + t * length
        }
        walked += length
    }
    return RouteFix(offMeters = bestOff, remainingMeters = (walked - bestAlong).coerceAtLeast(0.0))
}

/** The length of [route] in metres. */
fun routeMeters(route: List<Pair<Double, Double>>): Double {
    if (route.size < 2) return 0.0
    return routeFix(route, route.first())?.remainingMeters ?: 0.0
}

/**
 * Whole minutes on foot for [meters] at the app's pace, x [factor] ("Langsamer unterwegs", see
 * walkSpeedFactor), at least one for any way at all.
 */
fun walkMinutesFor(meters: Double, factor: Double = walkSpeedFactor): Long =
    if (meters <= 0.0) 0L else ceil(meters * factor / WALK_METERS_PER_MINUTE - 1e-9).toLong().coerceAtLeast(1L)

/**
 * The whole minutes to show for the walk [walk]: what was planned for it (departure to
 * arrival), but never less than its way as drawn (the real way on foot where known, to its
 * target) takes at the app's pace ([walkMinutesFor]). A provider often counts a long way through
 * a station as a minute; the drawn way of 300 m says four. A ride by bike keeps its planned time.
 */
fun shownWalkMinutes(walk: Leg): Long {
    val planned = Duration.between(walk.departure, walk.arrival).toMinutes().coerceAtLeast(0)
    if (walk.cycling) return planned
    val path = FootPaths.pathFor(walk)
    val target = walk.walkTarget?.position
    val points = if (target != null && path.isNotEmpty() && path.last() != target) path + target else path
    return maxOf(planned, walkMinutesFor(routeMeters(points)))
}

/**
 * Notices when the traveller leaves the way of a walk. Feed every position to [update]; it
 * says true once the traveller is off the way (more than [maxOffMeters] beside it, or
 * [backtrackMeters] further from its end than they already were) for [holdMillis] in a row,
 * and at most once per [minGapMillis]. Positions with an accuracy worse than
 * [maxAccuracyMeters], or none, are left out: they neither count nor clear. Near the end of
 * the way (within [maxOffMeters]) nothing counts as off: the traveller is there.
 */
class OffRouteWatch(
    private val maxOffMeters: Double = 40.0,
    private val holdMillis: Long = 20_000L,
    private val maxAccuracyMeters: Float = 30f,
    private val backtrackMeters: Double = 40.0,
    private val minGapMillis: Long = 30_000L,
) {
    private var offSince: Long? = null
    private var leastRemaining = Double.MAX_VALUE
    private var lastReroute: Long? = null

    /** Where the last usable position lay against the way; null before one. */
    var lastFix: RouteFix? = null
        private set

    /** Starts over on another way (a new walk, or the new way of a reroute); the pause between reroutes stays. */
    fun reset() {
        offSince = null
        leastRemaining = Double.MAX_VALUE
        lastFix = null
    }

    /**
     * Takes the position [position] with its [accuracyMeters] at [timeMillis] (a clock that
     * only runs forward) against [route]; true when a new way should be asked for now.
     */
    fun update(
        route: List<Pair<Double, Double>>,
        position: Pair<Double, Double>,
        accuracyMeters: Float?,
        timeMillis: Long,
    ): Boolean {
        if (accuracyMeters == null || accuracyMeters > maxAccuracyMeters) return false
        val fix = routeFix(route, position) ?: return false
        lastFix = fix
        if (fix.remainingMeters < leastRemaining) leastRemaining = fix.remainingMeters
        val atEnd = fix.remainingMeters <= maxOffMeters && fix.offMeters <= maxOffMeters
        val beside = fix.offMeters > maxOffMeters
        val back = fix.remainingMeters > leastRemaining + backtrackMeters
        if (atEnd || (!beside && !back)) {
            offSince = null
            return false
        }
        val since = offSince ?: timeMillis.also { offSince = it }
        if (timeMillis - since < holdMillis) return false
        val last = lastReroute
        if (last != null && timeMillis - last < minGapMillis) return false
        lastReroute = timeMillis
        offSince = null
        return true
    }
}

/**
 * The index of the walk of [journey] the traveller is on at [now]: a walk that has not
 * ended yet with no ride under way, or the walk right before a ride that has not left yet
 * (the traveller may take longer than the provider thinks). Null while riding, when the next
 * thing is a ride without a walk before it, and after the end.
 */
fun activeWalkIndex(journey: Journey, now: OffsetDateTime): Int? {
    val legs = journey.legs
    if (legs.any { !it.walking && !now.isBefore(it.departure) && now.isBefore(it.arrival) }) return null
    val index = legs.indexOfFirst { now.isBefore(it.arrival) }
    if (index < 0) return null
    if (legs[index].walking) return index
    return (index - 1).takeIf { it >= 0 && legs[it].walking }
}

/**
 * True when a walk of [journey] (at [walkIndex]) that ends at [arrival] reaches the ride
 * after it too late: after its departure. False for the last walk, which no ride follows.
 */
fun walkMissesRide(journey: Journey, walkIndex: Int, arrival: OffsetDateTime): Boolean {
    val next = journey.legs.drop(walkIndex + 1).firstOrNull { !it.walking } ?: return false
    return arrival.isAfter(next.departure)
}
