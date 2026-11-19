package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Where a ride lies on the whole run of its vehicle. A ring line (Berlin's S41 and S42) or
 * any run of more than one lap calls at the same station more than once, and its way passes
 * the same place more than once: the call that belongs to a ride is the one whose time
 * matches the ride, and a stop lies on the pass of the way that comes first after the stop
 * before it, never simply on the first one by name or the nearest one anywhere.
 */
object TripMatch {
    /** A way counts as reaching a stop once it comes this close to it (metres) ... */
    const val LOCK_METRES = 150.0

    /** ... and as having passed it once it runs this much farther off than its nearest point there. */
    const val LEAVE_METRES = 2_000.0

    /**
     * The call in [stops] where a ride from [name] at [departure] (planned [plannedDeparture])
     * boards: of all calls there the one nearest in time; the first by name where no time
     * tells them apart. -1 when the run does not call there.
     */
    fun boardIndex(stops: List<Stopover>, name: String, departure: OffsetDateTime?, plannedDeparture: OffsetDateTime?): Int {
        var best = -1
        var bestGap = Long.MAX_VALUE
        for (index in stops.indices) {
            val stop = stops[index]
            if (stop.name != name) continue
            if (best < 0) best = index
            val gap = minOf(
                gapSeconds(stop.departure ?: stop.arrival, departure),
                gapSeconds(stop.plannedDeparture, plannedDeparture),
            )
            if (gap < bestGap) {
                bestGap = gap
                best = index
            }
        }
        return best
    }

    /** [boardIndex] for [leg]. */
    fun boardIndex(stops: List<Stopover>, leg: Leg): Int = boardIndex(stops, leg.from, leg.departure, leg.plannedDeparture)

    /**
     * The call in [stops] after [board] where a ride to [name] at [arrival] (planned
     * [plannedArrival]) gets off: of all calls there after boarding the one nearest in time;
     * the first after boarding where no time tells them apart. -1 when there is none.
     */
    fun alightIndex(stops: List<Stopover>, board: Int, name: String, arrival: OffsetDateTime?, plannedArrival: OffsetDateTime?): Int {
        var best = -1
        var bestGap = Long.MAX_VALUE
        for (index in (board + 1).coerceAtLeast(0) until stops.size) {
            val stop = stops[index]
            if (stop.name != name) continue
            if (best < 0) best = index
            val gap = minOf(
                gapSeconds(stop.arrival ?: stop.departure, arrival),
                gapSeconds(stop.plannedArrival, plannedArrival),
            )
            if (gap < bestGap) {
                bestGap = gap
                best = index
            }
        }
        return best
    }

    /** [alightIndex] for [leg]. */
    fun alightIndex(stops: List<Stopover>, board: Int, leg: Leg): Int = alightIndex(stops, board, leg.to, leg.arrival, leg.plannedArrival)

    private fun gapSeconds(a: OffsetDateTime?, b: OffsetDateTime?): Long =
        if (a == null || b == null) Long.MAX_VALUE else abs(a.toEpochSecond() - b.toEpochSecond())

    /**
     * The point of [path] from [from] on where it reaches [point]: its nearest point on the
     * first pass that comes within [LOCK_METRES], not a later pass of a loop or a ring; the
     * nearest point anywhere after [from] where no pass comes that close.
     */
    fun pathIndex(path: List<Pair<Double, Double>>, point: Pair<Double, Double>, from: Int = 0): Int {
        val start = from.coerceAtLeast(0)
        if (start >= path.size) return (path.size - 1).coerceAtLeast(0)
        val scale = cos(Math.toRadians(point.first))
        var best = start
        var bestMetres = Double.MAX_VALUE
        for (index in start until path.size) {
            val north = (path[index].first - point.first) * 111_320.0
            val east = (path[index].second - point.second) * 111_320.0 * scale
            val metres = sqrt(north * north + east * east)
            if (metres < bestMetres) {
                bestMetres = metres
                best = index
            } else if (bestMetres <= LOCK_METRES && metres > bestMetres + LEAVE_METRES) {
                break
            }
        }
        return best
    }

    /**
     * For every stop of [points] (its position, null where unknown) the point of [path] it
     * lies at, in order: never behind the stop before it (see [pathIndex]). A stop without a
     * position takes the point of the stop before it.
     */
    fun stopPathIndices(path: List<Pair<Double, Double>>, points: List<Pair<Double, Double>?>): IntArray {
        val result = IntArray(points.size)
        var from = 0
        for (index in points.indices) {
            val point = points[index]
            if (point != null && path.isNotEmpty()) from = pathIndex(path, point, from)
            result[index] = from
        }
        return result
    }

    /**
     * The piece of [path] (the way of a whole run) from stop [board] to stop [alight] of the run,
     * whose stop positions are [points]; null when the way does not tell them apart.
     */
    fun cutBetween(path: List<Pair<Double, Double>>, points: List<Pair<Double, Double>?>, board: Int, alight: Int): List<Pair<Double, Double>>? {
        if (path.size < 2 || board < 0 || alight <= board || alight >= points.size) return null
        val at = stopPathIndices(path, points.subList(0, alight + 1))
        val from = at[board]
        val to = at[alight]
        return if (to > from) path.subList(from, to + 1) else null
    }
}
