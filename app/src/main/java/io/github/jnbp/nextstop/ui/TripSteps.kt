package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.CompanionPosition
import io.github.jnbp.nextstop.data.FixKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.KeptJourneyId
import io.github.jnbp.nextstop.data.journeyRideShape
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PositionFix
import io.github.jnbp.nextstop.data.RideStage
import io.github.jnbp.nextstop.data.RideState
import io.github.jnbp.nextstop.data.RideTracker
import io.github.jnbp.nextstop.data.byRide
import io.github.jnbp.nextstop.data.rideState
import io.github.jnbp.nextstop.data.STANDING_SPEED
import io.github.jnbp.nextstop.data.plausible
import java.time.OffsetDateTime

/** What the traveller has to do next on a followed trip. */
enum class StepKind { Board, GetOff, WalkToEnd, Arrived }

/** The next step of a followed trip: what to do, when, with which ride and where. */
data class TripStep(
    val kind: StepKind,
    val time: OffsetDateTime,
    val leg: Leg?,
    val place: String,
    /** True when [time] is the app's estimate, as the provider's prognosis was implausible (see [plausible]). */
    val estimated: Boolean = false,
    /** For [StepKind.WalkToEnd]: the way to the destination is ridden by bike (the bike combo, see [Leg.cycling]). */
    val cycling: Boolean = false,
    /**
     * For [StepKind.Board] before a journey that starts on foot: the walk has not begun yet, so the
     * step is setting off ("Leave at 05:07") and [time] is when to leave, not the departure of [leg].
     */
    val leave: Boolean = false,
    /**
     * For [StepKind.Board]: the traveller is at the stop of [leg] by position and waits for it
     * ("Warte auf 66 · kommt ca. 10:08"); the ride counts as boarded only once the position
     * moves with it (see [RideState]).
     */
    val waiting: Boolean = false,
    /** For [StepKind.GetOff] on board by position: the vehicle stands (jam, stop); [time] is the live one. */
    val standing: Boolean = false,
    /**
     * For [StepKind.Board] by position: the ride has probably gone without the traveller (see
     * [io.github.jnbp.nextstop.data.rideMissed]). The step asks "Missed it?"; a tap leads to
     * "Missed the train", which looks for the next way. Nothing is planned anew without it.
     */
    val missed: Boolean = false,
    /**
     * For [StepKind.Board] by position: when the ride waited for (or walked to) was due by its
     * times, before the state held it "still to come" (#184). [time] moves on with the clock past
     * it; this one stays, so the step can say how overdue the ride is (see [waitPhase]).
     */
    val dueAt: OffsetDateTime? = null,
    /** For a ride waited for: how it stands against its time when the step was made (see [waitPhase]). */
    val wait: WaitPhase = WaitPhase.Coming,
    /** With [WaitPhase.Overdue]: the whole minutes past its time. */
    val overdueMinutes: Long = 0,
)

/** How a ride the traveller waits for stands against its time (#184, see [waitPhase]). */
enum class WaitPhase {
    /** Still a minute or more away: the minutes count down. */
    Coming,

    /** Due within the minute, or overdue by less than [OVERDUE_SHOWN_MINUTES]: "S1 kommt jeden Moment". */
    AnyMoment,

    /** Overdue by [OVERDUE_SHOWN_MINUTES] or more, without news: "S1 überfällig · +2′". */
    Overdue,
}

/** A ride waited for is called overdue ("überfällig") from this many minutes past its time. */
const val OVERDUE_SHOWN_MINUTES = 2L

/**
 * The [WaitPhase] of the step [step] at [now], and for [WaitPhase.Overdue] the whole minutes
 * past its time (else 0). Only for a ride waited for at its stop ([TripStep.waiting]); every
 * other step counts down as before ([WaitPhase.Coming]). Instead of a counter stuck at "0′"
 * while the vehicle is late without news (#184).
 */
