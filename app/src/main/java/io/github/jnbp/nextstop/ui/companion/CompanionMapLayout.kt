package io.github.jnbp.nextstop.ui.companion

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import io.github.jnbp.nextstop.ui.pinRoom
import io.github.jnbp.nextstop.ui.clock
import io.github.jnbp.nextstop.ui.appendEstimatedTime
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.layout.width
import io.github.jnbp.nextstop.ui.rememberPinFade
import androidx.compose.ui.platform.LocalDensity
import io.github.jnbp.nextstop.ui.CompassButton
import io.github.jnbp.nextstop.ui.RoundButton
import io.github.jnbp.nextstop.ui.rememberAnimationsOn
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import io.github.jnbp.nextstop.ui.Chevron
import io.github.jnbp.nextstop.ui.FadeSwap
import io.github.jnbp.nextstop.ui.GlassSurface
import io.github.jnbp.nextstop.ui.Glyph
import io.github.jnbp.nextstop.ui.GlyphIcon
import io.github.jnbp.nextstop.ui.LocalWalkCardStyle
import io.github.jnbp.nextstop.ui.WalkCardStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.ChangeReach
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.changeReach
import io.github.jnbp.nextstop.ui.Alternatives
import io.github.jnbp.nextstop.ui.LineBadge
import io.github.jnbp.nextstop.ui.LocalMapCover
import io.github.jnbp.nextstop.ui.SheetStage
import io.github.jnbp.nextstop.ui.StageSheet
import io.github.jnbp.nextstop.ui.delayedTimeText
import io.github.jnbp.nextstop.ui.joinedText
import io.github.jnbp.nextstop.data.boardingTime
import androidx.compose.ui.text.AnnotatedString
import io.github.jnbp.nextstop.ui.coversMapTop
import io.github.jnbp.nextstop.ui.shortLine
import io.github.jnbp.nextstop.ui.CompanionMapFocus
import io.github.jnbp.nextstop.ui.toggled
import io.github.jnbp.nextstop.ui.theme.AppColors
import kotlin.math.floor

/**
 * The companion with the map ("Mit Karte"): the map fills the screen, on top only the compact
 * blue box ([hero]); the rest lies in a sheet at the bottom that starts put away, showing one
 * line about the ride ("U9 → Osloer Str. · 4 stops to go · then 66 10:02 · safe"), and pulled
 * up shows the same stops and next ride as the calm view, fully up it reaches the bar on top
 * (#128). The compass (once the map was turned) stands top right under the blue box (#113);
 * "centre on me" ([onRecentre]) floats at the bottom right above the sheet, drawn by the root
 * of the app for every map page (#165).
 */
