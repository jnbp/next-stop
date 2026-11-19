package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks the regional HAFAS ([hafas]: VBB HAFAS, BVG REST) for the names of long-distance trains
 * that came from Transitous (#271, see RideKinds): its board at a station the run leaves, at
 * the run's planned time, long-distance trains only. The answer is kept by
 * [RideKindFixes] and shown in place where it arrives (see RideKindFixes.version); what is
 * shown never waits for it. Only rides and boards someone looks at are checked (a search, a
 * detail, a followed trip, a board): no lookup of its own in the background. [usable] tells
 * whether a HAFAS source is in use at all.
 */
class RideKindCheck(
    private val hafas: TransitSource,
    private val usable: () -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // HAFAS boards asked lately, by place and quarter of an hour: one request serves every run there.
    private val boards = ConcurrentHashMap<String, Pair<Long, List<Departure>>>()

    /** [journeys] with the names known; the long-distance rides not checked yet are looked up in the background. */
    fun journeys(journeys: List<Journey>): List<Journey> {
        if (journeys.isEmpty()) return journeys
        val shown = journeys.map { withKnownRuns(it) }
        if (usable()) {
            shown.flatMap { it.legs }.filter { wantsKindCheck(it) && RideKindFixes.wanted(it.tripId!!) }
                .distinctBy { it.tripId }
                .take(MAX_RIDES)
                .forEach { leg -> if (RideKindFixes.claim(leg.tripId!!)) launchRide(leg) }
        }
        return shown
    }

    /**
     * [journey] as [journeys] has it, loaded again for a detail or a followed trip: the course
     * of each of its long-distance trains found at HAFAS is asked for there too, for its live
     * times (#274, see RideLive), at most every LIVE_REFRESH_MILLIS; taken in when it arrives.
     */
    fun journey(journey: Journey): Journey {
        val shown = journeys(listOf(journey)).first()
        if (!usable()) return shown
        for (leg in shown.legs) {
            val tripId = leg.tripId ?: continue
            // Live times of Transitous itself stay.
            if (leg.walking || (leg.realtime && !leg.hafasLive)) continue
            if (!RideKindFixes.liveDue(tripId)) continue
            val hafasTripId = RideKindFixes.linkOf(tripId) ?: continue
            if (RideKindFixes.claimLive(tripId)) launchCourse(tripId, hafasTripId)
        }
        return shown
    }

    private fun launchCourse(tripId: String, hafasTripId: String) {
        scope.launch {
            try {
                val stops = try {
                    withTimeoutOrNull(LOOKUP_MILLIS) { hafas.tripStopovers(hafasTripId) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return@launch
                RideKindFixes.putLive(tripId, RunLive(liveStopsOf(stops), wholeRun = true, atMillis = System.currentTimeMillis()))
            } finally {
                RideKindFixes.releaseLive(tripId)
            }
        }
    }

    /** [vehicles] with the names known; no request. */
    fun vehicles(vehicles: List<Vehicle>): List<Vehicle> = withVehicleKindFixes(vehicles) { RideKindFixes.get(it) }

    /**
     * [departures] (a board of Transitous) with the names known; its long-distance trains not
     * checked yet are looked up with one board of HAFAS at the same station.
     */
    fun departures(departures: List<Departure>): List<Departure> {
        val shown = withKnownBoardRuns(departures)
        if (!usable()) return shown
        val open = shown.filter { it.tripId != null && longDistanceNumber(it.line) != null && RideKindFixes.wanted(it.tripId) }
        val first = open.firstOrNull() ?: return shown
        val at = first.at ?: first.stop?.position ?: return shown
        val time = first.plannedTime ?: first.time ?: return shown
        val claimed = open.filter { RideKindFixes.claim(it.tripId!!) }
        if (claimed.isEmpty()) return shown
        scope.launch {
            try {
                val runs = runsAt(at, time)
                val until = time.plusMinutes(BOARD_SPAN_MINUTES)
                for (departure in claimed) {
                    val planned = departure.plannedTime ?: departure.time ?: continue
                    // Past what the board answered: left for a later look.
                    if (planned.isAfter(until)) continue
                    settle(departure.tripId!!, departure.line, matchingRun(departure.line, planned, runs.orEmpty()), runs != null, at)
                }
            } finally {
                claimed.forEach { RideKindFixes.release(it.tripId!!) }
            }
        }
        return shown
    }

    private fun launchRide(leg: Leg) {
        val tripId = leg.tripId!!
        scope.launch {
            try {
                var answered = false
                for ((place, planned) in kindLookupPlaces(leg, ::inVbbArea)) {
                    val runs = runsAt(place, planned) ?: continue
                    answered = true
                    val run = matchingRun(leg.line, planned, runs) ?: continue
                    settle(tripId, leg.line, run, true, place)
                    return@launch
                }
                settle(tripId, leg.line, null, answered, null)
            } finally {
                RideKindFixes.release(tripId)
            }
        }
    }

    // The answer for a run: its fix, or that it was looked at. A failed request is no answer
    // and leaves the run to be asked again when it is shown again. A run found is linked to
    // its trip at HAFAS, and its live time at the board ([place]) is taken in (#274).
    private fun settle(tripId: String, ours: String?, run: Departure?, answered: Boolean, place: Pair<Double, Double>?) {
        if (run != null) {
            run.tripId?.let { RideKindFixes.link(tripId, it) }
            liveStopOf(run, place)?.let { RideKindFixes.putLive(tripId, RunLive(listOf(it), wholeRun = false, atMillis = System.currentTimeMillis())) }
        }
        val fix = run?.let { kindFixOf(ours, it) }
        when {
            fix != null -> RideKindFixes.put(tripId, fix)
            answered -> RideKindFixes.markChecked(tripId)
        }
    }

    /**
     * The long-distance trains of HAFAS leaving the station at [place] from a little before
     * [time] on; empty where HAFAS has no such station near it, null where it did not answer.
     */
    private suspend fun runsAt(place: Pair<Double, Double>, time: OffsetDateTime): List<Departure>? {
        val start = time.minusMinutes(KIND_MATCH_MINUTES + 2)
        val key = "%.4f,%.4f@%d".format(java.util.Locale.ROOT, place.first, place.second, start.toEpochSecond() / BOARD_BUCKET_SECONDS)
        val now = System.currentTimeMillis()
        boards[key]?.takeIf { now - it.first < BOARD_KEEP_MILLIS }?.let { return it.second }
        val runs = try {
            withTimeoutOrNull(LOOKUP_MILLIS) {
                val station = hafas.nearbyStops(place.first, place.second, NEARBY_RESULTS).firstOrNull { stop ->
                    (stop.distanceMeters ?: 0) <= STATION_METERS && (stop.products.isEmpty() || "express" in stop.products)
                } ?: return@withTimeoutOrNull emptyList()
                hafas.departures(station.id, BOARD_RESULTS, setOf("express"), start)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        if (boards.size > MAX_BOARDS) boards.clear()
        boards[key] = now to runs
        return runs
    }

    private companion object {
        // Rides looked up per page at most: the long-distance trains of a search are few.
        const val MAX_RIDES = 6
        const val LOOKUP_MILLIS = 8_000L
        const val NEARBY_RESULTS = 3
        // The HAFAS station of a run's stop lies this near to where Transitous has it.
        const val STATION_METERS = 400
        const val BOARD_RESULTS = 40
        // How far a board of HAFAS reaches (the VBB HAFAS answers an hour).
        const val BOARD_SPAN_MINUTES = 50L
        const val BOARD_BUCKET_SECONDS = 15L * 60
        const val BOARD_KEEP_MILLIS = 10L * 60 * 1000
        const val MAX_BOARDS = 60
    }
}
