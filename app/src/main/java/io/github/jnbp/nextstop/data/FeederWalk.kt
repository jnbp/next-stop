package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlin.math.roundToInt

/*
 * Walk instead of a feeder. A journey that starts with a short bus or tram ride to the station
 * where its train leaves can often start with a walk to that station instead: one change fewer
 * for a few minutes more on foot. The providers rarely return that variant (they take the
 * feeder when it leaves later), and nothing of the app drops it when they do (withoutDominated
 * only weighs journeys on the same lines; a walk has one ride fewer). So it is made here: the
 * walk from the start to the station of the train, then the same trains (see walkInsteadOfFeeder).
 */

// The longest walk offered in place of a feeder, in minutes.
const val FEEDER_WALK_MAX_MINUTES = 15L

// The longest feeder ride that a walk can stand in for, in minutes.
private const val FEEDER_RIDE_MAX_MINUTES = 15L

// Minutes at the station before the train leaves, to find the platform.
private const val FEEDER_WALK_MARGIN_MINUTES = 2L

// How much earlier than the journey itself the walk may set off, in minutes.
private const val FEEDER_WALK_EARLIER_MAX_MINUTES = 15L

// How much longer the way on the streets is than the straight line.
private const val FEEDER_WALK_DETOUR = 1.25

private val FEEDER_PRODUCTS = setOf("bus", "tram")
private val RAIL_PRODUCTS = setOf("suburban", "subway", "regional", "express")

/**
 * The variant of [journey] that walks from its start to the station of its first train instead
 * of taking the feeder: where the journey starts (after a walk, if any) with a bus or tram ride
 * of at most FEEDER_RIDE_MAX_MINUTES, then (after a change walk, if any) an S-Bahn, U-Bahn or
 * train, and the walk from the start to the boarding stop of that train takes at most
 * [maxMinutes] (by [walkMinutes], the straight line with a detour at the app's pace by default).
 * The walk ends FEEDER_WALK_MARGIN_MINUTES before the train leaves; the rest is the same legs.
 * It must set off no more than FEEDER_WALK_EARLIER_MAX_MINUTES before the journey itself and,
 * given [now], not before it. Null where it does not apply. The variant has no handle for live
 * data of its own, and its walk carries no live time.
 */
fun walkInsteadOfFeeder(
    journey: Journey,
    now: OffsetDateTime? = null,
    maxMinutes: Long = FEEDER_WALK_MAX_MINUTES,
    walkMinutes: (Double) -> Long = { meters -> walkMinutesFor(meters * FEEDER_WALK_DETOUR) },
): Journey? {
    if (journey.split != null || journey.via != null || journey.cancelled) return null
    val legs = journey.legs
    val feederIndex = legs.indexOfFirst { !it.walking }
    if (feederIndex < 0) return null
    val feeder = legs[feederIndex]
    if (feeder.product !in FEEDER_PRODUCTS) return null
    if (java.time.Duration.between(feeder.departure, feeder.arrival).toMinutes() > FEEDER_RIDE_MAX_MINUTES) return null
    val trainIndex = (feederIndex + 1 until legs.size).firstOrNull { !legs[it].walking } ?: return null
    val train = legs[trainIndex]
    if (train.product !in RAIL_PRODUCTS || train.cancelled) return null
    val startLeg = legs.first()
    val startPlace = startLeg.fromPlace
    val start = startPlace?.position ?: return null
    val station = train.fromPlace?.position ?: return null
    val meters = groundDistance(start, station)
    val minutes = walkMinutes(meters)
    if (minutes > maxMinutes) return null
    val arrival = train.departure.minusMinutes(FEEDER_WALK_MARGIN_MINUTES)
    val plannedArrival = train.plannedDeparture?.minusMinutes(FEEDER_WALK_MARGIN_MINUTES)
    val leaves = arrival.minusMinutes(minutes)
    // Not much earlier than the journey with the feeder, and not in the past.
    if (leaves.isBefore(journey.departure.minusMinutes(FEEDER_WALK_EARLIER_MAX_MINUTES))) return null
    if (now != null && leaves.isBefore(now)) return null
    val walk = Leg(
        line = null,
        product = "",
        walking = true,
        from = startLeg.from,
        to = train.from,
        departure = leaves,
        plannedDeparture = plannedArrival?.minusMinutes(minutes),
        arrival = arrival,
        plannedArrival = plannedArrival,
        departurePlatform = null,
        arrivalPlatform = train.departurePlatform,
        direction = null,
        cancelled = false,
        realtime = false,
        path = listOf(start, station),
        fromPlace = startPlace,
        toPlace = train.fromPlace,
        walkMeters = (meters * FEEDER_WALK_DETOUR).roundToInt(),
    )
    return Journey(
        legs = listOf(walk) + legs.subList(trainIndex, legs.size),
        via = null,
        foundLevel = journey.foundLevel,
    )
}

/**
 * [journeys] and, for each that starts with a short feeder (see [walkInsteadOfFeeder]), its
 * variant on foot, unless the list has that very journey already (by [journeyKey]); with
 * [now], none whose walk would have to start before it.
 */
fun withFeederWalks(journeys: List<Journey>, now: OffsetDateTime? = null): List<Journey> {
    val keys = journeys.mapTo(HashSet()) { journeyKey(it) }
    val walks = journeys.mapNotNull { journey ->
        walkInsteadOfFeeder(journey, now)?.takeIf { keys.add(journeyKey(it)) }
    }
    return if (walks.isEmpty()) journeys else journeys + walks
}
