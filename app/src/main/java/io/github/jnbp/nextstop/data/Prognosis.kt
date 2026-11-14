package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime

/*
 * Implausible prognoses. A provider sometimes lets a late vehicle "catch up" on paper: several
 * stops in a row at the same minute although the timetable has minutes between them (bus M48:
 * five stops at 16:43 with delays from +24 to +17). Nothing on the road does that. This file
 * finds such stretches and puts a plausible estimate in their place: the last trustworthy
 * delay carried on along the planned run times, shrinking only slowly. It is a derived layer:
 * the sources keep the provider's data as it came, the screens and ratings ask for
 * [plausibleStops], [plausible] (of a course, a leg or a journey) where they use the times.
 */

// A vehicle may make up at most this share of the planned run time between two stops before
// the prognosis counts as implausible: it still needs 60 % of the time the timetable gives.
internal const val MAX_CATCH_UP_SHARE = 0.4

// Live times come in whole minutes: a drop of the delay by this much is never held against a
// prognosis on its own (one stop "at the same minute" as the one before is rounding).
internal const val ROUNDING_SECONDS = 60L

// Where the app estimates, the carried delay shrinks by at most this share of the planned run time.
internal const val ESTIMATE_CATCH_UP_SHARE = 0.3

// An estimated stop is never reached earlier than this after the departure from the one before.
internal const val MIN_RUN_SECONDS = 30L

// Run times below this are not judged: two calls this close are one place in all but name.
private const val JUDGED_RUN_SECONDS = 60L

private fun seconds(from: OffsetDateTime, to: OffsetDateTime): Long = Duration.between(from, to).seconds

/** The times of one call that the check needs: planned and live, at arrival and departure. */
private class Call(
    val plannedArrival: OffsetDateTime,
    val plannedDeparture: OffsetDateTime,
    val arrival: OffsetDateTime,
    val departure: OffsetDateTime,
)

/**
 * The planned and live times of [stop], where it has a prognosis; null for a call without live
 * data or without times. Planned times missing from the provider are the live ones less the delay.
 */
private fun callOf(stop: Stopover): Call? {
    val delay = stop.delaySeconds ?: return null
    val arrival = stop.arrival ?: stop.departure ?: return null
    val departure = stop.departure ?: stop.arrival ?: return null
    val plannedArrival = stop.plannedArrival ?: stop.plannedDeparture ?: arrival.minusSeconds(delay.toLong())
    val plannedDeparture = stop.plannedDeparture ?: stop.plannedArrival ?: departure.minusSeconds(delay.toLong())
    return Call(plannedArrival, plannedDeparture, arrival, departure)
}

/**
 * The calls of a run as the app shows them: where the provider's prognosis is implausible,
 * an estimate marked [Stopover.estimated], else the call as it came.
 *
 * A call is implausible when its delay dropped below what a vehicle can make up: since the last
 * trustworthy call, at most [MAX_CATCH_UP_SHARE] of the planned run time (and any planned dwell),
 * with [ROUNDING_SECONDS] for minute rounding. Times that stand still or go backwards over a
 * planned run of at least a minute fall under this as well. Behind a call the app estimated,
 * a call is also implausible where it would be reached less than [MIN_RUN_SECONDS] after the
 * estimated departure before it.
 *
 * The estimate: the delay of the call before, shrunk by at most [ESTIMATE_CATCH_UP_SHARE] of the
 * planned run time, on the planned times; never earlier than the provider's own time, nor than
 * [MIN_RUN_SECONDS] after the departure before. Its [Stopover.delaySeconds] is the estimated delay.
 *
 * Calls without a prognosis are kept and end a stretch. Returns [stops] itself when nothing
 * needed an estimate, so the result can be compared by identity.
 */
