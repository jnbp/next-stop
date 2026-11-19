package io.github.jnbp.nextstop.data

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * One state of the followed ride, from the position of the device and the live times together.
 * The position says WHERE the traveller is (on which ride, how far along its way, at which
 * stop); the live times say how fast the rest goes. Every part that shows the trip (the next
 * step, the timeline, the change card, the notification, the watch, the reminders and the
 * wake-up alarm) reads the same state, so they can no longer contradict each other: a ride
 * the traveller has left is over even when its live arrival is still ahead, a ride is only
 * ridden once the position moves with it, never because its departure time passed.
 */

/** Where the traveller stands on a followed trip. */
enum class RideStage {
    /** Before the first ride, not yet at its stop. */
    Before,

    /** On the vehicle of the ride [RideState.legIndex]. */
    OnBoard,

    /** At (or right before) the stop where the ride [RideState.legIndex] is left: get off now. */
    Alighting,

    /** Got off and on foot: to the ride [RideState.legIndex], or to the destination (-1). */
    Walking,

    /** At the stop of the ride [RideState.legIndex], which has not been seen leaving with the traveller. */
    Waiting,

    /** At the destination. */
    Arrived,
}

/**
 * The state of the followed ride of the journey [journeyId] (see [rideState]). [legIndex] is
 * an index of its legs (-1 for none): the ride on board or being left, or the ride walked to
 * or waited for. Legs before [doneBefore] are over. [time] is the time of what comes next:
 * the arrival on board (blended, see [blendedArrival]), now while getting off, the expected
 * departure while walking or waiting. [byPosition] is false where the times alone decide (no
 * fresh position, or one near none of the rides): then everything is as it was without the
 * position. [standing]: on board and the vehicle stands, not just for a stop at a station
 * (see [reportsStanding]): the live times hold; [standingSinceMillis] is when it came to stand.
 * [overdue]: waiting for (or walking to) a ride past its departure time without the vehicle
 * having left with the traveller; [missed]: that ride has probably gone without the traveller
 * (see [rideMissed]): the step asks whether to look for the next way. [realtimeDiscarded]: live times that contradict the position were set aside.
 * [estimated]: [time] is the app's estimate, not the provider's.
 */
data class RideState(
    val journeyId: String,
    val stage: RideStage,
    val legIndex: Int,
    val doneBefore: Int,
    val time: OffsetDateTime?,
    val byPosition: Boolean,
    val standing: Boolean = false,
    val overdue: Boolean = false,
    val realtimeDiscarded: Boolean = false,
    val estimated: Boolean = false,
    val standingSinceMillis: Long? = null,
    val missed: Boolean = false,
)

// Under way, the speed for the rest of the way is never taken below this (m/s).
private const val MIN_MOVING_SPEED = 2.0

// Without any better speed, a ride is taken to cover its way this fast (m/s).
private const val FALLBACK_SPEED = 8.0

// The share of the GPS speed in the blend; the rest is the speed the live times imply.
private const val GPS_WEIGHT = 0.5

// Live times contradict the position when the speed they imply for the rest of the way lies
// beyond this factor of the speeds that are plausible (the ride's own, the GPS speed).
private const val CONTRADICT_FACTOR = 2.5

// The GPS speed is smoothed with this time constant (s): one odd fix does not swing the time.
private const val SPEED_SMOOTH_SECONDS = 30.0

// The shown arrival moves toward a new estimate with this time constant (s): it never jumps.
private const val ETA_EASE_SECONDS = 20.0

// Closer than this (s) the shown arrival takes the new estimate as it is.
private const val ETA_SNAP_SECONDS = 10L

// The vehicle is said to stand ("steht") only once it has stood this long (s): never for a
// moment at a light or a stop.
const val STANDING_SHOWN_SECONDS = 60L

// Within this distance (m, along the way) of a stop of the ride a stand is a stop at a
// station, which is only said to stand once it lasts longer than a normal dwell ...
const val STOP_DWELL_METERS = 150.0

// ... that is, this long (s).
const val STOP_DWELL_SECONDS = 90L

// A ride waited for past its departure is expected this long from now (s): "kommt gleich".
const val OVERDUE_HOLD_SECONDS = 30L

