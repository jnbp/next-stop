package io.github.jnbp.nextstop.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.Departure
import io.github.jnbp.nextstop.wear.data.DepartureCache
import io.github.jnbp.nextstop.wear.data.NEARBY_GLANCE_REFRESH_MILLIS
import io.github.jnbp.nextstop.wear.data.NearbyGlance
import io.github.jnbp.nextstop.wear.data.NearbyGlanceResult
import io.github.jnbp.nextstop.wear.data.NearbyGlances
import io.github.jnbp.nextstop.wear.data.PhoneLink
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.PlaceKind
import io.github.jnbp.nextstop.wear.data.StopLine
import io.github.jnbp.nextstop.wear.data.SyncedPlace
import io.github.jnbp.nextstop.wear.data.UsualLines
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.LiveBoard
import io.github.jnbp.nextstop.wear.data.hasLocationPermission
import io.github.jnbp.nextstop.wear.data.liveBoard
import io.github.jnbp.nextstop.wear.data.locationGrants
import io.github.jnbp.nextstop.wear.data.nextCatchable
import io.github.jnbp.nextstop.wear.data.placeSubline
import io.github.jnbp.nextstop.wear.data.stopNameParts
import io.github.jnbp.nextstop.wear.data.upcomingDepartures

/*
 * The start page of the watch. From the top: the icon and name of Next Stop (scrolling away
 * with the list, as Wear OS headers do), the followed trip, the live departures of the nearest
 * stop as plain rows of the list (the rows of the departure board, with big minutes; five, four
 * on a small screen) under a header with the stop (shortened, see StopHeader) and its distance
 * and above a button to all its departures (or, with the setting off, the button "Departures
 * nearby" as before), the route search (the one filled
 * button), the map (its icon), the favourites (a
 * favourite stop with its next departure, any other with the lines of its usual connection)
 * and the settings. All buttons are full-size Wear buttons.
 *
 * Everything that loads does so only while the page is in front (AppForeground): the live
 * departures every NEARBY_GLANCE_REFRESH_MILLIS, the favourites from DepartureCache (refreshed
 * by its own prefetch, at most three stops), the minutes each time one of them changes. The
 * rows enter one after another when the page first opens (watchEnter), not when scrolled to.
 */

/** What the live departures show. */
private sealed interface GlanceState {
    data object Loading : GlanceState
    data object NoPosition : GlanceState
    data object Failed : GlanceState
    data class Shown(val glance: NearbyGlance) : GlanceState
}

// The rows enter only during this long after the page opened (see watchEnter).
private const val ENTRANCE_WINDOW_MILLIS = 1_200L

// Live departures shown: five from this screen height on (dp), four below it (a 192 dp Pixel
// Watch 1, where four rows and the header fill the screen); the list scrolls on to "All departures".
private const val FIVE_ROWS_MIN_HEIGHT_DP = 200
private const val LIVE_ROWS = 5
private const val LIVE_ROWS_SMALL = 4

// Favourite stops whose next departure is shown, and how often the kept departures are read again.
private const val FAVORITE_DEPARTURES_MAX = 3
private const val FAVORITE_READ_MILLIS = 30_000L

// The icon in the header and the name after it (milliseconds).
private val HEADER_MARK = 26.dp
private const val HEADER_NAME_DELAY_MILLIS = 120L

// An adaptive launcher icon is 108 units of which the launcher shows the middle 72: the mark is
// drawn this much larger than the circle it shows in, as on the launcher.
private const val ADAPTIVE_ICON_SCALE = 108f / 72f

// How strong the grey bars of the loading rows are.
private const val SKELETON_ALPHA = 0.14f

/**
 * The start page (see the comment at the top of this file). [onStop] opens the departure board
 * of a stop, [onPlace] a favourite: the departures of a stop, the connections to any other place.
 */