fun waitPhase(step: TripStep, now: OffsetDateTime): Pair<WaitPhase, Long> {
    if (!step.waiting || step.missed || step.kind != StepKind.Board) return WaitPhase.Coming to 0L
    val due = step.dueAt ?: step.time
    val seconds = java.time.Duration.between(now, due).seconds
    if (seconds >= 60) return WaitPhase.Coming to 0L
    val late = (-seconds).coerceAtLeast(0) / 60
    return if (late >= OVERDUE_SHOWN_MINUTES) WaitPhase.Overdue to late else WaitPhase.AnyMoment to 0L
}

/** What the sub-line of the companion's hero names of where (#211, see [heroWhere]). */
enum class HeroWhere {
    /** Where the ride of the step is boarded: its stop position or platform (see [boardPlace]). */
    Board,

    /** The change after the ride on board: where the connection leaves and the minutes to change. */
    Change,

    /** Neither. */
    None,
}

/**
 * #211: what the hero's sub-line names of where at the step [step], the traveller on board the
 * ride shown when [onBoard]: before boarding (waiting for it or on the way) where that ride is
 * boarded; on board before getting off, the change after it. Never the platform of the ride
 * after the one waited for ("Warte auf X1 · ab Gleis 2" named the platform of the next train).
 */
fun heroWhere(step: TripStep, onBoard: Boolean): HeroWhere = when {
    step.kind == StepKind.Board && !step.missed && step.leg != null -> HeroWhere.Board
    step.kind == StepKind.GetOff && onBoard -> HeroWhere.Change
    else -> HeroWhere.None
}

/** Where a ride is boarded (#211): the "Pos." number of its stop position ([spot]) or its platform as given ([platform]). */
data class BoardPlace(val spot: String?, val platform: String?)

/**
 * Where [leg] is boarded, for the hero: the stop position of a bus or tram where known (its
 * "Pos." number, [Leg.boardingSpot]), else the platform where it is plausible for the ride (see
 * [io.github.jnbp.nextstop.data.plausiblePlatform]); null where neither is known.
 */
fun boardPlace(leg: Leg): BoardPlace? {
    leg.boardingSpot?.label?.trim()?.takeIf { it.isNotEmpty() }?.let { return BoardPlace(it, null) }
    return io.github.jnbp.nextstop.data.plausiblePlatform(leg.departurePlatform, leg.product)?.let { BoardPlace(null, it) }
}

/** This step with its [waitPhase] at [now]. */
internal fun TripStep.waited(now: OffsetDateTime): TripStep {
    val (phase, minutes) = waitPhase(this, now)
    return if (phase == wait && minutes == overdueMinutes) this else copy(wait = phase, overdueMinutes = minutes)
}

/**
 * When to set off for a journey whose first ride, at [firstRide] in [legs], follows a walk: the
 * start of that walk (the provider's walk-start time). Null without a walk of its own before it,
 * and for walks of no distance only (a change of platform within a station, see [inStation]).
 */
internal fun leaveTime(legs: List<Leg>, firstRide: Int): OffsetDateTime? {
    if (firstRide <= 0) return null
    val walks = legs.subList(0, firstRide)
    if (walks.any { !it.walking } || walks.all { it.inStation }) return null
    return legs.first().departure.takeIf { it.isBefore(legs[firstRide].departure) }
}

/** A walk the provider gives as 0 m: within a station, nothing to set off for. Unknown distances are real walks. */
internal val Leg.inStation: Boolean get() = walking && walkMeters == 0

/**
 * The next step of [journey] at [now], by its times and, while the trip companion follows it
 * with the position of the device, by where the device is (see [positionStep] and
 * [CompanionPosition]). Its times are the plausible ones (see [Journey.plausible]), its
 * [TripStep.leg] is the leg of [journey] itself, so it can be found among its legs.
 */
fun tripStep(journey: Journey, now: OffsetDateTime): TripStep =
    tripStep(journey, now, CompanionPosition.fixFor(journey, now.toInstant().toEpochMilli()))

/**
 * The next step of [journey] at [now] with the position [fix] (null: by the times alone). With
 * a fix that places the traveller, the step follows the one ride state ([RideState], see
 * [rideStep]); else the times, corrected by [positionStep] as before.
 */