// Without live times, a ride waited for (or walked to) is taken as missed this long after its
// planned departure at the least (s); for a line that runs less often, twice its headway.
const val MISSED_AFTER_SECONDS = 300L

// With live times, a ride is taken as missed this long after the departure they give (s):
// a vehicle that had not left would have had its time moved on by a refresh in between.
const val MISSED_LIVE_SECONDS = 90L

/**
 * True when the ride [leg] (plausible times), waited for or walked to and not seen leaving with
 * the traveller, has probably gone without them at [now]. With live times ([Leg.realtime]): they
 * follow the vehicle, so once they say it left ([MISSED_LIVE_SECONDS] ago), it is gone. Without:
 * [MISSED_AFTER_SECONDS] after its planned departure, or twice [headwaySeconds] (the time between
 * two vehicles of the line, where known) when that is longer: a late vehicle without live times
 * is still worth a wait, never longer than the next one would take. Only ever a question to the
 * traveller: nothing is planned anew without their tap.
 */
fun rideMissed(leg: Leg, now: OffsetDateTime, headwaySeconds: Long? = null): Boolean {
    if (leg.walking || leg.cancelled) return false
    if (leg.realtime) return Duration.between(leg.departure, now).seconds >= MISSED_LIVE_SECONDS
    val wait = max(MISSED_AFTER_SECONDS, 2 * (headwaySeconds ?: 0L))
    return Duration.between(leg.plannedDeparture ?: leg.departure, now).seconds >= wait
}

/** [previous] smoothed toward the new GPS speed [sample] over [seconds]; [previous] when the fix has none. */
fun smoothSpeed(previous: Double?, sample: Float?, seconds: Double): Double? {
    if (sample == null) return previous
    if (previous == null) return sample.toDouble()
    val share = 1.0 - exp(-max(seconds, 0.0) / SPEED_SMOOTH_SECONDS)
    return previous + (sample - previous) * share
}

/** The ride's own average speed by its timetable (m/s); null without a way or a duration. */
fun rideSpeed(leg: Leg): Double? {
    val meters = wayMeters(rideWay(leg))
    val seconds = Duration.between(leg.plannedDeparture ?: leg.departure, leg.plannedArrival ?: leg.arrival).seconds
    if (meters <= 0.0 || seconds <= 0) return null
    return meters / seconds
}

/** An arrival estimated from position and live times (see [blendedArrival]). */
data class EtaEstimate(
    val arrival: OffsetDateTime,
    val standing: Boolean,
    val realtimeUsed: Boolean,
    val realtimeDiscarded: Boolean,
)

/**
 * When the vehicle reaches the end of the ride: [metersToEnd] along its way (from the position:
 * where the traveller is), at a speed blended from the smoothed GPS [speed] and the speed the
 * live [realtimeArrival] implies for that way. Rules (Joel, 17.11.):
 * - The vehicle stands ([speed] below [STANDING_SPEED]): no extrapolation at 0 km/h, the live
 *   time holds (it has the jam priced in) and the state says "steht". Only where the live time
 *   is already past, or implies an impossible speed, the ride's own speed [rideSpeed] stands in.
 * - Under way: a live time implying a speed far off the plausible ones ([CONTRADICT_FACTOR])
 *   contradicts the position (the traveller is further than it says, or not as far) and is
 *   set aside; the position wins, with the ride's own speed in its place.
 * - Otherwise half GPS, half live time.
 */
fun blendedArrival(
    metersToEnd: Double,
    speed: Double?,
    realtimeArrival: OffsetDateTime,
    rideSpeed: Double?,
    now: OffsetDateTime,
): EtaEstimate {
    val remaining = Duration.between(now, realtimeArrival).seconds.toDouble()
    val typical = rideSpeed?.takeIf { it > 0.5 } ?: FALLBACK_SPEED
    val implied = if (remaining > 0) metersToEnd / remaining else null
    if (speed != null && speed < STANDING_SPEED) {
        val holds = implied != null && implied <= typical * CONTRADICT_FACTOR
        return if (holds) {
            EtaEstimate(realtimeArrival, standing = true, realtimeUsed = true, realtimeDiscarded = false)
        } else {
            val seconds = metersToEnd / typical
            EtaEstimate(cappedArrival(now.plusSeconds(seconds.roundToLong()), realtimeArrival, now), standing = true, realtimeUsed = false, realtimeDiscarded = implied != null)
        }
    }
    val gps = speed?.coerceAtLeast(MIN_MOVING_SPEED)
    val low = minOf(typical, gps ?: typical) / CONTRADICT_FACTOR
    val high = maxOf(typical, gps ?: typical) * CONTRADICT_FACTOR
    val trusted = implied != null && implied in low..high
    val reference = if (trusted) implied!! else typical
    val blended = if (gps == null) reference else GPS_WEIGHT * gps + (1 - GPS_WEIGHT) * reference
    val seconds = metersToEnd / max(blended, MIN_MOVING_SPEED)
    return EtaEstimate(
        cappedArrival(now.plusSeconds(seconds.roundToLong()), realtimeArrival, now),
        standing = false,
        realtimeUsed = trusted,
        realtimeDiscarded = !trusted,
    )
}

