package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.shownRideStopNames
import io.github.jnbp.nextstop.data.nextWalkKey
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.journeyLegsId
import io.github.jnbp.nextstop.data.keptAfterSwap
import io.github.jnbp.nextstop.data.legSwapKey
import io.github.jnbp.nextstop.data.swapsRest
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.StopAction
import io.github.jnbp.nextstop.data.Stopover
import io.github.jnbp.nextstop.data.stopTimeBetween
import androidx.compose.ui.text.AnnotatedString
import io.github.jnbp.nextstop.data.boardingTime
import io.github.jnbp.nextstop.data.alightingTime
import io.github.jnbp.nextstop.data.changeTime
import io.github.jnbp.nextstop.data.journeyChangesOf
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.plausible
import io.github.jnbp.nextstop.data.rapidLineName
import io.github.jnbp.nextstop.data.rideStopName
import io.github.jnbp.nextstop.data.rideTown
import io.github.jnbp.nextstop.data.shownRideStop
import io.github.jnbp.nextstop.ui.companion.CompanionLayout
import io.github.jnbp.nextstop.ui.companion.towards
import io.github.jnbp.nextstop.ui.theme.AppColors
import java.time.Duration
import io.github.jnbp.nextstop.data.tone
import io.github.jnbp.nextstop.data.railReplacement

/*
 * The body of the connection detail in the look of the Live-Fahrt (#129, the default; the old
 * body stays as "Klassisch", see DetailStyleSetting): every ride as a card with its badge, its
 * hints and its stops on a rail in the colour of the line, between them the changes with their
 * walk cards, minutes and connection pickers, and the walks at the start and the end, as the
 * calm Live-Fahrt shows them. The header of the page (times, share, menu, start) stays as it
 * is (Connections.kt). One lazy list (see detailItems): only what is in view is composed, and
 * the head of a ride stays pinned at the top while its stops scroll beneath it.
 * docs/detail-parity.md lists every element of the classic body and where it is here.
 */

/** "Verbindungsdetails" (#129): the style of the detail body, read by the page, stored in the settings. */
object DetailStyleSetting {
    var style by mutableStateOf(DetailStyle.Timeline)
        private set

    private var installed = false

