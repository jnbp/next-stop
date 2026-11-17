package io.github.jnbp.nextstop.wear.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.ChangeMap
import io.github.jnbp.nextstop.wear.data.ChangeRating
import io.github.jnbp.nextstop.wear.data.LOG_TAG
import io.github.jnbp.nextstop.wear.data.LocalFollow
import io.github.jnbp.nextstop.wear.data.MapImageRequest
import io.github.jnbp.nextstop.wear.data.NextRide
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.PlanRide
import io.github.jnbp.nextstop.wear.data.Position
import io.github.jnbp.nextstop.wear.data.RideStop
import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.TripSnapshot
import io.github.jnbp.nextstop.wear.data.WalkSketch
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.WebMercator
import io.github.jnbp.nextstop.wear.data.changeMapFit
import io.github.jnbp.nextstop.wear.data.currentPosition
import io.github.jnbp.nextstop.wear.data.phonePosition
import io.github.jnbp.nextstop.wear.data.hasLocationPermission
import io.github.jnbp.nextstop.wear.data.getOffSoon
import io.github.jnbp.nextstop.wear.data.hoursAndMinutes
import io.github.jnbp.nextstop.wear.data.lineArgb
import io.github.jnbp.nextstop.wear.data.rideProgress
import io.github.jnbp.nextstop.wear.data.ridingStopsLeft

private val tripClient = BvgClient()

/** The views of the trip companion, swiped through horizontally. */
private enum class TripPage { Depart, Ride, Change, Overview }

/**
 * What the change card shows: the ride to catch ([ride], null for the walk to the
 * destination), the minutes on foot, how tight the change is, the map of the way and where it
 * leads. [ofThisStep] when it is the walk of the current step (a change under way, or the
 * walk to the first stop), else a look ahead at the change after this ride. [atChange] for a
 * change between two rides, not the walk to the start or to the destination.
 */
private data class TransferView(
    val ride: NextRide?,
    val walkMinutes: Int,
    val rating: ChangeRating?,
    val spare: Int?,
    val gap: Int?,
    val map: ChangeMap?,
    val toName: String,
    val ofThisStep: Boolean,
    val atChange: Boolean,
    /** The phone says the change is across the same platform: no way to show. */
    val samePlatform: Boolean = false,
    /** The way is ridden by bike (the bike combo of the phone), not walked. */
    val cycling: Boolean = false,
)

private fun WalkSketch.toMap(): ChangeMap = ChangeMap(fromLatitude, fromLongitude, toLatitude, toLongitude)

private fun transferOf(trip: TripSnapshot): TransferView? = when (trip.kind) {
    StepKind.GetOff -> trip.next?.let { next ->
        TransferView(
            next, next.walkMinutes, next.rating, next.spare, next.gap, next.map ?: trip.walk?.toMap(), next.stop,
            ofThisStep = false, atChange = true, samePlatform = next.samePlatform, cycling = next.cycling || trip.walk?.cycling == true,
        )
    }
    StepKind.Board -> {
        val walk = trip.walk
        val atChange = trip.rating != null || trip.planIndex > 0
        if (atChange || walk != null || trip.map != null) {
            val ride = NextRide(trip.line, trip.product, trip.direction, trip.place, trip.platform, if (trip.leave) trip.departure else trip.time, walk?.minutes ?: 0, color = trip.color)
            TransferView(
                ride, walk?.minutes ?: 0, trip.rating, trip.spare, trip.gap, trip.map ?: walk?.toMap(), trip.place,
                ofThisStep = true, atChange = atChange, cycling = walk?.cycling == true,
            )
        } else {
            trip.next?.let { next ->
                TransferView(
                    next, next.walkMinutes, next.rating, next.spare, next.gap, next.map, next.stop,
                    ofThisStep = false, atChange = true, samePlatform = next.samePlatform, cycling = next.cycling,
                )
            }
        }
    }
    StepKind.WalkToEnd -> {
        val walk = trip.walk
        val map = trip.map ?: walk?.toMap()
        if (walk == null && map == null) {
            null
        } else {
            TransferView(null, walk?.minutes ?: 0, null, null, null, map, trip.destination, ofThisStep = true, atChange = false, cycling = walk?.cycling == true)
        }
    }
    StepKind.Arrived -> null
}

/** The pages of [trip] in the order of the way, the overview always last. */
private fun pagesOf(trip: TripSnapshot, transfer: TransferView?): List<TripPage> = buildList {
    when (trip.kind) {
        StepKind.Board -> if (transfer != null && transfer.ofThisStep) {
            add(TripPage.Change)
            add(TripPage.Depart)
        } else {
            add(TripPage.Depart)
            if (transfer != null) add(TripPage.Change)
        }
        StepKind.GetOff -> {
            add(TripPage.Ride)
            if (transfer != null) add(TripPage.Change)
        }
        StepKind.WalkToEnd -> if (transfer != null) add(TripPage.Change)
        StepKind.Arrived -> Unit
    }
    add(TripPage.Overview)
}

/**
 * The page that fits the phase of [trip]: before the first ride the countdown, at a change
 * the change card, on board the progress line (the change card from the stop before a
 * change), on the walk to the end its card, once arrived the overview.
 */
private fun desiredPage(trip: TripSnapshot, transfer: TransferView?): TripPage = when (trip.kind) {
    StepKind.Board -> if (transfer != null && transfer.ofThisStep && transfer.atChange) TripPage.Change else TripPage.Depart
    StepKind.GetOff -> if (trip.change && trip.alert && transfer != null) TripPage.Change else TripPage.Ride
    StepKind.WalkToEnd -> if (transfer != null) TripPage.Change else TripPage.Overview
    StepKind.Arrived -> TripPage.Overview
}

/** True while the watch shows the app in ambient mode (always-on) on the trip page. */
@Composable
private fun rememberAmbient(): Boolean {
    // Turns always-on on for the activity while the trip page is shown.
    val manager = LocalAmbientModeManager.current ?: rememberAmbientModeManager()
    return manager.currentAmbientMode is AmbientMode.Ambient
}

/**
 * The time now, updated every [periodMillis] (on the full period) while [active]; read it
 * only where it is needed (a text, a draw scope), so only that part changes.
 */
@Composable
private fun rememberNow(periodMillis: Long, active: Boolean): State<Long> {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMillis, active) {
        state.longValue = System.currentTimeMillis()
        if (!active) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            delay(periodMillis - now % periodMillis)
            state.longValue = System.currentTimeMillis()
        }
    }
    return state
}

/**
 * The position of the user for the map of a change while [enabled]: a fresh fix of the
 * watch (or the phone's position) every [OWN_POSITION_MILLIS]; null when there is none or
 * it is too old to point the right way.
 */
