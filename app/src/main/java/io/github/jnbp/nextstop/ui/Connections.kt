package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.shownTime
import io.github.jnbp.nextstop.data.shownRideStopNames
import androidx.compose.runtime.CompositionLocalProvider
import io.github.jnbp.nextstop.data.StopAction
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.ui.MotionDurationScale
import androidx.compose.foundation.layout.wrapContentHeight
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.IntState
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.shownWalkMinutes
import io.github.jnbp.nextstop.data.ChartTotalTime
import io.github.jnbp.nextstop.data.rideEndsWithoutOverlap
import io.github.jnbp.nextstop.data.ChartShortLegs
import io.github.jnbp.nextstop.data.ChangeMapKind
import io.github.jnbp.nextstop.data.PlannedTrips
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.ALT_CHANGE_SWITCH
import io.github.jnbp.nextstop.data.switchToAltChange
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.data.TicketFilter
import io.github.jnbp.nextstop.data.ticketFilters
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.rideTown
import io.github.jnbp.nextstop.data.townedStopName
import io.github.jnbp.nextstop.data.railwayNames
import io.github.jnbp.nextstop.data.rideStopName
import io.github.jnbp.nextstop.data.shownRideStop
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Tightness
import io.github.jnbp.nextstop.data.ratingSettingsVersion
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.Via
import io.github.jnbp.nextstop.data.briefStopName
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.changeTightness
import io.github.jnbp.nextstop.data.transferTightness
import io.github.jnbp.nextstop.data.ChangeKind
import io.github.jnbp.nextstop.data.journeyTightness
import io.github.jnbp.nextstop.data.journeyChangesOf
import io.github.jnbp.nextstop.data.ChangeTime
import io.github.jnbp.nextstop.data.changeTime
import io.github.jnbp.nextstop.data.changeGapMinutes
import io.github.jnbp.nextstop.data.rapidLineName
import io.github.jnbp.nextstop.data.neededMinutes
import io.github.jnbp.nextstop.data.WaitTone
import io.github.jnbp.nextstop.data.waitTone
import io.github.jnbp.nextstop.data.changeNeed
import io.github.jnbp.nextstop.data.tone
import io.github.jnbp.nextstop.data.alertingWarnings
import io.github.jnbp.nextstop.data.quietWarnings
import io.github.jnbp.nextstop.data.warningAlerts
import io.github.jnbp.nextstop.ui.theme.AppColors
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.gestures.scrollBy
import kotlin.math.roundToInt

// Share of the height the detail card takes: small at first, larger once a journey is picked.
private const val DETAIL_START = 0.34f
private const val DETAIL_OPEN = 0.56f

// The spring of the border between chart and detail card, the same as the sheet of the start
// page: a slight overshoot before it rests.
private const val SHEET_DAMPING = 0.8f

// How far a released border carries on, in seconds of its speed when let go.
private const val SPLIT_PROJECTION = 0.12f
private const val DETAIL_MIN = 0.26f
private const val DETAIL_MAX = 0.7f

/**
 * What the connections screen remembers for one search. It lives above the screen, so the
 * pick and the place in the chart are still there after the trip companion or
 * the planning view was open in between.
 */
class ConnectionsState {
    var selectedId by mutableStateOf<String?>(null)
    val detailShare = mutableFloatStateOf(DETAIL_START)
    val chartState = LazyListState()
    val earlierPrimed = mutableStateOf(false)
    val anchored = mutableStateOf(false)
    // How far the list and the chart were scrolled up and down, kept while a full-screen detail page is open.
    val listScroll = ScrollState(0)
    val chartUpDown = ScrollState(0)
    // True while the picked journey is shown on its own page (connections in full screen).
    var detailPage by mutableStateOf(false)
    // True from a tap on a column until its page opens a moment later (see warmJourneyPage):
    // the chart already stands still meanwhile, as it does under the page.
    var opening by mutableStateOf(false)
    // Where each row of the list lies in it (by journey id) and how high the list is in view,
    // so another transfer level can keep the list on the same connection.
    val rowTops = mutableMapOf<String, Int>()
    var listViewport = 0
    // What was in view when another transfer level was chosen; taken once the new list is laid out.
    var levelHold: LevelHold? = null
    // True once "Frühere Verbindungen" was tapped in the list: journeys that left show too (#150).
    var listEarlier by mutableStateOf(false)
}

/**
 * What the overview showed when another transfer level was chosen: the journey in front
 * ([current], the picked one or the default), the journey to keep in place ([anchor], the
 * picked one while it is in view, else the first one in view) and how far [anchor] was from the
 * start of the view, in pixels.
 */
class LevelHold(val current: Journey, val anchor: Journey, val offset: Int)

/** [LevelHold] of the overview of [state] as it was laid out with [before], the journeys of the level before. */
private fun levelHoldOf(
    state: ConnectionsState,
    before: List<Journey>,
    time: TimeChoice?,
    now: OffsetDateTime,
    listView: Boolean,
): LevelHold? {
    val current = before.firstOrNull { it.id == state.selectedId } ?: defaultJourney(before, time, now) ?: return null
    if (!listView) {
        val info = state.chartState.layoutInfo
        val items = info.visibleItemsInfo
        val item = items.firstOrNull { it.key == current.id } ?: items.firstOrNull() ?: return LevelHold(current, current, 0)
        val anchor = before.firstOrNull { it.id == item.key } ?: current
        return LevelHold(current, anchor, item.offset - info.viewportStartOffset)
    }
    val scroll = state.listScroll.value
    val ids = before.map { it.id }.toSet()
    val tops = state.rowTops.filterKeys { it in ids }
    val currentTop = tops[current.id]
    val inView = currentTop != null && currentTop >= scroll && currentTop < scroll + state.listViewport.coerceAtLeast(1)
    val anchorId = if (inView) current.id else tops.filterValues { it >= scroll }.minByOrNull { it.value }?.key
    val anchor = before.firstOrNull { it.id == anchorId } ?: current
    return LevelHold(current, anchor, (tops[anchor.id] ?: scroll) - scroll)
}

/**
 * For a search at a picked time: the journey that fits it best. It does not move on
 * as the clock does. Null for a search that simply starts now.
 */
fun anchorJourney(journeys: List<Journey>, time: TimeChoice?): Journey? = when {
    time == null -> null
    time.arrival -> journeys.lastOrNull { !it.arrival.isAfter(time.time) } ?: journeys.lastOrNull()
    else -> journeys.firstOrNull { !it.departure.isBefore(time.time) } ?: journeys.firstOrNull()
}

/**
 * The journey a page shows before the user picked one: the one that fits the picked
 * time, or for a search from now the next one that has not left yet.
 */
fun defaultJourney(journeys: List<Journey>, time: TimeChoice?, now: OffsetDateTime): Journey? =
    anchorJourney(journeys, time)
        ?: journeys.firstOrNull { !it.departure.isBefore(now) }
        ?: journeys.firstOrNull()

// Frames another transfer level waits at most for the list to take its new height.
private const val LEVEL_SETTLE_FRAMES = 30

// The planning view offers this many journeys as cards and ways on the map.
private const val PLAN_OPTIONS = 6

/**
 * The journeys the planning view offers: a few from the default one on, or up to it
 * for an arrival time. A journey the user picked is always among them, also an earlier
 * one that was loaded later on.
 */
fun planWindow(journeys: List<Journey>, time: TimeChoice?, selectedId: String?, now: OffsetDateTime): List<Journey> {
    if (journeys.size <= PLAN_OPTIONS) return journeys
    val lead = defaultJourney(journeys, time, now)?.let { journeys.indexOf(it) } ?: 0
    var first = if (time?.arrival == true) lead - (PLAN_OPTIONS - 1) else lead
    val picked = journeys.indexOfFirst { it.id == selectedId }
    if (picked >= 0) {
        if (picked < first) first = picked
        if (picked > first + PLAN_OPTIONS - 1) first = picked - (PLAN_OPTIONS - 1)
    }
    first = first.coerceIn(0, journeys.size - PLAN_OPTIONS)
    return journeys.subList(first, first + PLAN_OPTIONS)
}

/**
 * Says that journeys were found but the chips hide all of them ([count] in the categories
 * [levels]), and offers them back with [onShow].
 */
@Composable
fun FilteredJourneysCard(
    count: Int,
    levels: Set<TransferComfort>,
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val names = TransferComfort.entries.filter { it in levels }.map { levelName(it) }
            Text(
                text = pluralStringResource(
                    R.plurals.connections_filtered,
                    count,
                    count,
                    names.joinToString(", "),
                ),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.connections_show_filtered),
                onClick = onShow,
                minHeight = 40.dp,
            )
        }
    }
}

/** The name of a category of connections, as its small tag says it. */
@Composable
private fun levelTag(level: TransferComfort): String = stringResource(
    when (level) {
        TransferComfort.Relaxed -> R.string.level_tag_relaxed
        TransferComfort.Normal -> R.string.level_tag_normal
        TransferComfort.Risky -> R.string.level_tag_risky
        TransferComfort.VeryRisky -> R.string.level_tag_very_risky
    },
)

/** The ground and the ink of the tag of a category: calm green, neutral, or the red of a risk. */
private fun levelColors(level: TransferComfort): Pair<Color, Color> = when (level) {
    TransferComfort.Relaxed -> AppColors.CalmContainer to AppColors.CalmInk
    TransferComfort.Normal -> AppColors.Chip to AppColors.Ink
    TransferComfort.Risky -> AppColors.DelayContainer to AppColors.DelayInk
    TransferComfort.VeryRisky -> AppColors.VeryRiskyContainer to AppColors.VeryRiskyInk
}

/** The name of a category of journeys, as on its chip. */
@Composable
private fun levelName(level: TransferComfort): String = stringResource(levelNameRes(level))

/** The string of the name of [level] ("Relaxed", "Normal", "Knapp", "Sprint"). */
internal fun levelNameRes(level: TransferComfort): Int = when (level) {
    TransferComfort.Relaxed -> R.string.comfort_relaxed
    TransferComfort.Normal -> R.string.comfort_normal
    TransferComfort.Risky -> R.string.comfort_risky
    TransferComfort.VeryRisky -> R.string.comfort_very_risky
}

/** The strong colour of a level: the colour its changes have in the chart. */
fun levelStrongColor(level: TransferComfort): Color = tightnessColor(
    when (level) {
        TransferComfort.Relaxed -> Tightness.Comfortable
        TransferComfort.Normal -> Tightness.Tight
        TransferComfort.Risky -> Tightness.Risky
        TransferComfort.VeryRisky -> Tightness.VeryRisky
    },
)

/**
 * The single choice how tight changes may be, as a slim bar of four equal segments without gaps
 * (Relaxed, Normal, Knapp, Sprint) with the four names centred below them. The choice is
 * additive: a journey is shown when its category is the picked [level] or a more relaxed one,
 * so the bar fills from the left up to the picked segment, each segment in the strong colour of
 * its level (the colour its changes have in the chart), and stays pale glass beyond. The fill
 * sweeps to a new choice. The picked name is bold, the others muted. With [tighterCount] above 0
 * the name of [tighterLevel] (by default the next tighter one) is followed by a blue "+N", the
 * journeys that pick would add; tapping it calls [onTighter]. Any other level is picked with a
 * tap on its segment or name, and a drag across the row picks the level under the finger, with
 * a haptic tick at each new one. To accessibility services the row is a radio group. [height]
 * is the least height of the whole control (bar and names); it keeps its own row.
 */