// The estimate from the position is never later than the live arrival by more than the time
// left until it (twice the remaining time), at least this much (s) ...
private const val ETA_LATE_FLOOR_SECONDS = 600L

// ... and at most this much (s): the GPS speed of a moment says little about hours of way (#88).
private const val ETA_LATE_CAP_SECONDS = 3_600L

/**
 * [estimate] no later than the live [realtimeArrival] plus the time left until it, at least
 * [ETA_LATE_FLOOR_SECONDS], at most [ETA_LATE_CAP_SECONDS]. Earlier stays: a position ahead of
 * the live times wins (a vehicle further along than its data).
 */
internal fun cappedArrival(estimate: OffsetDateTime, realtimeArrival: OffsetDateTime, now: OffsetDateTime): OffsetDateTime {
    val remaining = Duration.between(now, realtimeArrival).seconds
    val slack = remaining.coerceIn(ETA_LATE_FLOOR_SECONDS, ETA_LATE_CAP_SECONDS)
    val latest = realtimeArrival.plusSeconds(slack)
    return if (estimate.isAfter(latest)) latest else estimate
}

/**
 * The shown arrival [shown] moved toward the new estimate [target] over [seconds]: softly, so
 * the minutes never jump; within [ETA_SNAP_SECONDS] it takes [target]. Null [shown]: [target].
 */
fun easeArrival(shown: OffsetDateTime?, target: OffsetDateTime, seconds: Double): OffsetDateTime {
    if (shown == null) return target
    val diff = Duration.between(shown, target).seconds
    if (abs(diff) <= ETA_SNAP_SECONDS) return target
    val share = 1.0 - exp(-max(seconds, 0.0) / ETA_EASE_SECONDS)
    return shown.plusSeconds((diff * share).roundToLong())
}

/**
 * The arrival of the ride [legIndex] of the journey [journeyId] on board, as the position and
 * the live times give it over time ([RideTracker]): [arrival] eased, [speed] the smoothed GPS
 * speed, [atMillis] the time of the fix it was made from.
 */
data class RideEta(
    val journeyId: String,
    val legIndex: Int,
    val arrival: OffsetDateTime,
    val speed: Double?,
    val standing: Boolean,
    val realtimeDiscarded: Boolean,
    val atMillis: Long,
    /** Since when (time of a fix) the vehicle is below [STANDING_SPEED]; null while it moves. */
    val stillSinceMillis: Long? = null,
)

/**
 * The ride state over time, for every place that shows the trip: the eased arrival on board
 * ([eta]) and the run of the vehicle the traveller is on ([onBoardTripId], for the map, where
 * the badge of that vehicle goes with the traveller's own dot). Fed by each position of the
 * companion (ui/CompanionLocation.kt), cleared with it. Nothing of it is stored or logged.
 */
object RideTracker {
    /** The latest arrival on board; null while not on board by position. */
    val eta = MutableStateFlow<RideEta?>(null)

    /** The run ([Leg.tripId]) of the vehicle the traveller is on by position; null for none. Read every frame by the map. */
    @Volatile
    var onBoardTripId: String? = null
        private set

