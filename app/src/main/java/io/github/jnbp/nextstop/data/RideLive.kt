package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime

/*
 * Live times of a long-distance train from HAFAS for a ride of Transitous without realtime
 * (#274). DELFI sends none for line 27 (Berlin - Dresden - Prague), while DB and VBB HAFAS know
 * how late the run is. Where RideKindCheck has found the same run at HAFAS (see matchingRun),
 * its live times stand in: first the one of its board at the station looked at, then those of
 * its whole course (the trip of HAFAS, asked for with the refresh of a detail or a followed
 * trip). Live times of Transitous itself are never overwritten. Pure logic here.
 */

/**
 * One stop of a run as HAFAS knows it live: where ([place], [name]), the times of the
 * timetable and the live ones, and from a board its [platform] and whether it is [cancelled].
 */
data class LiveStop(
    val place: Pair<Double, Double>?,
    val name: String,
    val plannedArrival: OffsetDateTime?,
    val arrival: OffsetDateTime?,
    val plannedDeparture: OffsetDateTime?,
    val departure: OffsetDateTime?,
    val platform: String? = null,
    val cancelled: Boolean = false,
)

/**
 * What HAFAS knows live of a run: its [stops] with live data, [wholeRun] where they come from
 * its course (else from one board), as of [atMillis].
 */
data class RunLive(val stops: List<LiveStop>, val wholeRun: Boolean, val atMillis: Long)

// A stop of HAFAS lies this near to the same stop of Transitous.
private const val LIVE_STOP_METERS = 1500.0

private fun differs(a: OffsetDateTime?, b: OffsetDateTime?): Boolean = a != null && b != null && !a.isEqual(b)

private fun seconds(planned: OffsetDateTime?, live: OffsetDateTime?): Int? =
    if (planned == null || live == null) null else Duration.between(planned, live).seconds.toInt()

/**
 * The stop of a HAFAS board where [run] leaves (at [place] where it names none), live; null
 * where HAFAS has no live data for it.
 */
fun liveStopOf(run: Departure, place: Pair<Double, Double>?): LiveStop? {
    if (run.delaySeconds == null && !run.cancelled && !differs(run.time, run.plannedTime)) return null
    return LiveStop(
        place = run.at ?: run.stop?.position ?: place,
        name = run.stop?.name.orEmpty(),
        plannedArrival = null,
        arrival = null,
        plannedDeparture = run.plannedTime,
        departure = run.time ?: run.plannedTime,
        platform = run.platform,
        cancelled = run.cancelled,
    )
}

/** The stops of a HAFAS course ([stopovers]) that carry live data. */
fun liveStopsOf(stopovers: List<Stopover>): List<LiveStop> = stopovers.mapNotNull { stop ->
    val live = stop.delaySeconds != null || differs(stop.arrival, stop.plannedArrival) || differs(stop.departure, stop.plannedDeparture)
    if (!live) return@mapNotNull null
    LiveStop(stop.place?.position, stop.name, stop.plannedArrival, stop.arrival, stop.plannedDeparture, stop.departure)
}

/**
 * True when [stop] is the stop at [place] (or named [name] where either has no place) whose
 * planned time is [planned], within KIND_MATCH_MINUTES; [plannedOf] picks the time of [stop] to compare.
 */
private fun sameStop(
    stop: LiveStop,
    place: Pair<Double, Double>?,
    name: String,
    planned: OffsetDateTime?,
    plannedOf: (LiveStop) -> OffsetDateTime?,
): Boolean {
    val at = plannedOf(stop) ?: return false
    if (planned == null || kotlin.math.abs(at.toEpochSecond() - planned.toEpochSecond()) > KIND_MATCH_MINUTES * 60) return false
    if (place != null && stop.place != null) return spotMeters(place, stop.place) <= LIVE_STOP_METERS
    val one = stationNameKey(name)
    val other = stationNameKey(stop.name)
    return one.isNotEmpty() && other.isNotEmpty() && (one.contains(other) || other.contains(one))
}

/**
 * [this] ride of Transitous with the live times of [run] where it has none of its own: where
 * it is boarded (with the platform and a cancellation from the board), its stops on the way and
 * where it is left. From a board alone the delay at boarding is carried on to the later stops,
 * as a delay rarely goes on the way. The timetable stays in the planned times; [Leg.realtime]
 * and [Leg.hafasLive] are set. The same leg where nothing applies.
 */