@Composable
fun LevelSelector(
    level: TransferComfort,
    onLevel: (TransferComfort) -> Unit,
    modifier: Modifier = Modifier,
    tighterCount: Int = 0,
    tighterLevel: TransferComfort? = null,
    onTighter: () -> Unit = {},
    height: Dp = 40.dp,
) {
    val entries = TransferComfort.entries
    val haptics = LocalHapticFeedback.current
    val currentLevel by rememberUpdatedState(level)
    val currentOnLevel by rememberUpdatedState(onLevel)
    // Every new pick from this row, by tap or by drag, gives the same small tick.
    val pick: (TransferComfort) -> Unit = { entry ->
        if (entry != currentLevel) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLevel(entry)
        }
    }
    val currentPick by rememberUpdatedState(pick)
    val offerLevel = tighterLevel ?: entries.getOrNull(level.ordinal + 1)
    val label = stringResource(R.string.transfer_filter_question)
    // How far the bar is filled, in segments: 1 for Relaxed up to 4 for Sprint. It sweeps.
    val fill by animateFloatAsState(
        targetValue = (level.ordinal + 1).toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "levelFill",
    )
    Row(
        modifier = modifier
            .heightIn(min = height)
            .semantics { contentDescription = label }
            .selectableGroup()
            // A drag across the row picks the level under the finger. The segments share the
            // width equally, so the share of the width is the index.
            .pointerInput(Unit) {
                // The level last picked by this drag: a pick is only passed on when the finger
                // reaches another segment, not again for every move before the page has caught up.
                var last: TransferComfort? = null
                fun pickAt(x: Float) {
                    val width = size.width.coerceAtLeast(1)
                    val entry = entries[(x / width * entries.size).toInt().coerceIn(0, entries.lastIndex)]
                    if (entry != last) {
                        last = entry
                        currentPick(entry)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        last = currentLevel
                        pickAt(offset.x)
                    },
                ) { change, _ ->
                    change.consume()
                    pickAt(change.position.x)
                }
            },
        verticalAlignment = Alignment.Top,
    ) {
        entries.forEachIndexed { index, entry ->
            val offered = tighterCount > 0 && entry == offerLevel && entry.ordinal > level.ordinal
            LevelSegment(
                entry = entry,
                // The share of this segment that is filled: 0 to 1, as the sweep passes it.
                fill = (fill - index).coerceIn(0f, 1f),
                first = index == 0,
                last = index == entries.lastIndex,
                picked = entry == level,
                included = entry.ordinal < level.ordinal,
                offerCount = if (offered) tighterCount else 0,
                onClick = {
                    if (offered) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTighter()
                    } else {
                        pick(entry)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// The height of the bar of [LevelSelector]: slim, fully rounded at its ends.
private val LEVEL_BAR = 16.dp

/**
 * One level of [LevelSelector]: its segment of the bar, filled by [fill] in the strong colour of
 * its level over pale glass, and its name centred below, bold when [picked]. With [offerCount]
 * above 0 a blue "+N" follows the name. Only the [first] and the [last] segment are rounded, so
 * the four read as one bar.
 */
@Composable
private fun LevelSegment(
    entry: TransferComfort,
    fill: Float,
    first: Boolean,
    last: Boolean,
    picked: Boolean,
    included: Boolean,
    offerCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strong = levelStrongColor(entry)
    val pale = AppColors.Chip
    val ink by animateColorAsState(
        if (picked) AppColors.Ink else AppColors.Muted,
        Motion.settle(),
        label = "levelNameInk",
    )
    // The "+N" fades in and out; the last count stays while it fades, so it never reads "+0".
    val lastCount = remember { intArrayOf(offerCount) }
    if (offerCount > 0) lastCount[0] = offerCount
    val offerAlpha by animateFloatAsState(
        targetValue = if (offerCount > 0) 1f else 0f,
        animationSpec = if (offerCount > 0) Motion.fadeIn() else Motion.fadeOut(),
        label = "levelOffer",
    )
    // A pressed level gives way a little and springs back, as the chips do.
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = Motion.pop(),
        label = "levelPress",
    )
    val name = levelName(entry)
    val offerText = stringResource(R.string.level_tighter, lastCount[0].coerceAtLeast(1))
    val blue = AppColors.Blue
    val text = buildAnnotatedString {
        append(name)
        if (offerAlpha > 0.01f) {
            withStyle(SpanStyle(color = blue.copy(alpha = blue.alpha * offerAlpha), fontWeight = FontWeight.ExtraBold)) {
                append(" ")
                append(offerText)
            }
        }
    }
    val state = when {
        picked -> null
        included -> stringResource(R.string.level_included)
        offerCount > 0 -> pluralStringResource(R.plurals.level_tighter_more, offerCount, offerCount)
        else -> null
    }
    val round = LEVEL_BAR / 2
    val barShape = RoundedCornerShape(
        topStart = if (first) round else 0.dp,
        bottomStart = if (first) round else 0.dp,
        topEnd = if (last) round else 0.dp,
        bottomEnd = if (last) round else 0.dp,
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .selectable(
                selected = picked,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interactions,
                indication = null,
            )
            .semantics {
                contentDescription = name
                if (state != null) stateDescription = state
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LEVEL_BAR)
                .clip(barShape)
                .background(pale)
                .drawBehind {
                    // The fill grows from the left edge of the segment as the sweep passes it.
                    if (fill > 0f) drawRect(color = strong, size = Size(size.width * fill, size.height))
                },
        )
        // One line always: on a narrow screen the name shrinks a little before it is cut.
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            color = { ink },
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clearAndSetSemantics { },
        )
    }
}

/** Tells the offers of connecting departures apart: one per journey and change. */
fun offerKey(journeyId: String, keepLegs: Int): String = "$journeyId#$keepLegs"

// A journey has to stay picked for this long before its connecting departures are asked for,
// and while it stays picked they are asked for again this often: a delay on either side
// changes which of them can be reached.
private const val OFFER_SETTLE_MILLIS = 700L
private const val OFFER_REFRESH_MILLIS = 45_000L

// The height the detail card keeps when it is pulled all the way down: its handle.
private val DETAIL_HANDLE = 26.dp

// How much the chart's columns fade at its sides: a light touch, not a vanishing edge.
private const val SIDE_FADE_DEPTH = 0.45f

// Below this share of the height the overview is left out entirely.
private const val OVERVIEW_GONE = 0.01f

// How far the overview slides up while it fades out.
private val OVERVIEW_SLIDE = 24.dp

/**
 * The connections of one search as a chart, or with [listView] as a list, and below it
 * the picked journey in detail. [onLoadOffers] asks for the connecting departures at the
 * changes of a journey, with true also for those that are loaded already. [offers] holds
 * them, by [offerKey]; [onAdapt] swaps the rest of a journey from a change on for one of
 * them, and [onUndo] takes that back for the journeys in [adaptedIds]. [onCourse] shows
 * the whole line of a ride. [levels] holds the category of each journey by its id, shown as
 * its tag; [comfort] is the tightest level shown, and [tighterCount] journeys of the next
 * tighter level with hidden journeys ([tighterLevel]) are offered with [onShowFiltered].
 * With [fullScreen] the overview has the whole height and a picked journey opens on a page
 * of its own; going back from there returns to the overview as it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    fromName: String,
    toName: String,
    journeys: List<Journey>?,
    loading: Boolean,
    error: String?,
    now: OffsetDateTime,
    state: ConnectionsState,
    time: TimeChoice?,
    onTime: () -> Unit,
    comfort: TransferComfort,
    onComfort: (TransferComfort) -> Unit,
    everyMinute: Boolean,
    onEveryMinute: (Boolean) -> Unit,
    via: Via?,
    onAddVia: () -> Unit,
    onEditVia: () -> Unit,
    onRemoveVia: () -> Unit,
    levels: Map<String, TransferComfort>?,
    onBack: () -> Unit,
    filteredCount: Int = 0,
    filteredLevels: Set<TransferComfort> = emptySet(),
    tighterCount: Int = 0,
    tighterLevel: TransferComfort? = null,
    onShowFiltered: () -> Unit = {},
    onPickOrigin: () -> Unit,
    onLoadEarlier: () -> Unit,
    onLoadLater: () -> Unit,
    // Pages on their way at the edges of the chart and how the last ones ended (see ChartEdges.kt).
    edges: EdgeStatus = EdgeStatus(),
    routeSaved: Boolean,
    onToggleRoute: () -> Unit,
    stopsFailedId: String?,
    onLoadStops: (Journey) -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
    onStartTrip: (Journey) -> Unit,
    listView: Boolean,
    offers: Map<String, Alternatives>,
    onLoadOffers: (Journey, Boolean) -> Unit,
    onAdapt: (Journey, Int, Journey) -> Unit,
    adaptedIds: Set<String>,
    onUndo: (Journey) -> Unit,
    onCourse: (Leg) -> Unit,
    onFocusWalk: (Leg) -> Unit = {},
    fullScreen: Boolean = false,
    productsOff: Set<String> = emptySet(),
    onAllProducts: () -> Unit = {},
    searchOptions: SearchOptions? = null,
    onSearchOptions: () -> Unit = {},
    onTicketOff: (TicketFilter) -> Unit = {},
    levelChoice: Boolean = true,
    onRememberPattern: (Journey) -> Unit = {},
    patternTitle: String? = null,
    patternNote: String? = null,
    onClearPattern: () -> Unit = {},
    refreshing: Boolean = false,
    // Asking again for the journeys shown failed: they stay, with a note and a retry (#59).
    stale: Boolean = false,
    onRefresh: (asked: Boolean) -> Job? = { null },
    // False while the app is not on screen: a failed search is not asked again meanwhile.
    active: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AllocProbe.tick(AllocProbe.CONNECTIONS)
    val scope = rememberCoroutineScope()
    val detailShare = state.detailShare
    // The auto test of the performance lab goes back from a journey it opened (debug and staging only).
    if (PerfLab.available) {
        DisposableEffect(state) {
            val close: () -> Unit = { state.detailPage = false }
            ChartOpenProbe.close = close
            onDispose { if (ChartOpenProbe.close === close) ChartOpenProbe.close = null }
        }
    }
    var shareJob by remember { mutableStateOf<Job?>(null) }
    // A journey that is about to open on its own page (see warmJourneyPage).
    val openJob = remember { arrayOfNulls<Job>(1) }
    // Another transfer level (a tap here, or "Undo"): what was in view is noted while the overview
    // is still laid out with the journeys of the level before, and kept in place once the new ones
    // are (see the effect at the overview).
    val levelSeen = remember { arrayOf<Any?>(comfort, journeys) }
    if (levelSeen[0] != comfort) {
        @Suppress("UNCHECKED_CAST")
        val before = levelSeen[1] as? List<Journey>
        state.levelHold = before?.takeIf { it.isNotEmpty() }?.let { levelHoldOf(state, it, time, now, listView) }
    }
    levelSeen[0] = comfort
    levelSeen[1] = journeys
    // Carries the border between chart and detail card to a new place with the spring of the
    // sheet of the start page: it goes a little too far and settles, so the card feels like it
    // has weight. [velocity] is how fast the border moved when it was let go, in shares per second.
    val moveShare: (Float, Float) -> Unit = { target, velocity ->
        shareJob?.cancel()
        shareJob = scope.launch {
            animate(
                initialValue = detailShare.floatValue,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = SHEET_DAMPING, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> detailShare.floatValue = value }
        }
    }

    // From WIDE_FROM on, overview and detail card stand side by side instead of sharing the height.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Held sideways on a phone the page is too low to stack its parts: the header and the
        // choices go into a scrolling panel at the side, and the chart (or the list) has the
        // whole height next to it; a picked journey opens there on a page of its own.
        val side = sideBySide(maxWidth, maxHeight)
        val wide = !side && screenRoom(maxWidth, maxHeight) == ScreenRoom.Wide
        val pagewise = fullScreen || side
        // The page of one journey: back leads to the overview, not out of the search.
        val detailPage = pagewise && state.detailPage
        // Back from it is predictive: the overview lies underneath while the page shrinks.
        val detailBack = PredictiveNestedBackHandler(enabled = detailPage, label = "journeyPage") { state.detailPage = false }
        // Under a back gesture from the page (and the fade or the springing back after it) the
        // overview lies underneath as it will be, with the chips of the search above it. Nothing
        // is laid out anew for that: the page stays collapsed, so the page of the journey keeps
        // the very room and place it had (see StackedHeader), and the chips are only drawn in
        // their place behind it, the overview under the page moved down below them by
        // [headPlace] (see underHead). Once the gesture is over, the header takes that room
        // for real and the overview is where it was drawn.
        val headBack = detailBack.active && !side
        val headPlace = remember { mutableIntStateOf(0) }
        // The chips, the transfer levels and a remembered connection above the overview fade
        // while the page of a journey grows, and the page and the overview slide up over them
        // (see StackedHeader); on the way back they come in again as the page shrinks.
        val headerAnimates = rememberAnimationsOn()
        val headerTarget = if ((detailPage || headBack) && !side) 1f else 0f
        val headerAnim = remember { Animatable(headerTarget) }
        // Whether a back gesture was on at the last composition: when it has just ended (let go
        // or called off), the header is where it ends at once, as under the gesture.
        val backWas = remember { BooleanArray(1) }
        val backEnded = backWas[0] && !detailBack.active
        SideEffect { backWas[0] = detailBack.active }
        // Back never animates the header: under the gesture the chips are only drawn behind the
        // page (see headBack), and once it ends the header is where the overview was drawn.
        val headerSnaps = headBack || backEnded || !headerAnimates
        // A snapped header holds its end value from this very frame on (not one frame late,
        // when the effect has snapped the animation), so the overview (underHead) and its
        // place (StackedHeader) agree in every frame.
        val headerOut: State<Float> = remember(headerSnaps, headerTarget) {
            if (headerSnaps) mutableFloatStateOf(headerTarget) else headerAnim.asState()
        }
        LaunchedEffect(detailPage, headBack, detailBack.active, side) {
            if (headerSnaps) {
                headerAnim.snapTo(headerTarget)
            } else {
                headerAnim.animateTo(
                    headerTarget,
                    tween(if (detailPage) PAGE_GROW_MILLIS else PAGE_SHRINK_MILLIS, easing = EmphasizedEasing),
                )
            }
        }
        val headerGone by remember(headerOut) { derivedStateOf { headerOut.value >= 1f } }
        // While the header moves, the page of the journey grows out of its column or shrinks back
        // into it: the chart underneath stands still meanwhile, as under the page.
        val headerMoving by remember(headerOut) { derivedStateOf { headerOut.value > 0f && headerOut.value < 1f } }
        val headerShown = !detailPage || side || headBack || !headerGone
        SplitOrStack(
            side = side,
            paneWidth = (maxWidth * 0.4f).coerceIn(260.dp, SIDE_PANEL_MAX),
            collapsed = detailPage || headBack,
            headerOut = headerOut,
            headerUnder = headBack,
            headerGrow = detailBack,
            headerRoom = headPlace,
            modifier = Modifier
                .fillMaxSize()
                // Room for the floating tab bar.
                .padding(start = 12.dp, end = 12.dp, bottom = LocalNavInset.current),
            top = {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundButton(
                    glyph = Glyph.Back,
                    description = stringResource(R.string.back),
                    onClick = { if (detailPage) state.detailPage = false else onBack() },
                )
                Column(modifier = Modifier.weight(1f)) {
                    // The start, and a place on the way set for the search right after it in the
                    // same line: no row of its own.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The address of "Aktueller Standort" is looked up (#207): the train of the search.
                        if (HereAddress.fromResolving.value) LoadingRow(
                            text = stringResource(R.string.r5search_contact_resolving),
                            compact = true,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClick = onPickOrigin)
                                .padding(top = 2.dp, end = 8.dp),
                        ) else Text(
                            text = stringResource(R.string.from_label, fromName),
                            color = AppColors.Blue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClick = onPickOrigin)
                                .padding(top = 6.dp, bottom = 2.dp, end = 8.dp),
                        )
                        if (via != null) {
                            ViaLine(
                                via = via,
                                onAdd = onAddVia,
                                onEdit = onEditVia,
                                onRemove = onRemoveVia,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                    if (HereAddress.toResolving.value) LoadingRow(
                        text = stringResource(R.string.r5search_contact_resolving),
                        compact = true,
                    ) else Text(
                        text = toName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // A place on the way is added with a labelled chip ("Über …"); once set it shows in the line of the start.
                if (via == null && !detailPage) ViaButton(onAdd = onAddVia)
            }
            },
            header = {
            // The chips of the search in one row, and below it, across the whole width, the
            // choice how tight changes may be. The choice has its own row so a long time chip
            // ("Fr., 27. Nov., 11:26") can never squeeze it; the chips scroll sideways if they
            // need more room than there is. No caption above the pills: they name their levels
            // in the colours of the chart's tags, and only those who switched the choice on in
            // the settings see it at all; the caption is still read out to accessibility services.
            // The page of one journey leaves all of it out.
            if (headerShown) Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val chipScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadeEdges(chipScroll, horizontal = true)
                        .horizontalScroll(chipScroll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // How old the live times of the journeys found are, first so it is always in
                    // view over the chart and the list (#12); a tap asks for the journeys again,
                    // as a pull does.
                    if (!loading && !journeys.isNullOrEmpty()) {
                        FreshnessChip(source = LiveSource.Journeys, onRefresh = { onRefresh(true) }, refreshing = refreshing, minHeight = 36.dp)
                    }
                    // The same order as on the map with the options.
                    TimeChip(choice = time, onClick = onTime, minHeight = 36.dp)
                    EveryMinuteChip(active = everyMinute, onToggle = onEveryMinute, minHeight = 36.dp)
                    // Kinds of transport, Deutschlandticket and bicycle; kept for the next searches.
                    if (searchOptions != null) SearchOptionsChip(options = searchOptions, onClick = onSearchOptions, minHeight = 36.dp)
                    // Kinds of transport that are off: easy to forget, and otherwise it looks as if
                    // the provider had found no feeder bus. A compact chip in this row, no row of its own.
                    ProductsOffChip(off = productsOff, onUseAll = onAllProducts, minHeight = 36.dp)
                    // Deutschlandticket, bicycle: also easy to forget while on.
                    searchOptions?.let { ticketFilters(it) }.orEmpty().forEach { filter ->
                        TicketFilterChip(filter = filter, onOff = { onTicketOff(filter) }, minHeight = 36.dp)
                    }
                }
                // The tightest level shown; the next tighter one with hidden journeys shows how many it would add.
                // Without the setting "choose transfer times" the page shows normal and relaxed journeys and asks nothing.
                if (levelChoice) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        LevelSelector(
                            level = comfort,
                            onLevel = onComfort,
                            tighterCount = if (journeys.isNullOrEmpty()) 0 else tighterCount,
                            tighterLevel = tighterLevel,
                            onTighter = onShowFiltered,
                            modifier = Modifier.weight(1f),
                        )
                        // How the levels are rated, and their buffers.
                        TransferRatingInfoButton()
                    }
                }
            }

            // A remembered connection: its name, what the search found for it, and a way back to all journeys.
            if (patternTitle != null && headerShown) {
                PatternBanner(title = patternTitle, note = patternNote, onClear = onClearPattern)
            }
            },
            rest = {
            // Pulled down at its top, the chart or the list (or the message of a failed search)
            // asks for the journeys again; not under the page of one journey.
            val pullState = rememberPullToRefreshState()
            val pullTicks = rememberTicks()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pullToRefresh(
                        isRefreshing = refreshing,
                        state = pullState,
                        enabled = !loading && !detailPage && (pagewise || error != null),
                        onRefresh = {
                            pullTicks.refresh()
                            onRefresh(true)
                        },
                    ),
            ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // A failed search is asked again by itself while it is shown: after 5 s, 15 s, 30 s,
            // then every minute, each once the one before has ended (see screenRetryWaitMillis). Any
            // answer clears the message. Only while the app is on screen: back on it, the retries
            // start over.
            AutoRetry(enabled = error != null && !loading && active) { onRefresh(false) }
            // A search through a place on the way that found nothing soon says so (#35a).
            if (!loading && error == null) ViaDayNoteCard()
            // Asking again failed on a flaky network: the journeys shown stay, with a note and a
            // button; it is asked again by itself as the failed search is (#59).
            if (stale && !loading && error == null && !journeys.isNullOrEmpty()) {
                AutoRetry(enabled = active) { onRefresh(false) }
                RetryCard(
                    text = stringResource(R.string.r5search_refresh_failed),
                    onRetry = { onRefresh(true) },
                    compact = true,
                )
            }
            when {
                loading -> GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    // The little train rides while the search runs.
                    LoadingRow(text = stringResource(R.string.searching_connections), modifier = Modifier.padding(20.dp))
                }

                // Scrollable, so a pull anywhere on the page reaches the pull to refresh.
                error != null -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    GlassSurface(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(text = problemText(error), color = AppColors.Muted)
                            SecondaryButton(
                                text = stringResource(R.string.r5search_retry),
                                onClick = { onRefresh(true) },
                                minHeight = 44.dp,
                            )
                        }
                    }
                }

                // Connections were found, but the chips hide all of them: say so, and offer them back.
                journeys.isNullOrEmpty() && filteredCount > 0 -> FilteredJourneysCard(
                    count = filteredCount,
                    levels = filteredLevels,
                    onShow = onShowFiltered,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Only a search that really found nothing says so.
                journeys.isNullOrEmpty() -> GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.no_connections), modifier = Modifier.padding(20.dp))
                }

                else -> {
                    // The pick counts whenever that journey is loaded, also one that has left already.
                    val current = journeys.firstOrNull { it.id == state.selectedId }
                        ?: defaultJourney(journeys, time, now)
                        ?: journeys.first()
                    // Picking a journey lifts the detail card; picking it again lowers the card.
                    val select: (String) -> Unit = { id ->
                        val lower = id == current.id && detailShare.floatValue > (DETAIL_START + DETAIL_OPEN) / 2
                        state.selectedId = id
                        moveShare(if (lower) DETAIL_START else DETAIL_OPEN, 0f)
                    }
                    // Another transfer level keeps the overview on the connection it showed (or the
                    // nearest one) instead of jumping to its start; the journey in front stays the
                    // same where it is still shown.
                    LaunchedEffect(comfort) {
                        val hold = state.levelHold ?: return@LaunchedEffect
                        state.levelHold = null
                        val keep = levelAnchor(hold.current, journeys)
                        if (keep != null && (state.selectedId != null || defaultJourney(journeys, time, now)?.id != keep.id)) {
                            state.selectedId = keep.id
                        }
                        val target = levelAnchor(hold.anchor, journeys) ?: return@LaunchedEffect
                        // The overview has to be laid out with the new journeys first.
                        withFrameNanos { }
                        if (!listView) {
                            val index = journeys.indexOfFirst { it.id == target.id }
                            if (index < 0) return@LaunchedEffect
                            state.chartState.scrollToItem(index)
                            if (hold.offset != 0) state.chartState.scrollBy(-hold.offset.toFloat())
                        } else {
                            val top = state.rowTops[target.id] ?: return@LaunchedEffect
                            val want = (top - hold.offset).coerceAtLeast(0)
                            // The list takes its new height softly: wait until it is high enough.
                            var frames = 0
                            while (state.listScroll.maxValue < want && frames++ < LEVEL_SETTLE_FRAMES) withFrameNanos { }
                            state.listScroll.scrollTo(want.coerceAtMost(state.listScroll.maxValue))
                        }
                    }
                    // The connecting departures of the picked journey are asked for once it has
                    // stayed picked for a moment, so leafing through the chart costs no requests.
                    val loadOffers by rememberUpdatedState(onLoadOffers)
                    val picked by rememberUpdatedState(current)
                    LaunchedEffect(current.id) {
                        delay(OFFER_SETTLE_MILLIS)
                        loadOffers(picked, false)
                        while (true) {
                            delay(BatterySaver.pace(OFFER_REFRESH_MILLIS))
                            loadOffers(picked, true)
                        }
                    }
                    // The chart, or the list, with [onPick] for a tap on a journey.
                    val overview: @Composable (Modifier, (String) -> Unit) -> Unit = { overviewModifier, onPick ->
                        if (!listView) {
                            JourneyChart(
                                journeys = journeys,
                                listState = state.chartState,
                                upDown = state.chartUpDown,
                                earlierPrimed = state.earlierPrimed,
                                anchored = state.anchored,
                                // Three columns before it, so the fitting journey is the last one in full view.
                                startIndex = if (time == null) 0 else (journeys.indexOf(current) - 3).coerceAtLeast(0),
                                timed = time != null,
                                selectedId = current.id,
                                levels = levels,
                                now = now,
                                onSelect = onPick,
                                onLoadEarlier = onLoadEarlier,
                                onLoadLater = onLoadLater,
                                edges = edges,
                                modifier = overviewModifier,
                                // The journey opens on its own page: the chart stays as it is under it,
                                // from the tap on (see ConnectionsState.opening).
                                // Under a back gesture from the page it is the overview as it will be.
                                still = (detailPage && !detailBack.active) || (pagewise && (state.opening || headerMoving)),
                            )
                        } else {
                            // The list starts with the next departure; earlier ones live in the chart.
                            // A picked journey stays in the list after it has left, so the pick is never lost.
                            // A search at a picked time shows all it found, whatever the clock says.
                            val upcoming = listedJourneys(journeys, timed = time != null, earlierShown = state.listEarlier, now = now, currentId = current.id)
                            JourneyList(
                                // The row on top asks for earlier ones, as the chart's edge does (#150, see loadEarlier).
                                earlier = ListEarlier(loading = edges.loadingEarlier) {
                                    state.listEarlier = true
                                    onLoadEarlier()
                                },
                                journeys = upcoming,
                                selected = upcoming.indexOf(current),
                                levels = levels,
                                now = now,
                                onSelect = { index -> onPick(upcoming[index].id) },
                                scroll = state.listScroll,
                                rowTops = state.rowTops,
                                modifier = overviewModifier.onSizeChanged { state.listViewport = it.height },
                            )
                        }
                    }
                    if (pagewise) {
                        // The journey on its own page grows out of the row or column it was
                        // opened from, and shrinks back into it on the way back.
                        // pageOrigin is in the box of the page (see growFromColumn), pageBox where that box lies in the root.
                        val pageOrigin = remember { arrayOfNulls<Rect>(1) }
                        val pageBox = remember { arrayOfNulls<Rect>(1) }
                        // The jank meter follows this change of page too (see JankTransitions.kt).
                        JankMeter.noteShown("journey", if (detailPage) "Detail" else "Connections")
                        DisposableEffect(Unit) { onDispose { JankMeter.forgetShown("journey") } }
                        // A back gesture seeks the change to the overview as it goes (see rememberBackSeek).
                        val journeyPageSeek = rememberBackSeek(detailPage, detailBack, backTarget = false, label = "journeyPage")
                        JankTransitionProbe(journeyPageSeek)
                        journeyPageSeek.AnimatedContent(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .boundsInto(pageBox),
                            transitionSpec = {
                                when {
                                    // The page stays opaque while held and fades by itself once let go (backFront).
                                    detailBack.active -> backContentTransform(detailBack, hold = true)
                                    // Not clipped: a page that closes is drawn above its box while the chips come back (headerReturnLift).
                                    pageOrigin[0] == null -> fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                                    // The page grows out of its column and fades in as it does (see
                                    // growFromColumn); the chart stays under it, still, and goes once
                                    // the page covers it.
                                    targetState -> EnterTransition.None togetherWith
                                        fadeOut(tween(PAGE_GROW_MILLIS / 2, delayMillis = PAGE_GROW_MILLIS / 2))
                                    // Closing, the page shrinks above the overview that is back underneath.
                                    else -> (
                                        fadeIn(tween(PAGE_SHRINK_MILLIS / 2)) togetherWith
                                            fadeOut(tween(PAGE_SHRINK_MILLIS / 3, delayMillis = PAGE_SHRINK_MILLIS * 2 / 3)) using
                                            SizeTransform(clip = false)
                                        )
                                        .apply { targetContentZIndex = -1f }
                                }
                            },
                        ) { open ->
                            if (open) {
                                // A back gesture that was let go fades the page by itself (backFront) and
                                // ends (detailBack.active false) a frame or two before the transition has
                                // taken the page away: without the gesture's look in those frames the page
                                // stood there whole again, over the overview, for a frame (#112). It stays
                                // hidden until it is gone; a gesture called off leaves it as it was.
                                val backSeen = remember { BooleanArray(1) }
                                backSeen[0] = backLeftSeen(detailBack.active, closed = !detailPage, seenBefore = backSeen[0])
                                val goneAfterBack = backLeftHidden(detailBack.active, backSeen[0])
                                JourneyDetail(
                                    journey = current,
                                    fromName = fromName,
                                    toName = toName,
                                    now = now,
                                    routeSaved = routeSaved,
                                    onToggleRoute = onToggleRoute,
                                    stopsFailed = stopsFailedId == current.id,
                                    onLoadStops = { onLoadStops(current) },
                                    onStopAction = onStopAction,
                                    onStartTrip = { onStartTrip(current) },
                                    onRememberPattern = { onRememberPattern(current) },
                                    onHandle = {},
                                    offers = offers,
                                    onAdapt = { keepLegs, option -> onAdapt(current, keepLegs, option) },
                                    adapted = current.id in adaptedIds,
                                    onUndo = { onUndo(current) },
                                    onCourse = onCourse,
                                    onFocusWalk = onFocusWalk,
                                    onDrag = {},
                                    onDragEnd = {},
                                    handle = false,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .jankPageDrawn("Detail")
                                        // Where the page lies, before any shrinking (see BackProbe).
                                        .then(if (BackLog.on) Modifier.onGloballyPositioned { BackProbe.top("layer:journeyPage", it) } else Modifier)
                                        // Under a back gesture the page shrinks with it instead.
                                        .then(
                                            if (detailBack.active) {
                                                // A page of its own while it shrinks: the overview does not show through
                                                // the glass. The ground under it has the card's own shape, so its
                                                // corners show what really lies behind them, not a ground of another
                                                // shape and colour. Its room and place stay as they were (see headBack).
                                                // Its corners stay as round on the screen as those of the chart
                                                // card it goes back into (CARD_CORNER) while it shrinks, instead
                                                // of shrinking with it (and its shadow no longer shows in them).
                                                Modifier
                                                    .backFront(detailBack, CARD_CORNER.value)
                                                    .background(AppColors.PlainGround, CARD_SHAPE)
                                            } else if (goneAfterBack) {
                                                Modifier.graphicsLayer { alpha = 0f }
                                            } else {
                                                // Closing, the overview below stands still (#249): the page
                                                // comes down with the chips by itself (headerReturnLift).
                                                Modifier
                                                    .graphicsLayer {
                                                        translationY = headerReturnLift(headPlace.intValue, headerOut.value, closing = !detailPage && !side)
                                                    }
                                                    .growFromColumn(this, pageOrigin[0])
                                            },
                                        ),
                                )
                            } else {
                                // The overview alone; a tap opens the journey on its own page.
                                overview(Modifier.fillMaxSize().jankPageDrawn("Connections").then(if (headBack) Modifier.underHead(headPlace).backUnder(detailBack) else Modifier)) { id ->
                                    // The column as it lies in the box of the page right now, before the
                                    // header above goes: the page grows out of it wherever the box slides.
                                    val box = pageBox[0]
                                    pageOrigin[0] = OpenOrigin.take()?.let { origin ->
                                        if (box == null) null else origin.translate(-box.left, -box.top)
                                    }
                                    // The pick shows at once; the page follows a moment later, with
                                    // what it asks for first worked out off the main thread meanwhile
                                    // (see warmJourneyPage): the frame of the pick and the first frame
                                    // of the page no longer fall on one.
                                    state.selectedId = id
                                    state.opening = true
                                    val opening = journeys.firstOrNull { it.id == id }
                                    openJob[0]?.cancel()
                                    openJob[0] = scope.launch {
                                        try {
                                            if (opening != null) {
                                                withTimeoutOrNull(PAGE_WARM_MILLIS) {
                                                    withContext(Dispatchers.Default) { warmJourneyPage(opening) }
                                                }
                                            }
                                            if (state.selectedId == id) state.detailPage = true
                                        } finally {
                                            state.opening = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // On a wide screen the overview and the detail card stand side by side, each
                    // with the whole height; a tap on a journey shows it in the card.
                    if (!pagewise && wide) Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        overview(Modifier.weight(1f).fillMaxHeight()) { id -> state.selectedId = id }
                        JourneyDetail(
                            journey = current,
                            fromName = fromName,
                            toName = toName,
                            now = now,
                            routeSaved = routeSaved,
                            onToggleRoute = onToggleRoute,
                            stopsFailed = stopsFailedId == current.id,
                            onLoadStops = { onLoadStops(current) },
                            onStopAction = onStopAction,
                            onStartTrip = { onStartTrip(current) },
                            onRememberPattern = { onRememberPattern(current) },
                            onHandle = {},
                            offers = offers,
                            onAdapt = { keepLegs, option -> onAdapt(current, keepLegs, option) },
                            adapted = current.id in adaptedIds,
                            onUndo = { onUndo(current) },
                            onCourse = onCourse,
                            onFocusWalk = onFocusWalk,
                            onDrag = {},
                            onDragEnd = {},
                            handle = false,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    // The overview and the detail card share the height. The head of the detail
                    // card moves the border between them; chart and list scroll by themselves.
                    if (!pagewise && !wide) BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        // The gap between the cards is not shared out between them.
                        val fixedHeight = with(LocalDensity.current) { 12.dp.toPx() }
                        val totalHeight = (constraints.maxHeight - fixedHeight).coerceAtLeast(1f)
                        // Pulled all the way down, the card is only its handle and the chart has the rest.
                        val handleShare = (with(LocalDensity.current) { DETAIL_HANDLE.toPx() } / totalHeight)
                            .coerceAtMost(DETAIL_MIN)
                        // Pulled all the way up, the card has the whole height and the overview is gone.
                        val fullShare = 1f
                        // Takes a drag in pixels (up is negative) and returns how much of it was used. A drag
                        // on the head of the card reaches up to [fullShare]; scrolling inside it grows the
                        // card up to DETAIL_MAX only, and never pulls a card that is higher down to that.
                        val shiftUpTo: (Float, Float) -> Float = remember(totalHeight, handleShare, state) {
                            { delta, limit ->
                                shareJob?.cancel()
                                val before = detailShare.floatValue
                                val after = (before - delta / totalHeight).coerceIn(handleShare, maxOf(limit, before))
                                detailShare.floatValue = after
                                (before - after) * totalHeight
                            }
                        }
                        val shift: (Float) -> Float = remember(shiftUpTo) { { delta -> shiftUpTo(delta, DETAIL_MAX) } }
                        // Let go, the border carries on a little in the way it was moving ([velocity] in
                        // pixels per second, down positive) and comes to rest with a spring. Below the
                        // card's smallest readable size it settles there or on its handle; above its
                        // largest, there or at full height.
                        val settle: (Float) -> Unit = { velocity ->
                            val at = detailShare.floatValue
                            val shareSpeed = -velocity / totalHeight
                            val ahead = (at + shareSpeed * SPLIT_PROJECTION).coerceIn(handleShare, fullShare)
                            val target = when {
                                ahead < DETAIL_MIN -> if (ahead < (handleShare + DETAIL_MIN) / 2f) handleShare else DETAIL_MIN
                                ahead > DETAIL_MAX -> if (ahead > (DETAIL_MAX + fullShare) / 2f) fullShare else DETAIL_MAX
                                else -> ahead
                            }
                            if (target != at || shareSpeed != 0f) moveShare(target, shareSpeed)
                        }
                        val settleNow by rememberUpdatedState(settle)
                        val connection = remember(shift) {
                            object : NestedScrollConnection {
                                // Dragging up first grows the detail card, then scrolls its content.
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                                    if (available.y < 0f) Offset(0f, shift(available.y)) else Offset.Zero

                                // Dragging down first scrolls the content back, then shrinks the card.
                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource,
                                ): Offset =
                                    if (available.y > 0f) Offset(0f, shift(available.y)) else Offset.Zero

                                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                    // A fling of the content moves the border only where it stands between stages.
                                    val at = detailShare.floatValue
                                    if (at < DETAIL_MIN || at > DETAIL_MAX) settleNow(0f)
                                    return Velocity.Zero
                                }
                            }
                        }
                        // The border moves every frame of a drag or of its spring. It is read only while
                        // laying out and drawing (see SplitColumn and the layer below), never while composing:
                        // otherwise every frame would compose the chart (or the list) and the card anew.
                        // Only crossing OVERVIEW_GONE changes what is composed.
                        val overviewGone by remember(handleShare) {
                            derivedStateOf { 1f - detailShare.floatValue.coerceIn(handleShare, fullShare) < OVERVIEW_GONE }
                        }
                        val slide = with(LocalDensity.current) { OVERVIEW_SLIDE.toPx() }
                        SplitColumn(
                            share = { detailShare.floatValue.coerceIn(handleShare, fullShare) },
                            gap = 12.dp,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Above the card's largest readable size the overview fades and slides up, and at
                            // full height it is gone, with no strip of squeezed columns left; pulled down, it
                            // comes back the same way.
                            if (!overviewGone) Box {
                                overview(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val share = detailShare.floatValue.coerceIn(handleShare, fullShare)
                                            val shown = ((fullShare - share) / (fullShare - DETAIL_MAX)).coerceIn(0f, 1f)
                                            alpha = shown
                                            translationY = -(1f - shown) * slide
                                        },
                                    select,
                                )
                            }
                            Box {
                                JourneyDetail(
                                    journey = current,
                                    fromName = fromName,
                                    toName = toName,
                                    now = now,
                                    routeSaved = routeSaved,
                                    onToggleRoute = onToggleRoute,
                                    stopsFailed = stopsFailedId == current.id,
                                    onLoadStops = { onLoadStops(current) },
                                    onStopAction = onStopAction,
                                    onStartTrip = { onStartTrip(current) },
                                    onRememberPattern = { onRememberPattern(current) },
                                    // A tap on the handle puts the card away, or brings it back.
                                    onHandle = {
                                        val away = detailShare.floatValue < (handleShare + DETAIL_MIN) / 2f
                                        moveShare(if (away) DETAIL_OPEN else handleShare, 0f)
                                    },
                                    offers = offers,
                                    onAdapt = { keepLegs, option -> onAdapt(current, keepLegs, option) },
                                    adapted = current.id in adaptedIds,
                                    onUndo = { onUndo(current) },
                                    onCourse = onCourse,
                                    onFocusWalk = onFocusWalk,
                                    onDrag = { delta -> shiftUpTo(delta, fullShare) },
                                    onDragEnd = { velocity -> settleNow(velocity) },
                                    // Scrolling inside the card first grows it; the overview above is left alone.
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(connection),
                                )
                            }
                        }
                    }
                }
            }
            }
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                color = AppColors.Blue,
            )
            }
            },
        )
    }
}

/**
 * The overview above the detail card, sharing the height as a column with weights would: the
 * card gets [share] of the height left after [gap], the overview the rest, but never less than
 * OVERVIEW_GONE of it. With only one child (the overview gone) the card has the whole height.
 * [share] is read while measuring, so moving the border lays the two out again without
 * composing either of them anew.
 */
@Composable
private fun SplitColumn(
    share: () -> Float,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (measurables.size < 2) {
            val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
            return@Layout layout(width, height) { placeables.forEach { it.place(0, 0) } }
        }
        val detailShare = share()
        val overviewWeight = (1f - detailShare).coerceAtLeast(OVERVIEW_GONE)
        val room = (height - gap.roundToPx()).coerceAtLeast(0)
        val overviewHeight = Math.round(room * overviewWeight / (overviewWeight + detailShare)).coerceIn(0, room)
        val detailHeight = room - overviewHeight
        val overview = measurables[0].measure(Constraints.fixed(width, overviewHeight))
        val detail = measurables[1].measure(Constraints.fixed(width, detailHeight))
        layout(width, height) {
            overview.place(0, 0)
            detail.place(0, height - detailHeight)
        }
    }
}

