package io.github.jnbp.nextstop.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where to board, estimated from the map where the dataset of change positions has no record
 * (a train to a bus or tram, or a change between trains it does not cover). The arriving
 * train runs from [previous] to the change at [change]; the departing ride leaves from
 * [departure]. The way from the change to the departure is projected onto the axis the
 * train runs along: far enough ahead means the front, far enough behind the rear, else the
 * middle. [along] and [across] are that way in metres along and across the axis (ahead and
 * to the right positive), [bearing] the direction of travel in degrees from north,
 * [threshold] the distance from the middle of the platform beyond which an end counts.
 * [part] is the third of the train as in [ChangePosition.part]: 0 rear, 1 middle, 2 front.
 */
data class BoardingEstimate(
    val part: Int,
    val along: Double,
    val across: Double,
    val bearing: Double,
    val threshold: Double,
)

/** An estimate, or why there is none, for the log. */
data class BoardingDecision(val estimate: BoardingEstimate?, val reason: String)

/** Pure geometry for [BoardingEstimate], with no Android in it. Positions are latitude and longitude. */
object BoardingGeometry {
    /** A departure closer than this to the arrival point gives no direction (both are the same station point). */
    const val MIN_GAP = 12.0

    /** A departure farther than this is not reached from one end of the platform. */
    const val MAX_GAP = 600.0

    /** The shortest stretch of track that gives the direction of travel. */
    const val MIN_BASE = 40.0

    /** How far back along the way of the ride its direction at the change is taken. */
    const val TANGENT_BACK = 200.0

    private const val EARTH = 6_371_000.0

    /** About how long a train of [product] is, in metres. */
    fun trainLength(product: String): Double = when (product) {
        "suburban" -> 147.0
        "subway" -> 100.0
        "regional", "express" -> 160.0
        else -> 100.0
    }

    /**
     * How far ahead or behind the middle of the arrival point the departure must lie for an end
     * of the train to be the better one: about a third of the way to the end of the train,
     * kept between 30 and 60 metres for the inaccuracy of stop positions.
     */
    fun threshold(product: String): Double = (trainLength(product) * 0.3).coerceIn(30.0, 60.0)

    /** The initial bearing from [from] to [to], in degrees from north, 0 until 360. */
    fun bearing(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(from.first)
        val lat2 = Math.toRadians(to.first)
        val dLon = Math.toRadians(to.second - from.second)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** [to] as seen from [from] in metres east and north; exact enough for a few kilometres. */
    fun offset(from: Pair<Double, Double>, to: Pair<Double, Double>): Pair<Double, Double> {
        val meanLat = Math.toRadians((from.first + to.first) / 2.0)
        val east = Math.toRadians(to.second - from.second) * cos(meanLat) * EARTH
        val north = Math.toRadians(to.first - from.first) * EARTH
        return east to north
    }

    fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val (east, north) = offset(a, b)
        return hypot(east, north)
    }

    /**
     * A point on [path] about [TANGENT_BACK] metres before its point nearest to [change], so the
     * line from it to the change is the direction of travel at the change. Null when the way
     * is shorter than [MIN_BASE] before the change or does not come near it.
     */
    fun tangentPoint(path: List<Pair<Double, Double>>, change: Pair<Double, Double>): Pair<Double, Double>? {
        if (path.size < 2) return null
        // The point of the way nearest to the change, looked for from the end: the way ends there.
        var nearest = path.lastIndex
        var best = Double.MAX_VALUE
        for (index in path.indices.reversed()) {
            val gap = distance(path[index], change)
            if (gap < best) {
                best = gap
                nearest = index
            }
        }
        if (best > MAX_GAP / 2) return null
        var walked = 0.0
        var index = nearest
        while (index > 0 && walked < TANGENT_BACK) {
            walked += distance(path[index - 1], path[index])
            index--
        }
        val point = path[index]
        return if (distance(point, change) >= MIN_BASE) point else null
    }

