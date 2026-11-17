package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.cos

/**
 * Where a new plan on the way of a followed journey can start: at [place], not before [time].
 * [kept] are the legs of the journey that stay as they are, the ones done and the one that
 * leads to [place]; with [cut] the last of them is the ride on board, cut short at [place].
 * [stage] changes whenever the traveller passes a stop or starts another leg.
 */
data class ReplanPoint(
    val place: Stop,
    val time: OffsetDateTime,
    val kept: List<Leg>,
    val cut: Boolean,
    val stage: String,
) {
    /** True while the traveller rides towards [place]: the change happens on getting off there. */
    val onBoard: Boolean get() = stage.startsWith("ride")
}

// A stop the vehicle reaches within this time is passed before the traveller could get off there.
const val GET_OFF_MARGIN_SECONDS = 30L

/**
 * The next point of [journey] from which the traveller can still take another way at [now]:
 * on board the next stop of the vehicle, while waiting the stop of the next ride, on a walk the
 * place the walk started from. Null once the trip is over, when no ride is left to win time
 * with, or where the provider names no place.
 */
fun replanPoint(journey: Journey, now: OffsetDateTime): ReplanPoint? {
    val legs = journey.legs
    val index = legs.indexOfFirst { it.arrival.isAfter(now) }
    if (index < 0) return null
    if (legs.drop(index).none { !it.walking }) return null
    val leg = legs[index]
    if (!leg.walking && !now.isBefore(leg.departure)) {
        // On board: the next stop that comes late enough to get off there, the end of the ride at the latest.
        val limit = now.plusSeconds(GET_OFF_MARGIN_SECONDS)
        val stops = leg.stopovers
        val next = (1 until stops.size - 1).firstOrNull { position ->
            val stop = stops[position]
            stop.place != null && (stop.arrival ?: stop.departure)?.isAfter(limit) == true
        }
        if (next != null) {
            val stop = stops[next]
            return ReplanPoint(
                place = stop.place!!.copy(distanceMeters = null),
                time = stop.arrival ?: stop.departure!!,
                kept = legs.take(index) + cutLeg(leg, next),
                cut = true,
                stage = "ride$index@$next",
            )
        }
        val end = leg.toPlace ?: return null
        return ReplanPoint(end.copy(distanceMeters = null), leg.arrival, legs.take(index + 1), false, "ride$index@end")
    }
    // Waiting for a ride, or before or on a walk: from where that leg starts, from now on.
    val start = leg.fromPlace ?: legs.getOrNull(index - 1)?.toPlace ?: return null
    val stage = if (leg.walking) "walk$index" else "wait$index"
    return ReplanPoint(start.copy(distanceMeters = null), now, legs.take(index), false, stage)
}

/**
 * [leg] as far as its stop on the way at [index]: the traveller gets off there. The way on
 * the map ends at the point nearest to that stop.
 */
fun cutLeg(leg: Leg, index: Int): Leg {
    val stop = leg.stopovers[index]
    val arrival = stop.arrival ?: stop.departure ?: leg.arrival
    val path = pathUpTo(leg.path, stop.place?.position)
    return leg.copy(
        to = stop.name,
        toPlace = stop.place,
        arrival = arrival,
        plannedArrival = stop.plannedArrival ?: stop.delaySeconds?.let { arrival.minusSeconds(it.toLong()) },
        arrivalPlatform = null,
        stopovers = leg.stopovers.take(index + 1),
        path = path,
    )
}

/** [path] up to its point nearest to [position]; all of it without a position. */
private fun pathUpTo(path: List<Pair<Double, Double>>, position: Pair<Double, Double>?): List<Pair<Double, Double>> {
    if (position == null || path.size < 2) return path
    val scale = cos(Math.toRadians(position.first))
    val nearest = path.indices.minByOrNull { point ->
        val north = path[point].first - position.first
        val east = (path[point].second - position.second) * scale
        north * north + east * east
    } ?: path.lastIndex
    return path.take(nearest + 1)
}

