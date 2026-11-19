package io.github.jnbp.nextstop.ui.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ChangeReach
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.changeReach
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.shownRideStop
import io.github.jnbp.nextstop.ui.FreshnessChip
import io.github.jnbp.nextstop.ui.GlassSurface
import io.github.jnbp.nextstop.ui.Glyph
import io.github.jnbp.nextstop.ui.LiveFreshness
import io.github.jnbp.nextstop.ui.LiveSource
import io.github.jnbp.nextstop.ui.RoundButton
import io.github.jnbp.nextstop.ui.SecondaryButton
import io.github.jnbp.nextstop.ui.StepKind
import io.github.jnbp.nextstop.ui.RollingText
import io.github.jnbp.nextstop.ui.FadeSwap
import io.github.jnbp.nextstop.ui.TripStep
import io.github.jnbp.nextstop.ui.changeText
import io.github.jnbp.nextstop.ui.durationClock
import io.github.jnbp.nextstop.ui.speaks
import io.github.jnbp.nextstop.ui.spokenDuration
import io.github.jnbp.nextstop.ui.unitGap
import io.github.jnbp.nextstop.ui.unitSize
import io.github.jnbp.nextstop.ui.minutesBetween
import io.github.jnbp.nextstop.ui.rideAfter
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import io.github.jnbp.nextstop.ui.CarriageMark
import io.github.jnbp.nextstop.ui.carriageText
import io.github.jnbp.nextstop.ui.shortCarriagePosition
import io.github.jnbp.nextstop.ui.stepLabel
import io.github.jnbp.nextstop.ui.walkBetween
import io.github.jnbp.nextstop.ui.clock
import io.github.jnbp.nextstop.ui.platformLabel
import io.github.jnbp.nextstop.ui.rememberAnimationsOn
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.OffsetDateTime
import kotlinx.coroutines.delay

/*
 * The parts the calm companion and the companion with the map share: the bar on top, the blue
 * box of the next step with its progress and chips, the floating bar of actions and its ⋯ menu.
 * Each reads only the CompanionModel it is given (see companionModel).
 */

/**
 * The bar on top: back, a flag and the destination, "arr. 10:12 · Live 12 s ago" with the mark of what
 * the times rest on inside the chip ([SourceGlyph], a tap or a long press says it in words), the map button (only where
 * [onToggleMap] is given) and the one ⋯ of the companion. On glass over the map ([onMap]), on
 * the plain ground otherwise.
 */
@Composable
internal fun CompanionTopBar(
    model: CompanionModel,
    onMap: Boolean,
    onBack: () -> Unit,
    onToggleMap: (() -> Unit)?,
    menu: CompanionMenu?,
    modifier: Modifier = Modifier,
    everyMinute: Boolean = false,
    onEveryMinute: ((Boolean) -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton(glyph = Glyph.Back, description = stringResource(R.string.back), onClick = onBack, solid = true)
            Column(modifier = Modifier.weight(1f)) {
                // A small flag stands for "nach" and saves the width of the word; read as "nach".
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val toWord = stringResource(R.string.r6c_dest_icon)
                    io.github.jnbp.nextstop.ui.GlyphIcon(
                        Glyph.Flag,
                        color = AppColors.Ink,
                        size = 16.dp,
                        modifier = Modifier.semantics { contentDescription = toWord },
                    )
                    Text(
                        text = model.journey.legs.last().to.plainStopName(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.arrival_at, model.journey.arrival.clock()),
                        color = AppColors.Muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Gives way first while the bolt of "Jede Minute zählt" shows its word (#259).
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // How old the live times of the trip are; a tap asks for them at once. What
                    // the times rest on is a small mark inside it (#62), "Live · jetzt" fits (#95).
                    FreshnessChip(
                        source = LiveSource.Trip,
                        onRefresh = { LiveFreshness.ask(LiveSource.Trip) },
                        minHeight = 24.dp,
                        shortJustNow = true,
                        trailing = { SourceGlyph(model) },
                    )
                    if (LiveFreshness.at(LiveSource.Trip) == 0L) SourceGlyph(model)
                    // "Jede Minute zählt" on for this trip: a small bolt, popping in with its word (#259).
                    EveryMinuteTopMark(on = everyMinute, onSwitch = onEveryMinute)
                }
            }
            if (onToggleMap != null) {
                MapToggleButton(onMap = onMap, onClick = onToggleMap)
            }
            if (menu != null) CompanionMoreButton(menu = menu, solid = true)
        }
    }
    if (onMap) {
        GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) { content() }
    } else {
        Box(modifier = modifier.fillMaxWidth()) { content() }
    }
}

