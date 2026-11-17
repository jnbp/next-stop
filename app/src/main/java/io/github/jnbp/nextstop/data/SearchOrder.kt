package io.github.jnbp.nextstop.data

import java.time.Duration

/*
 * The order of the connections a search shows (the list, the chart and the plan view): by the
 * time on the card, the moment the traveller has to set off. That is the departure of the first
 * leg, a walk to the first stop included, live where the provider knows it (Journey.departure,
 * as journeySpanText and the minutes on the card show it). At the same moment the earlier
 * arrival comes first, then fewer rides, then less walking, and last the id, so the order is
 * total: it does not depend on which page or which level of transfer comfort a journey came
 * with, and two journeys swap places only when their times really cross.
 *
 * The list is put in this order every time it changes: when a page of earlier or later
 * journeys comes in, when tighter journeys are added, and when live data replaces a journey.
 * Before, a refresh put new times in place without sorting again, so a delayed journey kept
 * its slot ahead of one that now leaves earlier, and the next page then moved it at once.
 *
 * The choice offered at a change in the trip companion has its own order, by arrival
 * (optionOrder in OptionOrder.kt). The watch sorts its few results by the same rule
 * (searchOrder in W data/TransitRouter.kt); change both together.
 */

/**
 * Connections of a search in the order they are shown: the earliest time to set off first
 * (the start of a leading walk, live where known); then the earlier arrival, fewer rides,
 * less walking; last the [Journey.id], so every list of the same journeys sorts alike.
 * Times compare as instants, so two providers' offsets for the same moment sort together.
 */
val searchOrder: Comparator<Journey> = compareBy<Journey> { it.departure.toInstant() }
    .thenBy { it.arrival.toInstant() }
    .thenBy { it.rides.size }
    .thenBy { journey -> journey.legs.filter { it.walking }.sumOf { Duration.between(it.departure, it.arrival).seconds } }
    .thenBy { it.id }

/**
 * The connections of a search with [added] (a new page, or the pages of several levels of
 * transfer comfort) merged into [shown]: each connection once, and in [searchOrder]. The same
 * connection found twice (by [journeyKey]: its rides, not the handle of the request) keeps
 * the copy that is shown already, so a pick, the stops loaded for it and a swapped rest stay
 * with it; the live times of its rides become those of the added pages (see shareLiveTimes). A cancelled journey whose ride another
 * one replaces with an extra run is left out (see [withoutReplacedJourneys]). A journey that
 * starts with a short feeder to its train also comes on foot (see [withFeederWalks]), unless
 * that walk would have to start before [now].
 */
fun mergeSearchPages(shown: List<Journey>, added: List<Journey>, now: java.time.OffsetDateTime? = null): List<Journey> {
    // A journey with a short feeder to its train comes with its variant on foot (see withFeederWalks).
    val fresh = withFeederWalks(added, now)
    // A twin already shown stays and takes the new one as its other change (see withAltChanges).
    val shownKeys = shown.mapTo(HashSet()) { journeyKey(it) }
    // The rides they share take the freshest live times, those of the added pages (see shareLiveTimes).
    return withoutReplacedJourneys(
        shareLiveTimes(withAltChanges(withoutSameRunTwins((shown + fresh).distinctBy { journeyKey(it) }), shownKeys), fresher = fresh),
    ).sortedWith(searchOrder)
}

/**
 * The run of a train as two providers (or two feeds of one) may name it differently: the kind
 * in front where the name has one ("RJ" of "RJ 174", null for a bare "174") and the train
 * number. Only for trains named by their number: long-distance kinds ("ICE 29", "RJ 174") and
 * bare numbers of three digits or more on rails; regional lines ("RE 1", "RB 10"), S-Bahn and
 * buses have line numbers that many runs share, so they get none.
 */
internal data class TrainRun(val kind: String?, val number: String)

internal fun trainRunOf(ride: Leg): TrainRun? {
    if (ride.walking || ride.product !in RUN_PRODUCTS) return null
    val name = ride.line?.trim()?.replace(Regex("\\s+"), " ") ?: return null
    val match = RUN_NAME.matchEntire(name) ?: return null
    val kind = match.groupValues[1].uppercase().ifEmpty { null }
    val number = match.groupValues[2].trimStart('0').ifEmpty { return null }
    return when {
        // A bare number is only taken for a train run when the ride is known to be a train:
        // with an unknown product it may as well be a line number, which repeats all day.
        kind == null -> TrainRun(null, number).takeIf { number.length >= 3 && ride.product.isNotEmpty() }
        isLongDistanceTrainName(kind) && kind != "D" -> TrainRun(kind, number)
        else -> null
    }
}