    /** Takes the fix [fix] of [journey] at [now]: the arrival on board, smoothed and eased. */
    fun take(journey: Journey, fix: PositionFix, now: OffsetDateTime): RideEta? {
        val onBoard = fix.kind == FixKind.OnRide || fix.kind == FixKind.OnBoardEarly
        val leg = journey.plausible().legs.getOrNull(fix.legIndex)
        onBoardTripId = if ((onBoard || fix.kind == FixKind.AtEnd) && leg != null && !leg.walking) leg.tripId else null
        if (!onBoard || leg == null || leg.walking) {
            eta.value = null
            return null
        }
        val before = eta.value?.takeIf { it.journeyId == fix.journeyId && it.legIndex == fix.legIndex }
        val made = nextRideEta(before, leg, fix, now)
        eta.value = made
        return made
    }

    /** The arrival on board for [journey] at [nowMillis], when it belongs to it and is fresh. */
    fun etaFor(journey: Journey, nowMillis: Long): RideEta? {
        val current = eta.value ?: return null
        if (current.journeyId != journey.id) return null
        if (abs(nowMillis - current.atMillis) > FIX_MAX_AGE_MILLIS) return null
        return current
    }

    fun clear() {
        eta.value = null
        onBoardTripId = null
    }
}

// Farther than this (m) from the end of the ride, the speed of the moment says nothing about the
// arrival: only how far the vehicle lags behind its live times does (see longRideArrival).
const val SPEED_HORIZON_METERS = 10_000.0

// A lag behind the live times counts fully near the end and fades with the way left over this
// many metres (e^-1 at this distance): a long way gives the train time to make some of it up,
// and the provider's next prognosis will have it anyway.
private const val LAG_FADE_METERS = 300_000.0

// Within this distance (m, along the way) of a stop the vehicle is at that stop.
private const val AT_STOP_ALONG_METERS = 300.0

/**
 * The arrival of the ride [leg] on board far from its end ([PositionFix.metersToEnd] beyond
 * [SPEED_HORIZON_METERS]), else null (the blend of speeds takes over, see [blendedArrival]). The
 * position only tells how far the vehicle lags behind (or runs ahead of) its live times: the
 * time those times expect it where it is, interpolated between the stops around it by the
 * distance along the way, against [now]. That lag, fading with the way left ([LAG_FADE_METERS]),
 * moves the live arrival. Never the speed of the moment over hundreds of kilometres (#88: near
 * Huenfeld at 15 m/s in the curves, "um 16:33" for a train due in Berlin 15:17). Without stops
 * to compare with, the live arrival holds.
 */
fun longRideArrival(leg: Leg, fix: PositionFix, speed: Double?, now: OffsetDateTime): EtaEstimate? {
    if (fix.kind != FixKind.OnRide || fix.metersToEnd <= SPEED_HORIZON_METERS) return null
    val standing = speed != null && speed < STANDING_SPEED
    val expected = expectedTimeAlong(leg, fix.alongMeters, now)
        ?: return EtaEstimate(leg.arrival, standing = standing, realtimeUsed = true, realtimeDiscarded = false)
    val lag = Duration.between(expected, now).seconds
    val weight = exp(-fix.metersToEnd / LAG_FADE_METERS)
    val arrival = leg.arrival.plusSeconds((lag * weight).roundToLong())
    return EtaEstimate(arrival, standing = standing, realtimeUsed = true, realtimeDiscarded = false)
}

/**
 * When the live times of [leg] expect its vehicle [along] metres along its way: between the
 * departure of the stop before and the arrival of the stop after, in proportion to the way;
 * at a stop ([AT_STOP_ALONG_METERS]) [now] within its stay, else the nearer end of it. Null
 * where fewer than two stops have positions and times.
 */
internal fun expectedTimeAlong(leg: Leg, along: Double, now: OffsetDateTime): OffsetDateTime? {
    val way = rideWay(leg)
    if (way.size < 2) return null
    class Point(val along: Double, val arrival: OffsetDateTime, val departure: OffsetDateTime)
    val points = leg.stopovers.mapNotNull { stop ->
        val spot = stop.place?.position ?: return@mapNotNull null
        val arrival = stop.arrival ?: stop.departure ?: return@mapNotNull null
        val departure = stop.departure ?: arrival
        Point(alongPath(way, spot).first, arrival, departure)
    }.sortedBy { it.along }
    if (points.size < 2) return null
    points.firstOrNull { abs(it.along - along) <= AT_STOP_ALONG_METERS }?.let { stop ->
        return when {
            now.isBefore(stop.arrival) -> stop.arrival
            now.isAfter(stop.departure) -> stop.departure
            else -> now
        }
    }
    val after = points.indexOfFirst { it.along > along }
    if (after <= 0) return null
    val a = points[after - 1]
    val b = points[after]
    val span = (b.along - a.along).takeIf { it > 0 } ?: return null
    val seconds = Duration.between(a.departure, b.arrival).seconds
    return a.departure.plusSeconds((seconds * (along - a.along) / span).roundToLong())
}