@Composable
fun StartScreen(
    onTrip: () -> Unit,
    onNearby: () -> Unit,
    onRoute: () -> Unit,
    onMap: () -> Unit,
    onStop: (Place) -> Unit,
    onSettings: () -> Unit,
    onPlace: (Place) -> Unit,
) {
    val trip by WatchStore.trip.collectAsState()
    val places by WatchStore.places.collectAsState()
    val phone by WatchStore.phone.collectAsState()
    val live by WatchStore.liveDepartures.collectAsState()
    val resumed by AppForeground.resumed.collectAsState()
    val usual by UsualLines.byPlace.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The header first, the first content (trip or departures) in the middle of the screen.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    var entering by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { UsualLines.load(context) }
        delay(ENTRANCE_WINDOW_MILLIS)
        entering = false
    }
    // Counts the taps on "try again" after a failed load: each one loads the live departures anew.
    var glanceRetry by remember { mutableIntStateOf(0) }
    val glance by rememberNearbyGlance(running = resumed && live, retry = glanceRetry)
    val favorites = (places.favorites + places.saved).distinctBy { it.place.id }
    val favoriteDepartures by rememberFavoriteDepartures(favorites, resumed)
    // One clock for every countdown of the page: it ticks when one of the minutes changes.
    val shownGlance = (glance as? GlanceState.Shown)?.glance?.takeIf { live }
    val times = remember(shownGlance, favoriteDepartures) {
        (shownGlance?.departures.orEmpty() + favoriteDepartures.values.flatten()).mapNotNull { it.time }
    }
    val now by rememberCountdownClock(times, resumed)
    val rows = if (LocalConfiguration.current.screenHeightDp >= FIVE_ROWS_MIN_HEIGHT_DP) LIVE_ROWS else LIVE_ROWS_SMALL
    val onReconnect: () -> Unit = { scope.launch { WatchStore.refresh(context) } }
    // The short explanation behind the crossed-out phone; closes itself once the phone is back.
    var phoneInfo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(phone) { if (phone == PhoneLink.Connected) phoneInfo = false }
    if (phoneInfo) {
        BackHandler { phoneInfo = false }
        PhoneOffInfo(onReconnect = onReconnect, onClose = { phoneInfo = false })
        return
    }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus()) {
            // Without the phone (as with an LTE watch) only a small crossed-out phone under the
            // time, in the same item as the header so the list keeps its first centred item.
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PhoneOffMark(shown = phone == PhoneLink.NotConnected, onClick = { phoneInfo = true })
                    BrandHeader(play = entering)
                }
            }
            // Shown whenever the phone does not answer, even with favourites cached from earlier.
            val dataKnown = places.synced || !places.isEmpty
            val phoneNote = phone == PhoneLink.NotConnected || phone == PhoneLink.NoApp ||
                (phone == PhoneLink.Connected && !dataKnown)
            if (phone != PhoneLink.NotConnected) phoneNoteItems(phone, synced = dataKnown, onReconnect = onReconnect)
            var row = 0
            if (trip.active) {
                val at = row++
                item { Box(Modifier.watchEnter(at, entering)) { TripCard(trip, onClick = onTrip) } }
            }
            row = if (live) {
                liveDepartureItems(
                    glance, now, rows, firstRow = row, entering = entering, onNearby = onNearby, onStop = onStop,
                    onRetry = { glanceRetry++ },
                )
            } else {
                val at = row++
                item { NearbyButton(hint = null, onClick = onNearby, modifier = Modifier.watchEnter(at, entering)) }
                row
            }
            val routeAt = row++
            item {
                val press = rememberWatchPress()
                Button(
                    onClick = onRoute,
                    modifier = Modifier.fillMaxWidth().watchEnter(routeAt, entering).then(press.modifier),
                    interactionSource = press.source,
                    colors = brandButtonColors(),
                    icon = { Icon(painterResource(R.drawable.ic_route), contentDescription = null) },
                    label = { Text(stringResource(R.string.route_search), maxLines = 1) },
                )
            }
            val mapAt = row++
            item { MapButton(onClick = onMap, modifier = Modifier.watchEnter(mapAt, entering)) }
            val headerAt = row++
            item { ListHeader(modifier = Modifier.watchEnter(headerAt, entering)) { Text(stringResource(R.string.favorites)) } }
            if (favorites.isEmpty()) {
                if (!phoneNote) item { Note(stringResource(R.string.no_favorites)) }
            }
            val firstFavorite = row
            itemsIndexed(favorites) { index, favorite ->
                FavoriteButton(
                    favorite = favorite,
                    departure = nextCatchable(favoriteDepartures[favorite.place.id], now),
                    usual = usual[favorite.place.id].orEmpty(),
                    now = now,
                    onClick = { onPlace(favorite.place) },
                    modifier = Modifier.watchEnter(firstFavorite + index, entering),
                )
            }
            item {
                val press2 = rememberWatchPress()
                Button(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().then(press2.modifier),
                    interactionSource = press2.source,
                    colors = brandTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(R.string.settings), maxLines = 1) },
                )
            }
        }
    }
}