// The products a run named by its train number can have.
private val RUN_PRODUCTS = setOf("express", "regional", "")

// A kind of one to four letters or none, then the number: "RJ 174", "ICE29", "174".
private val RUN_NAME = Regex("^([A-Za-z]{1,4})? ?([0-9]{1,6})$")

// How far apart the planned departures of one run may be at two providers.
private const val SAME_RUN_MINUTES = 15L

// How far apart two stops of one run may be at two providers that name them differently.
private const val SAME_STOP_METERS = 400.0

/**
 * True when [a] and [b] are the same journey found twice under different names: the same
 * number of rides, each pair the same train run (the same number; the same kind, or one of
 * them a bare number, see [trainRunOf]), boarded and left at the same stations (by name, or
 * within SAME_STOP_METERS), leaving within SAME_RUN_MINUTES as planned. "RJ 174" of one
 * provider and "174" of another are one; "RE 1" and "RE 10", or an ICE 174 and an RJ 174, never.
 */
fun sameRunTwins(a: Journey, b: Journey): Boolean {
    if (a === b) return false
    val ridesA = a.rides
    val ridesB = b.rides
    if (ridesA.isEmpty() || ridesA.size != ridesB.size) return false
    return ridesA.indices.all { index -> sameRun(ridesA[index], ridesB[index]) }
}

private fun sameRun(a: Leg, b: Leg): Boolean {
    val runA = trainRunOf(a) ?: return false
    val runB = trainRunOf(b) ?: return false
    if (runA.number != runB.number) return false
    if (runA.kind != null && runB.kind != null && runA.kind != runB.kind) return false
    val leaves = Duration.between(a.plannedDeparture ?: a.departure, b.plannedDeparture ?: b.departure).abs()
    if (leaves.toMinutes() > SAME_RUN_MINUTES) return false
    return sameRunStation(a.fromPlace, a.from, b.fromPlace, b.from) && sameRunStation(a.toPlace, a.to, b.toPlace, b.to)
}

// One station at two providers: as sameStation (VBB number or name), else by position.
private fun sameRunStation(placeA: Stop?, nameA: String, placeB: Stop?, nameB: String): Boolean {
    if (sameStation(placeA, nameA, placeB, nameB)) return true
    val at = placeA?.position ?: return false
    val other = placeB?.position ?: return false
    val north = (at.first - other.first) * 111_320.0
    val east = (at.second - other.second) * 111_320.0 * Math.cos(Math.toRadians(at.first))
    return Math.hypot(north, east) <= SAME_STOP_METERS
}

/**
 * How good a copy of a journey is when two providers found it (see [withoutSameRunTwins]):
 * live times first, then names with the kind of the train ("RJ 174" over "174").
 */
private fun twinScore(journey: Journey): Int {
    val rides = journey.rides
    return rides.count { it.realtime } * 100 + rides.count { trainRunOf(it)?.kind != null }
}

/**
 * [kept] with the names of the rides of its dropped twin [other] where only that one has the
 * kind of the train: the live copy "174" of one provider shows as "RJ 174" of the other, so a
 * twin that wins by its live times never loses the kind (see [withoutSameRunTwins]).
 */
internal fun withTwinNames(kept: Journey, other: Journey): Journey {
    // sameRunTwins makes sure both have the same rides in the same order, so they pair by index.
    val otherRides = other.rides
    var ride = -1
    var changed = false
    val legs = kept.legs.map { leg ->
        if (leg.walking) return@map leg
        ride++
        val twin = otherRides.getOrNull(ride) ?: return@map leg
        if (trainRunOf(leg)?.kind == null && trainRunOf(twin)?.kind != null) {
            changed = true
            leg.copy(line = twin.line)
        } else {
            leg
        }
    }
    return if (changed) kept.copy(legs = legs) else kept
}

/**
 * [journeys] with each journey that two providers (or two feeds) found under different names
 * once (see [sameRunTwins]): the better copy stays in the place of the first (live times over
 * planned ones, the full name over a bare number; of equal ones the first). A journey whose
 * rest the user swapped ([Journey.split]) always stays as it is.
 */
