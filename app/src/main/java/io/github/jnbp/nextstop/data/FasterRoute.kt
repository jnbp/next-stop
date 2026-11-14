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
    val position = stop.place?.position
    val path = if (position == null || leg.path.size < 2) {
        leg.path
    } else {
        val scale = cos(Math.toRadians(position.first))
        val nearest = leg.path.indices.minByOrNull { point ->
            val north = leg.path[point].first - position.first
            val east = (leg.path[point].second - position.second) * scale
            north * north + east * east
        } ?: leg.path.lastIndex
        leg.path.take(nearest + 1)
    }
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
        keep <= before.headLegs -> before.headToken
        else -> null
    }
    return Journey(
        legs = point.kept + option.legs,
        split = JourneySplit(
            headLegs = keep,
            headToken = headToken,
            tailToken = option.refreshToken,
            headCut = point.kept.last().to,
        ),
        via = splicedVia(journey, keep, option),
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