    /** Reads the stored choice; once per process. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        style = DetailStyle.fromStored(prefs(context).getString(KEY, null))
    }

    /** Chooses and stores [value]. */
    fun change(context: Context, value: DetailStyle) {
        install(context)
        if (style == value) return
        style = value
        prefs(context).edit().putString(KEY, value.name).apply()
        DiagLog.i(LOG_TAG) { "style $value" }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    private const val KEY = "detailStyle"
    private const val LOG_TAG = "DetailStyle"
}

/**
 * The body of the detail of [journey] in the Live-Fahrt look; the parameters are those of
 * JourneyDetail. [top] holds the notices of the whole journey (the same as in the classic body).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TimelineJourneyBody(
    journey: Journey,
    stopsFailed: Boolean,
    onLoadStops: () -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
    offers: Map<String, Alternatives>,
    onAdapt: (Int, Journey) -> Unit,
    onCourse: (Leg) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    top: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    // Whether "Andere Wege" is offered at the changes: not in a mere preview, which cannot take one (#243).
    otherWays: Boolean = true,
) {
    val list = rememberLazyListState()
    // Another connection picked or another way taken swaps the rest of the journey: the
    // page stays where it is, the rows keep their places (keyed by position), those whose ride
    // changed crossfade and the rows below glide. [seen] is the journey of the last composition.
    val seen = remember { arrayOfNulls<Journey>(1) }
    val before = seen[0]
    val swapped = remember(journey.id) { swapsRest(before?.legs, journey.legs) }
    // Another journey is read from its top.
    LaunchedEffect(journey.id) {
        if (swapped) return@LaunchedEffect
        if (list.firstVisibleItemIndex != 0 || list.firstVisibleItemScrollOffset != 0) list.animateScrollToItem(0)
    }
    val legs = journey.legs
    // The rides with plausible times (an implausible prognosis shows as the app's estimate, "ca.").
    val shown = remember(journey) { legs.map { if (it.walking) it else it.plausible() } }
    // The open folds and pickers of the last journey, to keep those of rides a swap left alone.
    val seenOpen = remember { arrayOfNulls<List<Int>>(2) }
    // The rides that show all their stops, by leg index; another journey starts folded.
    val open = remember(journey.id) {
        mutableStateListOf<Int>().apply {
            if (swapped && before != null) addAll(keptAfterSwap(seenOpen[0].orEmpty(), before.legs, legs))
        }
    }
    // The connection pickers tapped by hand, by keepLegs: each turned from how the setting
    // "Anschlusswahl" shows it (B4, the same setting as the Live-Fahrt). The arriving ride of
    // a picker is the leg before keepLegs: a swap keeps the picker it was made at.
    val toggledPickers = remember(journey.id) {
        mutableStateListOf<Int>().apply {
            if (swapped && before != null) addAll(keptAfterSwap(seenOpen[1].orEmpty().map { it - 1 }, before.legs, legs).map { it + 1 })
        }
    }
    seen[0] = journey
    seenOpen[0] = open.toList()
    seenOpen[1] = toggledPickers.toList()
    val changes = remember(journey) { detailChanges(journey) }
    // One state for every body row, so a row recomposed without a change keeps its target.
    val swapState = remember(journey, shown, changes) { DetailSwapState(journey, shown, changes) }
    CompanionLayout.install(LocalContext.current)
    // While this journey is followed as the Live-Fahrt, its walks and changes that are over look
    // so here too; a journey only looked at shows nothing as over. No ticking: the page looks
    // again only when the next ride leaves.
    val tripJourney by TripState.journey.collectAsState()
    val followed = tripJourney?.id == journey.id
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val departures = remember(journey) { legs.filter { !it.walking }.map { it.departure.toInstant().toEpochMilli() } }
    LaunchedEffect(followed, departures) {
        if (!followed) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            val wake = detailNextWake(departures, now) ?: break
            delay(wake - now + DONE_WAKE_SLACK_MILLIS)
        }
    }
    val overBefore: (Int) -> Boolean = { rideIndex -> detailPartOver(followed, legs.getOrNull(rideIndex)?.departure?.toInstant()?.toEpochMilli(), now) }
    val openNow = open.toList()
    val plan = remember(journey, shown, openNow, stopsFailed) {
        detailItems(
            walking = legs.map { it.walking },
            walkMinutes = legs.map { Duration.between(it.departure, it.arrival).toMinutes() },
            stayAfter = journey.via?.takeIf { it.minutes > 0 }?.afterLegs,
        ) { index ->
            val leg = shown[index]
            val known = leg.stopovers.size >= 2
            detailStopRows(if (known) leg.stopovers.size else 2, known, index in openNow, stopsFailed)
        }
    }
    // "Nur nächste ausgeklappt" (#202): on the detail page the first walk (or change) with a map.
    val nextWalk = remember(journey, plan, OsmStations.loaded) {
        nextWalkKey(plan, key = { it.key }, hasMap = { item ->
            when (item) {
                is DetailItem.Walk -> hasWalkMap(legs[item.legIndex])
                is DetailItem.Change -> changeHasMiniMap(legs[item.arrivingIndex], legs[item.nextIndex], item.walkIndex?.let { legs[it] })
                else -> false
            }
        })
    }
    // #250: the stops of a ride are rows of the lazy list of their own, so folding them in or
    // out adds or takes away items; without item animations they jumped. They fade in and out,
    // and while a fold settles the rows below glide to their new places. Only then: a glide on
    // every relayout would make the rows chase a card that grows softly (animateContentSize).
    val animate = rememberAnimationsOn()
    // A swap glides the rows below as a fold does.
    var foldGlide by remember { mutableStateOf(false) }
    val swapGlide = remember(journey.id) { mutableStateOf(swapped && animate) }
    LaunchedEffect(journey.id) {
        if (!swapGlide.value) return@LaunchedEffect
        delay(FOLD_GLIDE_MILLIS)
        swapGlide.value = false
    }
    val glide = foldGlide || swapGlide.value
    var foldToggles by remember { mutableIntStateOf(0) }
    LaunchedEffect(foldToggles) {
        if (foldToggles == 0) return@LaunchedEffect
        delay(FOLD_GLIDE_MILLIS)
        foldGlide = false
    }
    val toggle: (Int) -> Unit = { index ->
        if (animate) {
            foldGlide = true
            foldToggles++
        }
        if (index in open) {
            open.remove(index)
        } else {
            open.add(index)
            // The search leaves the stops on the way out; they are fetched when a ride is unfolded.
            if (legs[index].stopovers.isEmpty()) onLoadStops()
        }
    }
    val pinsHeads = legs.any { !it.walking }
    // One form of the sun chip on the whole page (#236).
    val sunChipPage = remember(journey.id) { io.github.jnbp.nextstop.data.SunChipPage() }
    // The small maps open larger in place and the walk cards unfold them, as in the calm Live-Fahrt.
    CompositionLocalProvider(LocalMiniMapInPlace provides true, LocalWalkCardStyle provides WalkCardStyle.Calm, LocalSunChipPage provides sunChipPage) {
        LazyColumn(
            state = list,
            modifier = modifier
                // A pinned head is not faded away at the top; the foot fades as the classic body does.
                .fadeEdges(horizontal = false, cutAtStart = { !pinsHeads && list.canScrollBackward }, cutAtEnd = { list.canScrollForward }),
            // Generous room below, so the last line scrolls clear of the rounded edge.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            plan.forEach { entry ->
                when (entry) {
                    // The line stays in view while its stops scroll (#129): pinned until the next ride's head pushes it.
                    is DetailItem.RideHead -> stickyHeader(key = entry.key, contentType = HEAD_CONTENT_TYPE) {
                        // A head below a fold glides with the rows (#250); it never fades.
                        Box(modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = if (animate && glide) Motion.settle() else null, fadeOutSpec = null)) {
                        val ground = rememberHeadPush(list, entry.key, HEAD_CONTENT_TYPE, AppColors.Card, CARD_RADIUS)
                        // Another ride at this place after a swap crossfades into it.
                        FadeSwap(target = shown[entry.legIndex], contentKey = { leg -> journeyLegsId(listOf(leg)) }) { leg ->
                        DetailRideHead(
                            // The next ride's head pushes this one up with a gap, its bottom corners rounding (#251).
                            ground = ground,
                            leg = leg,
                            open = entry.legIndex in openNow,
                            onToggle = { toggle(entry.legIndex) },
                            onCourse = if (legs[entry.legIndex].tripId == null) null else ({ onCourse(legs[entry.legIndex]) }),
                        )
                        }
                        }
                    }
                    else -> item(key = entry.key, contentType = contentTypeOf(entry)) {
                        // What scrolls beneath a pinned head fades out before it is behind it (#238).
                        val fade = if (pinsHeads) rememberHeadFade(list, entry.key, HEAD_CONTENT_TYPE) else Modifier
                        CompositionLocalProvider(LocalWalkMapNext provides (entry.key == nextWalk)) {
                        // Stops folded in and out fade, the rows below glide while the fold settles (#250).
                        val moves = if (animate) {
                            Modifier.animateItem(fadeInSpec = Motion.fadeIn(), placementSpec = if (glide) Motion.settle() else null, fadeOutSpec = Motion.fadeOut())
                        } else {
                            Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                        }
                        Box(modifier = moves.then(fade)) {
                        // Another ride (or walk) at this place after a swap crossfades into it, the
                        // old one drawn from its own journey; changes and the rest update in place.
                        FadeSwap(target = swapState, contentKey = { state -> detailSwapKey(entry, state.journey.legs) }) { state ->
                        DetailBodyItem(
                            item = entry,
                            journey = state.journey,
                            shown = state.shown,
                            openNow = openNow,
                            changes = state.changes,
                            offers = offers,
                            toggledPickers = toggledPickers,
                            overBefore = overBefore,
                            toggle = toggle,
                            onStopAction = onStopAction,
                            onAdapt = onAdapt,
                            onCourse = onCourse,
                            onFocusWalk = onFocusWalk,
                            top = top,
                            otherWays = otherWays,
                        )
                        }
                        }
                        }
                    }
                }
            }
        }
    }
}

/** What a body row of the detail page draws, kept whole for the row that crossfades away. */
private class DetailSwapState(val journey: Journey, val shown: List<Leg>, val changes: List<TripChange>)

/**
 * Which ride (or walk) a body row of the detail page shows; another one after a swap
 * crossfades. A change, a stay and the top keep one key: they update in place (the picker the
 * user just tapped is in a change).
 */
private fun detailSwapKey(item: DetailItem, legs: List<Leg>): String = when (item) {
    is DetailItem.Walk -> legSwapKey(legs, item.legIndex)
    is DetailItem.RidePre -> legSwapKey(legs, item.legIndex)
    is DetailItem.RideInfo -> legSwapKey(legs, item.legIndex)
    is DetailItem.RideStop -> legSwapKey(legs, item.legIndex)
    is DetailItem.RideFoot -> legSwapKey(legs, item.legIndex)
    else -> ""
}

// How long after a fold of the stops the rows below glide to their places (#250): the settle spring's time.
private const val FOLD_GLIDE_MILLIS = 600L

// The content type of the ride heads, which stay pinned (#129) and fade what scrolls beneath them (#238).
private const val HEAD_CONTENT_TYPE = "head"

/** One item of the lazy body other than a ride's head (see [DetailItem]). */
@Composable
private fun DetailBodyItem(
    item: DetailItem,
    journey: Journey,
    shown: List<Leg>,
    openNow: List<Int>,
    changes: List<TripChange>,
    offers: Map<String, Alternatives>,
    toggledPickers: SnapshotStateList<Int>,
    overBefore: (Int) -> Boolean,
    toggle: (Int) -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
    onAdapt: (Int, Journey) -> Unit,
    onCourse: (Leg) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    top: @Composable () -> Unit,
    otherWays: Boolean,
) {
    val legs = journey.legs
    when (item) {
        DetailItem.Top -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { top() }
        is DetailItem.Stay -> journey.via?.let { stay -> DetailStayRow(stay.minutes, stay.place.name.plainStopName()) }
        is DetailItem.Walk -> Column {
            // A walk at the start is over once the first ride left (only while followed).
            val firstRide = legs.indexOfFirst { !it.walking }
            CompanionWalkPart(
                journey = journey,
                index = item.legIndex,
                over = firstRide > item.legIndex && overBefore(firstRide),
                // Before the trip Google Maps opens without ending anything (#143).
                endsTrip = false,
                onFocusWalk = onFocusWalk,
                maps = true,
                detour = null,
                modifier = Modifier.padding(start = CONNECTOR_INDENT, top = ITEM_GAP),
            )
            // The bike combo: the length of the bike leg, as the classic detail says it.
            val leg = legs[item.legIndex]
            val meters = leg.walkMeters
            if (leg.cycling && meters != null) {
                Text(
                    text = stringResource(R.string.cycle_distance, String.format(java.util.Locale.getDefault(), "%.1f", meters / 1000.0)),
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = CONNECTOR_INDENT + PART_RAIL + 14.dp),
                )
            }
        }
        is DetailItem.RidePre -> DetailRidePre(journey, item.legIndex, onFocusWalk)
        is DetailItem.RideInfo -> DetailRideInfo(journey, item.legIndex, shown[item.legIndex])
        is DetailItem.RideStop -> DetailStopItem(
            leg = shown[item.legIndex],
            row = item.row,
            first = item.first,
            last = item.last,
            onUnfold = { toggle(item.legIndex) },
            onStopAction = onStopAction,
        )
        is DetailItem.RideFoot -> DetailRideFoot(
            open = item.legIndex in openNow,
            onCourse = if (legs[item.legIndex].tripId == null) null else ({ onCourse(legs[item.legIndex]) }),
        )
        is DetailItem.Change -> DetailChangeItem(
            journey = journey,
            item = item,
            change = changes.firstOrNull { it.keepLegs == item.keepLegs },
            offers = offers,
            onAdapt = onAdapt,
            onFocusWalk = onFocusWalk,
            toggled = toggledPickers,
            firstKeepLegs = changes.firstOrNull()?.keepLegs,
            over = overBefore(item.nextIndex),
            otherWays = otherWays,
        )
        // What the colours of the times mean and where the data comes from (#277b).
        DetailItem.Foot -> TimesFooter(journey, modifier = Modifier.padding(top = 12.dp))
        is DetailItem.RideHead -> Unit
    }
}