fun withoutSameRunTwins(journeys: List<Journey>): List<Journey> {
    if (journeys.size < 2) return journeys
    // Only journeys whose rides all have train numbers can be twins: grouped by those numbers,
    // so the pairs are compared within a group only.
    val groups = journeys.indices.groupBy { index ->
        val runs = journeys[index].rides.map { trainRunOf(it)?.number ?: return@groupBy null }
        runs.takeIf { it.isNotEmpty() }
    }
    val result = journeys.toMutableList()
    val dropped = BooleanArray(journeys.size)
    for ((key, members) in groups) {
        if (key == null || members.size < 2) continue
        for (i in members.indices) {
            val first = members[i]
            if (dropped[first]) continue
            for (j in i + 1 until members.size) {
                val second = members[j]
                if (dropped[second] || !sameRunTwins(result[first], journeys[second])) continue
                if (journeys[second].split != null || result[first].split != null) continue
                result[first] = if (twinScore(journeys[second]) > twinScore(result[first])) {
                    withTwinNames(journeys[second], result[first])
                } else {
                    withTwinNames(result[first], journeys[second])
                }
                dropped[second] = true
            }
        }
    }
    return result.filterIndexed { index, _ -> !dropped[index] }
}

/**
 * True when [better] makes [worse] a pointless column of its own: the same lines in the same
 * order, its first ride leaving no earlier and the destination reached no later, no more
 * changes, and at its tightest change at least as much time to spare (see [JourneyChange.spare]).
 * Two trips of one line a minute apart give such twins: one with a 3 minute change, one with 4.
 * Ways on other lines are always choices of their own; a cancelled journey never decides.
 */
fun dominates(better: Journey, worse: Journey): Boolean =
    dominates(better, worse, leastSpare(better), leastSpare(worse))

private fun dominates(better: Journey, worse: Journey, betterSpare: Long, worseSpare: Long): Boolean {
    if (better === worse || better.cancelled) return false
    val betterRides = better.rides
    val worseRides = worse.rides
    if (betterRides.isEmpty() || betterRides.size != worseRides.size) return false
    if (lineSequence(betterRides) != lineSequence(worseRides)) return false
    if (betterRides.first().departure.isBefore(worseRides.first().departure)) return false
    if (better.arrival.isAfter(worse.arrival)) return false
    if (better.transfers > worse.transfers) return false
    return betterSpare >= worseSpare
}

/** The least time to spare at a change of [journey], in minutes; a journey without a change has all the time. */
private fun leastSpare(journey: Journey): Long = journeyChangesOf(journey).minOfOrNull { it.spare } ?: Long.MAX_VALUE

private fun lineSequence(rides: List<Leg>): List<String> = rides.map { ride -> ride.line?.let { normalLine(it) } ?: ride.product }

/**
 * [journeys] without those another one of them makes pointless (see [dominates]); of twins
 * just as good the first stays. A journey whose rest the user swapped ([Journey.split]) always
 * stays. The order is kept.
 */
fun withoutDominated(journeys: List<Journey>): List<Journey> {
    if (journeys.size < 2) return journeys
    // What [dominates] compares, worked out once per journey instead of once per pair: the
    // pairs grow with the square of the journeys (thousands for a few pages of all levels), and
    // the rides and their line names were made anew for every one of them.
    val facts = journeys.map { DominanceFacts(it) }
    return journeys.filterIndexed { index, journey ->
        journey.split != null || journeys.indices.none { other ->
            other != index &&
                facts[other].dominates(facts[index]) &&
                (other < index || !facts[index].dominates(facts[other]))
        }
    }
}

/** What [dominates] asks of one journey, worked out once (see [withoutDominated]). */
private class DominanceFacts(val journey: Journey) {
    private val rides = journey.rides
    private val lines = lineSequence(rides)
    private val spare = leastSpare(journey)

    /** [dominates] of this journey over [worse]. */
    fun dominates(worse: DominanceFacts): Boolean {
        if (journey === worse.journey || journey.cancelled) return false
        if (rides.isEmpty() || rides.size != worse.rides.size) return false
        if (lines != worse.lines) return false
        if (rides.first().departure.isBefore(worse.rides.first().departure)) return false
        if (journey.arrival.isAfter(worse.journey.arrival)) return false
        // The same number of rides (above), so no more changes either (Journey.transfers made its rides anew).
        return spare >= worse.spare
    }
}

