package io.github.jnbp.nextstop.data

/*
 * A change from a line into the same line (#34). Two trams 66 ran bunched by their delays
 * (+26 and +10): the first was at Bonn Hbf at 20:21, the second left there at 20:27, and the
 * search built "66 to Bonn Hbf, same platform, change into the 66 here, 66 to Siegburg",
 * although the first one reached Siegburg at 20:45 and the second only at 20:47. Vehicles of
 * one line in one direction keep their order, so the one the traveller sits in, being at the
 * change before the other leaves, gets everywhere ahead no later: staying seated is never
 * slower. Such a change is no change: the two rides are one ride in the first vehicle (see
 * mergeThroughRuns), unless the first vehicle ends there, or another journey of the same
 * search shows that the later vehicle really gets to the next change earlier (see
 * withSeatedTimes). The choice of connections at a change leaves such rides out as well
 * (see sameLineBehind).
 */

/**
 * True when [next] is a later vehicle of the line and direction of [ride], taken at the stop
 * where [ride] is left, that staying seated in [ride] beats: both rides the same line (see
 * [rapidLineName]) of one family towards the same end, the change at one station (see
 * [sameStation]) and on one platform where both name theirs, [ride] there no later than
 * [next] leaves, and the vehicle of [ride] not
 * ending there (a short run: its end is that stop). Rides of one vehicle are through runs
 * instead (see [isThroughRun]); cancelled rides and walks never count. Without a direction
 * on both rides nothing is joined.
 */
fun staysSeatedAhead(ride: Leg, next: Leg): Boolean {
    if (ride.walking || next.walking || ride.cancelled || next.cancelled) return false
    if (ride.tripId != null && ride.tripId == next.tripId) return false
    if (!sameLineBehind(ride, next)) return false
    if (!sameStation(ride.toPlace, ride.to, next.fromPlace, next.from)) return false
    // Another platform for the same line and direction may be another branch: a real change.
    val arriving = ride.arrivalPlatform?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val leaving = next.departurePlatform?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    if (arriving != null && leaving != null && arriving != leaving) return false
    return !ride.arrival.isAfter(next.departure)
}

/**
 * True when [next] is a vehicle of the line of [ride] in the same direction (by the end it
 * goes to), and the vehicle of [ride] does not end where it is left: staying seated is a way
 * on with the same line. Times and places are not compared (see [staysSeatedAhead]).
 */
fun sameLineBehind(ride: Leg, next: Leg): Boolean {
    if (ride.walking || next.walking) return false
    val line = rapidLineName(ride.line, ride.product) ?: return false
    if (isProductWord(line) || line != rapidLineName(next.line, next.product)) return false
    if (modeFamily(ride.product) != modeFamily(next.product)) return false
    val towards = stationNameKey(ride.direction ?: return false)
    val other = stationNameKey(next.direction ?: return false)
    if (towards.isEmpty() || other.isEmpty()) return false
    if (!(towards == other || towards.contains(other) || other.contains(towards))) return false
    // The vehicle ends where the traveller would change: a short run, the change is real.
    val here = stationNameKey(ride.toPlace?.name ?: ride.to)
    return here.isEmpty() || (here != towards && !towards.endsWith(here) && !here.endsWith(towards))
}

/**
 * [ride] staying seated past the change to where [next] is left: the vehicle and its handle
 * stay those of [ride]; from the change on the times are those of [next], the vehicle behind,
 * so at the latest, and marked as estimates. [walks] are the walks at the change, which go.
 * The legs as they came are kept ([Leg.seatedParts]).
 */
internal fun staySeated(ride: Leg, walks: List<Leg>, next: Leg): Leg {
    val parts = ride.seatedParts.ifEmpty { listOf(ride) } + walks + next.seatedParts.ifEmpty { listOf(next) }
    val firstStops = ride.stopovers
    val secondStops = next.stopovers
    val onward = if (firstStops.isNotEmpty() && secondStops.isNotEmpty() &&
        sameStation(firstStops.last().place, firstStops.last().name, secondStops.first().place, secondStops.first().name)
    ) {
        secondStops.drop(1)
    } else {
        secondStops
    }
    val path = if (ride.path.isNotEmpty() && next.path.isNotEmpty() && ride.path.last() == next.path.first()) {
        ride.path + next.path.drop(1)
    } else {
        ride.path + next.path
    }
    return ride.copy(
        to = next.to,
        toPlace = next.toPlace,
        arrival = next.arrival,
        // That of the vehicle behind: the time of the vehicle itself is not known here, and a
        // planned time keeps the journey the same one (Journey.id) while live times change.
        plannedArrival = next.plannedArrival,
        arrivalPlatform = next.arrivalPlatform,
        plannedArrivalPlatform = next.plannedArrivalPlatform,
        arrivalEstimated = true,
        path = path,
        stopovers = firstStops + onward.map { it.copy(estimated = true, delaySeconds = null, plannedArrival = null, plannedDeparture = null) },
        warnings = (ride.warnings + next.warnings).distinct(),
        alightingSpot = next.alightingSpot,
        seatedParts = parts,
    )
}

