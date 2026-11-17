package io.github.jnbp.nextstop.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ChangeMapKind
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.rideTown
import io.github.jnbp.nextstop.data.townedStopName
import io.github.jnbp.nextstop.data.WalkTarget
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.TripCourse
import io.github.jnbp.nextstop.data.briefStopName
import io.github.jnbp.nextstop.data.optionOrder
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.ChangeTime
import io.github.jnbp.nextstop.data.changeTime
import io.github.jnbp.nextstop.data.journeyChangesOf
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.Tightness
import io.github.jnbp.nextstop.data.WaitTone
import io.github.jnbp.nextstop.data.tone
import io.github.jnbp.nextstop.data.shownWalkMinutes
import io.github.jnbp.nextstop.data.alertingWarnings
import io.github.jnbp.nextstop.data.quietWarnings
import io.github.jnbp.nextstop.ui.theme.AppColors

/** The other ways on from a transfer stop: offered while a trip is followed, and for a journey that is looked at. */
data class Alternatives(
    val stopId: String,
    val stopName: String,
    /** How many legs of the followed journey stay as they are. */
    val keepLegs: Int,
    /** When the traveller reaches the transfer stop, by the plausible times (see [plausible]). */
    val arrival: OffsetDateTime,
    /** True when [arrival] is the app's estimate, as the provider's prognosis was implausible. */
    val arrivalEstimated: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val options: List<Journey> = emptyList(),
)

@Composable
fun stepLabel(step: TripStep): String = when (step.kind) {
    StepKind.Board -> if (step.leave) stringResource(R.string.step_leave, step.time.clock()) else stringResource(R.string.step_board, shortLine(step.leg?.line) ?: "", step.place.plainStopName())
    StepKind.GetOff -> stringResource(R.string.step_get_off, step.place.plainStopName())
    StepKind.WalkToEnd -> stringResource(if (step.cycling) R.string.step_cycle else R.string.step_walk, step.place.plainStopName())
    StepKind.Arrived -> stringResource(R.string.arrived)
}

/** The same words outside of the interface, for the notification of a followed trip. */
fun stepLabel(context: Context, step: TripStep): String = when (step.kind) {
    StepKind.Board -> if (step.leave) context.getString(R.string.step_leave, step.time.clock()) else context.getString(R.string.step_board, shortLine(step.leg?.line) ?: "", step.place.plainStopName())
    StepKind.GetOff -> context.getString(R.string.step_get_off, step.place.plainStopName())
    StepKind.WalkToEnd -> context.getString(if (step.cycling) R.string.step_cycle else R.string.step_walk, step.place.plainStopName())
    StepKind.Arrived -> context.getString(R.string.arrived)
}

/**
 * The change ahead on a followed trip: where it happens, how many legs lie before it,
 * the ride that arrives there, the ride that leaves, and the walk between them if there is one.
 * [rating] is its category within the journey (see journeyChangeRatings), [time] its minutes
 * and category as every screen shows them (see changeTime).
 */
data class TripChange(
    val stop: Stop,
    val keepLegs: Int,
    val arriving: Leg,
    val next: Leg,
    val walk: Leg?,
    val rating: Tightness? = null,
    val time: ChangeTime = changeTime(arriving, next, 0, rating),
    /**
     * Minutes from getting off to being at the next ride by a re-routed walk (see
     * [WalkDetour]); every connecting departure is reached no earlier. 0 without one.
     */
    val walkFloor: Long = 0,
)

/**
 * A walk of the followed trip re-routed from where the traveller left its way: the walk at
 * [legIndex] of the journey still takes [minutes] and ends at [arrival]; [late] when the ride
 * after it leaves before that.
 */
data class WalkDetour(val legIndex: Int, val minutes: Long, val arrival: OffsetDateTime, val late: Boolean)

/**
 * [change] as a re-routed walk ([detour]) changes it, where that walk belongs to it: the walk
 * counts from getting off to the new end of the walk, and a change that can no longer be
 * made says so, as one missed by a delay does. [nextIndex] is the index of its next ride.
 */
fun detouredChange(change: TripChange, nextIndex: Int, detour: WalkDetour?): TripChange {
    if (detour == null || detour.legIndex < change.keepLegs || detour.legIndex >= nextIndex) return change
    val walked = minutesBetween(change.arriving.arrival, detour.arrival).coerceAtLeast(0)
    val time = if (detour.late) {
        change.time.copy(walk = maxOf(walked, change.time.minutes + 1), tightness = Tightness.VeryRisky)
    } else {
        change.time.copy(walk = maxOf(change.time.walk, walked))
    }
    return change.copy(time = time, walkFloor = walked)
}

/** All changes of [journey] in order: every ride that has a ride before it. */
fun journeyChanges(journey: Journey): List<TripChange> {
    val legs = journey.legs
    // Other connections start where the traveller gets off, and need to know where the trip ends.
    if (legs.last().toPlace == null) return emptyList()
    val rideIndices = legs.indices.filter { !legs[it].walking }
    val rated = journeyChangesOf(journey).associateBy { it.nextIndex }
    return rideIndices.zipWithNext().mapNotNull { (arrivingIndex, nextIndex) ->
        val stop = legs[arrivingIndex].toPlace ?: legs[nextIndex].fromPlace
        if (stop == null) {
            null
        } else {
            TripChange(
                stop = stop,
                keepLegs = arrivingIndex + 1,
                arriving = legs[arrivingIndex],
                next = legs[nextIndex],
                walk = legs.subList(arrivingIndex + 1, nextIndex).firstOrNull { it.walking },
                rating = rated[nextIndex]?.tightness,
                time = rated[nextIndex]?.time ?: changeTime(legs[arrivingIndex], legs[nextIndex]),
            )
        }
    }
}

/** The next change of [journey]: the first one whose connecting ride has not left yet. */
fun upcomingChange(journey: Journey, now: OffsetDateTime): TripChange? =
    journeyChanges(journey).firstOrNull { now.isBefore(it.next.departure) }

/** Minutes on foot between [leg] and the next ride of [journey]. */
fun walkBetween(journey: Journey, leg: Leg): Long {
    val index = journey.legs.indexOfFirst { it === leg }.takeIf { it >= 0 } ?: journey.legs.indexOf(leg)
    if (index < 0) return 0
    return journey.legs.drop(index + 1).takeWhile { it.walking }
        .sumOf { Duration.between(it.departure, it.arrival).toMinutes().coerceAtLeast(0) }
}

/** Two legs are the same ride when line and planned departure agree. */
private fun sameRide(first: Leg, second: Leg): Boolean =
    first.line == second.line &&
        (first.plannedDeparture ?: first.departure) == (second.plannedDeparture ?: second.departure)

/**
 * The next step in one blue banner, with the minutes until then in lime.
 * [caption] is the small line above the step. With [actions] the sheet of the trip opens from
 * a visible ⋮ ([menuButton]) and, as a shortcut, from a long press on the banner. Without the
 * ⋮ the same actions must be reachable from a visible control elsewhere on the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripBanner(
    step: TripStep,
    now: OffsetDateTime,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    journey: Journey? = null,
    actions: TripActions? = null,
    menuButton: Boolean = true,
) {
    if (step.kind == StepKind.Arrived) {
        ArrivedBanner(destination = step.place.plainStopName(), onClick = onClick, onEnd = actions?.onEnd, compact = false, modifier = modifier)
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = actions != null && actions.any
    // Before boarding a train that leads to a change: where to board it.
    val boarding = step.leg?.takeIf { step.kind == StepKind.Board }
    val boardingNext = if (boarding != null && journey != null) rideAfter(journey, boarding) else null
    val gettingOff = step.leg?.takeIf { step.kind == StepKind.GetOff }
    val gettingOffNext = if (gettingOff != null && journey != null) rideAfter(journey, gettingOff) else null
    val gettingOffWalk = if (gettingOff != null && journey != null) walkBetween(journey, gettingOff) else 0L
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(AppColors.Blue)
            .then(
                when {
                    hasMenu -> Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { menuOpen = true },
                        onLongClickLabel = stringResource(R.string.trip_menu),
                    )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            )
            .padding(start = 12.dp, end = if (hasMenu && menuButton) 4.dp else 18.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (step.kind != StepKind.Arrived) {
            val minutes = minutesBetween(now, step.time).coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppColors.Lime)
                    .defaultMinSize(minWidth = 64.dp, minHeight = 64.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // From the clock time to the minutes as the step comes near: a cross-fade.
                FadeSwap(target = minutes < 100, contentAlignment = Alignment.Center) { near ->
                if (near) {
                    Row {
                        // A time the app estimated (see plausible) is said to be one.
                        if (step.estimated) {
                            Text(
                                text = stringResource(R.string.estimated_prefix),
                                color = AppColors.OnLime,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .alignByBaseline()
                                    .padding(end = 4.dp),
                            )
                        }
                        // From an hour on in hours and minutes ("1 h 37 min"), never one big number.
                        val (hours, rest) = durationSpan(minutes)
                        val parts = if (hours == null) {
                            listOf(minutes to stringResource(R.string.minutes_unit))
                        } else {
                            listOfNotNull(hours to stringResource(R.string.hours_unit), rest?.let { it to stringResource(R.string.minutes_unit) })
                        }
                        parts.forEachIndexed { index, (value, unit) ->
                            CountingText(
                                value = value.toInt(),
                                color = AppColors.OnLime,
                                fontSize = if (hours == null) 30.sp else 26.sp,
                                slotDigits = if (hours != null && index == 0) 1 else 2,
                                modifier = Modifier
                                    .alignByBaseline()
                                    .padding(start = if (index > 0) 6.dp else 0.dp),
                            )
                            Text(
                                text = unit,
                                color = AppColors.OnLime,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier
                                    .alignByBaseline()
                                    .padding(start = 4.dp),
                            )
                        }
                    }
                } else {
                    // Far ahead, the clock time says more than a large number of minutes.
                    Text(
                        text = step.time.clock(),
                        color = AppColors.OnLime,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        maxLines = 1,
                    )
                }
                }
            }
        }
        // The next step in place of the last one cross-fades, and the hints below come and go softly.
        Column(modifier = Modifier.weight(1f).softSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FadeSwap(target = caption) { shownCaption ->
                Text(
                    text = shownCaption,
                    color = AppColors.OnBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FadeSwap(target = stepLabel(step)) { label ->
                Text(
                    text = label,
                    color = AppColors.OnBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A bus or tram from one of many stop positions: which one, and how far.
            if (boarding != null) {
                BoardingPositionHint(boarding, journey, color = AppColors.OnBlue, modifier = Modifier.padding(top = 2.dp))
            }
            if (boarding != null && boardingNext != null) {
                CarriageHint(
                    arriving = boarding,
                    next = boardingNext,
                    onBlue = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // On board before a change: the same platform.
            if (gettingOff != null && gettingOffNext != null) {
                ChangeHint(
                    arriving = gettingOff,
                    next = gettingOffNext,
                    walkMinutes = gettingOffWalk,
                    onBlue = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (hasMenu && actions != null) {
            TripMenu(
                open = menuOpen,
                onOpen = { menuOpen = it },
                actions = actions,
                button = menuButton,
            )
        }
    }
}

// The column on the left of the timeline that holds its rail, and where the rail runs in it.
private val RAIL_WIDTH = 52.dp

// Every stop of a ride takes one row of this height, so where a stop lies is known without measuring.
private val STOP_ROW = 34.dp

// A ride runs on a rail this wide in the colour of its line.
private val RAIL_STROKE = 6.dp

// The vehicle on the rail: a small dot in the colour of its line with a ring, apart from the lime stop node.
private val VEHICLE_DOT = 14.dp

// The vehicle counts as standing at a stop this close to it, in stops; the node of that stop then shows it.
private const val AT_STOP = 0.06f

/** Where the timeline's scrolled content is seen: the place of its window, set when that is placed. */
private class TimelineViewport {
    var coordinates: LayoutCoordinates? = null
}

/** Where a ride stands at the moment: over, in progress, the next one to board, or further ahead. */
private enum class RidePhase { Done, Riding, Waiting, Later }

/** How a stop on the rail is drawn: passed, the next one the vehicle or the traveller reaches, or still ahead. */
private enum class NodeState { Passed, Next, Ahead }

/**
 * Where on the whole run of its vehicle [leg] is boarded: the call at its first stop near
 * the time of the ride, as a line can call at one stop twice (a ring). -1 when not found.
 */
private fun boardingIndex(leg: Leg, trip: List<Stopover>): Int {
    val timed = trip.indexOfFirst { stop ->
        val departure = stop.departure
        stop.name == leg.from && departure != null && abs(minutesBetween(departure, leg.departure)) <= 5
    }
    return if (timed >= 0) timed else trip.indexOfFirst { it.name == leg.from }
}

/**
 * All stops of a ride from boarding to getting off: those the journey brought, else the
 * part of the whole run of the vehicle ([course]) the ride takes, else only its two ends.
 */
private fun rideStops(leg: Leg, course: TripCourse?): List<Stopover> {
    leg.stopovers.takeIf { it.size >= 2 }?.let { return it }
    val trip = course?.stops.orEmpty()
    val board = boardingIndex(leg, trip)
    if (board >= 0) {
        val alight = (board + 1 until trip.size).firstOrNull { trip[it].name == leg.to }
        if (alight != null) return trip.subList(board, alight + 1)
    }
    return listOf(
        Stopover(leg.from, null, leg.departure, place = leg.fromPlace),
        Stopover(leg.to, leg.arrival, null, place = leg.toPlace),
    )
}

