package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/*
 * #272: long-distance trains with a feeder at the chosen level. A search to another city finds
 * a combination of long-distance trains, but the provider picks the local ride to the first
 * train (or from the last one) by its own minimum change time, which the app's station rule
 * often rates "knapp": the walk from a deep U-Bahn platform to the long-distance platform takes
 * longer than the provider allows for. The journey is rated by its tightest change (see
 * journeyComfort), so the whole combination of trains was "knapp" and at "Normal" it was hidden
 * or offered only as "knapp", although the trains themselves leave plenty of room between them.
 *
 * For such a journey (see [longFeederEnd]) one more search is made: an arrive-by search from the
 * start to the station of the first long-distance train, early enough for a change at the
 * chosen level (see [longFeederLatestArrival]), or a departure search from the station of the
 * last long-distance train late enough for one (see [longFeederEarliestDeparture]). The same
 * trains with that feeder become a journey of their own (see [longFeederVariants]) when the app
 * rates it at the chosen level. The rating rules are the same as for every journey; the tighter
 * original stays in the list, and the selection drops it where it is the same trains arriving in
 * the same minute (see withoutTightTwins) or hides it at the chosen level as before.
 */

/** Which end of the long-distance trains a feeder is looked for at. */
enum class LongFeederEnd { First, Last }

/** The most searches for feeders made for the journeys of one search. */
const val LONG_FEEDER_SEARCH_CAP = 6

/** The journeys asked for by one search for a feeder. */
const val LONG_FEEDER_RESULTS = 3

// A minute more than the change needs at the chosen level: changes count whole minutes, rounded down.
private const val LONG_FEEDER_MARGIN_MINUTES = 1L

/** True for a ride of a long-distance train (ICE, IC, EC and the like). */
fun isLongDistanceRide(leg: Leg): Boolean = !leg.walking && leg.product == "express"

/**
 * The minutes a change must leave beyond what it needs to be rated at [level] (see
 * paceTightness, with the user's settings): null for Sprint, which every change reaches.
 */
fun longFeederSpare(level: TransferComfort): Long? = when (level) {
    TransferComfort.Relaxed -> relaxedSpare
    TransferComfort.Normal -> normalSpare
    TransferComfort.Risky -> 0L
    TransferComfort.VeryRisky -> null
}

/** True when [journey] is rated at [level] or looser (see journeyComfort). */
fun journeyAtLevel(journey: Journey, level: TransferComfort): Boolean = journeyComfort(journey).ordinal <= level.ordinal

/**
 * The end of [journey] whose local part makes it tighter than [level], or null: the journey
 * rides a long-distance train, every change between its first and its last long-distance
 * train is at [level] or looser, and the changes tighter than [level] all lie before the
 * first long-distance train ([LongFeederEnd.First]) or all after the last one
 * ([LongFeederEnd.Last]). Journeys put together by the app or the user ([Journey.split]), with
 * a place on the way, cancelled ones, and Sprint (which hides nothing) never need one.
 */
fun longFeederEnd(journey: Journey, level: TransferComfort): LongFeederEnd? {
    if (level == TransferComfort.VeryRisky || journey.split != null || journey.via != null || journey.cancelled) return null
    val legs = journey.legs
    val first = legs.indexOfFirst { isLongDistanceRide(it) }
    if (first < 0) return null
    val last = legs.indexOfLast { isLongDistanceRide(it) }
    val changes = journeyChangesOf(journey)
    fun tooTight(change: JourneyChange) = transferComfort(change.tightness).ordinal > level.ordinal
    if (changes.any { it.nextIndex in (first + 1)..last && tooTight(it) }) return null
    val head = changes.any { it.nextIndex <= first && tooTight(it) }
    val tail = changes.any { it.nextIndex > last && tooTight(it) }
    return when {
        head && !tail && legs[first].fromPlace != null -> LongFeederEnd.First
        tail && !head && legs[last].toPlace != null -> LongFeederEnd.Last
        else -> null
    }
}

/**
 * The latest arrival of a feeder at the station of a long-distance train that leaves at
 * [departure] (live), where the change needs [need] minutes and must leave [spare] more: a
 * minute earlier still, as changes count whole minutes.
 */
fun longFeederLatestArrival(departure: OffsetDateTime, need: Long, spare: Long): OffsetDateTime =
    departure.minusMinutes(need + spare + LONG_FEEDER_MARGIN_MINUTES)

/**
 * The earliest departure of the way on from the station where a long-distance train arrives at
 * [arrival] (live), where the change needs [need] minutes and must leave [spare] more.
 */
fun longFeederEarliestDeparture(arrival: OffsetDateTime, need: Long, spare: Long): OffsetDateTime =
    arrival.plusMinutes(need + spare)

/**
 * One search for a feeder: at the [end] of the long-distance train [train] (the leg at
 * [trainIndex] of each of [journeys]), at [station], arriving by [time] (first) or leaving
 * from [time] (last). [key] tells searches apart: the same train at the same end and level is
 * searched for once.
 */
class LongFeederRequest(
    val key: String,
    val end: LongFeederEnd,
    val station: Stop,
    val train: Leg,
    val time: OffsetDateTime,
    val journeys: List<Pair<Journey, Int>>,
)

