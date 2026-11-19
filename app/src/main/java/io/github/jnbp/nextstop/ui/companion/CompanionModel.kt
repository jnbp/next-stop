package io.github.jnbp.nextstop.ui.companion

import androidx.compose.runtime.Immutable
import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PositionFix
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.fixedStopPosition
import io.github.jnbp.nextstop.data.stopPosition
import io.github.jnbp.nextstop.ui.StepKind
import io.github.jnbp.nextstop.ui.TripChange
import io.github.jnbp.nextstop.ui.TripStep
import io.github.jnbp.nextstop.ui.rideAfter
import io.github.jnbp.nextstop.ui.rideStops
import io.github.jnbp.nextstop.ui.currentRideState
import io.github.jnbp.nextstop.ui.tripStep
import io.github.jnbp.nextstop.ui.upcomingChange
import io.github.jnbp.nextstop.ui.walkBetween
import io.github.jnbp.nextstop.ui.journeyChanges
import java.time.Duration
import java.time.OffsetDateTime

/** What the times of the companion rest on, for the small line in the blue box. */
enum class CompanionSource { Location, Live, Timetable }

/**
 * The ride the calm companion is about: the one on board, else the next one. [stops] from
 * boarding to getting off; [position] where the traveller is among them, in stop units (see
 * [stopPosition]), below 0 while not on board yet.
 */
@Immutable
class CompanionRide(
    val leg: Leg,
    val legIndex: Int,
    val onBoard: Boolean,
    val stops: List<Stopover>,
    val position: Float,
)

/**
 * Everything the new companion views (calm and with map) show, in one place. This is the one
 * binding of those views to the data of the trip: the views read only this. It is built by
 * [companionModel] from the journey, the time and the position of the device today; a
 * position-based state of the ride (ETA, on board, waiting) can replace that function later
 * without touching the views.
 *
 * - [step]: the next step (the blue box), [arrived] once the trip is there.
 * - [ride]: the ride on board or the next one; null where none is left.
 * - [change]: the next change and its walk ([changeWalkMinutes]); [nextRide] the ride after
 *   [ride], [laterRides] those after that.
 * - [source], [speedKmh]: what the times rest on; [standingMinutes] how long the vehicle stands
 *   (a jam), null where unknown (today always).
 */
@Immutable
class CompanionModel(
    val journey: Journey,
    val step: TripStep,
    val arrived: Boolean,
    val ride: CompanionRide?,
    val change: TripChange?,
    val changeWalkMinutes: Long,
    val nextRide: Leg?,
    val laterRides: List<Leg>,
    val source: CompanionSource,
    val speedKmh: Int?,
    val standingMinutes: Int? = null,
    /** Every ride of the journey with its stops, in order (the one of [ride] is that one). */
    val rides: List<CompanionRide> = emptyList(),
    /** Every change of the journey (see journeyChanges). */
    val changes: List<TripChange> = emptyList(),
    /** The whole journey as rows of one timeline, top to bottom (see journeyRows). */
    val rows: List<JourneyRow> = emptyList(),
    /** The runs of the vehicles, by trip id, for the stops at the changes. */
    val courses: Map<String, TripCourse> = emptyMap(),
)

// Below this speed (km/h) a speed is not worth saying.
private const val SPEED_SHOWN_FROM_KMH = 3

/**
 * The [CompanionModel] of [journey] (its plausible times) at [now], with the runs of the
 * vehicles ([courses], by trip id) and the position [fix] of the device (null without one, or
 * when it does not belong to this journey).
 */