/**
 * The stop the vehicle of [leg] calls at right after the traveller gets off ([after] true) or
 * right before they board it, from the whole run of the vehicle ([course]); null while that
 * run is not known or the ride ends or begins at the end of its line. It tells the direction
 * of a ride at a change while the stops in between are unknown.
 */
private fun runNeighbour(leg: Leg, course: TripCourse?, after: Boolean): Stopover? {
    val trip = course?.stops.orEmpty()
    val board = boardingIndex(leg, trip)
    if (board < 0) return null
    if (!after) return trip.getOrNull(board - 1)
    val alight = (board + 1 until trip.size).firstOrNull { trip[it].name == leg.to } ?: return null
    return trip.getOrNull(alight + 1)
}

/** The way of the vehicle of a ride to the stop where the traveller boards it: its times, and that stop as number [board]. */
private class Approach(val clock: StopClock, val board: Int)

/**
 * The run of the vehicle of [leg] up to the boarding stop. A ride that begins at the end
 * of its line gets the run before ([feeder]) in front, which brings the vehicle there.
 * Null while the run of the vehicle is not known.
 */
private fun approachOf(leg: Leg, course: TripCourse?, feeder: TripCourse?): Approach? {
    val trip = course?.stops.orEmpty()
    val board = boardingIndex(leg, trip)
    if (board < 0) return null
    val before = feeder?.stops.orEmpty()
    if (board == 0 && before.size >= 2) {
        // The run before ends where this one begins: the vehicle arrives with the one and leaves with the other.
        val handover = before.last()
        val joined = before.dropLast(1) + trip[0].copy(arrival = handover.arrival ?: handover.departure)
        return Approach(StopClock(joined), joined.lastIndex)
    }
    return Approach(StopClock(trip.subList(0, board + 1)), board)
}

/**
 * A live time as the delay setting says: green when on time or early, red when late, grey
 * without live data; with "+2" beside it unless the coloured time stands alone (see DelayDisplay).
 */
private class LiveTime(val planned: OffsetDateTime?, val realtime: Boolean, val delayMinutes: Long)

/**
 * The vehicle of a ride on the rail: a dot in the [color] of its line with a ring, that
 * moves between the stop nodes; the line is named by the head of the ride. [place] says at
 * every beat where on the rail it is, in pixels from the top, or NaN while it is not on the
 * road or stands at a stop (the node of the stop shows it then); a change of the data lets
 * it glide to its new place, also back.
 */
@Composable
private fun VehicleMarker(color: Color, place: Density.(Long) -> Float) {
    val beat = rememberBeat()
    val drawn = remember { floatArrayOf(Float.NaN) }
    Box(
        modifier = Modifier
            // Read in the layout step, so moving costs no recomposition.
            .offset {
                beat.intValue
                val target = place(this, System.currentTimeMillis())
                if (target.isNaN()) {
                    drawn[0] = Float.NaN
                    IntOffset(-10_000, -10_000)
                } else {
                    val before = drawn[0]
                    val y = if (before.isNaN()) target else before + (target - before) * 0.3f
                    drawn[0] = y
                    IntOffset((RAIL_WIDTH / 2).roundToPx(), y.roundToInt())
                }
            }
            .graphicsLayer {
                translationX = -size.width / 2f
                translationY = -size.height / 2f
            }
            .size(VEHICLE_DOT)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(AppColors.Card)
            .padding(3.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.5.dp, AppColors.Ink.copy(alpha = 0.35f), CircleShape),
    )
}

/**
 * One stop of a ride in one row of the timeline: its node on the rail, its name, the
 * platform and the time. [strong] marks the stops where the traveller gets on or off.
 */