/** The map button: a map while the map is away, filled blue while it is shown. */
@Composable
private fun MapToggleButton(onMap: Boolean, onClick: () -> Unit) {
    val ground by animateColorAsState(if (onMap) AppColors.Blue else AppColors.Card, label = "mapToggle")
    val ink by animateColorAsState(if (onMap) AppColors.OnBlue else AppColors.Ink, label = "mapToggleInk")
    val label = stringResource(if (onMap) R.string.companion_map_hide else R.string.companion_map_show)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(ground)
            .clickable(onClickLabel = label, onClick = onClick)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        io.github.jnbp.nextstop.ui.GlyphIcon(Glyph.Map, color = ink, size = 22.dp)
    }
}

/**
 * What the one ⋯ of the new views (top right) offers (#63): the wake-up alarm ([onWake], its
 * small sheet), sharing ([onShare]), the view with or without the map ([onToggleView], where
 * the map button is offered; [withMap] says which one is shown) and, last and in red, ending
 * the trip. A null action is not offered.
 */
internal class CompanionMenu(
    val onShare: (() -> Unit)?,
    val onEnd: () -> Unit,
    val onWake: (() -> Unit)? = null,
    val onToggleView: (() -> Unit)? = null,
    val withMap: Boolean = false,
)

/** The ⋯ button with its menu, which opens right at the button. */
@Composable
internal fun CompanionMoreButton(menu: CompanionMenu, solid: Boolean, size: Dp = 48.dp) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(
            glyph = Glyph.More,
            description = stringResource(R.string.companion_more),
            onClick = { open = true },
            solid = solid,
            size = size,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu.onWake?.let { wake ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.r5c_menu_wake)) },
                    leadingIcon = { io.github.jnbp.nextstop.ui.GlyphIcon(Glyph.Alarm, color = AppColors.Ink, size = 20.dp) },
                    onClick = {
                        open = false
                        wake()
                    },
                )
            }
            menu.onShare?.let { share ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trip_share)) },
                    leadingIcon = { io.github.jnbp.nextstop.ui.GlyphIcon(Glyph.LiveShare, color = AppColors.Ink, size = 20.dp) },
                    onClick = {
                        open = false
                        share()
                    },
                )
            }
            menu.onToggleView?.let { toggle ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (menu.withMap) R.string.r5c_menu_view_calm else R.string.r5c_menu_view_map)) },
                    leadingIcon = { io.github.jnbp.nextstop.ui.GlyphIcon(Glyph.Map, color = AppColors.Ink, size = 20.dp) },
                    onClick = {
                        open = false
                        toggle()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.trip_end_action), color = AppColors.DelayRed, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { io.github.jnbp.nextstop.ui.GlyphIcon(Glyph.Close, color = AppColors.DelayRed, size = 20.dp) },
                onClick = {
                    open = false
                    menu.onEnd()
                },
            )
        }
    }
}

/**
 * The floating bar of actions at the foot, on glass: "Missed it" (amber, only while it matters:
 * [missedFrom] is the stop to search on from, null hides it; it makes room softly), "Change
 * trip" and the share button (#66). No ⋯ and no alarm here: the one menu is in the bar on top, the
 * wake-up alarm a chip in the blue box.
 */
@Composable
internal fun CompanionActionBar(
    missedFrom: Stop?,
    onMissed: (Stop) -> Unit,
    onChangeTrip: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animate = rememberAnimationsOn()
    // The last stop "Missed it" was for, so the button keeps its text while it makes room.
    val kept = remember { arrayOf<Stop?>(null) }
    if (missedFrom != null) kept[0] = missedFrom
    val share by animateFloatAsState(
        if (missedFrom != null) 1f else 0f,
        if (animate) spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "missedShare",
    )
    val missedShown = share > MISSED_HIDDEN_BELOW
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val stop = kept[0]
            if (missedShown && stop != null) {
                SecondaryButton(
                    text = stringResource(R.string.missed_train),
                    onClick = { onMissed(stop) },
                    modifier = Modifier
                        .weight(share)
                        .graphicsLayer { alpha = share },
                    shape = RoundedCornerShape(24.dp, 10.dp, 10.dp, 24.dp),
                    minHeight = 48.dp,
                    ground = AppColors.AmberContainer,
                    ink = AppColors.AmberInk,
                )
            }
            SecondaryButton(
                text = stringResource(R.string.companion_change_trip),
                onClick = onChangeTrip,
                modifier = Modifier.weight(1f),
                shape = if (missedShown) RoundedCornerShape(10.dp) else RoundedCornerShape(24.dp, 10.dp, 10.dp, 24.dp),
                minHeight = 48.dp,
            )
            // Sharing in plain sight, with the live glyph of the classic view; the last of the row.
            io.github.jnbp.nextstop.ui.ShareTripButton(
                onClick = onShare,
                size = 48.dp,
                shape = RoundedCornerShape(10.dp, 24.dp, 24.dp, 10.dp),
            )
        }
    }
}

