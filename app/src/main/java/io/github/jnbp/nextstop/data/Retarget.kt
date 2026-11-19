package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** On board, a new destination is searched from at most this many of the next stops of the vehicle. */
const val RETARGET_MAX_STOPS = 8

// Searches on their way at the same time, and the pause between two that start.
private const val RETARGET_PARALLEL = 3
private const val RETARGET_STAGGER_MILLIS = 250L

// Getting off one vehicle to board another at the same stop needs at least this many minutes.
private const val RETARGET_CHANGE_MINUTES = 2L

// The mark at the end of a stage whose ride goes on beyond where the followed journey left it.
private const val EXTENDED = "+"

/**
 * Where a followed [journey] can be left at [now] for another destination. On board: the next
 * stops of the vehicle (up to [max]) that come late enough to get off, also beyond the stop
 * where the journey leaves it, as far as its whole run [course] is known; else the end of the
 * ride. Waiting or walking: where the next leg starts, from now on. Once all is done: the end.
 */
fun retargetPoints(journey: Journey, now: OffsetDateTime, course: TripCourse?, max: Int = RETARGET_MAX_STOPS): List<ReplanPoint> {
    val legs = journey.legs
    val index = legs.indexOfFirst { it.arrival.isAfter(now) }
    if (index < 0) {
        val end = legs.last().toPlace ?: return emptyList()
        return listOf(ReplanPoint(end.copy(distanceMeters = null), now, legs, false, "end"))
    }
    val leg = legs[index]
    if (!leg.walking && !now.isBefore(leg.departure)) {
        val limit = now.plusSeconds(GET_OFF_MARGIN_SECONDS)
        val stops = vehicleStops(leg, course)
        val points = (1 until stops.size)
            .filter { position -> stops[position].place != null && stops[position].time?.isAfter(limit) == true }
            .take(max)
            .map { position ->
                val stop = stops[position]
                val own = position < leg.stopovers.size && leg.stopovers[position].name == stop.name
                ReplanPoint(
                    place = stop.place!!.copy(distanceMeters = null),
                    time = stop.time!!,
                    kept = legs.take(index) + if (own) cutLeg(leg, position) else rideUntil(leg, stops, position, course),
                    cut = true,
                    stage = "ride$index@$position" + if (own) "" else EXTENDED,
                )
            }
        if (points.isNotEmpty()) return points
        val end = leg.toPlace ?: return emptyList()
        return listOf(ReplanPoint(end.copy(distanceMeters = null), leg.arrival, legs.take(index + 1), false, "ride$index@end"))
    }
    val start = leg.fromPlace ?: legs.getOrNull(index - 1)?.toPlace ?: return emptyList()
    val stage = if (leg.walking) "walk$index" else "wait$index"
    return listOf(ReplanPoint(start.copy(distanceMeters = null), now, legs.take(index), false, stage))
}

/**
 * The stops of the vehicle of [leg] from the stop where it was boarded on: from its whole run
 * [course] where that is known and reaches at least as far as the ride, else the ride's own.
 */
fun vehicleStops(leg: Leg, course: TripCourse?): List<Stopover> {
    val own = leg.stopovers
    val stops = course?.stops ?: return own
    // On a ring the boarding stop comes round more than once: the call at the time of the ride.
    val board = TripMatch.boardIndex(stops, leg)
    if (board < 0 || stops.size - board < own.size) return own
    return stops.drop(board)
}

