package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/** How long before the arrival at the stop to get off the second reminder comes. */
const val ALIGHT_APPROACH_SECONDS = 60L

// Without the stops of a ride, the first reminder comes this long before its arrival.
private const val ALIGHT_EARLY_SECONDS = 180L

// A reminder whose time passed longer ago than this is not sent late any more.
const val ALIGHT_STALE_SECONDS = 150L

/**
 * A reminder to get off: [early] when the vehicle leaves the stop before the one to get off
 * at ("Next stop: …"), else on the approach, about a minute before the arrival. [key] stays
 * the same while live data moves the time, so a reminder is never sent twice. [estimated]
 * when its time rests on the app's own estimate (see plausible) rather than the provider's.
 */
data class AlightEvent(val key: String, val at: OffsetDateTime, val legIndex: Int, val early: Boolean, val estimated: Boolean = false)

/**
 * The reminders of [journey]: for every ride, at every change and at the end, one when the
 * vehicle leaves the stop before the one to get off at (by the live times of its stops; for
 * a ride of one stop when it sets off, without stops [ALIGHT_EARLY_SECONDS] before its
 * arrival) and one [ALIGHT_APPROACH_SECONDS] before its arrival. The first one is left out
 * where it would come within a minute of the second; a ride shorter than a minute is reminded
 * of as it sets off. Staying on board to a ride of the same
 * vehicle needs no reminder. The times are the plausible ones (see [Journey.plausible]): where
 * the provider lets a late vehicle catch up faster than it can, the estimate counts, so the
 * reminder does not come too early; the keys stay those of the provider's times. Sorted by time.
 */
fun alightEvents(journey: Journey): List<AlightEvent> {
    val events = mutableListOf<AlightEvent>()
    val legs = journey.plausible().legs
    legs.forEachIndexed { index, leg ->
        if (leg.walking) return@forEachIndexed
        val next = legs.drop(index + 1).firstOrNull { !it.walking }
        if (next != null && leg.tripId != null && leg.tripId == next.tripId) return@forEachIndexed
        val base = "${leg.tripId ?: leg.line ?: leg.product}|${leg.to}|${leg.plannedArrival ?: leg.arrival}"
        // A ride shorter than that is reminded of as it sets off.
        val approach = leg.arrival.minusSeconds(ALIGHT_APPROACH_SECONDS).let { if (it.isBefore(leg.departure)) leg.departure else it }
        val stops = leg.stopovers
        val before = if (stops.size >= 2) stops[stops.size - 2] else null
        val early = when {
            before != null -> (before.departure ?: before.arrival)?.let { if (it.isBefore(leg.departure)) leg.departure else it }
            else -> leg.arrival.minusSeconds(ALIGHT_EARLY_SECONDS).takeIf { !it.isBefore(leg.departure) }
        }
        val earlyEstimated = if (before != null) before.estimated else leg.arrivalEstimated
        if (early != null && early.isBefore(approach.minusSeconds(60))) {
            events.add(AlightEvent("early|$base", early, index, early = true, estimated = earlyEstimated))
        }
        events.add(AlightEvent("approach|$base", approach, index, early = false, estimated = leg.arrivalEstimated))
    }
    return events.sortedBy { it.at }
}

/**
 * The reminder to send at [now] of [events], none of which is in [sent]: the latest one that
 * is due and not older than [ALIGHT_STALE_SECONDS]; null when none is. [grace] lets one
 * that is due within that many seconds count already.
 */
fun dueAlight(events: List<AlightEvent>, sent: Set<String>, now: OffsetDateTime, grace: Long = 15): AlightEvent? =
    events.filter { it.key !in sent && !it.at.isAfter(now.plusSeconds(grace)) && it.at.isAfter(now.minusSeconds(ALIGHT_STALE_SECONDS)) }
        .maxByOrNull { it.at }

/** The next reminder of [events] after [now] that is not in [sent]; null when none is left. */
fun nextAlight(events: List<AlightEvent>, sent: Set<String>, now: OffsetDateTime): AlightEvent? =
    events.filter { it.key !in sent && it.at.isAfter(now) }.minByOrNull { it.at }
