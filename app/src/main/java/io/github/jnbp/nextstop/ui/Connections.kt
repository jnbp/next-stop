package io.github.jnbp.nextstop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.data.TicketFilter
import io.github.jnbp.nextstop.data.ticketFilters
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.railReplacement
import io.github.jnbp.nextstop.data.rideTown
import io.github.jnbp.nextstop.data.townedStopName
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.Tightness
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
import io.github.jnbp.nextstop.ui.theme.AppColors

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
private fun levelName(level: TransferComfort): String = stringResource(
    when (level) {
        TransferComfort.Relaxed -> R.string.comfort_relaxed
        TransferComfort.Normal -> R.string.comfort_normal
        TransferComfort.Risky -> R.string.comfort_risky
        TransferComfort.VeryRisky -> R.string.comfort_very_risky
    },
)

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
 * (Entspannt, Normal, Knapp, Sprint) with the four names centred below them. The choice is
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
    // How far the bar is filled, in segments: 1 for Entspannt up to 4 for Sprint. It sweeps.
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val detailShare = state.detailShare
    var shareJob by remember { mutableStateOf<Job?>(null) }
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
        BackHandler(enabled = detailPage) { state.detailPage = false }
        SplitOrStack(
            side = side,
            paneWidth = (maxWidth * 0.4f).coerceIn(260.dp, SIDE_PANEL_MAX),
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
                        Text(
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
                    Text(
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
            // The chips of the search in one row, and below it, across the whole width, the
            // choice how tight changes may be. The choice has its own row so a long time chip
            // ("Fr., 27. Nov., 11:26") can never squeeze it; the chips scroll sideways if they
            // need more room than there is. No caption above the pills: they name their levels
            // in the colours of the chart's tags, and only those who switched the choice on in
            // the settings see it at all; the caption is still read out to accessibility services.
            // The page of one journey leaves all of it out.
            if (!detailPage || side) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val chipScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fadeEdges(chipScroll, horizontal = true)
                        .horizontalScroll(chipScroll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The same order as on the map with the options.
                    TimeChip(choice = time, onClick = onTime, minHeight = 36.dp)
                    EveryMinuteChip(active = everyMinute, onToggle = onEveryMinute, minHeight = 36.dp)
                    // Kinds of transport, Deutschlandticket and bicycle; kept for the next searches.
                    if (searchOptions != null) SearchOptionsChip(options = searchOptions, onClick = onSearchOptions, minHeight = 36.dp)
                }
                // The tightest level shown; the next tighter one with hidden journeys shows how many it would add.
                // Without the setting "choose transfer times" the page shows normal and relaxed journeys and asks nothing.
                if (levelChoice) {
                    LevelSelector(
                        level = comfort,
                        onLevel = onComfort,
                        tighterCount = if (journeys.isNullOrEmpty()) 0 else tighterCount,
                        tighterLevel = tighterLevel,
                        onTighter = onShowFiltered,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Kinds of transport that are off: easy to forget, and otherwise it looks as if
                // the provider had found no feeder bus.
                SearchLimits(
                    productsOff = productsOff,
                    onAllProducts = onAllProducts,
                    tickets = searchOptions?.let { ticketFilters(it) }.orEmpty(),
                    onTicketOff = onTicketOff,
                )
            }

            // A remembered connection: its name, what the search found for it, and a way back to all journeys.
            if (patternTitle != null && (!detailPage || side)) {
                PatternBanner(title = patternTitle, note = patternNote, onClear = onClearPattern)
            }
            },
            rest = {
            when {
                loading -> GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    // The little train rides while the search runs.
                    LoadingRow(text = stringResource(R.string.searching_connections), modifier = Modifier.padding(20.dp))
                }

                error != null -> GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        color = AppColors.Muted,
                        modifier = Modifier.padding(20.dp),
                    )
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
                    // The connecting departures of the picked journey are asked for once it has
                    // stayed picked for a moment, so leafing through the chart costs no requests.
                    val loadOffers by rememberUpdatedState(onLoadOffers)
                    val picked by rememberUpdatedState(current)
                    LaunchedEffect(current.id) {
                        delay(OFFER_SETTLE_MILLIS)
                        loadOffers(picked, false)
                        while (true) {
                            delay(OFFER_REFRESH_MILLIS)
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
                                modifier = overviewModifier,
                            )
                        } else {
                            // The list starts with the next departure; earlier ones live in the chart.
                            // A picked journey stays in the list after it has left, so the pick is never lost.
                            // A search at a picked time shows all it found, whatever the clock says.
                            val upcoming = if (time != null) {
                                journeys
                            } else {
                                journeys.filter { !it.departure.isBefore(now) || it.id == current.id }
                            }
                            JourneyList(
                                journeys = upcoming,
                                selected = upcoming.indexOf(current),
                                levels = levels,
                                now = now,
                                onSelect = { index -> onPick(upcoming[index].id) },
                                scroll = state.listScroll,
                                modifier = overviewModifier,
                            )
                        }
                    }
                    if (pagewise) {
                        // The journey on its own page grows out of the row or column it was
                        // opened from, and shrinks back into it on the way back.
                        val pageOrigin = remember { arrayOfNulls<Rect>(1) }
                        AnimatedContent(
                            targetState = detailPage,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            transitionSpec = {
                                when {
                                    pageOrigin[0] == null -> fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                    targetState -> fadeIn(tween(120)) togetherWith fadeOut(tween(260, delayMillis = 120))
                                    // Closing, the page shrinks above the overview that is back underneath.
                                    else -> (fadeIn(tween(160)) togetherWith fadeOut(tween(220, delayMillis = 160)))
                                        .apply { targetContentZIndex = -1f }
                                }
                            },
                            label = "journeyPage",
                        ) { open ->
                            if (open) {
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
                                        .growFrom(this, pageOrigin[0]),
                                )
                            } else {
                                // The overview alone; a tap opens the journey on its own page.
                                overview(Modifier.fillMaxSize()) { id ->
                                    pageOrigin[0] = OpenOrigin.take()
                                    state.selectedId = id
                                    state.detailPage = true
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
                        val share = detailShare.floatValue.coerceIn(handleShare, fullShare)
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Above the card's largest readable size the overview fades and slides up, and at
                            // full height it is gone, with no strip of squeezed columns left; pulled down, it
                            // comes back the same way.
                            val overviewGone = 1f - share < OVERVIEW_GONE
                            val shown = ((fullShare - share) / (fullShare - DETAIL_MAX)).coerceIn(0f, 1f)
                            val slide = with(LocalDensity.current) { OVERVIEW_SLIDE.toPx() }
                            val overviewModifier = Modifier
                                .weight((1f - share).coerceAtLeast(OVERVIEW_GONE))
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = shown
                                    translationY = -(1f - shown) * slide
                                }
                            if (!overviewGone) overview(overviewModifier, select)
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
                                    .weight(share)
                                    .fillMaxWidth()
                                    .nestedScroll(connection),
                            )
                        }
                    }
                }
            }
            },
        )
    }
}