fun companionModel(
    journey: Journey,
    now: OffsetDateTime,
    courses: Map<String, TripCourse>,
    fix: PositionFix?,
): CompanionModel {
    val usable = fix?.takeIf { it.journeyId == journey.id && it.kind != FixKind.Off }
    val step = tripStep(journey, now, usable)
    val legs = journey.legs
    val onBoardByFix = usable?.takeIf {
        it.kind == FixKind.OnRide || it.kind == FixKind.AtEnd || it.kind == FixKind.OnBoardEarly
    }?.legIndex?.takeIf { legs.getOrNull(it)?.walking == false }
    val ridingByTime = legs.indexOfFirst { !it.walking && !now.isBefore(it.departure) && now.isBefore(it.arrival) }
        .takeIf { it >= 0 }
    val upcoming = legs.indexOfFirst { !it.walking && it.departure.isAfter(now) }.takeIf { it >= 0 }
    // The position wins over the times: on board by position is on board.
    val rideIndex = onBoardByFix ?: ridingByTime ?: upcoming
    val ride = rideIndex?.let { index ->
        val leg = legs[index]
        val onBoard = index == onBoardByFix || (onBoardByFix == null && index == ridingByTime)
        val stops = rideStops(leg, leg.tripId?.let { courses[it] })
        val position = if (!onBoard) {
            -1f
        } else {
            val times = stops.map { (it.arrival ?: it.departure)?.toInstant()?.toEpochMilli() }
            val timed = stopPosition(times, now.toInstant().toEpochMilli())
            val own = usable?.takeIf { it.legIndex == index }
            when {
                own == null -> timed
                own.kind == FixKind.OnBoardEarly -> 0f
                own.kind == FixKind.AtEnd -> (stops.lastIndex - AT_END_SHARE).coerceAtLeast(0f)
                // The next stop of the fix counts in the stops of the journey only.
                stops === leg.stopovers -> fixedStopPosition(timed, own.nextStop, stops.lastIndex)
                else -> timed
            }
        }
        CompanionRide(leg, index, onBoard, stops, position)
    }
    val change = upcomingChange(journey, now)
    val nextRide = ride?.let { rideAfter(journey, it.leg) }
    val nextIndex = nextRide?.let { next -> legs.indexOfFirst { it === next } } ?: -1
    val later = if (nextIndex >= 0) legs.drop(nextIndex + 1).filter { !it.walking } else emptyList()
    val walk = change?.let { walkBetween(journey, it.arriving) } ?: 0L
    // The whole journey: every ride with its stops (rides over drawn as passed), every change.
    val changes = journeyChanges(journey)
    val rides = legs.indices.filter { !legs[it].walking }.map { index ->
        if (ride != null && index == ride.legIndex) {
            ride
        } else {
            val leg = legs[index]
            val stops = rideStops(leg, leg.tripId?.let { courses[it] })
            val over = rideIndex == null || index < rideIndex
            CompanionRide(leg, index, false, stops, if (over) stops.lastIndex.toFloat() else -1f)
        }
    }
    val rows = journeyRows(
        walking = legs.map { it.walking },
        walkMinutes = legs.map { Duration.between(it.departure, it.arrival).toMinutes() },
        changes = changes.mapTo(HashSet()) { it.keepLegs },
        focus = ride?.legIndex,
        onBoard = ride?.onBoard == true,
    )
    // On board by the times only (#224) is not by location.
    val located = usable != null && usable.kind != FixKind.AtBoarding && !usable.byTimes
    val speed = usable?.speed?.let { (it * 3.6f).toInt() }?.takeIf { it >= SPEED_SHOWN_FROM_KMH }
    val source = when {
        located -> CompanionSource.Location
        ride?.leg?.realtime == true -> CompanionSource.Live
        else -> CompanionSource.Timetable
    }
    return CompanionModel(
        journey = journey,
        step = step,
        arrived = step.kind == StepKind.Arrived,
        ride = ride,
        change = change,
        changeWalkMinutes = walk,
        nextRide = nextRide,
        laterRides = later,
        source = source,
        speedKmh = if (located) speed else null,
        standingMinutes = StandingClock.minutes(journey, now),
        rides = rides,
        changes = changes,
        rows = rows,
        courses = courses,
    )
}

/**
 * How long the vehicle on board stands by the ride state (jam, long stop), in whole minutes;
 * null while it moves or has stood for less than [JAM_SHOWN_FROM_MINUTES]. Remembers when the standing began, per journey.
 */
internal object StandingClock {
    private var journeyId: String? = null
    private var since: Long? = null

    @Synchronized
    fun minutes(journey: Journey, now: OffsetDateTime): Int? {
        val at = now.toInstant().toEpochMilli()
        if (journeyId != journey.id) { journeyId = journey.id; since = null }
        val state = currentRideState(journey, now)
        if (!state.standing) { since = null; return null }
        // From when the vehicle came to stand, not from when it is first said to stand (a minute later).
        val start = state.standingSinceMillis?.coerceAtMost(at) ?: since ?: at.also { since = it }
        // A stop at a station also stands for a while: only a longer stand reads as a jam.
        return ((at - start) / 60_000L).toInt().takeIf { it >= JAM_SHOWN_FROM_MINUTES }
    }
}

// A vehicle standing shorter than this (a stop at a station) is not called a jam.
private const val JAM_SHOWN_FROM_MINUTES = 2

// At the stop where the ride is left, by position: drawn just short of it.
private const val AT_END_SHARE = 0.1f
