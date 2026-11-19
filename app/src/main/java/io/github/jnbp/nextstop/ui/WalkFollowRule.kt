package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import java.time.OffsetDateTime
import kotlin.math.cos
import kotlin.math.hypot

/*
 * Whether the big map of a walk follows the traveller or frames the walk and stands still
 * (#276): it follows only while the walk is the step under way (no ride before it still going)
 * and the traveller is on it or near it; from a train still far away the map shows the walk
 * from its start to its end and does not move. Pure logic, no Android.
 */

/** The traveller within this many metres of the walk's way counts as on it. */
const val WALK_NEAR_METRES = 180.0

// A walk counts as the step under way until this long after its planned end.
private const val WALK_CURRENT_GRACE_MINUTES = 30L

/** The shortest distance in metres from [point] to the line through the points of [way]; null for an empty way. */
fun metresToWay(way: List<Pair<Double, Double>>, point: Pair<Double, Double>): Double? {
    if (way.isEmpty()) return null
    // Flat metres around the point: x east, y north.
    val k = cos(Math.toRadians(point.first)) * 111_195.0
    fun x(p: Pair<Double, Double>) = (p.second - point.second) * k
    fun y(p: Pair<Double, Double>) = (p.first - point.first) * 111_195.0
    if (way.size == 1) return hypot(x(way[0]), y(way[0]))
    var best = Double.MAX_VALUE
    for (index in 0 until way.lastIndex) {
        val ax = x(way[index])
        val ay = y(way[index])
        val bx = x(way[index + 1])
        val by = y(way[index + 1])
        val dx = bx - ax
        val dy = by - ay
        val length = dx * dx + dy * dy
        // The share along the segment of the foot of the point (the point is the origin).
        val t = if (length <= 0.0) 0.0 else ((-ax * dx - ay * dy) / length).coerceIn(0.0, 1.0)
        val d = hypot(ax + t * dx, ay + t * dy)
        if (d < best) best = d
    }
    return best
}

/**
 * True while [walk] is the step under way of [journey] at [now]: every ride before it is over
 * and the walk not long over. Without the journey (or the walk not found in it) by its own time only.
 */
fun walkIsCurrent(journey: Journey?, walk: Leg, now: OffsetDateTime): Boolean {
    if (now.isAfter(walk.arrival.plusMinutes(WALK_CURRENT_GRACE_MINUTES))) return false
    val legs = journey?.legs ?: return true
    val index = legs.indexOfFirst { it === walk }.takeIf { it >= 0 } ?: legs.indexOf(walk)
    if (index < 0) return true
    return legs.take(index).none { !it.walking && now.isBefore(it.arrival) }
}

/**
 * The rule of #276: the map follows the traveller only while the walk is the step under way
 * ([current]) and the traveller is within [WALK_NEAR_METRES] of its way ([metresToWalk], null
 * without a position); otherwise it frames the walk and stands still.
 */
fun walkFollowsOwn(current: Boolean, metresToWalk: Double?): Boolean =
    current && metresToWalk != null && metresToWalk <= WALK_NEAR_METRES