fun plausibleStops(stops: List<Stopover>): List<Stopover> {
    if (stops.size < 2) return stops
    var result: MutableList<Stopover>? = null
    // State after the previous call: its call, its departure as shown, the delay it left with
    // (as shown), the lowest delay still plausible on leaving it, and whether it was estimated.
    var previous: Call? = null
    var shownDeparture: OffsetDateTime? = null
    var shownDelay = 0L
    var floor = 0L
    var previousEstimated = false
    stops.forEachIndexed { index, stop ->
        val call = callOf(stop)
        val before = previous
        val leftAt = shownDeparture
        if (call == null) {
            previous = null
            shownDeparture = null
            previousEstimated = false
            return@forEachIndexed
        }
        val arrivalDelay = seconds(call.plannedArrival, call.arrival)
        val departureDelay = seconds(call.plannedDeparture, call.departure)
        val dwell = seconds(call.plannedArrival, call.plannedDeparture).coerceAtLeast(0)
        val run = if (before == null) -1L else seconds(before.plannedDeparture, call.plannedArrival)
        if (before == null || leftAt == null || run < 0) {
            // The first call with a prognosis: trusted, the start of the check.
            previous = call
            shownDeparture = call.departure
            shownDelay = departureDelay
            floor = maxOf(arrivalDelay - dwell, departureDelay)
            previousEstimated = false
            return@forEachIndexed
        }
        val floorHere = if (run >= JUDGED_RUN_SECONDS) floor - (run * MAX_CATCH_UP_SHARE).toLong() else floor
        val tooFast = run >= JUDGED_RUN_SECONDS && arrivalDelay < floorHere - ROUNDING_SECONDS
        val tooSoon = previousEstimated && seconds(leftAt, call.arrival) < MIN_RUN_SECONDS
        if (!tooFast && !tooSoon) {
            previous = call
            shownDeparture = call.departure
            shownDelay = departureDelay
            // The floor is not lowered by a small drop within the rounding: so drops of a
            // minute at every stop add up and are caught once they are too many.
            floor = maxOf(departureDelay, floorHere - dwell)
            previousEstimated = false
            return@forEachIndexed
        }
        val carried = shownDelay - (run.coerceAtLeast(0) * ESTIMATE_CATCH_UP_SHARE).toLong()
        val estimatedArrivalDelay = maxOf(arrivalDelay, carried)
        val arrival = maxOf(call.plannedArrival.plusSeconds(estimatedArrivalDelay), leftAt.plusSeconds(MIN_RUN_SECONDS), call.arrival)
        val estimatedDepartureDelay = maxOf(departureDelay, estimatedArrivalDelay - dwell)
        val departure = maxOf(call.plannedDeparture.plusSeconds(estimatedDepartureDelay), arrival, call.departure)
        val shownDepartureDelay = seconds(call.plannedDeparture, departure)
        val list = result ?: stops.toMutableList().also { result = it }
        list[index] = stop.copy(
            arrival = if (stop.arrival != null) arrival else null,
            departure = if (stop.departure != null) departure else null,
            delaySeconds = (if (stop.departure != null) shownDepartureDelay else seconds(call.plannedArrival, arrival)).toInt(),
            plannedArrival = stop.plannedArrival ?: call.plannedArrival.takeIf { stop.arrival != null },
            plannedDeparture = stop.plannedDeparture ?: call.plannedDeparture.takeIf { stop.departure != null },
            estimated = true,
        )
        previous = call
        shownDeparture = departure
        shownDelay = shownDepartureDelay
        floor = floorHere - dwell
        previousEstimated = true
    }
    return result ?: stops
}

/** The course with plausible times at its stops (see [plausibleStops]); itself when nothing changed. */
fun TripCourse.plausible(): TripCourse {
    val shown = plausibleStops(stops)
    return if (shown === stops) this else copy(stops = shown)
}

/**
 * The ride with plausible times at its stops (see [plausibleStops]). Where its last stop got an
 * estimate later than the ride's own arrival, that estimate is its arrival, marked
 * [Leg.arrivalEstimated]. Itself when nothing changed; a ride without stops stays as it is.
 */
fun Leg.plausible(): Leg {
    if (walking || stopovers.size < 2) return this
    val shown = plausibleStops(stopovers)
    if (shown === stopovers) return this
    val last = shown.last()
    val estimate = last.time
    val later = last.estimated && estimate != null && estimate.isAfter(arrival)
    return copy(
        stopovers = shown,
        arrival = if (later) estimate!! else arrival,
        // The planned time stays as it was, so a journey keeps its [Journey.id].
        plannedArrival = if (later) plannedArrival ?: arrival else plannedArrival,
        arrivalEstimated = arrivalEstimated || later,
    )
}

/**
 * The journey with plausible times on its rides (see [Leg.plausible]). A walk right after a
 * ride whose arrival was estimated later starts no earlier than that arrival, and so do the
 * walks after it, up to the next ride; the departures of rides keep the provider's times.
 * Legs stay in their order and number; itself when nothing changed.
 */
fun Journey.plausible(): Journey {
    var changed = false
    var reached: OffsetDateTime? = null
    var estimated = false
    val shown = legs.map { leg ->
        if (!leg.walking) {
            val ride = leg.plausible()
            if (ride !== leg) changed = true
            reached = ride.arrival.takeIf { ride.arrivalEstimated }
            estimated = ride.arrivalEstimated
            ride
        } else {
            val from = reached
            if (from != null && leg.departure.isBefore(from)) {
                changed = true
                val shift = Duration.between(leg.departure, from)
                // The planned times stay as they were, so the journey keeps its [Journey.id].
                val walk = leg.copy(
                    departure = from,
                    arrival = leg.arrival.plus(shift),
                    plannedDeparture = leg.plannedDeparture ?: leg.departure,
                    plannedArrival = leg.plannedArrival ?: leg.arrival,
                    arrivalEstimated = estimated,
                )
                reached = walk.arrival
                walk
            } else {
                reached = null
                leg
            }
        }
    }
    return if (changed) copy(legs = shown) else this
}