    /**
     * Where to board a train of [product] that comes from [previous] to [change] for a ride
     * that leaves from [departure].
     */
    fun estimate(
        previous: Pair<Double, Double>,
        change: Pair<Double, Double>,
        departure: Pair<Double, Double>,
        product: String,
    ): BoardingDecision {
        val (travelEast, travelNorth) = offset(previous, change)
        val base = hypot(travelEast, travelNorth)
        if (base < MIN_BASE) return BoardingDecision(null, "base ${base.toInt()} m too short")
        val (wayEast, wayNorth) = offset(change, departure)
        val gap = hypot(wayEast, wayNorth)
        if (gap < MIN_GAP) return BoardingDecision(null, "departure ${gap.toInt()} m away: same point")
        if (gap > MAX_GAP) return BoardingDecision(null, "departure ${gap.toInt()} m away: too far")
        val unitEast = travelEast / base
        val unitNorth = travelNorth / base
        val along = wayEast * unitEast + wayNorth * unitNorth
        // Positive to the right of the direction of travel.
        val across = wayEast * unitNorth - wayNorth * unitEast
        val limit = threshold(product)
        val part = when {
            along > limit -> 2
            along < -limit -> 0
            else -> 1
        }
        val estimate = BoardingEstimate(part, along, across, bearing(previous, change), limit)
        return BoardingDecision(estimate, "along ${along.toInt()} m, across ${across.toInt()} m, limit ${limit.toInt()} m")
    }

    /** A platform shorter than this (in metres) is a mapping error, not a platform to place a train on. */
    const val MIN_PLATFORM = 20.0

    /**
     * The direction of travel must run at most this far across the platform (the cosine of the
     * angle between them, here 60 degrees): else the way or the stop before tells nothing.
     */
    const val MIN_ALIGNMENT = 0.5

    /**
     * Where to board a train of [product] that stops at the platform with the ends [platformA]
     * and [platformB] (as mapped, in either order), coming from [previous] (a point behind it on
     * its way: the way of the ride, the stop before), for a change that leaves the platform
     * towards [exit] (the station exit nearest to the next ride, or the point where the
     * platform of the next train is closest). The train is taken to stop with its front at the
     * end of the platform it runs towards, as trains do; where the platform is longer than the
     * train, its rear part stays empty. The exit is projected onto the platform: ahead of the
     * middle of the train by more than the threshold (a third of the train at most) means the
     * front, behind it the rear, else the middle. [BoardingEstimate.along] is measured from the
     * middle of the train. Null where the platform is too short, the direction is unknown or
     * runs across the platform, or the exit is too far away.
     */
    fun platformEstimate(
        platformA: Pair<Double, Double>,
        platformB: Pair<Double, Double>,
        previous: Pair<Double, Double>,
        exit: Pair<Double, Double>,
        product: String,
    ): BoardingDecision {
        val middle = ((platformA.first + platformB.first) / 2.0) to ((platformA.second + platformB.second) / 2.0)
        val (axisEast, axisNorth) = offset(platformA, platformB)
        val length = hypot(axisEast, axisNorth)
        if (length < MIN_PLATFORM) return BoardingDecision(null, "platform ${length.toInt()} m too short")
        val (travelEast, travelNorth) = offset(previous, middle)
        val base = hypot(travelEast, travelNorth)
        if (base < MIN_BASE) return BoardingDecision(null, "base ${base.toInt()} m too short")
        val alignment = (axisEast * travelEast + axisNorth * travelNorth) / (length * base)
        if (abs(alignment) < MIN_ALIGNMENT) {
            return BoardingDecision(null, "direction across the platform (cos ${"%.2f".format(alignment)})")
        }
        // The axis pointed the way the train runs: towards the end where its front stops.
        val sign = if (alignment > 0) 1.0 else -1.0
        val unitEast = sign * axisEast / length
        val unitNorth = sign * axisNorth / length
        val (wayEast, wayNorth) = offset(middle, exit)
        val gap = hypot(wayEast, wayNorth)
        if (gap > MAX_GAP) return BoardingDecision(null, "exit ${gap.toInt()} m away: too far")
        val alongPlatform = wayEast * unitEast + wayNorth * unitNorth
        val across = wayEast * unitNorth - wayNorth * unitEast
        val train = minOf(trainLength(product), length)
        // The middle of the train lies half a train behind the front end of the platform.
        val along = alongPlatform - (length / 2.0 - train / 2.0)
        val limit = minOf(threshold(product), train / 3.0)
        val part = when {
            along > limit -> 2
            along < -limit -> 0
            else -> 1
        }
        val estimate = BoardingEstimate(part, along, across, bearing(previous, middle), limit)
        return BoardingDecision(
            estimate,
            "platform ${length.toInt()} m, exit ${along.toInt()} m from the middle of the train, across ${across.toInt()} m, limit ${limit.toInt()} m",
        )
    }

    /** A position [north] and [east] metres from [from]; for tests with made-up coordinates. */
    fun moved(from: Pair<Double, Double>, north: Double, east: Double): Pair<Double, Double> {
        val lat = from.first + Math.toDegrees(north / EARTH)
        val lon = from.second + Math.toDegrees(east / (EARTH * cos(Math.toRadians(from.first))))
        return lat to lon
    }
}
