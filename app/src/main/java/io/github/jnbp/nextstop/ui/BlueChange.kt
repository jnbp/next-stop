package io.github.jnbp.nextstop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.Layout
import io.github.jnbp.nextstop.data.ChipRowPlacement
import io.github.jnbp.nextstop.data.chipRowPlacement
import io.github.jnbp.nextstop.data.chipsStartX
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ChangeMapKind
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.WaitTone
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.changeStepLine
import io.github.jnbp.nextstop.data.offersWalkNavigation
import io.github.jnbp.nextstop.data.pageStopName
import io.github.jnbp.nextstop.data.tone
import io.github.jnbp.nextstop.data.walkNavigationTarget
import io.github.jnbp.nextstop.ui.theme.AppColors

/*
 * The change between two rides in the new views (the detail body and both new Live-Fahrt
 * layouts, B1-B5): a blue card on the dotted rail, like every other walk. In it the walk
 * symbol with "Umstieg · 3′" and the rating once as a small chip, the step "Gleis 1 → Pos. 1
 * (Bus 285)", the walk where there is one, the same-platform hint and the buttons of the small
 * map (opening in place) and of Google Maps. Below it, on the page ground and not blue (picking
 * a connection is no walking), what the caller puts there ([below]): the link "Anschluss
 * wechseln" and the tiles of the connections. The classic views keep their own ChangeEntry.
 */