/** [leg] ridden on to the stop at [position] of [stops] (see [vehicleStops]), beyond where it ended. */
fun rideUntil(leg: Leg, stops: List<Stopover>, position: Int, course: TripCourse?): Leg {
    val stop = stops[position]
    val arrival = stop.arrival ?: stop.departure ?: leg.arrival
    val target = stop.place?.position
    val way = course?.path.orEmpty()
    val start = leg.path.firstOrNull() ?: stops.first().place?.position
    // Where the stops come from the whole run, the way is cut between their own calls on it: on
    // a ring the nearest point to a stop anywhere may lie a lap away (see TripMatch).
    val runStops = course?.stops
    val board = if (runStops != null && way.size >= 2) TripMatch.boardIndex(runStops, leg) else -1
    val fromRun = board >= 0 && runStops != null && runStops.size - board == stops.size
    val cut = if (fromRun && runStops != null) {
        TripMatch.cutBetween(way, runStops.map { it.place?.position }, board, board + position)
    } else {
        null
    }
    val path = if (cut != null) {
        cut
    } else if (way.size >= 2 && start != null && target != null) {
        val from = TripMatch.pathIndex(way, start, 0)
        val to = TripMatch.pathIndex(way, target, from)
        if (to > from) way.subList(from, to + 1) else leg.path + target
    } else if (target != null) {
        leg.path + target
    } else {
        leg.path
    }
    return leg.copy(
        to = stop.name,
        toPlace = stop.place,
        arrival = arrival,
        plannedArrival = stop.plannedArrival ?: stop.delaySeconds?.let { arrival.minusSeconds(it.toLong()) },
        arrivalPlatform = null,
        stopovers = stops.take(position + 1),
        path = path,
    )
}

/** A way to the new destination: leave the followed journey at [point], then take [option]. */
data class RetargetChoice(val point: ReplanPoint, val option: Journey)

/**
 * The best of the [found] journeys, each found from its point: the one that arrives first,
 * then the one with fewer changes, then the one that leaves the followed journey sooner.
 * Journeys that leave before the traveller is there are left out; one that changes from the
 * vehicle on board to another one leaves room for the change.
 */
fun bestRetarget(found: List<Pair<ReplanPoint, List<Journey>>>): RetargetChoice? = found
    .flatMap { (point, options) -> options.filter { usable(point, it) }.map { RetargetChoice(point, it) } }
    .minWithOrNull(
        compareBy<RetargetChoice> { it.option.arrival }
            .thenBy { it.option.transfers }
            .thenBy { it.point.time },
    )

private fun usable(point: ReplanPoint, option: Journey): Boolean {
    if (option.cancelled || option.legs.isEmpty()) return false
    if (option.departure.isBefore(point.time.minusSeconds(GET_OFF_MARGIN_SECONDS))) return false
    val onBoard = point.kept.lastOrNull()?.takeIf { point.cut }?.tripId
    val first = option.legs.first()
    // Staying on the same vehicle, or a walk first, needs no room; boarding another one there does.
    if (first.walking || onBoard == null || first.tripId == onBoard) return true
    return !first.departure.isBefore(point.time.plusMinutes(RETARGET_CHANGE_MINUTES))
}

/** The followed [journey] up to where [choice] leaves it, then the way it found. */
fun retargeted(journey: Journey, choice: RetargetChoice): Journey {
    val point = choice.point
    if (!point.stage.endsWith(EXTENDED)) return spliceAt(journey, point, choice.option)
    // The ride goes on beyond where the followed journey left it: the head cannot be loaded
    // again as it was, so only the new part keeps its handle for fresh live data.
    val keep = point.kept.size
    return mergeThroughRuns(
        Journey(
            legs = point.kept + choice.option.legs,
            split = JourneySplit(headLegs = keep, headToken = null, tailToken = choice.option.refreshToken, headCut = point.kept.last().to),
            via = splicedVia(journey, keep, choice.option),
        ),
    )
}

/**
 * Searches the way to [target] from every one of [points] at once, a few at a time and a
 * little apart, with [options]; a point whose search fails counts as one with no way.
 */
suspend fun searchRetarget(
    source: TransitSource,
    points: List<ReplanPoint>,
    target: Stop,
    options: SearchOptions,
): RetargetChoice? {
    val gate = Semaphore(RETARGET_PARALLEL)
    val found = coroutineScope {
        points.mapIndexed { index, point ->
            async {
                delay(index * RETARGET_STAGGER_MILLIS)
                gate.withPermit {
                    val journeys = try {
                        source.journeys(from = point.place, to = target, options = options, results = 3, departure = point.time).journeys
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    point to journeys
                }
            }
        }.awaitAll()
    }
    return bestRetarget(found)
}
