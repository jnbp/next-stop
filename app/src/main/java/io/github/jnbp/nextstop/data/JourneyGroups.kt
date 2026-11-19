package io.github.jnbp.nextstop.data

import java.time.Duration

/*
 * #57: fewer, truly different columns. A search early in the morning showed some forty columns
 * that were one journey: the same walk and the same U3, and then one of six buses that all got
 * there within a few minutes (X10, 112, 118, 115, 623, 285), and some of them twice: once as
 * the provider sent it and once as the app put it together from the U3 and a connection at its
 * end (TightJourneys). Each was right on its own; side by side they buried the choice.
 *
 * - The same rides are one journey, whatever the walks (see ridesKey): the best copy stays.
 * - The same rides up to a short last bus or tram (a feeder of at most FEEDER_MINUTES) that
 *   get there within FEEDER_SPREAD_MINUTES of the best one are one column: the best one, with
 *   the number of the others ([Journey.groupedAlternatives]).
 * - A tighter level offers only journeys that are new after that (see newExtras), and at most
 *   TIGHTER_EXTRA_CAP of them.
 */

/** The longest last ride that counts as a feeder to the destination, in minutes. */
const val FEEDER_MINUTES = 15L

/** How much later than the best one a feeder variant may arrive and still go into its column. */
const val FEEDER_SPREAD_MINUTES = 5L

/** The most journeys a tighter level adds to those of the level before it. */
const val TIGHTER_EXTRA_CAP = 6

/**
 * One ride as the vehicle the traveller is in: line, boarding station and planned departure,
 * and the station it is left at. Not the planned arrival: a ride joined by staying seated
 * carries an estimate there that changes once the vehicle's own time is known (see
 * withSeatedTimes), and the same ride found twice must have the same key.
 */
internal fun rideIdentity(ride: Leg, withEnd: Boolean = true): String {
    val line = ride.line?.let { normalLine(it) } ?: "?"
    val leaves = (ride.plannedDeparture ?: ride.departure).toInstant().epochSecond / 60
    val from = stationNameKey(ride.fromPlace?.name ?: ride.from)
    if (!withEnd) return "$line|$from|$leaves"
    return "$line|$from|$leaves>${stationNameKey(ride.toPlace?.name ?: ride.to)}"
}

/** The rides of [journey] as one key (see [rideIdentity]); walks do not count. */
fun ridesKey(journey: Journey): String = journey.rides.joinToString("/") { rideIdentity(it) }

/** The better of two copies or variants first: the earlier arrival, then fewer changes, then the more relaxed level. */
private val bestFirst: Comparator<Journey> = compareBy<Journey> { it.cancelled }
    .thenBy { it.arrival.toInstant() }
    .thenBy { it.transfers }
    .thenBy { journeyComfort(it).ordinal }

/**
 * [journeys] with each set of the same rides (see [ridesKey]) once: the best copy (see
 * bestFirst; of equal ones the first) in the place of the first of them. Journeys without a
 * ride stay as they are.
 */
fun withoutDuplicateRides(journeys: List<Journey>): List<Journey> {
    if (journeys.size < 2) return journeys
    val best = LinkedHashMap<String, Journey>()
    for (journey in journeys) {
        if (journey.rides.isEmpty()) continue
        val key = ridesKey(journey)
        val known = best[key]
        if (known == null || bestFirst.compare(journey, known) < 0) best[key] = journey
    }
    if (best.size == journeys.count { it.rides.isNotEmpty() }) return journeys
    val placed = HashSet<String>()
    return journeys.mapNotNull { journey ->
        if (journey.rides.isEmpty()) return@mapNotNull journey
        val key = ridesKey(journey)
        if (placed.add(key)) best.getValue(key) else null
    }
}

/**
 * The rides of [journey] before its last one when that last one is a short bus or tram (see
 * FEEDER_MINUTES), as a key: the last of them by the vehicle only, wherever it is left, as the
 * stop to get off follows the feeder. Null where the journey has no such feeder or nothing before it.
 */
internal fun feederPrefix(journey: Journey): String? {
    val rides = journey.rides
    if (rides.size < 2 || journey.cancelled) return null
    val last = rides.last()
    val family = modeFamily(last.product)
    if (family != ModeFamily.Bus && family != ModeFamily.Tram) return null
    if (Duration.between(last.departure, last.arrival).toMinutes() > FEEDER_MINUTES) return null
    val before = rides.dropLast(1)
    return before.dropLast(1).joinToString("/") { rideIdentity(it) } + "/" + rideIdentity(before.last(), withEnd = false)
}

/**
 * [journeys] with the variants that only differ in their short last ride (see [feederPrefix])
 * and arrive within FEEDER_SPREAD_MINUTES of the best of them as one: the best one (see
 * bestFirst) in the place of the first of them, counting the others in
 * [Journey.groupedAlternatives]. Other journeys stay as they are, the same objects.
 */