/**
 * The parts of a page: on top of each other ([top] above [rest]), or with [side] next to each
 * other, [top] in a scrolling panel [paneWidth] wide and [rest] with the whole height beside it.
 */
/**
 * The overview under the page of one journey while a back gesture runs (see headBack): the
 * page stays collapsed, so the overview lies below the chips drawn behind the page, [room]
 * lower (the room of the chips with their gap, as StackedHeader measured it), and is exactly
 * as high as it is at rest, without the page: its card ends where it ends then, with its round
 * corners and all it draws inside them (cut at the bottom of the room of the page, the card
 * lost its lower corners and the band of the picked column showed at the cut). Only the
 * overview is measured for it, which comes with the gesture anyway; the page is not touched,
 * and once the gesture is over the header gives the overview that very room.
 */
private fun Modifier.underHead(room: IntState): Modifier = layout { measurable, constraints ->
    val lower = room.intValue
    if (!constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, lower) }
    }
    val height = (constraints.maxHeight - lower).coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = minOf(constraints.minHeight, height), maxHeight = height))
    layout(placeable.width, constraints.maxHeight) { placeable.place(0, lower) }
}

// The shape of the cards of the page of the connections: the chart and the page of one journey.
private val CARD_CORNER = 32.dp
private val CARD_SHAPE = RoundedCornerShape(CARD_CORNER)

// The gap between the parts of the page of the connections (see SplitOrStack).
private val HEAD_GAP = 12.dp

@Composable
private fun SplitOrStack(
    side: Boolean,
    paneWidth: Dp,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    headerOut: State<Float>? = null,
    headerUnder: Boolean = false,
    headerGrow: BackPreview? = null,
    headerRoom: MutableIntState? = null,
    top: @Composable ColumnScope.() -> Unit,
    header: @Composable ColumnScope.() -> Unit = {},
    rest: @Composable ColumnScope.() -> Unit,
) {
    if (side) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val paneScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .width(paneWidth)
                    .fillMaxHeight()
                    .fadeEdges(paneScroll, horizontal = false)
                    .verticalScroll(paneScroll)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                top()
                header()
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = rest,
            )
        }
    } else {
        StackedHeader(
            modifier = modifier,
            collapsed = collapsed,
            headerOut = headerOut,
            headerUnder = headerUnder,
            headerGrow = headerGrow,
            headerRoomOut = headerRoom,
            top = top,
            header = header,
            rest = rest,
        )
    }
}

/**
 * [top], [header] and [rest] one above the other, 12 dp apart; [rest] takes the height left.
 * While [header] goes ([collapsed], [headerOut] running from 0 to 1) it only fades and [rest]
 * only slides up over it: [rest] is measured once for the height it ends with and then just
 * placed anew each frame, so no frame of the way measures the chart or the page of a journey
 * again. Coming back (1 to 0) [rest] is in its place at once and the header fades in above it
 * (see headerRestOffset, #249). What [rest] has too much below while it slides up is cut off at
 * the bottom. With [headerUnder] (collapsed, under a back gesture) the header is drawn whole
 * in its place behind [rest], which keeps its room and place: nothing is laid out anew; it
 * grows and fades in with the gesture of [headerGrow] about the centre of the card below it,
 * as that card does (see backUnder). The room the header takes with its gap goes to [headerRoom].
 */
@Composable
private fun StackedHeader(
    modifier: Modifier,
    collapsed: Boolean,
    headerOut: State<Float>?,
    headerUnder: Boolean,
    headerGrow: BackPreview?,
    headerRoomOut: MutableIntState?,
    top: @Composable ColumnScope.() -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    rest: @Composable ColumnScope.() -> Unit,
) {
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    Layout(
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = top)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = header)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = rest)
        },
        modifier = modifier.drawWithCache {
            // What [rest] has too much below is cut at the bottom along the round lower corners
            // of its card (CARD_CORNER), where the card ends at rest: cut straight, the chart
            // (and the page growing over it) lost its round lower corners while the header
            // moved. Made again only when the size changes.
            val corner = CARD_CORNER.toPx()
            val cut = Path().apply {
                addRect(Rect(-size.width, -size.height, size.width * 2f, size.height - corner))
                // Its upper corners lie far up, inside the rectangle above.
                addRoundRect(RoundRect(0f, -size.height, size.width, size.height, CornerRadius(corner)))
            }
            onDrawWithContent {
                val out = headerOut?.value ?: 0f
                // Also the frame a collapse starts in, before the header has moved: [rest] is
                // measured for the height without it already and would reach out at the bottom.
                if (out < 1f && (out > 0f || collapsed)) {
                    clipPath(cut) { this@onDrawWithContent.drawContent() }
                } else {
                    drawContent()
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val topPlace = measurables[0].measure(loose)
        val headerPlace = measurables[1].measure(loose)
        val total = constraints.maxHeight
        val headerRoom = if (headerPlace.height > 0) headerPlace.height + gap else 0
        if (headerRoomOut != null && headerRoomOut.intValue != headerRoom) headerRoomOut.intValue = headerRoom
        val restTop = topPlace.height + gap
        // The height the rest ends with: without the header when it goes, else below it.
        val restHeight = (total - restTop - if (collapsed) 0 else headerRoom).coerceAtLeast(0)
        val restPlace = measurables[2].measure(loose.copy(maxHeight = restHeight))
        layout(constraints.maxWidth, total) {
            topPlace.place(0, 0)
            // Read while placing (not while composing): each frame only places anew.
            val out = headerOut?.value ?: if (collapsed) 1f else 0f
            if (headerPlace.height > 0) {
                // Under a back gesture the card below lies [headerRoom] lower, down to the
                // bottom (see underHead): the header grows about its centre with it.
                val pivot = backUnderPivot((restHeight + headerRoom) / 2f, headerPlace.height.toFloat())
                // Placed before [rest], so drawn behind it.
                headerPlace.placeWithLayer(0, restTop) {
                    alpha = if (headerUnder) 1f else 1f - out
                    translationY = if (headerUnder) 0f else -headerPlace.height * 0.2f * out
                    if (headerUnder && headerGrow != null) backUnder(headerGrow, pivot)
                }
            }
            // Coming back the overview is in its place at once (#249, see headerRestOffset).
            restPlace.place(0, restTop + headerRestOffset(headerRoom, out, collapsed))
        }
    }
}

// Room above the first departure time and below the last arrival time.
private val CHART_TOP = 34.dp
private val CHART_BOTTOM = 50.dp
private val AXIS_WIDTH = 46.dp
private val COLUMN_GAP = 5.dp

// A ride block is never lower than this, so its line name stays readable.
private val MIN_BLOCK = 18.dp
private val BLOCK_GAP = 2.dp
private val BLOCK_ROUND = 14.dp

// After a ride there is always this much air before the next block, so a change shows as a gap.
private val RIDE_GAP = 6.dp

// Between two rides whose change shows a label (see chartChanges) there is at least this much
// air, so the label (OVERLAP_TAG_HEIGHT high) never touches either block; the blocks after it are
// pushed down. Every labelled change gets it, so a change looks the same in every column.
private val PILL_GAP = 22.dp

// The name and corners of a ride block grow from the small form at MIN_BLOCK to the full form
// at this height, smoothly, so zooming the chart morphs the blocks instead of switching them.
private val FULL_BLOCK = 32.dp

/** Room on both sides of the name of a line in its block of the chart. */
private val BLOCK_LABEL_PADDING = 2.dp

/** The smallest size (sp) the name of a line gets in a block before it is shortened. */
private const val BLOCK_LABEL_MIN_SIZE = 8f

/** The size (sp) at which the names of lines are measured for their blocks: the largest they get. */
private const val BLOCK_LABEL_REFERENCE_SIZE = 15f

/**
 * The name of a line for a block of the chart [room] wide (see [BlockLabelFit]): [full] where
 * it fits at BLOCK_LABEL_MIN_SIZE or more, else the [compact] name ("N1334" for "Flix N1334").
 */
@Composable
private fun fitBlockLabel(full: String, compact: String, room: Dp, marked: Boolean = false): BlockLabelFit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalTextStyle.current.merge(TextStyle(fontWeight = FontWeight.ExtraBold))
    // Text grows in step with its size, so each name is measured once, at
    // BLOCK_LABEL_REFERENCE_SIZE, and its width at any other size worked out from there. The mode
    // icon in front (see modeHint) grows with it: as wide as the size of the text, gap included.
    val widths = remember(full, compact, density, style, marked) {
        val mark = if (marked) with(density) { BLOCK_LABEL_REFERENCE_SIZE.sp.toPx() } else 0f
        val widthOf = { text: String ->
            measurer.measure(text, style.copy(fontSize = BLOCK_LABEL_REFERENCE_SIZE.sp), softWrap = false, maxLines = 1)
                .size.width.toFloat() + mark
        }
        val fullWidth = widthOf(full)
        fullWidth to (if (compact == full) fullWidth else widthOf(compact))
    }
    val roomPixels = with(density) { room.toPx() }.coerceAtLeast(1f)
    return remember(full, compact, widths, roomPixels) {
        // Which name shows does not depend on the size the block allows (11 to 15 sp): a name
        // that fits at the small size is never cut below the smallest one at the large size.
        val fullFit = BlockLabelFit(full, widths.first, roomPixels)
        if (compact == full || fullFit.fitted(BLOCK_LABEL_REFERENCE_SIZE) >= BLOCK_LABEL_MIN_SIZE) {
            fullFit
        } else {
            BlockLabelFit(compact, widths.second, roomPixels)
        }
    }
}

/**
 * The name [text] of a line in a block, [referenceWidth] pixels wide at
 * BLOCK_LABEL_REFERENCE_SIZE, for a block with [roomPixels] for it.
 */
private class BlockLabelFit(val text: String, private val referenceWidth: Float, private val roomPixels: Float) {
    /** The size (sp) at which the name fills the room at most, never larger than [maxSize]. */
    fun fitted(maxSize: Float): Float {
        val width = referenceWidth * maxSize / BLOCK_LABEL_REFERENCE_SIZE
        return if (width <= roomPixels) maxSize else maxSize * roomPixels / width * 0.98f
    }

    /**
     * The size (sp) of the name where the block allows [maxSize] (the size of every block, as
     * for "S1" or "U9"): that where it fits, else smaller until it fits, down to BLOCK_LABEL_MIN_SIZE.
     */
    fun size(maxSize: Float): Float = fitted(maxSize).coerceAtLeast(BLOCK_LABEL_MIN_SIZE)
}

// A walk is never lower than this; from the second height on it says how long it is.
private val MIN_WALK = 10.dp
private val WALK_LABEL = 18.dp

// A column counts as in view once this many pixels of it show.
private const val PEEK_PIXELS = 4

// The chart waits this long for its window and scale to settle before it scrolls up or down to a pick.
private const val SETTLE_SCROLL_MILLIS = 350L

// The journeys in view may ask for more height per minute than the card gives, so that
// all their stretches fit into the time of their journey, up to this value: beyond it
// one very short journey would stretch the whole chart.
private val MAX_ASKED_SCALE = 7.dp

/**
 * The height of one minute at which every journey of [journeys] can show each of its
 * stretches at least as tall as it has to be, within the time from its departure to its
 * arrival. Short rides keep their height and the long ones after them give it back
 * (see [columnParts]), so a long journey asks for little and fits the card.
 */
private fun neededScale(journeys: List<Journey>): Dp {
    var needed = 0.dp
    journeys.forEach { journey ->
        val legs = journey.legs
        val firstRide = legs.indexOfFirst { !it.walking }
        val lastRide = legs.indexOfLast { !it.walking }
        val pills = labelledChanges(journey)
        var least = 0.dp
        legs.forEachIndexed { index, leg ->
            val betweenRides = firstRide >= 0 && index > firstRide && index < lastRide
            least += when {
                !leg.walking -> {
                    // The air below a ride: room for the pill of the change to the next ride, if it has one.
                    val nextRide = (index + 1 until legs.size).firstOrNull { !legs[it].walking }
                    MIN_BLOCK + if (nextRide != null && nextRide in pills) PILL_GAP else RIDE_GAP
                }
                !betweenRides && minutesBetween(leg.departure, leg.arrival) >= 1 -> MIN_WALK + BLOCK_GAP
                else -> 0.dp
            }
        }
        val minutes = journey.durationMinutes.coerceAtLeast(1)
        needed = maxOf(needed, least / minutes.toFloat())
    }
    return minOf(needed, MAX_ASKED_SCALE)
}

/**
 * How a change between two rides shows in the chart: [minutes] from the arrival to the
 * departure, its [tightness] and the [tone] of its minutes. Staying on board ([onBoard]) is no
 * change and is rated comfortable. A risky or very risky change shows as a pill with a dot.
 */
internal class ChartChange(val minutes: Long, val tightness: Tightness, val onBoard: Boolean, val tone: WaitTone) {
    val pill: Boolean get() = !onBoard && tightness.ordinal >= Tightness.Risky.ordinal
}

/**
 * The changes of [journey] that show a label in the chart, by the leg index of the ride after
 * them: every change between two rides that both run, as a pill where it is risky or worse,
 * else with its minutes where it leaves at least one. Each is rated as the detail view rates
 * it (see journeyChangesOf), by the change alone, and gets room of its own (see
 * PILL_GAP): the same change looks the same in every column that has it, whatever the column
 * holds around it and whatever room the scale happens to leave.
 */
internal fun chartChanges(journey: Journey): Map<Int, ChartChange> {
    val legs = journey.legs
    val own = journeyChangesOf(journey).associate { it.nextIndex to it.tightness }
    val rides = legs.indices.filter { !legs[it].walking }
    val changes = LinkedHashMap<Int, ChartChange>()
    for ((from, to) in rides.zipWithNext()) {
        val arriving = legs[from]
        val next = legs[to]
        // A change to or from a cancelled ride does not happen: nothing to rate.
        if (arriving.cancelled || next.cancelled) continue
        val minutes = changeGapMinutes(arriving, next)
        val onBoard = arriving.tripId != null && arriving.tripId == next.tripId
        val tightness = if (onBoard) Tightness.Comfortable else own[to] ?: changeTightness(arriving, next)
        // Coloured by the wait (see waitTone): a long one is a calm sand, not green.
        val tone = if (onBoard) WaitTone.Neutral else waitTone(tightness, minutes, changeNeed(arriving, next))
        val change = ChartChange(minutes, tightness, onBoard, tone)
        if (change.pill || minutes >= 1) changes[to] = change
    }
    return changes
}

/** The legs of [journey] (by index) whose change before them shows a label in the chart (see chartChanges). */
private fun labelledChanges(journey: Journey): Set<Int> = LabelledChanges.of(journey)

/**
 * The labelled changes of the journeys asked for last, by identity. The height of the chart asks
 * for them every frame while its window glides (see columnBottom), and rating a change is not
 * free (it may look up the walk between two platforms).
 */
private object LabelledChanges {
    private const val SIZE = 64
    private val journeys = arrayOfNulls<Journey>(SIZE)
    private val labels = arrayOfNulls<Set<Int>>(SIZE)
    // The rating settings each entry was made with (see ratingSettingsVersion): after a change
    // of the transfer tuning or the walking pace a journey is rated afresh.
    private val versions = IntArray(SIZE)
    private var next = 0

    @Synchronized
    fun of(journey: Journey): Set<Int> {
        val version = ratingSettingsVersion
        for (index in 0 until SIZE) {
            if (journeys[index] === journey && versions[index] == version) return labels[index] ?: emptySet()
        }
        val found = chartChanges(journey).keys
        journeys[next] = journey
        labels[next] = found
        versions[next] = version
        next = (next + 1) % SIZE
        return found
    }
}

/**
 * One stretch of a journey column: leg [index] of it, a ride or a walk before the first or after
 * the last ride. Where short rides are combined (see ChartShortLegs.Combine), [members] holds
 * the leg indices of the rides it stands for, and [bands] where each of them lies in it, as
 * pairs of top and bottom from the top of the stretch; [index] and [leg] are its first ride.
 */
internal class ColumnPart(
    val index: Int,
    val leg: Leg,
    val top: Dp,
    val height: Dp,
    val members: IntArray? = null,
    val bands: FloatArray? = null,
)

/**
 * Where the stretches of a journey lie in its column: always where their times say, the time
 * axis rules. [starts] and [ends] are the times of its legs in minutes of the chart. Each
 * stretch runs from its departure to its arrival, however short: a short ride is drawn smaller
 * (without the minutes of the change, then without its name, at last as a thin bar, see
 * [ColumnLook]), never pushing what follows down, and never running into the next ride (it
 * ends where that one leaves, see rideEndsWithoutOverlap). A walk between two rides is part of the
 * change and has no stretch of its own. With [combine], two or more short rides in a row (each,
 * and the changes between them, lower than MIN_BLOCK) are one stretch from the departure of
 * the first to the arrival of the last. [pillsBefore] is no longer needed for the room and kept
 * for the callers.
 */
@Suppress("UNUSED_PARAMETER")
internal fun columnParts(
    legs: List<Leg>,
    starts: FloatArray,
    times: FloatArray,
    windowStart: Float,
    perMinute: Dp,
    pillsBefore: Set<Int> = emptySet(),
    top: Dp = CHART_TOP,
    combine: Boolean = false,
): List<ColumnPart> {
    // No ride runs into the next one, whatever times its legs carry (#57): a ride that has
    // missed the next one ends where that one leaves (see rideEndsWithoutOverlap).
    val ends = rideEndsWithoutOverlap(legs, starts, times)
    val firstRide = legs.indexOfFirst { !it.walking }
    val lastRide = legs.indexOfLast { !it.walking }
    val parts = ArrayList<ColumnPart>(legs.size)
    var index = 0
    while (index < legs.size) {
        val leg = legs[index]
        val minutes = ends[index] - starts[index]
        val betweenRides = firstRide >= 0 && index > firstRide && index < lastRide
        if (leg.walking && (betweenRides || minutes < 1f)) {
            index++
            continue
        }
        val partTop = top + perMinute * (starts[index] - windowStart)
        val height = perMinute * minutes.coerceAtLeast(0f)
        if (combine && !leg.walking && height < MIN_BLOCK) {
            val last = shortRun(legs, starts, ends, index, firstRide, lastRide, perMinute)
            if (last > index) {
                var count = 0
                for (k in index..last) if (!legs[k].walking) count++
                val members = IntArray(count)
                val bands = FloatArray(count * 2)
                var at = 0
                for (k in index..last) {
                    if (legs[k].walking) continue
                    members[at] = k
                    bands[2 * at] = (perMinute * (starts[k] - starts[index])).value
                    bands[2 * at + 1] = (perMinute * (ends[k] - starts[index]).coerceAtLeast(starts[k] - starts[index])).value
                    at++
                }
                parts.add(ColumnPart(index, leg, partTop, perMinute * (ends[last] - starts[index]).coerceAtLeast(0f), members, bands))
                index = last + 1
                continue
            }
        }
        parts.add(ColumnPart(index, leg, partTop, height))
        index++
    }
    return parts
}

/**
 * The last ride of the run of short rides that starts with the ride at [first] (see
 * columnParts with combine): each ride and each change between two of them lower than
 * MIN_BLOCK at [perMinute]; walks between rides belong to the change. [first] where the
 * ride after it is not short.
 */
private fun shortRun(
    legs: List<Leg>,
    starts: FloatArray,
    ends: FloatArray,
    first: Int,
    firstRide: Int,
    lastRide: Int,
    perMinute: Dp,
): Int {
    var last = first
    var k = first + 1
    while (k < legs.size) {
        if (legs[k].walking) {
            if (k > firstRide && k < lastRide) {
                k++
                continue
            }
            break
        }
        if (perMinute * (ends[k] - starts[k]) >= MIN_BLOCK || perMinute * (starts[k] - ends[last]) >= MIN_BLOCK) break
        last = k
        k++
    }
    return last
}

/**
 * The times of the legs of one journey in minutes of the chart, and its labelled changes, worked
 * out once: the height of the chart asks how far its columns reach every frame while the window
 * glides, and minuteOf makes a Duration for each time it is asked.
 */
private class ColumnSpan(
    val legs: List<Leg>,
    val starts: FloatArray,
    val ends: FloatArray,
    val arrival: Float,
    val pills: Set<Int>,
) {
    /** How far down the column reaches with the window starting at [windowStart] (see columnBottom). */
    fun bottom(windowStart: Float, perMinute: Dp, top: Dp = CHART_TOP): Dp {
        val parts = columnParts(legs, starts, ends, windowStart, perMinute, pills, top)
        val natural = top + perMinute * (arrival - windowStart)
        val last = parts.lastOrNull() ?: return natural
        return maxOf(last.top + last.height, natural)
    }

    companion object {
        fun of(journey: Journey, minuteOf: (OffsetDateTime) -> Float): ColumnSpan {
            val legs = journey.legs
            return ColumnSpan(
                legs,
                FloatArray(legs.size) { minuteOf(legs[it].departure) },
                FloatArray(legs.size) { minuteOf(legs[it].arrival) },
                minuteOf(journey.arrival),
                labelledChanges(journey),
            )
        }
    }
}

/**
 * How far down the column of [journey] reaches with the window starting at [windowStart]:
 * to its arrival, or below it where short stretches pushed the ones after them down.
 */