fun Leg.withRunLive(run: RunLive): Leg {
    if (walking || (realtime && !hafasLive) || run.stops.isEmpty()) return this
    val plannedDep = plannedDeparture ?: departure
    val plannedArr = plannedArrival ?: arrival
    val board = run.stops.firstOrNull { it.departure != null && sameStop(it, fromPlace?.position, from, plannedDep) { s -> s.plannedDeparture } }
    val alight = run.stops.firstOrNull { it.arrival != null && sameStop(it, toPlace?.position, to, plannedArr) { s -> s.plannedArrival } }
    val carried = if (!run.wholeRun && board != null) Duration.between(plannedDep, board.departure!!).seconds else null
    var touched = board != null || alight != null || carried != null
    val stops = stopovers.map { stop ->
        val arrivalPlan = stop.plannedArrival ?: if (realtime) null else stop.arrival
        val departurePlan = stop.plannedDeparture ?: if (realtime) null else stop.departure
        val match = run.stops.firstOrNull { live ->
            sameStop(live, stop.place?.position, stop.name, departurePlan) { it.plannedDeparture } ||
                sameStop(live, stop.place?.position, stop.name, arrivalPlan) { it.plannedArrival }
        }
        when {
            match != null -> {
                touched = true
                val newArrival = match.arrival ?: stop.arrival
                val newDeparture = match.departure ?: stop.departure
                stop.copy(
                    arrival = newArrival, departure = newDeparture,
                    plannedArrival = arrivalPlan, plannedDeparture = departurePlan,
                    delaySeconds = seconds(departurePlan, match.departure) ?: seconds(arrivalPlan, match.arrival),
                )
            }
            carried != null -> stop.copy(
                arrival = arrivalPlan?.plusSeconds(carried) ?: stop.arrival,
                departure = departurePlan?.plusSeconds(carried) ?: stop.departure,
                plannedArrival = arrivalPlan, plannedDeparture = departurePlan,
                delaySeconds = carried.toInt(),
            )
            else -> stop
        }
    }
    if (!touched) return this
    return copy(
        departure = board?.departure ?: departure,
        arrival = alight?.arrival ?: carried?.let { plannedArr.plusSeconds(it) } ?: arrival,
        plannedDeparture = plannedDep,
        plannedArrival = plannedArr,
        departurePlatform = board?.platform?.takeIf { it.isNotBlank() } ?: departurePlatform,
        cancelled = cancelled || board?.cancelled == true,
        stopovers = stops,
        realtime = true,
        hafasLive = true,
    )
}

/**
 * [this] departure of a board of Transitous with the live time of [run] at its stop, where it
 * has none of its own (see [withRunLive]). The same departure where nothing applies.
 */
fun Departure.withRunLive(run: RunLive): Departure {
    if (delaySeconds != null) return this
    val planned = plannedTime ?: time
    val match = run.stops.firstOrNull { it.departure != null && sameStop(it, at ?: stop?.position, stop?.name.orEmpty(), planned) { s -> s.plannedDeparture } }
        ?: return this
    return copy(
        time = match.departure,
        plannedTime = planned,
        delaySeconds = seconds(planned, match.departure),
        platform = match.platform?.takeIf { it.isNotBlank() } ?: platform,
        cancelled = cancelled || match.cancelled,
    )
}

/** [journey] with the live times [liveOf] knows for its rides, by their handles (see [withRunLive]); the same journey where none applies. */
fun withRunLives(journey: Journey, liveOf: (String) -> RunLive?): Journey {
    var changed = false
    val legs = journey.legs.map { leg ->
        val live = leg.tripId?.takeIf { !leg.walking }?.let(liveOf) ?: return@map leg
        leg.withRunLive(live).also { if (it !== leg) changed = true }
    }
    return if (changed) journey.copy(legs = legs) else journey
}

/** [departures] with the live times [liveOf] knows (see [withRunLive]); the same list where none applies. */
fun withDepartureLives(departures: List<Departure>, liveOf: (String) -> RunLive?): List<Departure> {
    var changed = false
    val result = departures.map { departure ->
        val live = departure.tripId?.let(liveOf) ?: return@map departure
        departure.withRunLive(live).also { if (it !== departure) changed = true }
    }
    return if (changed) result else departures
}

/**
 * [fresh] (what HAFAS knows now of a run) joined with [old]: the stops of [old] that [fresh]
 * does not have stay (the board's platform beside a course without it, a course beside a newer
 * board), and the platform and cancellation of a board stay with the same stop of a course.
 */
fun mergedLive(old: RunLive?, fresh: RunLive): RunLive {
    if (old == null) return fresh
    fun same(a: LiveStop, b: LiveStop): Boolean =
        (a.plannedDeparture != null && b.plannedDeparture != null && a.plannedDeparture.isEqual(b.plannedDeparture)) ||
            (a.plannedArrival != null && b.plannedArrival != null && a.plannedArrival.isEqual(b.plannedArrival))
    val stops = fresh.stops.map { stop ->
        val before = old.stops.firstOrNull { same(it, stop) } ?: return@map stop
        stop.copy(
            place = stop.place ?: before.place,
            platform = stop.platform ?: before.platform,
            cancelled = stop.cancelled || (!fresh.wholeRun && before.cancelled),
        )
    } + old.stops.filter { before -> fresh.stops.none { same(it, before) } }
    return RunLive(stops, fresh.wholeRun || old.wholeRun, fresh.atMillis)
}

/** [journey] with the names and live times of HAFAS known for its long-distance trains (see RideKindFixes); no request. */
fun withKnownRuns(journey: Journey, nowMillis: Long = System.currentTimeMillis()): Journey =
    withRunLives(withKindFixes(journey) { RideKindFixes.get(it, nowMillis) }) { RideKindFixes.live(it, nowMillis) }

/** [departures] with the names and live times of HAFAS known (see [withKnownRuns]); no request. */
fun withKnownBoardRuns(departures: List<Departure>, nowMillis: Long = System.currentTimeMillis()): List<Departure> =
    withDepartureLives(withDepartureKindFixes(departures) { RideKindFixes.get(it, nowMillis) }) { RideKindFixes.live(it, nowMillis) }