fun groupFeederAlternatives(journeys: List<Journey>): List<Journey> {
    if (journeys.size < 2) return journeys
    val buckets = LinkedHashMap<String, MutableList<Int>>()
    journeys.forEachIndexed { index, journey ->
        feederPrefix(journey)?.let { buckets.getOrPut(it) { mutableListOf() } += index }
    }
    if (buckets.values.none { it.size > 1 }) return journeys
    // For each journey: the one that stands for it (its own index where it stays itself).
    val standsFor = IntArray(journeys.size) { it }
    val extra = IntArray(journeys.size)
    for (members in buckets.values) {
        if (members.size < 2) continue
        val left = members.sortedWith { a, b -> bestFirst.compare(journeys[a], journeys[b]).takeIf { it != 0 } ?: a.compareTo(b) }.toMutableList()
        while (left.isNotEmpty()) {
            val best = left.removeAt(0)
            val limit = journeys[best].arrival.plusMinutes(FEEDER_SPREAD_MINUTES)
            val group = left.filter { !journeys[it].arrival.isAfter(limit) }
            left.removeAll(group)
            if (group.isEmpty()) continue
            // The column stands where the first of the group stood.
            val place = (group + best).min()
            standsFor[place] = best
            for (member in group + best) if (member != place) standsFor[member] = -1
            extra[best] = group.size + group.sumOf { journeys[it].groupedAlternatives }
        }
    }
    return journeys.indices.mapNotNull { index ->
        val shown = standsFor[index]
        when {
            shown < 0 -> null
            extra[shown] > 0 -> journeys[shown].let { it.copy(groupedAlternatives = it.groupedAlternatives + extra[shown]) }
            else -> journeys[shown]
        }
    }
}

/**
 * Of [hidden], the journeys of a tighter level, those that are new beside [shown]: not the same
 * rides as one shown (see [ridesKey]) and not a variant of one shown with another short last
 * ride that gets there within FEEDER_SPREAD_MINUTES of it (see [feederPrefix]); grouped among
 * themselves (see [groupFeederAlternatives]) and at most [cap], the first in their order.
 */
fun newExtras(hidden: List<Journey>, shown: List<Journey>, cap: Int = TIGHTER_EXTRA_CAP): List<Journey> {
    val shownRides = shown.mapTo(HashSet()) { ridesKey(it) }
    val shownFeeders = HashMap<String, MutableList<Journey>>()
    for (journey in shown) feederPrefix(journey)?.let { shownFeeders.getOrPut(it) { mutableListOf() } += journey }
    val fresh = hidden.filter { journey ->
        if (journey.rides.isNotEmpty() && ridesKey(journey) in shownRides) return@filter false
        val prefix = feederPrefix(journey) ?: return@filter true
        shownFeeders[prefix].orEmpty().none { kept ->
            !journey.arrival.isBefore(kept.arrival.minusMinutes(FEEDER_SPREAD_MINUTES)) &&
                !journey.arrival.isAfter(kept.arrival.plusMinutes(FEEDER_SPREAD_MINUTES))
        }
    }
    return groupFeederAlternatives(withoutDuplicateRides(fresh)).take(cap)
}

/*
 * #233: one train, two columns, once running and once "Fällt aus". A search from an address
 * finds several ways to the same long-distance train: by another bus to another station on
 * its run, say. When one of them cannot be done (the train skips the stop it is boarded at
 * today, or its feeder is cancelled), the provider marks that whole journey cancelled while the
 * same train runs in the other one. Both have other rides (see ridesKey), so neither
 * withoutDuplicateRides nor withoutReplacedJourneys (same stations) takes it, and the chart
 * showed the same train with the same arrival twice, once struck through.
 */

/**
 * The ride a journey is made for: its longest ride by planned time (the long-distance train
 * after a short bus). Null for a journey without a ride.
 */
private fun mainRide(journey: Journey): Leg? = journey.rides.maxByOrNull { ride ->
    Duration.between(ride.plannedDeparture ?: ride.departure, ride.plannedArrival ?: ride.arrival).seconds
}

/**
 * True when [ride] (of a running journey) is the very vehicle of [main] and leaves it at the same
 * station at the same planned time: the same run by its handle, or without one the same line.
 * Where it is boarded does not matter: that is what tells the variants apart.
 */
private fun ridesTheSameTrainTo(ride: Leg, main: Leg): Boolean {
    if (ride.walking || ride.cancelled) return false
    val sameRun = if (ride.tripId != null && main.tripId != null) {
        ride.tripId == main.tripId
    } else {
        ride.product == main.product && ride.line != null && main.line != null &&
            normalLine(ride.line) == normalLine(main.line)
    }
    if (!sameRun) return false
    val reach = (ride.plannedArrival ?: ride.arrival).toInstant()
    val mainReach = (main.plannedArrival ?: main.arrival).toInstant()
    return reach == mainReach && sameStation(ride.toPlace, ride.to, main.toPlace, main.to)
}

/**
 * [journeys] without a cancelled variant of a way that another one of them goes (#233): a
 * cancelled journey is left out when a journey that is not cancelled rides the same train as
 * its main ride (see [mainRide]) to the same station at the same planned time and gets to the
 * destination no later. That is one way with one honest status, the one that runs; a train
 * that is cancelled all along stays visible in every journey that takes it, as does a
 * cancelled journey that would have got there earlier. Journeys whose rest the user swapped
 * ([Journey.split]) stay. The order is kept.
 */
fun withoutCancelledVariants(journeys: List<Journey>): List<Journey> {
    if (journeys.size < 2 || journeys.none { it.cancelled }) return journeys
    val running = journeys.filter { !it.cancelled && it.rides.isNotEmpty() }
    if (running.isEmpty()) return journeys
    val kept = journeys.filter { journey ->
        if (!journey.cancelled || journey.split != null) return@filter true
        val main = mainRide(journey) ?: return@filter true
        val arrives = journey.arrival.toInstant()
        running.none { other ->
            !other.arrival.toInstant().isAfter(arrives) && other.rides.any { ridesTheSameTrainTo(it, main) }
        }
    }
    return if (kept.size == journeys.size) journeys else kept
}
