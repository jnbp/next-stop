package io.github.jnbp.nextstop.data

import kotlin.math.roundToInt

/*
 * The stops of the departures tab: the choice of stops around the user, in the order of the
 * distance it shows, and the board of several stops at once (one stop picked, others added).
 */

/**
 * [stops] for a list of stops to choose from, nearest first by the distance the list shows:
 * a stop that has a distance gets it anew from [here] (the stops are loaded for a position the
 * user may have walked on from; the provider's own order goes by its walking distance from
 * where they were asked for, which need not be the one shown). Stops without a distance (picked
 * by name, opened from elsewhere) follow in their order. [firstId] stays on top where given.
 */
fun stopsByDistance(stops: List<Stop>, here: Pair<Double, Double>?, firstId: String? = null): List<Stop> {
    val measured = stops.map { stop ->
        val position = stop.position
        if (here != null && position != null && stop.distanceMeters != null) {
            stop.copy(distanceMeters = spotMeters(here, position).roundToInt())
        } else {
            stop
        }
    }
    val first = firstId?.let { id -> measured.firstOrNull { it.id == id } }
    val rest = measured.filter { it !== first }
        .sortedWith(compareBy(nullsLast()) { stop: Stop -> stop.distanceMeters })
    return listOfNotNull(first) + rest
}

/**
 * The departures of one [stop] on a board of several. [full] when the provider sent as many as
 * were asked for: later ones of this stop are then missing, not absent.
 */
data class StopDepartures(val stop: Stop, val departures: List<Departure>, val full: Boolean = false)

/**
 * The departures of several stops as one board, by time (see [departureOrderTime]): each marked
 * with its stop (the rows then name it), a run that calls at two of them once, at the first
 * board that has it. The board ends where the first [StopDepartures.full] board ends, so no stop
 * seems to have nothing later only because fewer of its departures were asked for.
 *
 * With [walkSeconds] (for "when to leave", the walk to each stop) that end is measured in the
 * time to set off, not the time of departure: a full board of a near stop then cuts the
 * departures of a far stop where their time to set off passes its own, not where their
 * departure does. Measured by departure, the near stop's short board cut a far stop down to
 * the few departures it can no longer reach on foot, and "when to leave" showed the near stop
 * alone.
 */
fun mergeStopBoards(boards: List<StopDepartures>, walkSeconds: (Stop) -> Int? = { null }): List<Departure> {
    val walks = boards.associate { board -> board.stop.id to (walkSeconds(board.stop) ?: 0).toLong() }
    fun leaveInstant(departure: Departure, stopId: String) =
        departureOrderTime(departure)?.toInstant()?.minusSeconds(walks[stopId] ?: 0L)
    val horizon = boards.filter { it.full }
        .mapNotNull { board -> board.departures.mapNotNull { leaveInstant(it, board.stop.id) }.maxOrNull() }
        .minOrNull()
    val seenRuns = HashSet<String>()
    val all = boards.flatMap { board ->
        val stop = board.stop.copy(distanceMeters = null)
        board.departures.map { it.copy(stop = stop, at = it.at ?: stop.position) }
    }.filter { departure -> departure.tripId == null || seenRuns.add(departure.tripId) }
        .filter { departure ->
            val time = departure.stop?.let { leaveInstant(departure, it.id) }
            horizon == null || time == null || !time.isAfter(horizon)
        }
    return inDepartureOrder(all)
}

/**
 * The notices of a board (see [disruptionsOfDepartures]), each once: those about a station
 * (lifts, a moved stop) first. With [lines] (the lines left by a filter) only the notices of
 * those lines, and every station notice.
 */
fun boardDisruptions(departures: List<Departure>, lines: Set<String>? = null): List<Disruption> {
    val all = disruptionsOfDepartures(departures)
    val kept = if (lines == null) all else all.filter { it.station || it.on(lines) }
    return kept.sortedBy { if (it.station) 0 else 1 }
}

/**
 * A key for each row of a departures list that stays the same from one refresh to the next, so
 * a row keeps its place in the composition (its times roll, it moves to its new place) instead
 * of being drawn anew: the run ([Departure.tripId]) with its planned time and stop, or without a
 * run its line, direction, planned time and stop. Never its index, which shifts whenever an
 * earlier departure leaves the list. Rows that would share a key get "#2", "#3", … in order.
 */
fun departureKeys(departures: List<Departure>): List<String> {
    val seen = HashMap<String, Int>()
    return departures.map { departure ->
        val planned = (departure.plannedTime ?: departure.time)?.toEpochSecond()
        val stop = departure.stop?.id.orEmpty()
        val base = if (departure.tripId != null) {
            "t|${departure.tripId}|$planned|$stop"
        } else {
            "p|${departure.line}|${departure.direction}|$planned|$stop"
        }
        val count = (seen[base] ?: 0) + 1
        seen[base] = count
        if (count == 1) base else "$base#$count"
    }
}

/** The least radius of "all stops nearby", in metres. */
const val NEARBY_RADIUS_MIN = 100

/** The largest radius of "all stops nearby", in metres. */
const val NEARBY_RADIUS_MAX = 2_000

/** The radius of "all stops nearby" moves in steps of this many metres. */
const val NEARBY_RADIUS_STEP = 50

/** The radius of "all stops nearby" until the user picks another. */
const val NEARBY_RADIUS_DEFAULT = 500

/** The most stops "all stops nearby" asks departures of, however large the radius: one request each, every round. */
const val NEARBY_STOPS_CAP = 30

/** The fewest stops "all stops nearby" keeps (the count of old, enough for the default radius). */
const val NEARBY_STOPS_FLOOR = 10

/**
 * How many of the nearest stops "all stops nearby" keeps within [radius] metres: more with a
 * larger radius (one per 50 m of it, so 10 at the default 500 m, 20 at 1 km), never fewer than
 * [NEARBY_STOPS_FLOOR] nor more than [NEARBY_STOPS_CAP]. In a sparse area all stops within the
 * radius fit; in a dense one the farthest are left out, so the requests stay bounded.
 */
fun nearbyStopsKept(radius: Int): Int =
    (nearbyRadiusOf(radius) / NEARBY_RADIUS_STEP).coerceIn(NEARBY_STOPS_FLOOR, NEARBY_STOPS_CAP)

/**
 * How many stops to ask the provider for, to keep [nearbyStopsKept] of them within [radius]:
 * twice as many, since some answers are no stops or lie outside the circle (the provider
 * counts its own walk), at most two times [NEARBY_STOPS_CAP].
 */
fun nearbyStopsAsked(radius: Int): Int = nearbyStopsKept(radius) * 2

/**
 * [meters] as a radius of "all stops nearby": within [NEARBY_RADIUS_MIN] and
 * [NEARBY_RADIUS_MAX], on the nearest step of [NEARBY_RADIUS_STEP] (a stored 300, 500 or 800 of
 * the former fixed choices stays as it is).
 */
fun nearbyRadiusOf(meters: Int): Int {
    val stepped = Math.round(meters.toDouble() / NEARBY_RADIUS_STEP).toInt() * NEARBY_RADIUS_STEP
    return stepped.coerceIn(NEARBY_RADIUS_MIN, NEARBY_RADIUS_MAX)
}