/**
 * The arrival of the ride [leg] for a fix of [FixKind.OnBoardEarly], else null: boarded at an
 * earlier stop, the vehicle still has to reach the planned one and leave it by its timetable, so
 * its live arrival holds. The position lies before the ride's way: what is left of it can not be
 * measured from there, nor reckoned at the speed of the vehicle coming in (#88).
 */
internal fun earlyBoardArrival(fix: PositionFix, leg: Leg): EtaEstimate? =
    if (fix.kind == FixKind.OnBoardEarly) EtaEstimate(leg.arrival, standing = false, realtimeUsed = true, realtimeDiscarded = false) else null

/**
 * The arrival of the ride [leg] on board ahead of its live times ([PositionFix.aheadOfLive],
 * #216), else null: the live times lag behind the vehicle (a bus on time said +11), so neither
 * they nor a blend with them hold. Where those times expect the vehicle at the position (see
 * [expectedTimeAlong]) against [now] is how far ahead it runs; the live arrival moves by that.
 * Without stops to compare with, the timetable's arrival. Never before [now].
 */
internal fun aheadArrival(leg: Leg, fix: PositionFix, speed: Double?, now: OffsetDateTime): EtaEstimate? {
    if (!fix.aheadOfLive || fix.kind != FixKind.OnRide) return null
    val expected = expectedTimeAlong(leg, fix.alongMeters, now)
    val arrival = if (expected != null) leg.arrival.plusSeconds(Duration.between(expected, now).seconds) else leg.plannedArrival ?: leg.arrival
    val standing = speed != null && speed < STANDING_SPEED
    return EtaEstimate(if (arrival.isBefore(now)) now else arrival, standing = standing, realtimeUsed = false, realtimeDiscarded = true)
}

/**
 * The next [RideEta] of the ride [leg] (plausible times) from the fix [fix] at [now], after
 * [before] (the one of the same ride before, or null): the speed smoothed, the arrival
 * blended ([blendedArrival]) and eased ([easeArrival]), never before [now]. It stands (see
 * [reportsStanding]) only after a while below [STANDING_SPEED], counted from the fix it began.
 */
fun nextRideEta(before: RideEta?, leg: Leg, fix: PositionFix, now: OffsetDateTime): RideEta {
    val seconds = if (before == null) 0.0 else (fix.atMillis - before.atMillis) / 1000.0
    val speed = smoothSpeed(before?.speed, fix.speed, seconds)
    val estimate = earlyBoardArrival(fix, leg) ?: aheadArrival(leg, fix, speed, now) ?: longRideArrival(leg, fix, speed, now)
        ?: blendedArrival(fix.metersToEnd, speed, leg.arrival, rideSpeed(leg), now)
    val eased = easeArrival(before?.arrival, estimate.arrival, seconds).let { if (it.isBefore(now)) now else it }
    val stillSince = if (estimate.standing) before?.stillSinceMillis ?: fix.atMillis else null
    val standing = stillSince != null && reportsStanding(leg, fix, (fix.atMillis - stillSince) / 1000)
    return RideEta(fix.journeyId, fix.legIndex, eased, speed, standing, estimate.realtimeDiscarded, fix.atMillis, stillSince)
}

/**
 * True when a vehicle on the ride [leg] that has stood for [stillSeconds] at the position
 * [fix] is said to stand ("steht"): from [STANDING_SHOWN_SECONDS] on, and within
 * [STOP_DWELL_METERS] of a stop of the ride (see [nearStopOfRide]) only once the stop lasts
 * longer than a normal dwell ([STOP_DWELL_SECONDS]). A stop at every station is no news.
 */
fun reportsStanding(leg: Leg, fix: PositionFix, stillSeconds: Long): Boolean {
    if (stillSeconds < STANDING_SHOWN_SECONDS) return false
    if (stillSeconds >= STOP_DWELL_SECONDS) return true
    return !nearStopOfRide(leg, fix)
}