@Composable
private fun rememberOwnPosition(enabled: Boolean): State<Position?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Position?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled || !hasLocationPermission(context)) {
            state.value = null
            return@LaunchedEffect
        }
        var askedPhone = 0L
        while (true) {
            val found = try {
                currentPosition(context, waitForFix = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            var fresh = found?.takeIf { System.currentTimeMillis() - it.timeMillis < OWN_FRESH_MILLIS }
            // No fresh fix of the watch: ask the phone for its last location (Data Layer), at
            // most once a minute, as each ask makes the phone send all its places.
            if (fresh == null && System.currentTimeMillis() - askedPhone > PHONE_ASK_MILLIS) {
                askedPhone = System.currentTimeMillis()
                fresh = try {
                    phonePosition(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }?.takeIf { System.currentTimeMillis() - it.timeMillis < OWN_FRESH_MILLIS }
            }
            state.value = fresh
            delay(OWN_POSITION_MILLIS)
        }
    }
    return state
}

/**
 * The followed trip as a companion of pages, switched by a horizontal swipe: the countdown
 * before departure, the progress on board, the change card with its map, and the overview
 * of the whole connection with "Missed it" and "End trip". The watch turns to the page that
 * fits when the phase changes; a swipe of the user holds the page for [HOLD_MILLIS].
 * "Missed it" plans again from where the user is to the same destination ([onReplan]).
 */
@Composable
fun TripScreen(onEnded: () -> Unit, onReplan: (Place) -> Unit) {
    val context = LocalContext.current
    val trip by WatchStore.trip.collectAsState()
    val scope = rememberCoroutineScope()
    var replanning by remember { mutableStateOf(false) }
    var replanFailed by remember { mutableStateOf(false) }
    fun replan() {
        replanFailed = false
        // The destination as a place; a trip of an older phone app only names it, so it is searched.
        trip.target?.let { onReplan(it); return }
        val name = trip.destination.takeIf { it.isNotBlank() } ?: run { replanFailed = true; return }
        replanning = true
        scope.launch {
            val found = try {
                tripClient.searchPlaces(name, results = 1).firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(LOG_TAG, "missed it: search of the destination failed: $e")
                null
            }
            replanning = false
            if (found != null) onReplan(found) else replanFailed = true
        }
    }
    val ambient = rememberAmbient()
    // The moments of the trip on the wrist (see TripMoments): a buzz at its start and at every
    // change made; the arrival buzzes and pops a check with a ring of confetti along the edge.
    val moments = remember { TripMoments() }
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(trip) {
        val told = moments.update(trip.tripKey, trip.active, trip.kind, trip.arrival, trip.started, System.currentTimeMillis())
        told.forEach { moment -> buzz(context, moment) }
        if (WatchMoment.Arrived in told) celebrate = true
    }
    // The colour of the last line of the trip, kept for the confetti once the trip is over.
    val lastLine = remember { longArrayOf(0L) }
    if (trip.active && trip.line != null) lastLine[0] = lineArgb(trip.line, trip.product, trip.color)
    val confetti = listOfNotNull(
        lastLine[0].takeIf { it != 0L }?.let { Color(it) },
        CONFETTI_LIME,
        CONFETTI_BLUE,
        Color.White,
    )
    val celebration: @Composable () -> Unit = {
        ArrivalCelebration(
            play = celebrate,
            colors = confetti,
            ambient = ambient,
            check = CONFETTI_LIME,
            onCheck = Color(0xFF14210A),
        )
    }
    if (!trip.active) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScreenScaffold { _ ->
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    ListHeader { Text(stringResource(R.string.your_trip)) }
                    Note(stringResource(R.string.arrived))
                }
            }
            celebration()
        }
        return
    }
    val transfer = remember(trip) { transferOf(trip) }
    val pages = remember(trip, transfer) { pagesOf(trip, transfer) }
    val desired = desiredPage(trip, transfer)
    val pagesState = rememberUpdatedState(pages)
    val desiredState = rememberUpdatedState(desired)
    val pagerState = rememberPagerState(initialPage = pages.indexOf(desired).coerceAtLeast(0)) { pagesState.value.size }
    var holdUntil by remember { mutableLongStateOf(0L) }
    var shown by remember { mutableStateOf(desired) }
    var mapOpen by remember { mutableStateOf(false) }
    fun hold() {
        holdUntil = System.currentTimeMillis() + HOLD_MILLIS
    }
    // The page the user looks at, by its kind, so it stays when the pages around it change.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { index -> pagesState.value.getOrNull(index)?.let { shown = it } }
    }
    // A swipe of the user holds the page.
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { if (it is DragInteraction.Start) hold() }
    }
    // A new phase (or new pages): to the page that fits, unless the user swiped a moment ago.
    LaunchedEffect(pages, desired) {
        val held = System.currentTimeMillis() < holdUntil && shown in pages
        val index = pages.indexOf(if (held) shown else desired)
        if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) {
            if (held) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index)
        }
    }
    // Once the hold is over: back to the page that fits.
    LaunchedEffect(holdUntil) {
        val wait = holdUntil - System.currentTimeMillis()
        if (wait <= 0) return@LaunchedEffect
        delay(wait)
        val index = pagesState.value.indexOf(desiredState.value)
        if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) pagerState.animateScrollToPage(index)
    }
    // The user's position for the map of a change, only while that map is on screen.
    val mapShown = !ambient && transfer?.map != null &&
        (mapOpen || pages.getOrNull(pagerState.currentPage) == TripPage.Change)
    val own by rememberOwnPosition(mapShown)
    fun toOverview() {
        hold()
        scope.launch { pagerState.animateScrollToPage(pagesState.value.lastIndex) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPagerScaffold(
            pagerState = pagerState,
            pageIndicator = if (ambient) null else ({ HorizontalPageIndicator(pagerState) }),
        ) {
            // The crown scrolls the page of now (its column or list, see crownScroll), not the pager.
            HorizontalPager(state = pagerState, rotaryScrollableBehavior = null) { index ->
                val page = pages.getOrNull(index) ?: TripPage.Overview
                val active = pagerState.currentPage == index
                when (page) {
                    TripPage.Depart -> DepartPage(trip, active, ambient)
                    TripPage.Ride -> RidePage(trip, active, ambient)
                    TripPage.Change -> transfer?.let { view ->
                        ChangePage(trip, view, own, active, ambient, onOverview = ::toOverview, onMap = {
                            hold()
                            mapOpen = true
                        })
                    }
                    TripPage.Overview -> OverviewPage(
                        trip = trip,
                        active = active,
                        ambient = ambient,
                        replanning = replanning,
                        replanFailed = replanFailed,
                        onInteract = ::hold,
                        onMissed = { if (!replanning) replan() },
                        onEnd = {
                            LocalFollow.endEverywhere(context)
                            onEnded()
                        },
                    )
                }
            }
        }
        // The map of the change over the whole screen, always with the latest state of the trip.
        val opened = transfer?.takeIf { mapOpen && it.map != null }
        if (opened != null) {
            BackHandler { mapOpen = false }
            FullMap(
                view = opened,
                own = own,
                lineColor = opened.ride?.let { Color(lineArgb(it.line, it.product, it.color)) } ?: MaterialTheme.colorScheme.primary,
                ambient = ambient,
                onClose = { mapOpen = false },
            )
        }
        celebration()
    }
}

