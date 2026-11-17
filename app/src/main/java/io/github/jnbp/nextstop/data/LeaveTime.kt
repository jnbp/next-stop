package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * "Wann muss ich los?" on the departures tab: every departure by the latest time the user has
 * to set off on foot from where they are to catch it, its time (live where known) less the
 * walk to the stop it leaves from. Cheap on purpose, no request per row: the walk is the
 * straight line x [LEAVE_DETOUR_FACTOR] at the usual pace, or the length of a real way on foot
 * where FootPaths already holds one between the same two points; either at the pace set
 * ("Langsamer unterwegs", see walkSpeedFactor). A bus or tram leaves from its stop position
 * of OpenStreetMap where that is known (OsmStations.stopPosition), else from its stop.
 */

/** How much longer a way on the streets is than the straight line, where no real way is known. */
const val LEAVE_DETOUR_FACTOR = 1.3

/** A stop farther than this from the user is not walked to: no time to leave for it. */
const val LEAVE_MAX_METERS = 2_000.0

// The usual pace, as the walks the app measures itself (see WalkPace).
private const val LEAVE_METERS_PER_MINUTE = 75.0

/** A departure with the time the user must set off to catch it on foot, and that walk. */
data class LeaveTime(
    val departure: Departure,
    /** The latest time to set off; null where the place or time of the departure is not known, or it is too far. */
    val leaveAt: OffsetDateTime?,
    /** The walk to the place it leaves from, at the pace set; null with [leaveAt]. */
    val walkSeconds: Int?,
)

/**
 * The seconds of the walk from [from] to [to] at the pace of [factor]: the real way on foot
 * where [knownMeters] gives one (not shorter than the straight line), else the straight line
 * x [LEAVE_DETOUR_FACTOR]. Null beyond [LEAVE_MAX_METERS].
 */
fun leaveWalkSeconds(
    from: Pair<Double, Double>,
    to: Pair<Double, Double>,
    knownMeters: Double? = FootPaths.knownWayMeters(from, to),
    factor: Double = walkSpeedFactor,
): Int? {
    val straight = leaveStraightMeters(from, to)
    if (straight > LEAVE_MAX_METERS) return null
    val meters = knownMeters?.takeIf { it >= straight } ?: (straight * LEAVE_DETOUR_FACTOR)
    return pacedSeconds(ceil(meters / LEAVE_METERS_PER_MINUTE * 60.0).toInt(), factor)
}

/** The walk from [here] to [stop] at the pace set (see [leaveWalkSeconds]); null without its place or too far. */
fun leaveStopWalkSeconds(here: Pair<Double, Double>, stop: Stop): Int? = stop.position?.let { leaveWalkSeconds(here, it) }

/**
 * Where the departures of a stop [walkSeconds] away are asked from for "when to leave": not
 * before the user can be there, less the [keptSeconds] a missed one stays listed (see
 * [catchableLeaves]); [from] (a time the user picked) when that is later, and [from] as it is
 * (null: now) without a walk. Asked from now, a busy stop farther away brought only departures
 * that leave before the user gets there, all dropped as missed, and the stop vanished from
 * "when to leave" while the order by departure showed it.
 */
fun leaveDeparturesFrom(
    walkSeconds: Int?,
    from: OffsetDateTime?,
    now: OffsetDateTime,
    keptSeconds: Long = LEAVE_MISSED_KEPT_SECONDS,
): OffsetDateTime? {
    val ahead = (walkSeconds ?: 0) - keptSeconds
    if (ahead <= 0) return from
    val reachable = now.plusSeconds(ahead)
    return if (from != null && !from.isBefore(reachable)) from else reachable
}

/** True when [stop] is close enough to [here] to walk to (see [LEAVE_MAX_METERS]). */
fun walkableFrom(here: Pair<Double, Double>, stop: Stop?): Boolean {
    val position = stop?.position ?: return false
    return leaveStraightMeters(here, position) <= LEAVE_MAX_METERS
}

/**
 * The place [departure] leaves from: the stop position of a bus or tram in OpenStreetMap where
 * it is known, else its stop ([Departure.stop], else [boardStop], the stop of a board of one
 * stop), else the place the provider gave the run ([Departure.at]).
 */
fun departurePlace(departure: Departure, boardStop: Stop?): Pair<Double, Double>? {
    val stop = departure.stop ?: boardStop
    val near = stop?.position ?: departure.at
    if (departure.product == "bus" || departure.product == "tram") {
        runCatching { OsmStations.stopPosition(stop?.id, stop?.name, departure.platform, near) }
            .getOrNull()
            ?.let { return it.latitude to it.longitude }
    }
    return near
}

/**
 * [departures] with the time to leave for each (see [LeaveTime]), latest time to set off first;
 * those without one at the end, in their own order. A cancelled run counts by its planned time
 * ([departureOrderTime]). One lookup of the place and one walk per stop and platform.
 */
fun leaveTimes(
    departures: List<Departure>,
    here: Pair<Double, Double>,
    boardStop: Stop?,
    walk: (Pair<Double, Double>, Pair<Double, Double>) -> Int? = { from, to -> leaveWalkSeconds(from, to) },
    place: (Departure, Stop?) -> Pair<Double, Double>? = ::departurePlace,
): List<LeaveTime> {
    val walks = HashMap<String, Int?>()
    val timed = departures.map { departure ->
        val stop = departure.stop ?: boardStop
        val key = "${stop?.id}|${departure.product}|${departure.platform?.trim().orEmpty()}"
        val seconds = walks.getOrPut(key) { place(departure, boardStop)?.let { walk(here, it) } }
        val time = departureOrderTime(departure)
        val leaveAt = if (seconds != null && time != null) time.minusSeconds(seconds.toLong()) else null
        LeaveTime(departure, leaveAt, if (leaveAt != null) seconds else null)
    }
    return timed.sortedWith(compareBy(nullsLast()) { leave: LeaveTime -> leave.leaveAt?.toInstant() })
}

/**
 * A departure whose time to set off passed this long ago is no longer listed in "Wann los?";
 * one that passed more recently stays, greyed and marked as missed, so it does not just vanish.
 */
const val LEAVE_MISSED_KEPT_SECONDS = 120L

/**
 * Of [leaves], those still worth listing at [now]: the ones the user can still reach on foot,
 * those missed less than [keptSeconds] ago, and those without a time to set off.
 */
fun catchableLeaves(leaves: List<LeaveTime>, now: OffsetDateTime, keptSeconds: Long = LEAVE_MISSED_KEPT_SECONDS): List<LeaveTime> =
    leaves.filter { leave -> (secondsToLeave(leave, now) ?: 0L) >= -keptSeconds }

/**
 * True when there is any stop of [stops] close enough to [here] to walk to (see [walkableFrom]):
 * "Wann los?" then works for a board of several stops.
 */
fun anyWalkableFrom(here: Pair<Double, Double>, stops: List<Stop>): Boolean = stops.any { walkableFrom(here, it) }

/** The whole seconds from [now] until [leave] must set off; negative once that is past; null without a time. */
fun secondsToLeave(leave: LeaveTime, now: OffsetDateTime): Long? =
    leave.leaveAt?.let { it.toEpochSecond() - now.toEpochSecond() }

private fun leaveStraightMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
    val north = (to.first - from.first) * 111_320.0
    val east = (to.second - from.second) * 111_320.0 * cos(Math.toRadians((from.first + to.first) / 2))
    return sqrt(north * north + east * east)
}