/**
 * True when [fix] lies within [STOP_DWELL_METERS] along the way of [leg] of one of its stops:
 * where it is boarded or left, or the stop just passed or next ahead ([PositionFix.nextStop]).
 * Also true where the stops on the way have no positions: then no stand counts as away from them.
 */
fun nearStopOfRide(leg: Leg, fix: PositionFix): Boolean {
    if (fix.alongMeters <= STOP_DWELL_METERS || fix.metersToEnd <= STOP_DWELL_METERS) return true
    val stops = leg.stopovers
    if (fix.nextStop < 0 || stops.isEmpty()) return true
    val way = rideWay(leg)
    if (way.size < 2) return true
    var known = false
    for (index in (fix.nextStop - 1)..fix.nextStop) {
        val spot = stops.getOrNull(index)?.place?.position ?: continue
        known = true
        if (abs(alongPath(way, spot).first - fix.alongMeters) <= STOP_DWELL_METERS) return true
    }
    return !known
}

/**
 * The state of the followed [journey] at [now] from the position [fix] and the arrival on
 * board [eta] (see [RideTracker]); by its times alone without a fix of it (or one of kind
 * [FixKind.Off]). The times are the plausible ones of [journey] (see [Journey.plausible]).
 */
fun rideState(journey: Journey, now: OffsetDateTime, fix: PositionFix?, eta: RideEta? = null): RideState {
    val shown = journey.plausible()
    val legs = shown.legs
    val rides = legs.indices.filter { !legs[it].walking && !legs[it].cancelled }
    val positioned = fix?.takeIf { it.journeyId == journey.id && it.kind != FixKind.Off && it.legIndex in legs.indices && !legs[it.legIndex].walking }
    if (positioned == null) return timedRideState(journey.id, legs, rides, now)
    val index = positioned.legIndex
    val leg = legs[index]
    return when (positioned.kind) {
        FixKind.OnRide, FixKind.OnBoardEarly -> {
            // On board by the times (#224): the live times of the ride, nothing to blend with.
            val own = eta?.takeIf { it.journeyId == journey.id && it.legIndex == index && !positioned.byTimes }
            val estimate = if (positioned.byTimes) {
                EtaEstimate(leg.arrival, standing = false, realtimeUsed = true, realtimeDiscarded = false)
            } else if (own == null) {
                earlyBoardArrival(positioned, leg) ?: aheadArrival(leg, positioned, positioned.speed?.toDouble(), now)
                    ?: longRideArrival(leg, positioned, positioned.speed?.toDouble(), now)
                    ?: blendedArrival(positioned.metersToEnd, positioned.speed?.toDouble(), leg.arrival, rideSpeed(leg), now)
            } else {
                null
            }
            val arrival = (own?.arrival ?: estimate!!.arrival).let { if (it.isBefore(now)) now else it }
            val off = abs(Duration.between(leg.arrival, arrival).seconds) > 60
            // Standing is only told from fixes over time (see nextRideEta); one fix alone never stands.
            val standing = own?.standing == true
            RideState(
                journey.id, RideStage.OnBoard, index, index, arrival, byPosition = true,
                standing = standing,
                realtimeDiscarded = own?.realtimeDiscarded ?: estimate!!.realtimeDiscarded,
                estimated = off || leg.arrivalEstimated,
                standingSinceMillis = if (standing) own?.stillSinceMillis else null,
            )
        }
        FixKind.AtEnd -> RideState(journey.id, RideStage.Alighting, index, index, now, byPosition = true, realtimeDiscarded = leg.arrival.isAfter(now.plusMinutes(2)))
        FixKind.Alighted -> {
            val next = rides.firstOrNull { it > index } ?: -1
            // On foot past the departure of the next ride: it has not been seen leaving with the
            // traveller, so it is still to come, as for a wait at its stop ([OVERDUE_HOLD_SECONDS]).
            val overdue = next >= 0 && !legs[next].departure.isAfter(now)
            val time = if (next < 0) null else if (overdue) now.plusSeconds(OVERDUE_HOLD_SECONDS) else legs[next].departure
            RideState(
                journey.id, RideStage.Walking, next, index + 1, time, byPosition = true,
                overdue = overdue, realtimeDiscarded = leg.arrival.isAfter(now), estimated = overdue,
                missed = overdue && rideMissed(legs[next], now),
            )
        }
        FixKind.AtBoarding -> {
            val overdue = !leg.departure.isAfter(now)
            val time = if (overdue) now.plusSeconds(OVERDUE_HOLD_SECONDS) else leg.departure
            val contradicted = legs.subList(0, index).any { !it.walking && it.arrival.isAfter(now) }
            RideState(
                journey.id, RideStage.Waiting, index, index, time, byPosition = true, overdue = overdue,
                realtimeDiscarded = contradicted, estimated = overdue, missed = overdue && rideMissed(leg, now),
            )
        }
        FixKind.Off -> timedRideState(journey.id, legs, rides, now)
    }
}