fun tripStep(journey: Journey, now: OffsetDateTime, fix: PositionFix?): TripStep {
    if (fix == null) return timedStep(journey, now)
    val state = rideState(journey, now, fix, RideTracker.etaFor(journey, now.toInstant().toEpochMilli()))
    if (!state.byPosition) return positionStep(timedStep(journey, now), journey, fix, now)
    return rideStep(journey, state, now) ?: positionStep(timedStep(journey, now), journey, fix, now)
}

/**
 * The ride state of [journey] at [now] as the companion has it: the fresh position and the
 * arrival on board of [CompanionPosition] and [RideTracker]; by the times alone without them.
 * The one state the hero block, timeline, change card, notification and watch bind to.
 */
fun currentRideState(journey: Journey, now: OffsetDateTime): RideState {
    val millis = now.toInstant().toEpochMilli()
    return rideState(journey, now, CompanionPosition.fixFor(journey, millis), RideTracker.etaFor(journey, millis))
}

/**
 * [journey] with its times as the ride state at [now] has them (see [byRide]): the plausible
 * times, with rides left by position over, the ride on board arriving at the blended time and
 * a ride waited for past its time still to come. For everything that shows times of the
 * followed trip; the planned times and so [Journey.id] stay.
 */
fun rideJourney(journey: Journey, now: OffsetDateTime): Journey {
    val shown = journey.plausible()
    return shown.byRide(currentRideState(journey, now), now)
}

/**
 * The step of [journey] by the ride [state] (by position) at [now]: on board, get off at the
 * blended arrival; at the end, get off now; on foot after a ride, take the next one (or walk to
 * the destination); waiting at a stop, the ride waited for, at the time it is expected. Null
 * where [state] has no step of its own (before the trip, at the end): the times decide then.
 */
internal fun rideStep(journey: Journey, state: RideState, now: OffsetDateTime): TripStep? {
    val shown = journey.plausible()
    val ridden = shown.byRide(state, now)
    val legs = ridden.legs
    val index = state.legIndex
    // When the ride waited for was due, before the state held it as still to come (#184).
    val due = shown.legs.getOrNull(index)?.departure
    val own = journey.legs.getOrNull(index)
    val leg = legs.getOrNull(index)
    return when (state.stage) {
        RideStage.OnBoard -> if (own == null || leg == null) null else {
            TripStep(StepKind.GetOff, state.time ?: leg.arrival, own, leg.to, estimated = state.estimated, standing = state.standing)
        }
        RideStage.Alighting -> if (own == null || leg == null) null else TripStep(StepKind.GetOff, now, own, leg.to)
        RideStage.Walking -> if (own != null && leg != null) {
            TripStep(StepKind.Board, leg.departure, own, leg.from, estimated = state.overdue, missed = state.missed, dueAt = due)
        } else {
            val last = legs.last()
            if (now.isBefore(ridden.arrival)) {
                TripStep(StepKind.WalkToEnd, ridden.arrival, null, journey.legs.last().to, last.arrivalEstimated, cycling = last.walking && last.cycling)
            } else {
                TripStep(StepKind.Arrived, ridden.arrival, null, journey.legs.last().to, last.arrivalEstimated)
            }
        }
        RideStage.Waiting -> if (own == null || leg == null) null else {
            TripStep(StepKind.Board, leg.departure, own, leg.from, estimated = state.overdue, waiting = true, missed = state.missed, dueAt = due).waited(now)
        }
        RideStage.Before, RideStage.Arrived -> null
    }
}

// The trip lets go of the notification this long after its arrival, and the app ends the trip then.
internal const val AFTER_ARRIVAL_MINUTES = 5L

/** Still on board by position, a trip goes on past its arrival for at most this long. */
const val ON_BOARD_MAX_EXTRA_MINUTES = 60L

/**
 * True when the followed [journey] is over at [now]: [AFTER_ARRIVAL_MINUTES] after its arrival,
 * unless the position [fix] still has the traveller on board (a train later than its data say),
 * and then at the latest [ON_BOARD_MAX_EXTRA_MINUTES] after the arrival. The service and the
 * app end the trip by this one rule.
 */