private fun columnBottom(
    journey: Journey,
    minuteOf: (OffsetDateTime) -> Float,
    windowStart: Float,
    perMinute: Dp,
    top: Dp = CHART_TOP,
): Dp {
    val legs = journey.legs
    val parts = columnParts(
        legs,
        FloatArray(legs.size) { minuteOf(legs[it].departure) },
        FloatArray(legs.size) { minuteOf(legs[it].arrival) },
        windowStart,
        perMinute,
        labelledChanges(journey),
        top,
    )
    val natural = top + perMinute * (minuteOf(journey.arrival) - windowStart)
    return maxOf(parts.lastOrNull()?.let { it.top + it.height } ?: natural, natural)
}

/**
 * Where everything of one journey column lies for one window and scale: its stretches, the
 * dashed outline above each of them (0 dp where it has none), the top of its departure time and
 * of its arrival time, and the stretch that holds the tag of a cancelled ride (-1 for none).
 * It is worked out while the chart is laid out, so the window and the scale can move every
 * frame without composing the column again.
 */
internal class ColumnGeometry(
    val parts: List<ColumnPart>,
    val dashes: List<Dp>,
    val departureTop: Dp,
    val arrivalTop: Dp,
    val cancelTagPart: Int,
) {
    /** Where the gap below stretch [part] (a ride) begins. */
    fun gapTop(part: Int): Dp = parts[part].top + parts[part].height

    /** How high the gap below stretch [part] is, down to the outline of the next ride where it has one. */
    fun gapHeight(part: Int): Dp = parts[part + 1].top - dashes[part + 1] - gapTop(part)

    /** The middle of the gap below stretch [part], for the stop and the minutes of the change. */
    fun gapMiddle(part: Int): Dp = gapTop(part) + gapHeight(part) / 2

    /** The middle of the gap below stretch [part] for the pill of its change, the dashed outline below included. */
    fun pillMiddle(part: Int): Dp = gapTop(part) + (parts[part + 1].top - gapTop(part)) / 2

    /** What of the column is shown at all at this moment (see [ColumnLook]). */
    fun look(): ColumnLook = ColumnLook(
        legIndices = parts.map { it.index },
        walkLabels = parts.map { it.leg.walking && it.height >= WALK_LABEL },
        named = parts.map { !it.leg.walking && it.height >= NAMED_BLOCK },
        thin = parts.map { !it.leg.walking && it.height < SMALL_BLOCK },
        combined = parts.map { it.members?.size ?: 0 },
        gapRoom = parts.indices.map { part ->
            val next = parts.getOrNull(part + 1)
            when {
                parts[part].leg.walking || next == null || next.leg.walking -> 0
                gapHeight(part) >= OVERLAP_TAG_HEIGHT + 2.dp -> 2
                gapHeight(part) >= GAP_ONE_LINE -> 1
                else -> 0
            }
        },
        dashed = dashes.map { it > 0.dp },
        roomyGaps = parts.indices.map { part ->
            val next = parts.getOrNull(part + 1)
            !parts[part].leg.walking && next != null && !next.leg.walking && gapHeight(part) >= GAP_TWO_LINES
        },
        cancelTagPart = cancelTagPart,
    )
}

/**
 * What of a journey column is shown, as opposed to where: the legs that have a stretch (by
 * index), which walks are tall enough to say how long they are, which rides show the dashed
 * outline of their planned departure, whether the gap below each ride has room for the name
 * of the stop above the minutes of the change, and which stretch holds the tag of a cancelled ride. It changes only when a
 * stretch crosses one of the heights that decide it, so only then is the column composed again.
 */
internal data class ColumnLook(
    val legIndices: List<Int>,
    val walkLabels: List<Boolean>,
    /** Which rides are tall enough for the name of their line (see NAMED_BLOCK). */
    val named: List<Boolean>,
    /** Which rides are lower than a small block and show as a thin bar (see SMALL_BLOCK). */
    val thin: List<Boolean>,
    /** For each stretch, how many short rides it combines (0 for one ride or a walk). */
    val combined: List<Int>,
    /** Room in the gap below each ride for the change: 0 a dot only, 1 its minutes, 2 its pill. */
    val gapRoom: List<Int>,
    val dashed: List<Boolean>,
    val roomyGaps: List<Boolean>,
    val cancelTagPart: Int,
)

// From this height the gap between two rides holds the minutes of the change; below it a dot.
private val GAP_ONE_LINE = 13.dp

// From this height a ride block shows the name of its line; below it only its colour.
private val NAMED_BLOCK = 13.dp

// Below this height a ride is drawn as a thin bar of its colour (at least THIN_BAR high).
private val SMALL_BLOCK = 5.dp
private val THIN_BAR = 3.dp

// From this height the gap between two rides holds the name of the stop above the minutes of
// the change, where the setting asks for it (see LocalChartChangeStops).
private val GAP_TWO_LINES = 28.dp

/**
 * [ColumnGeometry] of the legs [legs] of a journey, [cancelled] or not, with the times of its
 * legs [starts] and [ends] in minutes of the chart, for the window starting at [windowStart] at
 * [perMinute]. [pillsBefore] as in [columnParts]; [labelHeight] is the height of the departure
 * time with the delay above it, where shown.
 */
internal fun columnGeometry(
    legs: List<Leg>,
    starts: FloatArray,
    ends: FloatArray,
    windowStart: Float,
    perMinute: Dp,
    pillsBefore: Set<Int>,
    labelHeight: Dp,
    cancelled: Boolean,
    top: Dp = CHART_TOP,
    combine: Boolean = false,
): ColumnGeometry {
    val parts = columnParts(legs, starts, ends, windowStart, perMinute, pillsBefore, top, combine)
    val firstTop = parts.firstOrNull()?.top ?: (top + perMinute * (starts.first() - windowStart))
    val lastBottom = parts.lastOrNull()?.let { it.top + it.height } ?: firstTop
    // The dashed outline of the planned part of a late ride, by its place among the parts.
    // It needs room above its block: below the times for the first ride, below the block before for the others.
    // Only above a block of its own that is tall enough to read as one, and only where the delay
    // itself is a visible stretch of time: above a short ride drawn small, a thin bar or a run
    // of combined rides, an outline of at least 6 dp stood alone as an empty dashed block, a
    // ghost after the ride before (#35d: after an IC 55, a late RB48 of 3 minutes to Köln Messe/Deutz).
    val dashes = parts.mapIndexed { index, part ->
        val delay = part.leg.departureDelayMinutes.toInt()
        if (!showsPlannedOutline(part, delay, perMinute, cancelled)) {
            0.dp
        } else {
            val room = if (index == 0) part.top - labelHeight else part.top - (parts[index - 1].top + parts[index - 1].height)
            val wanted = (perMinute * delay).coerceIn(6.dp, 28.dp)
            if (room >= 6.dp) minOf(wanted, room) else 0.dp
        }
    }
    // The first cancelled ride with room for it holds the tag "fällt aus".
    val tagPart = parts.indexOfFirst { !it.leg.walking && it.leg.cancelled && it.height >= CANCEL_TAG_BLOCK }
    return ColumnGeometry(
        parts = parts,
        dashes = dashes,
        departureTop = firstTop - (dashes.firstOrNull() ?: 0.dp) - labelHeight,
        arrivalTop = maxOf(top + perMinute * (ends.last() - windowStart), lastBottom) + 2.dp,
        cancelTagPart = tagPart,
    )
}

/**
 * True when the stretch [part], [delay] minutes late at its departure, shows its planned
 * departure as a dashed outline above it (see columnGeometry): a ride of its own (no walk, not
 * combined, not cancelled) at least NAMED_BLOCK high, late by at least MIN_DASH at [perMinute].
 */
internal fun showsPlannedOutline(part: ColumnPart, delay: Int, perMinute: Dp, cancelled: Boolean): Boolean =
    !part.leg.walking && !cancelled && delay > 0 && part.members == null &&
        part.height >= NAMED_BLOCK && perMinute * delay >= MIN_DASH

// Below this height of the delay itself no dashed outline of the planned departure is drawn.
private val MIN_DASH = 3.dp

/** Axis labels every 10, 20, 30 or 60 minutes at [perMinute], whatever still has room. */
internal fun axisStep(perMinute: Dp): Int = when {
    perMinute * 10 >= 26.dp -> 10
    perMinute * 20 >= 26.dp -> 20
    perMinute * 30 >= 26.dp -> 30
    else -> 60
}

/** The ticks of the axis: from [first] to [last] (inclusive) every [step] minutes. */
internal data class AxisTicks(val first: Int, val last: Int, val step: Int)

/** The ticks of the axis for the window from [windowStart] to [windowEnd] at [perMinute]. */
internal fun axisTicks(windowStart: Float, windowEnd: Float, perMinute: Dp): AxisTicks {
    val step = axisStep(perMinute)
    return AxisTicks(ceil(windowStart / step).toInt() * step, windowEnd.toInt(), step)
}

/** Whether the line of "now" at minute [now] shows in the window from [windowStart] to [windowEnd]. */
internal fun nowInWindow(now: Float, windowStart: Float, windowEnd: Float): Boolean =
    now >= windowStart - 0.5f && now <= windowEnd

/** Whether the axis label of a tick at [tickY] gives way to the marker of "now" at [nowY] (null: none). */
internal fun givesWayToNow(tickY: Dp, nowY: Dp?): Boolean =
    nowY != null && tickY > nowY - 16.dp && tickY < nowY + 16.dp

/**
 * The height of the chart: as tall as its window at [perMinute] ([span] minutes) and as the
 * column that was pushed down the furthest ([deepest]), with the room below; a hair more than
 * the card ([viewport]) is no reason to scroll, so then it is as tall as the card.
 */
internal fun chartHeightOf(
    deepest: Dp,
    perMinute: Dp,
    span: Float,
    viewport: Dp,
    top: Dp = CHART_TOP,
    bottom: Dp = CHART_BOTTOM,
): Dp {
    val wanted = maxOf(top + perMinute * span, deepest) + bottom
    return if (wanted > viewport + 4.dp) wanted else viewport
}

/**
 * The largest height of a minute at which all of [journeys], from their first departure to
 * the times below their arrival, fit into [height] with the window from [windowStart] to
 * [windowEnd]. Pushed stretches grow less than the minutes do, so the height of the chart
 * rises with the scale and the answer is found by halving.
 */
private fun fittingScale(
    journeys: List<Journey>,
    minuteOf: (OffsetDateTime) -> Float,
    windowStart: Float,
    windowEnd: Float,
    height: Dp,
    top: Dp = CHART_TOP,
    bottom: Dp = CHART_BOTTOM,
): Dp {
    val span = (windowEnd - windowStart).coerceAtLeast(10f)
    val room = (height - top - bottom).coerceAtLeast(80.dp)
    fun fits(scale: Dp): Boolean {
        val deepest = journeys.maxOfOrNull { columnBottom(it, minuteOf, windowStart, scale, top) } ?: 0.dp
        return maxOf(deepest, top + scale * span) + bottom <= maxOf(height, room + top + bottom)
    }
    var high = room / span
    if (fits(high)) return high
    var low = 0.dp
    repeat(18) {
        val middle = (low + high) / 2
        if (fits(middle)) low = middle else high = middle
    }
    return low
}

/**
 * The journeys as columns over one shared time axis. The axis always covers exactly
 * the columns in view: scrolling sideways moves the time window along, and reaching
 * either end loads earlier or later journeys. A minute never gets less height than the
 * journeys in view need; when they do not fit the card that way, the chart scrolls up
 * and down inside it instead of being squeezed.
 */
