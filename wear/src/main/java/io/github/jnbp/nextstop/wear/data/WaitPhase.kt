package io.github.jnbp.nextstop.wear.data

/*
 * #184 on the watch, as on the phone (waitPhase in TripSteps there): a ride the traveller waits
 * for at its stop is not a counter stuck at "now" while the vehicle is late without news. Due
 * within the minute or late by less than [WATCH_OVERDUE_MINUTES]: "S1 kommt jeden Moment"; from
 * then on "S1 überfällig · +2′". Only while the phone has the traveller at the stop ("waiting").
 */

/** How a ride waited for stands against its time; the twin of the phone's WaitPhase. */
enum class WatchWaitPhase { Coming, AnyMoment, Overdue }

/** A ride waited for is called overdue from this many minutes past its time (OVERDUE_SHOWN_MINUTES on the phone). */
const val WATCH_OVERDUE_MINUTES = 2L

/**
 * The [WatchWaitPhase] of [trip] at [now] (epoch ms), and for [WatchWaitPhase.Overdue] the whole
 * minutes past its time (else 0): by the phone's due time ([TripSnapshot.dueAt]) where it sent
 * one, else the step's [TripSnapshot.time]. Every step not waited for counts down ([WatchWaitPhase.Coming]).
 */
fun waitPhaseOf(trip: TripSnapshot, now: Long): Pair<WatchWaitPhase, Long> {
    if (!trip.waiting || trip.missed || trip.leave || trip.kind != StepKind.Board) return WatchWaitPhase.Coming to 0L
    val due = if (trip.dueAt > 0) trip.dueAt else trip.time
    // Whole seconds, as the phone counts them (Duration.seconds).
    val seconds = Math.floorDiv(due - now, 1000L)
    if (seconds >= 60) return WatchWaitPhase.Coming to 0L
    val late = (-seconds).coerceAtLeast(0) / 60
    return if (late >= WATCH_OVERDUE_MINUTES) WatchWaitPhase.Overdue to late else WatchWaitPhase.AnyMoment to 0L
}