fun tripOver(journey: Journey, now: OffsetDateTime, fix: PositionFix?): Boolean {
    if (!now.isAfter(journey.arrival.plusMinutes(AFTER_ARRIVAL_MINUTES))) return false
    val onBoard = fix?.kind == FixKind.OnRide || fix?.kind == FixKind.OnBoardEarly
    return !onBoard || now.isAfter(journey.arrival.plusMinutes(ON_BOARD_MAX_EXTRA_MINUTES))
}

/** [tripOver] with the position the companion has for [journey]. */
fun tripOver(journey: Journey, now: OffsetDateTime): Boolean =
    tripOver(journey, now, CompanionPosition.fixFor(journey, now.toInstant().toEpochMilli()))

/** While a followed trip is on board past its arrival, the app looks this often whether it is over. */
const val TRIP_OVER_LOOK_MILLIS = 30_000L

// A train that is late beyond its data is taken to cover the rest of its way this fast (m/s) at least.
private const val ETA_SPEED = 8.0

// "Board now" by position holds at most this long after the departure.
private const val BOARD_GRACE_MINUTES = 6L

/** The next step of [journey] at [now] by its times alone. */
internal fun timedStep(journey: Journey, now: OffsetDateTime): TripStep {
    val shown = journey.plausible()
    val legs = shown.legs
    val rides = legs.indices.filter { !legs[it].walking }
    val riding = rides.firstOrNull { !now.isBefore(legs[it].departure) && now.isBefore(legs[it].arrival) }
    val upcoming = rides.firstOrNull { legs[it].departure.isAfter(now) }
    val end = journey.legs.last().to
    val last = legs.last()
    return when {
        // On a vehicle: the next thing to do is getting off.
        riding != null -> legs[riding].let {
            TripStep(StepKind.GetOff, it.arrival, journey.legs[riding], it.to, it.arrivalEstimated)
        }
        upcoming != null -> legs[upcoming].let {
            // Before the walk to the first ride: when to leave, not when the ride departs.
            val leave = if (upcoming == rides.first()) leaveTime(legs, upcoming) else null
            if (leave != null && now.isBefore(leave)) {
                TripStep(StepKind.Board, leave, journey.legs[upcoming], it.from, leave = true)
            } else {
                TripStep(StepKind.Board, it.departure, journey.legs[upcoming], it.from)
            }
        }
        // The last walk to the destination is still part of the trip.
        now.isBefore(shown.arrival) ->
            TripStep(StepKind.WalkToEnd, shown.arrival, null, end, last.arrivalEstimated, cycling = last.walking && last.cycling)
        else -> TripStep(StepKind.Arrived, shown.arrival, null, end, last.arrivalEstimated)
    }
}

/**
 * The step by the times ([timed]) corrected by the position [fix] at [now]: where the times and
 * the position disagree, the position wins. On a ride short of its end: get off at its end
 * (also while the times say the ride is over, when the train is later than its data say, or
 * the next ride is due: then the time of the step is an estimate from the way left). On board
 * before the planned stop (boarded earlier): get off at the end of that ride. At the end of the
 * ride: get off now. Still standing at the stop of a ride the times say has left: board now
 * (for a few minutes). [journey] is the journey of [timed]; its legs are those of the step.
 */
fun positionStep(
    timed: TripStep,
    journey: Journey,
    fix: PositionFix?,
    now: OffsetDateTime,
): TripStep {
    if (fix == null || fix.kind == FixKind.Off || fix.journeyId != journey.id) return timed
    val leg = journey.legs.getOrNull(fix.legIndex) ?: return timed
    if (leg.walking) return timed
    val shown = journey.plausible().legs.getOrNull(fix.legIndex) ?: return timed
    val getOff = StepKind.GetOff
    val board = StepKind.Board
    return when (fix.kind) {
        FixKind.OnRide, FixKind.OnBoardEarly -> {
            val etaSeconds = (fix.metersToEnd / maxOf(ETA_SPEED, (fix.speed ?: 0f).toDouble())).toLong()
            val earliest = now.plusSeconds(etaSeconds)
            // The vehicle is later than its time: the time of getting off is the app's estimate.
            val late = shown.arrival.isBefore(earliest)
            val time = if (late) earliest else shown.arrival
            if (timed.kind == getOff && timed.leg === leg) {
                if (late) timed.copy(time = time, estimated = true) else timed
            } else {
                TripStep(getOff, time, leg, shown.to, estimated = late || shown.arrivalEstimated)
            }
        }
        FixKind.AtEnd -> {
            if (timed.kind == getOff && timed.leg === leg) {
                if (timed.time.isAfter(now)) timed.copy(time = now) else timed
            } else if (timed.kind == board && timed.leg === leg) {
                // Already at its end while the times say it has not left: the data hang.
                TripStep(getOff, now, leg, shown.to, estimated = true)
            } else {
                timed
            }
        }
        FixKind.AtBoarding -> {
            val standing = fix.speed == null || fix.speed < STANDING_SPEED
            if (timed.kind == getOff && timed.leg === leg && standing && !now.isAfter(shown.departure.plusMinutes(BOARD_GRACE_MINUTES))) {
                TripStep(board, now, leg, shown.from)
            } else {
                timed
            }
        }
        FixKind.Alighted, FixKind.Off -> timed
    }
}