/** The widest a later part of a ride may have moved in time and still be the same run (see [withoutSeamOverlap]). */
private const val SAME_RUN_DRIFT_MINUTES = 15L

/**
 * [head] and then [tail] as the two parts of a journey whose last ride of [head] goes on into
 * the first ride of [tail] (see [JourneySplit.tailJoined]). Where the last leg of [head] already
 * holds that ride too (a joined leg kept as it was known, because the fresh first part did not
 * name the stop it was cut at), it is cut back to where the second part starts, so the ride is
 * there once: at that stop among its stops, else by the time of the junction.
 */
fun withoutSeamOverlap(head: List<Leg>, tail: List<Leg>): List<Leg> {
    val last = head.lastOrNull() ?: return head
    val first = tail.firstOrNull()?.takeIf { !it.walking } ?: return head
    val runIndex = last.throughRuns.indexOfFirst { run ->
        (run.tripId != null && run.tripId == first.tripId) || (
            sameStation(run.at, run.atName, first.fromPlace, first.from) &&
                rapidLineName(run.line, last.product) == rapidLineName(first.line, first.product) &&
                kotlin.math.abs(java.time.Duration.between(run.departure, first.departure).toMinutes()) <= SAME_RUN_DRIFT_MINUTES
            )
    }
    if (runIndex < 0) return head
    val run = last.throughRuns[runIndex]
    val index = last.stopovers.indexOfLast { sameStation(it.place, it.name, run.at, run.atName) }
    val cut = if (index > 0) {
        cutLeg(last, index)
    } else {
        last.copy(
            to = run.atName,
            toPlace = run.at,
            arrival = run.departure,
            plannedArrival = null,
            arrivalPlatform = null,
            stopovers = last.stopovers.filter { stop -> stop.time?.isAfter(run.departure) != true },
            path = pathUpTo(last.path, run.at?.position),
        )
    }
    return head.dropLast(1) + cut.copy(throughRuns = last.throughRuns.take(runIndex))
}

/**
 * The followed [journey] up to [point], then [option], a journey the search found from
 * [point] on. A ride cut short stays cut when it is loaded again.
 */
fun spliceAt(journey: Journey, point: ReplanPoint, option: Journey): Journey {
    val keep = point.kept.size
    if (!point.cut) return spliceJourney(journey, keep, option)
    val before = journey.split
    val headToken = when {
        before == null -> journey.refreshToken
        before.tailJoined && keep >= before.headLegs -> null
        keep <= before.headLegs -> before.headToken
        else -> null
    }
    return mergeThroughRuns(
        Journey(
            legs = point.kept + option.legs,
            split = JourneySplit(
                headLegs = keep,
                headToken = headToken,
                tailToken = option.refreshToken,
                headCut = point.kept.last().to,
            ),
            via = splicedVia(journey, keep, option),
        ),
    )
}

// An alternative counts from this many seconds sooner at the destination; less is noise of the live data.
const val MIN_GAIN_SECONDS = 60L

/**
 * The change level of a search on the way: Sprint for "Jede Minute zählt" ([everyMinute]),
 * which takes every change however tight; for the shortcut alert alone the user's own
 * level ([userLevel]).
 */
fun shortcutComfort(everyMinute: Boolean, userLevel: TransferComfort): TransferComfort =
    if (everyMinute) TransferComfort.VeryRisky else userLevel

/** True where [option] asks no tighter changes than [level] allows (see journeyComfort). */
fun withinLevel(option: Journey, level: TransferComfort): Boolean = journeyComfort(option).ordinal <= level.ordinal

/**
 * When the chosen [journey] goes on from [point], by the live and by the planned time: on board
 * the arrival at the stop where the traveller could get off, otherwise the departure of the
 * next leg (the ride waited for, or the walk to it).
 */
