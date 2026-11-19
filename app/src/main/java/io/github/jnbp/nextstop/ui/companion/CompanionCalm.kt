package io.github.jnbp.nextstop.ui.companion

import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.nextWalkKey
import io.github.jnbp.nextstop.data.legSwapKey
import io.github.jnbp.nextstop.ui.FadeSwap
import io.github.jnbp.nextstop.ui.LocalWalkMapNext
import io.github.jnbp.nextstop.ui.changeHasMiniMap
import io.github.jnbp.nextstop.ui.hasWalkMap
import io.github.jnbp.nextstop.ui.hasBoardingPositionMap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import io.github.jnbp.nextstop.ui.pinRoom
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import io.github.jnbp.nextstop.data.rideTown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.jnbp.nextstop.ui.Chevron
import io.github.jnbp.nextstop.ui.StopMenuBox
import io.github.jnbp.nextstop.ui.LocalMiniMapInPlace
import io.github.jnbp.nextstop.ui.LocalWalkCardStyle
import io.github.jnbp.nextstop.ui.WalkCardStyle
import io.github.jnbp.nextstop.ui.durationLabel
import io.github.jnbp.nextstop.ui.minutesBetween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.StopRowSpec
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.railwayStationName
import io.github.jnbp.nextstop.data.rowPosition
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.ui.Alternatives
import io.github.jnbp.nextstop.ui.CarriageHint
import io.github.jnbp.nextstop.ui.CompanionChangePart
import io.github.jnbp.nextstop.ui.ChangeChoiceArea
import io.github.jnbp.nextstop.ui.ConnectionChipState
import io.github.jnbp.nextstop.ui.CompanionConnectionChoice
import io.github.jnbp.nextstop.ui.CompanionPositionWalkPart
import io.github.jnbp.nextstop.ui.CompanionWalkPart
import io.github.jnbp.nextstop.ui.DestinationHint
import io.github.jnbp.nextstop.ui.LineBadge
import io.github.jnbp.nextstop.ui.WalkDetour
import io.github.jnbp.nextstop.ui.clock
import androidx.compose.ui.text.AnnotatedString
import io.github.jnbp.nextstop.ui.DelayedTime
import io.github.jnbp.nextstop.ui.delayedTimeText
import io.github.jnbp.nextstop.ui.joinedText
import io.github.jnbp.nextstop.data.boardingTime
import io.github.jnbp.nextstop.data.alightingTime
import io.github.jnbp.nextstop.ui.connectionChoiceOn
import io.github.jnbp.nextstop.ui.rideAfter
import io.github.jnbp.nextstop.ui.lineColor
import io.github.jnbp.nextstop.ui.platformLabel
import io.github.jnbp.nextstop.ui.rememberAnimationsOn
import io.github.jnbp.nextstop.ui.theme.AppColors
import io.github.jnbp.nextstop.data.Journey
import java.time.OffsetDateTime
import kotlin.math.floor
import io.github.jnbp.nextstop.data.railReplacement

/**
 * The calm companion ("Ruhig", the default): no map, one column on the plain ground. On top
 * the blue box ([hero]), below it what the companion knows beyond the times ([extras]: the
 * position, a change of platform, the wake-up alarm, a faster way, notices), then the whole
 * journey as one timeline ([CompanionTimeline]) with the small maps of its walks.
 * [bottomRoom] keeps the end of the column clear of the floating bar of actions.
 */