// Below this share of its room, "Missed it" is gone from the bar.
private const val MISSED_HIDDEN_BELOW = 0.02f

/**
 * The blue box of the next step, compact: the minutes in a lime pill on the left; beside it
 * the step ("Losgehen um 06:12", "Aussteigen in …") and one line that merges what else matters
 * now (a jam, before boarding the stop position or platform of that ride and where to board
 * it with the small drawn train, on board the same platform or the next change
 * "↘ Gl. 2 · 6 min"), cut with an ellipsis, and at its end the chip of the wake-up alarm ([wake]). While on board in the calm view ([progress])
 * a thin bar of the ride lies inside the bottom of the box. What the times rest on is in the
 * bar on top (see [SourceGlyph]). Everything that changes fades or slides softly.
 */
@Composable
internal fun CompanionHero(
    model: CompanionModel,
    now: OffsetDateTime,
    wake: WakeChipState?,
    progress: Boolean,
    modifier: Modifier = Modifier,
) {
    val step = model.step
    val ride = model.ride
    val journey = model.journey
    val context = LocalContext.current
    // Before boarding a ride that leads to a change: where to board it, in a few words.
    val boarding = step.leg?.takeIf { step.kind == StepKind.Board }
    val boardingNext = boarding?.let { rideAfter(journey, it) }
    val carriagePosition = if (boarding != null && boardingNext != null) shortCarriagePosition(boarding, boardingNext) else null
    val carriage = carriagePosition?.let { carriageText(context, it) }
    // On board before a change: the same platform.
    val gettingOff = step.leg?.takeIf { step.kind == StepKind.GetOff }
    val gettingOffNext = gettingOff?.let { rideAfter(journey, it) }
    val samePlatform = if (gettingOff != null && gettingOffNext != null) {
        remember(gettingOff, gettingOffNext) { changeText(context, gettingOff, gettingOffNext, walkBetween(journey, gettingOff)) }
    } else {
        null
    }
    // Before boarding: where the ride waited for is boarded; the change after it only on board (#211).
    val where = io.github.jnbp.nextstop.ui.heroWhere(step, ride?.onBoard == true)
    val boardPart = step.leg?.takeIf { where == io.github.jnbp.nextstop.ui.HeroWhere.Board }?.let { leg ->
        io.github.jnbp.nextstop.ui.boardPlace(leg)?.let { place ->
            io.github.jnbp.nextstop.data.shownPositionLabel(place.spot)?.let { stringResource(R.string.walk_target_pos, it) } ?: platformLabel(place.platform, leg.product)
        }
    }
    val change = model.change?.takeIf { where == io.github.jnbp.nextstop.ui.HeroWhere.Change && ride != null && it.arriving === ride.leg }
    val changePart = change?.let {
        val where = platformLabel(it.next.departurePlatform, it.next.product) ?: it.next.from.plainStopName()
        // In words, not an arrow (#154): where the connection leaves and the minutes to change.
        stringResource(R.string.r6c_next_change, where, stringResource(R.string.companion_minutes, it.time.minutes.coerceAtLeast(0)))
    }
    // Getting off a long ride: its clock time is in the lime counter itself now (#148).
    val longRide: String? = null
    val standing = model.standingMinutes?.let { stringResource(R.string.companion_source_standing, it) }
    val subline = heroSubline(listOf(standing, longRide, boardPart, carriage, samePlatform, changePart))
    val showProgress = progress && ride != null && ride.onBoard && ride.stops.size >= 2
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(AppColors.Blue)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HERO_MIN_HEIGHT)
                .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = if (showProgress) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MinutePill(step = step, now = now)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FadeSwap(target = stepLabel(step, remember(journey) { io.github.jnbp.nextstop.data.railwayNamesOf(journey) })) { label ->
                    Text(
                        text = label,
                        color = AppColors.OnBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The one line below it: where to board with the small drawn train (#89), what
                // else matters now, and the wake-up alarm as a chip of its own at the end (#64).
                if (subline.isNotEmpty() || wake != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (boarding != null && carriagePosition != null) CarriageMark(boarding, carriagePosition)
                        FadeSwap(target = subline, modifier = Modifier.weight(1f, fill = false)) { line ->
                            if (line.isNotEmpty()) {
                                Text(
                                    text = line,
                                    color = AppColors.OnBlue.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (wake != null) WakeChip(wake)
                    }
                }
            }
        }
        if (showProgress && ride != null) {
            RideProgress(
                count = ride.stops.size,
                position = ride.position,
                thin = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .height(12.dp),
            )
        }
    }
}

// The compact blue box is at least this high: the lime pill and its margins.
private val HERO_MIN_HEIGHT = 80.dp

/**
 * The minutes until the step in lime: from the clock time to the minutes as it comes near
 * (getting off keeps hours and minutes), rolling to each new value; "approx." for a time the
 * app estimated. [compact]: the small pill of the pinned status line (#225), same rules.
 */
@Composable
internal fun MinutePill(step: TripStep, now: OffsetDateTime, compact: Boolean = false) {
    val minutes = minutesBetween(now, step.time).coerceAtLeast(0)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(if (compact) 12.dp else 20.dp))
            .background(AppColors.Lime)
            .defaultMinSize(minWidth = if (compact) 44.dp else 60.dp, minHeight = if (compact) 30.dp else 60.dp)
            .padding(horizontal = if (compact) 8.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // From an hour on the clock time ("an 15:17", #148), below it the minutes; a cross-fade between.
        val clockShown = io.github.jnbp.nextstop.ui.rememberHeroClock(minutes)
        FadeSwap(target = !clockShown, contentAlignment = Alignment.Center) { near ->
            if (near) {
                // The digits in the middle of the pill, the prime hanging off to the right (#182).
                io.github.jnbp.nextstop.ui.HeroMinutes(step, minutes, if (compact) PIN_HERO_SIZES else CARD_HERO_SIZES)
            } else if (compact) {
                io.github.jnbp.nextstop.ui.HeroClockText(step, fontSize = 15.sp, prefixSize = 10.sp)
            } else {
                io.github.jnbp.nextstop.ui.HeroClockText(step, fontSize = 20.sp, prefixSize = 12.sp)
            }
        }
    }
}