fun chosenDepartureAt(journey: Journey, point: ReplanPoint): Pair<OffsetDateTime, OffsetDateTime> {
    val onBoard = point.kept.lastOrNull()?.takeIf { point.onBoard }
    if (onBoard != null) return onBoard.arrival to (onBoard.plannedArrival ?: onBoard.arrival)
    val next = journey.legs.getOrNull(point.kept.size) ?: return point.time to point.time
    return next.departure to (next.plannedDeparture ?: next.departure)
}

/**
 * True when [option] leaves [point] no earlier than the chosen [journey] does there, both by
 * the live and by the planned time (see [chosenDepartureAt]): a ride that is only later because
 * it is late, but was planned before the chosen one, is no way the traveller planned for.
 */
fun leavesNoEarlier(journey: Journey, point: ReplanPoint, option: Journey): Boolean {
    val (live, planned) = chosenDepartureAt(journey, point)
    val first = option.legs.firstOrNull() ?: return false
    val optionPlanned = first.plannedDeparture ?: first.departure
    return !first.departure.isBefore(live) && !optionPlanned.isBefore(planned)
}

/**
 * [option] joined to the way the traveller comes to [point] on: the last ride of the kept legs
 * (and a walk after it), so the change at [point] is rated as well, with its walk and change
 * time. Without a ride before (the start of the trip) [option] as it is.
 */
fun joinedAtPoint(point: ReplanPoint, option: Journey): Journey {
    val lastRide = point.kept.indexOfLast { !it.walking }
    if (lastRide < 0) return option
    return mergeThroughRuns(Journey(legs = point.kept.drop(lastRide) + option.legs, foundLevel = option.foundLevel))
}

/**
 * True when every change of [option] from [point] on can be made: the change at [point] (from
 * the ride the traveller comes on, see [joinedAtPoint]) included, and at [level] at most as
 * tight as that level allows. Even a Sprint never takes a ride that leaves before the one
 * before it arrives (plus its walk).
 */
fun reachableAt(point: ReplanPoint, option: Journey, level: TransferComfort): Boolean {
    val joined = joinedAtPoint(point, option)
    val changes = journeyChangesOf(joined)
    if (changes.any { change -> change.arriving.arrival.plusMinutes(change.walkMinutes).isAfter(change.next.departure) }) return false
    return withinLevel(joined, level)
}

/** How many seconds sooner [option] reaches the destination than [current], by the live times. */
fun gainSeconds(current: Journey, option: Journey): Long = Duration.between(option.arrival, current.arrival).seconds

/**
 * The best of [options] found from [point]: one that arrives at least a minute before
 * [current], has a ride and nothing cancelled, leaves no earlier than the traveller is
 * there, and is not one the user put aside ([ignored]: its key and the gain it had then)
 * unless it gained another minute since, and [accept] takes it with its gain (see
 * [shortcutOffer]). The most time won counts first, then fewer changes.
 */
fun fasterOption(
    current: Journey,
    point: ReplanPoint,
    options: List<Journey>,
    ignored: Map<String, Long>,
    accept: (Journey, Long) -> Boolean = { _, _ -> true },
): Pair<Journey, Long>? = options
    .asSequence()
    .filter { !it.cancelled && it.rides.isNotEmpty() && !it.departure.isBefore(point.time.minusSeconds(GET_OFF_MARGIN_SECONDS)) }
    .map { it to gainSeconds(current, it) }
    .filter { (option, gain) ->
        gain >= MIN_GAIN_SECONDS && ignored[journeyKey(option)]?.let { gain >= it + MIN_GAIN_SECONDS } != false &&
            accept(option, gain)
    }
    .sortedWith(compareByDescending<Pair<Journey, Long>> { it.second }.thenBy { it.first.transfers })
    .firstOrNull()

/**
 * The ride of [option] the traveller changes to: on board of [kept]'s last ride, the first
 * ride of another vehicle; otherwise the first ride.
 */
fun changeRide(kept: List<Leg>, cut: Boolean, option: Journey): Leg? {
    val onBoard = kept.lastOrNull()?.takeIf { cut || !it.walking }?.tripId
    return option.rides.firstOrNull { onBoard == null || it.tripId != onBoard }
}