@Composable
private fun JourneyChart(
    journeys: List<Journey>,
    listState: LazyListState,
    upDown: ScrollState,
    earlierPrimed: MutableState<Boolean>,
    anchored: MutableState<Boolean>,
    startIndex: Int,
    timed: Boolean,
    selectedId: String,
    levels: Map<String, TransferComfort>?,
    now: OffsetDateTime,
    onSelect: (String) -> Unit,
    onLoadEarlier: () -> Unit,
    onLoadLater: () -> Unit,
    edges: EdgeStatus = EdgeStatus(),
    modifier: Modifier = Modifier,
    still: Boolean = false,
) {
    AllocProbe.begin(AllocProbe.CHART_COMPOSE)
    val loadEarlier by rememberUpdatedState(onLoadEarlier)
    // While a journey opens on a page of its own out of a column ([still]), the chart under it
    // stands still: it neither scrolls to the pick nor takes a new window or scale for the room
    // the page leaves it, so it costs no frame of the opening (see JourneyDetail).
    val stillNow by rememberUpdatedState(still)
    val loadLater by rememberUpdatedState(onLoadLater)
    // The tap on a column calls the latest [onSelect]; the click of a column stays the same
    // lambda, so a column whose journey did not change skips when the page composes again.
    val select by rememberUpdatedState(onSelect)
    val glide = rememberAnimationsOn()
    // A column held down shows its connection in a peek card until it is let go.
    val peek = remember { PeekState() }
    val ticks = rememberTicks()
    val peekLabel = stringResource(R.string.connection_peek)
    ConnectionPeek(peek, now)

    // Minutes are counted from a fixed full hour, so axis labels fall on round times.
    val base = remember { journeys.first().departure.withMinute(0).withSecond(0).withNano(0) }
    val minuteOf: (OffsetDateTime) -> Float = remember(base) { { time -> Duration.between(base, time).seconds / 60f } }

    // Every column that shows decides the time window, also one that only peeks in at an edge:
    // each of them is seen whole, from its first departure (a walk included) to its arrival.
    // Worked out for every frame of a scroll: only the first and the last index in view, packed
    // into one number, so the frames in between allocate no lists (see packedRange); the chart
    // composes again only when a column comes into view or leaves it.
    val visibleRange by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            var first = -1
            var last = -1
            for (i in items.indices) {
                val item = items[i]
                if (item.offset + item.size > info.viewportStartOffset + PEEK_PIXELS &&
                    item.offset < info.viewportEndOffset - PEEK_PIXELS
                ) {
                    if (first < 0) first = item.index
                    last = item.index
                }
            }
            packedRange(first, last)
        }
    }
    // The columns in view last time: when earlier journeys have just been put in front, the
    // indices of [visibleRange] still belong to the row as it was laid out before (for one
    // frame), so they are checked by key and, not matching yet, the columns before stay.
    val inViewBefore = remember { arrayOfNulls<List<Journey>>(1) }
    val inView = remember(journeys, visibleRange) {
        val first = rangeFirst(visibleRange)
        val last = rangeLast(visibleRange)
        val before = inViewBefore[0]
        val shown = if (first < 0) {
            journeys.take(4)
        } else if (before != null && !rangeMatches(listState, journeys, first, last)) {
            before
        } else {
            journeys.subList(first.coerceAtMost(journeys.size), (last + 1).coerceAtMost(journeys.size)).ifEmpty { journeys.take(4) }
        }
        inViewBefore[0] = shown
        shown
    }
    val visibleKeys = remember(inView) { inView.mapTo(HashSet()) { it.id } }

    val firstDeparture = inView.minOf { minuteOf(it.departure) }
    val nowMinute = minuteOf(now)
    // Start at "now" when the first departure is close, so the wait is visible.
    // A search at a picked time starts at its first journey and never slides to "now".
    val nowIsClose = !timed && nowMinute < firstDeparture && firstDeparture - nowMinute <= 45f
    val liveStart = if (nowIsClose) nowMinute else firstDeparture
    val liveEnd = maxOf(inView.maxOf { minuteOf(it.arrival) }, liveStart + 20f)
    // Held as they were while the chart stands still (see [still]).
    val held = remember { floatArrayOf(Float.NaN, Float.NaN) }
    if (!still || held[0].isNaN()) {
        held[0] = liveStart
        held[1] = liveEnd
    }
    val targetStart = held[0]
    val targetEnd = held[1]
    // A spring carries the window along: a new target while it moves keeps its speed, so
    // scrolling sideways glides instead of jerking from one window to the next.
    // The window is kept as states that only laying out and drawing read: it moves every frame
    // after a sideways scroll, and composing the whole chart again for each of those frames made
    // scrolling stutter (see ChartFrame).
    val windowSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
    val windowStart = animateFloatAsState(targetStart, windowSpring, label = "windowStart")
    val windowEnd = animateFloatAsState(targetEnd, windowSpring, label = "windowEnd")

    // A search at a picked time opens on the journey that fits it, once per search:
    // for an arrival time that is the last one, with the earlier ones to its left.
    LaunchedEffect(listState) {
        if (!anchored.value) {
            anchored.value = true
            if (startIndex > 0) listState.scrollToItem(startIndex)
        }
    }

    // The picked journey stands fully in view: when the chart opens, when another one is
    // picked, and when a page of earlier journeys came in before it. One that is cut at an
    // edge slides in; one that lies further away is scrolled to. Not while the user scrolls,
    // and not when it lies wholly out of view after a new page: then the user moved away.
    val headId = journeys.first().id
    val shownPick = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(selectedId, headId) {
        // The list has to be laid out with the new state first.
        withFrameNanos { }
        val firstRun = shownPick[0] == null
        val newPick = shownPick[0] != selectedId
        shownPick[0] = selectedId
        if (listState.isScrollInProgress || stillNow) return@LaunchedEffect
        val index = journeys.indexOfFirst { it.id == selectedId }
        if (index < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == selectedId }
        when {
            // A search at a picked time opens on its own journey (see above), not on the pick.
            item == null -> if (newPick && !(firstRun && startIndex > 0)) listState.animateScrollToItem(index)
            item.offset < info.viewportStartOffset ->
                listState.animateScrollBy((item.offset - info.viewportStartOffset).toFloat())
            item.offset + item.size > info.viewportEndOffset ->
                listState.animateScrollBy(
                    (minOf(item.offset + item.size - info.viewportEndOffset, item.offset - info.viewportStartOffset)).toFloat(),
                )
        }
    }

    // Reaching an end loads more, by a drag or a fling alike, once per reach (see EdgeTrigger);
    // a pull at an edge the row cannot scroll past loads the next page, also after a page that
    // brought nothing or failed. Earlier journeys load once up front and then only while the
    // user scrolls, so the chart never walks into the past by itself.
    // The journeys at the edges are read as they are now, not as they were when this started:
    // a page that leaves the count as it was (past journeys dropped, a filter) still moves them.
    val shownList by rememberUpdatedState(journeys)
    val edgeTriggers = remember(listState) { arrayOf(EdgeTrigger(onlyWhileScrolling = true), EdgeTrigger(onlyWhileScrolling = false)) }
    // The journey at an edge when the user asked for a page there (null: not asked), to tell a
    // page whose journeys all stay hidden from one that shows new ones. Only a page the user
    // asked for shows at its edge: the ones loaded up front come quietly, as before.
    val askedEarlier = remember(listState) { mutableStateOf<String?>(null) }
    val askedLater = remember(listState) { mutableStateOf<String?>(null) }
    val askEarlier: (Boolean) -> Unit = remember(listState) {
        { byUser ->
            if (byUser) askedEarlier.value = shownList.first().id
            loadEarlier()
        }
    }
    val askLater: (Boolean) -> Unit = remember(listState) {
        { byUser ->
            if (byUser) askedLater.value = shownList.last().id
            loadLater()
        }
    }
    // Three flags in one small number (boxed from the cache of small integers): no object
    // for each frame of a scroll, and the flow goes on only when one of them changes.
    val edgeFlags: () -> Int = remember(listState) {
        {
            val list = shownList
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            // The first and the last column that show in the row at all: an item the row keeps
            // laid out beyond its edge (pinned, say) does not count as one in view.
            var firstKey: Any? = null
            var lastKey: Any? = null
            for (i in items.indices) {
                val item = items[i]
                if (item.offset + item.size > info.viewportStartOffset && item.offset < info.viewportEndOffset) {
                    if (firstKey == null) firstKey = item.key
                    lastKey = item.key
                }
            }
            val atEnd = chartAtEnd(lastKey, list.lastOrNull()?.id, list.getOrNull(list.size - 2)?.id)
            val atStart = chartAtStart(firstKey, list.firstOrNull()?.id)
            (if (atEnd) 1 else 0) or (if (atStart) 2 else 0) or (if (listState.isScrollInProgress) 4 else 0)
        }
    }
    val onEdges: (Int) -> Unit = remember(listState) {
        { flags ->
            val list = shownList
            val scrolling = flags and 4 != 0
            if (edgeTriggers[1].edge(flags and 1 != 0, scrolling, list.lastOrNull()?.id)) askLater(scrolling)
            if (edgeTriggers[0].edge(flags and 2 != 0, scrolling, list.firstOrNull()?.id, earlierPrimed.value)) {
                earlierPrimed.value = true
                askEarlier(scrolling)
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { edgeFlags() }.collect { onEdges(it) }
    }
    // A page that changed the journeys at an edge while the flags stayed the same (still at the
    // end, say): looked at again once the row is laid out with them.
    val tailId = journeys.last().id
    LaunchedEffect(headId, tailId, listState) {
        withFrameNanos { }
        onEdges(Snapshot.withoutReadObservation { edgeFlags() })
    }
    // A pull at an edge: the part of a drag the row could not take because it is there already.
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val edgePull = remember(density) { EdgePull(with(density) { EDGE_PULL.toPx() }) }
    val pullAtEdges = remember(edgePull, listState, rtl) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val scrolling = listState.isScrollInProgress
                when (edgePull.drag(if (rtl) -available.x else available.x)) {
                    1 -> if (edgeTriggers[0].pull(scrolling)) {
                        earlierPrimed.value = true
                        askEarlier(true)
                    }
                    -1 -> if (edgeTriggers[1].pull(scrolling)) askLater(true)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                edgePull.release()
                return Velocity.Zero
            }
        }
    }
    // The note at an edge after a page that showed nothing new there, for a few seconds.
    val earlierNote = remember(listState) { mutableStateOf<EdgeNote?>(null) }
    val laterNote = remember(listState) { mutableStateOf<EdgeNote?>(null) }
    LaunchedEffect(edges.earlier) {
        val result = edges.earlier ?: return@LaunchedEffect
        val before = askedEarlier.value ?: return@LaunchedEffect
        askedEarlier.value = null
        val note = edgeNoteOf(result.outcome, before, shownList.first().id)
        earlierNote.value = note
        if (note == null) return@LaunchedEffect
        delay(EDGE_NOTE_MILLIS)
        earlierNote.value = null
    }
    LaunchedEffect(edges.later) {
        val result = edges.later ?: return@LaunchedEffect
        val before = askedLater.value ?: return@LaunchedEffect
        askedLater.value = null
        val note = edgeNoteOf(result.outcome, before, shownList.last().id)
        laterNote.value = note
        if (note == null) return@LaunchedEffect
        delay(EDGE_NOTE_MILLIS)
        laterNote.value = null
    }
    // A new page on its way replaces the note of the one before. A load that ended without a
    // result (cancelled by a new search or a refresh) forgets that it was asked for, a frame
    // later, so the result of one that did end is seen first (see above).
    LaunchedEffect(edges.loadingEarlier) {
        if (edges.loadingEarlier) {
            earlierNote.value = null
        } else {
            withFrameNanos { }
            askedEarlier.value = null
        }
    }
    LaunchedEffect(edges.loadingLater) {
        if (edges.loadingLater) {
            laterNote.value = null
        } else {
            withFrameNanos { }
            askedLater.value = null
        }
    }

    // The clock moves the line of "now": it is read while drawing, so a new minute composes nothing.
    val nowAt = rememberUpdatedState(nowMinute)
    // Where the row of columns lies on the screen (left, top, height), for the tap of the auto test.
    val rowPlace = remember { FloatArray(3) }
    // The auto test of the performance lab scrolls the chart where it is open (debug and staging only).
    if (PerfLab.available) {
        DisposableEffect(listState) {
            PerfLab.chart = listState
            // The auto test opens the first column in full view as a tap on it does (see ChartOpenProbe).
            val tap: () -> Boolean = {
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull {
                    it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset
                }
                val id = item?.key as? String
                if (item == null || id == null) {
                    false
                } else {
                    val left = rowPlace[0] + item.offset
                    OpenOrigin.set(Rect(left, rowPlace[1], left + item.size, rowPlace[1] + rowPlace[2]))
                    select(id)
                    true
                }
            }
            ChartOpenProbe.tap = tap
            onDispose {
                if (PerfLab.chart === listState) PerfLab.chart = null
                if (ChartOpenProbe.tap === tap) ChartOpenProbe.tap = null
            }
        }
    }
    AllocProbe.end(AllocProbe.CHART_COMPOSE)
    GlassSurface(modifier = modifier.then(rememberFirstFrameTrace("NS chart first frame")), shape = RoundedCornerShape(32.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            AllocProbe.begin(AllocProbe.CHART_BODY)
            // The scale at which every column in view fits the card whole, with the room for its
            // times above and below, unless the journeys in view need more to show their
            // stretches at all (see neededScale). Then the chart scrolls up and down instead.
            val heldScale = remember { arrayOfNulls<Dp>(1) }
            // The total time of each journey, where the setting asks for it, has room of its own
            // above or below every column, kept the same in all of them (see chartRoomOf).
            val totalTime = LocalChartTotalTime.current
            val chartTop = CHART_TOP + chartRoomOf(totalTime, top = true)
            val chartBottom = CHART_BOTTOM + chartRoomOf(totalTime, top = false)
            // Pinned total times keep a margin of their own at an edge; the chart scrolls in the rest.
            val bandTop = pinnedBandOf(totalTime, top = true)
            val bandBottom = pinnedBandOf(totalTime, top = false)
            val viewHeight = maxHeight - bandTop - bandBottom
            val fitted = if (still && heldScale[0] != null) {
                null
            } else {
                remember(inView, targetStart, targetEnd, viewHeight, totalTime) {
                    maxOf(fittingScale(inView, minuteOf, targetStart, targetEnd, viewHeight, chartTop, chartBottom), neededScale(inView))
                }
            }
            val targetScale = fitted ?: heldScale[0] ?: 0.dp
            heldScale[0] = targetScale
            val perMinute = animateDpAsState(
                targetScale,
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                label = "chartScale",
            )
            // The window and the scale as they move, for laying out and drawing only.
            val frame = remember(windowStart, windowEnd, perMinute, chartTop) { ChartFrame(windowStart, windowEnd, perMinute, chartTop) }

            // The chart is as tall as its window at this scale, and as the column that was
            // pushed down the furthest (see chartHeightOf), worked out while it is measured.
            val viewport = viewHeight
            // The times of the columns in view in minutes of the chart, worked out once per set
            // of columns: the height is asked for every frame while the window glides.
            val spans = rememberUpdatedState(remember(inView, minuteOf) { inView.map { ColumnSpan.of(it, minuteOf) } })
            val chartHeight: () -> Dp = remember(frame, viewport, chartBottom) {
                // The last answer, for the same columns, window and scale: measuring and the check
                // whether the chart scrolls both ask in the same frame.
                var askedSpans: List<ColumnSpan>? = null
                var askedStart = Float.NaN
                var askedScale = Dp.Unspecified
                var askedSpan = Float.NaN
                var answer = viewport
                {
                    val columns = spans.value
                    val start = frame.windowStart
                    val scale = frame.perMinute
                    val span = frame.span
                    if (columns !== askedSpans || start != askedStart || scale != askedScale || span != askedSpan) {
                        AllocProbe.begin(AllocProbe.CHART_HEIGHT)
                        var deepest = 0.dp
                        for (column in columns) deepest = maxOf(deepest, column.bottom(start, scale, frame.top))
                        answer = chartHeightOf(deepest, scale, span, viewport, frame.top, chartBottom)
                        askedSpans = columns
                        askedStart = start
                        askedScale = scale
                        askedSpan = span
                        AllocProbe.end(AllocProbe.CHART_HEIGHT)
                    }
                    answer
                }
            }
            val scrolls by remember(chartHeight) { derivedStateOf { chartHeight() > viewport } }

            // Where the chart is taller than its card, the picked journey is scrolled fully into
            // view, its times included: when it is picked, and when the window or the scale
            // changed after a sideways scroll. Not while a finger is on the chart.
            val picked = journeys.firstOrNull { it.id == selectedId }?.takeIf { it.id in visibleKeys }
            val sideways = listState.isScrollInProgress
            LaunchedEffect(selectedId, scrolls, targetScale, targetStart, sideways) {
                if (picked == null || !scrolls || sideways || upDown.isScrollInProgress || stillNow) return@LaunchedEffect
                // The new window and scale have to be reached first, or the place is off.
                delay(SETTLE_SCROLL_MILLIS)
                if (stillNow) return@LaunchedEffect
                with(density) {
                    // The top of its column is the top of its departure time, which lies above the first block.
                    val top = (targetScale * (minuteOf(picked.departure) - targetStart)).roundToPx()
                    val bottom = (columnBottom(picked, minuteOf, targetStart, targetScale, chartTop) + chartBottom).roundToPx()
                    val view = viewHeight.roundToPx()
                    val from = upDown.value
                    val target = when {
                        bottom - top > view || top < from -> top
                        bottom > from + view -> bottom - view
                        else -> return@LaunchedEffect
                    }
                    upDown.animateScrollTo(target.coerceAtLeast(0))
                }
            }

            // Where the line of "now" lies; null while it is outside the window.
            val nowY: () -> Dp? = remember(frame) {
                { if (nowInWindow(nowAt.value, frame.windowStart, frame.windowEnd)) frame.y(nowAt.value) else null }
            }

            // Four columns share the width; with more, a fifth peeks in to invite scrolling. Fewer
            // journeys keep the width of four and stand at the start, so a single one is never
            // stretched into wide bars.
            val plotWidth = maxWidth - AXIS_WIDTH
            val fittingWidth = if (journeys.size <= 4) {
                (plotWidth - COLUMN_GAP * 3) / 4
            } else {
                (plotWidth - COLUMN_GAP * 4) / 4.4f
            }
            val columnWidth = fittingWidth.coerceAtLeast(64.dp)
            AllocProbe.end(AllocProbe.CHART_BODY)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = bandTop, bottom = bandBottom)
                    .fadeEdges(upDown, horizontal = false)
                    .verticalScroll(upDown)
                    .heightOf(chartHeight),
            ) {
                ChartAxis(
                    frame = frame,
                    base = base,
                    now = now,
                    nowY = nowY,
                    modifier = Modifier
                        .width(AXIS_WIDTH)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // The grid, and the line of "now" behind the columns: it shows between them,
                        // and every column draws its own piece of it below its blocks, so no block is
                        // crossed out.
                        .drawBehind {
                            AllocProbe.begin(AllocProbe.CHART_GRID)
                            drawGrid(frame)
                            nowY()?.let { drawNowLine(it) }
                            AllocProbe.end(AllocProbe.CHART_GRID)
                        },
                ) {
                    // Columns that run out of the card fade lightly at its edge instead of being cut.
                    // The fade comes in softly once the chart is there, not with it.
                    val sideFade = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { sideFade.animateTo(1f, tween(durationMillis = 700, delayMillis = 250)) }
                    LazyRow(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            // A pull at an edge loads the next page there (see pullAtEdges).
                            .nestedScroll(pullAtEdges)
                            // A held column's peek follows the finger across the columns (see PeekScrub.kt).
                            .peekScrub(peek, vertical = false) { ticks.tick() }
                            .then(
                                // Lab builds, or the lab opened in an unlocked release: nothing extra otherwise.
                                if (PerfLab.labBuild || PerfLab.shown) {
                                    Modifier.onGloballyPositioned { coordinates ->
                                        val at = coordinates.positionInRoot()
                                        rowPlace[0] = at.x
                                        rowPlace[1] = at.y
                                        rowPlace[2] = coordinates.size.height.toFloat()
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .fadeEdges(
                                listState,
                                horizontal = true,
                                length = 22.dp,
                                edgeAlpha = { 1f - SIDE_FADE_DEPTH * sideFade.value },
                            )
                            // The row's own measuring (with the columns it composes and measures) and drawing.
                            .probed(AllocProbe.CHART_ROW_LAYOUT, AllocProbe.CHART_ROW_DRAW),
                        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
                    ) {
                        items(journeys, key = { it.id }, contentType = { CHART_COLUMN }) { journey ->
                            // Where the column lies is asked for at the tap only: working out its bounds for
                            // every frame of a scroll made a Rect per column and frame.
                            val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
                            PeekScrubTarget(peek, journey, placed)
                            // When live times move a journey past another (see searchOrder), its
                            // column slides to its new place; pages that come in appear as before.
                            val moves = if (glide) {
                                Modifier.animateItem(fadeInSpec = null, placementSpec = Motion.settle(), fadeOutSpec = null)
                            } else {
                                Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                            }
                            JourneyColumn(
                                journey = journey,
                                minuteOf = minuteOf,
                                frame = frame,
                                nowY = nowY,
                                selected = journey.id == selectedId,
                                level = levels?.get(journey.id),
                                columnWidth = columnWidth,
                                modifier = moves
                                    .width(columnWidth)
                                    .fillMaxHeight()
                                    .onPlaced { placed[0] = it }
                                    .clip(RoundedCornerShape(20.dp))
                                    .closePeekOnRelease(peek)
                                    .combinedClickable(
                                        onLongClickLabel = peekLabel,
                                        onLongClick = {
                                            ticks.tick()
                                            peek.show(journey, placed[0]?.takeIf { it.isAttached }?.boundsInWindow())
                                        },
                                        hapticFeedbackEnabled = false,
                                    ) {
                                        OpenOrigin.set(placed[0]?.takeIf { it.isAttached }?.boundsInRoot())
                                        select(journey.id)
                                    },
                            )
                        }
                    }
                }
            }
            // The pinned total times as chips in the margin at an edge of the chart: they follow their
            // columns sideways and stay put while the chart scrolls up and down.
            if (bandTop > 0.dp || bandBottom > 0.dp) {
                PinnedTotals(
                    journeys = journeys,
                    listState = listState,
                    selectedId = selectedId,
                    plotStart = AXIS_WIDTH,
                    modifier = Modifier
                        .align(if (bandTop > 0.dp) Alignment.TopStart else Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(PINNED_TOTAL_BAND),
                )
            }
            // A page on its way at an edge, or how the last one ended there when it showed nothing new.
            ChartEdgeBadge(
                start = true,
                loading = edges.loadingEarlier && askedEarlier.value != null,
                note = earlierNote.value,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = AXIS_WIDTH),
            )
            ChartEdgeBadge(
                start = false,
                loading = edges.loadingLater && askedLater.value != null,
                note = laterNote.value,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

// How far a drag has to pull past an edge of the chart to load the next page there.
private val EDGE_PULL = 28.dp

// How long the note at an edge stays after a page that showed nothing new.
private const val EDGE_NOTE_MILLIS = 3_000L

/** [first] and [last] (indices, -1 for none) in one number, for a state that changes every frame. */
private fun packedRange(first: Int, last: Int): Long = (first.toLong() shl 32) or (last.toLong() and 0xffffffffL)
/**
 * Whether the columns laid out at [first] and [last] of the row in [listState] are the
 * journeys at these indices of [journeys] (by key): right after journeys were put in front,
 * the layout still has the indices of before. Read without observing, allocates nothing.
 */
private fun rangeMatches(listState: LazyListState, journeys: List<Journey>, first: Int, last: Int): Boolean =
    Snapshot.withoutReadObservation {
        val items = listState.layoutInfo.visibleItemsInfo
        var matches = true
        for (i in items.indices) {
            val item = items[i]
            if (item.index == first || item.index == last) {
                if (journeys.getOrNull(item.index)?.id != item.key) matches = false
            }
        }
        matches
    }

private fun rangeFirst(range: Long): Int = (range shr 32).toInt()
private fun rangeLast(range: Long): Int = range.toInt()

// The steps of the corners of a block between its small and its full form (see BlockShape).
private const val BLOCK_SHAPE_STEPS = 48

/** The content type of the columns of the chart: all alike, so the row reuses any of them. */
private const val CHART_COLUMN = "journey"

/**
 * The time window and the scale of the chart as they move. They glide every frame after a
 * sideways scroll or a change of the scale; only laying out and drawing read them, so the chart
 * is not composed again for every frame of that.
 */
@Stable
private class ChartFrame(
    private val start: State<Float>,
    private val end: State<Float>,
    private val scale: State<Dp>,
    /** The room above the minute the window starts at: CHART_TOP, and the total times where they stand on top. */
    val top: Dp = CHART_TOP,
) {
    val windowStart: Float get() = start.value
    val windowEnd: Float get() = end.value
    val perMinute: Dp get() = scale.value
    val span: Float get() = (windowEnd - windowStart).coerceAtLeast(10f)

    /** Where [minute] lies in the chart, from its top. */
    fun y(minute: Float): Dp = top + perMinute * (minute - windowStart)
}

/** Places the element [top] down from the top of its parent; [top] is read while placing. */
private fun Modifier.atTop(top: () -> Dp): Modifier = offset { IntOffset(0, top().roundToPx()) }

/** Makes the element exactly [height] high, as Modifier.height does; [height] is read while measuring. */
private fun Modifier.heightOf(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val wanted = constraints.constrainHeight(height().roundToPx().coerceAtLeast(0))
    val placeable = measurable.measure(constraints.copy(minHeight = wanted, maxHeight = wanted))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** The lines of the axis across the chart, one per tick (see axisTicks), each 1 dp high. */
private fun DrawScope.drawGrid(frame: ChartFrame) {
    val ticks = axisTicks(frame.windowStart, frame.windowEnd, frame.perMinute)
    val line = 1.dp.roundToPx().toFloat()
    var tick = ticks.first
    while (tick <= ticks.last) {
        val y = frame.y(tick.toFloat()).roundToPx().toFloat()
        drawRect(AppColors.Grid, topLeft = Offset(0f, y), size = Size(size.width, line))
        tick += ticks.step
    }
}

/** The line of "now" across an element: 2 dp high, its middle at [y]. */
private fun DrawScope.drawNowLine(y: Dp) {
    drawRect(
        AppColors.Blue,
        topLeft = Offset(0f, (y - 1.dp).roundToPx().toFloat()),
        size = Size(size.width, 2.dp.roundToPx().toFloat()),
    )
}

/**
 * The times along the left of the chart, and the pill of "now". Only a tick that comes into or
 * leaves the window composes it again; the labels move with the window while being placed.
 */
@Composable
private fun ChartAxis(
    frame: ChartFrame,
    base: OffsetDateTime,
    now: OffsetDateTime,
    nowY: () -> Dp?,
    modifier: Modifier = Modifier,
) {
    AllocProbe.begin(AllocProbe.CHART_AXIS)
    val ticks by remember(frame) { derivedStateOf { axisTicks(frame.windowStart, frame.windowEnd, frame.perMinute) } }
    val nowVisible by remember(nowY) { derivedStateOf { nowY() != null } }
    Box(modifier = modifier) {
        var tick = ticks.first
        while (tick <= ticks.last) {
            val minute = tick
            key(minute) {
                Text(
                    text = base.plusMinutes(minute.toLong()).clock(),
                    color = AppColors.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .atTop { frame.y(minute.toFloat()) - 7.dp }
                        // A label that would collide with the "now" marker gives way to it.
                        .drawWithContent { if (!givesWayToNow(frame.y(minute.toFloat()), nowY())) drawContent() },
                )
            }
            tick += ticks.step
        }
        if (nowVisible) {
            Box(
                modifier = Modifier
                    .atTop { (nowY() ?: 0.dp) - 9.dp }
                    .clip(RoundedCornerShape(9.dp))
                    .background(AppColors.Blue)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = now.clock(),
                    color = AppColors.OnBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
    AllocProbe.end(AllocProbe.CHART_AXIS)
}

/** A vertical row of dots: a stretch on foot. */
@Composable
private fun WalkDots(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(4.dp)) {
        if (size.height > size.width) {
            drawLine(
                AppColors.Muted.copy(alpha = 0.6f),
                Offset(size.width / 2f, size.width / 2f),
                Offset(size.width / 2f, size.height - size.width / 2f),
                strokeWidth = size.width,
                cap = StrokeCap.Round,
                // A dash of almost no length with round ends draws as a dot.
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 7.dp.toPx()), 0f),
            )
        }
    }
}

/** A small filled label in a column of the chart: the minutes of a tight change, or "cancelled". */
@Composable
private fun ColumnTag(text: String, ground: Color, ink: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = ink,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(ground)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// From this height a cancelled block holds its line and the tag "fällt aus" below it.
private val CANCEL_TAG_BLOCK = 38.dp

// The opacity of a ride that runs within a journey that cannot be made as a whole.
private const val FADED_RIDE_ALPHA = 0.55f

// The height of the pill of a change that lies over the blocks.
private val OVERLAP_TAG_HEIGHT = 18.dp

/**
 * The minutes of a change between two blocks as plain text without a ground, in the colour of
 * [tightness] (normal: muted grey; knapp: red; Sprint: magenta; relaxed: dark green), with a
 * tiny dot before it for the risky ones. The chart keeps room for it (see PILL_GAP).
 */
@Composable
private fun OverlapTag(minutes: Long, tightness: Tightness, modifier: Modifier = Modifier) {
    val ink = changeInk(tightness)
    Box(modifier = modifier.fillMaxWidth().height(OVERLAP_TAG_HEIGHT), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (tightness.ordinal >= Tightness.Risky.ordinal) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(ink),
                )
            }
            Text(
                text = stringResource(R.string.in_minutes, minutes),
                color = ink,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * The ink of the minutes of a change in the chart: the strong colour of its tightness, except
 * that a normal change stays in the muted grey of the other labels.
 */
@Composable
private fun changeInk(tightness: Tightness): Color = when (tightness) {
    Tightness.Tight -> AppColors.Muted
    else -> tightnessColor(tightness)
}

/**
 * One journey from its departure to its arrival: a block per ride in the colour of its
 * line, a dotted stretch for a walk at either end, the transfer stop or the minutes of a
 * tight change in the gap between two rides, and the times above and below. A late
 * departure shows where the ride was planned as a dashed outline, a late arrival as a
 * hatched end. In a cancelled journey only the cancelled ride is grey, hatched and tagged
 * "fällt aus"; the rides that still run keep their colour, a little faded.
 */
@Composable
private fun JourneyColumn(
    journey: Journey,
    minuteOf: (OffsetDateTime) -> Float,
    frame: ChartFrame,
    nowY: () -> Dp?,
    selected: Boolean,
    level: TransferComfort?,
    columnWidth: Dp,
    modifier: Modifier = Modifier,
) {
    AllocProbe.begin(AllocProbe.COLUMN_COMPOSE)
    val legs = journey.legs
    val cancelled = journey.cancelled
    // Notices about lifts raise the sign only for step-free travel (see warningAlerts).
    val stepFree = rememberStepFree()
    val textColor = when {
        cancelled -> AppColors.Muted
        selected -> AppColors.OnBlueContainer
        else -> AppColors.Ink
    }
    val decoration = if (cancelled) TextDecoration.LineThrough else null
    // A pick moves in softly: the ground turns blue and the blocks grow a little wider. Both are
    // read only while drawing and laying out: read while composing, every frame of the pick
    // composed the column (and its block names) again, just as a journey opens.
    val ground = animateColorAsState(
        // The same blue without opacity, so the fade does not pass through grey.
        if (selected) AppColors.BlueContainer else AppColors.BlueContainer.copy(alpha = 0f),
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "columnGround",
    )
    val insetState = animateDpAsState(
        if (selected) SELECTED_INSET else COLUMN_INSET,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "columnInset",
    )
    val inset = { insetState.value }

    // The times of the legs in minutes of the chart. Fresh live data moves them there with
    // a spring, so a block that is late glides to its new place instead of jumping. They are
    // kept as states and read only where the column is laid out (see columnGeometry).
    val glide = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    val times = ArrayList<State<Float>>(legs.size * 2)
    legs.forEachIndexed { index, leg ->
        key(index) {
            times.add(animateFloatAsState(minuteOf(leg.departure), glide, label = "legStart"))
            times.add(animateFloatAsState(minuteOf(leg.arrival), glide, label = "legEnd"))
        }
    }
    // How each change shows, by the change alone (see chartChanges); these get room of their own.
    // Rated afresh when the transfer tuning or the walking pace changes (see ratingSettingsShown).
    val ratingVersion = ratingSettingsShown.intValue
    val changes = remember(journey, ratingVersion) { AllocProbe.measure(AllocProbe.CHART_CHANGES) { chartChanges(journey) } }
    val pills = changes.keys

    // A ride that leaves late shows its "+2" above its time, as the delay setting says (see DelayDisplay).
    val display = LocalDelayDisplay.current
    val firstLeg = legs.first()
    val startDelay = firstLeg.departureDelayMinutes.takeIf { !cancelled && firstLeg.realtime && it > 0 }
    val startDelayShown = startDelay != null && (display.showsDelay || display.strikesPlanned)
    // The total time on top takes a line of its own above the departure time in every column.
    val totalTime = LocalChartTotalTime.current
    val labelHeight = (if (startDelayShown) 32.dp else 18.dp) + chartRoomOf(totalTime, top = true)
    // Short rides each in a block of their own, or a run of them as one (see ChartShortLegs).
    val combine = ChartShortLegsSetting.load(LocalContext.current) == ChartShortLegs.Combine

    // Where everything of the column lies right now: worked out again when the window, the scale
    // or the time of a leg moves, and read only while the column is laid out and drawn.
    // The same inputs as long as the legs and what they show are the same (the states of the
    // times stay the same for the same legs): a column composed again for another reason (a pick,
    // the clock) works out none of its geometry again.
    val inputs = rememberUpdatedState(remember(legs, pills, labelHeight, cancelled, combine) { ColumnInputs(legs, times, pills, labelHeight, cancelled, combine) })
    val geometryState = remember(frame) {
        derivedStateOf {
            AllocProbe.begin(AllocProbe.COLUMN_GEOMETRY)
            val input = inputs.value
            val count = input.legs.size
            val made = columnGeometry(
                input.legs,
                FloatArray(count) { input.times[2 * it].value },
                FloatArray(count) { input.times[2 * it + 1].value },
                frame.windowStart,
                frame.perMinute,
                input.pills,
                input.labelHeight,
                input.cancelled,
                frame.top,
                input.combine,
            )
            AllocProbe.end(AllocProbe.COLUMN_GEOMETRY)
            made
        }
    }
    val geometry = { geometryState.value }
    // What is shown at all: the column is composed again only when that changes.
    val look by remember(geometryState) { derivedStateOf { geometryState.value.look() } }

    // The tightest change of the journey decides whether it is marked as risky, by the time to
    // spare less any walk, as its category does. Only real changes count: two rides of the
    // same trip (staying seated) are none.
    val tightest = remember(journey, ratingVersion) { journeyTightness(journey) }
    val showStops = LocalChartChangeStops.current

    Box(
        modifier = modifier
            .probed(AllocProbe.COLUMN_MEASURE, AllocProbe.COLUMN_DRAW)
            .drawBehind { drawRect(ground.value) }
            // The line of "now" above the ground of the column and below everything else in it.
            .drawBehind { nowY()?.let { drawNowLine(it) } },
    ) {
        // The triangle next to the departure time says: this journey has a disruption notice.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .atTop { geometry().departureTop },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (totalTime == ChartTotalTime.Above) TotalTimeText(journey, selected)
            val startPlanned = firstLeg.plannedDeparture
            if (startDelayShown && display.strikesPlanned && startPlanned != null) {
                StruckTime(startPlanned, modifier = Modifier.height(14.dp))
            } else if (startDelayShown && startDelay != null) {
                Text(
                    text = stringResource(R.string.delay_short, startDelay.toInt()),
                    color = AppColors.DelayRed,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (alertingWarnings(journey.warnings, stepFree).isNotEmpty()) WarningMark(size = 12.dp)
                Text(
                    text = (if (cancelled) journey.departure else display.clockOf(journey.departure, firstLeg.plannedDeparture)).clock(),
                    color = if (startDelay != null && !display.showsPlanned) AppColors.DelayRed else textColor,
                    textDecoration = decoration,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        look.legIndices.forEachIndexed { index, legIndex ->
            key(legIndex) {
                val leg = legs[legIndex]
                // This stretch as it lies right now; none for a moment where the stretches of the
                // column changed and it is not composed anew yet.
                val part = { geometry().parts.getOrNull(index)?.takeIf { it.index == legIndex } }
                if (leg.walking) {
                    Column(
                        modifier = Modifier
                            .atTop { part()?.top ?: 0.dp }
                            .fillMaxWidth()
                            .heightOf { part()?.height ?: 0.dp },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        WalkDots(modifier = Modifier.weight(1f))
                        if (look.walkLabels.getOrElse(index) { false }) {
                            WalkMark(
                                minutes = minutesBetween(leg.departure, leg.arrival),
                                color = if (selected) AppColors.OnBlueContainer else AppColors.Muted,
                                unit = true,
                                cycling = leg.cycling,
                            )
                        }
                        WalkDots(modifier = Modifier.weight(1f))
                    }
                } else if (look.combined.getOrElse(index) { 0 } > 0) {
                    // A run of short rides as one block (see ChartShortLegs.Combine).
                    val size = look.combined[index]
                    val rides = remember(legs, legIndex, size) { legs.drop(legIndex).filter { !it.walking }.take(size) }
                    CombinedBlock(
                        rides = rides,
                        part = part,
                        inset = inset,
                        named = look.named.getOrElse(index) { false },
                        room = columnWidth - (if (selected) SELECTED_INSET else COLUMN_INSET) * 2 - BLOCK_LABEL_PADDING * 2,
                    )
                    ChangeMark(index, legIndex, look, changes, legs, geometry, selected, showStops)
                } else {
                    // Only the ride that is cancelled is struck; the others of the journey still run.
                    val grey = leg.cancelled
                    val faded = cancelled && !grey
                    // From the small form to the full one by the height of the block, eased by a
                    // spring: zooming and dragging morph the name and the corners, never switch them.
                    val morph = rememberFollower(
                        target = { part()?.let { ((it.height - MIN_BLOCK) / (FULL_BLOCK - MIN_BLOCK)).coerceIn(0f, 1f) } },
                        stiffness = Spring.StiffnessMediumLow,
                    )
                    val color = lineColor(leg)
                    val dashed = look.dashed.getOrElse(index) { false }
                    val shape = remember(morph, dashed) { BlockShape({ morph.value }, squareTop = dashed) }
                    if (dashed) {
                        val dash = { if (part() != null) geometry().dashes[index] else 0.dp }
                        // Where the ride should have been: an outline that runs on below the block.
                        Box(
                            modifier = Modifier
                                .insetX(inset)
                                .atTop { (part()?.top ?: 0.dp) - dash() }
                                .fillMaxWidth()
                                .heightOf { dash() + shape.round() }
                                .drawWithCache {
                                    val line = 2.dp.toPx()
                                    val outline = Stroke(
                                        width = line,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
                                    )
                                    onDrawBehind {
                                        drawRoundRect(
                                            color = color,
                                            topLeft = Offset(line / 2f, line / 2f),
                                            size = Size(size.width - line, size.height - line),
                                            cornerRadius = CornerRadius(shape.round().toPx()),
                                            style = outline,
                                        )
                                    }
                                },
                        )
                    }
                    // How much of the block lies behind the timetable at its end.
                    val lateEnd = leg.arrivalDelayMinutes.toInt()
                    val tailed = !grey && lateEnd > 0
                    // A plain box: the width of a block is known (the column less its inset on both
                    // sides), and its height, place and corners are only read while it is laid out
                    // and drawn, so zooming the chart or gliding its window composes nothing.
                    Box(
                        modifier = Modifier
                            .insetX(inset)
                            .atTop { part()?.top ?: 0.dp }
                            .fillMaxWidth()
                            // A ride too short for a block is a thin bar of its colour at its time.
                            .heightOf { part()?.height?.coerceAtLeast(THIN_BAR) ?: 0.dp }
                            .graphicsLayer {
                                this.shape = shape.current()
                                clip = true
                            }
                            .then(if (faded) Modifier.alpha(FADED_RIDE_ALPHA) else Modifier)
                            .then(
                                when {
                                    grey -> Modifier
                                        .background(AppColors.Chip)
                                        .stripes(AppColors.Grid, step = 9.dp, width = 4.dp)
                                        .border(2.dp, AppColors.Rail, shape)

                                    tailed -> Modifier
                                        .background(color)
                                        .stripes(
                                            Color.White.copy(alpha = 0.6f),
                                            fromShare = {
                                                val height = part()?.height ?: 0.dp
                                                1f - (frame.perMinute * lateEnd / height).coerceIn(0.1f, 0.45f)
                                            },
                                            step = 6.dp,
                                            width = 2.5.dp,
                                        )

                                    else -> Modifier.background(color)
                                },
                            )
                            .then(if (leg.warnings.any { warningAlerts(it, stepFree) } && !grey) Modifier.warningBand() else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        // A low block gets a smaller name, so even a ride of one minute says which line it is.
                        // The size is that of every block ("S1", "U9") at most; a name too wide for its
                        // block gets smaller until it fits; a train keeps its kind and number ("RJ 174") however
                        // narrow, a coach drops its operator ("Flix N1334" as "N1334").
                        // A rail replacement bus says so: "SEV S1", or "SEV" where that does not fit.
                        val blockReplacement = leg.railReplacement
                        // Too low for its name: only its colour (see NAMED_BLOCK).
                        if (look.named.getOrElse(index) { true }) {
                        val fullLabel = blockReplacement?.let { listOfNotNull(SEV_MARK, it.replaced).joinToString(" ") }
                            ?: badgeLine(leg.line) ?: "?"
                        BlockLabel(
                            full = fullLabel,
                            compact = if (blockReplacement != null) SEV_MARK else compactLine(leg.line) ?: fullLabel,
                            morph = morph,
                            // By where the inset goes: the name is fitted once, not for each frame of a pick.
                            room = columnWidth - (if (selected) SELECTED_INSET else COLUMN_INSET) * 2 - BLOCK_LABEL_PADDING * 2,
                            ink = if (grey) AppColors.Muted else onLineColor(color),
                            struck = grey,
                            // The cancelled ride itself says so, where its block has room for the tag.
                            tag = look.cancelTagPart == index,
                            mode = if (blockReplacement != null) TravelMode.Bus else modeHint(leg.line, leg.product),
                        )
                        }
                    }

                    ChangeMark(index, legIndex, look, changes, legs, geometry, selected, showStops)
                }
            }
        }

        val lastLeg = legs.last()
        val arrivalDelay = if (lastLeg.realtime) lastLeg.arrivalDelayMinutes else 0L
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .atTop { geometry().arrivalTop },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = (if (cancelled) journey.arrival else display.clockOf(journey.arrival, lastLeg.plannedArrival)).clock(),
                color = if (!cancelled && arrivalDelay > 0 && !display.showsPlanned) AppColors.DelayRed else textColor,
                textDecoration = decoration,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            // Right under the arrival time, above the delay and the rating of the changes.
            if (totalTime == ChartTotalTime.Below) TotalTimeText(journey, selected)
            if (cancelled) {
                // The cancelled ride had no room for its tag: it stands below the column.
                if (look.cancelTagPart < 0) {
                    ColumnTag(
                        text = stringResource(R.string.cancelled_ride_tag),
                        ground = AppColors.DelayRed,
                        ink = AppColors.OnDelayRed,
                    )
                }
            } else {
                val endPlanned = lastLeg.plannedArrival
                if (arrivalDelay > 0 && display.strikesPlanned && endPlanned != null) StruckTime(endPlanned)
                if (arrivalDelay > 0 && display.showsDelay) {
                    Text(
                        text = stringResource(R.string.delay_minutes, arrivalDelay.toInt()),
                        color = AppColors.DelayRed,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
                if (level != null) {
                    // With every connection shown, the category takes the place of the tight mark.
                    Text(
                        text = levelTag(level),
                        color = levelStrongColor(level),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else if (tightest == Tightness.Risky || tightest == Tightness.VeryRisky) {
                    // Only a warning is worth the room: a normal change needs no mark, as its
                    // category would call it "normal" too.
                    Text(
                        text = stringResource(tightnessName(tightest)),
                        color = tightnessColor(tightest),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
                // The variants with only another short last bus or tram it stands for (#57).
                if (journey.groupedAlternatives > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.chart_alternatives, journey.groupedAlternatives, journey.groupedAlternatives),
                        color = AppColors.Muted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
    AllocProbe.end(AllocProbe.COLUMN_COMPOSE)
}

// The height of the line with the total time of a journey (see TotalTimeText).
private val TOTAL_TIME_HEIGHT = 12.dp

/**
 * The room the chart keeps for the total times at its [top] (or its bottom) for [setting]: a
 * line in every column where they stand above or below it, none otherwise, so no column jumps
 * for it. Pinned totals take no room in the columns (see pinnedBandOf).
 */
internal fun chartRoomOf(setting: ChartTotalTime, top: Boolean): Dp = when (setting) {
    ChartTotalTime.Above -> if (top) TOTAL_TIME_HEIGHT else 0.dp
    ChartTotalTime.Below -> if (top) 0.dp else TOTAL_TIME_HEIGHT
    ChartTotalTime.Off, ChartTotalTime.PinnedTop, ChartTotalTime.PinnedBottom -> 0.dp
}

// The height of the band with the pinned total times at an edge of the chart (see PinnedTotals).
internal val PINNED_TOTAL_BAND = 18.dp

/**
 * The band the chart keeps at its [top] (or its bottom) edge for the pinned total times of
 * [setting]: it stays put while the chart scrolls up and down, and the chart scrolls in the
 * rest of the card, so the columns themselves are laid out as without it.
 */
internal fun pinnedBandOf(setting: ChartTotalTime, top: Boolean): Dp = when (setting) {
    ChartTotalTime.PinnedTop -> if (top) PINNED_TOTAL_BAND else 0.dp
    ChartTotalTime.PinnedBottom -> if (top) 0.dp else PINNED_TOTAL_BAND
    ChartTotalTime.Off, ChartTotalTime.Above, ChartTotalTime.Below -> 0.dp
}

/**
 * The total times of [journeys] at an edge of the chart, each as a small chip centred over its
 * column as the row in [listState] lies right now (read while drawing: a scroll composes
 * nothing and measures nothing), from [plotStart] on (the axis stays free). There is no band
 * across the card: only the chips, in the margin the chart keeps for them. Near the left and
 * right edge of the plot they fade out as the columns do (see fadeEdges), so none is cut hard;
 * the picked column's chip takes the picked look of TotalTimeText.
 */
@Composable
private fun PinnedTotals(
    journeys: List<Journey>,
    listState: LazyListState,
    selectedId: String?,
    plotStart: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val labels = remember(journeys, context) { journeys.associate { it.id to durationText(context, journeyTotalMinutes(it)) } }
    // Text laid out once per label, then reused for every frame of a scroll.
    val laidOut = remember(measurer) { HashMap<String, TextLayoutResult>() }
    val picked = rememberUpdatedState(selectedId)
    // The same type as the total time in a column (see TotalTimeText).
    val style = remember { TextStyle(fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold) }
    val chip = AppColors.Chip
    val chipPicked = AppColors.BlueContainer
    val muted = AppColors.Muted
    val inkPicked = AppColors.OnBlueContainer
    Box(
        modifier = modifier.drawBehind {
            val left = plotStart.toPx()
            val right = size.width
            val padX = PINNED_CHIP_PAD_X.toPx()
            val chipHeight = PINNED_CHIP_HEIGHT.toPx().coerceAtMost(size.height)
            val chipTop = (size.height - chipHeight) / 2f
            val fade = PINNED_CHIP_FADE.toPx()
            val corner = CornerRadius(chipHeight / 2f)
            val items = listState.layoutInfo.visibleItemsInfo
            for (i in items.indices) {
                val item = items[i]
                val id = item.key as? String ?: continue
                val text = labels[id] ?: continue
                val layout = laidOut.getOrPut(text) { measurer.measure(text, style, maxLines = 1, softWrap = false) }
                val chipWidth = layout.size.width + padX * 2f
                val chipLeft = left + item.offset + (item.size - chipWidth) / 2f
                val chipRight = chipLeft + chipWidth
                // Fully there while a fade length inside the plot, gone where it would touch its edge.
                val alpha = minOf((chipLeft - left) / fade, (right - chipRight) / fade).coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                val isPicked = id == picked.value
                drawRoundRect(
                    color = if (isPicked) chipPicked else chip,
                    topLeft = Offset(chipLeft, chipTop),
                    size = Size(chipWidth, chipHeight),
                    cornerRadius = corner,
                    alpha = alpha,
                )
                drawText(
                    layout,
                    color = if (isPicked) inkPicked else muted,
                    topLeft = Offset(chipLeft + padX, chipTop + (chipHeight - layout.size.height) / 2f),
                    alpha = alpha,
                )
            }
        },
    )
}

// A pinned total time's chip: its height, the room beside its text, and how far from the edge
// of the plot it starts to fade (as the columns, see the LazyRow's fadeEdges).
private val PINNED_CHIP_HEIGHT = 14.dp
private val PINNED_CHIP_PAD_X = 6.dp
private val PINNED_CHIP_FADE = 22.dp

/**
 * The total time of [journey] door to door, the walks to the first stop and from the last one
 * included: from its first departure to its last arrival, in whole minutes, never below zero.
 */
internal fun journeyTotalMinutes(journey: Journey): Long =
    java.time.Duration.between(journey.departure, journey.arrival).toMinutes().coerceAtLeast(0L)

/**
 * A total time for people, as [durationLabel] says it: [minutesOnly] below an hour ("48 min"),
 * [wholeHours] on the hour ("2:00 h") and [hoursAndMinutes] otherwise ("2:23 h").
 */
internal fun totalTimeText(
    minutes: Long,
    minutesOnly: (Long) -> String,
    wholeHours: (Long) -> String,
    hoursAndMinutes: (Long, Long) -> String,
): String {
    val (hours, rest) = durationSpan(minutes)
    return when {
        hours == null -> minutesOnly(rest ?: 0L)
        rest == null -> wholeHours(hours)
        else -> hoursAndMinutes(hours, rest)
    }
}

/** The total time of [journey] in a column of the chart: small, muted, one line (see ChartTotalTime). */
@Composable
private fun TotalTimeText(journey: Journey, selected: Boolean) {
    Text(
        text = durationLabel(journeyTotalMinutes(journey)),
        color = if (selected) AppColors.OnBlueContainer else AppColors.Muted,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier.height(TOTAL_TIME_HEIGHT),
    )
}

// How far the blocks of a column stand in from its sides; a picked column's a little less.
private val COLUMN_INSET = 5.dp
private val SELECTED_INSET = 2.dp

/** Pads the element by [inset] on the left and the right; [inset] is read while measuring. */
private fun Modifier.insetX(inset: () -> Dp): Modifier = layout { measurable, constraints ->
    val side = inset().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.offset(horizontal = -2 * side))
    layout(constraints.constrainWidth(placeable.width + 2 * side), placeable.height) { placeable.place(side, 0) }
}


// The mark of a change whose minutes do not fit into its gap by the clock.
private val CHANGE_DOT = 5.dp

/**
 * The change below stretch [index] (leg [legIndex]) of a journey column: its minutes, or its pill
 * where it is risky, where the gap by the clock has room for them (see ColumnLook.gapRoom), else
 * a small dot. Each change shows by itself alone (see chartChanges), the same in every column.
 */
@Composable
private fun ChangeMark(
    index: Int,
    legIndex: Int,
    look: ColumnLook,
    changes: Map<Int, ChartChange>,
    legs: List<Leg>,
    geometry: () -> ColumnGeometry,
    selected: Boolean,
    showStops: Boolean,
) {
    // The gap to the next ride is the change: how long it is, and how tight. Each
    // change shows by itself alone (see chartChanges), the same in every column.
    val nextIndex = look.legIndices.getOrNull(index + 1)
    val change = nextIndex?.let { changes[it] }
    if (nextIndex != null && change != null && legs.getOrNull(nextIndex)?.walking == false) {
        // How much of the label the gap holds where it lies by the clock (see ColumnLook.gapRoom).
        val room = look.gapRoom.getOrElse(index) { 0 }
        // The gap as it lies right now, while the column still has these stretches.
        val gap = {
            geometry().takeIf {
                it.parts.getOrNull(index)?.index == legIndex && it.parts.getOrNull(index + 1)?.index == nextIndex
            }
        }
        if (room == 0 || (change.pill && room < 2)) {
            // No room in the true gap: a small dot of the tone of the change, pushing nothing.
            val tone = if (change.pill) AppColors.DelayRed else toneColor(change.tone)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .atTop { (gap()?.gapMiddle(index) ?: 0.dp) - CHANGE_DOT / 2 }
                    .height(CHANGE_DOT)
                    .zIndex(1f)
                    .drawBehind { drawCircle(tone, radius = size.height / 2f) },
            )
        } else if (change.pill) {
            // The minutes of a risky change: a pill in the middle of the room kept
            // free for it (see PILL_GAP); a dashed outline of a late ride may lie behind it.
            OverlapTag(
                minutes = change.minutes,
                tightness = change.tightness,
                modifier = Modifier
                    .zIndex(1f)
                    .atTop { (gap()?.pillMiddle(index) ?: 0.dp) - OVERLAP_TAG_HEIGHT / 2 },
            )
        } else {
            // The name of the stop only where the setting asks for it (it crowds the
            // chart) and the gap has room for it above the minutes, which always show.
            val twoLines = showStops && look.roomyGaps.getOrElse(index) { false }
            Column(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .fillMaxWidth()
                    .atTop { (gap()?.gapMiddle(index) ?: 0.dp) - (if (twoLines) 12.dp else 6.dp) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (twoLines) {
                    Text(
                        // A long-distance ride names the change as the railway does (#93).
                        text = legs.subList(0, nextIndex).last { !it.walking }.let { ride -> if (ride.railwayNames) shownRideStop(ride, ride.to) else ride.to.briefStopName() },
                        color = if (selected) AppColors.OnBlueContainer else AppColors.Muted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.in_minutes, change.minutes),
                    color = toneColor(change.tone),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

// The width of the strip of line colours in a block of combined short rides.
private val COMBINED_STRIP = 5.dp

/**
 * A run of short [rides] as one block (see ChartShortLegs.Combine), where they lie by the clock
 * ([part]): a strip of the colours of their lines, each band at its own times, a dot at each
 * change between them, and their names ("M11 · S1 · U9") where [named] and they fit into
 * [room], else the first with the count of the others ("M11 +3"). Read while drawing only.
 */
@Composable
private fun CombinedBlock(rides: List<Leg>, part: () -> ColumnPart?, inset: () -> Dp, named: Boolean, room: Dp) {
    val colors = remember(rides) { rides.map { lineColor(it) } }
    val dot = AppColors.Muted
    Box(
        modifier = Modifier
            .insetX(inset)
            .atTop { part()?.top ?: 0.dp }
            .fillMaxWidth()
            .heightOf { part()?.height?.coerceAtLeast(THIN_BAR) ?: 0.dp }
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Chip)
            .drawBehind {
                val bands = part()?.bands ?: return@drawBehind
                val strip = COMBINED_STRIP.toPx()
                val least = 1.dp.toPx()
                for (k in colors.indices) {
                    if (2 * k + 1 >= bands.size) break
                    val top = bands[2 * k].dp.toPx()
                    val bottom = maxOf(bands[2 * k + 1].dp.toPx(), top + least)
                    drawRect(colors[k], topLeft = Offset(0f, top), size = Size(strip, bottom - top))
                    if (k > 0) {
                        val before = bands[2 * k - 1].dp.toPx()
                        drawCircle(dot, radius = 1.5.dp.toPx(), center = Offset(strip / 2f, (before + top) / 2f))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (named) {
            val names = remember(rides) { rides.map { badgeLine(it.line) ?: "?" } }
            val fit = fitBlockLabel(
                full = names.joinToString(" · "),
                compact = names.first() + " +" + (names.size - 1),
                room = room - COMBINED_STRIP - 3.dp,
            )
            Text(
                text = fit.text,
                color = AppColors.Ink,
                fontSize = fit.size(11f).sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = COMBINED_STRIP + 3.dp),
            )
        }
    }
}

/** What the geometry of a journey column is worked out from (see columnGeometry). */
private class ColumnInputs(
    val legs: List<Leg>,
    val times: List<State<Float>>,
    val pills: Set<Int>,
    val labelHeight: Dp,
    val cancelled: Boolean,
    val combine: Boolean,
)

/**
 * The shape of a ride block: its corners grow from 8 dp to BLOCK_ROUND with [morph]; with
 * [squareTop] (a late ride with its outline above) only the lower ones are round. [morph] is
 * read where the shape is used, while drawing, so a morph composes nothing.
 */
private class BlockShape(private val morph: () -> Float, private val squareTop: Boolean) : Shape {
    fun round(): Dp = lerp(8.dp, BLOCK_ROUND, morph())

    // The shapes by quarter dp, made once each: a new shape every frame of a morph made the
    // layer work out a new outline (and a path, for the square top) each frame.
    private val made = arrayOfNulls<Shape>(BLOCK_SHAPE_STEPS + 1)

    /** The shape as it is at this moment. */
    fun current(): Shape {
        val step = (((round() - 8.dp) / (BLOCK_ROUND - 8.dp)).coerceIn(0f, 1f) * BLOCK_SHAPE_STEPS).roundToInt()
        made[step]?.let { return it }
        val round = lerp(8.dp, BLOCK_ROUND, step / BLOCK_SHAPE_STEPS.toFloat())
        val shape = if (squareTop) RoundedCornerShape(0.dp, 0.dp, round, round) else RoundedCornerShape(round)
        made[step] = shape
        return shape
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        current().createOutline(size, layoutDirection, density)
}

/**
 * The name of the line in a ride block, sized by the [morph] of the block within [room] (see
 * fitBlockLabel), and the tag of a cancelled ride below it where [tag]. The name is laid out
 * once, at the size of the full form, and drawn smaller by the morph: a block that morphs (the
 * chart zoomed, its window gliding) composes and lays out nothing again, it is only drawn.
 */
@Composable
private fun BlockLabel(
    full: String,
    compact: String,
    morph: State<Float>,
    room: Dp,
    ink: Color,
    struck: Boolean,
    tag: Boolean,
    mode: TravelMode? = null,
) {
    AllocProbe.begin(AllocProbe.BLOCK_LABEL)
    // The mode icon gives way before the name gets smaller than the smallest size: a train
    // named by its number keeps the number ("174", "RJ 174"), the icon goes where room is short.
    // Only a block with a mode icon measures its name a second time, with the icon.
    val plainFit = fitBlockLabel(full = full, compact = compact, room = room, marked = false)
    val markedFit = if (mode != null) fitBlockLabel(full = full, compact = compact, room = room, marked = true) else null
    val markShown = markedFit != null && markedFit.fitted(BLOCK_LABEL_REFERENCE_SIZE) >= BLOCK_LABEL_MIN_SIZE
    val fit = if (markShown) markedFit!! else plainFit
    // The full form: 15 sp where the name fits, as at a morph of 1 (11 sp + 4 sp of morph).
    val laidOut = fit.size(BLOCK_LABEL_REFERENCE_SIZE)
    // Laid out at the full size, the name may be taller than a low block: it is measured as tall
    // as it is and stands in the middle of the block, as the smaller name it is drawn as does.
    Column(modifier = Modifier.wrapContentHeight(unbounded = true), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    // The size of the name at this moment of the morph, around its middle.
                    val scale = fit.size(11f + 4f * morph.value) / laidOut
                    scaleX = scale
                    scaleY = scale
                }
                .padding(horizontal = BLOCK_LABEL_PADDING),
        ) {
            // The kind of vehicle where the name does not tell it (see modeHint), as tall as the
            // capitals of the name and growing with it.
            if (mode != null && markShown) {
                val mark = with(LocalDensity.current) { (laidOut * 0.8f).sp.toDp() }
                val gap = with(LocalDensity.current) { (laidOut * 0.2f).sp.toDp() }
                ModeMark(mode, ink, mark, Modifier.padding(end = gap))
            }
            Text(
                text = fit.text,
                color = ink,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                fontSize = laidOut.sp,
                lineHeight = BLOCK_LABEL_FULL_LINE.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                // A name too wide even at the smallest size ("ICE 1092" in a very narrow column)
                // ends in "…" instead of being cut mid-letter.
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (tag) {
            ColumnTag(
                text = stringResource(R.string.cancelled_ride_tag),
                ground = AppColors.DelayRed,
                ink = AppColors.OnDelayRed,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    AllocProbe.end(AllocProbe.BLOCK_LABEL)
}

// The line height (sp) of the name of a line in a block of the full form; a smaller form is the
// same line drawn smaller.
private const val BLOCK_LABEL_FULL_LINE = 18f

/**
 * A value that follows [target] with a spring of [stiffness] without bounce, as
 * animateFloatAsState does (a new target while it moves keeps its speed), but with the target
 * read outside of composition: where the target moves every frame, as the height of a block
 * while the window glides, nothing is composed again for it, only what reads the value is
 * updated. A null target leaves the value going where it goes.
 */
@Composable
private fun rememberFollower(target: () -> Float?, stiffness: Float): State<Float> {
    val currentTarget = rememberUpdatedState(target)
    val follower = remember { Follower(Snapshot.withoutReadObservation { target() } ?: 0f, stiffness) }
    LaunchedEffect(follower) {
        val scope = this
        // One spring runs while the target moves: a new target only turns it (see Follower.aim),
        // so no animation and no coroutine is started for each frame of a glide.
        snapshotFlow {
            AllocProbe.begin(AllocProbe.FOLLOWER_TARGET)
            try {
                currentTarget.value()
            } finally {
                AllocProbe.end(AllocProbe.FOLLOWER_TARGET)
            }
        }.filterNotNull().collect { goal -> follower.aim(goal, scope) }
    }
    return follower
}

/**
 * The value of [rememberFollower]: it runs one spring towards the newest goal, frame by frame.
 * A new goal while it runs starts the spring afresh from where the value is, with its speed, as
 * an Animatable that is sent to a new target does, but without a new animation for each.
 */
@Stable
private class Follower(initial: Float, stiffness: Float) : State<Float> {
    private val shown = mutableFloatStateOf(initial)
    override val value: Float get() = shown.floatValue

    private val spring = FloatSpringSpec(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)
    private var goal = initial
    private var running: Job? = null

    /** Sends the value towards [target], with the spring that runs in [scope] or a new one. */
    fun aim(target: Float, scope: CoroutineScope) {
        goal = target
        if (running?.isActive != true && target != shown.floatValue) running = scope.launch { run() }
    }

    // The spring that runs (see run): where it started, with what speed, to where, and when.
    private var from = 0f
    private var speed = 0f
    private var to = 0f
    private var start = -1L
    private var last = 0L

    private suspend fun run() {
        // As every animation: slower or faster with the animation scale of the system, at once without animations.
        val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        from = shown.floatValue
        speed = 0f
        to = goal
        start = -1L
        last = 0L
        while (true) {
            val frame = withFrameNanos { it }
            AllocProbe.begin(AllocProbe.FOLLOWER)
            val arrived = try {
                step(frame, durationScale)
            } finally {
                AllocProbe.end(AllocProbe.FOLLOWER)
            }
            if (arrived) return
        }
    }

    /** One frame of the spring at [frame] (nanoseconds); true when it has arrived and stops. */
    private fun step(frame: Long, durationScale: Float): Boolean {
        if (durationScale <= 0f) {
            shown.floatValue = goal
            return true
        }
        if (start < 0L) {
            start = frame
            last = frame
        }
        if (goal != to) {
            // A new goal: on from where the value was at the last frame, with its speed there.
            val played = ((last - start) / durationScale).toLong()
            val at = spring.getValueFromNanos(played, from, to, speed)
            speed = spring.getVelocityFromNanos(played, from, to, speed)
            from = at
            to = goal
            start = last
        }
        val play = ((frame - start) / durationScale).toLong()
        last = frame
        if (play >= spring.getDurationNanos(from, to, speed)) {
            shown.floatValue = to
            if (goal == to) return true
            // A goal came in just as the spring arrived: on to it from rest.
            from = to
            speed = 0f
            to = goal
            start = frame
        } else {
            shown.floatValue = spring.getValueFromNanos(play, from, to, speed)
        }
        return false
    }
}

@Composable
private fun JourneyList(
    journeys: List<Journey>,
    selected: Int,
    levels: Map<String, TransferComfort>?,
    now: OffsetDateTime,
    onSelect: (Int) -> Unit,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
    rowTops: MutableMap<String, Int> = mutableMapOf(),
    earlier: ListEarlier? = null,
) {
    // The row that is first, and where it lies, while earlier ones are asked for: each time they
    // come in above it, the list scrolls on so that it stays where it was on screen (#150). Held
    // from the tap until the loading has ended.
    var earlierAnchor by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val firstId = journeys.firstOrNull()?.id
    LaunchedEffect(firstId) {
        val (id, before) = earlierAnchor ?: return@LaunchedEffect
        if (firstId == null || firstId == id) return@LaunchedEffect
        // Laid out first; the list grows softly (softSize), so its end may come a few frames later.
        withFrameNanos { }
        val after = rowTops[id]
        if (after != null) {
            val target = scroll.value + earlierScrollShift(before, after)
            for (frame in 0 until EARLIER_SETTLE_FRAMES) {
                scroll.scrollTo(minOf(target, scroll.maxValue))
                if (scroll.value >= target) break
                withFrameNanos { }
            }
        }
        // The new first row is the one kept in place if still more come in.
        earlierAnchor = earlierAnchor?.let { firstId to (rowTops[firstId] ?: 0) }
    }
    val loadingEarlier = earlier?.loading == true
    val holdingEarlier = earlierAnchor != null
    LaunchedEffect(loadingEarlier, holdingEarlier) {
        if (loadingEarlier || !holdingEarlier) return@LaunchedEffect
        // Not loading (any more): what came is in place after a moment, and the rows glide again.
        // Also where no loading started at all, the hold ends by itself.
        delay(EARLIER_HOLD_MILLIS)
        earlierAnchor = null
    }
    // Notices about lifts raise the sign only for step-free travel (see warningAlerts).
    val stepFree = rememberStepFree()
    // A row held down shows its connection in a peek card until it is let go.
    val peek = remember { PeekState() }
    val ticks = rememberTicks()
    val peekLabel = stringResource(R.string.connection_peek)
    ConnectionPeek(peek, now)
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(32.dp)) {
        Column(
            modifier = Modifier
                // A held row's peek follows the finger up and down the rows (see PeekScrub.kt).
                .peekScrub(peek, vertical = true) { ticks.tick() }
                .fadeEdges(scroll, horizontal = false)
                .verticalScroll(scroll)
                // Earlier or later journeys that come in move the list softly.
                .softSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Each row keyed by its journey: when live times reorder the list, the row and its
            // state go with the journey and glide to the new place (see glideToPlace).
            // While earlier rows come in above, the rows keep their place on screen instead of gliding.
            val glide = rememberAnimationsOn() && earlierAnchor == null
            if (earlier != null) {
                ListEarlierRow(earlier.loading) {
                    val first = journeys.firstOrNull()
                    if (first != null) earlierAnchor = first.id to (rowTops[first.id] ?: 0)
                    earlier.onClick()
                }
            }
            journeys.forEachIndexed { index, journey ->
                key(journey.id) {
                    // Where the row is, so a journey opened on its own page grows out of it.
                    val bounds = remember { arrayOfNulls<Rect>(1) }
                    // The row itself, asked for its place in the window only when it is held down.
                    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    PeekScrubTarget(peek, journey, placed)
                    Column(
                        modifier = Modifier
                            // Where the row lies in the list, before it glides there (see ConnectionsState.rowTops).
                            .onPlaced {
                                rowTops[journey.id] = it.positionInParent().y.roundToInt()
                                placed[0] = it
                            }
                            .glideToPlace(glide)
                            .then(appearModifier(index))
                            .fillMaxWidth()
                            .boundsInto(bounds)
                            .clip(groupShape(index, journeys.lastIndex, outer = 24.dp))
                            .background(if (index == selected) AppColors.BlueContainer else AppColors.Card)
                            .closePeekOnRelease(peek)
                            .combinedClickable(
                                onLongClickLabel = peekLabel,
                                onLongClick = {
                                    ticks.tick()
                                    peek.show(journey, placed[0]?.takeIf { it.isAttached }?.boundsInWindow())
                                },
                                hapticFeedbackEnabled = false,
                            ) {
                                OpenOrigin.set(bounds[0])
                                onSelect(index)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // A refreshed time (a new delay) cross-fades instead of jumping.
                            FadeSwap(
                                target = journeySpanText(journey, R.string.time_range, AppColors.Ink) to journey.cancelled,
                                modifier = Modifier.weight(1f),
                            ) { (span, cancelled) ->
                                Text(
                                    text = span,
                                    color = if (cancelled) AppColors.Muted else AppColors.Ink,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    maxLines = 1,
                                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                                )
                            }
                            if (alertingWarnings(journey.warnings, stepFree).isNotEmpty()) {
                                WarningMark(modifier = Modifier.padding(end = 8.dp))
                            }
                            MinutesPill(
                                minutes = minutesBetween(now, journey.departure),
                                live = index == selected,
                                cancelled = journey.cancelled,
                                at = journey.departure,
                            )
                        }
                        // The rides as one bar, each as wide as it is long.
                        LegBar(journey = journey, labels = true)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.duration_and_transfers,
                                    durationLabel(journey.durationMinutes),
                                    journey.transfers,
                                ),
                                color = AppColors.Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            // A journey into fare zone C says so, quietly.
                            if (rememberNeedsZoneC(journey)) FareZoneChip()
                            // With every connection shown, each card says which category it is.
                            levels?.get(journey.id)?.let { level ->
                                val (tagGround, tagInk) = levelColors(level)
                                Text(
                                    text = levelTag(level),
                                    color = tagInk,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(tagGround)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The row on top of the list of connections that asks for earlier ones (#150). */
class ListEarlier(val loading: Boolean, val onClick: () -> Unit)

// How many frames the list may take to grow before it is scrolled to keep its first row in place.
private const val EARLIER_SETTLE_FRAMES = 40

// How long the list holds its first row in place after earlier ones stopped loading.
private const val EARLIER_HOLD_MILLIS = 400L

/** "Frühere Verbindungen", with a small spinner while they load. */
@Composable
private fun ListEarlierRow(loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = !loading, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AppColors.Blue)
        } else {
            Chevron(open = true, color = AppColors.Blue)
        }
        Text(
            text = stringResource(R.string.r6_list_earlier),
            color = AppColors.Blue,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/**
 * The detail of [journey] alone, as a preview (#120, the preview of a shortcut): no route to
 * keep, no other connections to pick at its changes; starting the trip from it calls [onStart].
 */
@Composable
internal fun JourneyPreview(journey: Journey, now: OffsetDateTime, onStart: () -> Unit, modifier: Modifier = Modifier) {
    JourneyDetail(
        journey = journey,
        fromName = journey.legs.first().from.plainStopName(),
        toName = journey.legs.last().to.plainStopName(),
        now = now,
        routeSaved = false,
        onToggleRoute = {},
        stopsFailed = true,
        onLoadStops = {},
        onStopAction = { _, _ -> },
        onStartTrip = onStart,
        onRememberPattern = {},
        onHandle = {},
        offers = emptyMap(),
        onAdapt = { _, _ -> },
        adapted = false,
        onUndo = {},
        onCourse = {},
        onFocusWalk = {},
        onDrag = {},
        onDragEnd = {},
        handle = false,
        // A preview cannot take another way (#243): no "Andere Wege" at its changes.
        otherWays = false,
        modifier = modifier,
    )
}

@Composable
private fun JourneyDetail(
    journey: Journey,
    fromName: String,
    toName: String,
    now: OffsetDateTime,
    routeSaved: Boolean,
    onToggleRoute: () -> Unit,
    stopsFailed: Boolean,
    onLoadStops: () -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
    onStartTrip: () -> Unit,
    onRememberPattern: () -> Unit,
    onHandle: () -> Unit,
    offers: Map<String, Alternatives>,
    onAdapt: (Int, Journey) -> Unit,
    adapted: Boolean,
    onUndo: () -> Unit,
    onCourse: (Leg) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    handle: Boolean = true,
    otherWays: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AllocProbe.begin(AllocProbe.DETAIL_COMPOSE)
    val scroll = rememberScrollState()
    // Notices about lifts are listed quietly, without the sign, unless the traveller travels step-free.
    val stepFree = rememberStepFree()
    // Another journey is read from its top.
    LaunchedEffect(journey.id) { if (scroll.value != 0) scroll.animateScrollTo(0) }
    // The ride that shows all its stops, by its place among the legs; another journey starts folded.
    var openRide by remember(journey.id) { mutableStateOf<Int?>(null) }
    val ratingVersion = ratingSettingsShown.intValue
    val changes = remember(journey, ratingVersion) { journeyChanges(journey) }
    // The time of every change, by the index of the ride it leads to: the same numbers as the
    // choice of connections, the trip companion and the watch show (see changeTime).
    val changeTimes = remember(journey, ratingVersion) { journeyChangesOf(journey).associate { it.nextIndex to it.time } }
    // Where to board for a change needs the stop before it, which the search leaves out: a
    // journey with a change between two trains gets its stops once when it is opened, as long
    // as boarding hints are on.
    LaunchedEffect(journey.id) {
        val needsStops = boardingHintsOn.value && changes.any { change ->
            change.arriving.stopovers.isEmpty() &&
                (changeQuery(change.arriving, change.next) != null || needsStopsForGeometry(change.arriving, change.next))
        }
        if (needsStops && !stopsFailed) onLoadStops()
    }
    val handleLabel = stringResource(R.string.sheet_toggle)
    val context = LocalContext.current
    // The share sheet of this journey (see ShareSheet); another journey starts with it closed.
    var shareOpen by remember(journey.id) { mutableStateOf(false) }
    // Planned trips, for "Fahrt planen" / "Planung aufheben" in the menu.
    val plannedTrips by PlannedTrips.trips.collectAsState()
    GlassSurface(modifier = modifier.then(rememberFirstFrameTrace("NS journey page first frame")), shape = RoundedCornerShape(32.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The head of the card stays in place: pulling it up or down moves the border to the overview.
            // On a page of its own the card has no handle and does not move.
            val dragState = rememberDraggableState { delta -> onDrag(delta) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        enabled = handle,
                        onDragStopped = { velocity -> onDragEnd(velocity) },
                    )
                    .padding(start = 16.dp, end = 16.dp, top = if (handle) 0.dp else 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Handle: a tap puts the card away or brings it back.
                // It is as tall as the card is when it is put away.
                if (handle) Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = handleLabel }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onHandle)
                        .size(width = 96.dp, height = DETAIL_HANDLE - 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppColors.Rail),
                    )
                }
                // First line: the whole time range, never cut, with the minutes until the
                // departure before it where the width leaves room for both. Second line: duration
                // and changes, then the share button, the menu of the quiet actions (save, calendar) and the
                // start at the far right, under the thumb.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillBeforeText(
                        pill = {
                            MinutesPill(
                                minutes = minutesBetween(now, journey.departure),
                                live = true,
                                cancelled = journey.cancelled,
                                at = journey.departure,
                            )
                        },
                        text = {
                            Text(
                                text = journeySpanText(journey, R.string.time_span, AppColors.Ink),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                lineHeight = 26.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        gap = 10.dp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.duration_and_transfers,
                                durationLabel(journey.durationMinutes),
                                journey.transfers,
                            ),
                            color = AppColors.Muted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        // Sharing in plain sight, the one way in: the share sheet with its choices.
                        RoundButton(
                            glyph = Glyph.Share,
                            description = stringResource(R.string.share_chooser),
                            onClick = { shareOpen = true },
                            modifier = appearModifier(1),
                            size = 40.dp,
                            solid = true,
                        )
                        if (shareOpen) {
                            ShareSheet(
                                journey = journey,
                                fromName = fromName,
                                toName = toName,
                                now = now,
                                following = false,
                                onDismiss = { shareOpen = false },
                                onStartTrip = onStartTrip,
                            )
                        }
                        JourneyMenu(
                            routeSaved = routeSaved,
                            onToggleRoute = onToggleRoute,
                            onCalendar = { addJourneyToCalendar(context, journey, fromName, toName) },
                            // A journey on foot only has no lines to remember.
                            onRememberPattern = if (journey.rides.isEmpty()) null else onRememberPattern,
                            // Only a journey that has not left yet can be planned.
                            onPlanTrip = if (journey.departure.isAfter(now)) ({ togglePlannedTrip(context, journey, fromName, toName) }) else null,
                            planned = plannedTrips.any { it.journey.id == journey.id },
                        )
                        StartPill(onClick = onStartTrip)
                    }
                }
            }
            // The body: the Live-Fahrt look (#129, default) or the classic one, as "Verbindungsdetails" says.
            DetailStyleSetting.install(context)
            if (DetailStyleSetting.style == DetailStyle.Timeline) {
                TimelineJourneyBody(
                    journey = journey,
                    stopsFailed = stopsFailed,
                    onLoadStops = onLoadStops,
                    onStopAction = onStopAction,
                    offers = offers,
                    onAdapt = onAdapt,
                    onCourse = onCourse,
                    onFocusWalk = onFocusWalk,
                    top = {
                        if (adapted) AdaptedBanner(onUndo)
                        WarningSummary(alertingWarnings(journey.warnings, stepFree))
                        QuietNotices(quietWarnings(journey.warnings, stepFree))
                        FareZoneNote(journey)
                        JourneyLiftWarning(journey)
                    },
                    otherWays = otherWays,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadeEdges(scroll, horizontal = false)
                    .verticalScroll(scroll)
                    // Generous room below, so the last line scrolls clear of the rounded edge.
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A journey with a swapped connection says so, and offers the way back.
                if (adapted) AdaptedBanner(onUndo)
                WarningSummary(alertingWarnings(journey.warnings, stepFree))
                QuietNotices(quietWarnings(journey.warnings, stepFree))
                FareZoneNote(journey)
                JourneyLiftWarning(journey)
                val legs = journey.legs
                // The stay at a place on the way stands between the legs before and after it.
                val stay = journey.via?.takeIf { it.minutes > 0 }
                val stayAt = stay?.afterLegs
                // "Nur nächste ausgeklappt" (#202): the maps at the first leg that has one start unfolded.
                val firstMapLeg = remember(journey, stayAt, OsmStations.loaded) { classicFirstMapLeg(legs, stayAt) }
                legs.forEachIndexed { index, leg ->
                    if (stay != null && stayAt == index && index > 0) {
                        BetweenRow(
                            stringResource(R.string.via_stay_row, stay.minutes, stay.place.name.plainStopName()),
                            dots = AppColors.Blue,
                            ink = AppColors.Ink,
                        )
                    }
                    CompositionLocalProvider(LocalWalkMapNext provides (index == firstMapLeg)) {
                    when {
                        !leg.walking -> {
                            // A bus or tram from a stop of many stop positions: a small map of the stop
                            // with the position to go to, from where the ride before ended (or from the
                            // stop's own point at the start). A walk before it shows its own map.
                            val previous = legs.getOrNull(index - 1)
                            val positionMap = hasBoardingPositionMap(leg) && when {
                                previous == null -> true
                                previous.walking -> false
                                else -> stayAt != index && !hasSpotWalkMap(previous, leg) && !OsmStations.samePlatform(previous, leg)
                            }
                            if (positionMap) {
                                val arrivedAt = previous?.let { it.path.lastOrNull() ?: it.toPlace?.position }
                                FoldedMap(indent = 84.dp) { BoardingPositionMap(leg, arrivedAt, onFocusWalk) }
                            }
                            // Where to board this ride for a short change to the next one, said where it is boarded.
                            val following = rideAfter(journey, leg)
                            RideRow(
                                boardingHint = if (following != null) {
                                    @Composable { CarriageHint(arriving = leg, next = following) }
                                } else if (leg === journey.rides.lastOrNull() && journey.legs.last().walking) {
                                    // The last ride to a place: where to board for the shortest way out.
                                    @Composable { DestinationHint(journey = journey, ride = leg) }
                                } else {
                                    null
                                },
                                leg = leg,
                                open = openRide == index,
                                stopsFailed = stopsFailed,
                                onToggle = {
                                    val opening = openRide != index
                                    openRide = if (opening) index else null
                                    // The search leaves the stops on the way out; they are fetched when a ride is unfolded.
                                    if (opening && leg.stopovers.isEmpty()) onLoadStops()
                                },
                                onStopAction = onStopAction,
                                onCourse = if (leg.tripId == null) null else ({ onCourse(leg) }),
                            )
                            // One vehicle that changes its line on the way: no change, only a note.
                            leg.throughRuns.lastOrNull { it.lineChanges }?.let { run ->
                                Text(
                                    text = stringResource(
                                        R.string.stay_seated_continues_as,
                                        rapidLineName(run.line, leg.product) ?: run.line.orEmpty(),
                                        run.atName.plainStopName(),
                                    ),
                                    color = AppColors.Muted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 84.dp, top = 2.dp, bottom = 2.dp),
                                )
                            }
                            // Two rides in a row: the time between them is the change.
                            val next = legs.getOrNull(index + 1)
                            if (next != null && !next.walking && stayAt != index + 1) {
                                TransferRow(changeTimes[index + 1] ?: changeTime(leg, next))
                                // At the change itself: the same platform; where to board was said above.
                                ChangeHint(arriving = leg, next = next, modifier = Modifier.padding(start = 84.dp))
                                // Two stop positions of buses or trams apart: both and the way between them, folded at first.
                                if (hasSpotWalkMap(leg, next)) {
                                    FoldedMap(indent = 84.dp) { SpotWalkMap(leg, next, onFocusWalk) }
                                } else if (!hasBoardingPositionMap(next) && remember(leg, next) { hasChangeMap(leg, next) }) {
                                    // Any other change to another place, as from a stop in the street to a platform.
                                    FoldedMap(indent = 84.dp) { ChangeWalkMap(leg, next, onFocusWalk) }
                                }
                                TransferChoice(journey.id, changes.firstOrNull { it.keepLegs == index + 1 }, offers, onAdapt)
                                AltChangeOffers(journey, index + 1, onAdapt)
                            }
                        }

                        else -> {
                            val minutes = minutesBetween(leg.departure, leg.arrival)
                            val betweenRides = index > 0 && index < legs.lastIndex
                            if (betweenRides) {
                                // A walk between two rides: the whole change time, from the arrival
                                // to the departure by the live times, never stretched to the walk.
                                val before = legs[index - 1]
                                val after = legs[index + 1]
                                TransferRow(
                                    if (!before.walking && !after.walking) {
                                        changeTimes[index + 1] ?: changeTime(before, after, minutes)
                                    } else {
                                        val change = changeGapMinutes(before, after)
                                        ChangeTime(
                                            change,
                                            neededMinutes(ChangeKind.Walk, minutes),
                                            transferTightness(change, ChangeKind.Walk, walkMinutes = minutes),
                                            minutes,
                                        )
                                    },
                                )
                                // At a walk between two stations: the same platform, where the numbers say so.
                                if (!before.walking && !after.walking) {
                                    ChangeHint(arriving = before, next = after, walkMinutes = minutes, modifier = Modifier.padding(start = 84.dp))
                                }
                                // The walk of the change as a small map, folded at first; where the walk has
                                // none of its own (no minutes, no way) but leads elsewhere, as to another stop
                                // position of the same stop, the map of the change (see changeMapKind).
                                val mapKind = remember(before, after, leg, minutes) { changeWalkMapKind(before, after, leg, minutes) }
                                if (mapKind != ChangeMapKind.None) {
                                    FoldedMap(indent = 84.dp) { ChangeMapOf(mapKind, before, after, leg, onFocusWalk) }
                                }
                                TransferChoice(journey.id, changes.firstOrNull { it.keepLegs == index }, offers, onAdapt)
                                AltChangeOffers(journey, index + 1, onAdapt)
                            } else if (minutes > 0) {
                                if (leg.cycling) {
                                    // The bike combo: by bike to the first stop or from the last one, with its length.
                                    val km = leg.walkMeters?.let { String.format(Locale.getDefault(), "%.1f", it / 1000.0) }
                                    BetweenRow(
                                        stringResource(R.string.cycle_to, minutes, leg.to.plainStopName()) +
                                            (km?.let { " · " + stringResource(R.string.cycle_distance, it) } ?: ""),
                                    )
                                } else {
                                    BetweenRow(stringResource(R.string.walk_to, shownWalkMinutes(leg), leg.to.plainStopName()))
                                }
                                // The walk to the first stop or to the destination as a small map, folded at first.
                                if (hasWalkMap(leg)) {
                                    FoldedMap(indent = 84.dp) { WalkMap(leg, onFocusWalk, walkEndsAtDestination(index, legs.size, leg.walking)) }
                                }
                            }
                        }
                    }
                    }
                }
                // What the colours of the times mean and where the data comes from (#277b).
                TimesFooter(journey)
            }
        }
    }
    AllocProbe.end(AllocProbe.DETAIL_COMPOSE)
}

/** "Verbindung angepasst" with the way back ("Rückgängig"), above the body of a journey whose connection was swapped. */
@Composable
private fun AdaptedBanner(onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.BlueContainer)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.adapted_note),
            color = AppColors.OnBlueContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(
            text = stringResource(R.string.undo),
            onClick = onUndo,
            shape = RoundedCornerShape(14.dp),
            minHeight = 40.dp,
        )
    }
}

/**
 * Works out, off the main thread, what the page of [journey] asks for first and what is kept
 * once worked out: its rated changes with their times (see journeyChangesOf) and where to board
 * for each of them (see changePositionOf). The page then finds them ready in its first frame.
 */
private fun warmJourneyPage(journey: Journey) {
    for (change in journeyChangesOf(journey)) {
        change.time
        changePositionOf(change.arriving, change.next)
    }
}

// How long a tap on a column waits for warmJourneyPage at most before the page opens anyway.
private const val PAGE_WARM_MILLIS = 60L

// The page of a journey grows out of its column in this time, and shrinks back into it in this.
private const val PAGE_GROW_MILLIS = 420
private const val PAGE_SHRINK_MILLIS = 360

// The emphasized easing of Material: quick to leave, long and soft to arrive.
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Lets the page of a journey grow out of the column it was opened from ([origin], in the box
 * the page fills, as it lay when the column was tapped)
 * when it comes in, and shrink back into it when it goes: a container transform. The page is
 * laid out once at its full size and only drawn moved, scaled, faded and rounded (a graphics
 * layer), so no frame of the way lays anything out again. Material's emphasized easing over
 * PAGE_GROW_MILLIS (PAGE_SHRINK_MILLIS back) instead of a spring: the page arrives softly and
 * does not overshoot its own size. Without an origin or animations it is left alone.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun Modifier.growFromColumn(scope: AnimatedVisibilityScope, origin: Rect?): Modifier {
    if (origin == null || !rememberAnimationsOn()) return this
    val grow = scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(PAGE_GROW_MILLIS, easing = EmphasizedEasing)
            } else {
                tween(PAGE_SHRINK_MILLIS, easing = EmphasizedEasing)
            }
        },
        label = "growFromColumn",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    // The shapes of the corners by whole dp, made once each: the layer takes a new outline only
    // from another shape (one shape reading a changing radius would keep its first outline),
    // and a new RoundedCornerShape every frame is garbage.
    val shapes = remember { arrayOfNulls<Shape>(GROW_CORNER_SHAPES) }
    return this.graphicsLayer {
        // The progress is read first, always: the layer is worked out anew only when what the
        // block read changes. (It used to wait for the bounds of the page from
        // onGloballyPositioned first; those come after the first layer is made, so the block
        // read nothing, never ran again and the page stood there whole from its first frame.)
        val progress = grow.value
        if (progress >= 1f) return@graphicsLayer
        // Only on the way out of the column (a real open) and back into it (closed without a
        // gesture): a page that stays after a back gesture was called off is whole, it never
        // grows out of the column again.
        val from = scope.transition.currentState
        val to = scope.transition.targetState
        // From PreEnter whatever the target: an open cut short shrinks back instead of standing whole.
        val opening = from == EnterExitState.PreEnter
        val closing = from == EnterExitState.Visible && to == EnterExitState.PostExit
        if (!opening && !closing) return@graphicsLayer
        // The page fills the box the origin is measured in: its size is all it needs.
        val page = size
        if (page.width <= 0f || page.height <= 0f) {
            alpha = 0f
            return@graphicsLayer
        }
        val rest = 1f - progress
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = (origin.width / page.width) * rest + progress
        scaleY = (origin.height / page.height) * rest + progress
        translationX = origin.left * rest
        translationY = origin.top * rest
        // The content comes in over the first third of the way, as the column turns into the page.
        alpha = (progress / 0.35f).coerceIn(0f, 1f)
        // From the corners of the column to those of the page, as they show on the screen
        // (the layer scales them with the page).
        val shown = (20f + 12f * progress) / kotlin.math.sqrt(scaleX * scaleY).coerceAtLeast(0.05f)
        val radius = kotlin.math.round(shown).toInt().coerceIn(0, GROW_CORNER_SHAPES - 1)
        shape = shapes[radius] ?: RoundedCornerShape(radius.dp).also { shapes[radius] = it }
        clip = true
    }
}

// The most corner radii (dp) growFromColumn keeps a shape for; larger ones are drawn this large.
private const val GROW_CORNER_SHAPES = 257

/** The main action of the detail card as a compact blue pill: follow this journey. */
@Composable
private fun StartPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Blue)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GlyphIcon(Glyph.Play, color = AppColors.OnBlue, size = 16.dp)
        Text(
            text = stringResource(R.string.trip_start),
            color = AppColors.OnBlue,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/** The quiet actions of the detail card behind a small round menu button (sharing has its own button). */
@Composable
private fun JourneyMenu(
    routeSaved: Boolean,
    onToggleRoute: () -> Unit,
    onCalendar: () -> Unit,
    onRememberPattern: (() -> Unit)? = null,
    onPlanTrip: (() -> Unit)? = null,
    planned: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(
            glyph = Glyph.More,
            description = stringResource(R.string.more_actions),
            onClick = { open = true },
            size = 40.dp,
            solid = true,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val entries = listOfNotNull(
                Triple(
                    if (routeSaved) Glyph.StarFilled else Glyph.Star,
                    stringResource(if (routeSaved) R.string.action_route_saved else R.string.action_route_save),
                    onToggleRoute,
                ),
                onRememberPattern?.let { Triple(Glyph.Route, stringResource(R.string.pattern_remember), it) },
                Triple(Glyph.Calendar, stringResource(R.string.menu_calendar), onCalendar),
                // A reminder before leaving, and the companion starts by itself (see PlannedTripAlarms).
                onPlanTrip?.let { Triple(Glyph.Recent, stringResource(if (planned) R.string.plan_trip_cancel else R.string.plan_trip), it) },
            )
            entries.forEach { (glyph, label, action) ->
                DropdownMenuItem(
                    text = { Text(text = label) },
                    leadingIcon = { GlyphIcon(glyph = glyph, size = 20.dp) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

/**
 * The connecting departures to choose from at one change of a journey, once they are
 * loaded: tighter and more relaxed ones around the chosen one. Picking another one swaps
 * the rest of the journey for it.
 */
/**
 * The other stops where the very same rides can be changed, under the change before the leg
 * [nextLeg] (see Journey.altChanges): "Change also possible at … · 3 min walk · normal"; a tap
 * puts that variant in place of the journey (see switchToAltChange).
 */
@Composable
internal fun AltChangeOffers(journey: Journey, nextLeg: Int, onAdapt: (Int, Journey) -> Unit) {
    if (journey.altChanges.isEmpty()) return
    val change = journey.legs.take(nextLeg).count { !it.walking } - 1
    for (alt in journey.altChanges) {
        if (alt.changeIndex != change) continue
        val rating = stringResource(tightnessName(alt.rating))
        val stop = alt.at.plainStopName()
        Text(
            text = if (alt.walkMinutes > 0) {
                stringResource(R.string.alt_change_offer, stop, alt.walkMinutes, rating)
            } else {
                stringResource(R.string.alt_change_offer_no_walk, stop, rating)
            },
            color = AppColors.Blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 76.dp, top = 2.dp, bottom = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { onAdapt(ALT_CHANGE_SWITCH, switchToAltChange(journey, alt)) }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/**
 * The first leg of [legs] at which the classic detail draws a small map (#202, "Nur nächste
 * ausgeklappt"), by the same rules as its rows: the stop position before a ride, the map of a
 * change after a ride, the map of a walk between rides or at the start or end. [stayAt] is the
 * leg after a stay at a place on the way. Null for none.
 */
private fun classicFirstMapLeg(legs: List<Leg>, stayAt: Int?): Int? {
    legs.forEachIndexed { index, leg ->
        if (!leg.walking) {
            val previous = legs.getOrNull(index - 1)
            val positionMap = hasBoardingPositionMap(leg) && when {
                previous == null -> true
                previous.walking -> false
                else -> stayAt != index && !hasSpotWalkMap(previous, leg) && !OsmStations.samePlatform(previous, leg)
            }
            if (positionMap) return index
            val next = legs.getOrNull(index + 1)
            if (next != null && !next.walking && stayAt != index + 1 &&
                (hasSpotWalkMap(leg, next) || (!hasBoardingPositionMap(next) && hasChangeMap(leg, next)))
            ) {
                return index
            }
        } else {
            val minutes = minutesBetween(leg.departure, leg.arrival)
            if (index > 0 && index < legs.lastIndex) {
                if (changeWalkMapKind(legs[index - 1], legs[index + 1], leg, minutes) != ChangeMapKind.None) return index
            } else if (minutes > 0 && hasWalkMap(leg)) {
                return index
            }
        }
    }
    return null
}

@Composable
internal fun TransferChoice(
    journeyId: String,
    change: TripChange?,
    offers: Map<String, Alternatives>,
    onAdapt: (Int, Journey) -> Unit,
    // Other ways from the change in the same row (#243, detail page with both chips on).
    extras: List<ChoiceExtraTile> = emptyList(),
) {
    if (change == null) return
    // Too few to choose from: the other ways alone, in the same row (#257: never swapped for another one).
    val options = connectionOptions(change, offers[offerKey(journeyId, change.keepLegs)]).takeIf { it.size >= 2 }.orEmpty()
    if (options.isEmpty() && extras.isEmpty()) return
    // Keyed by the journey and its change: the choice of another journey comes in with its own
    // appearance, instead of taking over the place (and the finished appearance) of the one before.
    key(journeyId, change.keepLegs) {
        Column(modifier = appearModifier(0), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ConnectionChips(change = change, options = options, onSwitch = { option -> onAdapt(change.keepLegs, option) }, extras = extras)
        }
    }
}

/**
 * [text] in one line at its full width, with [pill] before it only where the room leaves space
 * for both; otherwise the pill is left out, so the text is never cut. Both are centred vertically.
 */
@Composable
private fun PillBeforeText(
    pill: @Composable () -> Unit,
    text: @Composable () -> Unit,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(pill, text), modifier = modifier) { (pillMeasurables, textMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val textPlaceable = textMeasurables.first().measure(loose.copy(maxWidth = Constraints.Infinity))
        val gapPx = gap.roundToPx()
        val room = constraints.maxWidth - textPlaceable.width - gapPx
        val pillPlaceable = if (room > 0) {
            pillMeasurables.firstOrNull()?.measure(loose.copy(maxWidth = room))?.takeIf { it.width <= room }
        } else {
            null
        }
        val height = maxOf(textPlaceable.height, pillPlaceable?.height ?: 0)
        val width = (pillPlaceable?.let { it.width + gapPx } ?: 0) + textPlaceable.width
        layout(width.coerceAtMost(constraints.maxWidth), height) {
            var x = 0
            if (pillPlaceable != null) {
                pillPlaceable.placeRelative(x, (height - pillPlaceable.height) / 2)
                x += pillPlaceable.width + gapPx
            }
            textPlaceable.placeRelative(x, (height - textPlaceable.height) / 2)
        }
    }
}

/**
 * A change between two rides: the minutes from the arrival to the departure (live times) and
 * how tight it is, in the colour of the wait it leaves (see waitTone; a long wait is a calm sand
 * and says so); "too late" where the next ride leaves before the arrival.
 */
@Composable
internal fun TransferRow(time: ChangeTime) {
    val rating = if (time.possible) toneLabel(time) else stringResource(R.string.choice_gone)
    val color = toneColor(time.tone)
    BetweenRow(text = stringResource(R.string.transfer_rated, time.minutes, rating), dots = color, ink = color)
}

/** A change or a walk between two rides: a dotted trail and one line of text. */
@Composable
private fun BetweenRow(text: String, dots: Color = AppColors.Rail, ink: Color = AppColors.Muted) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(modifier = Modifier.width(48.dp))
        // As wide as the rail of a ride, so the dots sit in line with it.
        Column(
            modifier = Modifier.width(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dots),
                )
            }
        }
        Text(text = text, color = ink, fontSize = 13.sp)
    }
}

/**
 * A time of a ride, as the delay setting says (see DelayDisplay): live times are green or red,
 * timetable times are black; "+2" below unless the coloured time stands alone.
 */
@Composable
internal fun LegTime(time: OffsetDateTime, planned: OffsetDateTime?, delayMinutes: Long, realtime: Boolean, estimated: Boolean = false) {
    val display = LocalDelayDisplay.current
    // One decision for every view (see shownTime, #227).
    val decided = shownTime(display, time, planned, realtime, delayMinutes)
    // A refreshed time or delay cross-fades instead of jumping.
    FadeSwap(
        target = LegTimeShown(
            (if (estimated) time else decided.clock).clock(),
            delayMinutes,
            realtime,
            estimated,
            struck = decided.struck?.clock(),
            tone = decided.tone,
            delay = decided.delay,
        ),
        contentAlignment = Alignment.TopEnd,
    ) { shown ->
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = shown.clock,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                // A time the app estimated is blue with "ca." and without a delay (#277): it is a guess.
                color = timeToneColor(if (shown.estimated) io.github.jnbp.nextstop.data.TimeTone.Estimated else shown.tone, AppColors.Ink),
            )
            if (shown.estimated) {
                EstimatedMark()
            } else if (shown.struck != null) {
                Text(
                    text = shown.struck,
                    color = AppColors.Muted,
                    textDecoration = TextDecoration.LineThrough,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            } else if (shown.delay != null) {
                Text(
                    text = stringResource(R.string.delay_short, shown.delay.toInt()),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = if (shown.delay > 0) AppColors.DelayRed else AppColors.Early,
                )
            }
        }
    }
}

/** What a [LegTime] shows, so a change of any of it cross-fades. */
private data class LegTimeShown(
    val clock: String,
    val delayMinutes: Long,
    val realtime: Boolean,
    val estimated: Boolean,
    // The timetable time struck through below (see DelayDisplay.StruckPlanned); null for none.
    val struck: String? = null,
    // The colour of the time and the "+2" below it, as shownTime decided.
    val tone: io.github.jnbp.nextstop.data.TimeTone = io.github.jnbp.nextstop.data.TimeTone.Scheduled,
    val delay: Long? = null,
)

/** The blue "ca." (#277) in front of or below a time the app estimated (see plausible), as in the course of a line. */
@Composable
internal fun EstimatedMark() {
    Text(
        text = stringResource(R.string.estimated_prefix),
        color = timeToneColor(io.github.jnbp.nextstop.data.TimeTone.Estimated, AppColors.Ink),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        maxLines = 1,
    )
}

/** The ring at an end of a ride on the rail of the timeline. */
@Composable
private fun RailNode(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .padding(4.dp)
            .clip(CircleShape)
            .background(AppColors.Card),
    )
}

/**
 * One ride: times on the left, a rail in the colour of the line with a ring at each end,
 * both stops on the right. A tap on the line unfolds all stops on the way; a long press,
 * or the link below the unfolded stops, shows the whole course of the line ([onCourse],
 * null when the ride has no handle for it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RideRow(
    leg: Leg,
    open: Boolean,
    stopsFailed: Boolean,
    onToggle: () -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
    onCourse: (() -> Unit)?,
    boardingHint: (@Composable () -> Unit)? = null,
) {
    val color = if (leg.cancelled) AppColors.Rail else lineColor(leg)
    var menuOpen by remember { mutableStateOf(false) }
    // The stops on the way and the arrival with plausible times: an implausible prognosis
    // shows as the app's estimate, grey with "ca." (see plausible).
    val shown = remember(leg) { leg.plausible() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            LegTime(leg.departure, leg.plannedDeparture, leg.departureDelayMinutes, leg.realtime)
            LegTime(shown.arrival, shown.plannedArrival, shown.arrivalDelayMinutes, shown.realtime, estimated = shown.arrivalEstimated)
        }
        Column(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailNode(color)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .weight(1f)
                    .background(color),
            )
            RailNode(color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            StopLine(
                name = rideStopName(leg, leg.from, leg.rideTown),
                note = platformLabel(leg.departurePlatform, leg.product),
                changed = platformMoved(leg.departurePlatform, leg.plannedDeparturePlatform),
                place = leg.fromPlace,
                onStopAction = onStopAction,
            )
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClickLabel = stringResource(R.string.stops_show),
                            onLongClickLabel = if (onCourse == null) null else stringResource(R.string.line_course),
                            onLongClick = if (onCourse == null) null else ({ menuOpen = true }),
                            onClick = onToggle,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // A rail replacement: one calm badge ("RE8" with a small "SEV"), no extra tag,
                    // so the destination stays readable (it may take a second line).
                    val replacement = leg.railReplacement
                    if (replacement != null) PlainReplacementBadge(leg, replacement, minWidth = 40.dp) else LineBadge(leg, minWidth = 40.dp)
                    val direction = leg.direction
                    if (leg.cancelled) {
                        Text(
                            text = stringResource(R.string.cancelled),
                            color = AppColors.DelayRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        // The whole name with the train number ("FEX 021857") where the badge leaves it out.
                        // An arrow before the destination saves room; read out as "towards …".
                        val towards = if (direction == null) "" else stringResource(R.string.direction_arrow, shownRideStop(leg, direction))
                        val spokenTowards = if (direction == null) "" else stringResource(R.string.direction, shownRideStop(leg, direction))
                        // Not for a replacement: its badge already names the line it stands in for.
                        val whole = if (replacement != null) null else fullLineName(leg.line)
                        val spoken = if (whole == null) spokenTowards else listOf(whole, spokenTowards).filter { it.isNotEmpty() }.joinToString(" · ")
                        Text(
                            text = if (whole == null) towards else listOf(whole, towards).filter { it.isNotEmpty() }.joinToString(" · "),
                            color = AppColors.Muted,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = if (replacement != null) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).semantics { contentDescription = spoken },
                        )
                    }
                    Chevron(open = open)
                }
                if (onCourse != null) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.line_course)) },
                            onClick = {
                                menuOpen = false
                                onCourse()
                            },
                        )
                    }
                }
            }
            boardingHint?.invoke()
            // Occupancy, bicycles, step-free and the sunny side, where known (see RideHints).
            RideHintsRow(leg)
            if (open) {
                RideStops(leg = shown, failed = stopsFailed, onStopAction = onStopAction)
                if (onCourse != null) {
                    Text(
                        text = stringResource(R.string.line_course),
                        color = AppColors.Blue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onCourse)
                            .padding(vertical = 6.dp),
                    )
                }
            }
            StopLine(
                name = rideStopName(leg, leg.to, leg.rideTown),
                note = platformLabel(leg.arrivalPlatform, leg.product),
                changed = platformMoved(leg.arrivalPlatform, leg.plannedArrivalPlatform),
                place = leg.toPlace,
                onStopAction = onStopAction,
            )
        }
    }
}

/** The stops of a ride between boarding and getting off, each with its time. */
@Composable
private fun RideStops(leg: Leg, failed: Boolean, onStopAction: (Stop, StopAction) -> Unit) {
    // Both ends already stand above and below this list.
    val between = leg.stopovers.drop(1).dropLast(1)
    // Loading, a note or the stops: a change between them cross-fades.
    val phase = when {
        leg.stopovers.isEmpty() && !failed -> STOPS_LOADING
        leg.stopovers.isEmpty() -> STOPS_FAILED
        between.isEmpty() -> STOPS_NONE
        else -> STOPS_SHOWN
    }
    FadeSwap(target = phase) { shownPhase ->
    when (shownPhase) {
        STOPS_LOADING -> LoadingRow(text = stringResource(R.string.stops_loading), compact = true)

        STOPS_FAILED -> Text(
            text = stringResource(R.string.stops_failed),
            color = AppColors.Muted,
            fontSize = 13.sp,
        )

        STOPS_NONE -> Text(
            text = stringResource(R.string.stops_none),
            color = AppColors.Muted,
            fontSize = 13.sp,
        )

        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Stops in another town than the ride's say theirs ("Kirchstr. · Zagelsdorf").
            val town = leg.rideTown
            // Never two different stops alike in the list, the ends included (#278).
            val shownNames = remember(leg) { shownRideStopNames(leg, leg.stopovers, town) }
            between.forEachIndexed { index, stop ->
                StopLine(
                    name = shownNames.getOrNull(index + 1) ?: rideStopName(leg, stop.name, town),
                    note = stop.time?.clock(),
                    place = stop.place,
                    onStopAction = onStopAction,
                    strong = false,
                    estimated = stop.estimated,
                )
            }
        }
    }
    }
}

// What the stops of a ride show (see RideStops).
private const val STOPS_LOADING = 0
private const val STOPS_FAILED = 1
private const val STOPS_NONE = 2
private const val STOPS_SHOWN = 3

/** True when [now] is another platform than the timetable's [planned] (both known). */
internal fun platformMoved(now: String?, planned: String?): Boolean =
    !now.isNullOrBlank() && !planned.isNullOrBlank() && !now.trim().equals(planned.trim(), ignoreCase = true)

/**
 * A stop name with a note at the right edge: the platform, or the time of a stop on the
 * way. A small ⋮ at its end (or, as a shortcut, a long press) opens the menu of what can be
 * done with the stop.
 */
@Composable
private fun StopLine(
    name: String,
    note: String?,
    place: Stop?,
    onStopAction: (Stop, StopAction) -> Unit,
    strong: Boolean = true,
    estimated: Boolean = false,
    changed: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.stop_menu)
    Box {
        Row(
            modifier = if (place == null) {
                Modifier
            } else {
                Modifier
                    .semantics {
                        onLongClick(label = menuLabel) {
                            menuOpen = true
                            true
                        }
                    }
                    .pointerInput(place.id) { detectTapGestures(onLongPress = { menuOpen = true }) }
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                color = if (strong) AppColors.Ink else AppColors.Muted,
                fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = if (strong) 15.sp else 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note != null) {
                // A time the app estimated: "ca." in front of it.
                if (estimated) EstimatedMark()
                // A platform other than the timetable's: marked like a delay.
                Text(
                    text = note,
                    color = when {
                        changed -> AppColors.AmberInk
                        estimated -> timeToneColor(io.github.jnbp.nextstop.data.TimeTone.Estimated, AppColors.Muted)
                        else -> AppColors.Muted
                    },
                    fontWeight = if (changed) FontWeight.ExtraBold else null,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = if (changed) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.AmberContainer)
                            .padding(horizontal = 5.dp)
                    } else {
                        Modifier
                    },
                )
            }
            // The menu opens at the dots, on the right (see StopMenu).
            if (place != null) {
                Box {
                    MoreDots(label = menuLabel, onClick = { menuOpen = true }, size = if (strong) 32.dp else 28.dp)
                    StopMenu(
                        open = menuOpen,
                        onDismiss = { menuOpen = false },
                        onAction = { action -> onStopAction(place, action) },
                        place = place,
                    )
                }
            }
        }
    }
}

/**
 * A row of small chips that say what a search leaves out: the kinds of transport in
 * [productsOff] switched off in the settings. A tap brings them back. Nothing shows while
 * nothing is left out.
 */
@Composable
fun SearchLimits(
    productsOff: Set<String>,
    onAllProducts: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 36.dp,
    tickets: List<TicketFilter> = emptyList(),
    onTicketOff: (TicketFilter) -> Unit = {},
) {
    if (productsOff.isEmpty() && tickets.isEmpty()) return
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fadeEdges(scroll, horizontal = true)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProductsOffChip(off = productsOff, onUseAll = onAllProducts, minHeight = minHeight)
        // Deutschlandticket, bicycle: also easy to forget while on.
        tickets.forEach { filter -> TicketFilterChip(filter = filter, onOff = { onTicketOff(filter) }, minHeight = minHeight) }
    }
}

/**
 * The line above the connections of a remembered connection: its name, a [note] on what the
 * search found (it is not running now, or another journey is faster) and a cross that shows
 * all journeys again.
 */
@Composable
private fun PatternBanner(title: String, note: String?, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.BlueContainer)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlyphIcon(Glyph.Route, color = AppColors.OnBlueContainer, size = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.OnBlueContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note != null) {
                Text(text = note, color = AppColors.OnBlueContainer, fontSize = 12.sp, lineHeight = 15.sp)
            }
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClear)
                .size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Close, color = AppColors.OnBlueContainer, size = 18.dp)
        }
    }
}