@Composable
internal fun CalmCompanion(
    model: CompanionModel,
    alternatives: Alternatives?,
    onSwitch: (Journey) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    hero: @Composable () -> Unit,
    extras: @Composable () -> Unit,
    scroll: ScrollState,
    bodyModifier: Modifier,
    bottomRoom: androidx.compose.ui.unit.Dp,
    now: OffsetDateTime,
    walkDetour: WalkDetour?,
) {
    // The blue box stays at the top while the timeline scrolls beneath it (#156), so the next
    // step is always in view; once the timeline is scrolled it settles a little smaller and
    // lifts with a soft shadow.
    val animate = io.github.jnbp.nextstop.ui.rememberAnimationsOn()
    val scrolled by remember(scroll) { derivedStateOf { scroll.value > 0 } }
    val lift by animateFloatAsState(
        if (scrolled) 1f else 0f,
        if (animate) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "heroLift",
    )
    // The timeline scrolls beneath the blue box and fades out there (#245, as under the status
    // line of the sheet over the map): the box lies over the top of the list, which leaves room
    // for it at rest. The box is measured first; its height is the list's room.
    val heroRoom = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val heroGap = with(density) { HERO_GAP.toPx() }
    val heroFade = io.github.jnbp.nextstop.ui.rememberPinFade(
        top = { heroGap },
        // The box settles a little smaller from its top edge while the timeline is scrolled.
        bottom = { if (heroRoom.intValue <= 0) 0f else heroGap + (heroRoom.intValue - 2 * heroGap).coerceAtLeast(0f) * (1f - HERO_SHRINK * lift) },
        scrolled = { scroll.value },
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .zIndex(1f)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    heroRoom.intValue = placeable.height
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .padding(start = 12.dp, end = 12.dp, top = HERO_GAP, bottom = HERO_GAP)
                .graphicsLayer {
                    val scale = 1f - HERO_SHRINK * lift
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    shadowElevation = 6.dp.toPx() * lift
                    shape = RoundedCornerShape(28.dp)
                    clip = false
                },
        ) {
            hero()
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(heroFade)
                .verticalScroll(scroll)
                .pinRoom { heroRoom.intValue }
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomRoom),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The small maps open larger in place and the walk cards unfold them: the calm view stays calm (#61, #81).
            // One form of the sun chip on the whole page (#236).
            val sunChipPage = remember(model.journey.id) { io.github.jnbp.nextstop.data.SunChipPage() }
            CompositionLocalProvider(
                LocalMiniMapInPlace provides true,
                LocalWalkCardStyle provides WalkCardStyle.Calm,
                io.github.jnbp.nextstop.ui.LocalSunChipPage provides sunChipPage,
            ) {
                Column(modifier = bodyModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    extras()
                    CompanionTimeline(model, alternatives, onSwitch, onFocusWalk, now = now, maps = true, walkDetour = walkDetour)
                    // What the colours of the times mean and where the data comes from (#277b).
                    io.github.jnbp.nextstop.ui.TimesFooter(model.journey, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                }
            }
        }
    }
}

// How much smaller the sticky blue box gets while the timeline is scrolled (#156).
private const val HERO_SHRINK = 0.03f

// The room above and below the sticky blue box.
private val HERO_GAP = 4.dp

/**
 * The whole journey as one timeline, top to bottom, shared by the calm view and the sheet over
 * the map: every ride in its own card with its stops (see [RideCard]), between two rides their
 * change with the walk, the hints and the connection picker, and the walks at the start and
 * the end. The picker is open at the change the connecting departures are loaded for (the
 * next one, or one picked by hand); every other change ahead offers "Anschluss wechseln".
 * [maps] shows the small maps of the walks (the calm view; over the big map they are there).
 */
