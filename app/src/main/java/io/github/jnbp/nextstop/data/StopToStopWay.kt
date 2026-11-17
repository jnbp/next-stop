package io.github.jnbp.nextstop.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * Ways that are no ways. Where a feed has no shape for a trip (often for rail replacement
 * buses, added at short notice), the providers draw it as the straight lines from stop to stop:
 * HAFAS as a polyline through the stops' coordinates, MOTIS as one straight segment per hop.
 * A bus moving along such a line drives through woods and fields. Here such ways are told
 * apart from real ones, so the map draws them as a dashed connector and puts the bus at its
 * stops instead of moving it across the land. Pure logic, no Android.
 */

/** A point of a way this close to a stop is the stop's own point. */
const val STOP_POINT_METERS = 30.0

/** A straight hop of a bus longer than this cannot be its road: no street runs that straight for long. */
const val STRAIGHT_HOP_METERS = 400.0

/**
 * True when [path] is no real way between [stops] (their positions in order): fewer than two
 * points, or every point of it one of the stops' own (within [STOP_POINT_METERS]), so that it
 * is just the straight lines from stop to stop.
 */
fun isStopToStopWay(path: List<Pair<Double, Double>>, stops: List<Pair<Double, Double>>): Boolean {
    if (path.size < 2) return true
    if (stops.size < 2) return false
    // A real way has points between the stops; one of the stops' points alone has at most theirs.
    if (path.size > stops.size * 2) return false
    return path.all { point -> stops.any { metersApart(point, it) <= STOP_POINT_METERS } }
}

/** True for the products whose vehicles drive on roads: a way without a shape is not theirs. */
fun drivesOnRoads(product: String): Boolean = product == "bus" || product == "coach"

/**
 * True when a vehicle of [product] on [path] between [stops] has no way to move along: a bus
 * or coach whose way is only the straight lines from stop to stop (see isStopToStopWay). A
 * train is not judged by its whole way: its hops that are air lines are found one by one
 * (see airHops and onAirLine) and stand at their stations too.
 */
fun movesWithoutWay(product: String, path: List<Pair<Double, Double>>, stops: List<Pair<Double, Double>>): Boolean =
    drivesOnRoads(product) && isStopToStopWay(path, stops)

/**
 * True when [shape], the way of one hop of a bus or coach of [product] from stop to stop, is a
 * straight line where it cannot be one: two points more than [STRAIGHT_HOP_METERS] apart.
 */
fun isStraightRoadHop(product: String, shape: List<Pair<Double, Double>>): Boolean =
    drivesOnRoads(product) && shape.size == 2 && metersApart(shape[0], shape[1]) > STRAIGHT_HOP_METERS

/**
 * The index of the stop where a vehicle without a way stands at [progress] (counted in stops,
 * as StopClock.progress: 2 while it stands at the third stop, 2.5 halfway from there to the
 * fourth): the stop it stands at, and once it has left, the one it is heading to. Between two
 * stops it shows at a station, never in the fields.
 */
fun parkedStop(progress: Float, stops: Int): Int {
    if (stops <= 0) return 0
    return Math.ceil(progress.toDouble()).toInt().coerceIn(0, stops - 1)
}

/**
 * Where a vehicle without a way stands between the stop it reached last, at [left], which it
 * leaves at [leftAt] (epoch milliseconds), and the stop it comes to, at [next], at [now]: at
 * the one it reached while it stands there, then at the next one, where it is heading (as
 * parkedStop). Where only one of them is known, there; null with neither.
 */
fun parkedPoint(
    left: Pair<Double, Double>?,
    leftAt: Long?,
    next: Pair<Double, Double>?,
    now: Long,
): Pair<Double, Double>? {
    if (left == null || next == null) return left ?: next
    return if (leftAt != null && now < leftAt) left else next
}

private fun metersApart(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}