/*
 * The same rides changed at another stop. A bus to a U-Bahn line can often be changed at two
 * stops (the bus meets the line twice): the providers return one journey per change point,
 * with the same vehicles, the same start and the same arrival. They are one choice with a
 * detail, not two columns: the better change stays (see altChangeOrder), the other is offered
 * on it ([Journey.altChanges]) and can be switched to (see switchToAltChange).
 */

/**
 * True when [a] and [b] ride the very same vehicles from the same boarding to the same arrival
 * and only change at another stop somewhere: the same number of rides (two at least), each
 * pair one run (the same handle, or the same line meeting at one end at the same planned
 * time), the first boarded and the last left at the same station at the same planned time.
 * Journeys on other lines, at other times or with a swapped rest are never such twins.
 */
fun sameRidesOtherChange(a: Journey, b: Journey): Boolean {
    if (a === b || a.split != null || b.split != null) return false
    val ridesA = a.rides
    val ridesB = b.rides
    if (ridesA.size < 2 || ridesA.size != ridesB.size) return false
    val firstA = ridesA.first()
    val firstB = ridesB.first()
    val lastA = ridesA.last()
    val lastB = ridesB.last()
    if (!sameStation(firstA.fromPlace, firstA.from, firstB.fromPlace, firstB.from) || plannedLeave(firstA) != plannedLeave(firstB)) return false
    if (!sameStation(lastA.toPlace, lastA.to, lastB.toPlace, lastB.to) || plannedReach(lastA) != plannedReach(lastB)) return false
    if (!ridesA.indices.all { sameVehicle(ridesA[it], ridesB[it]) }) return false
    // Somewhere the change is at another stop; else they are the same journey.
    return ridesA.indices.any { index ->
        !sameStation(ridesA[index].toPlace, ridesA[index].to, ridesB[index].toPlace, ridesB[index].to) ||
            !sameStation(ridesA[index].fromPlace, ridesA[index].from, ridesB[index].fromPlace, ridesB[index].from)
    }
}

private fun plannedLeave(ride: Leg) = (ride.plannedDeparture ?: ride.departure).toInstant()

private fun plannedReach(ride: Leg) = (ride.plannedArrival ?: ride.arrival).toInstant()

// One vehicle in two journeys, boarded or left at other stops.
private fun sameVehicle(a: Leg, b: Leg): Boolean {
    if (a.tripId != null && a.tripId == b.tripId) return true
    if (a.product != b.product) return false
    val lineA = a.line?.let { normalLine(it) } ?: return false
    val lineB = b.line?.let { normalLine(it) } ?: return false
    if (lineA != lineB) return false
    // Without one handle: the same line at the same stop at the same planned time at one end.
    val sameStart = sameStation(a.fromPlace, a.from, b.fromPlace, b.from) && plannedLeave(a) == plannedLeave(b)
    val sameEnd = sameStation(a.toPlace, a.to, b.toPlace, b.to) && plannedReach(a) == plannedReach(b)
    return sameStart || sameEnd
}

/** How good the changes of a journey are, to choose among twins of [sameRidesOtherChange]. */
private class ChangeQuality(journey: Journey) {
    private val changes = journeyChangesOf(journey)
    val worst = changes.maxOfOrNull { it.tightness.ordinal } ?: 0
    val spare = changes.minOfOrNull { it.spare } ?: Long.MAX_VALUE
    val walk = changes.sumOf { it.walkMinutes }
}

/**
 * The order of twins that only change at other stops: the better rating of the worst change
 * first, then the more time to spare at the tightest change, then the shorter walk.
 */
private val altChangeOrder: Comparator<ChangeQuality> = compareBy<ChangeQuality> { it.worst }
    .thenByDescending { it.spare }
    .thenBy { it.walk }

/**
 * The change of [other] that [kept] makes elsewhere, as an offer on [kept]: the first change
 * whose stop differs, with its walk and rating. Null where none differs.
 */
