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
 */
fun mergeStopBoards(boards: List<StopDepartures>): List<Departure> {
    val horizon = boards.filter { it.full }
        .mapNotNull { board -> board.departures.mapNotNull { departureOrderTime(it)?.toInstant() }.maxOrNull() }
        .minOrNull()
    val seenRuns = HashSet<String>()
    val all = boards.flatMap { board ->
        val stop = board.stop.copy(distanceMeters = null)
        board.departures.map { it.copy(stop = stop, at = it.at ?: stop.position) }
    }.filter { departure -> departure.tripId == null || seenRuns.add(departure.tripId) }
        .filter { departure ->
            val time = departureOrderTime(departure)?.toInstant()
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
