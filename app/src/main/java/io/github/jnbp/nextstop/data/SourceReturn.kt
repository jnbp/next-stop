package io.github.jnbp.nextstop.data

import java.time.Duration

/*
 * #185: at a station with a poor connection the search fell back to Transitous, and the followed
 * trip then stayed there to its end: its refresh token carries the provider it came from (see
 * RoutingSource.refreshJourney), so every refresh went back to Transitous, never to the VBB
 * family that knows the S-Bahn live. While a trip is followed (the trip service or the app in
 * front, never in the background otherwise: battery rule), the preferred provider is asked again
 * now and then, backing off (1, 2, 4, 8 min, then every 10 min); once it answers with the very
 * same journey, the trip goes on with its handle. The same journey: the same rides in the same
 * order (by line, the stations of boarding and getting off by StationIdentity, the planned
 * departure within a few minutes); the walks may differ and come from the preferred provider.
 * The names and planned times of the followed rides are kept and so is its id, so reminders,
 * alarms and everything on screen stay put.
 */

/** The first re-probe of the preferred provider waits this long, each next one twice as long ... */
const val RETURN_PROBE_FIRST_MILLIS = 60_000L

/** ... up to this. */
const val RETURN_PROBE_MAX_MILLIS = 10 * 60_000L

// Two rides are one when their planned departures lie at most this far apart (s).
private const val SAME_RIDE_SECONDS = 180L

/** The wait before re-probe number [attempt] (0 for the first): 1, 2, 4, 8, then 10 minutes. */
fun returnProbeDelayMillis(attempt: Int): Long {
    val shift = attempt.coerceIn(0, 10)
    return minOf(RETURN_PROBE_FIRST_MILLIS shl shift, RETURN_PROBE_MAX_MILLIS)
}

/**
 * When to ask the preferred provider again about a followed trip that runs on a stand-in
 * (see [returnProbeDelayMillis]). [due] at [nowMillis]; [failed] after an attempt that brought
 * no match, [reset] for a new trip (or once back on the preferred provider). Not thread-safe:
 * one per refresh loop.
 */
class ReturnProbe(private val firstAtMillis: Long) {
    private var attempt = 0
    private var nextAtMillis = firstAtMillis + returnProbeDelayMillis(0)

    fun due(nowMillis: Long): Boolean = nowMillis >= nextAtMillis

    fun failed(nowMillis: Long) {
        attempt++
        nextAtMillis = nowMillis + returnProbeDelayMillis(attempt)
    }

    fun reset(nowMillis: Long) {
        attempt = 0
        nextAtMillis = nowMillis + returnProbeDelayMillis(0)
    }
}

/** True when [a] and [b] are one stop: the same station by [StationIdentity], else by name. */
private fun sameStop(aName: String, a: Stop?, bName: String, b: Stop?): Boolean {
    if (a != null && b != null && StationIdentity.same(a, b)) return true
    return aName == bName || StationIdentity.namesMatch(aName, bName)
}

private fun returnLineKey(line: String?): String = line.orEmpty().replace(" ", "").lowercase()

/** True when [fresh] is the same leg as [known] (of two providers): see the top of this file. */
internal fun sameLeg(known: Leg, fresh: Leg): Boolean {
    if (known.walking != fresh.walking) return false
    if (known.walking) return true
    if (returnLineKey(known.line) != returnLineKey(fresh.line)) return false
    if (!sameStop(known.from, known.fromPlace, fresh.from, fresh.fromPlace)) return false
    if (!sameStop(known.to, known.toPlace, fresh.to, fresh.toPlace)) return false
    val planned = Duration.between(known.plannedDeparture ?: known.departure, fresh.plannedDeparture ?: fresh.departure).seconds
    return kotlin.math.abs(planned) <= SAME_RIDE_SECONDS
}