// Items of one kind share their compositions while scrolling.
private fun contentTypeOf(item: DetailItem): String = when (item) {
    is DetailItem.RideStop -> "stop"
    is DetailItem.Change -> "change"
    is DetailItem.Walk -> "walk"
    is DetailItem.RideInfo -> "info"
    is DetailItem.RideFoot -> "foot"
    is DetailItem.RidePre -> "pre"
    else -> item.key
}

/**
 * Every change of [journey] for the timeline: as journeyChanges, also where the trip ends at
 * no stop (the classic detail shows those changes too), with the walk counted into its time.
 */
private fun detailChanges(journey: Journey): List<TripChange> {
    val legs = journey.legs
    val rated = journeyChangesOf(journey).associateBy { it.nextIndex }
    val rides = legs.indices.filter { !legs[it].walking }
    return rides.zipWithNext().mapNotNull { (arrivingIndex, nextIndex) ->
        val stop = legs[arrivingIndex].toPlace ?: legs[nextIndex].fromPlace ?: return@mapNotNull null
        val walk = legs.subList(arrivingIndex + 1, nextIndex).firstOrNull { it.walking }
        val walkMinutes = walk?.let { Duration.between(it.departure, it.arrival).toMinutes() } ?: 0L
        TripChange(
            stop = stop,
            keepLegs = arrivingIndex + 1,
            arriving = legs[arrivingIndex],
            next = legs[nextIndex],
            walk = walk,
            rating = rated[nextIndex]?.tightness,
            time = rated[nextIndex]?.time ?: changeTime(legs[arrivingIndex], legs[nextIndex], walkMinutes),
            journeyArrival = journey.arrival,
        )
    }
}