/**
 * The parts of a page: on top of each other ([top] above [rest]), or with [side] next to each
 * other, [top] in a scrolling panel [paneWidth] wide and [rest] with the whole height beside it.
 */
@Composable
private fun SplitOrStack(
    side: Boolean,
    paneWidth: Dp,
    modifier: Modifier = Modifier,
    top: @Composable ColumnScope.() -> Unit,
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
                content = top,
            )
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
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            top()
            rest()
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

// Between two rides whose change shows a pill there is at least this much air, so the pill
// (OVERLAP_TAG_HEIGHT high) never touches either block; the blocks after it are pushed down.
private val PILL_GAP = 22.dp

// The name and corners of a ride block grow from the small form at MIN_BLOCK to the full form
// at this height, smoothly, so zooming the chart morphs the blocks instead of switching them.
private val FULL_BLOCK = 32.dp

/** Room on both sides of the name of a line in its block of the chart. */
private val BLOCK_LABEL_PADDING = 2.dp

/** The smallest size (sp) the name of a line gets in a block before it is shortened. */
private const val BLOCK_LABEL_MIN_SIZE = 8f

/**
 * The name of a line for a block of the chart [room] wide, and its size in sp: [full] at
 * [maxSize] (the size of every block, as for "S1" or "U9") where it fits, else smaller until it
 * fits, down to BLOCK_LABEL_MIN_SIZE; below that the [compact] name ("ICE" for "ICE 29"), sized
 * the same way. Never larger than [maxSize].
 */
@Composable
private fun fitBlockLabel(full: String, compact: String, maxSize: Float, room: Dp): Pair<String, Float> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalTextStyle.current.merge(TextStyle(fontWeight = FontWeight.ExtraBold))
    return remember(full, compact, maxSize, room, density, style) {
        val roomPixels = with(density) { room.toPx() }.coerceAtLeast(1f)
        val fitting = { text: String ->
            val width = measurer.measure(text, style.copy(fontSize = maxSize.sp), softWrap = false, maxLines = 1)
                .size.width.toFloat()
            if (width <= roomPixels) maxSize else maxSize * roomPixels / width * 0.98f
        }
        val fullSize = fitting(full)
        if (fullSize >= BLOCK_LABEL_MIN_SIZE || compact == full) {
            full to fullSize.coerceAtLeast(BLOCK_LABEL_MIN_SIZE)
        } else {
            compact to fitting(compact).coerceAtLeast(BLOCK_LABEL_MIN_SIZE)
        }
    }
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
        val pills = pillChanges(journey)
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
 * The legs of [journey] (by index) whose change before them shows a pill in the chart: risky
 * and very risky changes (see journeyChangesOf), none in a cancelled journey.
 */
private fun pillChanges(journey: Journey): Set<Int> =
    if (journey.cancelled) {
        emptySet()
    } else {
        journeyChangesOf(journey).filter { it.tightness.ordinal >= Tightness.Risky.ordinal }.map { it.nextIndex }.toSet()
    }

/** One stretch of a journey column: a ride, or a walk before the first or after the last ride. */
private class ColumnPart(val leg: Leg, val top: Dp, val height: Dp)

