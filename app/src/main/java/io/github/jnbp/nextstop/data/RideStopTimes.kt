package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime

/*
 * #226: the stop rows of a ride took their time from the stops the journey brought, while its
 * head took it from the ride itself. Live data shared between journeys (see SharedLive) and the
 * steady departures (see SteadyLive) update only the ride's own departure and arrival, so the
 * boarding row could show the old timetable time in white ("21:51") under a head saying
 * "ab 21:54". The ends of a ride therefore always show the ride's own times: the departure at
 * the boarding stop, the arrival at the stop of getting off. A stop between shows its
 * departure (its arrival only where it has none), with the delay of that same time.
 */

/**
 * One time of a stop on a ride as it is to be shown: [actual] (live where known) planned for
 * [planned], [realtime] whether the provider sent live data, [delayMinutes] its delay,
 * [estimated] whether the app estimated it (see plausibleStops).
 */
data class StopTime(
    val actual: OffsetDateTime,
    val planned: OffsetDateTime?,
    val realtime: Boolean,
    val delayMinutes: Long,
    val estimated: Boolean = false,
)

/** The time of the boarding stop of [leg]: its live departure. */
fun boardingTime(leg: Leg): StopTime =
    StopTime(leg.departure, leg.plannedDeparture, leg.realtime, leg.departureDelayMinutes)

/** The time of the stop where [leg] is left: its live arrival. */
fun alightingTime(leg: Leg): StopTime =
    StopTime(leg.arrival, leg.plannedArrival, leg.realtime, leg.arrivalDelayMinutes, leg.arrivalEstimated)

/**
 * The time of the stop [index] of the stops [stops] of [leg] (from boarding to getting off):
 * the ride's departure and arrival at its ends, the stop's own time between; null where unknown.
 */
fun rideStopTime(leg: Leg, stops: List<Stopover>, index: Int): StopTime? = when {
    index < 0 || index > stops.lastIndex -> null
    index == 0 -> boardingTime(leg)
    index == stops.lastIndex -> alightingTime(leg)
    else -> stopTimeBetween(stops[index])
}

/**
 * The time of a stop between: its departure, else its arrival, with the delay of that same
 * time (from its timetable time where the provider sends it, else the stop's delay).
 */
fun stopTimeBetween(stop: Stopover): StopTime? {
    val departs = stop.departure != null
    val actual = stop.departure ?: stop.arrival ?: return null
    val planned = if (departs) stop.plannedDeparture else stop.plannedArrival
    val realtime = stop.delaySeconds != null
    val delay = when {
        !realtime -> 0L
        planned != null -> Duration.between(planned, actual).toMinutes()
        else -> (stop.delaySeconds ?: 0) / 60L
    }
    return StopTime(actual, planned, realtime, delay, stop.estimated)
}