// The parts between the cards draw their rail at half of their own rail column; moved in this far, it lines up with the rail of the stops.
private val CONNECTOR_INDENT = 6.dp

// The page looks again this long after a ride of a followed journey left.
private const val DONE_WAKE_SLACK_MILLIS = 500L

// The gap above every part outside the cards and above every card.
private val ITEM_GAP = 8.dp

// The corner of a ride's card, as in the Live-Fahrt.
private val CARD_RADIUS = 24.dp

// The column on the left that holds the rail, and where the rail runs in it.
private val RAIL_COLUMN = 40.dp
private val RAIL_X = 20.dp

// Every stop takes at least this height.
private val STOP_ROW = 38.dp

// The small links under a change line up with its text.
private val LINK_INDENT = 52.dp

// The rail column of the parts between the cards (as the walk and change parts of the Live-Fahrt).
private val PART_RAIL = 52.dp

/** A part of a ride's card: its ground, rounded at the top for the head and at the bottom for the foot. */
private fun Modifier.cardPart(top: Boolean = false, bottom: Boolean = false): Modifier = this
    .fillMaxWidth()
    .background(
        AppColors.Card,
        RoundedCornerShape(
            topStart = if (top) CARD_RADIUS else 0.dp,
            topEnd = if (top) CARD_RADIUS else 0.dp,
            bottomStart = if (bottom) CARD_RADIUS else 0.dp,
            bottomEnd = if (bottom) CARD_RADIUS else 0.dp,
        ),
    )
    .padding(start = 12.dp, end = 14.dp)