/** "By location · 47 km/h", "By live data", "By timetable", or a jam ("standing for 2 min"). */
@Composable
internal fun sourceText(model: CompanionModel): String {
    val standing = model.standingMinutes
    return when {
        standing != null -> stringResource(R.string.companion_source_standing, standing)
        model.source == CompanionSource.Location && model.speedKmh != null ->
            stringResource(R.string.companion_source_location_speed, model.speedKmh)
        model.source == CompanionSource.Location -> stringResource(R.string.companion_source_location)
        model.source == CompanionSource.Live -> stringResource(R.string.companion_source_live)
        else -> stringResource(R.string.companion_source_timetable)
    }
}

/**
 * What the times of the companion rest on, as a small icon inside the Live chip of the bar on
 * top (#62): ◎ by location, a clock by timetable; nothing extra for live data (the chip says
 * "Live" already). A tap or a long press shows it in words ("Aus Standort · 47 km/h") for a
 * moment; screen readers get the words at once.
 */
@Composable
internal fun SourceGlyph(model: CompanionModel, modifier: Modifier = Modifier) {
    val glyph = when (model.source) {
        CompanionSource.Location -> Glyph.Locate
        CompanionSource.Timetable -> Glyph.Recent
        CompanionSource.Live -> return
    }
    val text = sourceText(model)
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            delay(SOURCE_OPEN_MILLIS)
            open = false
        }
    }
    val haptics = LocalHapticFeedback.current
    val description = stringResource(R.string.r4c_source_hint, text)
    val tipGap = with(LocalDensity.current) { 26.dp.roundToPx() }
    Box(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { open = !open },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        open = true
                    },
                )
            }
            .padding(start = 1.dp, top = 2.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        io.github.jnbp.nextstop.ui.GlyphIcon(glyph, color = AppColors.Muted, size = 14.dp)
        if (open) {
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, tipGap), onDismissRequest = { open = false }) {
                Text(
                    text = text,
                    color = AppColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Card)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// How long the words of the source mark stay after a tap or a long press.
private const val SOURCE_OPEN_MILLIS = 3_000L

/**
 * The ride as a bar: a tick for every stop, the destination in lime, the part already ridden
 * filled and the traveller as a dot at [position] (stop units). The dot glides to a new place;
 * only the drawing reads it, so a move redraws without composing anything.
 */
@Composable
internal fun RideProgress(count: Int, position: Float, modifier: Modifier = Modifier, thin: Boolean = false) {
    val animate = rememberAnimationsOn()
    val shown = remember { Animatable(position.coerceAtLeast(0f)) }
    LaunchedEffect(position, animate) {
        val target = position.coerceAtLeast(0f)
        if (animate) shown.animateTo(target, tween(PROGRESS_GLIDE_MILLIS)) else shown.snapTo(target)
    }
    val track = AppColors.OnBlue.copy(alpha = 0.3f)
    val ridden = AppColors.OnBlue
    val lime = AppColors.Lime
    val dotRing = AppColors.Blue
    Box(
        modifier = modifier.drawBehind {
            val last = (count - 1).coerceAtLeast(1)
            // Thin inside the compact blue box: a finer line, smaller ticks and dot.
            val k = if (thin) 0.6f else 1f
            val edge = 7.dp.toPx() * k
            val y = size.height / 2f
            val width = size.width - 2 * edge
            fun x(stop: Float) = edge + width * (stop / last)
            val stroke = 4.dp.toPx() * k
            drawLine(track, Offset(edge, y), Offset(edge + width, y), strokeWidth = stroke, cap = StrokeCap.Round)
            val at = shown.value.coerceIn(0f, last.toFloat())
            drawLine(ridden, Offset(edge, y), Offset(x(at), y), strokeWidth = stroke, cap = StrokeCap.Round)
            for (stop in 0 until count) {
                val px = x(stop.toFloat())
                if (stop == count - 1) {
                    drawCircle(lime, radius = 6.dp.toPx() * k, center = Offset(px, y))
                } else {
                    drawCircle(if (stop <= at) ridden else track, radius = 3.dp.toPx() * k, center = Offset(px, y))
                }
            }
            drawCircle(Color.White, radius = 7.dp.toPx() * k, center = Offset(x(at), y))
            drawCircle(dotRing, radius = 7.dp.toPx() * k, center = Offset(x(at), y), style = Stroke(width = 2.5.dp.toPx() * k))
        },
    )
}

// How long the dot of the traveller glides to a new place.
private const val PROGRESS_GLIDE_MILLIS = 900

/** A section title over a card ("You are riding", "After that"). */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppColors.Muted,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** The chip of a change: "safe · 13 min" in green, "tight · 3 min" in amber, "too late" in red. */
@Composable
internal fun ReachChip(change: io.github.jnbp.nextstop.ui.TripChange) {
    val reach = changeReach(change.time)
    val minutes = change.time.minutes.coerceAtLeast(0)
    val (ground, ink, text) = when (reach) {
        ChangeReach.Safe -> Triple(AppColors.CalmContainer, AppColors.CalmInk, stringResource(R.string.companion_reach_safe, minutes))
        ChangeReach.Tight -> Triple(AppColors.AmberContainer, AppColors.AmberInk, stringResource(R.string.companion_reach_tight, minutes))
        ChangeReach.Late -> Triple(AppColors.DelayContainer, AppColors.DelayInk, stringResource(R.string.companion_reach_late))
    }
    Text(
        text = text,
        color = ink,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ground)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** "→ Gronau": the direction of a ride with an arrow, or its destination stop. */
internal fun towards(leg: Leg): String = "→ " + shownRideStop(leg, leg.direction ?: leg.to)

/** A gap of [height]. */
@Composable
internal fun Gap(height: Dp) = Spacer(modifier = Modifier.height(height))

/** A gap of [width]. */
@Composable
internal fun HGap(width: Dp) = Spacer(modifier = Modifier.width(width))

// The type sizes of the small lime count of the pinned status line (#225, see CompanionPinnedStatus).
private val PIN_HERO_SIZES = io.github.jnbp.nextstop.ui.HeroCountSizes(
    prefix = 9.sp,
    prefixGap = 2.dp,
    number = 17.sp,
    hoursNumber = 15.sp,
    unit = 10.sp,
    prime = 15.sp,
    hoursPrime = 13.sp,
    unitGap = 2.dp,
)

// The type sizes of the card's lime minute count (see HeroMinutes).
private val CARD_HERO_SIZES = io.github.jnbp.nextstop.ui.HeroCountSizes(
    prefix = 12.sp,
    prefixGap = 3.dp,
    number = 26.sp,
    hoursNumber = 22.sp,
    unit = 13.sp,
    prime = 22.sp,
    hoursPrime = 19.sp,
    unitGap = 3.dp,
)
