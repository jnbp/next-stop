package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import kotlinx.coroutines.flow.MutableStateFlow

/** One position of the device, as the trip companion gets it (see ui/CompanionLocation.kt). */
data class PositionSample(
    val latitude: Double,
    val longitude: Double,
    /** Radius of 68 % confidence in metres; 0 when unknown. */
    val accuracy: Float,
    /** Speed over ground in metres per second; null when the device gives none. */
    val speed: Float?,
    val atMillis: Long,
    /** How old the position was when it came (ms): a cached one in a tunnel is old. */
    val ageMillis: Long = 0L,
) {
    val spot: Pair<Double, Double> get() = latitude to longitude
}

/** Where the traveller is on a followed journey, by position. */
enum class FixKind {
    /** On the vehicle of the ride, away from where it was boarded and short of where to get off. */
    OnRide,

    /** At the stop where the ride is boarded, standing (the vehicle has not left with them). */
    AtBoarding,

    /** At or right before the stop where the ride is left: time to get off. */
    AtEnd,

    /** Moving at the speed of a vehicle toward the stop where the ride is boarded: on board already (boarded earlier). */
    OnBoardEarly,

    /**
     * Got off the ride at its end (an earlier fix was [AtEnd]) and on foot near it, on the way
     * to the next ride or the destination: that ride is over, whatever its live times say.
     */
    Alighted,

    /** Near none of the rides that matter now: the time-based step stands. */
    Off,
}

/**
 * The position of the device matched to a followed journey: the ride [legIndex] (an index of
 * [Journey.legs]) and how it stands there ([kind]). [alongMeters] and [metersToEnd] are measured
 * along the way of that ride, [nextStop] is the index in its [Leg.stopovers] of the next stop
 * ahead (-1 where unknown). [boardMeters] is the straight distance to where the next ride is
 * boarded (for [FixKind.OnBoardEarly], which needs two samples coming closer), [speed] the
 * speed of the sample in m/s.
 */
data class PositionFix(
    val journeyId: String,
    val legIndex: Int,
    val kind: FixKind,
    val alongMeters: Double,
    val metersToEnd: Double,
    val nextStop: Int,
    val atMillis: Long,
    val speed: Float? = null,
    val boardMeters: Double? = null,
    /**
     * #216: on the way of the ride [aheadLeg] beyond its boarding stop at the speed of a vehicle
     * while its live departure is still to come (live times that are wrong): [aheadCount]
     * fixes in a row so far, the latest [aheadAlong] metres along its way. -1 / 0 for none.
     */
    val aheadLeg: Int = -1,
    val aheadAlong: Double = 0.0,
    val aheadCount: Int = 0,
    /**
     * On board ([FixKind.OnRide]) a ride whose live times lag behind the vehicle (#216): boarded
     * before its live departure. Its arrival is estimated from the position (see aheadArrival).
     */
    val aheadOfLive: Boolean = false,
    /**
     * #224: not placed by this position but by the times: the last usable fix had the traveller
     * at the stop of the ride [legIndex], which has left by its times since without a usable fix
     * saying otherwise (see [boardedByTimes]). On board ([FixKind.OnRide]) as its live times go.
     */
    val byTimes: Boolean = false,
)

/**
 * The latest position of the device on the followed trip, for every place that shows its next
 * step ([io.github.jnbp.nextstop.ui.tripStep] reads it). Only set while the trip companion runs
 * with "Standort im Begleiter nutzen" on; a fix older than [FIX_MAX_AGE_MILLIS] is not used, so
 * a position that stopped coming hands the step back to the times.
 */
object CompanionPosition {
    /** The latest fix; null without one. */
    val fix = MutableStateFlow<PositionFix?>(null)

