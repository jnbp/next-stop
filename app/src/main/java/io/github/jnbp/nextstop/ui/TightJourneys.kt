package io.github.jnbp.nextstop.ui

import java.time.Duration
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.changeWalkMinutes
import io.github.jnbp.nextstop.data.journeyKey
import io.github.jnbp.nextstop.data.searchOrder
import io.github.jnbp.nextstop.data.spliceJourney

/*
 * Tighter changes than the provider offers.
 *
 * A search of BVG HAFAS (and Transitous) never returns a change shorter than the minimum
 * change time the provider keeps for that station, whatever change time the search asks for:
 * `transferTime=0` lowers only the change time the app adds on top. The app rates changes by
 * its own station rule (see journeyChangeRatings), which allows less at many stations (one
 * minute across a platform, three to another one), so a change the app calls risky can be
 * one no search of the provider ever offers. A search that starts at the station of the
 * change has no change there and finds those departures: that is how the connection chooser
 * of the detail lists them. For the chosen level to show every journey of that level, the
 * same request is made for the changes of the journeys found, and every connecting departure
 * that leaves before the one the journey takes and gets there earlier becomes a journey of
 * its own.
 */

/**
 * One change of the journeys found where tighter connecting departures may be asked for:
 * the [stop] where the ride [arriving] is left, and every journey of the list that leaves
 * that ride there, with the number of legs it keeps ([keepLegs] per journey id).
 * [key] tells requests apart: the same ride left at the same station is asked for once.
 */
class TightRequest(
    val key: String,
    val stop: Stop,
    val arriving: Leg,
    val journeys: List<Pair<Journey, Int>>,
)

/** The key of a change for [TightRequest]: the station and the ride that arrives there. */
private fun changeRequestKey(stop: Stop, arriving: Leg): String =
    "${stop.id}@${arriving.line}:${arriving.plannedArrival ?: arriving.arrival}"

/**
 * The changes of [journeys] where tighter connecting departures are asked for, one per
 * station and arriving ride, in the order of the journeys; those with a key in [asked] are
 * left out. A journey whose change has no station to start a search from is skipped there.
 */
fun tightRequests(journeys: List<Journey>, asked: Set<String> = emptySet()): List<TightRequest> {
    val byKey = LinkedHashMap<String, Triple<Stop, Leg, MutableList<Pair<Journey, Int>>>>()
    journeys.forEach { journey ->
        journeyChanges(journey).forEach { change ->
            val key = changeRequestKey(change.stop, change.arriving)
            if (key !in asked) {
                byKey.getOrPut(key) { Triple(change.stop, change.arriving, mutableListOf()) }.third += journey to change.keepLegs
            }
        }
    }
    return byKey.map { (key, value) -> TightRequest(key, value.first, value.second, value.third) }
}

/** The first ride of [legs] from [from] on; null when there is none. */
private fun firstRide(legs: List<Leg>, from: Int = 0): Leg? = legs.drop(from).firstOrNull { !it.walking }

/**
 * The journeys made of [journey] up to the change after its first [keepLegs] legs and then one
 * of [options] (journeys from the station of that change to the destination): those that leave
 * the station before the ride [journey] takes there and arrive earlier than it. A departure that
 * cannot be caught even by the live times (it leaves before the traveller gets there and has
 * walked over) is left out. Each is found by no search of the provider and so counts as found
 * without any change time ([TransferComfort.Risky], see journeyChangeRatings).
 */
fun tighterAlternatives(journey: Journey, keepLegs: Int, options: List<Journey>): List<Journey> {
    val arriving = journey.legs.getOrNull(keepLegs - 1) ?: return emptyList()
    if (arriving.walking) return emptyList()
    val taken = firstRide(journey.legs, keepLegs) ?: return emptyList()
    val takenLeaves = taken.plannedDeparture ?: taken.departure
    return options.mapNotNull { option ->
        val ride = firstRide(option.legs) ?: return@mapNotNull null
        val leaves = ride.plannedDeparture ?: ride.departure
        if (!leaves.isBefore(takenLeaves)) return@mapNotNull null
        if (!option.arrival.isBefore(journey.arrival)) return@mapNotNull null
        val walk = option.legs.takeWhile { it.walking }.sumOf { changeWalkMinutes(it, arriving, ride) }
        if (Duration.between(arriving.arrival, ride.departure).toMinutes() - walk < 0) return@mapNotNull null
        spliceJourney(journey, keepLegs, option).copy(foundLevel = TransferComfort.Risky)
    }
}

/**
 * [journeys] with [added] put in among them in the order of a search (see [searchOrder]); a
 * journey already there (see [journeyKey]) is not added again.
 */
fun withTighterJourneys(journeys: List<Journey>, added: List<Journey>): List<Journey> {
    val known = journeys.map { journeyKey(it) }.toMutableSet()
    val fresh = added.filter { known.add(journeyKey(it)) }
    if (fresh.isEmpty()) return journeys
    return (journeys + fresh).sortedWith(searchOrder)
}
