package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime

/**
 * The ways to the destination the user already knew when they chose the followed journey:
 * the connections of the search they picked it from (or, where none is at hand, of a first
 * search right after the start). [arrivals] holds the live arrival each way had then, by its
 * [arrivalKey]; [chosenArrival] the arrival of the chosen journey then; [chosenId] its
 * [Journey.id]. The shortcut alert only speaks up for what changed since that choice: a way
 * that is new, that got faster, or that is now faster because the chosen journey got worse.
 */
data class KnownWays(
    val chosenId: String,
    val chosenArrival: OffsetDateTime,
    val arrivals: Map<String, OffsetDateTime>,
) {
    /**
     * True when [option], [gainSeconds] sooner than the chosen journey now, is news since the
     * choice: a way not known then, or one that wins at least another minute against the
     * chosen journey than it did then. A way known and just as fast then is no news.
     */
    fun isNews(option: Journey, gainSeconds: Long): Boolean {
        val key = arrivalKey(option) ?: return true
        val then = arrivals[key] ?: return true
        val gainThen = Duration.between(then, chosenArrival).seconds
        return gainSeconds >= gainThen + MIN_GAIN_SECONDS
    }

    /**
     * The user switched to [chosen] (an offer they took): it counts as the new choice, with
     * [more] (the ways of the search it came from) known as well.
     */
    fun rebased(chosen: Journey, more: List<Journey>): KnownWays = KnownWays(
        chosenId = chosen.id,
        chosenArrival = chosen.plausible().arrival,
        arrivals = knownArrivals(more + chosen) + arrivals,
    )
}

/**
 * The ways known when [chosen] was picked from [alternatives] (which may contain it); see
 * [KnownWays]. Cancelled ways do not count as known: one that runs again is news.
 */
fun knownWays(chosen: Journey, alternatives: List<Journey>): KnownWays = KnownWays(
    chosenId = chosen.id,
    chosenArrival = chosen.plausible().arrival,
    arrivals = knownArrivals(alternatives + chosen),
)

private fun knownArrivals(journeys: List<Journey>): Map<String, OffsetDateTime> {
    val map = mutableMapOf<String, OffsetDateTime>()
    for (journey in journeys) {
        if (journey.cancelled) continue
        val key = arrivalKey(journey) ?: continue
        val arrival = journey.arrival
        val known = map[key]
        if (known == null || arrival.isBefore(known)) map[key] = arrival
    }
    return map
}

/**
 * How a way reaches the destination, the same wherever it is searched from: line, station
 * and planned arrival of its last ride. A way found on the trip from a stop on the way
 * matches the way of the first search that ends with the same vehicle. Null without a ride.
 */
fun arrivalKey(journey: Journey): String? {
    val ride = journey.rides.lastOrNull() ?: return null
    val line = ride.line?.let { normalLine(it) } ?: "?"
    val place = ride.toPlace?.id?.let { normalStationId(it) }
        ?.takeIf { it.length == 9 && it.startsWith("900") && it.all { c -> c.isDigit() } }
        ?: stationNameKey(ride.toPlace?.name ?: ride.to)
    return "$line@${ride.plannedArrival ?: ride.arrival}>$place"
}

// "Ruhe nach dem Start": this long after the start only bigger gains count.
const val QUIET_START_SECONDS = 180L
const val QUIET_START_GAIN_SECONDS = 180L

/**
 * The rules of the shortcut alert for one search on the way. [everyMinute] is "Jede Minute
 * zählt": Sprint changes and every minute, the two options below do not apply. [userLevel]
 * is the user's own change level. [sameComfort] ("Nur gleich bequeme Alternativen") takes
 * only ways with no more changes ahead than the chosen one. [quietStart] ("Ruhe nach dem
 * Start") asks [QUIET_START_GAIN_SECONDS] during the first [QUIET_START_SECONDS] of the trip.
 */