@Composable
private fun StopRow(
    name: String,
    platform: String?,
    time: OffsetDateTime?,
    live: LiveTime,
    state: NodeState,
    strong: Boolean,
    color: Color,
    estimated: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(STOP_ROW)
            .drawBehind {
                val spot = Offset(RAIL_WIDTH.toPx() / 2f, size.height / 2f)
                when {
                    state == NodeState.Next -> {
                        drawCircle(AppColors.Lime, radius = 8.dp.toPx(), center = spot)
                        drawCircle(AppColors.OnLime, radius = 8.dp.toPx(), center = spot, style = Stroke(2.dp.toPx()))
                    }

                    state == NodeState.Passed -> drawCircle(color, radius = (if (strong) 7 else 5).dp.toPx(), center = spot)

                    strong -> {
                        drawCircle(AppColors.Card, radius = 9.dp.toPx(), center = spot)
                        drawCircle(AppColors.Ink, radius = 6.5.dp.toPx(), center = spot, style = Stroke(5.dp.toPx()))
                    }

                    else -> {
                        drawCircle(AppColors.Card, radius = 6.dp.toPx(), center = spot)
                        drawCircle(color, radius = 4.5.dp.toPx(), center = spot, style = Stroke(3.dp.toPx()))
                    }
                }
            }
            .padding(start = RAIL_WIDTH, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            color = if (state == NodeState.Passed) AppColors.Muted else AppColors.Ink,
            fontWeight = when {
                state == NodeState.Next -> FontWeight.ExtraBold
                strong -> FontWeight.Bold
                state == NodeState.Passed -> FontWeight.Normal
                else -> FontWeight.SemiBold
            },
            fontSize = if (strong || state == NodeState.Next) 15.sp else 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (platform != null) {
            Text(text = platform, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
        }
        if (time != null) {
            // A time the app estimated is grey with "ca.": it is a guess, not the provider's prognosis.
            val display = LocalDelayDisplay.current
            Text(
                text = when {
                    estimated -> AnnotatedString(stringResource(R.string.estimated_prefix) + " " + time.clock())
                    state == NodeState.Passed -> AnnotatedString(display.clockOf(time, live.planned).clock())
                    else -> buildAnnotatedString {
                        appendDelayedTime(display, time, live.planned, live.delayMinutes, live.realtime, AppColors.Muted)
                    }
                },
                color = AppColors.Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * The head of a ride: the line on the rail, what the ride is to the traveller now and
 * where it leads. A tap folds its stops on the way in or out, when it has any.
 */
@Composable
private fun RideHead(
    leg: Leg,
    phase: RidePhase,
    next: Stopover?,
    open: Boolean,
    foldable: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badgeShown: Boolean = true,
) {
    // An arrow before the destination saves room; read out as "towards …".
    val direction = leg.direction?.let { stringResource(R.string.direction_arrow, it.plainStopName()) }
    val spokenDirection = leg.direction?.let { stringResource(R.string.direction, it.plainStopName()) }
    val delay = leg.departureDelayMinutes
    val title = when (phase) {
        RidePhase.Riding -> stringResource(R.string.riding_now)
        RidePhase.Waiting -> stringResource(R.string.your_line_comes, shortLine(leg.line) ?: "", leg.departure.clock())
        RidePhase.Done, RidePhase.Later -> direction ?: leg.to.plainStopName()
    }
    val notes = when (phase) {
        RidePhase.Riding -> listOfNotNull(
            direction,
            next?.let { stringResource(R.string.riding_next, it.name.briefStopName()) },
        )

        RidePhase.Waiting -> listOfNotNull(
            direction,
            when {
                !leg.realtime -> stringResource(R.string.scheduled)
                delay > 0 -> stringResource(R.string.late_by, delay)
                else -> stringResource(R.string.on_time)
            },
        )

        RidePhase.Done, RidePhase.Later -> listOf(
            stringResource(R.string.time_range, leg.departure.clock(), leg.arrival.clock()),
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // On the white of the timeline card, so stops that scroll beneath it do not show through.
            .background(AppColors.Card)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = foldable,
                onClickLabel = stringResource(if (open) R.string.stops_hide else R.string.stops_show),
                onClick = onToggle,
            )
            .padding(top = 10.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A long line name makes the badge wider than its column: it starts at the column's
        // left edge and reaches into the text, which gives way to it (never cut by the screen).
        val replacement = leg.railReplacement
        val badge = badgeWidth(leg)
        // Without its badge the head keeps the room of it, so a badge laid over it lines up.
        Box(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .graphicsLayer { alpha = if (badgeShown) 1f else 0f },
            contentAlignment = badgeAlignment(badge),
        ) {
            LineBadge(leg, modifier = badgePlacement(badge), minWidth = 38.dp)
        }
        Column(modifier = Modifier.weight(1f).padding(start = badgeOverhang(badge, RAIL_WIDTH))) {
            Text(
                text = title,
                color = if (phase == RidePhase.Done) AppColors.Muted else AppColors.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (direction != null && title == direction) Modifier.semantics { contentDescription = spokenDirection ?: title } else Modifier,
            )
            val noted = notes.joinToString(" · ")
            Text(
                text = noted,
                color = AppColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (direction != null && spokenDirection != null && direction in notes) {
                    Modifier.semantics { contentDescription = notes.joinToString(" · ") { if (it == direction) spokenDirection else it } }
                } else {
                    Modifier
                },
            )
            // A bus in place of the train: said in words too, not only by the badge.
            if (replacement != null) RailReplacementTag(replacement, modifier = Modifier.padding(top = 3.dp))
        }
        if (foldable) Chevron(open = open, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The width of the badge of the line of [leg] as LineBadge draws it: 8 dp on both sides, 38 dp to [BADGE_MAX_WIDTH]. */
@Composable
private fun badgeWidth(leg: Leg): Dp {
    val replacement = leg.railReplacement
    val line = replacement?.let { listOfNotNull(SEV_MARK, it.replaced).joinToString(" ") } ?: badgeLine(leg.line)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalTextStyle.current.merge(TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp))
    return remember(line, style, density) {
        val text = with(density) { measurer.measure(line ?: "?", style).size.width.toDp() }
        (text + 16.dp).coerceIn(38.dp, BADGE_MAX_WIDTH)
    }
}

/**
 * How far a badge [badge] wide reaches past the right edge of the rail column [rail] wide,
 * plus a small gap; zero for one that fits. A badge that fits is centred on the rail, a wider
 * one starts at the left edge of the column (see [badgeAlignment]), so it is never cut by the
 * edge of the screen.
 */
internal fun badgeOverhang(badge: Dp, rail: Dp): Dp = if (badge > rail) badge - rail + 6.dp else 0.dp

/** Where a badge [badge] wide sits in the rail column: centred where it fits, else from its left edge. */
private fun badgeAlignment(badge: Dp): Alignment = if (badge > RAIL_WIDTH) Alignment.CenterStart else Alignment.Center

/** The badge in the rail column: as wide as it is, growing to the right where it is wider than the column. */
private fun badgePlacement(badge: Dp): Modifier =
    Modifier.wrapContentWidth(align = if (badge > RAIL_WIDTH) Alignment.Start else Alignment.CenterHorizontally, unbounded = true)

/**
 * One ride of the timeline: its head, then its stops on a rail in the colour of the line.
 * Every ride, also the one in progress, starts with its two ends and how many stops lie
 * between; a tap on the head (with its chevron) or on that line unfolds all of them.
 * Passed stops are grey, the next one is lime, and the vehicle sits on the rail where its
 * times put it. While the traveller waits for the ride ([approach]), its vehicle comes
 * down the rail above the boarding stop. [focus], when given, gets the place of the last
 * passed stop inside this section and the height of its head, for the timeline to keep it
 * in view. The badge of its line sticks to the top of the timeline's window ([viewport],
 * moved by [scroll]) while the stops of this ride scroll through beneath it.
 */
@Composable
private fun RideSection(
    leg: Leg,
    phase: RidePhase,
    stops: List<Stopover>,
    approach: Approach?,
    now: OffsetDateTime,
    focus: IntArray?,
    viewport: TimelineViewport,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    boardingHint: (@Composable () -> Unit)? = null,
) {
    val color = if (leg.cancelled) AppColors.Rail else lineColor(leg)
    val clock = remember(stops) { StopClock(stops) }
    val nowMillis = now.toInstant().toEpochMilli()
    // The stop the vehicle reached last, while the traveller is on board, and whether it stands there.
    val progress = if (phase == RidePhase.Riding) clock.progress(nowMillis) else -1f
    val reached = if (phase == RidePhase.Riding) progress.toInt() else -1
    val standing = phase == RidePhase.Riding && progress - reached < AT_STOP
    val middle = stops.subList(1, stops.size - 1)
    // Folded to its two ends in every phase, also on board: the traveller unfolds the stops himself.
    var open by remember(phase) { mutableStateOf(false) }
    var blockHeight by remember { mutableIntStateOf(0) }
    val shownApproach = approach.takeIf { phase == RidePhase.Waiting }
    // Rows above the boarding stop: the one where the vehicle comes down to it.
    val above = if (shownApproach == null) 0 else 1
    val density = LocalDensity.current
    val dots = remember(density) { PathEffect.dashPathEffect(floatArrayOf(0.1f, with(density) { 7.dp.toPx() }), 0f) }
    fun state(index: Int): NodeState = when {
        phase == RidePhase.Done -> NodeState.Passed
        // A vehicle that stands at a stop is shown by the node of that stop.
        standing && index == reached -> NodeState.Next
        index <= reached -> NodeState.Passed
        phase == RidePhase.Riding && !standing && index == reached + 1 -> NodeState.Next
        phase == RidePhase.Waiting && index == 0 -> NodeState.Next
        else -> NodeState.Ahead
    }
    val section = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var headHeight by remember { mutableIntStateOf(0) }
    var badgeHeight by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced { section[0] = it },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RideHead(
                leg = leg,
                phase = phase,
                next = stops.getOrNull(reached + 1)?.takeIf { phase == RidePhase.Riding && reached + 1 < stops.lastIndex },
                open = open,
                foldable = middle.isNotEmpty(),
                onToggle = { open = !open },
                // The badge lies over the head as a sticky layer of its own, see below.
                badgeShown = false,
                modifier = Modifier.onSizeChanged {
                    headHeight = it.height
                    if (focus != null) focus[1] = it.height
                },
            )
            if (phase == RidePhase.Waiting) boardingHint?.invoke()
            // Where the app estimated times of this ride, it says so once, below its head.
            if (phase != RidePhase.Done && (leg.arrivalEstimated || stops.any { it.estimated })) {
                Text(
                    text = stringResource(R.string.prognosis_uncertain),
                    color = AppColors.Muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = RAIL_WIDTH, bottom = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { blockHeight = it.height }
                    .onPlaced { place ->
                        if (focus != null) {
                            val row = with(density) { STOP_ROW.roundToPx() }
                            val rows = above + if (open) (reached - 1).coerceAtLeast(0) else 0
                            focus[0] = place.positionInParent().y.roundToInt() + rows * row
                        }
                    }
                    .drawBehind {
                        val x = RAIL_WIDTH.toPx() / 2f
                        val row = STOP_ROW.toPx()
                        val first = above * row + row / 2f
                        val last = size.height - row / 2f
                        val width = RAIL_STROKE.toPx()
                        if (above > 0) {
                            drawLine(color, Offset(x, 0f), Offset(x, first), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
                        }
                        // The part of the ride that is behind is pale; the rest in the full colour of the line.
                        val behind = when {
                            phase == RidePhase.Done -> last
                            reached <= 0 -> first
                            open -> minOf(first + reached * row, last)
                            else -> first + (last - first) * reached / (stops.size - 1)
                        }
                        if (behind > first) drawLine(color.copy(alpha = 0.35f), Offset(x, first), Offset(x, behind), strokeWidth = width)
                        if (last > behind) drawLine(color, Offset(x, behind), Offset(x, last), strokeWidth = width)
                    },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (shownApproach != null) {
                        val away = ceil(shownApproach.board - shownApproach.clock.progress(nowMillis)).toInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(STOP_ROW)
                                .padding(start = RAIL_WIDTH),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when {
                                    away <= 0 -> ""
                                    away == 1 -> stringResource(R.string.stops_away_one)
                                    else -> stringResource(R.string.stops_away_many, away)
                                },
                                color = AppColors.Muted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    StopRow(
                        name = townedStopName(leg.from, leg.rideTown),
                        platform = platformLabel(leg.departurePlatform),
                        time = leg.departure,
                        live = LiveTime(leg.plannedDeparture, leg.realtime, leg.departureDelayMinutes),
                        state = state(0),
                        strong = true,
                        color = color,
                    )
                    if (middle.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = !open,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(STOP_ROW)
                                    .clickable(onClickLabel = stringResource(R.string.stops_show)) { open = true }
                                    .padding(start = RAIL_WIDTH),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.stops_between, middle.size),
                                    color = AppColors.Muted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = open,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                middle.forEachIndexed { place, stop ->
                                    val delay = stop.delaySeconds
                                    StopRow(
                                        name = townedStopName(stop.name, leg.rideTown),
                                        platform = null,
                                        time = stop.time,
                                        live = LiveTime(if (stop.arrival != null) stop.plannedArrival else stop.plannedDeparture, delay != null, ((delay ?: 0) / 60).toLong()),
                                        state = state(place + 1),
                                        strong = false,
                                        color = color,
                                        estimated = stop.estimated,
                                    )
                                }
                            }
                        }
                    }
                    StopRow(
                        name = townedStopName(leg.to, leg.rideTown),
                        platform = platformLabel(leg.arrivalPlatform),
                        time = leg.arrival,
                        live = LiveTime(leg.plannedArrival, leg.realtime, leg.arrivalDelayMinutes),
                        state = state(stops.lastIndex),
                        strong = true,
                        color = color,
                        estimated = leg.arrivalEstimated,
                    )
                }
                if (phase == RidePhase.Riding) {
                    VehicleMarker(color) { millis ->
                        val row = STOP_ROW.toPx()
                        val at = clock.progress(millis)
                        val first = row / 2f
                        val last = blockHeight - row / 2f
                        val nearest = at.roundToInt()
                        // Never on top of a node: at a stop that is shown, the node itself shows the vehicle.
                        val onNode = abs(at - nearest) < AT_STOP && (open || nearest == 0 || nearest == stops.lastIndex)
                        when {
                            onNode -> Float.NaN
                            open -> minOf(first + at * row, last)
                            else -> first + (last - first) * at / (stops.size - 1)
                        }
                    }
                } else if (shownApproach != null) {
                    VehicleMarker(color) { millis ->
                        val starts = shownApproach.clock.firstDeparture
                        if (starts == null || millis < starts - LEAD_MILLIS) {
                            Float.NaN
                        } else {
                            // Far away it waits above the stop; over the last stretch it comes down to it.
                            val row = STOP_ROW.toPx()
                            val left = (shownApproach.board - shownApproach.clock.progress(millis)).coerceIn(0f, 1f)
                            // Arrived at the boarding stop, its node shows the vehicle.
                            if (left < AT_STOP) Float.NaN else row / 2f + row * (1f - left)
                        }
                    }
                }
            }
        }
        // Only the badge of the line sticks to the top of the timeline's window while the stops
        // of this ride scroll beneath it; read while drawing, so scrolling costs no recomposition.
        val stickyBadge = badgeWidth(leg)
        Box(
            modifier = Modifier
                .width(RAIL_WIDTH)
                .zIndex(1f)
                .onSizeChanged { badgeHeight = it.height }
                .graphicsLayer {
                    scroll.value
                    // Where the head puts its badge: centred between its paddings (10 dp on top, 6 dp below).
                    val top = 10.dp.toPx() + (headHeight - 16.dp.toPx() - badgeHeight) / 2f
                    val window = viewport.coordinates
                    val own = section[0]
                    val held = if (window != null && own != null && window.isAttached && own.isAttached) {
                        // How far the top of the section has gone above the window, held so the
                        // badge leaves the last stop of its ride in view below it.
                        val above = window.positionInRoot().y - own.positionInRoot().y
                        val room = (own.size.height - top - badgeHeight - STOP_ROW.toPx()).coerceAtLeast(0f)
                        above.coerceIn(0f, room)
                    } else {
                        0f
                    }
                    translationY = top + held
                },
            contentAlignment = badgeAlignment(stickyBadge),
        ) {
            LineBadge(leg, modifier = badgePlacement(stickyBadge), minWidth = 38.dp)
        }
    }
}

/**
 * Opens walking directions to a place in Google Maps, or in whatever app shows the link;
 * a device that has none for it gets the place handed over as a plain position instead.
 */
private fun openWalkNavigation(context: Context, target: Pair<Double, Double>, cycling: Boolean = false) {
    val place = "${target.first},${target.second}"
    val mode = if (cycling) "bicycling" else "walking"
    val directions = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$place&travelmode=$mode")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, directions).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$place?q=$place")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            // No app on the device shows a place: the walk stays on the map of this app.
        }
    }
}

/**
 * A walk in one line; a tap on it shows it on the map. Below it, where given: the button
 * that unfolds its small [map] (folded unless the setting unfolds it) and, with
 * [onNavigate], the button that opens the walk in Google Maps. The map unfolds at the
 * bottom of the card.
 */
@Composable
private fun WalkCard(
    text: String,
    onClick: () -> Unit,
    onNavigate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    map: (@Composable () -> Unit)? = null,
    cycling: Boolean = false,
) {
    var mapOpen by rememberWalkMapOpen()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.BlueContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.show_on_map), onClick = onClick)
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlyphIcon(if (cycling) Glyph.Bike else Glyph.Walk, color = AppColors.Blue, size = if (cycling) 22.dp else 20.dp)
            Text(
                text = text,
                color = AppColors.OnBlueContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            )
        }
        if (map != null || onNavigate != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (map != null) Arrangement.SpaceBetween else Arrangement.End,
            ) {
                if (map != null) {
                    MapFoldButton(
                        open = mapOpen,
                        onToggle = { mapOpen = !mapOpen },
                        ground = AppColors.Card,
                        ink = AppColors.OnBlueContainer,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                if (onNavigate != null) {
                    // Measured last: where both buttons do not fit in one line, its label takes two.
                    Row(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.Blue)
                            .clickable(onClick = onNavigate)
                            .defaultMinSize(minHeight = 40.dp)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GlyphIcon(Glyph.Navigate, color = AppColors.OnBlue, size = 16.dp)
                        Text(
                            text = stringResource(R.string.navigate),
                            color = AppColors.OnBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (map != null) {
            MapFold(open = mapOpen) {
                Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) { map() }
            }
        }
    }
}

/**
 * A walk of the timeline where it happens: dotted grey on the rail, and one line that
 * shows it on the map at a tap; while it lies ahead, its small map (folded at first) and
 * a button that opens it in Google Maps.
 */
@Composable
private fun WalkEntry(
    leg: Leg,
    target: String,
    over: Boolean,
    onFocusWalk: (Leg) -> Unit,
    modifier: Modifier = Modifier,
    detour: WalkDetour? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dots = remember(density) { PathEffect.dashPathEffect(floatArrayOf(0.1f, with(density) { 7.dp.toPx() }), 0f) }
    // The stop position or entrance from OpenStreetMap where known, else the stop itself.
    val end = leg.walkTarget?.position ?: leg.toPlace?.position ?: leg.path.lastOrNull()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (over) PAST_ALPHA else 1f)
            .drawBehind {
                val x = RAIL_WIDTH.toPx() / 2f
                drawLine(AppColors.Rail, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
            }
            .padding(start = RAIL_WIDTH, top = 6.dp, bottom = 6.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WalkCard(
                text = if (detour != null) {
                    stringResource(R.string.walk_rerouted, detour.minutes, target)
                } else {
                    stringResource(
                        if (leg.cycling) R.string.cycle_to else R.string.walk_to,
                        shownWalkMinutes(leg),
                        target,
                    )
                },
                onClick = { onFocusWalk(leg) },
                onNavigate = if (end == null || over) null else ({ openWalkNavigation(context, end, leg.cycling) }),
                map = if (over || !hasWalkMap(leg)) null else ({ WalkMap(leg, onFocusWalk) }),
                cycling = leg.cycling,
            )
            // The new way takes too long for the ride after it: as a missed ride, "Missed it" searches on.
            if (detour?.late == true) {
                Text(
                    text = stringResource(R.string.walk_rerouted_late),
                    color = AppColors.DelayRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Where a walk leads by OpenStreetMap: "Pos. 3", "Eingang H" or "Zugang über …"; null where it is not known. */
@Composable
private fun walkTargetText(target: WalkTarget?): String? {
    target ?: return null
    val label = target.label
    return when {
        target.kind == WalkTargetKind.StopPosition -> stringResource(R.string.walk_target_pos, label ?: "")
        target.throughBuilding && label != null -> stringResource(R.string.walk_target_building, label)
        target.throughBuilding -> stringResource(R.string.walk_target_building_plain)
        label != null -> stringResource(R.string.walk_target_entrance, label)
        else -> stringResource(R.string.walk_target_entrance_plain)
    }
}

/**
 * The exit of a station a walk leaves by, by OpenStreetMap: "über Ausgang H", "mit dem Aufzug";
 * null where it has no name or letter.
 */
@Composable
private fun walkExitText(exit: WalkTarget?): String? {
    if (exit?.kind == WalkTargetKind.Elevator) return stringResource(R.string.walk_exit_lift)
    val label = exit?.label ?: return null
    return if (exit.throughBuilding) stringResource(R.string.walk_exit_building, label) else stringResource(R.string.walk_exit, label)
}

/** [text] with the exit [exitText] the walk leaves its station by, where known. */
private fun withExit(text: String, exitText: String?): String = if (exitText == null) text else "$text, $exitText"

/**
 * The stop and platform a walk leads to for the timeline: [name], then the stop position the
 * walk ends at (in place of the platform the provider names) or the platform and the entrance.
 */
private fun walkTargetName(name: String, platform: String?, target: WalkTarget?, targetText: String?): String =
    when {
        target == null || targetText == null -> listOfNotNull(name, platform)
        target.kind == WalkTargetKind.StopPosition -> listOf(name, targetText)
        else -> listOfNotNull(name, platform, targetText)
    }.joinToString(", ")

/**
 * Before the first ride, where it leaves from one of many stop positions of its stop: a short
 * walk row "Zu Pos. 9" on the dotted rail, with the small map of the stop (all positions faint,
 * the ride's highlighted) and the button to walk there with Google Maps.
 */
@Composable
private fun PositionWalkEntry(ride: Leg, onFocusWalk: (Leg) -> Unit, modifier: Modifier = Modifier) {
    val spot = ride.boardingSpot ?: return
    val label = spot.label ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val dots = remember(density) { PathEffect.dashPathEffect(floatArrayOf(0.1f, with(density) { 7.dp.toPx() }), 0f) }
    val walk = remember(ride) {
        val start = ride.fromPlace?.position
        boardingWalk(ride, listOfNotNull(start, spot.position))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val x = RAIL_WIDTH.toPx() / 2f
                drawLine(AppColors.Rail, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
            }
            .padding(start = RAIL_WIDTH, top = 6.dp, bottom = 6.dp),
    ) {
        WalkCard(
            text = stringResource(R.string.walk_to_position, label),
            onClick = { onFocusWalk(walk) },
            onNavigate = { openWalkNavigation(context, spot.position) },
            map = { BoardingPositionMap(ride, null, onFocusWalk) },
        )
    }
}

// Things of the trip that are over are drawn this pale.
private const val PAST_ALPHA = 0.55f

/**
 * A change in the timeline, where it happens: a gap in the rail with a node in the colour
 * of how much time the change leaves (dotted grey when there is a walk), the time, where
 * to go, and while it lies ahead where to board for a short change. The change ahead also
 * offers which connecting departure to take ([offer], null for any other change).
 */
@Composable
private fun ChangeEntry(
    change: TripChange,
    offer: Alternatives?,
    over: Boolean,
    onFocusWalk: (Leg) -> Unit,
    onSwitch: (Journey) -> Unit,
    modifier: Modifier = Modifier,
    detourMinutes: Long? = null,
    arrivingStops: List<Stopover> = change.arriving.stopovers,
    nextStops: List<Stopover> = change.next.stopovers,
    arrivingBeyond: Stopover? = null,
    nextBefore: Stopover? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dots = remember(density) { PathEffect.dashPathEffect(floatArrayOf(0.1f, with(density) { 7.dp.toPx() }), 0f) }
    // The minutes from the arrival to the departure and the category, as every screen shows them.
    val time = change.time
    val walk = change.walk
    val walkMinutes = if (walk == null) 0L else minutesBetween(walk.departure, walk.arrival)
    // A knapp change or a sprint that worked: once the connecting ride is under way the row
    // turns green and its node springs into a check. Seen happen, with a short buzz; a change
    // made before the screen opened is simply shown made.
    val made = changeMade(over, change.time.tightness)
    val madeShown = remember { Animatable(if (made) 1f else 0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(made) {
        if (!made) {
            madeShown.snapTo(0f)
        } else if (madeShown.value < 1f) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            madeShown.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val madeGreen = AppColors.Early
    val madeGround by animateColorAsState(
        if (made) madeGreen.copy(alpha = 0.12f) else madeGreen.copy(alpha = 0f),
        label = "changeMadeGround",
    )
    val rating = if (made) madeGreen else toneColor(time.tone)
    // The number of the stop position the next bus or tram leaves from, where the provider names no platform.
    val spotText = change.next.boardingSpot?.label?.let { stringResource(R.string.walk_target_pos, it) }
    val target = withExit(
        walkTargetName(
            change.next.from.plainStopName(),
            platformLabel(change.next.departurePlatform) ?: spotText,
            walk?.walkTarget,
            walkTargetText(walk?.walkTarget),
        ),
        walkExitText(walk?.walkExit),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (over && !made) PAST_ALPHA else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(madeGround)
            .drawBehind {
                val x = RAIL_WIDTH.toPx() / 2f
                if (walkMinutes > 0) {
                    drawLine(AppColors.Rail, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
                }
                val spot = Offset(x, 20.dp.toPx())
                val pop = madeShown.value
                if (pop > 0f) {
                    // The node of a change made: a green disc with a white tick, springing in.
                    drawCircle(madeGreen, radius = 9.dp.toPx() * pop, center = spot)
                    val tick = ((pop - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    if (tick > 0f) {
                        val w = 10.dp.toPx()
                        val a = spot + Offset(-w * 0.45f, 0f)
                        val b = spot + Offset(-w * 0.12f, w * 0.32f)
                        val c = spot + Offset(w * 0.45f, -w * 0.3f)
                        val first = (tick / 0.35f).coerceIn(0f, 1f)
                        val second = ((tick - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        val stroke = 2.2.dp.toPx()
                        drawLine(Color.White, a, a + (b - a) * first, strokeWidth = stroke, cap = StrokeCap.Round)
                        if (second > 0f) drawLine(Color.White, b, b + (c - b) * second, strokeWidth = stroke, cap = StrokeCap.Round)
                    }
                } else {
                    drawCircle(AppColors.Card, radius = 8.dp.toPx(), center = spot)
                    drawCircle(rating, radius = 6.dp.toPx(), center = spot, style = Stroke(3.dp.toPx()))
                }
            }
            .padding(start = RAIL_WIDTH, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.transfer_at, change.stop.name.plainStopName()),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    made -> stringResource(R.string.change_made)
                    time.possible -> changeLabel(time.minutes)
                    else -> stringResource(R.string.choice_gone)
                },
                color = rating,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
        // A change inside one station takes no walking time: name the line to change to instead.
        val way = if (walk != null && detourMinutes != null) {
            stringResource(R.string.walk_rerouted, detourMinutes, target)
        } else if (walk != null && walkMinutes > 0) {
            stringResource(if (walk.cycling) R.string.cycle_to else R.string.walk_to, walkMinutes, target)
        } else {
            stringResource(R.string.change_to, shortLine(change.next.line) ?: "", target)
        }
        if (walk != null) {
            // Keyed on the OSM data being there too: it may come after the first look (platforms, ends).
            val mapKind = remember(change.arriving, change.next, walk, walkMinutes, OsmStations.loaded) {
                changeWalkMapKind(change.arriving, change.next, walk, walkMinutes)
            }
            val end = walk.walkTarget?.position ?: change.next.fromPlace?.position ?: walk.toPlace?.position ?: walk.path.lastOrNull()
            WalkCard(
                text = way,
                onClick = { onFocusWalk(walk) },
                onNavigate = if (end == null || walkMinutes <= 0 || over) null else ({ openWalkNavigation(context, end, walk.cycling) }),
                // The walk's own map, else (a walk of no minutes or without a way, as to another stop
                // position of the same stop) the map of the change (see changeMapKind).
                map = if (over || mapKind == ChangeMapKind.None) {
                    null
                } else {
                    ({ ChangeMapOf(mapKind, change.arriving, change.next, walk, onFocusWalk) })
                },
                cycling = walk.cycling,
            )
        } else {
            Text(text = way, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            // Two stop positions of buses or trams apart: both and the way between them, folded at first.
            if (!over && hasSpotWalkMap(change.arriving, change.next)) {
                FoldedMap { SpotWalkMap(change.arriving, change.next, onFocusWalk) }
            } else if (!over && change.arriving.boardingSpot == null && change.arriving.alightingSpot == null &&
                hasBoardingPositionMap(change.next)
            ) {
                // From a train to a bus or tram of a stop of many positions: the way to its position.
                val arrivedAt = change.arriving.path.lastOrNull() ?: change.arriving.toPlace?.position
                FoldedMap { BoardingPositionMap(change.next, arrivedAt, onFocusWalk) }
            } else if (!over && !hasBoardingPositionMap(change.next) && hasChangeMap(change.arriving, change.next)) {
                // Any other change to another place, as from a stop in the street to a platform.
                FoldedMap { ChangeWalkMap(change.arriving, change.next, onFocusWalk) }
            }
        }
        // At the change itself: the same platform. Where to board was said at the ride before it.
        if (!over) {
            ChangeHint(
                arriving = change.arriving,
                next = change.next,
                walkMinutes = walkMinutes,
                arrivingStops = arrivingStops,
                nextStops = nextStops,
                arrivingBeyond = arrivingBeyond,
                nextBefore = nextBefore,
            )
        }
        if (offer != null) ConnectionChoice(change = change, offer = offer, onSwitch = onSwitch)
    }
}

/**
 * Whether a change counts as made and is shown so: the connecting ride is under way ([over])
 * and the change was knapp or a sprint, where making it is worth a little cheer.
 */
internal fun changeMade(over: Boolean, tightness: Tightness): Boolean =
    over && (tightness == Tightness.Risky || tightness == Tightness.VeryRisky)

/** The connecting departures to choose from at the change ahead, or a line that says they are on their way. */
@Composable
private fun ConnectionChoice(change: TripChange, offer: Alternatives, onSwitch: (Journey) -> Unit) {
    // Switched off in the settings: no choice and no line about it.
    if (!connectionChoiceOn.value) return
    // An offer that was loaded for another change of the trip says nothing about this one.
    if (offer.keepLegs != change.keepLegs) return
    if (offer.loading) {
        LoadingRow(text = stringResource(R.string.searching_connections), compact = true)
        return
    }
    if (offer.failed) {
        Text(
            text = stringResource(R.string.alternatives_failed),
            color = AppColors.Muted,
            fontSize = 12.sp,
        )
        return
    }
    val options = connectionOptions(change, offer)
    if (options.size >= 2) ConnectionChips(change = change, options = options, onSwitch = onSwitch)
}

/**
 * The connecting departures of a loaded [offer] at [change], in the order they arrive at the
 * destination, the chosen one among them (see [optionOrder]), and
 * every first ride once: also those that leave a little before the traveller gets
 * there, because a delay on either side can still make them the next one to catch. Empty
 * while the offer loads or when it belongs to another change.
 */
fun connectionOptions(change: TripChange, offer: Alternatives?): List<Journey> {
    if (offer == null || offer.loading || offer.failed || offer.keepLegs != change.keepLegs) return emptyList()
    val arrivingTrip = change.arriving.tripId
    return offer.options
        .filter { option ->
            val ride = option.rides.firstOrNull()
            // Staying on the vehicle the traveller arrives with is no change.
            // A cancelled ride is no connection to choose.
            ride != null && !ride.cancelled && (arrivingTrip == null || ride.tripId != arrivingTrip)
        }
        .distinctBy { option ->
            val ride = option.rides.first()
            "${ride.line}|${ride.plannedDeparture ?: ride.departure}"
        }
        .sortedWith(optionOrder)
}

/** False: the settings hide the choice of connecting departures at changes, wherever it is drawn. */
val connectionChoiceOn = mutableStateOf(true)

// So many connecting departures before and after the chosen one are kept at hand.
private const val CHOICE_BEFORE = 4
private const val CHOICE_AFTER = 3
private val CHOICE_GAP = 6.dp
private val CHOICE_HEIGHT = 84.dp

/** The check mark of the chosen tile. */
private val CHOICE_CHECK = 18.dp

/** The walking figure or clock before the minutes of a tile. */
private val CHOICE_GLYPH = 12.dp

/**
 * The question which connection to take, and the connecting departures around the chosen
 * one as chips of one size, in the order of [options] (by arrival: sooner ones before it,
 * later ones after it), each in
 * the colour of how tight the change to it is. Only departures that can be reached by the
 * live times are shown; one that becomes reachable or is lost slides in or out, and a
 * change of how tight it is changes its colour softly. Three chips fit the width, the
 * others are reached by pushing the row sideways.
 */
@Composable
fun ConnectionChips(change: TripChange, options: List<Journey>, onSwitch: (Journey) -> Unit) {
    if (!connectionChoiceOn.value) return
    val chosen = options.indexOfFirst { sameRide(it.rides.first(), change.next) }
    val from = (chosen - CHOICE_BEFORE).coerceAtLeast(0)
    val until = minOf(options.size, chosen.coerceAtLeast(0) + CHOICE_AFTER + 1)
    val offered = options.subList(from, until)
    // The time of each change as every screen shows it (see changeTime), by the live times,
    // plausible ones where the provider's prognosis of the arriving ride is not (as the change
    // row itself is rated). The chosen one is the change itself: exactly the numbers of its row.
    val arriving = remember(change.arriving) { change.arriving.plausible() }
    val times = offered.mapIndexed { index, option ->
        if (index == chosen - from) {
            change.time
        } else {
            // A re-routed walk gets there no earlier, whichever departure is taken.
            changeTime(arriving, option.rides.first(), maxOf(option.walkBeforeMinutes, change.walkFloor))
        }
    }
    // Reckoned from an estimated arrival: the minutes say "ca.".
    val about = if (arriving.arrivalEstimated) stringResource(R.string.estimated_prefix) + " " else ""
    // Reachable: the ride leaves after the traveller got there and walked to it.
    val reachable = times.map { it.possible }
    // The chips in front of the chosen one; the row is pushed so that one of them stays in view.
    val shownBefore = offered.indices.count { it < chosen - from && reachable[it] }
    val scroll = rememberScrollState()
    val settle = spring<Color>(stiffness = Spring.StiffnessMediumLow)
    // The arrival at the destination of the chosen connection, which the other tiles count from.
    val chosenArrival = offered.getOrNull(chosen - from)?.takeIf { chosen >= 0 }?.legs?.lastOrNull()?.arrival
    // What every tile says, worked out first, so that all tiles can be made wide enough for the
    // longest of them: no tile cuts its text off, also on a small phone with large text.
    val lineTexts = offered.map { badgeLine(it.rides.first().line) ?: "?" }
    val deltaTexts = offered.mapIndexed { index, option ->
        if (index == chosen - from) {
            null
        } else {
            choiceDelta(option.legs.lastOrNull()?.arrival, chosenArrival)?.let { choiceDeltaLabel(it) }
        }
    }
    val arrivalTexts = offered.map { option ->
        option.legs.lastOrNull()?.arrival?.let { stringResource(R.string.arrival_at, it.clock()) }
    }
    // Short on the tile ("5 min" after a small walking figure), in full for accessibility services.
    val minuteTexts = times.mapIndexed { index, time ->
        when {
            !reachable[index] -> stringResource(R.string.choice_gone)
            time.minutes < 0 -> about + stringResource(R.string.delay_minutes, time.minutes.toInt())
            // From an hour on in hours and minutes ("1 h 37 min").
            time.minutes >= 60 -> about + durationLabel(time.minutes)
            else -> about + stringResource(R.string.choice_change_minutes, time.minutes)
        }
    }
    val minuteSpoken = times.mapIndexed { index, time ->
        when {
            !reachable[index] -> stringResource(R.string.choice_gone)
            time.minutes < 0 -> about + stringResource(R.string.delay_minutes, time.minutes.toInt())
            else -> about + changeLabel(time.minutes)
        }
    }
    val chosenLabel = stringResource(R.string.choice_chosen)
    val baseStyle = LocalTextStyle.current
    val badgeStyle = baseStyle.merge(TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 14.sp))
    val deltaStyle = baseStyle.merge(TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp))
    val arrivalStyle = baseStyle.merge(TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 19.sp))
    val minuteStyle = baseStyle.merge(TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 14.sp))
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The width the widest tile needs: its longest line, the padding and a little slack.
    val neededWidth = remember(lineTexts, deltaTexts, arrivalTexts, minuteTexts, baseStyle, density) {
        val measure = { text: String, style: TextStyle ->
            with(density) { measurer.measure(text, style).size.width.toDp() }
        }
        val top = offered.indices.maxOfOrNull { index ->
            val badge = measure(lineTexts[index], badgeStyle) + 12.dp
            // The delta chip, or the room of the check mark of the chosen tile.
            val end = deltaTexts[index]?.let { measure(it, deltaStyle) + 10.dp } ?: CHOICE_CHECK
            badge + 6.dp + end
        } ?: 0.dp
        val arrival = arrivalTexts.maxOfOrNull { text -> text?.let { measure(it, arrivalStyle) } ?: 0.dp } ?: 0.dp
        val minutes = minuteTexts.maxOfOrNull { measure(it, minuteStyle) + CHOICE_GLYPH + 3.dp } ?: 0.dp
        maxOf(top, arrival, minutes) + 20.dp + 4.dp
    }
    // Larger text gets a taller tile, so the three lines keep their air.
    val tileHeight = CHOICE_HEIGHT + 56.dp * (density.fontScale - 1f).coerceAtLeast(0f)
    Text(
        text = stringResource(R.string.transfer_choice),
        color = AppColors.Muted,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Three tiles fit the width where their text allows it; otherwise fewer, and the row scrolls.
        val chipWidth = maxOf((maxWidth - CHOICE_GAP * 2) / 3, neededWidth)
        val stepPixels = with(LocalDensity.current) { (chipWidth + CHOICE_GAP).toPx() }
        LaunchedEffect(shownBefore, stepPixels) {
            scroll.animateScrollTo(((shownBefore - 1).coerceAtLeast(0) * stepPixels).roundToInt())
        }
        Row(
            modifier = Modifier
                .fadeEdges(scroll, horizontal = true, length = 12.dp)
                .horizontalScroll(scroll),
        ) {
            offered.forEachIndexed { index, option ->
                val ride = option.rides.first()
                key("${ride.line}|${ride.plannedDeparture ?: ride.departure}") {
                    val picked = index == chosen - from
                    val inReach = reachable[index]
                    val time = times[index]
                    // Rated by the minutes left beyond what the change needs, walk included, by the
                    // live times on both sides, and coloured by the wait it leaves (see waitTone):
                    // a long wait is a calm sand with a clock, not the green of a good change. The
                    // label names the stage and, where it is so, the long wait.
                    val tone = time.tone
                    // Read while drawing: the colour glides without composing the tile for each frame.
                    val ground = animateColorAsState(
                        if (!inReach) AppColors.DelayRed else toneContainer(tone),
                        settle,
                        label = "choiceGround",
                    )
                    val ink by animateColorAsState(
                        if (!inReach) AppColors.OnDelayRed else toneInk(tone),
                        settle,
                        label = "choiceInk",
                    )
                    // The chosen tile stands a little forward and springs there when it is picked.
                    val stand by animateFloatAsState(
                        if (picked) 1f else 0.95f,
                        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                        label = "choiceStand",
                    )
                    val press = remember { MutableInteractionSource() }
                    val toneText = toneLabel(time)
                    val tightnessLabel = if (inReach) toneText else stringResource(R.string.choice_gone)
                    val delta = deltaTexts[index]
                    val arrivalText = arrivalTexts[index]
                    // The chosen connection stays in view also when it can no longer be reached, and says so.
                    AnimatedVisibility(
                        visible = inReach || picked,
                        enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) +
                            fadeIn() + scaleIn(initialScale = 0.8f),
                        exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                    ) {
                        // Every tile alike, three lines at most: the line and the delta, the
                        // arrival at the destination, the minutes of the change. The colour says
                        // how tight it is; its name is read out to accessibility services. The
                        // chosen one has a frame and a check mark in its corner.
                        Box(
                            modifier = Modifier
                                .padding(end = CHOICE_GAP)
                                .size(width = chipWidth, height = tileHeight)
                                .graphicsLayer {
                                    scaleX = stand
                                    scaleY = stand
                                }
                                .springPress(press)
                                .clip(RoundedCornerShape(20.dp))
                                .drawBehind { drawRect(ground.value) }
                                // The chosen one keeps the colour of its stage and gets a frame.
                                .then(
                                    if (picked) Modifier.border(3.dp, AppColors.Blue, RoundedCornerShape(20.dp)) else Modifier,
                                )
                                .semantics {
                                    selected = picked
                                    stateDescription = tightnessLabel
                                }
                                .clickable(interactionSource = press, indication = LocalIndication.current, enabled = !picked && inReach) {
                                    onSwitch(option)
                                },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val badge = lineColor(ride)
                                    Text(
                                        text = lineTexts[index],
                                        color = lineInk(ride, badge),
                                        style = badgeStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(badge)
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    // How much sooner or later than the chosen one it arrives, at the right.
                                    if (delta != null) {
                                        Text(
                                            text = delta,
                                            color = ink,
                                            style = deltaStyle,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ink.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                if (arrivalText != null) {
                                    Text(
                                        text = arrivalText,
                                        color = ink,
                                        style = arrivalStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                                // The minutes from the arrival to the departure: the time for the change,
                                // after a small walking figure, or a clock where the wait is long.
                                Row(
                                    modifier = Modifier.clearAndSetSemantics { contentDescription = minuteSpoken[index] },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    if (inReach) {
                                        GlyphIcon(
                                            glyph = if (tone == WaitTone.LongWait) Glyph.Recent else Glyph.Walk,
                                            color = ink,
                                            size = CHOICE_GLYPH,
                                        )
                                    }
                                    Text(
                                        text = minuteTexts[index],
                                        color = ink,
                                        style = minuteStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            if (picked) {
                                ChosenCheck(
                                    label = chosenLabel,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 7.dp, end = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The mark of the chosen tile: a small filled circle in the main colour with a white tick. */
@Composable
private fun ChosenCheck(label: String, modifier: Modifier = Modifier) {
    val blue = AppColors.Blue
    val onBlue = AppColors.OnBlue
    Box(
        modifier = modifier
            .size(CHOICE_CHECK)
            .semantics { contentDescription = label }
            .drawBehind {
                drawCircle(blue)
                val w = size.width
                val stroke = w * 0.13f
                drawLine(onBlue, Offset(w * 0.28f, w * 0.52f), Offset(w * 0.44f, w * 0.67f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(onBlue, Offset(w * 0.44f, w * 0.67f), Offset(w * 0.73f, w * 0.36f), strokeWidth = stroke, cap = StrokeCap.Round)
            },
    )
}

/** One part of the timeline of a followed trip, in the order things happen. [key] tells it apart. */
private sealed interface TimelineEntry {
    val key: String

    class Walk(val leg: Leg, val index: Int, val target: String) : TimelineEntry {
        override val key = "walk$index"
    }

    class Ride(val leg: Leg, val index: Int) : TimelineEntry {
        override val key = "ride$index"
    }

    class Change(val change: TripChange) : TimelineEntry {
        override val key = "change${change.keepLegs}"
    }
}

/**
 * The whole of [journey] leg by leg, from the first walk to the last: a walk stands where
 * it happens, and every change between the ride that arrives and the ride that leaves
 * (a walk between two rides belongs to it). [nameTarget] names where a walk leads.
 */
private fun timelineEntries(journey: Journey, nameTarget: (Leg, Leg?) -> String): List<TimelineEntry> {
    val legs = journey.legs
    val changes = journeyChanges(journey)
    val firstRide = legs.indexOfFirst { !it.walking }
    val lastRide = legs.indexOfLast { !it.walking }
    val entries = mutableListOf<TimelineEntry>()
    legs.forEachIndexed { index, leg ->
        if (leg.walking) {
            val betweenRides = firstRide >= 0 && index > firstRide && index < lastRide
            if (!betweenRides && minutesBetween(leg.departure, leg.arrival) > 0) {
                entries.add(TimelineEntry.Walk(leg, index, nameTarget(leg, legs.drop(index + 1).firstOrNull { !it.walking })))
            }
        } else {
            entries.add(TimelineEntry.Ride(leg, index))
            changes.firstOrNull { it.keepLegs == index + 1 }?.let { entries.add(TimelineEntry.Change(it)) }
        }
    }
    return entries
}

/**
 * The entry of the timeline that shows where the traveller is at [step]: the ride on
 * board, the change or walk before the ride to board next, or the end of the trip.
 */
private fun anchorKey(journey: Journey, entries: List<TimelineEntry>, step: TripStep): String? {
    val index = journey.legs.indexOfFirst { it === step.leg }
    return when (step.kind) {
        StepKind.GetOff -> "ride$index"
        StepKind.Board -> entries.lastOrNull { entry ->
            when (entry) {
                is TimelineEntry.Change -> entry.change.next === step.leg
                is TimelineEntry.Walk -> entry.index == index - 1
                is TimelineEntry.Ride -> false
            }
        }?.key ?: "ride$index"

        StepKind.WalkToEnd, StepKind.Arrived -> entries.lastOrNull()?.key
    }
}

// The timeline waits for folding rides to settle before it moves to where the traveller is.
private const val FOLLOW_DELAY_MILLIS = 400L

/** How far a sheet over the map is pulled up: down to its low peek, the middle, or full height. */
internal enum class SheetStage { Away, Half, Full }

// The companion's blue box of the next step keeps this much room above it (to the bar on top)
// and below it (to the sheet of the timeline when that is fully up).
private val BANNER_GAP = 10.dp

// The top strip of the sheet that takes the pull: the grip line is drawn in its middle.
private val SHEET_GRIP = 22.dp

// A sheet put away (see StageSheet) is a handle this wide; it starts to shrink below this content height.
private val COMPACT_SHEET_WIDTH = 120.dp
private val COMPACT_SHEET_FROM = 28.dp

/**
 * A sheet over the map, as for the timeline of the companion and the start page. Its top
 * edge, with a small grip line in it, is pulled or tapped: down puts the sheet away so only
 * that edge and [peek] of its content stay and the map is free, up lets it cover the map.
 * In between it takes about half of the room and no more than its content needs. [header]
 * stands under the edge and [footer] at the bottom of the sheet; both stay in every stage.
 * [onRecentre], when given, shows a round button above the sheet that lets the map frame
 * the trip again after the user moved it.
 */
@Composable
internal fun StageSheet(
    stage: SheetStage,
    onStage: (SheetStage) -> Unit,
    onRecentre: (() -> Unit)?,
    modifier: Modifier = Modifier,
    peek: Dp = 0.dp,
    topGap: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onSheetTop: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // How far the finger has pulled the sheet, in pixels (up is negative).
    var pull by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val handleLabel = stringResource(if (stage == SheetStage.Away) R.string.sheet_show else R.string.sheet_hide)
    // What header and footer take, measured, so the content gets the rest of the room.
    var headerHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        val fixed = with(density) {
            ((if (header != null) headerHeight else 0) + (if (footer != null) footerHeight else 0)).toDp()
        }
        // Fully up, the sheet stays [topGap] below what lies above it.
        val room = (maxHeight - SHEET_GRIP - fixed - topGap).coerceAtLeast(0.dp)
        // The height of the content in every stage.
        val stageHeights = SheetStage.entries.map { each ->
            when (each) {
                SheetStage.Away -> peek.coerceAtMost(room)
                SheetStage.Half -> maxOf(room * 0.48f, peek).coerceAtMost(room)
                SheetStage.Full -> room
            }
        }
        val rest = stageHeights[stage.ordinal]
        // The sheet follows the finger and settles on its stage with a spring.
        val height by animateDpAsState(
            (rest - with(density) { pull.toDp() }).coerceIn(0.dp, room),
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "tripSheet",
        )
        // The pull and the stages in pixels, read by the gestures as they are now.
        val restPx = with(density) { rest.toPx() }
        val roomPx = with(density) { room.toPx() }
        val stagePx = stageHeights.map { with(density) { it.toPx() } }
        val geometry by rememberUpdatedState(SheetGeometry(restPx, roomPx, stagePx[0], stagePx))
        val currentStage by rememberUpdatedState(stage)
        val currentOnStage by rememberUpdatedState(onStage)
        // Lets go of the sheet: it settles on the stage nearest to where it is heading.
        val settle: (Float) -> Unit = { velocity ->
            val shown = (geometry.rest - pull).coerceIn(0f, geometry.room)
            val target = SheetStage.entries[settleStage(geometry.stages, shown, velocity)]
            if (target != currentStage) currentOnStage(target)
            pull = 0f
        }
        // Drags anywhere on the sheet move it first where it can still go that way: up until it
        // is at its top stage, then the content scrolls; down the content scrolls back to its
        // top first, and what is left moves the sheet down.
        val sheetScroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                    val shown = geometry.rest - pull
                    val free = (geometry.room - shown).coerceAtLeast(0f)
                    val used = maxOf(available.y, -free)
                    pull += used
                    return Offset(0f, used)
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    val shown = geometry.rest - pull
                    val free = (shown - geometry.lowest).coerceAtLeast(0f)
                    val used = minOf(available.y, free)
                    pull += used
                    return Offset(0f, used)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    // A sheet that was moved settles and takes the fling; otherwise the content flings.
                    if (pull == 0f) return Velocity.Zero
                    settle(available.y)
                    return available
                }
            }
        }
        // The grip, header and footer move the sheet directly.
        val dragState = rememberDraggableState { delta ->
            pull = (pull + delta).coerceIn(geometry.rest - geometry.room, geometry.rest - geometry.lowest)
        }
        val sheetDrag = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity -> settle(velocity) },
        )
        if (onRecentre != null && stage != SheetStage.Full) {
            RoundButton(
                glyph = Glyph.Locate,
                description = stringResource(R.string.recentre_trip),
                onClick = onRecentre,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp),
            )
        }
        // A sheet with nothing but its content (the companion) shrinks, as it goes away, to a
        // small handle in the middle instead of a bar across the whole width; it grows back
        // with the content, in step with the height, so it morphs rather than switches.
        val shrinks = header == null && footer == null && peek <= 0.dp
        val shrink = if (shrinks) ((COMPACT_SHEET_FROM - height) / COMPACT_SHEET_FROM).coerceIn(0f, 1f) else 0f
        GlassSurface(
            // The top edge of the sheet on the screen, for a map that centres what is in view above it.
            modifier = Modifier
                .then(if (shrink > 0f) Modifier.width(lerp(maxWidth, COMPACT_SHEET_WIDTH, shrink)) else Modifier.fillMaxWidth())
                .graphicsLayer { alpha = 1f - 0.15f * shrink }
                .then(if (onSheetTop != null) Modifier.onGloballyPositioned { onSheetTop(it.positionInRoot().y) } else Modifier),
            shape = RoundedCornerShape(30.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The whole width of the top edge takes the pull, so it is easy to catch.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SHEET_GRIP)
                        .semantics { contentDescription = handleLabel }
                        .clickable { onStage(if (stage == SheetStage.Away) SheetStage.Half else SheetStage.Away) }
                        .then(sheetDrag),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppColors.Muted.copy(alpha = 0.5f)),
                    )
                }
                if (header != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { headerHeight = it.height }
                            .then(sheetDrag),
                    ) {
                        header()
                    }
                }
                if (height > 1.dp) {
                    // The content takes what header and footer leave: they are measured first, so
                    // the footer is never squeezed below the sheet's edge, whatever its last
                    // measured height said.
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .heightIn(max = height)
                            // Parts that do not scroll move the sheet too; scrolling content
                            // passes its drags through the connection above.
                            .then(sheetDrag)
                            .nestedScroll(sheetScroll),
                    ) {
                        content()
                    }
                }
                if (footer != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { footerHeight = it.height }
                            .then(sheetDrag),
                    ) {
                        footer()
                    }
                }
            }
        }
    }
}

/** The heights of a sheet in pixels: of its stage now, of all its room, of its lowest stage, and of every stage. */
private class SheetGeometry(val rest: Float, val room: Float, val lowest: Float, val stages: List<Float>)

// A sheet let go faster than this, in pixels a second, goes on to the next stage that way.
private const val SHEET_FLING = 1200f

// How far ahead a sheet that is let go is looked at, in seconds of its speed.
private const val SHEET_PROJECTION = 0.15f

/**
 * The stage a sheet settles on when it is let go at [shown] pixels of content height while
 * moving at [velocity] pixels a second (up is negative): the stage nearest to where it would
 * be a moment later, and at least the next one in the direction of a fast fling.
 * [stages] are the content heights of all stages from low to high.
 */
internal fun settleStage(stages: List<Float>, shown: Float, velocity: Float): Int {
    val ahead = shown - velocity * SHEET_PROJECTION
    var nearest = stages.indices.minByOrNull { abs(stages[it] - ahead) } ?: 0
    if (velocity < -SHEET_FLING) {
        val above = stages.indices.firstOrNull { stages[it] > shown + 1f }
        if (above != null && stages[nearest] <= shown + 1f) nearest = above
    } else if (velocity > SHEET_FLING) {
        val below = stages.indices.lastOrNull { stages[it] < shown - 1f }
        if (below != null && stages[nearest] >= shown - 1f) nearest = below
    }
    return nearest
}

/**
 * A followed trip in one slim line, for the top of the start page's sheet, in the strong
 * colours of the banner of the trip: the minutes until the next step in the highlight box,
 * the step, and the line of the ride it is about. A tap opens the trip; with [actions] a
 * visible ⋮ (and, as a shortcut, a long press) opens the sheet of the trip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripBand(
    step: TripStep,
    now: OffsetDateTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: TripActions? = null,
) {
    if (step.kind == StepKind.Arrived) {
        ArrivedBanner(destination = step.place.plainStopName(), onClick = onClick, onEnd = actions?.onEnd, compact = true, modifier = modifier)
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = actions != null && actions.any
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Blue)
            .then(
                if (hasMenu) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuOpen = true },
                        onLongClickLabel = stringResource(R.string.trip_menu),
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(start = 6.dp, end = if (hasMenu) 2.dp else 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (step.kind != StepKind.Arrived) {
            val minutes = minutesBetween(now, step.time).coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.Lime)
                    .defaultMinSize(minWidth = 52.dp, minHeight = 36.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                // The minutes roll as they count down (see RollingText).
                RollingText(
                    value = minutes,
                    text = (if (step.estimated) stringResource(R.string.estimated_prefix) + " " else "") + if (minutes < 60) {
                        stringResource(R.string.trip_band_minutes, minutes)
                    } else if (minutes < 100) {
                        // From an hour on in hours and minutes ("1 h 37 min").
                        durationLabel(minutes)
                    } else {
                        step.time.clock()
                    },
                    color = AppColors.OnLime,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                )
            }
        }
        // The next step in place of the last one cross-fades.
        FadeSwap(target = stepLabel(step), modifier = Modifier.weight(1f)) { label ->
            Text(
                text = label,
                color = AppColors.OnBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val leg = step.leg
        if (leg != null) LineBadge(leg, minWidth = 38.dp)
        if (hasMenu && actions != null) TripMenu(open = menuOpen, onOpen = { menuOpen = it }, actions = actions)
    }
}

/**
 * A followed trip that has arrived, as the banner ([compact] false) or the slim band of the
 * start page: a check mark, "Arrived · <destination>" and, with [onEnd], a visible ✕ that
 * ends the trip; no ⋮, nothing else is left to do. A tap opens the trip ([onClick]). As high
 * as the banner or band of a trip on the way, so the page does not jump at the arrival.
 */
@Composable
private fun ArrivedBanner(
    destination: String,
    onClick: (() -> Unit)?,
    onEnd: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val mark = if (compact) 36.dp else 52.dp
    val endLabel = stringResource(R.string.trip_end_action)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 20.dp else 32.dp))
            .background(AppColors.Blue)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = if (compact) 48.dp else 76.dp)
            .padding(start = if (compact) 6.dp else 12.dp, end = if (onEnd != null) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(mark)
                .clip(CircleShape)
                .background(AppColors.Lime),
            contentAlignment = Alignment.Center,
        ) {
            CheckMark(color = AppColors.OnLime, modifier = Modifier.size(mark * 0.5f))
        }
        Text(
            text = stringResource(R.string.trip_arrived_band, destination),
            color = AppColors.OnBlue,
            fontWeight = if (compact) FontWeight.Bold else FontWeight.ExtraBold,
            fontSize = if (compact) 14.sp else 18.sp,
            lineHeight = if (compact) 18.sp else 22.sp,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onEnd != null) {
            Box(
                modifier = Modifier
                    .size(if (compact) 40.dp else 48.dp)
                    .clip(CircleShape)
                    .background(AppColors.OnBlue.copy(alpha = 0.16f))
                    .clickable(onClickLabel = endLabel, onClick = onEnd)
                    .semantics { contentDescription = endLabel },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Close, color = AppColors.OnBlue, size = if (compact) 16.dp else 18.dp)
            }
        }
    }
}

/**
 * What can be done with a followed trip, for its sheet ([TripActionSheet]); an action left
 * null is not offered. [everyMinute] is the state of the mode "Every minute counts", which
 * [onEveryMinute] switches.
 */
class TripActions(
    val onReplan: (() -> Unit)? = null,
    val onChangeDestination: (() -> Unit)? = null,
    val onContinueTo: (() -> Unit)? = null,
    val onShare: (() -> Unit)? = null,
    val everyMinute: Boolean = false,
    val onEveryMinute: ((Boolean) -> Unit)? = null,
    val onEnd: (() -> Unit)? = null,
    /** "Abkürzungs-Alarm" for this trip, and its switch; on while [everyMinute] is on. */
    val shortcutAlert: Boolean = false,
    val onShortcutAlert: ((Boolean) -> Unit)? = null,
) {
    /** Whether there is anything to offer at all. */
    val any: Boolean
        get() = onReplan != null || onChangeDestination != null || onContinueTo != null ||
            onShare != null || onEveryMinute != null || onEnd != null
}

/**
 * The ⋮ of a trip banner and the sheet it opens. [open] is shared with a long press on the
 * banner, which is only a shortcut; with [button] false the banner shows no ⋮, and the screen
 * must offer the sheet through another visible control (the companion's "Change" button).
 */
@Composable
private fun TripMenu(
    open: Boolean,
    onOpen: (Boolean) -> Unit,
    actions: TripActions,
    button: Boolean = true,
) {
    val menuLabel = stringResource(R.string.trip_menu)
    if (button) Text(
        text = "⋮",
        color = AppColors.OnBlue,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = menuLabel) { onOpen(true) }
            .semantics { contentDescription = menuLabel }
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
    if (open) TripActionSheet(actions = actions, onDismiss = { onOpen(false) })
}

/**
 * The sheet of a followed trip at the bottom of the screen: re-plan from here, change the
 * destination, go on to one more place, share the trip (where the screen has no share button of
 * its own: the banner on the start page; it opens [ShareSheet]), the switch
 * "Every minute counts" and ending the trip, as far as [actions] offers them. Picking an
 * action closes the sheet; the switch keeps it open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripActionSheet(actions: TripActions, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.PlainGround,
        contentColor = AppColors.Ink,
    ) {
        // A sheet is a window of its own: the picture of the map does not line up behind it.
        CompositionLocalProvider(LocalBackdrop provides null) {
            val onEveryMinute = actions.onEveryMinute
            // The switch "Every minute counts" has no action of its own: its entry is marked by a null run.
            val rows = buildList {
                actions.onReplan?.let { add(TripSheetEntry(R.string.replan_here, R.string.replan_here_hint, it)) }
                actions.onChangeDestination?.let { add(TripSheetEntry(R.string.retarget_change, null, it)) }
                actions.onContinueTo?.let { add(TripSheetEntry(R.string.retarget_continue, null, it)) }
                actions.onShare?.let { add(TripSheetEntry(R.string.trip_share, R.string.trip_share_hint, it)) }
                actions.onEnd?.let { add(TripSheetEntry(R.string.trip_end_action, null, it)) }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.trip_change_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
                rows.forEachIndexed { index, entry ->
                    val run = entry.run ?: return@forEachIndexed
                    TripActionRow(
                        title = stringResource(entry.title),
                        text = entry.hint?.let { stringResource(it) },
                        shape = groupShape(index, rows.lastIndex),
                        onClick = {
                            onDismiss()
                            run()
                        },
                    )
                }
                if (onEveryMinute != null) {
                    // A mode, not an action: a group of its own with a heading, and a full explanation.
                    Text(
                        text = stringResource(R.string.trip_mode_title),
                        color = AppColors.Muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                    )
                    val alertSwitch = actions.onShortcutAlert
                    val lastRow = if (alertSwitch != null) 1 else 0
                    TripSwitchRow(
                        title = stringResource(R.string.every_minute),
                        text = stringResource(R.string.every_minute_sheet_hint),
                        checked = actions.everyMinute,
                        onChange = onEveryMinute,
                        shape = groupShape(0, lastRow),
                    )
                    // The shortcut alert for this trip: on with "Every minute counts", which needs it.
                    if (alertSwitch != null) {
                        TripSwitchRow(
                            title = stringResource(R.string.shortcut_alert),
                            text = stringResource(R.string.shortcut_alert_trip_text),
                            checked = actions.shortcutAlert || actions.everyMinute,
                            onChange = { on -> if (!actions.everyMinute) alertSwitch(on) },
                            shape = groupShape(1, lastRow),
                        )
                    }
                }
            }
        }
    }
}

/** An entry of the sheet of a trip: its name, a short explanation, and what it does. */
private class TripSheetEntry(@StringRes val title: Int, @StringRes val hint: Int?, val run: (() -> Unit)?)

/** One action in the sheet of a trip: a white row with its name and, where given, a short explanation. */
@Composable
private fun TripActionRow(title: String, text: String?, shape: Shape, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(text = title, color = AppColors.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (text != null) Text(text = text, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

/**
 * The small mark in the companion's header while "Every minute counts" is on: a blue pill
 * with a lightning bolt and "Every minute". Only a mark; the switch is in the sheet of "Change".
 */
@Composable
private fun EveryMinuteBadge() {
    val description = stringResource(R.string.every_minute_on)
    Text(
        text = "⚡ " + stringResource(R.string.every_minute_badge),
        color = AppColors.OnBlue,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Blue)
            .semantics { contentDescription = description }
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** A switch in the sheet of a trip: the whole row toggles it. */
@Composable
private fun TripSwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit, shape: Shape) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.Card)
            .clickable { onChange(!checked) }
            .defaultMinSize(minHeight = 56.dp)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = AppColors.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = text, color = AppColors.Muted, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Where a new plan starts when the device knows no position: the next stop the traveller
 * reaches on the ride in progress, else where the next ride begins.
 */
fun replanFallback(journey: Journey, now: OffsetDateTime): Stop? {
    val rides = journey.rides
    val riding = rides.firstOrNull { !now.isBefore(it.departure) && now.isBefore(it.arrival) }
    val upcoming = rides.firstOrNull { it.departure.isAfter(now) }
    return riding?.stopovers?.firstOrNull { it.time?.isAfter(now) == true }?.place
        ?: riding?.toPlace
        ?: upcoming?.fromPlace
}

/**
 * The visible button that opens the sheet of a trip in the companion's bottom row: "Change"
 * with a small chevron.
 */
@Composable
private fun ChangeTripButton(onClick: () -> Unit, shape: Shape, modifier: Modifier = Modifier, minHeight: Dp = 56.dp) {
    val label = stringResource(R.string.trip_change)
    Row(
        modifier = modifier
            .clip(shape)
            .background(AppColors.BlueContainer)
            .clickable(onClickLabel = stringResource(R.string.trip_menu), onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            text = label,
            color = AppColors.OnBlueContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = "▾",
            color = AppColors.OnBlueContainer,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
        )
    }
}

/**
 * The live-share button in the companion's bottom row: a square tile with the live glyph (a dot
 * sending waves), between "Change" and "End". It opens the share sheet of the trip with the live
 * link first (see [ShareSheet]); the trip's only share entry. It settles in with the row
 * ([appearModifier]; with animations off it is there at once).
 */
@Composable
private fun ShareTripButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    val description = stringResource(R.string.trip_share_live)
    Box(
        modifier = modifier
            .then(appearModifier(1))
            .semantics { contentDescription = description }
            .springPress(interactions)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Card)
            .clickable(interactionSource = interactions, indication = LocalIndication.current, onClick = onClick)
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.LiveShare, color = AppColors.Ink, size = 22.dp)
    }
}

/**
 * Trip companion, one view: the map with the way, the traveller and the vehicles of the
 * trip stays behind; on top the next step in one banner, and below it a sheet with the
 * whole trip as one timeline from top to bottom, which keeps where the traveller is in
 * view. The sheet can be pulled down to free the map and up to cover it.
 * [courses] holds the whole trips of the rides, by their handle, once they are loaded;
 * [feeders] the runs that bring the vehicle of a ride to the stop where it begins.
 * [onMissed] gets the stop to search a new connection from; [onReplan] the stop to fall
 * back on where the device knows no position. [onFocusWalk] shows a walk on the map.
 * [onSwitch] takes another connecting departure for the rest of the trip.
 */
@Composable
fun CompanionScreen(
    journey: Journey,
    now: OffsetDateTime,
    alternatives: Alternatives?,
    courses: Map<String, TripCourse>,
    feeders: Map<String, TripCourse>,
    onMinimize: () -> Unit,
    onEnd: () -> Unit,
    onMissed: (Stop) -> Unit,
    onReplan: (Stop?) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    onSwitch: (Journey) -> Unit,
    everyMinute: Boolean,
    onEveryMinute: (Boolean) -> Unit,
    shortcutAlert: Boolean,
    onShortcutAlert: (Boolean) -> Unit,
    faster: FasterOffer?,
    onTakeFaster: (FasterOffer) -> Unit,
    onIgnoreFaster: (FasterOffer) -> Unit,
    onRecentre: (() -> Unit)?,
    notificationsOff: Boolean,
    onNotificationsNoteSeen: () -> Unit,
    onChangeDestination: (() -> Unit)? = null,
    onContinueTo: (() -> Unit)? = null,
    walkDetour: WalkDetour? = null,
) {
    // Everything below shows plausible times: where the provider's prognosis is implausible,
    // the app's estimate (see plausible). The provider's journey itself stays as it is.
    val provided = journey
    val journey = remember(provided) { provided.plausible() }
    val courses = remember(courses) { courses.mapValues { it.value.plausible() } }
    val feeders = remember(feeders) { feeders.mapValues { it.value.plausible() } }
    val legs = journey.legs
    val rides = journey.rides
    val step = tripStep(journey, now)
    val arrived = step.kind == StepKind.Arrived
    val riding = rides.firstOrNull { !now.isBefore(it.departure) && now.isBefore(it.arrival) }
    val upcoming = rides.firstOrNull { it.departure.isAfter(now) }
    // Who missed a ride still stands where it left: at the stop of the current or the next ride.
    val boarding = (riding ?: upcoming)?.fromPlace
    // Without a position of the device, a new plan starts at the next stop the traveller reaches.
    val ahead = replanFallback(journey, now)
    // Everything that changes the trip, in one sheet: opened by the visible "Change" button,
    // and by a long press on the banner as a shortcut.
    val tripActions = TripActions(
        onReplan = if (arrived) null else ({ onReplan(ahead) }),
        onChangeDestination = onChangeDestination,
        onContinueTo = onContinueTo,
        // Sharing has its own button in the bottom row, not a second entry in this sheet.
        everyMinute = everyMinute,
        onEveryMinute = onEveryMinute,
        shortcutAlert = shortcutAlert,
        onShortcutAlert = onShortcutAlert,
    )
    var actionsOpen by remember { mutableStateOf(false) }
    if (actionsOpen) TripActionSheet(actions = tripActions, onDismiss = { actionsOpen = false })
    var shareOpen by remember { mutableStateOf(false) }
    if (shareOpen) {
        ShareSheet(
            journey = journey,
            fromName = legs.first().from.plainStopName(),
            toName = legs.last().to.plainStopName(),
            now = now,
            following = true,
            onDismiss = { shareOpen = false },
        )
    }
    // Notices of rides that are over no longer matter.
    // Notices about lifts raise the sign only for step-free travel (see warningAlerts); for
    // everyone else they are listed quietly below it, as in the detail of a journey.
    val stepFree = rememberStepFree()
    val rideWarnings = rides.filter { now.isBefore(it.arrival) }.flatMap { it.warnings }.distinct()
    val warnings = alertingWarnings(rideWarnings, stepFree)
    val quiet = quietWarnings(rideWarnings, stepFree)
    var stage by remember { mutableStateOf(SheetStage.Half) }
    // Where a walk leads: the stop and platform of the ride after it, or the place itself.
    val platforms = legs.associateWith { platformLabel(it.departurePlatform) }
    // Where OpenStreetMap leads a walk: its stop position ("Pos. 3") or a station entrance.
    val walkTargets = legs.associateWith { walkTargetText(it.walkTarget) }
    // And the exit of the station a walk after a train leaves by ("über Ausgang H").
    val walkExits = legs.associateWith { walkExitText(it.walkExit) }
    val entries = timelineEntries(journey) { walk, rideAfter ->
        val name = if (rideAfter == null) {
            walk.to.plainStopName()
        } else {
            walkTargetName(rideAfter.from.plainStopName(), platforms[rideAfter], walk.walkTarget, walkTargets[walk])
        }
        withExit(name, walkExits[walk])
    }
    val showWalk: (Leg) -> Unit = { leg ->
        // The map has to be free to show the walk.
        stage = SheetStage.Away
        // The leg of the provider's journey, which the map finds among its legs.
        onFocusWalk(provided.legs.getOrNull(journey.legs.indexOfFirst { it === leg }) ?: leg)
    }
    val upcomingIndex = legs.indexOfFirst { it === upcoming }
    val nextChange = upcomingChange(journey, now)
    // The timeline moves to where the traveller is whenever the step changes, unless the user is scrolling it.
    val scroll = rememberScrollState()
    val entryTops = remember { mutableMapOf<String, Int>() }
    val cardTop = remember { IntArray(1) }
    // The place of the last passed stop in the ride on board, and the height of its head.
    val focus = remember { IntArray(2) }
    val viewport = remember { TimelineViewport() }
    val anchor = anchorKey(journey, entries, step)
    val followMargin = with(LocalDensity.current) { 8.dp.roundToPx() }
    LaunchedEffect(anchor) {
        val target = anchor ?: return@LaunchedEffect
        delay(FOLLOW_DELAY_MILLIS)
        val top = entryTops[target] ?: return@LaunchedEffect
        if (scroll.isScrollInProgress) return@LaunchedEffect
        // Inside the ride on board, the head that sticks to the top must not cover the stop.
        val inner = if (target.startsWith("ride") && riding != null && focus[0] > 0) focus[0] - focus[1] else 0
        scroll.animateScrollTo((cardTop[0] + top + inner - followMargin).coerceAtLeast(0))
    }

    // On a small screen (the cover screen of a flip phone) the next step fills the screen; a
    // swipe shows the map with the step in one line on top, and a tap on it brings the card back.
    var compactMap by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val room = screenRoom(maxWidth, maxHeight)
        val wide = room == ScreenRoom.Wide
        // A phone held sideways: little height, so the buttons come right below the blue box
        // (always reachable), the bars are lower and the timeline takes what is left.
        val short = wide && isShortWide(maxWidth, maxHeight)
        val buttonHeight = if (short) SHORT_BUTTON_HEIGHT else 56.dp
        val mapCovers = LocalMapCover.current
        // Without the sheet nothing covers the bottom of the map.
        if (room != ScreenRoom.Phone && mapCovers != null) SideEffect { mapCovers[1] = Float.NaN }
        if (room == ScreenRoom.Small && !compactMap) {
            CompactTripCard(
                journey = journey,
                now = now,
                onBack = onMinimize,
                onShowMap = { compactMap = true },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = LocalNavInset.current),
            )
        } else if (room == ScreenRoom.Small) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = LocalNavInset.current),
            ) {
                TripBand(
                    step = step,
                    now = now,
                    onClick = { compactMap = false },
                    modifier = Modifier.coversMapTop(),
                )
            }
        } else Column(
            modifier = if (wide) {
                // On a wide screen a panel at the side, and the map beside it.
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (short) SHORT_SIDE_PANEL_MAX else SIDE_PANEL_MAX)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = LocalNavInset.current)
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, bottom = LocalNavInset.current)
            },
        ) {
            Spacer(modifier = Modifier.height(if (short) 4.dp else 8.dp))
            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RoundButton(
                        glyph = Glyph.Back,
                        description = stringResource(R.string.back),
                        onClick = onMinimize,
                        solid = true,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Held sideways the bar is one line lower: the caption goes, the mark stays.
                            if (!short) {
                                Text(
                                    text = stringResource(R.string.trip_active),
                                    color = AppColors.Muted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            // "Every minute counts" is switched in the sheet of "Change"; here only a small mark while it is on.
                            AnimatedVisibility(visible = everyMinute, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                                EveryMinuteBadge()
                            }
                        }
                        Text(
                            text = stringResource(R.string.trip_to, legs.last().to.plainStopName()),
                            fontWeight = FontWeight.Bold,
                            fontSize = if (short) 17.sp else 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.arrival_at, journey.arrival.clock()),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.Card)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            // Missed it, Change and End: at the foot, or held sideways right below the blue box.
            val tripButtons: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (boarding != null) {
                        SecondaryButton(
                            text = stringResource(R.string.missed_train),
                            onClick = { onMissed(boarding) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp),
                            minHeight = buttonHeight,
                            ground = AppColors.AmberContainer,
                            ink = AppColors.AmberInk,
                        )
                    }
                    // Re-plan, another destination, going on and "Every minute counts" in one sheet.
                    ChangeTripButton(
                        onClick = { actionsOpen = true },
                        modifier = Modifier.weight(1f),
                        minHeight = buttonHeight,
                        shape = if (boarding != null) {
                            RoundedCornerShape(10.dp)
                        } else {
                            RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp)
                        },
                    )
                    // Sharing in plain sight, the one way in: the share sheet with the live link first.
                    ShareTripButton(
                        onClick = { shareOpen = true },
                        size = buttonHeight,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.trip_end),
                        onClick = onEnd,
                        shape = RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp),
                        minHeight = buttonHeight,
                    )
                }
            }
            Spacer(modifier = Modifier.height(BANNER_GAP))
            if (!arrived) {
                TripBanner(
                    step = step,
                    now = now,
                    caption = stringResource(R.string.next_up),
                    journey = journey,
                    // The map frames the trip below the banner and above the sheet.
                    modifier = Modifier.coversMapTop(),
                    // The long press is a shortcut to the sheet of the "Change" button below; no ⋮ of its own.
                    actions = tripActions,
                    menuButton = false,
                )
            }
            // At the destination the banner gives way to the card of the arrival, which holds the buttons.
            // The first time a trip gets there, confetti in its line colours flies up from the check.
            val arrivalId = arrivalKey(journey)
            val celebrate = remember(arrivalId, arrived) { arrived && Celebrations.claim(arrivalId) }
            val haptics = LocalHapticFeedback.current
            LaunchedEffect(celebrate) { if (celebrate) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
            val confettiColors = remember(journey.id) {
                (journey.rides.map { lineColor(it) } + listOf(ARRIVAL_LIME, ARRIVAL_BLUE)).distinct()
            }
            val density = LocalDensity.current
            Box {
                // Not the ColumnScope overload of the surrounding column: called inside a Box.
                androidx.compose.animation.AnimatedVisibility(visible = arrived, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    ArrivedCard(
                        journey = journey,
                        endsAt = journey.arrival.plusMinutes(AFTER_ARRIVAL_MINUTES),
                        onEnd = onEnd,
                        onContinueTo = onContinueTo,
                        onLongPress = { actionsOpen = true },
                        compact = short,
                        modifier = Modifier.coversMapTop(),
                    )
                }
                ConfettiBurst(
                    colors = confettiColors,
                    play = celebrate,
                    // From the middle of the check of the card.
                    origin = { with(density) { Offset((16 + if (short) 22 else 28).dp.toPx(), (if (short) 32 else 44).dp.toPx()) } },
                    modifier = Modifier.matchParentSize(),
                )
            }
            if (!arrived && short) tripButtons()
            // A faster way found on the way: offered until it is taken or put aside, never taken by itself.
            if (faster != null && (everyMinute || shortcutAlert)) {
                FasterCard(
                    offer = faster,
                    onTake = { onTakeFaster(faster) },
                    onIgnore = { onIgnoreFaster(faster) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (notificationsOff) {
                // Said once: a tap puts the note away.
                Text(
                    text = stringResource(R.string.trip_notifications_off),
                    color = AppColors.Ink,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppColors.WarningContainer)
                        .clickable(onClick = onNotificationsNoteSeen)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            if (entries.isEmpty() && warnings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    if (onRecentre != null && !wide) {
                        RoundButton(
                            glyph = Glyph.Locate,
                            description = stringResource(R.string.recentre_trip),
                            onClick = onRecentre,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                val mapCover = LocalMapCover.current
                // The timeline: in a sheet over the map, or on a wide screen in the panel at its side.
                val timeline: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .onPlaced { viewport.coordinates = it }
                            // Short, so a ride head stuck to the top keeps its badge clear of the fade.
                            .fadeEdges(scroll, horizontal = false, length = 10.dp)
                            .verticalScroll(scroll)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WarningSummary(warnings)
                        QuietNotices(quiet)
                        if (entries.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPlaced { cardTop[0] = it.positionInParent().y.roundToInt() }
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(AppColors.Card)
                                    .padding(start = 2.dp, end = 10.dp, top = 2.dp, bottom = 8.dp),
                            ) {
                                entries.forEachIndexed { index, entry ->
                                    key(entry.key) {
                                        val placed = appearModifier(index)
                                            .onPlaced { entryTops[entry.key] = it.positionInParent().y.roundToInt() }
                                        when (entry) {
                                            is TimelineEntry.Walk -> WalkEntry(
                                                leg = entry.leg,
                                                target = entry.target,
                                                over = !now.isBefore(entry.leg.arrival) && walkDetour?.legIndex != entry.index,
                                                onFocusWalk = showWalk,
                                                modifier = placed,
                                                detour = walkDetour?.takeIf { it.legIndex == entry.index },
                                            )

                                            is TimelineEntry.Ride -> {
                                                val leg = entry.leg
                                                val phase = when {
                                                    !now.isBefore(leg.arrival) -> RidePhase.Done
                                                    !now.isBefore(leg.departure) -> RidePhase.Riding
                                                    entry.index == upcomingIndex -> RidePhase.Waiting
                                                    else -> RidePhase.Later
                                                }
                                                val course = leg.tripId?.let { courses[it] }
                                                val feeder = leg.tripId?.let { feeders[it] }
                                                val stops = remember(leg, course) { rideStops(leg, course) }
                                                val approach = remember(leg, course, feeder, phase) {
                                                    if (phase == RidePhase.Waiting) approachOf(leg, course, feeder) else null
                                                }
                                                // Before boarding a train that leads to a change: where to board it.
                                                val following = if (phase == RidePhase.Waiting) rideAfter(journey, leg) else null
                                                val followingCourse = following?.tripId?.let { courses[it] }
                                                val followingStops = remember(following, followingCourse) {
                                                    following?.let { rideStops(it, followingCourse) }.orEmpty()
                                                }
                                                // The first ride from one of many stop positions: the short walk to it first.
                                                if (entry.index == 0 && phase != RidePhase.Done && phase != RidePhase.Riding && hasBoardingPositionMap(leg)) {
                                                    PositionWalkEntry(ride = leg, onFocusWalk = showWalk)
                                                }
                                                RideSection(
                                                    boardingHint = if (following == null) {
                                                        null
                                                    } else {
                                                        {
                                                            CarriageHint(
                                                                arriving = leg,
                                                                next = following,
                                                                arrivingStops = stops,
                                                                nextStops = followingStops,
                                                                arrivingBeyond = remember(leg, course) { runNeighbour(leg, course, after = true) },
                                                                nextBefore = remember(following, followingCourse) {
                                                                    runNeighbour(following, followingCourse, after = false)
                                                                },
                                                                modifier = Modifier.padding(start = RAIL_WIDTH, top = 4.dp, bottom = 4.dp),
                                                            )
                                                        }
                                                    },
                                                    leg = leg,
                                                    phase = phase,
                                                    stops = stops,
                                                    approach = approach,
                                                    now = now,
                                                    focus = if (phase == RidePhase.Riding) focus else null,
                                                    viewport = viewport,
                                                    scroll = scroll,
                                                    modifier = placed,
                                                )
                                            }

                                            is TimelineEntry.Change -> ChangeEntry(
                                                // A re-routed walk of this change times it anew, up to "too late".
                                                change = detouredChange(
                                                    entry.change,
                                                    legs.indexOfFirst { it === entry.change.next },
                                                    walkDetour,
                                                ),
                                                detourMinutes = walkDetour?.takeIf { detour ->
                                                    detour.legIndex >= entry.change.keepLegs &&
                                                        detour.legIndex < legs.indexOfFirst { it === entry.change.next }
                                                }?.minutes,
                                                // The stops of both rides from the runs of their vehicles where the journey has none.
                                                // Keyed by the runs themselves: the map of runs stays the same object
                                                // while runs are added to it, so a key on the map would keep the
                                                // stops from before the runs were loaded.
                                                arrivingStops = entry.change.arriving.let { ride ->
                                                    val course = ride.tripId?.let { courses[it] }
                                                    remember(ride, course) { rideStops(ride, course) }
                                                },
                                                nextStops = entry.change.next.let { ride ->
                                                    val course = ride.tripId?.let { courses[it] }
                                                    remember(ride, course) { rideStops(ride, course) }
                                                },
                                                arrivingBeyond = entry.change.arriving.let { ride ->
                                                    val course = ride.tripId?.let { courses[it] }
                                                    remember(ride, course) { runNeighbour(ride, course, after = true) }
                                                },
                                                nextBefore = entry.change.next.let { ride ->
                                                    val course = ride.tripId?.let { courses[it] }
                                                    remember(ride, course) { runNeighbour(ride, course, after = false) }
                                                },
                                                offer = if (entry.change.keepLegs == nextChange?.keepLegs) alternatives else null,
                                                over = !now.isBefore(entry.change.next.departure),
                                                onFocusWalk = showWalk,
                                                onSwitch = onSwitch,
                                                modifier = placed,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (wide) {
                    GlassSurface(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                    ) {
                        timeline()
                    }
                } else {
                    StageSheet(
                        stage = stage,
                        onStage = { stage = it },
                        onRecentre = onRecentre,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onSheetTop = if (mapCover == null) null else ({ y -> mapCover[1] = y }),
                        // Fully up, as far below the blue box as the box is below the bar on top.
                        topGap = BANNER_GAP,
                    ) {
                        timeline()
                    }
                }
            }
            // Arrived, the card on top holds the buttons ("End trip", "Go on to …").
            if (!arrived && !short) tripButtons()
        }
        // On a wide screen the button that frames the trip again stands on the map, not on the panel.
        if (wide && onRecentre != null) {
            RoundButton(
                glyph = Glyph.Locate,
                description = stringResource(R.string.recentre_trip),
                onClick = onRecentre,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp),
            )
        }
    }
}

/**
 * The end of a followed trip in the companion, in place of the banner of the next step, on
 * glass: a check mark that pops in, "Arrived", the destination, and when the trip got there
 * against the timetable ("arr. 13:52, 2 min early"). "End trip" first, "Go on to …" where
 * [onContinueTo] offers it, and when the trip ends by itself ([endsAt]). A long press opens
 * the sheet of the trip (sharing, another destination). [compact] (a phone held sideways):
 * smaller, the two buttons side by side and no line about the end.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArrivedCard(
    journey: Journey,
    endsAt: OffsetDateTime?,
    onEnd: () -> Unit,
    onContinueTo: (() -> Unit)?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val checkScale by animateFloatAsState(if (shown) 1f else 0.3f, Motion.pop(), label = "arrivedCheckScale")
    val checkAlpha by animateFloatAsState(if (shown) 1f else 0f, Motion.fadeIn(), label = "arrivedCheckAlpha")
    // The tick draws itself once the circle is there.
    val tick by animateFloatAsState(
        if (shown) 1f else 0f,
        androidx.compose.animation.core.tween(durationMillis = 380, delayMillis = 160),
        label = "arrivedTick",
    )
    val last = journey.legs.last()
    val at = stringResource(R.string.arrival_at, journey.arrival.clock())
    val lateBy = last.arrivalDelayMinutes
    val timing = when {
        last.plannedArrival == null -> at
        lateBy < 0 -> stringResource(R.string.arrived_early, at, -lateBy)
        lateBy > 0 -> stringResource(R.string.arrived_late, at, lateBy)
        else -> stringResource(R.string.arrived_on_time, at)
    }
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                    onLongClickLabel = stringResource(R.string.trip_menu),
                )
                .padding(start = 16.dp, end = 16.dp, top = if (compact) 10.dp else 16.dp, bottom = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkAlpha
                        }
                        .size(if (compact) 44.dp else 56.dp)
                        .clip(CircleShape)
                        .background(AppColors.Lime),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckMark(color = AppColors.OnLime, progress = tick, modifier = Modifier.size(if (compact) 22.dp else 28.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.arrived),
                        color = AppColors.Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 19.sp else 22.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = last.to.plainStopName(),
                        color = AppColors.Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timing,
                        color = if (lateBy > 0) AppColors.DelayInk else AppColors.Muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (compact) {
                // Side by side: "End trip" on the right, where the thumb is.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onContinueTo != null) {
                        SecondaryButton(
                            text = stringResource(R.string.retarget_continue),
                            onClick = onContinueTo,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp),
                            minHeight = SHORT_BUTTON_HEIGHT,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.trip_end_action),
                        onClick = onEnd,
                        modifier = Modifier.weight(1f),
                        shape = if (onContinueTo != null) RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp) else RoundedCornerShape(28.dp),
                        minHeight = SHORT_BUTTON_HEIGHT,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.trip_end_action),
                        onClick = onEnd,
                        modifier = Modifier.fillMaxWidth(),
                        shape = if (onContinueTo != null) RoundedCornerShape(28.dp, 28.dp, 10.dp, 10.dp) else RoundedCornerShape(28.dp),
                    )
                    if (onContinueTo != null) {
                        SecondaryButton(
                            text = stringResource(R.string.retarget_continue),
                            onClick = onContinueTo,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp, 10.dp, 28.dp, 28.dp),
                        )
                    }
                }
            }
            if (endsAt != null && !compact) {
                Text(
                    text = stringResource(R.string.trip_auto_end, endsAt.clock()),
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// The colours the confetti of an arrival always has besides the lines of the trip.
private val ARRIVAL_LIME = Color(0xFFC8F16B)
private val ARRIVAL_BLUE = Color(0xFF1B4FD8)

/** What makes a followed trip one trip for its celebration: where and when it started, and where it ends. */
internal fun arrivalKey(journey: Journey): String =
    "${(journey.legs.first().plannedDeparture ?: journey.legs.first().departure).toEpochSecond()}|${journey.legs.last().to}"

/** A tick, drawn up to [progress] (0 nothing, 1 whole): the short stroke first, then the long one. */
@Composable
internal fun CheckMark(color: Color, modifier: Modifier = Modifier, progress: Float = 1f) {
    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val stroke = w * 0.14f
            val a = Offset(w * 0.18f, w * 0.52f)
            val b = Offset(w * 0.42f, w * 0.76f)
            val c = Offset(w * 0.84f, w * 0.28f)
            // The short stroke is about a third of the whole tick.
            val first = (progress / 0.35f).coerceIn(0f, 1f)
            val second = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
            if (first > 0f) drawLine(color, a, a + (b - a) * first, strokeWidth = stroke, cap = StrokeCap.Round)
            if (second > 0f) drawLine(color, b, b + (c - b) * second, strokeWidth = stroke, cap = StrokeCap.Round)
        },
    )
}

/**
 * A faster way to the destination: how much sooner, where to change and into what, and the
 * new arrival, with the buttons to take it or put it aside.
 */
@Composable
internal fun FasterCard(offer: FasterOffer, onTake: () -> Unit, onIgnore: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.BlueContainer)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = FasterWatch.offerTitle(context, offer) + ": " + FasterWatch.offerText(context, offer),
            color = AppColors.OnBlueContainer,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        Text(
            text = stringResource(R.string.faster_arrival, offer.arrival.clock(), offer.before.clock()),
            color = AppColors.OnBlueContainer,
            fontSize = 13.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            SecondaryButton(
                text = stringResource(R.string.faster_ignore_action),
                onClick = onIgnore,
                shape = RoundedCornerShape(16.dp),
                minHeight = 40.dp,
            )
            PrimaryButton(
                text = stringResource(R.string.faster_take_action),
                onClick = onTake,
                shape = RoundedCornerShape(16.dp),
                minHeight = 40.dp,
            )
        }
    }
}

/** The time for a change: "12 min to change", from an hour on "1 h 37 min to change" (see durationLabel). */
@Composable
private fun changeLabel(minutes: Long): String =
    if (minutes < 60) stringResource(R.string.change_minutes, minutes) else stringResource(R.string.change_span, durationLabel(minutes))