    /**
     * The fix for [journey] at [nowMillis], when it belongs to it and is fresh. Where the last
     * usable fix had the traveller at the stop of a ride that has left by its times since, the
     * ride on board by the times ([boardedByTimes], #224); where it had them on foot to or at the
     * stop of a ride not left yet, that fix for a while ([heldOnFoot]): no position is no
     * evidence that they went elsewhere.
     */
    fun fixFor(journey: Journey, nowMillis: Long = System.currentTimeMillis()): PositionFix? {
        val current = fix.value ?: return null
        if (current.journeyId != journey.id) return null
        if (current.atMillis - nowMillis > FIX_MAX_AGE_MILLIS) return null
        boardedByTimes(journey, current, nowMillis)?.let { return it }
        // On board, a fix holds longer: a tunnel or a train standing before the station leaves
        // the ride on board for a while, not ended by the clock at once (#170).
        val onBoard = current.kind == FixKind.OnRide || current.kind == FixKind.OnBoardEarly
        val maxAge = if (onBoard) ON_BOARD_FIX_MAX_AGE_MILLIS else FIX_MAX_AGE_MILLIS
        if (nowMillis - current.atMillis > maxAge) return current.takeIf { heldOnFoot(journey, it, nowMillis) }
        return current
    }

    fun clear() {
        fix.value = null
    }
}

// A position less accurate than this (m) is no evidence of where the traveller is (#224) ...
const val POOR_ACCURACY_METERS = 50f

// ... nor one older than this (ms) when it comes: a cached one, as in a tunnel.
const val POOR_AGE_MILLIS = 60_000L

// The vehicle is taken to have left with the traveller this long (s) after its departure by its
// times, when no usable fix came since (a fix every 15 s would have said otherwise) ...
const val BOARDED_GRACE_SECONDS = 45L

// ... where the last usable fix was at its stop at most this long (min) before that departure ...
const val BOARDED_FIX_WINDOW_MINUTES = 15L

// ... or on foot from the ride before within this distance (m) of where it is boarded.
const val BOARDED_NEAR_METERS = 300.0

// On foot to (or at the stop of) a ride not left yet, the last fix holds this long (ms) at most.
const val ON_FOOT_HOLD_MILLIS = 15 * 60_000L

/**
 * True when [sample] says nothing about where the traveller is (#224): older than
 * [POOR_AGE_MILLIS], or less accurate than [POOR_ACCURACY_METERS] without the speed of a
 * vehicle (a cell or Wi-Fi fix underground). An accuracy of 0 is unknown and counts.
 */
fun noEvidence(sample: PositionSample): Boolean =
    sample.ageMillis > POOR_AGE_MILLIS ||
        (sample.accuracy > POOR_ACCURACY_METERS && (sample.speed == null || sample.speed < RIDING_SPEED))

/** The ride [last] has the traveller waiting for (or walking to, near its stop); null for none. */
private fun boardingRideOf(legs: List<Leg>, last: PositionFix): Int? = when (last.kind) {
    FixKind.AtBoarding -> last.legIndex
    FixKind.OnRide -> last.legIndex.takeIf { last.byTimes }
    FixKind.Alighted -> legs.indices.firstOrNull { it > last.legIndex && !legs[it].walking }
        ?.takeIf { (last.boardMeters ?: Double.MAX_VALUE) <= BOARDED_NEAR_METERS }
    else -> null
}?.takeIf { it in legs.indices && !legs[it].walking && !legs[it].cancelled }

/**
 * #224: the ride the traveller is on by the times, from the last usable fix [last] at [nowMillis]:
 * that fix had them at the stop where the ride is boarded (or on foot near it, from the ride
 * before) at most [BOARDED_FIX_WINDOW_MINUTES] before its departure, and its departure by its
 * (live, plausible) times passed [BOARDED_GRACE_SECONDS] ago with no usable fix since; or [last]
 * is such a ride already ([PositionFix.byTimes]). Until its arrival. Underground the position
 * stays at the platform while the live times follow the train: the times win then, until a
 * usable fix comes. Null where none applies. The fix is of kind [FixKind.OnRide] with
 * [PositionFix.byTimes], the next stop and the way by the times.
 */