/** The change [change] as a blue card (see above); the parameters are those of ChangeEntry. */
@Composable
internal fun BlueChangeEntry(
    change: TripChange,
    over: Boolean,
    onFocusWalk: (Leg) -> Unit,
    style: WalkCardStyle,
    modifier: Modifier = Modifier,
    detourMinutes: Long? = null,
    arrivingStops: List<Stopover> = change.arriving.stopovers,
    nextStops: List<Stopover> = change.next.stopovers,
    arrivingBeyond: Stopover? = null,
    nextBefore: Stopover? = null,
    maps: Boolean = true,
    railwayNames: Set<String> = emptySet(),
    below: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dots = remember(density) { PathEffect.dashPathEffect(floatArrayOf(0.1f, with(density) { 7.dp.toPx() }), 0f) }
    val time = change.time
    val walk = change.walk
    val walkMinutes = if (walk == null) 0L else minutesBetween(walk.departure, walk.arrival)
    // A knapp change or a sprint that worked: the node on the rail springs into a check, with a short buzz.
    val made = changeMade(over, time.tightness)
    val madeShown = remember { Animatable(if (made) 1f else 0f) }
    val haptics = LocalHapticFeedback.current
    val springs = rememberAnimationsOn()
    LaunchedEffect(made) {
        if (!made) {
            madeShown.snapTo(0f)
        } else if (madeShown.value < 1f) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            if (springs) madeShown.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) else madeShown.snapTo(1f)
        }
    }
    val madeGreen = AppColors.Early
    val rating = if (made) madeGreen else toneColor(time.tone)
    // A change that is over turns grey and pale, a change made stays clear with its green chip;
    // both animate once as the state changes (snapped without animations), nothing per frame.
    val animate = rememberAnimationsOn()
    val done = over && !made
    val pale by animateFloatAsState(if (done) BLUE_PAST_ALPHA else 1f, if (animate) spring(stiffness = Spring.StiffnessMediumLow) else snap(), label = "changeDone")
    val cardGround by animateColorAsState(
        if (done) AppColors.Chip else AppColors.BlueContainer,
        if (animate) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "changeGround",
    )
    val rail = AppColors.Rail
    val nodeGround = AppColors.Card
    // The small map of the change: the walk's own, else (no walk leg, or one without a way) that of the change.
    val mapKind = remember(change.arriving, change.next, walk, walkMinutes, OsmStations.loaded) {
        if (walk == null) ChangeMapKind.None else changeWalkMapKind(change.arriving, change.next, walk, walkMinutes)
    }
    // The way of the change, whether or not its small map is drawn here: a card over the big map
    // ([maps] false) still shows it there with "Karte öffnen" (#191).
    val anyMap: (@Composable () -> Unit)? = when {
        over -> null
        walk != null -> if (mapKind == ChangeMapKind.None) null else ({ ChangeMapOf(mapKind, change.arriving, change.next, walk, onFocusWalk) })
        hasSpotWalkMap(change.arriving, change.next) -> ({ SpotWalkMap(change.arriving, change.next, onFocusWalk) })
        change.arriving.boardingSpot == null && change.arriving.alightingSpot == null && hasBoardingPositionMap(change.next) -> {
            // From a train to a bus or tram of a stop of many positions: the way to its position.
            val arrivedAt = change.arriving.path.lastOrNull() ?: change.arriving.toPlace?.position
            ({ BoardingPositionMap(change.next, arrivedAt, onFocusWalk) })
        }
        !hasBoardingPositionMap(change.next) && hasChangeMap(change.arriving, change.next) -> ({ ChangeWalkMap(change.arriving, change.next, onFocusWalk) })
        else -> null
    }
    val map = anyMap.takeIf { maps }
    // "Karte öffnen" (#191): the walk on the big map, or for a change without a walk of its own
    // the way its small map draws (see changeFocusWalk).
    val openBig: (() -> Unit)? = when {
        over -> null
        walk != null -> ({ onFocusWalk(walk) })
        anyMap != null -> ({ changeFocusWalk(change.arriving, change.next)?.let(onFocusWalk) })
        else -> null
    }
    // Folded or open as the setting "Minikarte für Fußwege immer aufgeklappt" says, opened in place.
    var mapOpen by rememberWalkMapOpen()
    val end = walk?.let { walkNavigationTarget(it, change.next) }
    val navigate = walk != null && end != null && offersWalkNavigation(end, over)

    // The words of the card.
    val stopName = pageStopName(change.stop.name, railwayNames)
    val spoken = stringResource(R.string.transfer_at, stopName)
    val ratingText = when {
        made -> stringResource(R.string.change_made)
        time.possible -> toneLabel(time)
        else -> stringResource(R.string.choice_gone)
    }
    val minutesShown = time.possible && !made
    val minutesText = stringResource(R.string.companion_minutes, time.minutes.coerceAtLeast(0))
    val head = if (minutesShown) stringResource(R.string.r6b_change_head, minutesText) else stringResource(R.string.r6b_change_head_plain)
    val target = walk?.walkTarget
    val targetText = walkTargetText(target)
    val spotText = io.github.jnbp.nextstop.data.shownPositionLabel(change.next.boardingSpot?.label)?.let { stringResource(R.string.walk_target_pos, it) }
    // Never "Pos." without its number (#239): else the platform, the other stop, or "Haltestelle" for a bus or tram.
    val roadStop = if (change.next.product == "bus" || change.next.product == "tram") stringResource(R.string.r8fix_change_to_stop) else null
    val to = io.github.jnbp.nextstop.data.changeStepTarget(
        osmPosition = targetText.takeIf { target?.kind == WalkTargetKind.StopPosition },
        platform = platformLabel(change.next.departurePlatform, change.next.product),
        spot = spotText,
        // The other stop by name only where the walk line below does not say it already.
        otherStop = pageStopName(change.next.from, railwayNames).takeIf { it != stopName && walk == null },
        roadStop = roadStop,
    )
    val step = changeStepLine(
        platformLabel(change.arriving.arrivalPlatform, change.arriving.product),
        to,
        changeLineText(change.next),
    )
    // The walk itself where it says more than the step: its minutes, another stop it leads to, the entrance and the exit.
    val nextName = pageStopName(change.next.from, railwayNames)
    val place = listOfNotNull(
        nextName.takeIf { it != stopName },
        targetText.takeIf { target != null && target.kind != WalkTargetKind.StopPosition },
    ).joinToString(", ").ifEmpty { null }
    val exitText = walkExitText(walk?.walkExit)
    val cycling = walk?.cycling == true
    val walkLine = when {
        walk != null && detourMinutes != null -> withExit(stringResource(R.string.walk_rerouted, detourMinutes, place ?: to ?: stopName), exitText)
        walk != null && walkMinutes > 0 && place != null ->
            withExit(stringResource(if (cycling) R.string.cycle_to else R.string.walk_to, walkMinutes, place), exitText)
        walk != null && walkMinutes > 0 ->
            withExit(stringResource(if (cycling) R.string.r6b_cycle_minutes else R.string.r6b_walk_minutes, walkMinutes), exitText)
        else -> listOfNotNull(place, exitText).joinToString(", ").ifEmpty { null }
    }
    val ink = AppColors.OnBlueContainer
    val showLabel = stringResource(R.string.show_on_map)
    val mapLabel = stringResource(if (mapOpen) R.string.walk_map_hide else R.string.walk_map_show)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = pale }
            .drawBehind {
                // The dotted rail runs through the whole change, picker included, and on into the
                // gaps to the ride cards, so the timeline has no break.
                val x = BLUE_RAIL.toPx() / 2f
                val bleed = BLUE_RAIL_BLEED.toPx()
                drawLine(rail, Offset(x, -bleed), Offset(x, size.height + bleed), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
                val spot = Offset(x, BLUE_NODE_Y.toPx())
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
                    drawCircle(nodeGround, radius = 8.dp.toPx(), center = spot)
                    drawCircle(rating, radius = 6.dp.toPx(), center = spot, style = Stroke(3.dp.toPx()))
                }
            }
            .padding(start = BLUE_RAIL, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardGround)
                .then(
                    when {
                        // The calm views: a tap on the card opens its small map in place.
                        map != null -> Modifier.clickable(onClickLabel = mapLabel) { mapOpen = !mapOpen }
                        // Over the big map: a tap shows the walk there (as "Karte öffnen" below, #191).
                        style == WalkCardStyle.Map && walk != null -> Modifier.clickable(onClickLabel = showLabel) { onFocusWalk(walk) }
                        style == WalkCardStyle.Map && openBig != null -> Modifier.clickable(onClickLabel = showLabel, onClick = openBig)
                        else -> Modifier
                    },
                )
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Walk symbol, "Umstieg · 3′" and the rating once, as a chip; read out with the station.
            val said = listOfNotNull(spoken, if (minutesShown) spokenDuration(time.minutes) else null, ratingText).joinToString(", ")
            // The head is never cut for the chip (#242, "Ums…" beside "relaxed, lange Wartezeit"):
            // where both do not fit on one line, the chip goes below, under the head's text.
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .clearAndSetSemantics { contentDescription = said },
                content = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlyphIcon(if (cycling) Glyph.Bike else Glyph.Walk, color = ink, size = if (cycling) 22.dp else 20.dp)
                        Text(
                            text = head,
                            color = ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RatingChip(text = ratingText, tone = time.tone, made = made, animate = animate, check = { madeShown.value })
                },
            ) { measurables, constraints ->
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val gap = 10.dp.roundToPx()
                val headWide = measurables[0].maxIntrinsicWidth(loose.maxHeight.coerceAtMost(10_000))
                val bounded = constraints.hasBoundedWidth
                val indent = 30.dp.roundToPx()
                val chipBeside = measurables[1].maxIntrinsicWidth(loose.maxHeight.coerceAtMost(10_000))
                val placement = if (bounded) chipRowPlacement(headWide, chipBeside, gap, loose.maxWidth) else ChipRowPlacement.Beside
                if (placement == ChipRowPlacement.Beside) {
                    val chip = measurables[1].measure(loose)
                    val headMax = if (bounded) (loose.maxWidth - chip.width - gap).coerceAtLeast(0) else loose.maxWidth
                    val headLine = measurables[0].measure(loose.copy(maxWidth = headMax))
                    val width = if (bounded) loose.maxWidth else headLine.width + gap + chip.width
                    val height = maxOf(constraints.minHeight, headLine.height, chip.height)
                    layout(width, height) {
                        headLine.place(0, (height - headLine.height) / 2)
                        chip.place(chipsStartX(width, chip.width), (height - chip.height) / 2)
                    }
                } else {
                    val headLine = measurables[0].measure(loose)
                    val chip = measurables[1].measure(loose.copy(maxWidth = (loose.maxWidth - indent).coerceAtLeast(0)))
                    val top = maxOf(constraints.minHeight, headLine.height)
                    val below = 2.dp.roundToPx()
                    layout(loose.maxWidth, top + below + chip.height) {
                        headLine.place(0, (top - headLine.height) / 2)
                        chip.place(indent, top + below)
                    }
                }
            }
            if (step != null) {
                Text(
                    text = step,
                    color = ink.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 30.dp),
                )
            }
            if (walkLine != null) {
                Text(
                    text = walkLine,
                    color = ink.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 30.dp),
                )
            }
            if (detourMinutes == null && walk == null && step == null && walkLine == null) {
                // Nothing known of the way: at least where the change is.
                Text(text = stopName, color = ink, fontSize = 13.sp, modifier = Modifier.padding(start = 30.dp))
            }
            // At the change itself: the same platform, or where the other stop position lies.
            if (!over) {
                ChangeHint(
                    arriving = change.arriving,
                    next = change.next,
                    walkMinutes = walkMinutes,
                    arrivingStops = arrivingStops,
                    nextStops = nextStops,
                    arrivingBeyond = arrivingBeyond,
                    nextBefore = nextBefore,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // "Karte zeigen ▾", "Karte öffnen" and Google Maps, visible in every blue card (#191).
            BlueWalkActions(
                mapOpen = mapOpen,
                onToggleMap = if (map != null) ({ mapOpen = !mapOpen }) else null,
                onOpenMap = openBig,
                onNavigate = if (navigate && end != null) ({ openWalkNavigation(context, end, cycling) }) else null,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (map != null) {
                MapFold(open = mapOpen) {
                    Box(modifier = Modifier.padding(top = 8.dp)) { map() }
                }
            }
        }
        below?.invoke()
    }
}