/** The state by the times alone (no position): [RideState.byPosition] false. */
private fun timedRideState(id: String, legs: List<Leg>, rides: List<Int>, now: OffsetDateTime): RideState {
    val riding = rides.firstOrNull { !now.isBefore(legs[it].departure) && now.isBefore(legs[it].arrival) }
    if (riding != null) return RideState(id, RideStage.OnBoard, riding, riding, legs[riding].arrival, byPosition = false, estimated = legs[riding].arrivalEstimated)
    val upcoming = rides.firstOrNull { legs[it].departure.isAfter(now) }
    if (upcoming != null) {
        val stage = if (upcoming == rides.first()) RideStage.Before else RideStage.Walking
        return RideState(id, stage, upcoming, upcoming, legs[upcoming].departure, byPosition = false)
    }
    val end = legs.lastOrNull()?.arrival
    return if (end != null && now.isBefore(end)) {
        RideState(id, RideStage.Walking, -1, legs.size, end, byPosition = false)
    } else {
        RideState(id, RideStage.Arrived, -1, legs.size, end, byPosition = false)
    }
}

/**
 * The journey with its times as the ride [state] at [now] has them: rides the traveller has
 * left end by now (live times that say otherwise are set aside), the ride on board arrives at
 * the blended time, the walk after a ride starts when the ride ends, a ride waited for past its
 * departure comes [OVERDUE_HOLD_SECONDS] from now. The planned times stay as they were, so the
 * journey keeps its [Journey.id], and so do the keys of reminders and alarms. Itself where
 * [state] is not by position or not of this journey. Use on plausible times (see
 * [Journey.plausible]); the legs keep their order and number.
 */