/**
 * The live departures as items of the list, starting at entrance row [firstRow]; returns the
 * next free row. Loaded: a header with the stop and its distance (and the age of the data when
 * it comes from the cache), the next [rows] departures as the rows of the departure board with
 * big minutes, and a button to all departures of the stop. Loading: the header and grey rows
 * that shimmer. Without a position: the button "Departures nearby" with the reason. A failed
 * load (nothing loaded, or only old kept departures that are all gone) says so with a button
 * to try again ([onRetry]); "No departures in the next hour" only for a load that reached the
 * network (see liveBoard). The header looks pressable and opens the list of stops nearby, as
 * does the small "Other stop" button below it (or below the note when nothing leaves soon).
 */
private fun ScalingLazyListScope.liveDepartureItems(
    state: GlanceState,
    now: Long,
    rows: Int,
    firstRow: Int,
    entering: Boolean,
    onNearby: () -> Unit,
    onStop: (Place) -> Unit,
    onRetry: () -> Unit,
): Int {
    var row = firstRow
    when (state) {
        GlanceState.Loading -> {
            val at = row++
            item {
                ListHeader(modifier = Modifier.watchEnter(at, entering)) {
                    Text(stringResource(R.string.nearby_departures), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(rows) {
                val skeletonAt = row++
                item { SkeletonRow(Modifier.watchEnter(skeletonAt, entering)) }
            }
        }
        GlanceState.NoPosition -> {
            val at = row++
            item {
                val context = LocalContext.current
                val hint = if (!hasLocationPermission(context)) R.string.start_nearby_no_permission else R.string.no_position_fix
                NearbyButton(hint = hint, onClick = onNearby, modifier = Modifier.watchEnter(at, entering))
            }
        }
        GlanceState.Failed -> {
            // Nothing could be loaded: say so and offer to try again, never "no departures".
            val retryAt = row++
            item { RetryButton(onClick = onRetry, modifier = Modifier.watchEnter(retryAt, entering)) }
            val at = row++
            item { NearbyButton(hint = null, onClick = onNearby, modifier = Modifier.watchEnter(at, entering)) }
        }
        is GlanceState.Shown -> {
            val glance = state.glance
            val headerAt = row++
            item { LiveHeader(glance, onClick = onNearby, modifier = Modifier.watchEnter(headerAt, entering)) }
            val shown = upcomingDepartures(glance.departures, now, rows)
            when (liveBoard(glance.departures, glance.fresh, now, rows)) {
                LiveBoard.NoneSoon -> item { Note(stringResource(R.string.no_departures)) }
                LiveBoard.NotLoaded -> {
                    val retryAt = row++
                    item { RetryButton(onClick = onRetry, modifier = Modifier.watchEnter(retryAt, entering)) }
                }
                LiveBoard.Departures -> Unit
            }
            // Another stop: the list of the stops nearby, nearest first, with their distances.
            val otherAt = row++
            item { OtherStopButton(onClick = onNearby, modifier = Modifier.watchEnter(otherAt, entering)) }
            // Each departure keeps its item while it is shown, so when the first one has gone the
            // others glide up to their new places (watchSlide) and a new one fades in at the end.
            val keys = departureKeys(shown)
            shown.forEachIndexed { slot, departure ->
                val at = row++
                item(key = keys[slot]) {
                    Box(Modifier.watchEnter(at, entering).watchSlide(slot).watchAppear().watchSoftSize()) {
                        DepartureRow(departure, now = now)
                    }
                }
            }
            val allAt = row++
            item {
                val press3 = rememberWatchPress()
                Button(
                    onClick = { onStop(glance.stop) },
                    modifier = Modifier.fillMaxWidth().watchEnter(allAt, entering).then(press3.modifier),
                    interactionSource = press3.source,
                    colors = brandTonalButtonColors(),
                    icon = { Icon(painterResource(R.drawable.ic_schedule), contentDescription = null) },
                    label = { Text(stringResource(R.string.all_departures), maxLines = 1) },
                )
            }
        }
    }
    return row
}

/**
 * The header of the live departures: the nearest stop (see StopHeader: its name without the
 * town the watch is used in, in up to two lines), below it its distance and the age of the data
 * when it is not fresh ("120 m · 5 min ago"). A tap opens the stops nearby.
 */
@Composable
private fun LiveHeader(glance: NearbyGlance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val detail = listOfNotNull(
        glance.stop.distanceMeters?.let { stringResource(R.string.distance_m, it) },
        if (glance.fresh) null else ageText(context, glance.timeMillis),
    ).joinToString(" · ")
    val nearbyLabel = stringResource(R.string.nearby_departures)
    val title = stopNameParts(glance.stop.name).title
    StopHeader(
        name = glance.stop.name,
        detail = detail,
        description = listOf(title, detail).filter { it.isNotEmpty() }.joinToString(", ") + ". " + nearbyLabel,
        onClick = onClick,
        modifier = modifier,
        framed = true,
    )
}

/** The button under the stop of the live departures that opens the list of stops nearby. */
@Composable
private fun OtherStopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A full-width tonal action as the other actions of the watch (#145).
    WatchAction(stringResource(R.string.other_stop), onClick = onClick, icon = R.drawable.ic_place, modifier = modifier)
}

/** The live departures could not be loaded: a full-size button that says so and loads them again. */
@Composable
private fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(press.modifier),
        interactionSource = press.source,
        colors = brandTonalButtonColors(),
        icon = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
        label = { Text(stringResource(R.string.start_departures_failed), maxLines = 3, overflow = TextOverflow.Ellipsis) },
    )
}

/** The button "Departures nearby" of before, full-size, with [hint] (why no live departures show) below when given. */
@Composable
private fun NearbyButton(hint: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press4 = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(press4.modifier),
        interactionSource = press4.source,
        colors = brandTonalButtonColors(),
        icon = { Icon(painterResource(R.drawable.ic_schedule), contentDescription = null) },
        label = { WordSafeLabel(stringResource(R.string.nearby_departures)) },
        secondaryLabel = if (hint != null) {
            { Text(stringResource(hint), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
    )
}

/** A departure row while the live departures load: badge, direction and minutes as grey bars with a shimmer. */
@Composable
private fun SkeletonRow(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.loading)
    val bar = MaterialTheme.colorScheme.onSurface.copy(alpha = SKELETON_ALPHA)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .semantics { contentDescription = loading }
            .watchShimmer(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).background(bar))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(bar))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(22.dp).height(18.dp).clip(RoundedCornerShape(5.dp)).background(bar))
    }
}