/** The rating of a change once, as a small chip in the colours of its tone ("knapp", "relaxed", "Sprint"). */
@Composable
private fun RatingChip(text: String, tone: WaitTone, made: Boolean, animate: Boolean, check: () -> Float) {
    // A change made: the chip turns green ("Umstieg geschafft") with a tick drawing in; the colours move softly.
    val spec = if (animate) spring<Color>(stiffness = Spring.StiffnessMediumLow) else snap()
    val ground by animateColorAsState(
        when {
            made -> AppColors.CalmContainer
            tone == WaitTone.Neutral -> AppColors.Card
            else -> toneContainer(tone)
        },
        spec,
        label = "ratingGround",
    )
    val chipInk by animateColorAsState(if (made) AppColors.CalmInk else toneInk(tone), spec, label = "ratingInk")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ground)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (made) {
            CheckMark(color = chipInk, modifier = Modifier.size(12.dp), progress = check())
        }
        Text(
            text = text,
            color = chipInk,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            // On a line of its own below the head (#242) a long rating wraps rather than being cut.
            maxLines = 2,
        )
    }
}

/** The next line as the step names it: "Bus 285", "Tram M10", else as its badge ("S1", "RE1"). */
@Composable
private fun changeLineText(next: Leg): String? {
    val short = shortLine(next.line) ?: return null
    return when (next.product) {
        "bus" -> stringResource(R.string.r6b_bus_line, short)
        "tram" -> stringResource(R.string.r6b_tram_line, short)
        else -> short
    }
}

// The rail column of the parts between the cards (as RAIL_WIDTH of the classic parts).
private val BLUE_RAIL = 52.dp

// How far the dotted rail of a change reaches into the gaps above and below it.
private val BLUE_RAIL_BLEED = 8.dp

// Where the node of the change sits on the rail: beside the head line of the blue card.
private val BLUE_NODE_Y = 30.dp

// A change that is over is drawn this pale (as the classic parts).
private const val BLUE_PAST_ALPHA = 0.55f