/**
 * [fresh] (of another provider) as the followed [known] journey: its live times, stops, ways,
 * walks and handles, with the names, lines and planned times of the rides of [known], and the
 * id of [known] (see [Journey.keptId]). Only the rides are compared (see [sameLeg]): the same
 * number, in the same order, each the same; the walks may differ in number and length, as two
 * providers lead them their own way. Null where [fresh] is not the same journey.
 */
fun ontoKnown(fresh: Journey, known: Journey): Journey? {
    if (fresh.split != null || known.split != null) return null
    val freshRides = fresh.legs.indices.filter { !fresh.legs[it].walking }
    val knownRides = known.legs.indices.filter { !known.legs[it].walking }
    if (freshRides.isEmpty() || freshRides.size != knownRides.size) return null
    if (!freshRides.indices.all { sameLeg(known.legs[knownRides[it]], fresh.legs[freshRides[it]]) }) return null
    val legs = fresh.legs.toMutableList()
    freshRides.forEachIndexed { ride, index ->
        val leg = legs[index]
        val before = known.legs[knownRides[ride]]
        legs[index] = leg.copy(
            line = before.line,
            from = before.from,
            to = before.to,
            // The timetable as known; none is made up from live times (it showed as a delay).
            plannedDeparture = before.plannedDeparture ?: leg.plannedDeparture,
            plannedArrival = before.plannedArrival ?: leg.plannedArrival,
            // A search may bring neither ways nor stops on the way: the known ones until the next refresh.
            path = leg.path.ifEmpty { before.path },
            stopovers = leg.stopovers.ifEmpty { before.stopovers },
        )
    }
    // The id of [known] stays; kept apart only where the walks make the legs another id.
    // Rides without planned times keep it by their shape (see KeptJourneyId.loose).
    val keptId = when {
        journeyLegsId(legs) == known.id -> null
        ridesPlanned(legs) -> KeptJourneyId(known.id, journeyRideStamp(legs))
        else -> KeptJourneyId(known.id, journeyRideShape(legs), loose = true)
    }
    val via = known.via?.let { via -> via.copy(afterLegs = via.afterLegs?.let { viaAfterLegs(it, known.legs, legs) }) }
    return fresh.copy(legs = legs, via = via, keptId = keptId)
}

/**
 * The stay after [afterLegs] legs of [knownLegs] in [freshLegs] with the same rides: after the
 * same ride and as many of the walks that follow it as there are.
 */
internal fun viaAfterLegs(afterLegs: Int, knownLegs: List<Leg>, freshLegs: List<Leg>): Int {
    if (knownLegs.size == freshLegs.size) return afterLegs
    val before = knownLegs.take(afterLegs)
    val rides = before.count { !it.walking }
    val walksAfter = before.size - (before.indexOfLast { !it.walking } + 1)
    var at = if (rides == 0) 0 else freshLegs.indices.filter { !freshLegs[it].walking }.getOrNull(rides - 1)?.plus(1) ?: return afterLegs
    var walks = walksAfter
    while (walks > 0 && at < freshLegs.size && freshLegs[at].walking) {
        at++
        walks--
    }
    return at
}

/** Of [candidates] (a search of the preferred provider), the followed [known] journey (see [ontoKnown]); null for none. */
fun preferredTwin(known: Journey, candidates: List<Journey>): Journey? =
    candidates.firstNotNullOfOrNull { ontoKnown(it, known) }

/**
 * [fresh], the followed [known] journey loaded again, with the names and planned times of
 * [known] where a provider other than the one [known] came from names them differently (after a
 * return to the preferred provider, see [preferredTwin]), and the id [known] kept: the id stays.
 * As it is otherwise.
 */
fun withKnownNames(fresh: Journey, known: Journey): Journey {
    val freshRides = fresh.rides
    val knownRides = known.rides
    if (freshRides.size != knownRides.size) return fresh
    val differs = freshRides.indices.any { freshRides[it].from != knownRides[it].from || freshRides[it].to != knownRides[it].to || freshRides[it].line != knownRides[it].line }
    if (!differs && known.keptId == null) return fresh
    return ontoKnown(fresh, known) ?: fresh
}