data class ShortcutRules(
    val everyMinute: Boolean,
    val userLevel: TransferComfort,
    val sameComfort: Boolean = false,
    val quietStart: Boolean = false,
)

/**
 * The way to offer from [options], found from [point] on the followed journey [current]
 * (with live times; its prognosis counts, see [Journey.plausible]), or null. Besides the
 * rules of [fasterOption] the way must be news since the choice ([known], see
 * [KnownWays.isNews]) unless the chosen journey can no longer be made as planned ahead of
 * [point] ([brokenAhead]), and follow [rules]: it leaves [point] no earlier than [current] does
 * ([leavesNoEarlier]) and every change from [point] on fits the level ([reachableAt]).
 * [startedAt] is when the trip was started.
 */
fun shortcutOffer(
    current: Journey,
    point: ReplanPoint,
    options: List<Journey>,
    ignored: Map<String, Long>,
    known: KnownWays,
    rules: ShortcutRules,
    startedAt: OffsetDateTime,
    now: OffsetDateTime,
): Pair<Journey, Long>? {
    val shown = current.plausible()
    val level = shortcutComfort(rules.everyMinute, rules.userLevel)
    val broken = brokenAhead(shown, point)
    val ahead = boardingsAhead(shown.legs.drop(point.kept.size))
    val allowed = options.filter { option ->
        // Never a way that leaves before the chosen one, by the live or by the planned time;
        // every change from where the traveller is on as the level allows (the change at the
        // point itself included), and never one that cannot be made at all.
        leavesNoEarlier(current, point, option) && reachableAt(point, option, level) &&
            (rules.everyMinute || !rules.sameComfort || optionBoardings(point, option) <= ahead)
    }
    val quiet = !rules.everyMinute && rules.quietStart &&
        now.isBefore(startedAt.plusSeconds(QUIET_START_SECONDS))
    val minGain = if (quiet) QUIET_START_GAIN_SECONDS else MIN_GAIN_SECONDS
    return fasterOption(shown, point, allowed, ignored) { option, gain ->
        gain >= minGain && (broken || known.isNews(option, gain))
    }
}

/** The rides boarded on [legs]. */
private fun boardingsAhead(legs: List<Leg>): Int = legs.count { !it.walking }

/**
 * The rides boarded on [option] from [point]: a first ride that goes on with the vehicle the
 * traveller is on (same run, or same line where no run is known) is no boarding.
 */
fun optionBoardings(point: ReplanPoint, option: Journey): Int {
    val rides = option.rides
    val onBoard = point.kept.lastOrNull()?.takeIf { point.onBoard && !it.walking } ?: return rides.size
    val first = rides.firstOrNull() ?: return 0
    val same = if (onBoard.tripId != null && first.tripId != null) {
        onBoard.tripIds.any { it == first.tripId }
    } else {
        onBoard.line != null && first.line != null && normalLine(onBoard.line) == normalLine(first.line)
    }
    return if (same) rides.size - 1 else rides.size
}

/**
 * True when [journey] (with its prognosis) can no longer be made as planned after [point]:
 * a ride ahead is cancelled, or a change ahead is lost because the ride before arrives
 * (plus the walk between) after the next ride leaves.
 */
fun brokenAhead(journey: Journey, point: ReplanPoint): Boolean {
    val legs = journey.legs
    // The ride on board, cut at the point, still counts with its full arrival for the next change.
    val from = (point.kept.size - 1).coerceAtLeast(0)
    if (legs.drop(from).any { it.cancelled && !it.walking }) return true
    val rides = legs.indices.filter { it >= from && !legs[it].walking }
    return rides.zipWithNext().any { (arriving, next) ->
        val walk = legs.subList(arriving + 1, next).sumOf { Duration.between(it.departure, it.arrival).seconds.coerceAtLeast(0) }
        legs[arriving].arrival.plusSeconds(walk).isAfter(legs[next].departure)
    }
}
