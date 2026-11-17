package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.plausible
import java.time.OffsetDateTime

/** What the traveller has to do next on a followed trip. */
enum class StepKind { Board, GetOff, WalkToEnd, Arrived }

/** The next step of a followed trip: what to do, when, with which ride and where. */
data class TripStep(
    val kind: StepKind,
    val time: OffsetDateTime,
    val leg: Leg?,
    val place: String,
    /** True when [time] is the app's estimate, as the provider's prognosis was implausible (see [plausible]). */
    val estimated: Boolean = false,
    /** For [StepKind.WalkToEnd]: the way to the destination is ridden by bike (the bike combo, see [Leg.cycling]). */
    val cycling: Boolean = false,
    /**
     * For [StepKind.Board] before a journey that starts on foot: the walk has not begun yet, so the
     * step is setting off ("Leave at 05:07") and [time] is when to leave, not the departure of [leg].
     */
    val leave: Boolean = false,
)

/**
 * When to set off for a journey whose first ride, at [firstRide] in [legs], follows a walk: the
 * start of that walk (the provider's walk-start time). Null without a walk of its own before it,
 * and for walks of no distance only (a change of platform within a station, see [inStation]).
 */
internal fun leaveTime(legs: List<Leg>, firstRide: Int): OffsetDateTime? {
    if (firstRide <= 0) return null
    val walks = legs.subList(0, firstRide)
    if (walks.any { !it.walking } || walks.all { it.inStation }) return null
    return legs.first().departure.takeIf { it.isBefore(legs[firstRide].departure) }
}

/** A walk the provider gives as 0 m: within a station, nothing to set off for. Unknown distances are real walks. */
internal val Leg.inStation: Boolean get() = walking && walkMeters == 0

/**
 * The next step of [journey] at [now]. Its times are the plausible ones (see [Journey.plausible]),
 * its [TripStep.leg] is the leg of [journey] itself, so it can be found among its legs.
 */
fun tripStep(journey: Journey, now: OffsetDateTime): TripStep {
    val shown = journey.plausible()
    val legs = shown.legs
    val rides = legs.indices.filter { !legs[it].walking }
    val riding = rides.firstOrNull { !now.isBefore(legs[it].departure) && now.isBefore(legs[it].arrival) }
    val upcoming = rides.firstOrNull { legs[it].departure.isAfter(now) }
    val end = journey.legs.last().to
    val last = legs.last()
    return when {
        // On a vehicle: the next thing to do is getting off.
        riding != null -> legs[riding].let {
            TripStep(StepKind.GetOff, it.arrival, journey.legs[riding], it.to, it.arrivalEstimated)
        }
        upcoming != null -> legs[upcoming].let {
            // Before the walk to the first ride: when to leave, not when the ride departs.
            val leave = if (upcoming == rides.first()) leaveTime(legs, upcoming) else null
            if (leave != null && now.isBefore(leave)) {
                TripStep(StepKind.Board, leave, journey.legs[upcoming], it.from, leave = true)
            } else {
                TripStep(StepKind.Board, it.departure, journey.legs[upcoming], it.from)
            }
        }
        // The last walk to the destination is still part of the trip.
        now.isBefore(shown.arrival) ->
            TripStep(StepKind.WalkToEnd, shown.arrival, null, end, last.arrivalEstimated, cycling = last.walking && last.cycling)
        else -> TripStep(StepKind.Arrived, shown.arrival, null, end, last.arrivalEstimated)
    }
}