/**
 * The app's icon as the launcher shows it on a round watch: its background
 * (ic_launcher_background) in a circle of [size], its mark (ic_launcher_foreground) over it at
 * the scale of an adaptive icon. Decoration: the name beside it says what it is.
 */
@Composable
fun AppMark(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * ADAPTIVE_ICON_SCALE),
        )
    }
}

// The crossed-out phone of the start page: drawn small, tappable on a full touch target.
private val PHONE_OFF_ICON = 16.dp
private val PHONE_OFF_TOUCH = 48.dp

/**
 * Keys for the live departure rows that stay with a departure while it is shown (line,
 * direction and planned time), numbered where two would be alike, so no key is used twice.
 */
internal fun departureKeys(departures: List<Departure>): List<String> {
    val seen = HashMap<String, Int>()
    return departures.map { d ->
        val base = "departure|${d.line}|${d.direction}|${d.plannedTime ?: d.time}"
        val n = seen[base] ?: 0
        seen[base] = n + 1
        if (n == 0) base else "$base#$n"
    }
}

// The space between two items of the start page's list, as the list puts it (ScalingLazyColumn).
private val LIST_ITEM_GAP = 4.dp

// What watchSlide keeps of a row between compositions.
private class SlideState {
    var slot = -1
    var height = 0
    var from = 0f
    val progress = mutableFloatStateOf(0f)
}

/**
 * Lets a row of the list that moves to another [slot] (one row up when the departure above it
 * has gone) glide there from its old place instead of jumping; at once with animations off.
 */