/**
 * [journeys] of one search with the times of each ride joined by staying seated (see
 * [staySeated]) taken from another journey that rides the same vehicle to the same stop:
 * the same line, boarded at the same station at the same planned minute, left at the stop
 * the joined ride ends at. Its own times then replace the estimate, and the two journeys
 * become one (see journeyKey). Where that other journey shows the vehicle getting there
 * later than the later vehicle did, the change was worth it after all: the journey gets
 * its legs as they came back ([Leg.seatedParts]); one put together from parts keeps the
 * estimate instead, never a time after the next ride leaves (see withSeatingInBounds).
 */
fun withSeatedTimes(journeys: List<Journey>): List<Journey> {
    if (journeys.none { journey -> journey.legs.any { it.seatedParts.isNotEmpty() } }) return journeys
    // The rides the providers sent as they are, by line, boarding station and planned minute.
    val rides = HashMap<String, MutableList<Leg>>()
    for (journey in journeys) {
        for (leg in journey.legs) {
            if (leg.walking || leg.seatedParts.isNotEmpty() || leg.cancelled) continue
            rides.getOrPut(seatKey(leg)) { mutableListOf() } += leg
        }
    }
    return journeys.map { journey ->
        if (journey.legs.none { it.seatedParts.isNotEmpty() }) return@map journey
        var changed = false
        val legs = journey.legs.flatMap { leg ->
            if (leg.seatedParts.isEmpty()) return@flatMap listOf(leg)
            val own = rides[seatKey(leg)]?.firstOrNull { sameStation(it.toPlace, it.to, leg.toPlace, leg.to) }
                ?: return@flatMap listOf(leg)
            changed = true
            // Only a journey not put together from parts gets its legs back: the count of legs
            // of each part would not fit any more (see JourneySplit, JourneyVia).
            val whole = journey.split == null && journey.via?.afterLegs == null
            if (own.arrival.isAfter(leg.seatedParts.last().arrival)) {
                // The later vehicle is there first: the change stands, as the provider sent it.
                // A journey put together from parts keeps the joined ride with the time of the
                // vehicle behind: the later time of the vehicle itself would run past the next
                // ride, which was chosen to leave after the vehicle behind arrives (#57: a U3
                // that ended after the X11 had left, the X11 drawn inside the U3).
                if (whole) leg.seatedParts else listOf(leg)
            } else {
                listOf(
                    leg.copy(
                        arrival = own.arrival,
                        plannedArrival = own.plannedArrival,
                        arrivalPlatform = own.arrivalPlatform,
                        plannedArrivalPlatform = own.plannedArrivalPlatform,
                        arrivalEstimated = own.arrivalEstimated,
                        stopovers = own.stopovers.ifEmpty { leg.stopovers },
                        path = own.path.ifEmpty { leg.path },
                        alightingSpot = own.alightingSpot ?: leg.alightingSpot,
                    ),
                )
            }
        }
        if (changed) journey.copy(legs = legs) else journey
    }
}

// One vehicle as a search names it where it is boarded: line, station and planned minute.
private fun seatKey(ride: Leg): String =
    "${ride.line?.let { normalLine(it) }}|${stationNameKey(ride.fromPlace?.name ?: ride.from)}|" +
        "${(ride.plannedDeparture ?: ride.departure).toInstant().epochSecond / 60}"

/**
 * [fresh], a followed journey loaded again, with the legs of [known], the journey as it is
 * followed: rides joined by staying seated (see [staySeated]) stay joined, and rides that were
 * not joined stay apart, whatever the new live times say. Joining rests on live times
 * ([staysSeatedAhead]), so a refresh with new delays could join or split rides, and with that
 * change the number of legs and [Journey.id], and so the keys of reminders and wake-up alarms
 * and what is known of faster ways. The legs the providers sent ([Leg.seatedParts]) are put
 * together as [known] has them, with their fresh times. [fresh] as it is when its legs already
 * have the shape of [known], or when the rides the providers sent do not match (another
 * journey, other rides): then nothing is forced.
 */
fun withKnownSeating(fresh: Journey, known: Journey): Journey {
    // The common case: nothing joined by staying seated, or joined alike.
    if (fresh.legs.size == known.legs.size &&
        fresh.legs.indices.all { fresh.legs[it].seatedParts.size == known.legs[it].seatedParts.size }
    ) {
        return fresh
    }
    val parts = fresh.legs.flatMap { it.seatedParts.ifEmpty { listOf(it) } }
    val knownParts = known.legs.flatMap { it.seatedParts.ifEmpty { listOf(it) } }
    if (parts.size != knownParts.size) return fresh
    for (index in parts.indices) {
        val part = parts[index]
        val before = knownParts[index]
        if (part.walking != before.walking || part.line != before.line) return fresh
        if (!sameStation(part.fromPlace, part.from, before.fromPlace, before.from)) return fresh
    }
    var at = 0
    val legs = known.legs.map { shape ->
        val count = shape.seatedParts.size.coerceAtLeast(1)
        val own = parts.subList(at, at + count)
        at += count
        own.drop(1).fold(own.first()) { ride, next -> staySeated(ride, emptyList(), next) }
    }
    // The counts of legs of the parts of a journey put together from two are those of [known].
    val split = known.split?.let { shape -> fresh.split?.copy(headLegs = shape.headLegs, tailJoined = shape.tailJoined) ?: shape }
    return fresh.copy(legs = legs, split = split, via = known.via)
}