/**
 * The column of a trip page: below the time of the watch at the top (the content padding of
 * the scaffold, at least [TOP_CLEARANCE]) and above the page indicator, centred in between
 * when it fits, else scrolled (crown or swipe). The crown scrolls the column of the [active]
 * page only (the pager leaves the crown to its pages).
 */
@Composable
private fun PageColumn(
    scroll: ScrollState,
    active: Boolean,
    contentPadding: PaddingValues,
    horizontal: Dp,
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val top = maxOf(contentPadding.calculateTopPadding(), TOP_CLEARANCE)
        val bottom = maxOf(contentPadding.calculateBottomPadding(), BOTTOM_CLEARANCE)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .crownScroll(scroll, active)
                .verticalScroll(scroll)
                .heightIn(min = maxHeight)
                .padding(start = horizontal, end = horizontal, top = top, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * The way on foot of a change: its minutes and metres as the phone sends them (the minutes of
 * the step, else those of the way drawn; the metres of the way drawn, else the straight line
 * between its ends). "Same platform" only where the phone says so; nothing where nothing is
 * known (never "no walk" for a change whose platforms lie apart).
 */
@Composable
private fun WalkLine(view: TransferView) {
    val minutes = maxOf(view.walkMinutes, view.map?.minutes ?: 0)
    val meters = view.map?.let { walkMeters(it) }?.takeIf { it >= MIN_WALK_METRES }
    // A way by bike (the bike combo of the phone) says so instead of "on foot".
    val cycling = view.cycling
    val walk = when {
        minutes > 0 && meters != null -> stringResource(if (cycling) R.string.cycle_distance else R.string.walk_distance, meters, minutes)
        minutes > 0 -> stringResource(if (cycling) R.string.cycle_minutes else R.string.walk_on_foot, minutes)
        meters != null -> stringResource(if (cycling) R.string.cycle_meters else R.string.walk_meters, meters)
        else -> null
    }
    when {
        walk != null -> Text("→ $walk", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        view.atChange && view.samePlatform -> Text(
            stringResource(R.string.same_platform),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The metres of the way of [map]: as the phone measured the way drawn, else the straight line, to 10 m. */
private fun walkMeters(map: ChangeMap): Int {
    map.meters?.let { return it }
    val east = (map.toLongitude - map.fromLongitude) * METRES_PER_DEGREE * cos(Math.toRadians((map.fromLatitude + map.toLatitude) / 2))
    val north = (map.toLatitude - map.fromLatitude) * METRES_PER_DEGREE
    return ((sqrt(east * east + north * north) + 5) / 10).toInt() * 10
}

/** The small chip to the overview at the foot of a page. */
@Composable
private fun OverviewChip(onClick: () -> Unit) {
    SmallAction(text = stringResource(R.string.overview), onClick = onClick)
}

/** "Departs in 4 min", "in 45 s" in the last minute (with [seconds]), "now", or the clock time when it is far. */
@Composable
private fun departText(time: Long, now: Long, seconds: Boolean): String {
    val left = time - now
    return when {
        left <= 0 -> stringResource(R.string.depart_now)
        left < 60_000 && seconds -> stringResource(R.string.depart_in_seconds, ((left + 999) / 1000).toInt())
        left < 60_000 -> stringResource(R.string.depart_in, 1)
        // From an hour on (also where the minutes round up to 60) the clock time.
        left >= FAR_MILLIS - 30_000 -> stringResource(R.string.depart_at, clock(time))
        else -> stringResource(R.string.depart_in, ((left + 30_000) / 60_000).toInt())
    }
}

/** Only this text follows the ticking clock, not the page around it. */
@Composable
private fun CountdownText(time: Long, now: State<Long>, seconds: Boolean) {
    // Each new value rolls in from below; in ambient mode (no seconds) it is simply there.
    WatchSwap(target = departText(time, now.value, seconds), ambient = !seconds, roll = true) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** "4 min", "now" (or the clock time when far) until [time], at [now]. */
@Composable
private fun untilText(time: Long, now: Long): String {
    val left = time - now
    return when {
        left < 30_000 -> stringResource(R.string.now)
        left >= FAR_MILLIS - 30_000 -> clock(time)
        else -> stringResource(R.string.in_min, ((left + 30_000) / 60_000).toInt())
    }
}

/** "4 min left", "now", or nothing when it is far, until [time] at [now]. */
@Composable
private fun remainingText(time: Long, now: Long): String {
    val left = time - now
    return when {
        left < 30_000 -> stringResource(R.string.now)
        left >= FAR_MILLIS - 30_000 -> ""
        else -> stringResource(R.string.remaining_min, ((left + 30_000) / 60_000).toInt())
    }
}

/**
 * Before departure: a ring around the edge of the screen that empties until the departure
 * (green, amber in the last minute; none in ambient mode), the countdown, the line, platform
 * and direction, and where to board as a small train.
 */
@Composable
private fun DepartPage(trip: TripSnapshot, active: Boolean, ambient: Boolean) {
    val now = rememberNow(if (ambient) 60_000 else 1_000, active)
    val lineColor = Color(lineArgb(trip.line, trip.product, trip.color))
    val track = MaterialTheme.colorScheme.surfaceContainer
    // The ring drains smoothly between the ticks of the clock; in ambient mode nothing moves.
    val ringTarget = ((trip.time - now.value).toFloat() / RING_WINDOW_MILLIS).coerceIn(0f, 1f)
    val ring by animateFloatAsState(
        ringTarget,
        if (ambient || !rememberWatchAnimationsOn()) snap() else tween(1_000, easing = LinearEasing),
        label = "departRing",
    )
    val scroll = rememberScrollState()
    // The ring runs along the edge: no scroll indicator over it. The page fits the screen; only
    // with very large text does its column still scroll (crown or swipe).
    ScreenScaffold(scrollState = scroll, scrollIndicator = null) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!ambient) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2 + 2.dp.toPx()
                    val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
                    val topLeft = Offset(inset, inset)
                    drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                    val left = trip.time - now.value
                    val fraction = ring
                    if (fraction > 0f) {
                        drawArc(
                            color = if (left <= 60_000) AMBER else GREEN,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            PageColumn(scroll, active, contentPadding, horizontal = 24.dp, spacing = 2.dp) {
                Text(
                    trip.place,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                LineBadge(trip.line, trip.product, color = trip.color)
                // Before the walk to the first ride the countdown runs to leaving, and says so.
                if (trip.leave) {
                    Text(stringResource(R.string.step_leave, clock(trip.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                CountdownText(trip.time, now, seconds = !ambient)
                // An arrow before the destination saves room; read out as "towards …".
                val platform = platformText(trip.platform, trip.line)
                val where = listOfNotNull(platform, trip.direction?.let { stringResource(R.string.towards_arrow, it) }).joinToString(" · ")
                val spokenWhere = listOfNotNull(platform, trip.direction?.let { stringResource(R.string.towards, it) }).joinToString(" · ")
                if (where.isNotEmpty() || trip.delayMinutes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (where.isNotEmpty()) {
                            Text(where, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).semantics { contentDescription = spokenWhere })
                        }
                        if (trip.delayMinutes > 0) {
                            Text(" +${trip.delayMinutes}", style = MaterialTheme.typography.bodySmall, color = AMBER)
                        }
                    }
                }
                val board = boardPartOf(trip)
                if (board != null) {
                    Spacer(Modifier.height(3.dp))
                    TrainSketch(part = board.first, estimated = board.second, product = trip.product, lineColor = lineColor, ambient = ambient)
                    Text(
                        stringResource(boardLabel(board.first, board.second)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (board.second) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    trip.hint?.let { Note(it) }
                }
            }
        }
    }
}

/**
 * The third of the train to board in, 0 rear, 1 middle, 2 front, and whether it is an
 * estimate: as the phone sends it, else read from its hint text (an older phone app).
 */
private fun boardPartOf(trip: TripSnapshot): Pair<Int, Boolean>? {
    if (trip.kind != StepKind.Board) return null
    if (trip.boardPart in 0..2) return trip.boardPart to trip.boardEstimated
    val hint = trip.hint?.lowercase() ?: return null
    val estimated = "eher" in hint || "towards" in hint || "around" in hint
    val part = when {
        "vorne" in hint || "front" in hint -> 2
        "hinten" in hint || "rear" in hint -> 0
        "mitte" in hint || "middle" in hint -> 1
        else -> return null
    }
    return part to estimated
}

private fun boardLabel(part: Int, estimated: Boolean): Int = when (part) {
    0 -> if (estimated) R.string.board_rear_estimated else R.string.board_rear
    2 -> if (estimated) R.string.board_front_estimated else R.string.board_front
    else -> if (estimated) R.string.board_middle_estimated else R.string.board_middle
}

/**
 * The train to board in, as the phone draws it: one capsule running to the right with a
 * softly rounded rear and a fully rounded front, split by thin gaps into its cars (an S-Bahn
 * four, other trains three). The cars of the third of [part] (0 rear, 1 middle, 2 front) are
 * filled in the colour of the line, the others in a light tint of it; a small arrow ahead of
 * the front shows the direction of travel. An [estimated] part is paler. In ambient mode
 * outlines and white only.
 */
@Composable
private fun TrainSketch(part: Int, estimated: Boolean, product: String, lineColor: Color, ambient: Boolean) {
    val body = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val cars = if (product == "suburban") 4 else 3
    Canvas(modifier = Modifier.width(96.dp).height(18.dp)) {
        val arrowWidth = 7.dp.toPx()
        val arrowGap = 4.dp.toPx()
        val top = 2.dp.toPx()
        val bottom = size.height - 2.dp.toPx()
        val height = bottom - top
        val trainWidth = size.width - arrowWidth - arrowGap
        val gap = 1.5.dp.toPx()
        val car = (trainWidth - gap * (cars - 1)) / cars
        // The cars of the third to board: those whose middle lies in it.
        val picked = (0 until cars).filter { index ->
            val middle = (index + 0.5f) / cars
            when (part) {
                0 -> middle < 1f / 3f
                2 -> middle > 2f / 3f
                else -> middle in (1f / 3f)..(2f / 3f)
            }
        }.ifEmpty { listOf(if (part == 0) 0 else if (part == 2) cars - 1 else cars / 2) }.toSet()
        val rear = CornerRadius(4.dp.toPx())
        val front = CornerRadius(height / 2f)
        val shape = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, top, trainWidth, bottom),
                    topLeft = rear,
                    topRight = front,
                    bottomRight = front,
                    bottomLeft = rear,
                ),
            )
        }
        val zone = if (estimated) 0.6f else 1f
        clipPath(shape) {
            for (index in 0 until cars) {
                val left = index * (car + gap)
                val inZone = index in picked
                when {
                    ambient && inZone -> drawRect(Color.White.copy(alpha = zone), topLeft = Offset(left, top), size = Size(car, height))
                    ambient -> Unit
                    inZone -> drawRect(lineColor.copy(alpha = zone), topLeft = Offset(left, top), size = Size(car, height))
                    else -> {
                        drawRect(body, topLeft = Offset(left, top), size = Size(car, height))
                        drawRect(lineColor.copy(alpha = 0.22f), topLeft = Offset(left, top), size = Size(car, height))
                    }
                }
            }
        }
        if (ambient) drawPath(shape, Color.White, style = Stroke(1.dp.toPx()))
        // The direction of travel: a small rounded chevron ahead of the front.
        val mid = size.height / 2f
        val tipX = size.width - 1.dp.toPx()
        val chevron = Path().apply {
            moveTo(tipX - arrowWidth + 1.dp.toPx(), mid - height / 3f)
            lineTo(tipX, mid)
            lineTo(tipX - arrowWidth + 1.dp.toPx(), mid + height / 3f)
        }
        drawPath(chevron, if (ambient) Color.White else ink, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * On board: the way to the stop to get off as a ring along the round edge of the screen (a bar
 * at the foot of a square one, see [drawRideWay]) in the colour of the line, a dot for each
 * stop; in the middle the line and its direction, the stops left as a big number (or "next
 * stop: get off / change"), where to get off, the arrival with its delay and the next stop by
 * name; below, as before, the line of stops around the train ([StopLine]); the overview is the last page of the pager (a swipe away).
 * Only thin outlines in ambient mode. The buzz before getting off comes from alertGetOff
 * (WatchDataService for a trip of the phone, LocalFollow for one of the watch), not from this
 * page, so it never comes twice.
 */
@Composable
private fun RidePage(trip: TripSnapshot, active: Boolean, ambient: Boolean) {
    val now = rememberNow(if (ambient) 60_000 else 10_000, active)
    val lineColor = Color(lineArgb(trip.line, trip.product, trip.color))
    val stops = trip.stops
    val times = remember(stops) { stops.map { it.time } }
    val current = currentStop(stops, now.value)
    // The stops left: as the phone counts them, else from the times of the stops.
    val left = ridingStopsLeft(trip.stopsLeft, times, now.value)
    val progress = rideProgress(times, now.value)
    val animationsOn = rememberWatchAnimationsOn()
    // The ring moves on smoothly between the ticks of the clock; in ambient mode it jumps.
    val shown by animateFloatAsState(
        progress ?: 0f,
        if (ambient || !animationsOn) snap() else tween(1_000, easing = LinearEasing),
        label = "rideRing",
    )
    val round = LocalConfiguration.current.isScreenRound
    val track = MaterialTheme.colorScheme.surfaceContainer
    val ground = MaterialTheme.colorScheme.background
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val scroll = rememberScrollState()
    // The ring runs along the edge: no scroll indicator over it. The page fits the screen; only
    // with very large text does its column still scroll (crown or swipe).
    ScreenScaffold(scrollState = scroll, scrollIndicator = null) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (progress != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = RIDE_RING_STROKE.toPx()
                    // On a round screen just inside the edge; on a square one a bar above the page indicator.
                    val inset = if (round) stroke / 2 + RIDE_RING_EDGE.toPx() else BOTTOM_CLEARANCE.toPx() + stroke
                    drawRideWay(shown, stops.size, lineColor, track, ground, tick, stroke, inset, round, ambient)
                }
            }
            PageColumn(scroll, active, contentPadding, horizontal = 26.dp, spacing = 2.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                    LineBadge(trip.line, trip.product, color = trip.color)
                    trip.direction?.let {
                        Spacer(Modifier.width(4.dp))
                        val spoken = stringResource(R.string.towards, it)
                        Text(stringResource(R.string.towards_arrow, it), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { contentDescription = spoken })
                    }
                }
                if (left > 1) {
                    // Each stop passed rolls the number down (simply there in ambient mode).
                    WatchSwap(target = left, ambient = ambient, roll = true) { count ->
                        Text("$count", style = MaterialTheme.typography.numeralMedium, maxLines = 1)
                    }
                    Text(
                        stringResource(R.string.ride_stops_to_go),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (left == 1 && !getOffSoon(trip.time, now.value)) {
                    // A long last hop: the time left, "next stop" only in the last minutes.
                    Text(
                        stringResource(
                            if (trip.change) R.string.change_in_at else R.string.get_off_in_at,
                            minutesLabel(trip.time),
                            clock(trip.time),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                } else if (left == 1) {
                    Text(
                        stringResource(if (trip.change) R.string.next_stop_change else R.string.next_stop_get_off),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (ambient) Color.White else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    stringResource(if (trip.change) R.string.ride_change_at else R.string.ride_get_off_at, trip.place),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val remaining = remainingText(trip.time, now.value)
                // A new minute cross-fades in (not in ambient mode).
                WatchSwap(
                    target = (if (remaining.isEmpty()) stringResource(R.string.arrive_clock, clock(trip.time)) else stringResource(R.string.arrive_left, clock(trip.time), remaining)) +
                        if (trip.delayMinutes > 0) " (+${trip.delayMinutes})" else "",
                    ambient = ambient,
                ) { arrive ->
                    Text(
                        arrive,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                // The next stop by name, while it is not the one to get off at.
                stops.getOrNull(current + 1)?.takeIf { current + 1 < stops.lastIndex && it.name.isNotBlank() }?.let {
                    Text(
                        stringResource(R.string.next_stop_name, it.name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The line of stops of before, kept: it also shows a ride the phone sends without
                // times (dots by the stops left) and the stops around the train on a long ride.
                val dots = if (stops.size >= 2) stops else if (left > 0) List(left + 1) { RideStop("", 0) } else emptyList()
                if (dots.size >= 2) {
                    StopLine(dots, now, lineColor, ambient, modifier = Modifier.fillMaxWidth().height(22.dp))
                }
            }
        }
    }
}

/** The stop the train was at last (its time passed), 0 before it left the first. */
private fun currentStop(stops: List<RideStop>, now: Long): Int =
    stops.indexOfLast { it.time in 1..now }.coerceAtLeast(0)

/**
 * The horizontal line of the stops: at most [MAX_DOTS] dots around the train, and when the
 * stop to get off lies beyond, a dashed gap and that stop at the end. Stops behind are dim,
 * the train a white dot between two stops by the time, the stop to get off a ring.
 */
@Composable
private fun StopLine(stops: List<RideStop>, now: State<Long>, lineColor: Color, ambient: Boolean, modifier: Modifier) {
    val ink = if (ambient) Color.White else lineColor
    val ground = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier) {
        val time = now.value
        val last = stops.lastIndex
        val current = currentStop(stops, time).coerceAtMost(last)
        val window: List<Int>
        val gap: Boolean
        if (stops.size <= MAX_DOTS) {
            window = (0..last).toList()
            gap = false
        } else {
            var start = (current - 1).coerceAtLeast(0)
            if (start + MAX_DOTS - 1 >= last) {
                start = last - (MAX_DOTS - 1)
                window = (start..last).toList()
                gap = false
            } else {
                window = (start until start + MAX_DOTS - 1).toList() + last
                gap = true
            }
        }
        val pad = 8.dp.toPx()
        val y = size.height / 2
        val step = (size.width - 2 * pad) / (window.size - 1)
        fun x(slot: Int) = pad + slot * step
        val stroke = 3.dp.toPx()
        // Where the train is: between the current stop and the next by their times.
        val slot = window.indexOf(current)
        val next = stops.getOrNull(current + 1)
        val fraction = if (next != null && stops[current].time > 0 && next.time > stops[current].time) {
            ((time - stops[current].time).toFloat() / (next.time - stops[current].time)).coerceIn(0f, 1f)
        } else {
            0f
        }
        val trainX = if (slot < 0) x(0) else if (slot + 1 < window.size) x(slot) + fraction * (x(slot + 1) - x(slot)) else x(slot)
        for (k in 0 until window.size - 1) {
            val start = Offset(x(k), y)
            val end = Offset(x(k + 1), y)
            val dashed = gap && k == window.size - 2
            val behind = x(k + 1) <= trainX
            drawLine(
                color = if (behind) ink.copy(alpha = 0.35f) else ink,
                start = start,
                end = end,
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())) else null,
            )
            if (!behind && x(k) < trainX) {
                drawLine(ink.copy(alpha = 0.35f), start, Offset(trainX, y), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        window.forEachIndexed { k, index ->
            val centre = Offset(x(k), y)
            when {
                index == last -> {
                    drawCircle(Color.White, radius = 7.dp.toPx(), center = centre)
                    drawCircle(ink, radius = 4.5.dp.toPx(), center = centre)
                }
                index <= current -> drawCircle(ink.copy(alpha = 0.45f), radius = 3.dp.toPx(), center = centre)
                else -> {
                    drawCircle(ink, radius = 4.dp.toPx(), center = centre)
                    drawCircle(ground, radius = 2.dp.toPx(), center = centre)
                }
            }
        }
        // The train.
        drawCircle(ink, radius = 6.dp.toPx(), center = Offset(trainX, y))
        drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(trainX, y))
    }
}

/**
 * The change card: the ride to catch with its time, big where it leaves (platform or stop
 * position), the minutes on foot, how tight it is with the minutes to spare, and a small map
 * of the way, which a tap opens over the whole screen.
 */
@Composable
private fun ChangePage(
    trip: TripSnapshot,
    view: TransferView,
    own: Position?,
    active: Boolean,
    ambient: Boolean,
    onOverview: () -> Unit,
    onMap: () -> Unit,
) {
    val now = rememberNow(if (ambient) 60_000 else 10_000, active)
    val scroll = rememberScrollState()
    val ride = view.ride
    val lineColor = ride?.let { Color(lineArgb(it.line, it.product, it.color)) } ?: MaterialTheme.colorScheme.primary
    ScreenScaffold(scrollState = scroll) { contentPadding ->
        PageColumn(scroll, active, contentPadding, horizontal = 22.dp, spacing = 3.dp) {
            Text(
                stringResource(
                    when {
                        ride == null -> R.string.to_destination
                        !view.ofThisStep -> R.string.then_label
                        view.atChange -> R.string.change_label
                        else -> R.string.to_first_stop
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (ride != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(ride.line, ride.product, color = ride.color)
                    Spacer(Modifier.width(6.dp))
                    // A new minute cross-fades in (not in ambient mode).
                    WatchSwap(target = stringResource(R.string.departs_at, clock(ride.time), untilText(ride.time, now.value)), ambient = ambient) { departs ->
                        Text(
                            departs,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                val platform = platformText(ride.platform, ride.line)
                Text(
                    platform ?: ride.stop,
                    style = if (platform != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (platform != null && ride.stop.isNotBlank()) {
                    Text(ride.stop, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(
                    view.toName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.arrival_at, clock(trip.arrival)), style = MaterialTheme.typography.bodySmall)
            }
            WalkLine(view)
            view.rating?.let { rating ->
                Text(
                    ratingText(rating, view.spare, view.gap),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ambient) Color.White else ratingColor(rating),
                    textAlign = TextAlign.Center,
                )
            }
            view.map?.let { map ->
                Spacer(Modifier.height(2.dp))
                ChangeMapView(
                    map = map,
                    own = own,
                    lineColor = lineColor,
                    ambient = ambient,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(70.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(onClick = onMap),
                )
            }
            Spacer(Modifier.height(2.dp))
            OverviewChip(onOverview)
        }
    }
}

/**
 * "relaxed · 8 min to change" in the words of the comfort levels: the minutes from the live
 * arrival to the live departure, as the phone shows them; from a phone that sends none, the
 * minutes beyond what the change needs ("+4 min to spare").
 */
@Composable
private fun ratingText(rating: ChangeRating, spare: Int?, gap: Int? = null): String {
    val name = stringResource(
        when (rating) {
            ChangeRating.Comfortable -> R.string.comfort_relaxed
            ChangeRating.Tight -> R.string.comfort_normal
            ChangeRating.Risky -> R.string.comfort_risky
            ChangeRating.VeryRisky -> R.string.comfort_very_risky
        },
    )
    if (gap != null) {
        // From an hour on in hours and minutes ("1 h 5 min"), as every duration of the app.
        val (hours, rest) = hoursAndMinutes(gap.toLong())
        return when {
            hours == null -> stringResource(R.string.rating_gap, name, gap)
            rest == null -> stringResource(R.string.rating_gap_span, name, stringResource(R.string.in_whole_hours, hours.toInt()))
            else -> stringResource(R.string.rating_gap_span, name, stringResource(R.string.in_hours, hours.toInt(), rest.toInt()))
        }
    }
    if (spare == null) return name
    val signed = if (spare >= 0) "+$spare" else "−${-spare}"
    return stringResource(R.string.rating_spare, name, signed)
}

private fun ratingColor(rating: ChangeRating): Color = when (rating) {
    ChangeRating.Comfortable -> Color(0xFF81C784)
    ChangeRating.Tight -> Color(0xFFE0E0E0)
    ChangeRating.Risky -> AMBER
    ChangeRating.VeryRisky -> Color(0xFFE57373)
}

/**
 * The way of a change or walk, north up: where it starts (hollow), where it leads (filled, in
 * the colour of the next line), the way on foot (solid when it is the real way; dashed and
 * marked "Luftlinie" while only the straight line is known, the real way fading in once the
 * phone sends it), and the user as a blue dot where the position is fresh and near. Under it
 * the map picture of the phone for the same view (Web Mercator, see [rememberMapImage]; in the
 * style of the setting, dark and dimmed in ambient mode); without the phone the sketch alone.
 */
@Composable
private fun ChangeMapView(map: ChangeMap, own: Position?, lineColor: Color, ambient: Boolean, modifier: Modifier, large: Boolean = false) {
    val context = LocalContext.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val pad = with(density) { (if (large) 36.dp else 12.dp).toPx() }
    val fit = changeMapFit(map, own?.let { it.latitude to it.longitude }, canvasSize.width, canvasSize.height, pad)
    val dark = rememberMapDark(ambient)
    val view = fit?.let {
        MapImageRequest.ofView(
            it.x,
            it.y,
            it.pixelsPerUnit,
            canvasSize.width,
            canvasSize.height,
            density.density.toDouble().coerceIn(1.0, 3.0),
            dark = dark,
        )
    }
    // Its size, so the pictures of the next changes can be asked for before it shows them (TripMapPrefetch).
    LaunchedEffect(canvasSize, large) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            WatchStore.saveChangeMapSize(context, large, canvasSize.width, canvasSize.height, pad.roundToInt())
        }
    }
    val mapImage = rememberMapImage(if (large) "change-full" else "change", view)
    val image = mapImage.image
    val onMap = image != null && image.frame.dark == dark
    val lightMap = onMap && !dark
    val way = when {
        ambient -> Color.White
        lightMap -> WAY_ON_MAP
        else -> MaterialTheme.colorScheme.primary
    }
    val ink = if (lightMap) MAP_INK else MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val attribution = stringResource(R.string.map_attribution)
    // The real way fades in over the straight line once it is there (not when it was there from the start).
    val routed by animateFloatAsState(if (map.path.isEmpty()) 0f else 1f, animationSpec = tween(WAY_FADE_MILLIS), label = "way")
    val straightLabel = stringResource(R.string.walk_straight_line)
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
            val at = fit ?: return@Canvas
            if (onMap && image != null) drawMapImage(image, at.x, at.y, at.pixelsPerUnit, alpha = if (ambient) AMBIENT_MAP_ALPHA else 1f)
            val from = map.fromLatitude to map.fromLongitude
            val to = map.toLatitude to map.toLongitude
            fun project(point: Pair<Double, Double>) = Offset(
                (size.width / 2 + (WebMercator.x(point.second) - at.x) * at.pixelsPerUnit).toFloat(),
                (size.height / 2 + (WebMercator.y(point.first) - at.y) * at.pixelsPerUnit).toFloat(),
            )
            fun lineOf(points: List<Pair<Double, Double>>) = Path().apply {
                points.forEachIndexed { index, point ->
                    val p = project(point)
                    if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            val width = (if (large) 4.dp else 3.dp).toPx()
            // The way on map: a casing, so it stands out from the streets (white on the light map, black on the dark).
            val casing = if (lightMap) Color.White else Color.Black.copy(alpha = 0.6f)
            fun drawWay(line: Path, alpha: Float, dashed: Boolean) {
                if (alpha <= 0.01f) return
                if (onMap && !ambient) {
                    drawPath(line, color = casing, alpha = alpha, style = Stroke(width = width + 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawPath(
                    line,
                    color = way,
                    alpha = alpha,
                    style = Stroke(
                        width = width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                    ),
                )
            }
            val straightAlpha = 1f - routed
            drawWay(lineOf(listOf(from, to)), straightAlpha, dashed = true)
            if (map.path.isNotEmpty()) drawWay(lineOf(map.path), routed, dashed = false)
            // While only the straight line is known: say so, small, beside its middle.
            val a = project(from)
            val b = project(to)
            val length = (b - a).getDistance()
            if (straightAlpha > 0.01f && length > STRAIGHT_LABEL_MIN.toPx()) {
                val label = textMeasurer.measure(
                    straightLabel,
                    TextStyle(color = if (lightMap) MAP_INK else Color.White, fontSize = if (large) 10.sp else 8.sp),
                )
                val middle = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                // Off the line to the side away from it, so the label does not hide the dashes.
                val normal = Offset(-(b.y - a.y) / length, (b.x - a.x) / length).let { if (it.y > 0) -it else it }
                val gap = label.size.height * 0.75f + 2.dp.toPx()
                val topLeft = Offset(
                    (middle.x + normal.x * gap - label.size.width / 2f).coerceIn(2f, (size.width - label.size.width - 2f).coerceAtLeast(2f)),
                    (middle.y + normal.y * gap - label.size.height / 2f).coerceIn(2f, (size.height - label.size.height - 2f).coerceAtLeast(2f)),
                )
                drawRoundRect(
                    color = if (lightMap) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(topLeft.x - 2.dp.toPx(), topLeft.y),
                    size = Size(label.size.width + 4.dp.toPx(), label.size.height.toFloat()),
                    cornerRadius = CornerRadius(label.size.height / 2f),
                    alpha = straightAlpha,
                )
                drawText(label, topLeft = topLeft, alpha = straightAlpha)
            }
            val dot = (if (large) 7.dp else 5.dp).toPx()
            if (lightMap && !ambient) drawCircle(Color.White, radius = dot + 1.dp.toPx(), center = a)
            drawCircle(ink, radius = dot, center = a, style = Stroke(width = 2.dp.toPx()))
            drawCircle(if (ambient) Color.White else lineColor, radius = dot + 1.dp.toPx(), center = b)
            drawCircle(Color.White, radius = dot + 1.dp.toPx(), center = b, style = Stroke(width = 1.5.dp.toPx()))
            at.user?.let {
                val p = project(it)
                drawCircle(Color.White, radius = dot + 1.dp.toPx(), center = p)
                drawCircle(if (ambient) Color.Black else OWN_BLUE, radius = dot - 0.5.dp.toPx(), center = p)
            }
            northMark(ink, large)
            if (onMap) {
                drawMapAttribution(textMeasurer.measureAttribution(attribution, dark = dark), dark = dark, corner = !large)
            }
        }
        // While the phone has sent no picture: how it is coming along, instead of a silent sketch.
        if (image == null && !ambient) {
            MapImageStatusLine(
                mapImage.status,
                modifier = if (large) {
                    Modifier.align(Alignment.TopCenter).padding(top = TOP_CLEARANCE + 4.dp)
                } else {
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                },
                onRetry = mapImage.retry,
            )
        }
    }
}

/** North is up: a small arrow in the upper right corner. */
private fun DrawScope.northMark(ink: Color, large: Boolean) {
    val pad = (if (large) 40.dp else 10.dp).toPx()
    val length = (if (large) 9.dp else 6.dp).toPx()
    val base = Offset(size.width - pad, pad + length)
    val tip = Offset(base.x, base.y - 2 * length)
    drawLine(ink, base, tip, strokeWidth = 1.5.dp.toPx())
    val head = Path().apply {
        moveTo(tip.x, tip.y - 1.dp.toPx())
        lineTo(tip.x - length / 2, tip.y + length / 1.5f)
        lineTo(tip.x + length / 2, tip.y + length / 1.5f)
        close()
    }
    drawPath(head, ink)
}

/** The map of a change over the whole screen, with the distance; a tap (or back) closes it. */
@Composable
private fun FullMap(view: TransferView, own: Position?, lineColor: Color, ambient: Boolean, onClose: () -> Unit) {
    val map = view.map ?: return
    val east = (map.toLongitude - map.fromLongitude) * METRES_PER_DEGREE * cos(Math.toRadians((map.fromLatitude + map.toLatitude) / 2))
    val north = (map.toLatitude - map.fromLatitude) * METRES_PER_DEGREE
    // The length of the way drawn and its minutes, both from the phone where it sent them;
    // else the straight line with the minutes of the step.
    val distance = map.meters ?: sqrt(east * east + north * north).roundToInt()
    val minutes = map.minutes ?: view.walkMinutes
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ChangeMapView(map, own, lineColor, ambient, modifier = Modifier.fillMaxSize(), large = true)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 18.dp)) {
            Text(
                view.ride?.let { platformText(it.platform, it.line) } ?: view.toName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            Text(
                if (minutes > 0) {
                    stringResource(if (view.cycling) R.string.cycle_distance else R.string.walk_distance, distance, minutes)
                } else {
                    stringResource(R.string.distance_m, distance)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The whole connection as a list: every ride with its line, from and to, times and
 * platforms; between them the walks and changes with their minutes and rating; the step of
 * now marked. At the foot "Missed it" and "End trip".
 */
@Composable
private fun OverviewPage(
    trip: TripSnapshot,
    active: Boolean,
    ambient: Boolean,
    replanning: Boolean,
    replanFailed: Boolean,
    onInteract: () -> Unit,
    onMissed: () -> Unit,
    onEnd: () -> Unit,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val now = rememberNow(if (ambient) 60_000 else 30_000, active)
    // Scrolling the list counts as looking at it: the page is held.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) onInteract() }
    }
    val plan = trip.plan
    val current = when {
        trip.planIndex >= 0 -> trip.planIndex
        trip.kind == StepKind.WalkToEnd || trip.kind == StepKind.Arrived -> plan.size
        else -> plan.indexOfFirst { it.arrival > now.value }.takeIf { it >= 0 } ?: plan.size
    }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus(active)) {
            item { ListHeader { Text(stringResource(R.string.overview)) } }
            if (plan.isEmpty()) {
                // An older phone app sends no plan: the step and the next ride.
                item { Text(stepText(trip), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center) }
                item { Note(untilText(trip.time, now.value)) }
                trip.next?.let { next ->
                    item {
                        Note(
                            stringResource(R.string.then_label) + ": " + (next.line ?: "") + " · " + next.stop + " · " +
                                stringResource(R.string.departs_at, clock(next.time), untilText(next.time, now.value)),
                        )
                    }
                }
                item { ProgressBar(trip.progress, modifier = Modifier.fillMaxWidth()) }
            } else {
                plan.forEachIndexed { index, ride ->
                    if (ride.walkBefore > 0 || ride.rating != null) {
                        item {
                            Connector(
                                minutes = ride.walkBefore,
                                cycling = ride.bikeBefore,
                                rating = ride.rating,
                                spare = ride.spare,
                                gap = ride.gap,
                                current = index == current && trip.kind == StepKind.Board,
                                ambient = ambient,
                            )
                        }
                    }
                    item { PlanRideRow(ride, current = index == current, past = index < current) }
                }
                if (trip.planWalkEnd > 0) {
                    item { Connector(trip.planWalkEnd, null, null, null, current = current >= plan.size, ambient = ambient, cycling = trip.planBikeEnd) }
                }
            }
            item {
                Text(
                    stringResource(R.string.arrival_at, clock(trip.arrival)) + " · " + trip.destination,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    fontWeight = if (current >= plan.size && plan.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trip.kind != StepKind.Arrived) {
                item {
                    SmallAction(
                        text = stringResource(if (replanning) R.string.loading else R.string.missed_it),
                        onClick = onMissed,
                        icon = R.drawable.ic_replan,
                    )
                }
                if (replanFailed) item { Note(stringResource(R.string.missed_it_failed)) }
            }
            // Ends the trip here and on the phone, whichever of the two follows it.
            item { SmallAction(text = stringResource(R.string.end_trip), onClick = onEnd, icon = R.drawable.ic_close) }
        }
    }
}

/** A walk or change between two rides: its minutes on foot (or [cycling], by bike) and its rating in colour. */
@Composable
private fun Connector(
    minutes: Int,
    rating: ChangeRating?,
    spare: Int?,
    gap: Int?,
    current: Boolean,
    ambient: Boolean,
    cycling: Boolean = false,
) {
    val parts = listOfNotNull(
        if (minutes > 0) stringResource(if (cycling) R.string.cycle_minutes else R.string.walk_on_foot, minutes) else null,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .background(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                )
            }
            rating?.let {
                Text(ratingText(it, spare, gap), style = MaterialTheme.typography.labelSmall, color = if (ambient) Color.White else ratingColor(it))
            }
        }
    }
}

/** One ride of the overview; the one of the step of now framed, those behind dim. */
@Composable
private fun PlanRideRow(ride: PlanRide, current: Boolean, past: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (past) 0.5f else 1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(ride.line, ride.product, color = ride.color)
            Spacer(Modifier.width(6.dp))
            Text(
                "${clock(ride.departure)} – ${clock(ride.arrival)}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        Text(
            "${ride.from} → ${ride.to}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // An arrow before the destination saves room; read out as "towards …".
        val platform = platformText(ride.departurePlatform, ride.line)
        val where = listOfNotNull(platform, ride.direction?.let { stringResource(R.string.towards_arrow, it) }).joinToString(" · ")
        val spokenWhere = listOfNotNull(platform, ride.direction?.let { stringResource(R.string.towards, it) }).joinToString(" · ")
        if (where.isNotEmpty()) {
            Text(where, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { contentDescription = spokenWhere })
        }
        if (ride.cancelled) {
            Text(stringResource(R.string.cancelled), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

// A swipe of the user holds the page this long before the watch turns to the fitting one again.
private const val HOLD_MILLIS = 60_000L

// The ring before departure shows the last minutes up to this many; before that it is full.
private const val RING_WINDOW_MILLIS = 10 * 60_000L

// From this far ahead, a clock time instead of minutes.
private const val FAR_MILLIS = 60 * 60_000L

// The dots on the line of stops on board, and the cars of the boarding sketch.
private const val MAX_DOTS = 5

// The ring of the ride page: its width and its distance from the edge of a round screen.
private val RIDE_RING_STROKE = 6.dp
private val RIDE_RING_EDGE = 3.dp

// The user's position on the change map: asked for this often, shown while this fresh (how near: see changeMapFit).
private const val OWN_POSITION_MILLIS = 15_000L
private const val OWN_FRESH_MILLIS = 2 * 60_000L
private const val PHONE_ASK_MILLIS = 60_000L

// The real way on foot fades in over the straight line this fast; "Luftlinie" only beside a line this long.
private const val WAY_FADE_MILLIS = 600
private val STRAIGHT_LABEL_MIN = 36.dp
private const val METRES_PER_DEGREE = 111_320.0

// Below this a way on foot is not worth a line (the two ends of one platform's stop positions).
private const val MIN_WALK_METRES = 40

// The room the time of the watch takes at the top, and the page indicator at the foot.
private val TOP_CLEARANCE = 34.dp
private val BOTTOM_CLEARANCE = 22.dp

private val GREEN = Color(0xFF4CAF50)
private val AMBER = Color(0xFFFFB300)

// The colours the arrival confetti always has besides the line: the lime and blue of Next Stop.
private val CONFETTI_LIME = Color(0xFFC8F16B)
private val CONFETTI_BLUE = Color(0xFF3D6AF2)
private val OWN_BLUE = Color(0xFF4FC3F7)

// On the light map of the phone: the way in a deep blue, arrow and start in dark grey; in
// ambient mode the dark map only faintly, so the screen stays mostly black.
private val WAY_ON_MAP = Color(0xFF1565C0)
private val MAP_INK = Color(0xFF37474F)
private const val AMBIENT_MAP_ALPHA = 0.35f