private fun Modifier.watchSlide(slot: Int): Modifier = composed {
    val on = rememberWatchAnimationsOn()
    val gap = with(LocalDensity.current) { LIST_ITEM_GAP.roundToPx() }
    val state = remember { SlideState() }
    if (state.slot >= 0 && slot != state.slot && on && state.height > 0) {
        // From where it is drawn now (mid-glide included) to its new place.
        state.from = (state.slot - slot) * (state.height + gap) + state.from * state.progress.floatValue
        state.progress.floatValue = 1f
    }
    state.slot = slot
    LaunchedEffect(slot) {
        val start = state.progress.floatValue
        if (start > 0f) {
            animate(start, 0f, animationSpec = tween(SLIDE_MILLIS)) { value, _ -> state.progress.floatValue = value }
        }
    }
    onSizeChanged { state.height = it.height }
        .graphicsLayer { translationY = state.from * state.progress.floatValue }
}

// How long a departure row glides to its new place.
private const val SLIDE_MILLIS = 320

// How long the crossed-out phone takes to show or go.
private const val PHONE_OFF_MILLIS = 250

/**
 * A small crossed-out phone that opens the explanation ([onClick]) while [shown]; it fades and
 * grows in (and out again) over about a quarter second, and the departures below move with it.
 */
@Composable
private fun PhoneOffMark(shown: Boolean, onClick: () -> Unit) {
    val animated = rememberWatchAnimationsOn()
    AnimatedVisibility(
        visible = shown,
        enter = if (animated) {
            fadeIn(tween(PHONE_OFF_MILLIS)) + scaleIn(tween(PHONE_OFF_MILLIS), initialScale = 0.8f) +
                expandVertically(tween(PHONE_OFF_MILLIS), expandFrom = Alignment.Top)
        } else {
            EnterTransition.None
        },
        exit = if (animated) {
            fadeOut(tween(PHONE_OFF_MILLIS)) + scaleOut(tween(PHONE_OFF_MILLIS), targetScale = 0.8f) +
                shrinkVertically(tween(PHONE_OFF_MILLIS), shrinkTowards = Alignment.Top)
        } else {
            ExitTransition.None
        },
        label = "phoneOffMark",
    ) {
        PhoneOffIcon(onClick)
    }
}

@Composable
private fun PhoneOffIcon(onClick: () -> Unit) {
    val description = stringResource(R.string.phone_off_icon)
    Box(
        modifier = Modifier
            .size(PHONE_OFF_TOUCH)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_phone_off),
            contentDescription = null,
            modifier = Modifier.size(PHONE_OFF_ICON),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Why the phone is crossed out: without it favourites and the companion are not synced, the
 * departures still come. With the button to connect again ([onReconnect]) and one to close.
 */
@Composable
private fun PhoneOffInfo(onReconnect: () -> Unit, onClose: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().crownFocus()) {
            item {
                Icon(
                    painterResource(R.drawable.ic_phone_off),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Note(stringResource(R.string.phone_off_info)) }
            item { SmallAction(stringResource(R.string.reconnect), onClick = onReconnect, icon = R.drawable.ic_refresh, primary = true) }
            item { SmallAction(stringResource(R.string.phone_off_close), onClick = onClose, icon = R.drawable.ic_close) }
        }
    }
}

/**
 * The top of the start page: the icon and the name of Next Stop. When the page first opens the
 * icon springs in with a small turn and the name slides in beside it; with animations off (or
 * once [play] is over) both are simply there.
 */
@Composable
private fun BrandHeader(play: Boolean) {
    val on = play && rememberWatchAnimationsOn()
    val mark = remember { Animatable(if (on) 0f else 1f) }
    val name = remember { Animatable(if (on) 0f else 1f) }
    LaunchedEffect(mark) {
        if (mark.value >= 1f) return@LaunchedEffect
        launch { mark.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
        delay(HEADER_NAME_DELAY_MILLIS)
        name.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(
            HEADER_MARK,
            modifier = Modifier.graphicsLayer {
                val shown = mark.value
                alpha = shown.coerceIn(0f, 1f)
                scaleX = 0.6f + 0.4f * shown
                scaleY = 0.6f + 0.4f * shown
                rotationZ = (1f - shown) * -40f
            },
        )
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val shown = name.value
                alpha = shown.coerceIn(0f, 1f)
                translationX = (1f - shown) * -10.dp.toPx()
            },
        )
    }
}

/**
 * The live departures of the nearest stop (see NearbyGlances) while [running] (the page in
 * front and the setting on): at once, then every NEARBY_GLANCE_REFRESH_MILLIS, again when the
 * location permission is granted, and at once on each new [retry] (the "try again" button,
 * which shows the loading rows meanwhile). It starts with what was shown last while the app runs.
 */