@Composable
internal fun MapCompanion(
    model: CompanionModel,
    alternatives: Alternatives?,
    onSwitch: (Journey) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    onRecentre: (() -> Unit)?,
    hero: @Composable () -> Unit,
    extras: @Composable () -> Unit,
    sheetModifier: Modifier,
    bottomRoom: Dp,
    now: java.time.OffsetDateTime,
    walkDetour: io.github.jnbp.nextstop.ui.WalkDetour?,
) {
    var stage by rememberSaveable { mutableStateOf(SheetStage.Away) }
    val mapCover = LocalMapCover.current
    val density = LocalDensity.current
    val animate = rememberAnimationsOn()
    // What the blue box and the chips under it take: the lower stages of the sheet stay below
    // them as before, its top stage reaches up to the bar on top (#128).
    var heroHeight by remember { mutableIntStateOf(0) }
    var chipsHeight by remember { mutableIntStateOf(0) }
    val restTop = with(density) { (heroHeight + if (chipsHeight > 0) chipsHeight + CHIP_GAP.roundToPx() else 0).toDp() }
    // Settled at another height, the sheet leaves the map another room: the trip is framed into it (#137).
    val seenStage = remember { arrayOf(stage) }
    LaunchedEffect(stage) {
        if (seenStage[0] == stage) return@LaunchedEffect
        seenStage[0] = stage
        delay(SHEET_SETTLE_MILLIS)
        CompanionLayout.coverChanged()
    }
    val full = stage == SheetStage.Full
    // The pinned status line (#225): where the blue box and the top edge of the sheet are on the
    // screen; the line shows once the sheet covers most of the box (see heroPinShown). Only the
    // yes or no is read in composition, not every frame of the sheet's move.
    var heroTop by remember { mutableFloatStateOf(0f) }
    var heroBottom by remember { mutableFloatStateOf(0f) }
    var sheetTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val pinBefore = remember { booleanArrayOf(false) }
    val pinShown by remember {
        derivedStateOf { heroPinShown(heroCovered(heroTop, heroBottom, sheetTop), pinBefore[0]).also { pinBefore[0] = it } }
    }
    val listScroll = rememberScrollState()
    // How high the status line over the list is right now (0 while it is not there), set while measuring.
    val pinHeight = remember { mutableIntStateOf(0) }
    val pinScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottomRoom),
    ) {
        // The map frames the trip (and centres the traveller, #273) below the blue box and the
        // chips under it, above the sheet: the lower edge of both together is what covers its top.
        Column(modifier = Modifier.fillMaxWidth().coversMapTop()) {
            Column(
                modifier = Modifier
                    // Hero corners (Joel 19.11.): drawn only above the sheet's top edge (read while drawing, no
                    // recomposition), so nothing of the blue box shimmers through the sheet or
                    // peeks out beside its rounded top corners once it is pulled over the box.
                    .drawWithContent {
                        val bottom = heroClipBottom(heroTop, sheetTop)
                        if (bottom == null || bottom >= size.height * 2f) {
                            drawContent()
                        } else if (bottom > 0f) {
                            clipRect(left = -size.width, top = -size.height, right = size.width * 2f, bottom = bottom) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                    .onSizeChanged { heroHeight = it.height }
                    .onGloballyPositioned {
                        val top = it.positionInRoot().y
                        heroTop = top
                        heroBottom = top + it.size.height
                    },
            ) { hero() }
            // Under the blue box: the chips at the start, the round buttons of the map in one
            // column at the end (#113), always at the same place; under the sheet pulled up to
            // the top they are not needed (#128).
            AnimatedVisibility(
                visible = !full,
                enter = if (animate) fadeIn(tween(CHIPS_FADE_MILLIS)) else EnterTransition.None,
                exit = if (animate) fadeOut(tween(CHIPS_FADE_MILLIS)) else ExitTransition.None,
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = CHIP_GAP), verticalAlignment = Alignment.Top) {
                    Column(
                        modifier = Modifier.weight(1f).onSizeChanged { chipsHeight = it.height },
                        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
                    ) {
                        // Before boarding the map frames the traveller and the stop; the vehicle far away only on
                        // a tap (#91). A second tap goes back to the traveller.
                        val ride = model.ride
                        if (ride != null && !ride.onBoard && ride.leg.tripId != null) {
                            WhereIsMyRideChip(
                                product = ride.leg.product,
                                shown = CompanionLayout.vehicleShown,
                                onClick = {
                                    CompanionLayout.vehicleShown = !CompanionLayout.vehicleShown
                                    // A map moved by hand (or showing the own position or the route) is framed again for it.
                                    CompanionLayout.askTripFrame()
                                },
                            )
                        }
                        // After the locate button (#104): the own position up close, or the route still to go.
                        val focus = CompanionLayout.mapFocus
                        if (focus != CompanionMapFocus.Trip) {
                            FocusChip(
                                own = focus == CompanionMapFocus.Own,
                                onClick = { CompanionLayout.mapFocus = focus.toggled() },
                            )
                        }
                    }
                    HGap(8.dp)
                    // The compass stays top right under the blue box; "centre on me" floats at the
                    // bottom right above the sheet (#165, see FloatingLocateButton in the root).
                    CompassButton()
                }
            }
        }
        StageSheet(
            stage = stage,
            onStage = { stage = it },
            // The round buttons stand in their own column under the blue box (see MapButtonColumn).
            onRecentre = null,
            modifier = sheetModifier.fillMaxSize(),
            restTop = restTop + CHIP_GAP,
            // No grip line over the one line of the sheet: it is the handle itself, with a chevron (#79).
            header = { SheetSummary(model, away = stage == SheetStage.Away, modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)) },
            onSheetTop = { y ->
                sheetTop = y
                if (mapCover != null) io.github.jnbp.nextstop.ui.MapEdges.report(mapCover, 1, y)
            },
            noGrip = true,
        ) {
            // The list scrolls beneath the status line (#245): the line lies over its top, the list
            // leaves room for it at rest and fades out beneath it once scrolled (see rememberPinFade).
            val pinTop = with(density) { PIN_TOP_GAP.toPx() }
            val pinFade = rememberPinFade(top = { pinTop }, bottom = { pinHeight.intValue.toFloat() }, scrolled = { listScroll.value })
            Box {
                // Measured first (its height is the list's room), drawn over the list.
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            pinHeight.intValue = placeable.height
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                ) {
                    // Over the list, under the handle: the next step in one line while the sheet hides the blue box (#225).
                    AnimatedVisibility(
                        visible = pinShown && !model.arrived,
                        enter = if (animate) {
                            fadeIn(tween(PIN_MOVE_MILLIS)) +
                                expandVertically(tween(PIN_MOVE_MILLIS), expandFrom = Alignment.Top) +
                                slideInVertically(tween(PIN_MOVE_MILLIS)) { -it / 2 }
                        } else {
                            EnterTransition.None
                        },
                        exit = if (animate) {
                            fadeOut(tween(PIN_MOVE_MILLIS)) +
                                shrinkVertically(tween(PIN_MOVE_MILLIS), shrinkTowards = Alignment.Top) +
                                slideOutVertically(tween(PIN_MOVE_MILLIS)) { -it / 2 }
                        } else {
                            ExitTransition.None
                        },
                    ) {
                        CompanionPinnedStatus(
                            model = model,
                            now = now,
                            // Back to the top of the list, and the sheet down to its middle: the blue box shows again.
                            onClick = {
                                pinScope.launch { if (animate) listScroll.animateScrollTo(0) else listScroll.scrollTo(0) }
                                stage = SheetStage.Half
                            },
                            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = PIN_TOP_GAP),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .then(pinFade)
                        .verticalScroll(listScroll)
                        .pinRoom { pinHeight.intValue }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    extras()
                    // No small maps here: the big map behind shows every walk; its cards say so (#81).
                    // One form of the sun chip on the whole page (#236).
                    val sunChipPage = remember(model.journey.id) { io.github.jnbp.nextstop.data.SunChipPage() }
                    CompositionLocalProvider(LocalWalkCardStyle provides WalkCardStyle.Map, io.github.jnbp.nextstop.ui.LocalSunChipPage provides sunChipPage) {
                        CompanionTimeline(model, alternatives, onSwitch, onFocusWalk, now = now, maps = false, walkDetour = walkDetour)
                        // What the colours of the times mean and where the data comes from (#277b).
                        io.github.jnbp.nextstop.ui.TimesFooter(model.journey, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

// The pinned status line stands this far below the handle of the sheet.
private val PIN_TOP_GAP = 2.dp

// The pinned status line fades and slides in and out this long (#225).
private const val PIN_MOVE_MILLIS = 220

// The gap between the blue box, the chips under it and the sheet.
private val CHIP_GAP = 8.dp

// The chips and buttons under the blue box fade this long as the sheet covers them.
private const val CHIPS_FADE_MILLIS = 160

// The sheet settles on a stage within about this long; the map is framed anew after it.
private const val SHEET_SETTLE_MILLIS = 450L

/** The one line of the put-away sheet: the ride, how many stops are left, the next ride and its change. */
@Composable
private fun SheetSummary(model: CompanionModel, away: Boolean, modifier: Modifier = Modifier) {
    // Only the final walk left (or arrived): no ride to sum up, the walk in its place (#246).
    val ride = model.ride ?: return SheetStepSummary(model, away, modifier)
    val leg = ride.leg
    val left = if (ride.onBoard) (ride.stops.lastIndex - floor(ride.position.coerceAtLeast(0f)).toInt()).coerceAtLeast(0) else -1
    // The times as the delay setting says (#227).
    val parts = buildList<AnnotatedString> {
        if (left >= 0) add(AnnotatedString(pluralStringResource(R.plurals.companion_stops_left, left, left)))
        else add(delayedTimeText(R.string.companion_departs_at, boardingTime(leg), AppColors.Muted))
        model.nextRide?.let { next ->
            add(delayedTimeText(R.string.companion_then, boardingTime(next), AppColors.Muted, listOf(shortLine(next.line) ?: "", null)))
            model.change?.takeIf { it.next === next }?.let { change ->
                val minutes = change.time.minutes.coerceAtLeast(0)
                val reach = when (changeReach(change.time)) {
                    ChangeReach.Safe -> stringResource(R.string.companion_reach_safe, minutes)
                    ChangeReach.Tight -> stringResource(R.string.companion_reach_tight, minutes)
                    ChangeReach.Late -> stringResource(R.string.companion_reach_late)
                }
                add(AnnotatedString(reach))
            }
        }
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LineBadge(leg, minWidth = 40.dp)
        HGap(10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = towards(leg), fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = joinedText(parts),
                color = AppColors.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HGap(8.dp)
        // Up while the sheet is put away, down while it is open.
        Chevron(open = away, color = AppColors.Muted)
    }
}

/**
 * The one line of the sheet once no ride is left (#246, see sheetSummaryKind): the final walk
 * ("Zu Fuß nach …", "an 10:12") or the arrival, at the height of the ride's line, so the sheet
 * put away is never an empty strip.
 */
@Composable
private fun SheetStepSummary(model: CompanionModel, away: Boolean, modifier: Modifier = Modifier) {
    val step = model.step
    val kind = sheetSummaryKind(hasRide = model.ride != null, kind = step.kind)
    val glyph = when {
        kind == SheetSummaryKind.Arrived -> Glyph.Flag
        step.cycling -> Glyph.Bike
        else -> Glyph.Walk
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, color = AppColors.Blue, size = 24.dp)
        }
        HGap(10.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = io.github.jnbp.nextstop.ui.stepLabel(step),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (kind == SheetSummaryKind.Step) {
                // #277: a time the app estimated is blue with "ca.".
                val about = stringResource(R.string.estimated_prefix)
                val template = stringResource(R.string.arrival_at, "\u0000")
                Text(
                    text = buildAnnotatedString {
                        template.forEach { char ->
                            when {
                                char != '\u0000' -> append(char)
                                step.estimated -> appendEstimatedTime(about, step.time)
                                else -> append(step.time.clock())
                            }
                        }
                    },
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HGap(8.dp)
        Chevron(open = away, color = AppColors.Muted)
    }
}

/** The question of the chip by the kind of ride: S- and U-Bahn "Bahn", bus, tram, ferry, train; "Fahrzeug" where not known (#212). */
private fun whereIsMyRide(product: String): Int = io.github.jnbp.nextstop.ui.whereRideRes(io.github.jnbp.nextstop.ui.rideKindOf(product))

/** "Wo ist mein Zug?" over the map before boarding (#91); while the vehicle is framed, "Zurück zu mir". */
/** The chip of the locate button (#104): "Restliche Strecke" while the own position shows, "Mein Standort" while the route does. */
@Composable
private fun FocusChip(own: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 40.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlyphIcon(if (own) Glyph.Station else Glyph.Locate, color = AppColors.Blue, size = 18.dp)
            FadeSwap(target = own) { showsOwn ->
                Text(
                    text = stringResource(if (showsOwn) R.string.r5perf_companion_show_route else R.string.r5perf_companion_my_position),
                    color = AppColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WhereIsMyRideChip(product: String, shown: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 40.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlyphIcon(if (shown) Glyph.Locate else Glyph.Station, color = AppColors.Blue, size = 18.dp)
            FadeSwap(target = shown) { back ->
                Text(
                    text = stringResource(if (back) R.string.r5c_back_to_me else whereIsMyRide(product)),
                    color = AppColors.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