fun boardedByTimes(journey: Journey, last: PositionFix, nowMillis: Long): PositionFix? {
    if (last.journeyId != journey.id) return null
    if (last.kind != FixKind.AtBoarding && last.kind != FixKind.Alighted && !(last.kind == FixKind.OnRide && last.byTimes)) return null
    val legs = journey.plausible().legs
    val index = boardingRideOf(legs, last) ?: return null
    val leg = legs[index]
    val departure = leg.departure.toInstant().toEpochMilli()
    if (nowMillis < departure + BOARDED_GRACE_SECONDS * 1000) return null
    if (nowMillis >= leg.arrival.toInstant().toEpochMilli()) return null
    if (!last.byTimes) {
        if (last.atMillis >= departure) return null
        if (last.atMillis < departure - BOARDED_FIX_WINDOW_MINUTES * 60_000) return null
    }
    // Where the times put the vehicle: the next stop ahead and the way to the end from the one before.
    val stops = leg.stopovers
    val nextStop = stops.indices.firstOrNull { it > 0 && (stops[it].arrival ?: stops[it].departure)?.toInstant()?.toEpochMilli()?.let { at -> at > nowMillis } == true }
        ?: if (stops.isEmpty()) -1 else stops.lastIndex
    val way = rideWay(leg)
    val total = wayMeters(way)
    val along = stops.getOrNull(nextStop - 1)?.place?.position?.takeIf { way.size >= 2 }?.let { alongPath(way, it).first } ?: 0.0
    return PositionFix(
        journeyId = journey.id,
        legIndex = index,
        kind = FixKind.OnRide,
        alongMeters = along,
        metersToEnd = (total - along).coerceAtLeast(0.0),
        nextStop = nextStop,
        atMillis = last.atMillis,
        byTimes = true,
    )
}

/**
 * True when the fix [last], too old to be fresh, still stands at [nowMillis]: the traveller at
 * the stop of a ride, or on foot from the ride before toward it, which has not left yet by its
 * times ([BOARDED_GRACE_SECONDS] after its departure at the latest), for [ON_FOOT_HOLD_MILLIS]
 * at most. Without a usable fix since, nothing says they went elsewhere (#224: underground, the
 * times alone took the bus of a wrong delay for the ride under way).
 */
fun heldOnFoot(journey: Journey, last: PositionFix, nowMillis: Long): Boolean {
    if (last.kind != FixKind.AtBoarding && last.kind != FixKind.Alighted) return false
    if (nowMillis - last.atMillis > ON_FOOT_HOLD_MILLIS) return false
    val legs = journey.plausible().legs
    val index = when (last.kind) {
        FixKind.AtBoarding -> last.legIndex
        else -> legs.indices.firstOrNull { it > last.legIndex && !legs[it].walking }
    } ?: return false
    val leg = legs.getOrNull(index)?.takeIf { !it.walking && !it.cancelled } ?: return false
    return nowMillis < leg.departure.toInstant().toEpochMilli() + BOARDED_GRACE_SECONDS * 1000
}

/** A fix older than this is not used any more. */
const val FIX_MAX_AGE_MILLIS = 90_000L

/** A fix on board is used this long (see CompanionPosition.fixFor). */
const val ON_BOARD_FIX_MAX_AGE_MILLIS = 180_000L

// Within this many metres of the start of a ride's way the traveller is still at its stop.
const val AT_STOP_METERS = 150.0

// Within this many metres of the end of a ride's way the traveller is at the stop to get off.
const val AT_END_METERS = 150.0

// A position counts as on a way within this distance of it at the least and at the most,
// widened by the accuracy of the position in between.
private const val NEAR_MIN_METERS = 60.0
private const val NEAR_MAX_METERS = 220.0

// Faster than this (m/s, 10.8 km/h) a traveller is not walking.
private const val RIDING_SPEED = 3.0f

// Faster than this (m/s, about 25 km/h) a traveller is in a vehicle, not on a bike.
private const val VEHICLE_SPEED = 7.0f

/** Below this (m/s) a traveller stands still. */
const val STANDING_SPEED = 1.5f

// After getting off, a position on foot within this distance of the end of the ride counts as walking on from it.
private const val ALIGHTED_MAX_METERS = 1_000.0

// An early boarding is only looked for this long before the departure of the ride.
private const val EARLY_WINDOW_MINUTES = 30L

// ... and within this distance of its boarding stop.
private const val EARLY_MAX_METERS = 15_000.0

// ... and only when the traveller gets to the boarding stop at most this long before the ride
// leaves there: a vehicle stands at a station a few minutes, not half an hour. Arriving much
// earlier, the traveller comes on another vehicle and changes there (#88: ICE 621 from the
// airport into Frankfurt Hbf 25 minutes before the ICE to Berlin).
private const val EARLY_DWELL_MINUTES = 8L

