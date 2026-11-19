package io.github.jnbp.nextstop.wear.complication

import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.TripSnapshot

/*
 * What the countdown of a trip in a complication refers to, told by a monochrome icon in place
 * of the line's title (a short slot or a ring has room for one of the two, see textSlotExtras):
 * waiting for the ride, riding, getting off soon, changing, on foot or by bike.
 */

/** The state of a running trip a complication shows. */
enum class TripState { Wait, Ride, GetOff, Change, Walk, Cycle, Arrived }

/** The glyph of a [TripState]; a ride and the wait for it by the kind of vehicle. */
enum class TripGlyph { Hourglass, Bus, Tram, Train, Exit, Change, Walk, Bike, Check }

/**
 * The state of a step of [kind]. [leave]: the step sets off on foot to the first ride.
 * [cycling]: the way of this step on foot is ridden by bike. [waiting]: the phone has the
 * traveller at the stop. [afterRide]: another ride came before this one (the step is a change).
 * [stopsLeft] (-1 unknown) and [alert] tell a get-off that comes next.
 */
fun tripState(
    kind: StepKind,
    leave: Boolean = false,
    cycling: Boolean = false,
    waiting: Boolean = false,
    afterRide: Boolean = false,
    change: Boolean = false,
    stopsLeft: Int = -1,
    alert: Boolean = false,
): TripState = when (kind) {
    StepKind.Board -> when {
        leave -> if (cycling) TripState.Cycle else TripState.Walk
        // Between two rides and not yet seen at the stop: the change is still going on.
        afterRide && !waiting -> TripState.Change
        else -> TripState.Wait
    }
    StepKind.GetOff -> when {
        !(alert || stopsLeft in 0..1) -> TripState.Ride
        change -> TripState.Change
        else -> TripState.GetOff
    }
    StepKind.WalkToEnd -> if (cycling) TripState.Cycle else TripState.Walk
    StepKind.Arrived -> TripState.Arrived
}

/** The state of [trip] (see the other tripState). */
fun tripState(trip: TripSnapshot): TripState = tripState(
    kind = trip.kind,
    leave = trip.leave,
    cycling = trip.walk?.cycling == true,
    waiting = trip.waiting,
    afterRide = trip.planIndex > 0 || trip.rating != null || trip.gap != null,
    change = trip.change,
    stopsLeft = trip.stopsLeft,
    alert = trip.alert,
)

/** The glyph of [state]; waiting and riding show the vehicle of [product] (a train for an unknown one). */
fun tripGlyph(state: TripState, product: String?): TripGlyph = when (state) {
    TripState.Wait -> TripGlyph.Hourglass
    TripState.Ride -> vehicleGlyph(product)
    TripState.GetOff -> TripGlyph.Exit
    TripState.Change -> TripGlyph.Change
    TripState.Walk -> TripGlyph.Walk
    TripState.Cycle -> TripGlyph.Bike
    TripState.Arrived -> TripGlyph.Check
}

/** The vehicle of [product]: a bus, a tram, else a train (S-Bahn, U-Bahn, regional, ferry, unknown). */
fun vehicleGlyph(product: String?): TripGlyph = when (product) {
    "bus", "coach" -> TripGlyph.Bus
    "tram" -> TripGlyph.Tram
    else -> TripGlyph.Train
}

/**
 * Where the icons of a running trip go (#253, #282): by default the complication and the
 * ongoing activity (its chip at the foot of the watch face) both show the app's icon ([app]).
 * With the option "state icons" ([stateIcons]) the complication shows the [state] and the chip
 * keeps the app's icon, so the same state icon never stacks twice under a ring.
 */
data class TripIcons<I>(val complication: I, val ongoing: I)

/** The [TripIcons] of a trip in [state] with the option [stateIcons]; see there. */
fun <I> tripIcons(stateIcons: Boolean, state: I, app: I): TripIcons<I> =
    if (stateIcons) TripIcons(complication = state, ongoing = app) else TripIcons(complication = app, ongoing = app)

/**
 * The icon a complication shows in place of a slide's own [specific] one (home, warning, a
 * state, #282): the app's icon ([app]) by default, the specific one only with the option
 * [stateIcons]; a slide without an icon stays without one.
 */
fun <I> complicationIcon(stateIcons: Boolean, specific: I?, app: I): I? =
    if (specific == null || stateIcons) specific else app

/** What stands before the minutes in the short status of a running trip on the watch face (its ongoing activity). */
enum class StatusLead { Line, GetOff, Change, Leave, None }

/**
 * The lead of the status of a step of [kind] in [state]: getting off or changing at the next
 * stop says so, setting off on foot ([leave]) too; else the [line] waited for or ridden, and
 * nothing on the way to the end.
 */
fun statusLead(state: TripState, kind: StepKind, leave: Boolean, line: String?): StatusLead = when {
    kind == StepKind.GetOff && state == TripState.GetOff -> StatusLead.GetOff
    kind == StepKind.GetOff && state == TripState.Change -> StatusLead.Change
    kind == StepKind.Board && leave -> StatusLead.Leave
    (kind == StepKind.Board || kind == StepKind.GetOff) && !line.isNullOrBlank() -> StatusLead.Line
    else -> StatusLead.None
}

/**
 * The short status ("U3 · 6'", "Aussteigen 2'"): [count] the minutes or the clock time, the
 * words for getting off, changing and setting off as the app says them.
 */
fun statusText(lead: StatusLead, line: String?, count: String, getOff: String, change: String, leave: String): String = when (lead) {
    StatusLead.Line -> "$line · $count"
    StatusLead.GetOff -> "$getOff $count"
    StatusLead.Change -> "$change $count"
    StatusLead.Leave -> "$leave · $count"
    StatusLead.None -> count
}