fun Journey.byRide(state: RideState, now: OffsetDateTime): Journey {
    if (!state.byPosition || state.journeyId != id) return this
    val out = legs.toMutableList()
    var changed = false
    fun put(index: Int, leg: Leg) {
        if (leg != out[index]) {
            out[index] = leg
            changed = true
        }
    }
    fun retimed(leg: Leg, departure: OffsetDateTime, arrival: OffsetDateTime, estimated: Boolean): Leg =
        if (leg.departure == departure && leg.arrival == arrival) {
            leg
        } else {
            leg.copy(
                departure = departure,
                arrival = arrival,
                // Only a planned time that is known stays: one made up from the live time
                // showed the estimate as a delay (a provider without a timetable).
                plannedDeparture = leg.plannedDeparture ?: if (leg.walking) leg.departure else null,
                plannedArrival = leg.plannedArrival ?: if (leg.walking) leg.arrival else null,
                arrivalEstimated = leg.arrivalEstimated || estimated,
            )
        }
    // Everything before the current part is over by now.
    for (k in 0 until state.doneBefore.coerceAtMost(out.size)) {
        val leg = out[k]
        if (leg.arrival.isAfter(now)) {
            val departure = if (leg.departure.isAfter(now)) now else leg.departure
            put(k, retimed(leg, departure, now, estimated = true))
        }
    }
    // The walks after [from] up to the next ride, chained from [start].
    fun chainWalks(from: Int, start: OffsetDateTime) {
        var at = start
        var k = from
        while (k < out.size && out[k].walking) {
            val leg = out[k]
            val length = Duration.between(leg.departure, leg.arrival).let { if (it.isNegative) Duration.ZERO else it }
            put(k, retimed(leg, at, at.plus(length), estimated = true))
            at = out[k].arrival
            k++
        }
    }
    // On the ride [ridden]: everything before it ended before it left (#224). A ride whose live
    // arrival (a wrong delay) lies after that departure was over before it, with the walk after
    // it; so the change to the ride the traveller is on was made, not "too late".
    fun endBefore(ridden: Int) {
        var limit = out[ridden].departure
        for (k in ridden - 1 downTo 0) {
            val leg = out[k]
            if (!leg.arrival.isAfter(limit)) break
            val departure = if (leg.walking) {
                // Its own length: the planned one, as the loop above may have cut it to nothing.
                val length = Duration.between(leg.plannedDeparture ?: leg.departure, leg.plannedArrival ?: leg.arrival)
                    .let { if (it.isNegative) Duration.ZERO else it }
                limit.minus(length)
            } else if (leg.departure.isAfter(limit)) {
                limit
            } else {
                leg.departure
            }
            put(k, retimed(leg, departure, limit, estimated = true))
            limit = out[k].departure
        }
    }
    val index = state.legIndex
    when (state.stage) {
        RideStage.OnBoard -> {
            val leg = out.getOrNull(index)
            val arrival = state.time
            if (leg != null && arrival != null) {
                val departure = if (leg.departure.isAfter(now)) now else leg.departure
                put(index, retimed(leg, departure, arrival, estimated = state.estimated))
                endBefore(index)
                chainWalks(index + 1, out[index].arrival)
            }
        }
        RideStage.Alighting -> {
            val leg = out.getOrNull(index)
            if (leg != null) {
                val departure = if (leg.departure.isAfter(now)) now else leg.departure
                if (leg.arrival.isAfter(now)) put(index, retimed(leg, departure, now, estimated = true))
                endBefore(index)
                chainWalks(index + 1, out[index].arrival)
            }
        }
        RideStage.Walking -> {
            // On foot now: the walk began no later than now.
            val first = state.doneBefore
            if (first in out.indices && out[first].walking && out[first].departure.isAfter(now)) chainWalks(first, now)
            // The next ride, past its departure without the traveller on it, is still to come.
            val next = out.getOrNull(index)
            if (next != null && !next.walking && state.overdue) {
                val departure = now.plusSeconds(OVERDUE_HOLD_SECONDS)
                if (next.departure.isBefore(departure)) {
                    val shift = Duration.between(next.departure, departure)
                    put(index, retimed(next, departure, next.arrival.plus(shift), estimated = true))
                }
            }
        }
        RideStage.Waiting -> {
            val leg = out.getOrNull(index)
            if (leg != null && state.overdue) {
                val departure = now.plusSeconds(OVERDUE_HOLD_SECONDS)
                val shift = Duration.between(leg.departure, departure)
                put(index, retimed(leg, departure, leg.arrival.plus(shift), estimated = true))
            }
        }
        RideStage.Before, RideStage.Arrived -> Unit
    }
    if (!changed) return this
    // Rides without planned times take their id along (see KeptJourneyId.loose): it rested on
    // the live times this moved.
    if (ridesPlanned(legs) || keptId?.loose == true) return copy(legs = out)
    return copy(legs = out, keptId = KeptJourneyId(id, journeyRideShape(out), loose = true))
}

// Within this long before the next departure the live times of the trip are asked for more often.
const val CLOSE_DEPARTURE_SECONDS = 300L

/** How often the live times of a followed trip are asked for in the last minutes before a departure. */
const val CLOSE_REFRESH_MILLIS = 20_000L

/**
 * How long to wait before the next refresh of the followed [journey] at [now]: [CLOSE_REFRESH_MILLIS]
 * within [CLOSE_DEPARTURE_SECONDS] before the departure of its next ride (and while one is
 * waited for past its time, up to five minutes), else [regular]. Only for the companion in the
 * foreground or the trip service: the battery rule allows nothing else.
 */
fun tripRefreshMillis(journey: Journey, now: OffsetDateTime, regular: Long): Long {
    val close = journey.plausible().legs.any { leg ->
        if (leg.walking || leg.cancelled) return@any false
        val seconds = Duration.between(now, leg.departure).seconds
        seconds in -CLOSE_DEPARTURE_SECONDS..CLOSE_DEPARTURE_SECONDS
    }
    return if (close) minOf(regular, CLOSE_REFRESH_MILLIS) else regular
}