// A ride is not boarded away from its start more than this before its (live) departure.
private const val DEPART_GRACE_MINUTES = 2L

// Past its live arrival a ride still counts as ridden by position this long at most (#170) ...
const val OVERDUE_ON_BOARD_MINUTES = 30L

// ... and only more than this far (m, along the way) before its end ...
private const val OVERDUE_MIN_TO_END_METERS = 300.0

// ... and not within this distance of a stop on its way: standing at a station is ambiguous.
private const val OVERDUE_STOP_METERS = 250.0

// Moving this far (m) back along a ride's way from the fix before: going the other way.
private const val BACKWARD_METERS = 300.0

// #216: on a ride before its live departure, the traveller is on board once this many fixes in
// a row ([AHEAD_FIXES]) follow its way away from the boarding stop: beyond it by this much (m,
// along the way) or past the next stop ...
const val AHEAD_MIN_METERS = 300.0

// ... at the speed of a vehicle (m/s, 15 km/h), by the device or by the way made between fixes ...
const val AHEAD_SPEED = 4.2f

// ... each fix this much (m) further along than the one before ...
private const val AHEAD_STEP_METERS = 20.0

// ... no more than this (ms) apart.
private const val AHEAD_GAP_MILLIS = 90_000L

/** Fixes in a row that put the traveller on a ride ahead of its live departure (#216). */
const val AHEAD_FIXES = 3

// Of two ways about as near, the one of the ride the traveller was on wins within this (m).
private const val STICKY_WAY_METERS = 40.0

/**
 * #216: how many fixes in a row (this one included) follow the way of [leg] like its vehicle,
 * with [sample] [along] metres along [way] and [track] the fix before when it tracked the same
 * ride ([PositionFix.aheadLeg]); 0 for a fix beyond the stop not at the speed of a vehicle (it
 * still lets the next one measure the way made), -1 while within [AHEAD_MIN_METERS] of the
 * boarding stop and short of the next stop: standing at the stop is no evidence. A fix that
 * stands or moves little keeps the count; one far back, or long after the one before, starts
 * it again.
 */
internal fun aheadCount(leg: Leg, way: List<Pair<Double, Double>>, along: Double, sample: PositionSample, track: PositionFix?): Int {
    val past = along >= AHEAD_MIN_METERS || nextStopIndex(leg, way, along) >= 2
    if (!past) return -1
    val gap = track?.let { sample.atMillis - it.atMillis }
    val before = track?.takeIf { gap != null && gap in 1..AHEAD_GAP_MILLIS }
    val rate = before?.let { (along - it.aheadAlong) / (gap!! / 1000.0) }
    val fast = (sample.speed ?: 0f) >= AHEAD_SPEED || (rate != null && rate >= AHEAD_SPEED)
    if (before == null || along < before.aheadAlong - BACKWARD_METERS) return if (fast) 1 else 0
    val forward = along - before.aheadAlong >= AHEAD_STEP_METERS
    return if (fast && forward) before.aheadCount + 1 else before.aheadCount
}

/** True when [along] metres on [way] lie within [OVERDUE_STOP_METERS] of a stop of [leg] with a position. */
private fun nearStopOnWay(leg: Leg, way: List<Pair<Double, Double>>, along: Double): Boolean =
    leg.stopovers.any { stop ->
        val spot = stop.place?.position ?: return@any false
        kotlin.math.abs(alongPath(way, spot).first - along) <= OVERDUE_STOP_METERS
    }

/** The length of [path] in metres. */
fun wayMeters(path: List<Pair<Double, Double>>): Double {
    var sum = 0.0
    for (i in 1 until path.size) sum += PathJoin.meters(path[i - 1], path[i])
    return sum
}

/** How far along [path] (metres from its start) the point nearest to [spot] lies, and how far off the way [spot] is. */
fun alongPath(path: List<Pair<Double, Double>>, spot: Pair<Double, Double>): Pair<Double, Double> {
    val hit = PathJoin.nearest(path, spot)
    var along = 0.0
    for (i in 1..hit.index) along += PathJoin.meters(path[i - 1], path[i])
    if (hit.index < path.lastIndex) along += PathJoin.meters(path[hit.index], hit.point)
    return along to hit.meters
}

