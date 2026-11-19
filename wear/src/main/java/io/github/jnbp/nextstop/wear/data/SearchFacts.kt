package io.github.jnbp.nextstop.wear.data

/**
 * [fresh] (the journey loaded again by its handle, for its live times) with what the phone sent
 * with [known] that a reload by the watch does not bring: where to board each ride
 * ([Leg.boardHint], [Leg.boardPart], [Leg.boardEstimated]), the colour of its line ([Leg.color]),
 * its names as the phone shows them ([Leg.fromShown], while it runs between the same stops, and
 * the destination's), the phone's minutes of the walk of a change ([Leg.changeWalkMinutes]),
 * and the phone's rating of a change ([Leg.changeRating]) as long as the change keeps its minutes
 * (live times that change them make the watch rate it anew). Rides are matched in order by line and boarding stop (else by
 * line at the same place in the order); a ride without a match keeps what it has.
 */
fun withSearchFacts(fresh: Journey, known: Journey): Journey {
    val knownRides = known.legs.withIndex().filter { !it.value.walking }
    val shownEnd = known.legs.last().toShown?.takeIf { fresh.legs.last().to == known.legs.last().to && fresh.legs.last().toShown == null }
    val named = if (shownEnd == null) fresh else fresh.copy(legs = fresh.legs.dropLast(1) + fresh.legs.last().copy(toShown = shownEnd))
    val ridesKnown = knownRides.any {
        val ride = it.value
        ride.boardHint != null || ride.changeRating != null || ride.color != null ||
            ride.fromShown != null || ride.toShown != null || ride.directionShown != null
    }
    if (!ridesKnown && known.legs.none { it.changeWalkMinutes != null }) return named
    var from = 0
    var changed = false
    val legs = named.legs.mapIndexed { index, leg ->
        if (leg.walking) {
            // The phone's minutes of a walk of a change, while it leads between the same places.
            val minutes = leg.changeWalkMinutes ?: known.legs.firstOrNull { it.walking && it.from == leg.from && it.to == leg.to }?.changeWalkMinutes
            if (minutes == leg.changeWalkMinutes) return@mapIndexed leg
            changed = true
            return@mapIndexed leg.copy(changeWalkMinutes = minutes)
        }
        // The same line from the same stop; else (a name written otherwise) the ride at the same
        // place in the order with the same line.
        val position = fresh.legs.subList(0, index).count { !it.walking }
        val match = (from until knownRides.size).firstOrNull { knownRides[it].value.line == leg.line && knownRides[it].value.from == leg.from }
            ?: position.takeIf { it >= from && it < knownRides.size && knownRides[it].value.line == leg.line }
            ?: return@mapIndexed leg
        from = match + 1
        val old = knownRides[match].value
        val sameGap = gapBefore(fresh.legs, index) == gapBefore(known.legs, knownRides[match].index)
        changed = true
        leg.copy(
            boardHint = leg.boardHint ?: old.boardHint,
            boardPart = if (leg.boardPart >= 0) leg.boardPart else old.boardPart,
            boardEstimated = if (leg.boardPart >= 0) leg.boardEstimated else old.boardEstimated,
            changeRating = leg.changeRating ?: old.changeRating?.takeIf { sameGap },
            color = leg.color ?: old.color,
            // The phone's names, while the ride runs between the same stops.
            fromShown = leg.fromShown ?: old.fromShown?.takeIf { leg.from == old.from },
            toShown = leg.toShown ?: old.toShown?.takeIf { leg.to == old.to },
            directionShown = leg.directionShown ?: old.directionShown?.takeIf { leg.direction == old.direction },
        )
    }
    return if (changed) named.copy(legs = legs) else named
}

/** The milliseconds from the arrival of the ride before the leg at [index] to its departure; null for the first ride. */
private fun gapBefore(legs: List<Leg>, index: Int): Long? {
    val before = legs.subList(0, index).lastOrNull { !it.walking } ?: return null
    return legs[index].departure - before.arrival
}