/**
 * [fresh] with the id of [known] where the provider sends no planned times: the id of a
 * journey (see [Journey.id]) rests on the planned times, else on the live ones, which move
 * with every refresh. The id is kept by the shape of the rides (see KeptJourneyId.loose), so
 * the id of the followed journey, and the keys of its reminders and alarms, stay the same while
 * it is followed. No planned time is made up from the first times seen: it showed every later
 * live time as a delay. Only for the same legs (same count, lines and stops); else [fresh] as it is.
 */
internal fun withKnownPlannedTimes(fresh: Journey, known: Journey): Journey {
    if (fresh.legs.size != known.legs.size) return fresh
    if (fresh.legs.all { it.plannedDeparture != null && it.plannedArrival != null } || fresh.id == known.id) return fresh
    for (index in fresh.legs.indices) {
        val leg = fresh.legs[index]
        val before = known.legs[index]
        if (before.line != leg.line || before.from != leg.from || before.to != leg.to) return fresh
    }
    return fresh.copy(keptId = KeptJourneyId(known.id, journeyRideShape(fresh.legs), loose = true))
}

/** How the way of a leg of the followed trip is drawn on the map (see AppRoot.tripRoutes). */
enum class RoutePhase {
    /** Behind the traveller: grey. */
    Behind,

    /** On board: grey up to the vehicle, in its colour ahead. */
    Riding,

    /** Still to come: in its colour. */
    Ahead,
}

/**
 * The [RoutePhase] of the leg [index] ([leg], times as shown) at [now] by the ride [state]:
 * on board by position the ride on board is ridden, the legs before it behind and every leg
 * after it ahead, whatever the clock says (#170: past the live arrival, still on the train,
 * the next ride's departure already passed). Otherwise by the times.
 */
fun routePhase(index: Int, leg: Leg, now: OffsetDateTime, state: RideState): RoutePhase {
    val onBoard = state.byPosition && state.legIndex >= 0 &&
        (state.stage == RideStage.OnBoard || state.stage == RideStage.Alighting)
    if (onBoard) {
        return when {
            index < state.legIndex -> RoutePhase.Behind
            index == state.legIndex -> RoutePhase.Riding
            else -> RoutePhase.Ahead
        }
    }
    return when {
        !now.isBefore(leg.arrival) -> RoutePhase.Behind
        !now.isBefore(leg.departure) -> RoutePhase.Riding
        else -> RoutePhase.Ahead
    }
}

/** The point [along] metres along [way] (its end beyond it); null for a way of fewer than two points. */
fun ridePositionOnWay(way: List<Pair<Double, Double>>, along: Double): Pair<Double, Double>? {
    if (way.size < 2) return null
    var left = along.coerceAtLeast(0.0)
    for (i in 1 until way.size) {
        val step = io.github.jnbp.nextstop.data.PathJoin.meters(way[i - 1], way[i])
        if (step >= left && step > 0) {
            val share = left / step
            return (way[i - 1].first + (way[i].first - way[i - 1].first) * share) to
                (way[i - 1].second + (way[i].second - way[i - 1].second) * share)
        }
        left -= step
    }
    return way.last()
}