/** The way of [leg] to match a position to: its path, else the line through the stops it calls at. */
fun rideWay(leg: Leg): List<Pair<Double, Double>> {
    if (leg.path.size >= 2) return leg.path
    val stops = leg.stopovers.mapNotNull { it.place?.position }
    if (stops.size >= 2) return stops
    return listOfNotNull(leg.fromPlace?.position, leg.toPlace?.position).takeIf { it.size == 2 }.orEmpty()
}

/**
 * The index in [leg]'s stops of the next stop ahead of [along] metres on [way]; -1 where its
 * stops have no positions. A stop counts as passed once the position is 30 m beyond it.
 */
fun nextStopIndex(leg: Leg, way: List<Pair<Double, Double>>, along: Double): Int {
    val stops = leg.stopovers
    if (stops.isEmpty() || way.size < 2) return -1
    var known = false
    for (index in stops.indices) {
        val spot = stops[index].place?.position ?: continue
        known = true
        if (alongPath(way, spot).first > along + 30.0) return index
    }
    return if (known) stops.lastIndex else -1
}

/**
 * Matches [sample] to [journey] at [now]: which ride the traveller is on, at the stop of, or
 * near the end of. Only the rides that matter at this time are looked at: the one the times
 * say is under way, the next one, the one before it, and the one of [previous]. A position on
 * the way of a ride but away from its stops counts as on board only at the speed of a vehicle,
 * when the times say it is under way, or when [previous] already had the traveller on it (a
 * train standing at a station between). The times count only where the position does not
 * speak against them: not after the traveller was seen waiting at its stop (then the vehicle
 * has to be seen moving), and not after the traveller got off it. At a stop, moving on
 * board takes the speed of a vehicle or an earlier fix on board, never the time alone.
 * Waiting at the stop of a later ride wins over the end of an earlier one once the traveller
 * was at that end (or is nearer the later stop), and over a ride that only the times call
 * under way. A ride is never boarded away from its start before its live departure, unless
 * [AHEAD_FIXES] fixes in a row follow its way like its vehicle once its timetable time has come
 * (live times that lag behind, #216; see [aheadCount]): then on board ahead of the live times.
 * On foot near the end of the ride just left: [FixKind.Alighted]. Returns a fix of
 * kind [FixKind.Off] where nothing matches (it still carries the distance to the next
 * boarding stop).
 */