/**
 * Where the stretches of a journey lie in its column. [starts] and [ends] are the times
 * of its legs in minutes of the chart. Each stretch starts at its departure, but never
 * inside the one before it: a very short ride keeps a readable height and pushes what
 * follows down a little, so blocks can not overlap at any scale. A pushed stretch still
 * ends at its arrival where it is long enough to give up the room, so a long ride after
 * short ones is a little compressed and the column does not run past its arrival.
 * A walk between two rides is part of the change and has no stretch of its own. A ride in
 * [pillsBefore] (a leg index whose change shows a pill) starts at least [PILL_GAP] below the
 * ride before it, so the pill has room of its own.
 */
private fun columnParts(
    legs: List<Leg>,
    starts: FloatArray,
    ends: FloatArray,
    windowStart: Float,
    perMinute: Dp,
    pillsBefore: Set<Int> = emptySet(),
): List<ColumnPart> {
    val firstRide = legs.indexOfFirst { !it.walking }
    val lastRide = legs.indexOfLast { !it.walking }
    var floor = 0.dp
    var lastRideBottom: Dp? = null
    return legs.mapIndexedNotNull { index, leg ->
        val minutes = ends[index] - starts[index]
        val betweenRides = firstRide >= 0 && index > firstRide && index < lastRide
        if (leg.walking && (betweenRides || minutes < 1f)) {
            null
        } else {
            val naturalTop = CHART_TOP + perMinute * (starts[index] - windowStart)
            val naturalBottom = naturalTop + perMinute * minutes.coerceAtLeast(1f)
            val pillFloor = lastRideBottom?.takeIf { !leg.walking && index in pillsBefore }?.plus(PILL_GAP) ?: 0.dp
            val top = maxOf(naturalTop, floor, pillFloor)
            val height = maxOf(naturalBottom - top, if (leg.walking) MIN_WALK else MIN_BLOCK)
            floor = top + height + (if (leg.walking) BLOCK_GAP else RIDE_GAP)
            if (!leg.walking) lastRideBottom = top + height
            ColumnPart(leg, top, height)
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
): Dp {
    val legs = journey.legs
    val parts = columnParts(
        legs,
        FloatArray(legs.size) { minuteOf(legs[it].departure) },
        FloatArray(legs.size) { minuteOf(legs[it].arrival) },
        windowStart,
        perMinute,
        pillChanges(journey),
    )
    val natural = CHART_TOP + perMinute * (minuteOf(journey.arrival) - windowStart)
    return maxOf(parts.lastOrNull()?.let { it.top + it.height } ?: natural, natural)
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
): Dp {
    val span = (windowEnd - windowStart).coerceAtLeast(10f)
    val room = (height - CHART_TOP - CHART_BOTTOM).coerceAtLeast(80.dp)
    fun fits(scale: Dp): Boolean {
        val deepest = journeys.maxOfOrNull { columnBottom(it, minuteOf, windowStart, scale) } ?: 0.dp
        return maxOf(deepest, CHART_TOP + scale * span) + CHART_BOTTOM <= maxOf(height, room + CHART_TOP + CHART_BOTTOM)
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
    modifier: Modifier = Modifier,
) {
    val loadEarlier by rememberUpdatedState(onLoadEarlier)
    val loadLater by rememberUpdatedState(onLoadLater)
    val glide = rememberAnimationsOn()

    // Minutes are counted from a fixed full hour, so axis labels fall on round times.
    val base = remember { journeys.first().departure.withMinute(0).withSecond(0).withNano(0) }
    val minuteOf: (OffsetDateTime) -> Float = { time -> Duration.between(base, time).seconds / 60f }

    // Every column that shows decides the time window, also one that only peeks in at an edge:
    // each of them is seen whole, from its first departure (a walk included) to its arrival.
    val visibleKeys by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            info.visibleItemsInfo
                .filter { item ->
                    item.offset + item.size > info.viewportStartOffset + PEEK_PIXELS &&
                        item.offset < info.viewportEndOffset - PEEK_PIXELS
                }
                .map { it.key }
        }
    }
    val inView = journeys.filter { it.id in visibleKeys }.ifEmpty { journeys.take(4) }

    val firstDeparture = inView.minOf { minuteOf(it.departure) }
    val nowMinute = minuteOf(now)
    // Start at "now" when the first departure is close, so the wait is visible.
    // A search at a picked time starts at its first journey and never slides to "now".
    val nowIsClose = !timed && nowMinute < firstDeparture && firstDeparture - nowMinute <= 45f
    val targetStart = if (nowIsClose) nowMinute else firstDeparture
    val targetEnd = maxOf(inView.maxOf { minuteOf(it.arrival) }, targetStart + 20f)
    // A spring carries the window along: a new target while it moves keeps its speed, so
    // scrolling sideways glides instead of jerking from one window to the next.
    val windowSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
    val windowStart by animateFloatAsState(targetStart, windowSpring, label = "windowStart")
    val windowEnd by animateFloatAsState(targetEnd, windowSpring, label = "windowEnd")
    val span = (windowEnd - windowStart).coerceAtLeast(10f)

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
        if (listState.isScrollInProgress) return@LaunchedEffect
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

    // Reaching an end loads more. Earlier journeys load once up front and then only
    // while the user scrolls, so the chart never walks into the past by itself.
    // Keys, not positions, are compared: positions are stale right after a page was added.
    LaunchedEffect(journeys.size, listState) {
        val firstId = journeys.first().id
        val lastIds = journeys.takeLast(2).map { it.id }
        snapshotFlow {
            val items = listState.layoutInfo.visibleItemsInfo
            Triple(items.firstOrNull()?.key, items.lastOrNull()?.key, listState.isScrollInProgress)
        }.collect { (firstKey, lastKey, scrolling) ->
            if (lastIds.any { it == lastKey }) loadLater()
            if (firstKey == firstId && (scrolling || !earlierPrimed.value)) {
                earlierPrimed.value = true
                loadEarlier()
            }
        }
    }

    val density = LocalDensity.current
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(32.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            // The scale at which every column in view fits the card whole, with the room for its
            // times above and below, unless the journeys in view need more to show their
            // stretches at all (see neededScale). Then the chart scrolls up and down instead.
            val targetScale = remember(inView, targetStart, targetEnd, maxHeight) {
                maxOf(fittingScale(inView, minuteOf, targetStart, targetEnd, maxHeight), neededScale(inView))
            }
            val perMinute by animateDpAsState(
                targetScale,
                spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                label = "chartScale",
            )

            // The chart is as tall as its window at this scale, and as the column that was
            // pushed down the furthest. A hair more than the card is no reason to scroll.
            val deepest = inView.maxOf { columnBottom(it, minuteOf, windowStart, perMinute) }
            val wanted = maxOf(CHART_TOP + perMinute * span, deepest) + CHART_BOTTOM
            val chartHeight = if (wanted > maxHeight + 4.dp) wanted else maxHeight
            val scrolls = chartHeight > maxHeight

            // Where the chart is taller than its card, the picked journey is scrolled fully into
            // view, its times included: when it is picked, and when the window or the scale
            // changed after a sideways scroll. Not while a finger is on the chart.
            val picked = journeys.firstOrNull { it.id == selectedId }?.takeIf { it.id in visibleKeys }
            val sideways = listState.isScrollInProgress
            LaunchedEffect(selectedId, scrolls, targetScale, targetStart, sideways) {
                if (picked == null || !scrolls || sideways || upDown.isScrollInProgress) return@LaunchedEffect
                // The new window and scale have to be reached first, or the place is off.
                delay(SETTLE_SCROLL_MILLIS)
                with(density) {
                    // The top of its column is the top of its departure time, which lies above the first block.
                    val top = (targetScale * (minuteOf(picked.departure) - targetStart)).roundToPx()
                    val bottom = (columnBottom(picked, minuteOf, targetStart, targetScale) + CHART_BOTTOM).roundToPx()
                    val view = maxHeight.roundToPx()
                    val from = upDown.value
                    val target = when {
                        bottom - top > view || top < from -> top
                        bottom > from + view -> bottom - view
                        else -> return@LaunchedEffect
                    }
                    upDown.animateScrollTo(target.coerceAtLeast(0))
                }
            }

            // Axis labels every 10, 20, 30 or 60 minutes, whatever still has room.
            val step = when {
                perMinute * 10 >= 26.dp -> 10
                perMinute * 20 >= 26.dp -> 20
                perMinute * 30 >= 26.dp -> 30
                else -> 60
            }
            val firstTick = ceil(windowStart / step).toInt() * step
            val ticks = (firstTick..windowEnd.toInt() step step).toList()
            val nowVisible = nowMinute >= windowStart - 0.5f && nowMinute <= windowEnd
            val nowY = CHART_TOP + perMinute * (nowMinute - windowStart)

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadeEdges(upDown, horizontal = false)
                    .verticalScroll(upDown)
                    .height(chartHeight),
            ) {
                Box(
                    modifier = Modifier
                        .width(AXIS_WIDTH)
                        .fillMaxHeight(),
                ) {
                    ticks.forEach { tick ->
                        val tickY = CHART_TOP + perMinute * (tick - windowStart)
                        // A label that would collide with the "now" marker gives way to it.
                        val hidden = nowVisible && tickY > nowY - 16.dp && tickY < nowY + 16.dp
                        if (!hidden) {
                            Text(
                                text = base.plusMinutes(tick.toLong()).clock(),
                                color = AppColors.Muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.offset(y = tickY - 7.dp),
                            )
                        }
                    }
                    if (nowVisible) {
                        Box(
                            modifier = Modifier
                                .offset(y = nowY - 9.dp)
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    ticks.forEach { tick ->
                        Box(
                            modifier = Modifier
                                .offset(y = CHART_TOP + perMinute * (tick - windowStart))
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(AppColors.Grid),
                        )
                    }
                    // The line of "now" lies behind the columns: it shows between them, and every
                    // column draws its own piece of it below its blocks, so no block is crossed out.
                    if (nowVisible) {
                        Box(
                            modifier = Modifier
                                .offset(y = nowY - 1.dp)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(AppColors.Blue),
                        )
                    }
                    // Columns that run out of the card fade lightly at its edge instead of being cut.
                    // The fade comes in softly once the chart is there, not with it.
                    val sideFade = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { sideFade.animateTo(1f, tween(durationMillis = 700, delayMillis = 250)) }
                    LazyRow(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .fadeEdges(
                                listState,
                                horizontal = true,
                                length = 22.dp,
                                edgeAlpha = { 1f - SIDE_FADE_DEPTH * sideFade.value },
                            ),
                        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
                    ) {
                        items(journeys, key = { it.id }) { journey ->
                            val bounds = remember { arrayOfNulls<Rect>(1) }
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
                                windowStart = windowStart,
                                perMinute = perMinute,
                                nowY = if (nowVisible) nowY else null,
                                selected = journey.id == selectedId,
                                level = levels?.get(journey.id),
                                modifier = moves
                                    .width(columnWidth)
                                    .fillMaxHeight()
                                    .boundsInto(bounds)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        OpenOrigin.set(bounds[0])
                                        onSelect(journey.id)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
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
 * [tightness] (normal: muted grey; knapp: red; Sprint: magenta; entspannt: dark green), with a
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
    windowStart: Float,
    perMinute: Dp,
    nowY: Dp?,
    selected: Boolean,
    level: TransferComfort?,
    modifier: Modifier = Modifier,
) {
    val legs = journey.legs
    val cancelled = journey.cancelled
    val textColor = when {
        cancelled -> AppColors.Muted
        selected -> AppColors.OnBlueContainer
        else -> AppColors.Ink
    }
    val decoration = if (cancelled) TextDecoration.LineThrough else null
    // A pick moves in softly: the ground turns blue and the blocks grow a little wider.
    val ground by animateColorAsState(
        // The same blue without opacity, so the fade does not pass through grey.
        if (selected) AppColors.BlueContainer else AppColors.BlueContainer.copy(alpha = 0f),
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "columnGround",
    )
    val inset by animateDpAsState(
        if (selected) 2.dp else 5.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "columnInset",
    )

    // The times of the legs in minutes of the chart. Fresh live data moves them there with
    // a spring, so a block that is late glides to its new place instead of jumping.
    val starts = FloatArray(legs.size)
    val ends = FloatArray(legs.size)
    val glide = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    legs.forEachIndexed { index, leg ->
        key(index) {
            starts[index] = animateFloatAsState(minuteOf(leg.departure), glide, label = "legStart").value
            ends[index] = animateFloatAsState(minuteOf(leg.arrival), glide, label = "legEnd").value
        }
    }
    val pills = remember(journey) { pillChanges(journey) }
    val parts = columnParts(legs, starts, ends, windowStart, perMinute, pills)
    val firstTop = parts.firstOrNull()?.top ?: (CHART_TOP + perMinute * (starts.first() - windowStart))
    val lastBottom = parts.lastOrNull()?.let { it.top + it.height } ?: firstTop

    // A ride that leaves late shows its "+2" above its time, as the delay setting says (see DelayDisplay).
    val display = LocalDelayDisplay.current
    val firstLeg = legs.first()
    val startDelay = firstLeg.departureDelayMinutes.takeIf { !cancelled && firstLeg.realtime && it > 0 }
    val startDelayShown = startDelay != null && display.showsDelay
    val labelHeight = if (startDelayShown) 32.dp else 18.dp

    // The dashed outline of the planned part of a late ride, by its place among the parts.
    // It needs room above its block: below the times for the first ride, below the block before for the others.
    val dashes = parts.mapIndexed { index, part ->
        val delay = part.leg.departureDelayMinutes.toInt()
        if (part.leg.walking || cancelled || delay <= 0) {
            0.dp
        } else {
            val room = if (index == 0) part.top - labelHeight else part.top - (parts[index - 1].top + parts[index - 1].height)
            val wanted = (perMinute * delay).coerceIn(6.dp, 28.dp)
            if (room >= 6.dp) minOf(wanted, room) else 0.dp
        }
    }
    // The tightest change of the journey decides whether it is marked as risky, by the time to
    // spare less any walk, as its category does. Only real changes count: two rides of the
    // same trip (staying seated) are none.
    val tightest = journeyTightness(journey)
    // Every change rated within its journey, by the leg it leads to (see journeyChangesOf).
    val changeRatings = remember(journey) { journeyChangesOf(journey).associate { it.nextIndex to it.tightness } }
    var cancelTagShown = false
    val showStops = LocalChartChangeStops.current

    Box(modifier = modifier.background(ground)) {
        // The line of "now" above the ground of the column and below everything else in it.
        if (nowY != null) {
            Box(
                modifier = Modifier
                    .offset(y = nowY - 1.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(AppColors.Blue),
            )
        }
        // The triangle next to the departure time says: this journey has a disruption notice.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = firstTop - (dashes.firstOrNull() ?: 0.dp) - labelHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (startDelayShown && startDelay != null) {
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
                if (journey.warnings.isNotEmpty()) WarningMark(size = 12.dp)
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

        parts.forEachIndexed { index, part ->
            val leg = part.leg
            if (leg.walking) {
                Column(
                    modifier = Modifier
                        .offset(y = part.top)
                        .fillMaxWidth()
                        .height(part.height),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WalkDots(modifier = Modifier.weight(1f))
                    if (part.height >= WALK_LABEL) {
                        WalkMark(
                            minutes = minutesBetween(leg.departure, leg.arrival),
                            color = if (selected) AppColors.OnBlueContainer else AppColors.Muted,
                            unit = true,
                            cycling = leg.cycling,
                        )
                    }
                    WalkDots(modifier = Modifier.weight(1f))
                }
            } else {
                // Only the ride that is cancelled is struck; the others of the journey still run.
                val grey = leg.cancelled
                val faded = cancelled && !grey
                // From the small form to the full one by the height of the block, eased by a
                // spring: zooming and dragging morph the name and the corners, never switch them.
                val morphTarget = ((part.height - MIN_BLOCK) / (FULL_BLOCK - MIN_BLOCK)).coerceIn(0f, 1f)
                val morph = key(index) {
                    animateFloatAsState(morphTarget, spring(stiffness = Spring.StiffnessMediumLow), label = "blockMorph").value
                }
                val color = lineColor(leg)
                val round = lerp(8.dp, BLOCK_ROUND, morph)
                val dash = dashes[index]
                if (dash > 0.dp) {
                    // Where the ride should have been: an outline that runs on below the block.
                    Box(
                        modifier = Modifier
                            .padding(horizontal = inset)
                            .offset(y = part.top - dash)
                            .fillMaxWidth()
                            .height(dash + round)
                            .drawBehind {
                                val line = 2.dp.toPx()
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(line / 2f, line / 2f),
                                    size = Size(size.width - line, size.height - line),
                                    cornerRadius = CornerRadius(round.toPx()),
                                    style = Stroke(
                                        width = line,
                                        pathEffect = PathEffect.dashPathEffect(
                                            floatArrayOf(4.dp.toPx(), 3.dp.toPx()),
                                            0f,
                                        ),
                                    ),
                                )
                            },
                    )
                }
                val shape = if (dash > 0.dp) RoundedCornerShape(0.dp, 0.dp, round, round) else RoundedCornerShape(round)
                // How much of the block lies behind the timetable at its end.
                val lateEnd = leg.arrivalDelayMinutes.toInt()
                val tail = if (grey || lateEnd <= 0) 0f else (perMinute * lateEnd / part.height).coerceIn(0.1f, 0.45f)
                BoxWithConstraints(
                    modifier = Modifier
                        .padding(horizontal = inset)
                        .offset(y = part.top)
                        .fillMaxWidth()
                        .height(part.height)
                        .clip(shape)
                        .then(if (faded) Modifier.alpha(FADED_RIDE_ALPHA) else Modifier)
                        .then(
                            when {
                                grey -> Modifier
                                    .background(AppColors.Chip)
                                    .stripes(AppColors.Grid, step = 9.dp, width = 4.dp)
                                    .border(2.dp, AppColors.Rail, shape)

                                tail > 0f -> Modifier
                                    .background(color)
                                    .stripes(Color.White.copy(alpha = 0.6f), fromShare = 1f - tail, step = 6.dp, width = 2.5.dp)

                                else -> Modifier.background(color)
                            },
                        )
                        .then(if (leg.warnings.isNotEmpty() && !grey) Modifier.warningBand() else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    // A low block gets a smaller name, so even a ride of one minute says which line it is.
                    // The size is that of every block ("S1", "U9") at most; a name too wide for its
                    // block gets smaller until it fits, and below a smallest size a long-distance
                    // train drops its number ("ICE 29" as "ICE") instead of being cut.
                    // A rail replacement bus says so: "SEV S1", or "SEV" where that does not fit.
                    val blockReplacement = leg.railReplacement
                    val fullLabel = blockReplacement?.let { listOfNotNull(SEV_MARK, it.replaced).joinToString(" ") }
                        ?: shortLine(leg.line) ?: "?"
                    val blockLabel = fitBlockLabel(
                        full = fullLabel,
                        compact = if (blockReplacement != null) SEV_MARK else compactLine(leg.line) ?: fullLabel,
                        maxSize = 11f + 4f * morph,
                        room = maxWidth - BLOCK_LABEL_PADDING * 2,
                    )
                    // The cancelled ride itself says so, where its block has room for the tag.
                    val tagHere = grey && !cancelTagShown && part.height >= CANCEL_TAG_BLOCK
                    if (tagHere) cancelTagShown = true
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = blockLabel.first,
                            color = if (grey) AppColors.Muted else onLineColor(color),
                            textDecoration = if (grey) TextDecoration.LineThrough else null,
                            fontSize = blockLabel.second.sp,
                            lineHeight = (13f + 5f * morph).sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(horizontal = BLOCK_LABEL_PADDING),
                        )
                        if (tagHere) {
                            ColumnTag(
                                text = stringResource(R.string.cancelled_ride_tag),
                                ground = AppColors.DelayRed,
                                ink = AppColors.OnDelayRed,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                // The gap to the next ride is the change: where it happens, or how tight it is.
                val next = parts.getOrNull(index + 1)?.takeIf { !it.leg.walking }
                if (next != null) {
                    val gapMinutes = changeGapMinutes(leg, next.leg)
                    // Staying on board is no change.
                    val onBoard = leg.tripId != null && leg.tripId == next.leg.tripId
                    val tightness = if (onBoard) {
                        Tightness.Comfortable
                    } else {
                        changeRatings[legs.indexOfFirst { it === next.leg }] ?: changeTightness(leg, next.leg)
                    }
                    // Only risky and very risky changes get a pill, so the overview stays calm; the
                    // minutes of every change stay in the detail.
                    val risky = !onBoard && tightness.ordinal >= Tightness.Risky.ordinal
                    val gapTop = part.top + part.height
                    val gapHeight = next.top - dashes[index + 1] - gapTop
                    val middle = gapTop + gapHeight / 2
                    // A pill sits in the middle of the room reserved for it (see PILL_GAP); a dashed
                    // outline of a late ride may lie behind it.
                    val pillMiddle = gapTop + (next.top - gapTop) / 2
                    when {
                        // A change to or from a cancelled ride does not happen: nothing to rate.
                        leg.cancelled || next.leg.cancelled -> Unit

                        // The minutes of a risky change: a neutral pill in the room kept free for it.
                        risky -> OverlapTag(
                            minutes = gapMinutes,
                            tightness = tightness,
                            modifier = Modifier
                                .zIndex(1f)
                                .offset(y = pillMiddle - OVERLAP_TAG_HEIGHT / 2),
                        )

                        gapHeight >= 12.dp -> {
                            // The name of the stop only where the setting asks for it (it crowds the
                            // chart); the minutes then stand below it where there is room, else alone.
                            val withStop = showStops
                            val roomy = gapHeight >= 28.dp
                            val twoLines = withStop && roomy
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .fillMaxWidth()
                                    .offset(y = middle - (if (twoLines) 12.dp else 6.dp)),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (withStop) {
                                    Text(
                                        text = leg.to.briefStopName(),
                                        color = if (selected) AppColors.OnBlueContainer else AppColors.Muted,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!withStop || roomy) {
                                    // Coloured by the wait (see waitTone): a long one is a calm sand, not green.
                                    val tone = if (onBoard) WaitTone.Neutral else waitTone(tightness, gapMinutes, changeNeed(leg, next.leg))
                                    Text(
                                        text = stringResource(R.string.in_minutes, gapMinutes),
                                        color = toneColor(tone),
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val lastLeg = legs.last()
        val arrivalDelay = if (lastLeg.realtime) lastLeg.arrivalDelayMinutes else 0L
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = maxOf(CHART_TOP + perMinute * (ends.last() - windowStart), lastBottom) + 2.dp),
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
            if (cancelled) {
                // The cancelled ride had no room for its tag: it stands below the column.
                if (!cancelTagShown) {
                    ColumnTag(
                        text = stringResource(R.string.cancelled_ride_tag),
                        ground = AppColors.DelayRed,
                        ink = AppColors.OnDelayRed,
                    )
                }
            } else {
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
            }
        }
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
) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(32.dp)) {
        Column(
            modifier = Modifier
                .fadeEdges(scroll, horizontal = false)
                .verticalScroll(scroll)
                // Earlier or later journeys that come in move the list softly.
                .softSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Each row keyed by its journey: when live times reorder the list, the row and its
            // state go with the journey and glide to the new place (see glideToPlace).
            val glide = rememberAnimationsOn()
            journeys.forEachIndexed { index, journey ->
                key(journey.id) {
                    // Where the row is, so a journey opened on its own page grows out of it.
                    val bounds = remember { arrayOfNulls<Rect>(1) }
                    Column(
                        modifier = Modifier
                            .glideToPlace(glide)
                            .then(appearModifier(index))
                            .fillMaxWidth()
                            .boundsInto(bounds)
                            .clip(groupShape(index, journeys.lastIndex, outer = 24.dp))
                            .background(if (index == selected) AppColors.BlueContainer else AppColors.Card)
                            .clickable {
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
                            if (journey.warnings.isNotEmpty()) {
                                WarningMark(modifier = Modifier.padding(end = 8.dp))
                            }
                            MinutesPill(
                                minutes = minutesBetween(now, journey.departure),
                                live = index == selected,
                                cancelled = journey.cancelled,
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
                                    journey.durationMinutes,
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
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    // Another journey is read from its top.
    LaunchedEffect(journey.id) { if (scroll.value != 0) scroll.animateScrollTo(0) }
    // The ride that shows all its stops, by its place among the legs; another journey starts folded.
    var openRide by remember(journey.id) { mutableStateOf<Int?>(null) }
    val changes = remember(journey) { journeyChanges(journey) }
    // The time of every change, by the index of the ride it leads to: the same numbers as the
    // choice of connections, the trip companion and the watch show (see changeTime).
    val changeTimes = remember(journey) { journeyChangesOf(journey).associate { it.nextIndex to it.time } }
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
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(32.dp)) {
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
                                journey.durationMinutes,
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
                        )
                        StartPill(onClick = onStartTrip)
                    }
                }
            }
            Column(
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
                if (adapted) {
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
                WarningSummary(journey.warnings)
                FareZoneNote(journey)
                JourneyLiftWarning(journey)
                val legs = journey.legs
                // The stay at a place on the way stands between the legs before and after it.
                val stay = journey.via?.takeIf { it.minutes > 0 }
                val stayAt = stay?.afterLegs
                legs.forEachIndexed { index, leg ->
                    if (stay != null && stayAt == index && index > 0) {
                        BetweenRow(
                            stringResource(R.string.via_stay_row, stay.minutes, stay.place.name.plainStopName()),
                            dots = AppColors.Blue,
                            ink = AppColors.Ink,
                        )
                    }
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
                                boardingHint = following?.let { after ->
                                    { CarriageHint(arriving = leg, next = after) }
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
                                }
                                TransferChoice(journey.id, changes.firstOrNull { it.keepLegs == index + 1 }, offers, onAdapt)
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
                                // The walk of the change as a small map, folded at first.
                                if (minutes > 0 && hasWalkMap(leg)) {
                                    FoldedMap(indent = 84.dp) { WalkMap(leg, onFocusWalk) }
                                }
                                TransferChoice(journey.id, changes.firstOrNull { it.keepLegs == index }, offers, onAdapt)
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
                                    FoldedMap(indent = 84.dp) { WalkMap(leg, onFocusWalk) }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.live_hint),
                    color = AppColors.Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

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
@Composable
private fun TransferChoice(
    journeyId: String,
    change: TripChange?,
    offers: Map<String, Alternatives>,
    onAdapt: (Int, Journey) -> Unit,
) {
    if (change == null) return
    val options = connectionOptions(change, offers[offerKey(journeyId, change.keepLegs)])
    if (options.size < 2) return
    Column(modifier = appearModifier(0), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ConnectionChips(change = change, options = options, onSwitch = { option -> onAdapt(change.keepLegs, option) })
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
private fun TransferRow(time: ChangeTime) {
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
private fun LegTime(time: OffsetDateTime, planned: OffsetDateTime?, delayMinutes: Long, realtime: Boolean, estimated: Boolean = false) {
    val display = LocalDelayDisplay.current
    // A refreshed time or delay cross-fades instead of jumping.
    FadeSwap(
        target = LegTimeShown((if (estimated) time else display.clockOf(time, planned)).clock(), delayMinutes, realtime, estimated),
        contentAlignment = Alignment.TopEnd,
    ) { shown ->
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = shown.clock,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                // A time the app estimated is grey with "ca." and without a delay: it is a guess.
                color = if (shown.estimated) AppColors.Muted else display.timeColor(shown.realtime, shown.delayMinutes, AppColors.Ink),
            )
            if (shown.estimated) {
                EstimatedMark()
            } else if (display.showsDelay && shown.delayMinutes != 0L) {
                Text(
                    text = stringResource(R.string.delay_short, shown.delayMinutes.toInt()),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = if (shown.delayMinutes > 0) AppColors.DelayRed else AppColors.Early,
                )
            }
        }
    }
}

/** What a [LegTime] shows, so a change of any of it cross-fades. */
private data class LegTimeShown(val clock: String, val delayMinutes: Long, val realtime: Boolean, val estimated: Boolean)

/** The grey "ca." in front of or below a time the app estimated (see plausible), as in the course of a line. */
@Composable
private fun EstimatedMark() {
    Text(
        text = stringResource(R.string.estimated_prefix),
        color = AppColors.Muted,
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
                name = townedStopName(leg.from, leg.rideTown),
                note = platformLabel(leg.departurePlatform),
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
                    LineBadge(leg, minWidth = 40.dp)
                    leg.railReplacement?.let { RailReplacementTag(it) }
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
                        val towards = if (direction == null) "" else stringResource(R.string.direction, direction.plainStopName())
                        val whole = fullLineName(leg.line)
                        Text(
                            text = if (whole == null) towards else listOf(whole, towards).filter { it.isNotEmpty() }.joinToString(" · "),
                            color = AppColors.Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
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
                name = townedStopName(leg.to, leg.rideTown),
                note = platformLabel(leg.arrivalPlatform),
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
            between.forEach { stop ->
                StopLine(
                    name = townedStopName(stop.name, town),
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
                Text(text = note, color = AppColors.Muted, fontSize = 12.sp, maxLines = 1)
            }
            if (place != null) MoreDots(label = menuLabel, onClick = { menuOpen = true }, size = if (strong) 32.dp else 28.dp)
        }
        if (place != null) {
            StopMenu(
                open = menuOpen,
                onDismiss = { menuOpen = false },
                onAction = { action -> onStopAction(place, action) },
            )
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