@Composable
internal fun CompanionTimeline(
    model: CompanionModel,
    alternatives: Alternatives?,
    onSwitch: (Journey) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    now: OffsetDateTime,
    maps: Boolean,
    walkDetour: WalkDetour?,
) {
    val journey = model.journey
    // Read while composing (not remembered): picking another change shows at once.
    val active = CompanionOffers.activeKeepLegs(journey, now)
    val opened = CompanionOffers.openKeepLegs(journey)
    val folded = CompanionOffers.foldedKeepLegs(journey)
    // "Anschlusswahl im Begleiter" (#138): folded, all open, or the next one open.
    val pickerMode = CompanionLayout.pickers
    val choiceOn = connectionChoiceOn.value
    val firstRide = model.rides.firstOrNull()?.legIndex
    // A stay at a place on the way (a via with minutes) stands between the legs before and after it, as in the detail.
    val stay = journey.via?.takeIf { it.minutes > 0 }
    // "Nur nächste ausgeklappt" (#202): the next walk or change not yet over with a small map
    // (also the walk to the stop position before the first ride, drawn with that ride).
    val nextWalk = remember(journey, model.rows, OsmStations.loaded) {
        val legs = journey.legs
        nextWalkKey(model.rows, key = { it.key }, over = { row ->
            when (row) {
                is JourneyRow.Walk -> row.over
                is JourneyRow.Change -> row.over
                is JourneyRow.Ride -> false
            }
        }, hasMap = { row ->
            when (row) {
                is JourneyRow.Walk -> hasWalkMap(legs[row.legIndex])
                is JourneyRow.Change -> changeHasMiniMap(legs[row.arrivingIndex], legs[row.nextIndex], row.walkIndex?.let { legs[it] })
                is JourneyRow.Ride -> row.legIndex == firstRide && row.phase == JourneyRidePhase.Next && hasBoardingPositionMap(legs[row.legIndex])
            }
        })
    }
    model.rows.forEach { row ->
        val rowLeg = when (row) {
            is JourneyRow.Walk -> row.legIndex
            is JourneyRow.Ride -> row.legIndex
            is JourneyRow.Change -> -1
        }
        if (stay != null && stay.afterLegs == rowLeg && rowLeg > 0) {
            key("stay$rowLeg") {
                Text(
                    text = stringResource(R.string.via_stay_row, stay.minutes, io.github.jnbp.nextstop.data.journeyStopName(journey, stay.place.name)),
                    color = AppColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(start = CONNECTOR_INDENT + LINK_INDENT, top = 2.dp, bottom = 2.dp),
                )
            }
        }
        key(row.key) {
            CompositionLocalProvider(LocalWalkMapNext provides (row.key == nextWalk)) {
            when (row) {
                // Another connection picked or another way taken: a walk or ride card whose leg
                // changed crossfades into the new one (the old one drawn from its own journey) and its
                // height glides, so the rows below move along; the change cards update in place.
                is JourneyRow.Walk -> FadeSwap(target = journey, contentKey = { shownJourney -> legSwapKey(shownJourney.legs, row.legIndex) }) { shownJourney ->
                    CompanionWalkPart(
                        journey = shownJourney,
                        index = row.legIndex,
                        over = row.over,
                        endsTrip = row.endsTrip,
                        onFocusWalk = onFocusWalk,
                        maps = maps,
                        detour = walkDetour,
                        modifier = Modifier.padding(start = CONNECTOR_INDENT),
                    )
                }
                is JourneyRow.Ride -> if (model.rides.any { it.legIndex == row.legIndex }) FadeSwap(target = model, contentKey = { shownModel -> legSwapKey(shownModel.journey.legs, row.legIndex) }) { shownModel ->
                    val ride = shownModel.rides.firstOrNull { it.legIndex == row.legIndex }
                    // The parts of a ride as the timeline spaces its rows.
                    if (ride != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The first ride from one of many stop positions: the short walk to it first.
                        if (maps && row.legIndex == firstRide && row.phase == JourneyRidePhase.Next) {
                            CompanionPositionWalkPart(ride.leg, onFocusWalk, modifier = Modifier.padding(start = CONNECTOR_INDENT))
                        }
                        // Starting at a station: the way inside it to the platform, folded (#87).
                        if (row.legIndex == firstRide && row.phase == JourneyRidePhase.Next && shownModel.journey.legs.take(row.legIndex).none { it.walking }) {
                            io.github.jnbp.nextstop.ui.PlatformWalkPart(ride.leg, onFocusWalk, maps = maps, modifier = Modifier.padding(start = CONNECTOR_INDENT))
                        }
                        RideCard(ride = ride, phase = row.phase, journey = shownModel.journey, model = shownModel)
                    }
                }
                is JourneyRow.Change -> {
                    val change = model.changes.firstOrNull { it.keepLegs == row.keepLegs }
                    if (change != null) {
                        val show = pickerShow(row.keepLegs, row.over, active, opened, pickerMode, folded)
                        Column(modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
                            // The blue change card; under it, on the page ground and in line with it (B3),
                            // the chips "Anschluss" and "Andere Wege" with their shared row (#243).
                            CompanionChangePart(
                                journey = journey,
                                change = change,
                                courses = model.courses,
                                offer = null,
                                over = row.over,
                                onFocusWalk = onFocusWalk,
                                onSwitch = onSwitch,
                                maps = maps,
                                detour = walkDetour,
                                modifier = Modifier.padding(start = CONNECTOR_INDENT),
                                below = {
                                    val picker = show != PickerShow.None && choiceOn
                                    val open = show == PickerShow.Open
                                    // Completely different ways from this change on, on a tap (#164).
                                    val ways = !row.over
                                    // Two chips under the card (#243), "Anschluss" and "Andere Wege", each
                                    // with its own section below (Variante 2: tiles, then a list of ways).
                                    ChangeChoiceArea(
                                        journey = journey,
                                        change = change,
                                        now = now,
                                        connection = if (!picker) {
                                            null
                                        } else {
                                            ConnectionChipState(
                                                on = open,
                                                // A toggle in every setting, "Alle ausgeklappt" too: it only starts open (r7plan).
                                                onToggle = { if (open) CompanionOffers.fold(journey, row.keepLegs) else CompanionOffers.choose(journey, row.keepLegs) },
                                            )
                                        },
                                        ways = ways,
                                        onTakeWay = null,
                                        modifier = Modifier.padding(end = 4.dp),
                                    ) {
                                        // The departures as loaded for this change (nothing while they are
                                        // loaded for another one or none).
                                        CompanionConnectionChoice(journey, change, alternatives, onSwitch)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

// The classic parts between the cards (changes, walks) draw their rail at half of their own
// rail column; moved in this far, it lines up with the rail of the stops in the cards.
private val CONNECTOR_INDENT = 6.dp

// The small link of a change lines up with its text (the rail column of the classic parts).
private val LINK_INDENT = 46.dp

// Every stop of the ride takes one row of this height, so the rail and the dot are drawn without measuring.
private val STOP_ROW = 38.dp

// The column on the left that holds the rail, and where the rail runs in it.
private val RAIL_COLUMN = 40.dp
private val RAIL_X = 20.dp

/**
 * One ride of the journey in its card: its badge, where it goes and the hints of the ride
 * (occupancy, sun side), then its stops on a rail in the colour of the line. On board, stops
 * passed are grey and the traveller is a dot on the rail between the stops; the ride to board
 * next says when it leaves and where to board for the change after it; a later ride shows its
 * first and last stop; a ride that is over is pale and shows its stops only on a tap. Middle
 * stops fold into one row ("2 more stops ▾") that unfolds on a tap; a tap on the head (with its
 * chevron) unfolds or folds all of them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RideCard(
    ride: CompanionRide,
    phase: JourneyRidePhase,
    journey: Journey,
    model: CompanionModel,
    modifier: Modifier = Modifier,
) {
    val leg = ride.leg
    var expanded by remember(leg.tripId, ride.legIndex) { mutableStateOf(false) }
    val start = if (ride.position < 0f) -1 else floor(ride.position).toInt()
    // Built anew only when a stop is passed or the fold is opened, not on every tick.
    val rows = remember(ride.stops.size, start, expanded, phase) { journeyStopRows(ride.stops.size, ride.position, phase, expanded) }
    // Whether a tap on the head has stops to show or hide: some are folded, or the ride is over.
    val foldable = remember(ride.stops.size, start, phase) {
        phase == JourneyRidePhase.Done ||
            journeyStopRows(ride.stops.size, ride.position, phase, expanded = true).size !=
            journeyStopRows(ride.stops.size, ride.position, phase, expanded = false).size
    }
    val onBoard = phase == JourneyRidePhase.Riding
    val done = phase == JourneyRidePhase.Done
    val dotRow = if (onBoard) rowPosition(rows, ride.position) else Float.NaN
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (done) DONE_ALPHA else 1f)
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.Card)
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        // The head unfolds and folds the stops, with a chevron at its end, as in 0.1.2.
        val headLabel = stringResource(if (expanded) R.string.stops_hide else R.string.stops_show)
        val headState = stringResource(if (expanded) R.string.companion_stops_open else R.string.companion_stops_folded)
        val courseLabel = stringResource(R.string.line_course)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The ripple's corner is the badge's: a larger one cut the badge's left end (#121).
                .clip(RoundedCornerShape(io.github.jnbp.nextstop.ui.LINE_BADGE_CORNER))
                .then(if (foldable) Modifier.semantics { stateDescription = headState } else Modifier)
                // A tap folds the stops; a long press opens the course of the line, as in the detail.
                .combinedClickable(
                    enabled = foldable || leg.tripId != null,
                    onClickLabel = if (foldable) headLabel else null,
                    role = Role.Button,
                    onLongClickLabel = if (leg.tripId != null) courseLabel else null,
                    onLongClick = if (leg.tripId != null) ({ io.github.jnbp.nextstop.ui.CourseRequests.open(leg) }) else null,
                    onClick = { if (foldable) expanded = !expanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineBadge(leg)
            HGap(10.dp)
            Column(modifier = Modifier.weight(1f)) {
                // The whole name with the train number ("FEX 021857") where the badge leaves it out, as in the detail.
                val whole = if (leg.railReplacement != null) null else io.github.jnbp.nextstop.ui.fullLineName(leg.line)
                Text(
                    text = listOfNotNull(whole, towards(leg)).joinToString(" · "),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    // One line, never wrapping (#168).
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!onBoard) {
                    val platform = platformLabel(leg.departurePlatform, leg.product)
                    // ab · Gleis · an · Dauer (#92), the times as the delay setting says (#227).
                    val first = if (done) AnnotatedString(stringResource(R.string.r4c_ride_done)) else delayedTimeText(R.string.companion_departs_at, boardingTime(leg), AppColors.Muted)
                    val duration = durationLabel(minutesBetween(leg.departure, leg.arrival).coerceAtLeast(0))
                    val arrival = delayedTimeText(R.string.arrival_at, alightingTime(leg), AppColors.Muted)
                    Text(
                        text = joinedText(listOfNotNull(first, platform?.let { AnnotatedString(it) }, arrival, AnnotatedString(duration))),
                        color = AppColors.Muted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (foldable) Chevron(open = expanded, modifier = Modifier.padding(start = 6.dp))
        }
        // Parity with the classic view and the detail (docs/companion-parity.md): a cancelled ride
        // and a rail replacement say so in words, not only by colour or badge.
        if (leg.cancelled && !done) {
            Text(
                text = stringResource(R.string.cancelled_ride_tag),
                color = AppColors.OnDelayRed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 4.dp, top = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.DelayRed)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        leg.railReplacement?.let { replacement ->
            io.github.jnbp.nextstop.ui.RailReplacementTag(replacement, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        // A ride that leads to a change: where to board it (the carriage for a short change); the
        // last ride to a place: the way out. As in the detail of the connection, at the start of
        // every ride still ahead or under way, not only the next one (#127): on board one can
        // still move to the right part of the train. One row with occupancy, step-free, bicycles
        // and the sunny side as small chips (#220).
        if (rideHintShown(phase)) {
            val following = remember(journey, leg) { rideAfter(journey, leg) }
            io.github.jnbp.nextstop.ui.CompactRideHints(leg, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) { hintModifier ->
                if (following != null) {
                    val followingStops = model.rides.firstOrNull { it.leg === following }?.stops ?: following.stopovers
                    CarriageHint(
                        arriving = leg,
                        next = following,
                        arrivingStops = ride.stops,
                        nextStops = followingStops,
                        modifier = hintModifier,
                    )
                } else if (journey.legs.lastOrNull()?.let { it.walking && it.toPlace?.kind != PlaceKind.Stop } == true) {
                    DestinationHint(journey = journey, ride = leg, modifier = hintModifier)
                }
            }
        }
        // One vehicle that changes its line on the way: no change, only a note (as in the detail).
        leg.throughRuns.lastOrNull { it.lineChanges }?.let { run ->
            Text(
                text = stringResource(
                    R.string.stay_seated_continues_as,
                    io.github.jnbp.nextstop.data.rapidLineName(run.line, leg.product) ?: run.line.orEmpty(),
                    io.github.jnbp.nextstop.data.journeyStopName(journey, run.atName),
                ),
                color = AppColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
        Gap(6.dp)
        if (done && !expanded) {
            Text(
                text = pluralStringResource(R.plurals.r4c_ride_stops, ride.stops.size, ride.stops.size) + " ▾",
                color = AppColors.Blue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = RAIL_COLUMN - 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        } else {
            StopRail(
                rows = rows,
                stops = ride.stops,
                color = lineColor(leg),
                passedUpTo = when (phase) {
                    JourneyRidePhase.Riding -> ride.position
                    JourneyRidePhase.Done -> ride.stops.size.toFloat()
                    else -> -1f
                },
                dotRow = dotRow,
                onUnfold = { expanded = true },
                // Where to board and where one arrives, at their rows (#153), not only in the hero.
                nameOf = { name -> io.github.jnbp.nextstop.data.rideStopName(leg, name, leg.rideTown) },
                boardPlatform = platformLabel(leg.departurePlatform, leg.product),
                alightPlatform = platformLabel(leg.arrivalPlatform, leg.product),
                railway = leg.product == "express",
                // The ends show the ride's own live departure and arrival, as its head (#226).
                timeOf = { index -> io.github.jnbp.nextstop.data.rideStopTime(leg, ride.stops, index) },
            )
            // The course of the line as a visible link while the stops are open, as in the detail:
            // the long press on the ride's head is only a shortcut, never the only way.
            if (expanded && leg.tripId != null) {
                Text(
                    text = stringResource(R.string.line_course) + " ›",
                    color = AppColors.Blue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(start = RAIL_COLUMN - 6.dp, top = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { io.github.jnbp.nextstop.ui.CourseRequests.open(leg) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// A ride that is over is drawn this pale.
private const val DONE_ALPHA = 0.6f

/**
 * The stops of [rows] on a rail in [color]: the part before [passedUpTo] grey, the dot of the
 * traveller at [dotRow] (row units, NaN for none), which glides along as it moves.
 */
@Composable
private fun StopRail(
    rows: List<StopRowSpec>,
    stops: List<Stopover>,
    color: Color,
    passedUpTo: Float,
    dotRow: Float,
    onUnfold: () -> Unit,
    railway: Boolean = false,
    boardPlatform: String? = null,
    alightPlatform: String? = null,
    nameOf: (String) -> String = { it.plainStopName() },
    timeOf: (Int) -> io.github.jnbp.nextstop.data.StopTime? = { index -> stops.getOrNull(index)?.let { io.github.jnbp.nextstop.data.stopTimeBetween(it) } },
) {
    val animate = rememberAnimationsOn()
    val dot = remember { Animatable(if (dotRow.isNaN()) 0f else dotRow) }
    LaunchedEffect(dotRow, animate) {
        if (dotRow.isNaN()) return@LaunchedEffect
        if (animate) dot.animateTo(dotRow, tween(DOT_GLIDE_MILLIS)) else dot.snapTo(dotRow)
    }
    val last = stops.lastIndex
    val grey = AppColors.Muted.copy(alpha = 0.35f)
    val card = AppColors.Card
    val lime = AppColors.Lime
    val ink = AppColors.Ink
    val blue = AppColors.Blue
    val hasDot = !dotRow.isNaN()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (rows.isEmpty()) return@drawBehind
                val row = STOP_ROW.toPx()
                val x = RAIL_X.toPx()
                val top = row / 2f
                val bottom = rows.lastIndex * row + row / 2f
                val dotY = if (hasDot) dot.value * row + row / 2f else top
                val stroke = 5.dp.toPx()
                if (hasDot && dotY > top) {
                    drawLine(grey, Offset(x, top), Offset(x, dotY), strokeWidth = stroke, cap = StrokeCap.Round)
                }
                drawLine(color, Offset(x, if (hasDot) dotY.coerceAtLeast(top) else top), Offset(x, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
                rows.forEachIndexed { k, spec ->
                    val y = k * row + row / 2f
                    when (spec) {
                        is StopRowSpec.Folded -> {
                            for (n in -1..1) drawCircle(card, radius = 2.dp.toPx(), center = Offset(x, y + n * 6.dp.toPx()))
                        }
                        is StopRowSpec.Single -> if (spec.index == last) {
                            drawCircle(lime, radius = 8.dp.toPx(), center = Offset(x, y))
                            drawCircle(ink, radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
                        } else {
                            val passed = spec.index <= passedUpTo
                            drawCircle(card, radius = 5.dp.toPx(), center = Offset(x, y))
                            drawCircle(if (passed) grey else color, radius = 5.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.5.dp.toPx()))
                        }
                    }
                }
                if (hasDot) {
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, dotY))
                    drawCircle(blue, radius = 6.dp.toPx(), center = Offset(x, dotY))
                }
            },
    ) {
        rows.forEach { spec ->
            key(spec) {
                when (spec) {
                    is StopRowSpec.Single -> StopLine(
                        stop = stops[spec.index],
                        passed = spec.index <= passedUpTo && spec.index != last,
                        destination = spec.index == last,
                        railway = railway,
                        platform = stopRowPlatform(spec.index, last, boardPlatform, alightPlatform),
                        nameOf = nameOf,
                        time = timeOf(spec.index),
                        // The stops between open their menu on a tap or a long press (#186).
                        between = io.github.jnbp.nextstop.ui.stopMenuOpening(spec.index, last) == io.github.jnbp.nextstop.ui.StopMenuOpening.Row,
                    )
                    is StopRowSpec.Folded -> StopFoldRow(
                        // Stops already passed (#187) or still ahead.
                        text = pluralStringResource(if (spec.passed) R.plurals.r6live_stops_ridden else R.plurals.companion_more_stops, spec.count, spec.count) + " ▾",
                        onClick = onUnfold,
                    )
                }
            }
        }
    }
}

/** A full-width row on the rail that unfolds the stops hidden in it. */
@Composable
private fun StopFoldRow(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(STOP_ROW)
            .clickable(onClick = onClick)
            .padding(start = RAIL_COLUMN),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = AppColors.Blue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

// How long the dot of the traveller glides to a new place on the rail.
private const val DOT_GLIDE_MILLIS = 900

/** One stop on the rail: its name and time; grey once passed, bold for the destination. */
@Composable
private fun StopLine(
    stop: Stopover,
    passed: Boolean,
    destination: Boolean,
    railway: Boolean = false,
    platform: String? = null,
    nameOf: (String) -> String = { it.plainStopName() },
    time: io.github.jnbp.nextstop.data.StopTime? = null,
    between: Boolean = false,
) {
    // A tap on the stop opens its menu (#139); on a stop between a long press does too (#186).
    StopMenuBox(place = stop.place, modifier = Modifier.fillMaxWidth(), longPress = between) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(STOP_ROW)
                .padding(start = RAIL_COLUMN),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                // A long-distance ride names its stations as the railway does (#93).
                // Other stops say their town where it is another than the ride's ("Kirchstr. · Zagelsdorf").
                text = if (railway) railwayStationName(stop.name) else nameOf(stop.name),
                color = if (passed) AppColors.Muted else AppColors.Ink,
                fontWeight = if (destination) FontWeight.ExtraBold else FontWeight.Normal,
                fontSize = if (destination) 16.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (platform != null) {
                Text(
                    text = platform,
                    color = AppColors.Muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            // One time column in every row, tabular figures: the platforms stand on one line (#237).
            io.github.jnbp.nextstop.ui.StopTimeColumn {
                if (time != null) {
                    // As the setting "Verspätungen" says, green on time, red late, plain without live data (#226, #227).
                    DelayedTime(
                        time = time,
                        passed = passed,
                        fontWeight = if (destination) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