internal fun altChangeOf(kept: Journey, other: Journey): JourneyAltChange? {
    val keptRides = kept.rides
    val otherRides = other.rides
    if (keptRides.size != otherRides.size) return null
    val index = (0 until otherRides.size - 1).firstOrNull { i ->
        !sameStation(keptRides[i].toPlace, keptRides[i].to, otherRides[i].toPlace, otherRides[i].to) ||
            !sameStation(keptRides[i + 1].fromPlace, keptRides[i + 1].from, otherRides[i + 1].fromPlace, otherRides[i + 1].from)
    } ?: return null
    // The change of [other] whose next ride is ride index + 1.
    val nextLeg = other.legs.indices.filter { !other.legs[it].walking }[index + 1]
    val change = journeyChangesOf(other).firstOrNull { it.nextIndex == nextLeg }
    val arriving = otherRides[index]
    return JourneyAltChange(
        changeIndex = index,
        at = arriving.toPlace?.name ?: arriving.to,
        walkMinutes = change?.walkMinutes ?: 0,
        rating = change?.tightness ?: Tightness.Comfortable,
        journey = other.copy(altChanges = emptyList()),
    )
}

/** [kept] offering every other journey of [others] that changes elsewhere, each once. */
private fun withOffers(kept: Journey, others: List<Journey>): Journey {
    val keptKey = journeyKey(kept)
    val offers = others
        .distinctBy { journeyKey(it) }
        .filter { journeyKey(it) != keptKey }
        .mapNotNull { altChangeOf(kept, it) }
    return if (offers == kept.altChanges) kept else kept.copy(altChanges = offers)
}

/**
 * [journeys] with each group of twins that ride the same vehicles and only change at other
 * stops (see [sameRidesOtherChange]) as one journey in the place of the first: the one with
 * the better change (see altChangeOrder; of equal ones the first), offering the others
 * ([Journey.altChanges]). A journey that offers the other already (the variant the user
 * switched to, see [switchToAltChange]) stays as it is, and so does one already shown (its key
 * in [shownKeys]): the pick, the stops loaded for it and a swapped rest stay with it, and a
 * better twin found later is offered on it. Genuinely different journeys stay apart.
 */
fun withAltChanges(journeys: List<Journey>, shownKeys: Set<String> = emptySet()): List<Journey> {
    if (journeys.size < 2) return journeys
    // Only journeys with the same start, arrival and lines at both ends can be such twins.
    val groups = journeys.indices.groupBy { index ->
        val journey = journeys[index]
        val rides = journey.rides
        if (rides.size < 2 || journey.split != null) {
            null
        } else {
            "${rides.size}|${plannedLeave(rides.first())}|${plannedReach(rides.last())}|" +
                "${rides.first().line?.let { normalLine(it) }}|${rides.last().line?.let { normalLine(it) }}"
        }
    }
    val result = journeys.toMutableList()
    val dropped = BooleanArray(journeys.size)
    for ((key, members) in groups) {
        if (key == null || members.size < 2) continue
        for (i in members.indices) {
            val first = members[i]
            if (dropped[first]) continue
            for (j in i + 1 until members.size) {
                val second = members[j]
                if (dropped[second]) continue
                val a = result[first]
                val b = journeys[second]
                if (!sameRidesOtherChange(a, b)) continue
                val aOffersB = a.altChanges.any { journeyKey(it.journey) == journeyKey(b) }
                val bOffersA = b.altChanges.any { journeyKey(it.journey) == journeyKey(a) }
                val aShown = journeyKey(a) in shownKeys
                val bShown = journeyKey(b) in shownKeys
                val keep = when {
                    aShown && !bShown -> a
                    bShown && !aShown -> b
                    aOffersB -> a
                    bOffersA -> b
                    altChangeOrder.compare(ChangeQuality(b), ChangeQuality(a)) < 0 -> b
                    else -> a
                }
                val other = if (keep === a) b else a
                result[first] = withOffers(keep, keep.altChanges.map { it.journey } + other + other.altChanges.map { it.journey })
                dropped[second] = true
            }
        }
    }
    return result.filterIndexed { index, _ -> !dropped[index] }
}

/** The number of legs to keep that asks to put a whole other variant in place (see switchToAltChange). */
const val ALT_CHANGE_SWITCH = -1

/**
 * [journey] changed at the stop of [alt] instead: that variant, offering [journey] and its
 * other variants in turn, so the user can switch back.
 */
fun switchToAltChange(journey: Journey, alt: JourneyAltChange): Journey {
    val target = alt.journey
    val others = listOf(journey.copy(altChanges = emptyList())) + journey.altChanges.map { it.journey }
    return withOffers(target.copy(altChanges = emptyList()), others)
}