/** The stay at a place on the way ("20 min Aufenthalt in …") on the dotted rail between the cards. */
@Composable
private fun DetailStayRow(minutes: Int, place: String) {
    val density = LocalDensity.current
    val dots = remember(density) { PathEffect.dashPathEffect(floatArrayOf(0.1f, with(density) { 7.dp.toPx() }), 0f) }
    val blue = AppColors.Blue
    Box(
        modifier = Modifier
            .padding(start = CONNECTOR_INDENT, top = ITEM_GAP)
            .fillMaxWidth()
            .drawBehind {
                val x = PART_RAIL.toPx() / 2f
                drawLine(blue, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
            }
            .padding(start = PART_RAIL, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.via_stay_row, minutes, place),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

/**
 * Before a ride's card, outside it: at the first ride, the way inside the station to its
 * platform (#87, where no walk comes before) and the way to one of many stop positions.
 */
@Composable
private fun DetailRidePre(journey: Journey, index: Int, onFocusWalk: (Leg) -> Unit) {
    val legs = journey.legs
    val leg = legs[index]
    Column(modifier = Modifier.padding(start = CONNECTOR_INDENT)) {
        if (index == legs.indexOfFirst { !it.walking } && legs.take(index).none { it.walking }) {
            PlatformWalkPart(leg, onFocusWalk, maps = true)
        }
        // A bus or tram from a stop of many positions at the very start: the way to its position.
        if (index == 0) CompanionPositionWalkPart(leg, onFocusWalk)
        Spacer(modifier = Modifier.height(ITEM_GAP))
    }
}

/**
 * The head of a ride's card: its badge, where it goes and, below, when and from which platform
 * it leaves, when it arrives and how long it takes, with the whole train name where the badge
 * leaves its number out. A tap unfolds or folds the stops (the chevron says which); a long
 * press opens the course of the line, which the foot also offers as a link while unfolded.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailRideHead(ground: Modifier, leg: Leg, open: Boolean, onToggle: () -> Unit, onCourse: (() -> Unit)?) {
    var menuOpen by remember { mutableStateOf(false) }
    val headLabel = stringResource(if (open) R.string.stops_hide else R.string.stops_show)
    val headState = stringResource(if (open) R.string.companion_stops_open else R.string.companion_stops_folded)
    val courseLabel = stringResource(R.string.line_course)
    // The ground of the card's top part as cardPart(top = true) draws it, drawn by [ground] (#251).
    Box(modifier = Modifier.fillMaxWidth().then(ground).padding(start = 12.dp, end = 14.dp).padding(top = 12.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The ripple's corner is the badge's, as in the Live-Fahrt (#121).
                .clip(RoundedCornerShape(LINE_BADGE_CORNER))
                .semantics { stateDescription = headState }
                .combinedClickable(
                    onClickLabel = headLabel,
                    role = Role.Button,
                    onLongClickLabel = if (onCourse == null) null else courseLabel,
                    onLongClick = if (onCourse == null) null else ({ menuOpen = true }),
                    onClick = onToggle,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A rail replacement: the calm badge of the line it stands in for, with "SEV".
            val replacement = leg.railReplacement
            if (replacement != null) PlainReplacementBadge(leg, replacement, minWidth = 46.dp) else LineBadge(leg)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                val spoken = leg.direction?.let { stringResource(R.string.direction, shownRideStop(leg, it)) }
                Text(
                    text = towards(leg),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (spoken == null) Modifier else Modifier.semantics { contentDescription = spoken },
                )
                // The whole name with the train number ("FEX 021857") where the badge leaves it out;
                // then ab · Gleis · an · Dauer, as the Live-Fahrt (#92).
                val whole = if (replacement != null) null else fullLineName(leg.line)
                val duration = durationLabel(minutesBetween(leg.departure, leg.arrival).coerceAtLeast(0))
                Text(
                    // The times as the delay setting says (#227).
                    text = joinedText(
                        listOfNotNull(
                            whole?.let { AnnotatedString(it) },
                            delayedTimeText(R.string.companion_departs_at, boardingTime(leg), AppColors.Muted),
                            platformLabel(leg.departurePlatform, leg.product)?.let { AnnotatedString(it) },
                            delayedTimeText(R.string.arrival_at, alightingTime(leg), AppColors.Muted),
                            AnnotatedString(duration),
                        ),
                    ),
                    color = AppColors.Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Chevron(open = open, modifier = Modifier.padding(start = 6.dp))
        }
        if (onCourse != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(text = courseLabel) },
                    onClick = {
                        menuOpen = false
                        onCourse()
                    },
                )
            }
        }
    }
}

/**
 * The hints of a ride under its head, in its card: cancelled and rail replacement in words,
 * occupancy, bicycles, step-free and the sunny side, where to board for the change after it
 * (or the way out at the destination), and a vehicle that goes on as another line. [own] is
 * the journey's own leg at [index]; [leg] its copy with plausible times.
 */
@Composable
private fun DetailRideInfo(journey: Journey, index: Int, leg: Leg) {
    val own = journey.legs[index]
    Column(
        modifier = Modifier.cardPart().padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leg.cancelled) {
            Text(
                text = stringResource(R.string.cancelled_ride_tag),
                color = AppColors.OnDelayRed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.DelayRed)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        leg.railReplacement?.let { RailReplacementTag(it) }
        // Where to board this ride for a short change to the next one; the last ride to a place: the way out.
        // One row with occupancy, step-free, bicycles and the sunny side as small chips (#220).
        val following = remember(journey, own) { rideAfter(journey, own) }
        CompactRideHints(own) { hintModifier ->
            if (following != null) {
                CarriageHint(arriving = own, next = following, modifier = hintModifier)
            } else if (own === journey.rides.lastOrNull() && journey.legs.last().walking && journey.legs.last().toPlace?.kind != PlaceKind.Stop) {
                DestinationHint(journey = journey, ride = own, modifier = hintModifier)
            }
        }
        // One vehicle that changes its line on the way: no change, only a note.
        own.throughRuns.lastOrNull { it.lineChanges }?.let { run ->
            Text(
                text = stringResource(
                    R.string.stay_seated_continues_as,
                    rapidLineName(run.line, own.product) ?: run.line.orEmpty(),
                    run.atName.plainStopName(),
                ),
                color = AppColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * One row of a ride's stops on the rail in the colour of the line (grey for a cancelled ride):
 * a stop with its name, platform and time, folded stops ("3 weitere Halte ▾"), or a note in
 * their place (loading, failed, none). The rail of each row is drawn by the row itself, so the
 * rows can come and go one by one.
 */
@Composable
private fun DetailStopItem(
    leg: Leg,
    row: DetailStopRow,
    first: Boolean,
    last: Boolean,
    onUnfold: () -> Unit,
    onStopAction: (Stop, StopAction) -> Unit,
) {
    val color = if (leg.cancelled) AppColors.Rail else lineColor(leg)
    val card = AppColors.Card
    val lime = AppColors.Lime
    val ink = AppColors.Ink
    val stops = if (leg.stopovers.size >= 2) leg.stopovers else null
    val lastIndex = (stops?.size ?: 2) - 1
    val isEnd = row is DetailStopRow.Stop && (row.index == 0 || row.index == lastIndex)
    val isDestination = row is DetailStopRow.Stop && row.index == lastIndex
    Box(
        modifier = Modifier
            .cardPart()
            .heightIn(min = STOP_ROW)
            .drawBehind { drawStopRail(color, card, lime, ink, row, first, last, isEnd, isDestination) },
    ) {
        when (row) {
            is DetailStopRow.Stop -> DetailStopLine(leg, stops, row.index, lastIndex, onStopAction)
            is DetailStopRow.Folded -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = STOP_ROW)
                    .clickable(onClick = onUnfold)
                    .padding(start = RAIL_COLUMN),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.companion_more_stops, row.count, row.count) + " ▾",
                    color = AppColors.Blue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
            is DetailStopRow.Note -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = STOP_ROW)
                    .padding(start = RAIL_COLUMN),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (row.kind) {
                    StopsNote.Loading -> LoadingRow(text = stringResource(R.string.stops_loading), compact = true)
                    StopsNote.Failed -> Text(text = stringResource(R.string.stops_failed), color = AppColors.Muted, fontSize = 13.sp)
                    StopsNote.None -> Text(text = stringResource(R.string.stops_none), color = AppColors.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

/** The rail of one stop row: the line through it (from its middle at the first and the last row) and its node. */
private fun DrawScope.drawStopRail(
    color: Color,
    card: Color,
    lime: Color,
    ink: Color,
    row: DetailStopRow,
    first: Boolean,
    last: Boolean,
    end: Boolean,
    destination: Boolean,
) {
    val x = RAIL_X.toPx()
    val y = size.height / 2f
    val stroke = 5.dp.toPx()
    drawLine(color, Offset(x, if (first) y else 0f), Offset(x, if (last) y else size.height), strokeWidth = stroke, cap = if (first || last) StrokeCap.Round else StrokeCap.Butt)
    when (row) {
        is DetailStopRow.Folded -> for (n in -1..1) drawCircle(card, radius = 2.dp.toPx(), center = Offset(x, y + n * 6.dp.toPx()))
        is DetailStopRow.Stop -> if (destination) {
            drawCircle(lime, radius = 8.dp.toPx(), center = Offset(x, y))
            drawCircle(ink, radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
        } else {
            val radius = if (end) 7.dp.toPx() else 5.dp.toPx()
            drawCircle(card, radius = radius, center = Offset(x, y))
            drawCircle(color, radius = radius, center = Offset(x, y), style = Stroke(width = 2.5.dp.toPx()))
        }
        is DetailStopRow.Note -> Unit
    }
}

/**
 * A stop of a ride: its name (with its town where it lies in another, railway names on a
 * long-distance ride), the platform at both ends (a moved one marked as in the classic detail),
 * the time (live, with the delay or the planned time as the delay setting says, "ca." for an
 * estimate) and the menu of the stop, behind a tap on the row and its dots.
 */
@Composable
private fun DetailStopLine(leg: Leg, stops: List<Stopover>?, index: Int, lastIndex: Int, onStopAction: (Stop, StopAction) -> Unit) {
    val boarding = index == 0
    val destination = index == lastIndex
    val stop = stops?.getOrNull(index)
    val town = leg.rideTown
    // Never two different stops alike in the list (#278).
    val shownNames = remember(leg, stops) { stops?.let { shownRideStopNames(leg, it, town) } }
    val name = when {
        shownNames != null && index in shownNames.indices -> shownNames[index]
        boarding -> rideStopName(leg, leg.from, town)
        destination -> rideStopName(leg, leg.to, town)
        else -> rideStopName(leg, stop?.name.orEmpty(), town)
    }
    val place = when {
        boarding -> leg.fromPlace ?: stop?.place
        destination -> leg.toPlace ?: stop?.place
        else -> stop?.place
    }
    var menuOpen by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.stop_menu)
    // The ends keep their ⋮; the stops between open their menu on a tap or a long press on the row (#186).
    val dots = stopMenuOpening(index, lastIndex) == StopMenuOpening.Dots
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = STOP_ROW)
            .then(
                when {
                    place == null -> Modifier
                    dots -> Modifier.clickable(onClickLabel = menuLabel, role = Role.Button) { menuOpen = true }
                    else -> Modifier.rowMenu(menuLabel) { menuOpen = true }
                },
            )
            .padding(start = RAIL_COLUMN, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val end = boarding || destination
        Text(
            text = name,
            color = AppColors.Ink,
            fontWeight = if (destination) FontWeight.ExtraBold else if (end) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (end) 16.sp else 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val platform = when {
            boarding -> platformLabel(leg.departurePlatform, leg.product)
            destination -> platformLabel(leg.arrivalPlatform, leg.product)
            else -> null
        }
        if (platform != null) {
            // A platform other than the timetable's: marked like a delay.
            val moved = if (boarding) {
                platformMoved(leg.departurePlatform, leg.plannedDeparturePlatform)
            } else {
                platformMoved(leg.arrivalPlatform, leg.plannedArrivalPlatform)
            }
            Text(
                text = platform,
                color = if (moved) AppColors.AmberInk else AppColors.Muted,
                fontWeight = if (moved) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .then(
                        if (moved) {
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.AmberContainer)
                                .padding(horizontal = 5.dp)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        // One time column in every row, tabular figures: the platforms stand on one line (#237).
        StopTimeColumn(modifier = Modifier.padding(start = 4.dp)) {
            when {
                boarding -> LegTime(leg.departure, leg.plannedDeparture, leg.departureDelayMinutes, leg.realtime)
                destination -> LegTime(leg.arrival, leg.plannedArrival, leg.arrivalDelayMinutes, leg.realtime, estimated = leg.arrivalEstimated)
                // A stop between: its departure with the delay of that time (#226), as the delay setting says (#227).
                else -> stop?.let { stopTimeBetween(it) }?.let { time -> DelayedTime(time = time) }
            }
        }
        if (place != null && dots) {
            Box {
                MoreDots(label = menuLabel, onClick = { menuOpen = true }, size = STOP_DOTS_ROOM)
                StopMenu(
                    open = menuOpen,
                    onDismiss = { menuOpen = false },
                    onAction = { action -> onStopAction(place, action) },
                    place = place,
                )
            }
        } else {
            // No dots: the menu of a stop between still opens at the end of its row; the room of
            // the dots stays, so its time stands in the same column as at the ends (#237).
            Box(modifier = Modifier.width(STOP_DOTS_ROOM)) {
                if (place != null) {
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

// The room of the ⋮ at the end of a stop row, kept in the rows without it (#237).
private val STOP_DOTS_ROOM = 32.dp

/** The foot of a ride's card: while unfolded, the link to the whole course of the line. */
@Composable
private fun DetailRideFoot(open: Boolean, onCourse: (() -> Unit)?) {
    Box(modifier = Modifier.cardPart(bottom = true).padding(start = RAIL_COLUMN - 6.dp, bottom = 10.dp)) {
        if (open && onCourse != null) {
            Text(
                text = stringResource(R.string.line_course) + " ›",
                color = AppColors.Blue,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onCourse)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * A change between two rides, as the Live-Fahrt shows it (the blue card of BlueChangeEntry:
 * the minutes, the rating as a chip, the step from platform to platform, the walk with its map
 * and Google Maps, the same platform), then under it the chips "Anschluss" and "Andere Wege" with
 * their shared row of tiles (#243), and the other stops where the very same rides can be changed.
 */
@Composable
private fun DetailChangeItem(
    journey: Journey,
    item: DetailItem.Change,
    change: TripChange?,
    offers: Map<String, Alternatives>,
    onAdapt: (Int, Journey) -> Unit,
    onFocusWalk: (Leg) -> Unit,
    toggled: SnapshotStateList<Int>,
    firstKeepLegs: Int?,
    over: Boolean,
    otherWays: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ITEM_GAP)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (change == null) {
            // No place for the change known: the minutes and the platform hint, as the classic detail says them.
            val arriving = journey.legs[item.arrivingIndex]
            val next = journey.legs[item.nextIndex]
            val walk = item.walkIndex?.let { journey.legs[it] }
            TransferRow(changeTime(arriving, next, walk?.let { minutesBetween(it.departure, it.arrival) } ?: 0L))
            ChangeHint(arriving = arriving, next = next, modifier = Modifier.padding(start = LINK_INDENT))
        } else {
            val options = connectionOptions(change, offers[offerKey(journey.id, change.keepLegs)])
            // Switched off in the settings: no choice and no chip for it.
            val canPick = connectionChoiceOn.value && options.size >= 2 && !over
            val mode = CompanionLayout.pickers
            val open = canPick && detailPickerOpen(mode, change.keepLegs, firstKeepLegs, change.keepLegs in toggled)
            // Other ways from this change on, as in the Live-Fahrt (#243); taken, they adapt the journey shown.
            // Not in a preview (#120, the preview of another way): it has nothing to adapt.
            val ways = otherWays && !over
            // The blue change card (the rating once, as its chip); under it, on the page ground and
            // in line with it, the chips "Anschluss" and "Andere Wege" and their shared row of tiles (#243).
            CompanionChangePart(
                journey = journey,
                change = change,
                courses = emptyMap(),
                offer = null,
                over = over,
                onFocusWalk = onFocusWalk,
                onSwitch = {},
                maps = true,
                detour = null,
                modifier = Modifier.padding(start = CONNECTOR_INDENT),
                below = if (!canPick && !ways) {
                    null
                } else {
                    {
                        ChangeChoiceArea(
                            journey = journey,
                            change = change,
                            now = java.time.OffsetDateTime.now(),
                            connection = if (!canPick) {
                                null
                            } else {
                                ConnectionChipState(
                                    on = open,
                                    // A toggle in every setting, "Alle ausgeklappt" too: it only starts open (r7plan).
                                    onToggle = { if (change.keepLegs in toggled) toggled.remove(change.keepLegs) else toggled.add(change.keepLegs) },
                                )
                            },
                            ways = ways,
                            onTakeWay = { option -> onAdapt(change.keepLegs, option) },
                        ) { TransferChoice(journey.id, change, offers, onAdapt) }
                    }
                },
            )
        }
        AltChangeOffers(journey, item.nextIndex, onAdapt)
    }
}

/** "Verbindungsdetails" (#129) in Settings › Appearance: the new look (default) or the classic one, with a small picture of each. */
@Composable
fun DetailStyleSettings() {
    val context = LocalContext.current
    DetailStyleSetting.install(context)
    val style = DetailStyleSetting.style
    val all = DetailStyle.entries
    val labels = all.map { stringResource(detailStyleLabel(it)) }
    SettingsGroup(label = null) {
        SettingsRow(
            shape = groupShape(0, 1),
            title = stringResource(R.string.r6d_detail_style_title),
            text = stringResource(R.string.r6d_detail_style_text),
            preview = {
                SketchOptions(labels = labels, selected = all.indexOf(style), height = 104.dp) { index, reveal ->
                    detailStyleSketch(all[index], reveal)
                }
            },
            // A tap on the picture switches to the other style, a tap on one of them picks it.
            previewTap = { DetailStyleSetting.change(context, all[(all.indexOf(style) + 1) % all.size]) },
            previewPick = { index -> DetailStyleSetting.change(context, all[index]) },
        ) {
            SegmentedToggle(
                labels = labels,
                selected = all.indexOf(style),
                onSelect = { index -> DetailStyleSetting.change(context, all[index]) },
                minHeight = 40.dp,
                ground = AppColors.Chip,
            )
            FadeSwap(target = style) { shown ->
                Text(
                    text = stringResource(detailStyleHint(shown)),
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

private fun detailStyleLabel(style: DetailStyle): Int = when (style) {
    DetailStyle.Timeline -> R.string.r6d_detail_style_timeline
    DetailStyle.Classic -> R.string.r6d_detail_style_classic
}

private fun detailStyleHint(style: DetailStyle): Int = when (style) {
    DetailStyle.Timeline -> R.string.r6d_detail_style_timeline_text
    DetailStyle.Classic -> R.string.r6d_detail_style_classic_text
}

/**
 * The picture of a style: the head of the page on top in both, then two ride cards with a rail
 * of stops and a change between them (new), or times on the left and one rail per ride
 * (classic). [reveal] lets the cards settle in as the style is picked.
 */
private fun DrawScope.detailStyleSketch(style: DetailStyle, reveal: Float) {
    val w = size.width
    val h = size.height
    val pad = 8.dp.toPx()
    val settle = 0.85f + 0.15f * reveal.coerceIn(0f, 1f)
    block(AppColors.PlainGround, 0f, 0f, w, h, radius = 0f)
    // The head of the page: the times and the start button, the same in both.
    block(AppColors.Ink, pad, pad, w * 0.5f, 5.dp.toPx(), radius = 2.dp.toPx(), alpha = 0.8f)
    block(AppColors.Blue, w - pad - w * 0.22f, pad - 1.dp.toPx(), w * 0.22f, 8.dp.toPx(), radius = 4.dp.toPx())
    val top = pad + 14.dp.toPx()
    val room = h - top - pad
    when (style) {
        DetailStyle.Timeline -> {
            val cardH = room * 0.42f * settle
            listOf(top, top + room * 0.56f).forEachIndexed { k, y ->
                block(AppColors.Card, pad, y, w - 2 * pad, cardH, radius = 8.dp.toPx())
                block(SketchLines[k], pad + 5.dp.toPx(), y + 4.dp.toPx(), 14.dp.toPx(), 7.dp.toPx(), radius = 2.dp.toPx())
                block(SketchLines[k], pad + 10.dp.toPx(), y + 14.dp.toPx(), 3.dp.toPx(), (cardH - 18.dp.toPx()).coerceAtLeast(2f), radius = 1.dp.toPx())
                for (r in 0 until 2) {
                    block(AppColors.Ink, pad + 18.dp.toPx(), y + 15.dp.toPx() + r * (cardH - 22.dp.toPx()).coerceAtLeast(0f), (w - 2 * pad) * 0.45f, 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.5f)
                }
            }
            // The change between the cards: a dot on the rail and its minutes.
            val cy = top + room * 0.47f
            block(AppColors.Rail, pad + 10.dp.toPx(), cy, 3.dp.toPx(), 3.dp.toPx(), radius = 1.5.dp.toPx())
            block(AppColors.Early, pad + 18.dp.toPx(), cy, w * 0.25f, 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.8f)
        }
        DetailStyle.Classic -> {
            listOf(top, top + room * 0.55f).forEachIndexed { k, y ->
                val tall = room * 0.4f * settle
                block(AppColors.Ink, pad, y, 12.dp.toPx(), 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.6f)
                block(AppColors.Ink, pad, y + tall - 3.dp.toPx(), 12.dp.toPx(), 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.6f)
                block(SketchLines[k], pad + 17.dp.toPx(), y, 3.dp.toPx(), tall, radius = 1.dp.toPx())
                block(AppColors.Ink, pad + 25.dp.toPx(), y, (w - 2 * pad) * 0.45f, 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.6f)
                block(SketchLines[k], pad + 25.dp.toPx(), y + tall * 0.4f, 12.dp.toPx(), 6.dp.toPx(), radius = 2.dp.toPx())
                block(AppColors.Ink, pad + 25.dp.toPx(), y + tall - 3.dp.toPx(), (w - 2 * pad) * 0.4f, 3.dp.toPx(), radius = 1.dp.toPx(), alpha = 0.6f)
            }
        }
    }
}
