package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/*
 * #57: no two rides of one journey overlap in time. The rides of a journey are each sent with
 * times of their own, and several steps put other times in later: the live times shared between
 * journeys (shareLiveTimes), the time of a vehicle the traveller stays seated in (withSeatedTimes),
 * a rest swapped at a change (spliceJourney). Each step was right for its own ride, but nothing
 * checked the journey as a whole, and the chart draws every ride exactly where its times say:
 * a U3 that ran past the departure of the bus after it was drawn over that bus, and a late U3
 * over the U3 behind it. A ride that runs into the next one has missed it; the change says so
 * (its rating), and the chart ends the block where the next ride leaves.
 */

/** The ride after leg [index] of [legs] (walks skipped); -1 where there is none. */
private fun nextRide(legs: List<Leg>, index: Int): Int {
    for (k in index + 1 until legs.size) if (!legs[k].walking) return k
    return -1
}

/**
 * True when a ride of [legs], with the times [starts] and [ends] (minutes of the chart, one
 * per leg), ends after the next ride starts.
 */
fun ridesOverlap(legs: List<Leg>, starts: FloatArray, ends: FloatArray): Boolean {
    for (index in legs.indices) {
        if (legs[index].walking) continue
        val next = nextRide(legs, index)
        if (next >= 0 && ends[index] > starts[next]) return true
    }
    return false
}

/**
 * [ends] with every ride ending no later than the next ride starts (and never before it starts
 * itself): the invariant of the chart, whatever times the legs carry. The very same array when
 * nothing overlaps, so the common case costs no copy.
 */
fun rideEndsWithoutOverlap(legs: List<Leg>, starts: FloatArray, ends: FloatArray): FloatArray {
    if (!ridesOverlap(legs, starts, ends)) return ends
    val fixed = ends.copyOf()
    for (index in legs.indices) {
        if (legs[index].walking) continue
        val next = nextRide(legs, index)
        if (next >= 0 && fixed[index] > starts[next]) fixed[index] = maxOf(starts[index], starts[next])
    }
    return fixed
}

/**
 * [journey] with every ride joined by staying seated ([Leg.seatedParts]) arriving no later than
 * the next ride leaves: its arrival is an estimate (see staySeated), and the next ride was
 * chosen to leave after it, so an estimate that later times pushed past it is no estimate of
 * anything. The very same journey where nothing needs it.
 */
fun withSeatingInBounds(journey: Journey): Journey {
    val legs = journey.legs
    var fixed: MutableList<Leg>? = null
    for (index in legs.indices) {
        val leg = legs[index]
        if (leg.walking || leg.seatedParts.isEmpty()) continue
        val next = nextRide(legs, index)
        if (next < 0) continue
        val leaves: OffsetDateTime = legs[next].departure
        if (!leg.arrival.isAfter(leaves)) continue
        val list = fixed ?: legs.toMutableList().also { fixed = it }
        list[index] = leg.copy(arrival = if (leaves.isBefore(leg.departure)) leg.departure else leaves, arrivalEstimated = true)
    }
    return fixed?.let { journey.copy(legs = it) } ?: journey
}