@Composable
private fun rememberNearbyGlance(running: Boolean, retry: Int): State<GlanceState> {
    val context = LocalContext.current
    val grants by locationGrants.collectAsState()
    val state = remember { mutableStateOf<GlanceState>(NearbyGlances.last?.let { GlanceState.Shown(it) } ?: GlanceState.Loading) }
    // The last retry handled: only a new one shows the loading rows, not the page coming back.
    val retried = remember { intArrayOf(0) }
    LaunchedEffect(running, grants, retry) {
        if (!running) return@LaunchedEffect
        if (retry != retried[0]) {
            retried[0] = retry
            state.value = GlanceState.Loading
        }
        while (true) {
            val result = try {
                NearbyGlances.load(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                NearbyGlanceResult.Failed(e)
            }
            state.value = when (result) {
                NearbyGlanceResult.NoPosition -> GlanceState.NoPosition
                is NearbyGlanceResult.Failed -> GlanceState.Failed
                is NearbyGlanceResult.Ready -> GlanceState.Shown(result.glance)
            }
            delay(NEARBY_GLANCE_REFRESH_MILLIS)
        }
    }
    return state
}

/**
 * The kept departures (DepartureCache) of the first FAVORITE_DEPARTURES_MAX favourite stops
 * among [favorites], by stop id, read again every FAVORITE_READ_MILLIS while [resumed]. They
 * are the ones the app keeps for the tile and the time offline: each is loaded again (one
 * request, by DepartureCache.prefetchFavorites) only once it is older than five minutes.
 */
@Composable
private fun rememberFavoriteDepartures(favorites: List<SyncedPlace>, resumed: Boolean): State<Map<String, List<Departure>>> {
    val context = LocalContext.current
    val ids = favorites.map { it.place }
        .filter { it.kind == PlaceKind.Stop && !it.id.contains(':') }
        .map { it.id }
        .distinct()
        .take(FAVORITE_DEPARTURES_MAX)
    val state = remember { mutableStateOf<Map<String, List<Departure>>>(emptyMap()) }
    val client = remember { BvgClient() }
    LaunchedEffect(ids, resumed) {
        if (!resumed || ids.isEmpty()) return@LaunchedEffect
        while (true) {
            state.value = ids.mapNotNull { id -> DepartureCache.get(context, id)?.let { id to it.departures } }.toMap()
            delay(FAVORITE_READ_MILLIS)
            // MainActivity loaded them on resume; while the page stays open, older ones load again here.
            DepartureCache.prefetchFavorites(context, client)
        }
    }
    return state
}

/** The button of the map, with the map icon (as the other buttons of the menu, no picture). */
@Composable
private fun MapButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val press5 = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(press5.modifier),
        interactionSource = press5.source,
        colors = brandTonalButtonColors(),
        icon = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
        label = { Text(stringResource(R.string.map), maxLines = 1) },
    )
}

/**
 * A favourite as a full-size button: its name, and below it the next departure from it (a stop
 * the watch keeps departures of: line, direction and minutes), else the lines of the connection
 * started to it last on the watch, else the name of the place when the label is another.
 */
@Composable
private fun FavoriteButton(
    favorite: SyncedPlace,
    departure: Departure?,
    usual: List<StopLine>,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = departure?.time
    val subline = placeSubline(favorite.label, favorite.place)
    val secondary: (@Composable RowScope.() -> Unit)? = when {
        departure != null && time != null -> {
            {
                LineBadge(departure.line, departure.product, color = departure.color)
                Spacer(Modifier.width(4.dp))
                Text(
                    departure.direction,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                WatchSwap(target = bigMinutesLabel(time, now), roll = true) { label -> Text(label, maxLines = 1, fontWeight = FontWeight.Bold) }
            }
        }
        usual.isNotEmpty() -> {
            {
                usual.forEach { line ->
                    LineBadge(line.name, line.product)
                    Spacer(Modifier.width(3.dp))
                }
            }
        }
        subline != null -> {
            { Text(subline, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        else -> null
    }
    val press6 = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(press6.modifier),
        interactionSource = press6.source,
        colors = brandTonalButtonColors(),
        icon = { Icon(painterResource(R.drawable.ic_place), contentDescription = null) },
        label = { Text(favorite.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = secondary,
    )
}