fun matchPosition(journey: Journey, sample: PositionSample, now: OffsetDateTime, previous: PositionFix?): PositionFix {
    val legs = journey.plausible().legs
    val rides = legs.indices.filter { !legs[it].walking && !legs[it].cancelled }
    val riding = rides.firstOrNull { !now.isBefore(legs[it].departure) && now.isBefore(legs[it].arrival) }
    val upcoming = rides.firstOrNull { legs[it].departure.isAfter(now) }
    val beforeUpcoming = upcoming?.let { u -> rides.lastOrNull { it < u } }
    val prev = previous?.takeIf { it.journeyId == journey.id }
    // A position that says nothing (old, or vague and still, as underground) changes nothing of
    // a wait or a walk to the next ride: the last usable fix stands (#224, see fixFor).
    if (prev != null && noEvidence(sample) && (prev.kind == FixKind.AtBoarding || prev.kind == FixKind.Alighted)) return prev
    // On board by the times since the last usable fix (#224): the ride is the one the traveller was on.
    val boarded = prev?.let { boardedByTimes(journey, it, sample.atMillis) }
    // The ride the traveller got off, by the fixes before: the times no longer count for it.
    val leftRide = prev?.takeIf { it.kind == FixKind.AtEnd || it.kind == FixKind.Alighted }?.legIndex
    val candidates = linkedSetOf<Int>().apply {
        riding?.let { add(it) }
        // The ride after it as well: a late ride by its live times may already lie behind the traveller.
        riding?.let { r -> rides.firstOrNull { it > r }?.let { add(it) } }
        prev?.legIndex?.takeIf { it in rides && it >= 0 }?.let { add(it) }
        boarded?.let { add(it.legIndex) }
        // After getting off: the ride after it, wherever the times are.
        leftRide?.let { left -> rides.firstOrNull { it > left }?.let { add(it) } }
        beforeUpcoming?.let { add(it) }
        upcoming?.let { add(it) }
        if (riding == null && upcoming == null) rides.lastOrNull()?.let { add(it) }
    }
    val speed = sample.speed
    val near = (sample.accuracy * 1.5).coerceIn(NEAR_MIN_METERS, NEAR_MAX_METERS)
    val moving = speed != null && speed >= RIDING_SPEED
    // Every ride the position matches, with how far off its way and whether only the times put it there.
    class Match(val fix: PositionFix, val off: Double, val byTimesOnly: Boolean, val timeSaysOn: Boolean, val wasOn: Boolean = false)
    val matches = mutableListOf<Match>()
    // A ride followed ahead of its live departure (#216), kept on the fix returned: (leg, along, count).
    var aheadSeen: Triple<Int, Double, Int>? = null
    fun PositionFix.tracked(): PositionFix =
        aheadSeen?.let { (leg, along, count) -> copy(aheadLeg = leg, aheadAlong = along, aheadCount = count) } ?: this
    for (index in candidates) {
        val leg = legs[index]
        val way = rideWay(leg)
        if (way.size < 2) continue
        val (along, off) = alongPath(way, sample.spot)
        if (off > near) continue
        val total = wayMeters(way)
        val toEnd = (total - along).coerceAtLeast(0.0)
        val onBefore = prev != null && prev.legIndex == index && (prev.kind == FixKind.OnRide || prev.kind == FixKind.OnBoardEarly)
        val wasOn = onBefore || boarded?.legIndex == index
        val waited = prev != null && prev.legIndex == index && prev.kind == FixKind.AtBoarding && boarded == null
        val left = leftRide != null && index <= leftRide
        val timeSaysOn = index == riding
        // The times alone put the traveller on board only where the position does not speak against it.
        val trustTimes = timeSaysOn && !waited && !left
        // Away from its start on a ride that has not left yet: impossible, whatever way lies
        // nearest (#170: the next ride leaves the station back along the same track) ...
        val beforeLive = along > AT_STOP_METERS && leg.departure.isAfter(now.plusMinutes(DEPART_GRACE_MINUTES))
        // ... unless the traveller is on it already, ahead of live times that lag behind ...
        val keptAhead = wasOn && prev?.aheadOfLive == true
        var boardedAhead = false
        if (beforeLive && !keptAhead) {
            // ... or follows its way like its vehicle for some fixes, its timetable time come
            // (#216: the live times said +11 for a bus on time, and kept the traveller "waiting").
            val planned = leg.plannedDeparture ?: leg.departure
            val due = !planned.isAfter(now.plusMinutes(DEPART_GRACE_MINUTES)) || leg.plannedDeparture == null
            val count = if (due) aheadCount(leg, way, along, sample, prev?.takeIf { it.aheadLeg == index }) else -1
            if (count >= 0) aheadSeen = Triple(index, along, count)
            if (count < AHEAD_FIXES) continue
            boardedAhead = true
        }
        // Moving back toward its start on the ride the fix before put the traveller on: the
        // other way along a shared track, not this ride.
        if (onBefore && moving && prev != null && along < prev.alongMeters - BACKWARD_METERS) continue
        // Past its live arrival, on its way well before its end and not at a stop on it: still on
        // the vehicle, late or standing on the track before the station (#170), for a while.
        val overdue = !left && !leg.arrival.isAfter(now) && now.isBefore(leg.arrival.plusMinutes(OVERDUE_ON_BOARD_MINUTES)) &&
            toEnd > OVERDUE_MIN_TO_END_METERS && !nearStopOnWay(leg, way, along)
        val kind = when {
            toEnd <= AT_END_METERS && along > AT_STOP_METERS -> if (left && !moving) FixKind.Alighted else FixKind.AtEnd
            along <= AT_STOP_METERS && (speed == null || speed < STANDING_SPEED) -> FixKind.AtBoarding
            along <= AT_STOP_METERS -> if (wasOn || moving) FixKind.OnRide else FixKind.AtBoarding
            moving || wasOn || trustTimes || overdue || boardedAhead -> FixKind.OnRide
            else -> null
        } ?: continue
        matches.add(
            Match(
                PositionFix(
                    journeyId = journey.id,
                    legIndex = index,
                    kind = kind,
                    alongMeters = along,
                    metersToEnd = toEnd,
                    nextStop = nextStopIndex(leg, way, along),
                    atMillis = sample.atMillis,
                    speed = speed,
                    aheadOfLive = kind == FixKind.OnRide && (boardedAhead || keptAhead),
                ),
                off,
                byTimesOnly = kind == FixKind.OnRide && !moving && !wasOn && !overdue,
                timeSaysOn = timeSaysOn,
                wasOn = wasOn,
            ),
        )
    }
    // The nearest way wins; on a tie the ride the times say is under way. The ride the traveller
    // was on keeps them within [STICKY_WAY_METERS]: two ways side by side do not flicker (#170).
    var best = matches.minByOrNull { it.off - (if (it.timeSaysOn) 1.0 else 0.0) - (if (it.wasOn) STICKY_WAY_METERS else 0.0) }
    // Standing at the stop of a later ride: waiting for it, not on (or getting off) an earlier one.
    val waiting = matches.filter { it.fix.kind == FixKind.AtBoarding }.maxByOrNull { it.fix.legIndex }
    if (best != null && waiting != null && waiting !== best && waiting.fix.legIndex > best.fix.legIndex) {
        val wasAtEnd = leftRide != null && leftRide >= best.fix.legIndex
        val prefer = when (best.fix.kind) {
            FixKind.AtEnd, FixKind.Alighted -> wasAtEnd || waiting.off < best.off
            FixKind.OnRide -> best.byTimesOnly
            else -> false
        }
        if (prefer) best = waiting
    }
    // The distance to where the next ride is boarded, for an early boarding.
    val boardSpot = upcoming?.let { u -> legs[u].fromPlace?.position ?: rideWay(legs[u]).firstOrNull() }
    val boardMeters = boardSpot?.let { PathJoin.meters(it, sample.spot) }
    if (best != null && best.fix.kind != FixKind.AtBoarding) return best.fix.copy(boardMeters = boardMeters).tracked()
    // Got off and walking on, off the ways of the rides: still on foot from the ride just left.
    if (best == null && leftRide != null && !moving) {
        val end = rideWay(legs[leftRide]).lastOrNull()
        if (end != null && PathJoin.meters(end, sample.spot) <= ALIGHTED_MAX_METERS) {
            return PositionFix(journey.id, leftRide, FixKind.Alighted, wayMeters(rideWay(legs[leftRide])), 0.0, -1, sample.atMillis, speed, boardMeters).tracked()
        }
    }
    // Boarded at an earlier stop than planned: before the departure, fast like a vehicle, the
    // stop of the ride coming closer since the last sample, and no ride under way by the times.
    if (riding == null && upcoming != null && boardMeters != null && best == null) {
        val departure = legs[upcoming].departure
        val soon = !departure.isAfter(now.plusMinutes(EARLY_WINDOW_MINUTES))
        val cycling = legs.subList(0, upcoming).lastOrNull()?.let { it.walking && it.cycling } == true
        val closer = prev?.boardMeters?.let { before ->
            val seconds = ((sample.atMillis - prev.atMillis) / 1000.0).coerceAtLeast(1.0)
            before - boardMeters >= 0.5 * (speed ?: 0f) * seconds
        } == true
        // When the traveller gets to the boarding stop at this speed.
        val there = speed?.takeIf { it > 0f }?.let { now.plusSeconds((boardMeters / it).toLong()) }
        val dwellFits = there != null && !there.isBefore(departure.minusMinutes(EARLY_DWELL_MINUTES))
        if (soon && dwellFits && !cycling && speed != null && speed >= VEHICLE_SPEED && boardMeters <= EARLY_MAX_METERS &&
            (closer || prev?.kind == FixKind.OnBoardEarly && prev.legIndex == upcoming)
        ) {
            return PositionFix(journey.id, upcoming, FixKind.OnBoardEarly, 0.0, wayMeters(rideWay(legs[upcoming])), 0, sample.atMillis, speed, boardMeters).tracked()
        }
    }
    return (
        best?.fix?.copy(boardMeters = boardMeters)
            ?: PositionFix(journey.id, -1, FixKind.Off, 0.0, 0.0, -1, sample.atMillis, speed, boardMeters)
        ).tracked()
}
