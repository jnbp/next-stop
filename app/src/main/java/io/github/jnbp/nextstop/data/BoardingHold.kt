package io.github.jnbp.nextstop.data

/*
 * #184: the traveller waits at the stop of the S1, which has no live position (no radar, only
 * times). Its marker moved along by the clock: at its departure time it left the stop on the
 * map, although the train had not come and the traveller was still standing there. A vehicle
 * placed only by its times must not leave the stop where the traveller waits for it before
 * there is any sign that it left: the position moving with it (the ride state turns to on
 * board, see matchPosition), live times saying it has gone (RideState.missed) or a newer
 * forecast (its times move, and with them the marker, which then stays before the stop anyway).
 */

/**
 * The vehicles that must stay at the stop where the traveller waits for them, by trip id
 * ([Leg.tripId]): the index of that stop in the whole run of the vehicle, its boarding call
 * (see [TripMatch.boardIndex]) in [stopsOf] (the stops of a trip's run, null where unknown).
 * Only by position ([RideState.byPosition]): waiting at the stop or walking to it, and not
 * taken as missed. Empty otherwise: by the times alone the times decide, as before.
 */
fun boardingHolds(journey: Journey, state: RideState, stopsOf: (String) -> List<Stopover>?): Map<String, Int> {
    if (!state.byPosition || state.journeyId != journey.id || state.missed) return emptyMap()
    if (state.stage != RideStage.Waiting && state.stage != RideStage.Walking) return emptyMap()
    val leg = journey.plausible().legs.getOrNull(state.legIndex) ?: return emptyMap()
    if (leg.walking || leg.cancelled) return emptyMap()
    val tripId = leg.tripId ?: return emptyMap()
    val stops = stopsOf(tripId) ?: return emptyMap()
    val index = TripMatch.boardIndex(stops, leg)
    return if (index < 0) emptyMap() else mapOf(tripId to index)
}

/** [progress] (in stops, see the StopClock of the map) held at [holdStop]; as it is for a negative [holdStop]. */
fun heldProgress(progress: Float, holdStop: Int): Float =
    if (holdStop < 0) progress else minOf(progress, holdStop.toFloat())
