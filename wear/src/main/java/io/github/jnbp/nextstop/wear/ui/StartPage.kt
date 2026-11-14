package io.github.jnbp.nextstop.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import androidx.wear.compose.material3.ButtonDefaults
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
import io.github.jnbp.nextstop.wear.data.MapImage
import io.github.jnbp.nextstop.wear.data.MapImages
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
import io.github.jnbp.nextstop.wear.data.hasLocationPermission
import io.github.jnbp.nextstop.wear.data.locationGrants
import io.github.jnbp.nextstop.wear.data.nextCatchable
import io.github.jnbp.nextstop.wear.data.stopNameParts
import io.github.jnbp.nextstop.wear.data.upcomingDepartures

/*
 * The start page of the watch. From the top: the icon and name of Next Stop (scrolling away
 * with the list, as Wear OS headers do), the followed trip, the live departures of the nearest
 * stop as plain rows of the list (the rows of the departure board, with big minutes; five, four
 * on a small screen) under a header with the stop (shortened, see StopHeader) and its distance
 * and above a button to all its departures (or, with the setting off, the button "Departures
 * nearby" as before), the route search (the one filled
 * button), the map with a preview of the last map picture near the user, the favourites (a
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

// The icon in the header, the name after it (milliseconds), and the preview on the map button.
private val HEADER_MARK = 26.dp
private const val HEADER_NAME_DELAY_MILLIS = 120L
private val MAP_PREVIEW = ButtonDefaults.LargeIconSize

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
    val glance by rememberNearbyGlance(running = resumed && live)
    val favorites = (places.favorites + places.saved).distinctBy { it.place.id }
    val favoriteDepartures by rememberFavoriteDepartures(favorites, resumed)
    // One clock for every countdown of the page: it ticks when one of the minutes changes.
    val shownGlance = (glance as? GlanceState.Shown)?.glance?.takeIf { live }
    val times = remember(shownGlance, favoriteDepartures) {
        (shownGlance?.departures.orEmpty() + favoriteDepartures.values.flatten()).mapNotNull { it.time }
    }
    val now by rememberCountdownClock(times, resumed)
    val rows = if (LocalConfiguration.current.screenHeightDp >= FIVE_ROWS_MIN_HEIGHT_DP) LIVE_ROWS else LIVE_ROWS_SMALL
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item { BrandHeader(play = entering) }
            // Shown whenever the phone does not answer, even with favourites cached from earlier.
            val dataKnown = places.synced || !places.isEmpty
            val phoneNote = phone == PhoneLink.NotConnected || phone == PhoneLink.NoApp ||
                (phone == PhoneLink.Connected && !dataKnown)
            phoneNoteItems(phone, synced = dataKnown, onReconnect = { scope.launch { WatchStore.refresh(context) } })
            var row = 0
            if (trip.active) {
                val at = row++
                item { Box(Modifier.watchEnter(at, entering)) { TripCard(trip, onClick = onTrip) } }
            }
            row = if (live) {
                liveDepartureItems(glance, now, rows, firstRow = row, entering = entering, onNearby = onNearby, onStop = onStop)
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
            item { MapButton(near = shownGlance?.stop, onClick = onMap, modifier = Modifier.watchEnter(mapAt, entering)) }
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
 * that shimmer. Without a position or any data: the button "Departures nearby" with the reason.
 * A tap on the header opens the stops nearby, as the button did.
 */
private fun ScalingLazyListScope.liveDepartureItems(
    state: GlanceState,
    now: Long,
    rows: Int,
    firstRow: Int,
    entering: Boolean,
    onNearby: () -> Unit,
    onStop: (Place) -> Unit,
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
        GlanceState.NoPosition, GlanceState.Failed -> {
            val at = row++
            item {
                val context = LocalContext.current
                val hint = when {
                    state == GlanceState.Failed -> R.string.start_nearby_offline
                    !hasLocationPermission(context) -> R.string.start_nearby_no_permission
                    else -> R.string.no_position_fix
                }
                NearbyButton(hint = hint, onClick = onNearby, modifier = Modifier.watchEnter(at, entering))
            }
        }
        is GlanceState.Shown -> {
            val glance = state.glance
            val headerAt = row++
            item { LiveHeader(glance, onClick = onNearby, modifier = Modifier.watchEnter(headerAt, entering)) }
            val shown = upcomingDepartures(glance.departures, now, rows)
            if (shown.isEmpty()) {
                item { Note(stringResource(R.string.no_departures)) }
            }
            shown.forEach { departure ->
                val at = row++
                item {
                    Box(Modifier.watchEnter(at, entering)) { DepartureRow(departure, now = now) }
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
        label = { Text(stringResource(R.string.nearby_departures), maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
 * location permission is granted. It starts with what was shown last while the app runs.
 */
@Composable
private fun rememberNearbyGlance(running: Boolean): State<GlanceState> {
    val context = LocalContext.current
    val grants by locationGrants.collectAsState()
    val state = remember { mutableStateOf<GlanceState>(NearbyGlances.last?.let { GlanceState.Shown(it) } ?: GlanceState.Loading) }
    LaunchedEffect(running, grants) {
        if (!running) return@LaunchedEffect
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

/**
 * The button of the map: a small round preview of the last map picture near [near] (the stop
 * of the live departures; any place when null) where the watch keeps one, else the map icon.
 * The picture is only read from the watch's cache, never asked from the phone for this.
 */
@Composable
private fun MapButton(near: Place?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preview by produceState<MapImage?>(initialValue = null, near?.id) {
        value = try {
            MapImages.latestNear(context, near?.latitude, near?.longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    val press5 = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(press5.modifier),
        interactionSource = press5.source,
        colors = brandTonalButtonColors(),
        icon = {
            val picture = preview
            if (picture != null) {
                Image(
                    bitmap = picture.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(MAP_PREVIEW).clip(CircleShape),
                )
            } else {
                Icon(painterResource(R.drawable.ic_map), contentDescription = null)
            }
        },
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
    val secondary: (@Composable RowScope.() -> Unit)? = when {
        departure != null && time != null -> {
            {
                LineBadge(departure.line, departure.product)
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
        favorite.label != favorite.place.name -> {
            { Text(favorite.place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