/** The key of a search for a feeder (see [LongFeederRequest.key]): end, train, station and level. */
fun longFeederKey(end: LongFeederEnd, train: Leg, level: TransferComfort): String {
    val station = if (end == LongFeederEnd.First) stationNameKey(train.fromPlace?.name ?: train.from) else stationNameKey(train.toPlace?.name ?: train.to)
    val time = if (end == LongFeederEnd.First) train.plannedDeparture ?: train.departure else train.plannedArrival ?: train.arrival
    return "$end|${train.line?.let { normalLine(it) }}|$station|${time.toInstant().epochSecond / 60}|$level"
}

/**
 * The searches for feeders the journeys of [journeys] need at [level] (see [longFeederEnd]),
 * one per train and end, in the order of the journeys; those with a key in [asked] are left
 * out, and no more than [cap]. The time comes from the change at that end of the first journey
 * that needs it: what it needs (the walk, the user's buffer) and the spare of [level].
 */
fun longFeederRequests(
    journeys: List<Journey>,
    level: TransferComfort,
    asked: Set<String> = emptySet(),
    cap: Int = LONG_FEEDER_SEARCH_CAP,
): List<LongFeederRequest> {
    if (cap <= 0) return emptyList()
    val spare = longFeederSpare(level) ?: return emptyList()
    val requests = LinkedHashMap<String, LongFeederRequest>()
    val members = HashMap<String, MutableList<Pair<Journey, Int>>>()
    for (journey in journeys) {
        val end = longFeederEnd(journey, level) ?: continue
        val legs = journey.legs
        val index = if (end == LongFeederEnd.First) legs.indexOfFirst { isLongDistanceRide(it) } else legs.indexOfLast { isLongDistanceRide(it) }
        val train = legs[index]
        val key = longFeederKey(end, train, level)
        if (key in asked) continue
        val known = members[key]
        if (known != null) {
            known += journey to index
            continue
        }
        if (requests.size >= cap) continue
        val changes = journeyChangesOf(journey)
        val request = if (end == LongFeederEnd.First) {
            val need = changes.firstOrNull { it.nextIndex == index }?.time?.need ?: RAIL_STATION_NEED
            LongFeederRequest(key, end, train.fromPlace ?: continue, train, longFeederLatestArrival(train.departure, need, spare), emptyList())
        } else {
            val need = changes.firstOrNull { it.nextIndex > index }?.time?.need ?: RAIL_STATION_NEED
            LongFeederRequest(key, end, train.toPlace ?: continue, train, longFeederEarliestDeparture(train.arrival, need, spare), emptyList())
        }
        requests[key] = request
        members[key] = mutableListOf(journey to index)
    }
    return requests.values.map { LongFeederRequest(it.key, it.end, it.station, it.train, it.time, members.getValue(it.key)) }
}

/**
 * [journey] with the legs of [head] (a journey from the start to the station of the train at
 * [trainIndex]) in place of everything before that train: null where [head] does not end at
 * that station or gets there after the train leaves. The new first part can be loaded again by
 * its own handle; the trains keep their known times (see refreshed).
 */
fun withEarlierFeeder(journey: Journey, trainIndex: Int, head: Journey): Journey? {
    val train = journey.legs.getOrNull(trainIndex) ?: return null
    val end = head.legs.lastOrNull() ?: return null
    if (head.rides.isEmpty() || head.cancelled) return null
    if (!sameStation(end.toPlace, end.to, train.fromPlace, train.from)) return null
    if (end.arrival.isAfter(train.departure)) return null
    // A head that rides the very train already is no feeder to it.
    if (head.rides.any { isLongDistanceRide(it) && it.tripId != null && it.tripId == train.tripId }) return null
    return mergeThroughRuns(
        Journey(
            legs = head.legs + journey.legs.drop(trainIndex),
            split = JourneySplit(headLegs = head.legs.size, headToken = head.refreshToken, tailToken = null),
            foundLevel = journey.foundLevel,
        ),
    )
}

/**
 * [journey] up to the train at [trainIndex], then [rest] (a journey from the station where that
 * train arrives to the destination): null where [rest] does not start there or leaves before
 * the train arrives.
 */
fun withLaterLastMile(journey: Journey, trainIndex: Int, rest: Journey): Journey? {
    val train = journey.legs.getOrNull(trainIndex) ?: return null
    val start = rest.legs.firstOrNull() ?: return null
    if (rest.rides.isEmpty() || rest.cancelled) return null
    if (!sameStation(start.fromPlace, start.from, train.toPlace, train.to)) return null
    if (start.departure.isBefore(train.arrival)) return null
    return spliceJourney(journey, trainIndex + 1, rest).copy(foundLevel = journey.foundLevel)
}

/**
 * The journeys [request] makes of [found] (the answer to its search) for [level]: for each of
 * its journeys the best variant with the same trains rated at [level] (see [journeyAtLevel]): with
 * the latest start of those with an earlier feeder, the earliest arrival of those with a later
 * last mile. A journey without such a variant adds none.
 */
fun longFeederVariants(request: LongFeederRequest, found: List<Journey>, level: TransferComfort): List<Journey> =
    request.journeys.mapNotNull { (journey, index) ->
        if (request.end == LongFeederEnd.First) {
            found.mapNotNull { withEarlierFeeder(journey, index, it) }
                .filter { journeyAtLevel(it, level) }
                .maxByOrNull { it.departure.toInstant() }
        } else {
            found.mapNotNull { withLaterLastMile(journey, index, it) }
                .filter { journeyAtLevel(it, level) }
                .minByOrNull { it.arrival.toInstant() }
        }
    }
