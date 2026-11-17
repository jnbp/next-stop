package io.github.jnbp.nextstop.data

/*
 * One ride, one set of live times. A result list often holds the same ride in several journeys
 * (four journeys that all start with the same bus and change to the same train), each sent by
 * another search or refreshed at another moment. Left as sent, the same change then showed
 * other live times, another delay and another rating from one journey to the next. Here every
 * ride that calls at the same stop at the same planned time on the same run takes the live
 * data of one source: the freshest one, and among equally fresh ones the first with live data.
 * Planned times stay untouched, so the ids of the journeys (see [Journey.id]) do not change.
 *
 * Cancellation is shared only between legs of the same span (same run, boarded and left at the
 * same stops and planned times): a run cancelled only on a later part marks a longer leg on it
 * cancelled, never a shorter one that ends before (see [spanKey]).
 */

/** The live departure of a ride at the stop it is boarded: its run, stop and planned time. */
private fun departureKey(leg: Leg): String? {
    if (leg.walking) return null
    val trip = leg.tripId ?: return null
    val planned = leg.plannedDeparture ?: return null
    return "d|$trip|${leg.fromPlace?.id ?: leg.from}|${planned.toInstant()}"
}

/** The live arrival of a ride at the stop it is left: its run, stop and planned time. */
private fun arrivalKey(leg: Leg): String? {
    if (leg.walking) return null
    val trip = leg.tripId ?: return null
    val planned = leg.plannedArrival ?: return null
    return "a|$trip|${leg.toPlace?.id ?: leg.from + ">" + leg.to}|${planned.toInstant()}"
}

/**
 * The span of a ride: its run from the stop it is boarded to the one it is left, at their planned
 * times. A leg's cancellation flag stands for its own span only (the provider sets it when the run
 * is cancelled at its boarding or alighting stop or anywhere between), so only a leg with the same
 * span may lend it.
 */
private fun spanKey(leg: Leg): String? {
    val departure = departureKey(leg) ?: return null
    val arrival = arrivalKey(leg) ?: return null
    return "$departure>$arrival"
}

/**
 * [journeys] with the live times of every ride shared (see above): each departure and arrival
 * of a run at a stop takes those of the first leg of [fresher] (data loaded later, such as a
 * refreshed journey or a new page) that has it, else of the first leg of [journeys]; a leg with
 * live data ([Leg.realtime]) is taken before one without within each. A ride with no run id or
 * planned time keeps its own.
 */
fun shareLiveTimes(journeys: List<Journey>, fresher: List<Journey> = emptyList()): List<Journey> {
    val departures = HashMap<String, Leg>()
    val arrivals = HashMap<String, Leg>()
    val spans = HashMap<String, Leg>()
    fun collect(source: List<Journey>) {
        // Within one source a leg with live data beats one without; an earlier source beats a later.
        val newDepartures = HashMap<String, Leg>()
        val newArrivals = HashMap<String, Leg>()
        val newSpans = HashMap<String, Leg>()
        for (journey in source) {
            for (leg in journey.legs) {
                departureKey(leg)?.let { key ->
                    val known = newDepartures[key]
                    if (known == null || (!known.realtime && leg.realtime)) newDepartures[key] = leg
                }
                arrivalKey(leg)?.let { key ->
                    val known = newArrivals[key]
                    if (known == null || (!known.realtime && leg.realtime)) newArrivals[key] = leg
                }
                spanKey(leg)?.let { key ->
                    val known = newSpans[key]
                    if (known == null || (!known.realtime && leg.realtime)) newSpans[key] = leg
                }
            }
        }
        newDepartures.forEach { (key, leg) -> departures.putIfAbsent(key, leg) }
        newArrivals.forEach { (key, leg) -> arrivals.putIfAbsent(key, leg) }
        newSpans.forEach { (key, leg) -> spans.putIfAbsent(key, leg) }
    }
    collect(fresher)
    collect(journeys)
    return journeys.map { journey ->
        var changed = false
        val legs = journey.legs.map { leg ->
            val departure = departureKey(leg)?.let { departures[it] }
            val arrival = arrivalKey(leg)?.let { arrivals[it] }
            val span = spanKey(leg)?.let { spans[it] }
            val shared = withSharedLive(leg, departure, arrival, span)
            // A leg is made anew only where something differs (see withSharedLive): the same
            // object is the same leg, without comparing all of it (its stops) with equals.
            if (shared !== leg) changed = true
            shared
        }
        // An unchanged journey stays the very same object: what was worked out for it (its
        // rated changes, see ChangeRatings, and the columns that show it) stays valid.
        if (changed) journey.copy(legs = legs) else journey
    }
}

/**
 * [leg] with the live departure of [departure] and the live arrival of [arrival], where given,
 * and cancelled as [span] (a leg of the same span) says; without one it keeps its own flag.
 */
private fun withSharedLive(leg: Leg, departure: Leg?, arrival: Leg?, span: Leg?): Leg {
    // Copied only where a value differs, so a leg that keeps all of them stays the same object.
    var result = leg
    if (departure != null && departure !== leg) {
        val platform = departure.departurePlatform ?: result.departurePlatform
        if (departure.departure != result.departure || platform != result.departurePlatform) {
            result = result.copy(departure = departure.departure, departurePlatform = platform)
        }
    }
    if (arrival != null && arrival !== leg) {
        val platform = arrival.arrivalPlatform ?: result.arrivalPlatform
        if (arrival.arrival != result.arrival || platform != result.arrivalPlatform || arrival.arrivalEstimated != result.arrivalEstimated) {
            result = result.copy(arrival = arrival.arrival, arrivalPlatform = platform, arrivalEstimated = arrival.arrivalEstimated)
        }
    }
    if (span != null && span !== leg && span.cancelled != result.cancelled) {
        result = result.copy(cancelled = span.cancelled)
    }
    val realtime = (departure ?: leg).realtime || (arrival ?: leg).realtime
    return if (result.realtime == realtime) result else result.copy(realtime = realtime)
}
