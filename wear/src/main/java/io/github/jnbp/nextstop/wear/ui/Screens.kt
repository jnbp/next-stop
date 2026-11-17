package io.github.jnbp.nextstop.wear.ui

import android.Manifest
import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.data.BvgClient
import io.github.jnbp.nextstop.wear.data.BvgException
import io.github.jnbp.nextstop.wear.data.BvgFailure
import io.github.jnbp.nextstop.wear.data.CachedDepartures
import io.github.jnbp.nextstop.wear.data.ContactUsage
import io.github.jnbp.nextstop.wear.data.DepartureCache
import io.github.jnbp.nextstop.wear.data.Journey
import io.github.jnbp.nextstop.wear.data.LOG_TAG
import io.github.jnbp.nextstop.wear.data.LocalFollow
import io.github.jnbp.nextstop.wear.data.PhoneLink
import io.github.jnbp.nextstop.wear.data.PhoneSetup
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.PlaceKind
import io.github.jnbp.nextstop.wear.data.RouteUnavailableException
import io.github.jnbp.nextstop.wear.data.TransitRouter
import io.github.jnbp.nextstop.wear.data.UsualLines
import io.github.jnbp.nextstop.wear.data.TransferComfort
import io.github.jnbp.nextstop.wear.data.SyncedAppointment
import io.github.jnbp.nextstop.wear.data.SyncedContact
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.currentPosition
import io.github.jnbp.nextstop.wear.data.hasLocationPermission
import io.github.jnbp.nextstop.wear.data.locationGrants
import io.github.jnbp.nextstop.wear.data.listedStopName
import io.github.jnbp.nextstop.wear.data.platformWithoutLine

private val client = BvgClient()

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object NoPosition : Load<Nothing>
    data object Failed : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
}

/**
 * What "Allow location" does: asks for the location permission; once it was refused, the
 * next tap opens the settings of the app instead, where Android no longer shows the dialog.
 * A grant counts up [locationGrants], so screens without a position load again.
 */
@Composable
fun rememberLocationRequest(): LocationRequest {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasLocationPermission(context)) locationGrants.value++ else refused = true
    }
    return LocationRequest(refused) {
        if (refused) {
            openAppSettings(context)
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}

/** See [rememberLocationRequest]; [refused] tells that the button opens the settings now. */
class LocationRequest(val refused: Boolean, val ask: () -> Unit)

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        // No settings page for apps on this watch: nothing more to offer.
    }
}

/**
 * What a screen shows without a position: without the permission a button to allow it,
 * with it (no fix yet) a retry; and, when the phone could not help either, the state of the
 * connection to the phone.
 */
internal fun ScalingLazyListScope.noPositionItems(
    permitted: Boolean,
    request: LocationRequest,
    phone: PhoneLink,
    onRetry: () -> Unit,
    onReconnect: () -> Unit,
) {
    if (!permitted) {
        item { Note(stringResource(R.string.no_position_permission)) }
        item {
            SmallAction(
                text = stringResource(if (request.refused) R.string.open_settings else R.string.allow_location),
                onClick = request.ask,
                icon = R.drawable.ic_place,
                primary = true,
            )
        }
    } else {
        item { Note(stringResource(R.string.no_position_fix)) }
        item { SmallAction(stringResource(R.string.retry), onClick = onRetry, icon = R.drawable.ic_refresh) }
    }
    phoneNoteItems(phone, synced = true, onReconnect = onReconnect)
}

/**
 * The state of the connection to the phone, when it is not fine: no phone, no Next Stop on
 * it, or (with [synced] false) a connected phone that has not sent anything yet. With a
 * button that opens Next Stop on the phone (or its download page when it is not there),
 * and one that checks again and asks the phone for its places.
 */
fun ScalingLazyListScope.phoneNoteItems(phone: PhoneLink, synced: Boolean, onReconnect: () -> Unit) {
    val text = when (phone) {
        PhoneLink.NotConnected -> R.string.phone_not_connected
        PhoneLink.NoApp -> R.string.phone_no_app
        PhoneLink.Connected -> if (synced) return else R.string.phone_waiting
        PhoneLink.Unknown -> return
    }
    item { Note(stringResource(text)) }
    if (phone != PhoneLink.NotConnected) {
        item {
            val context = LocalContext.current
            val opened by PhoneSetup.opened.collectAsState()
            SmallAction(
                text = stringResource(
                    when {
                        opened -> R.string.look_at_phone
                        phone == PhoneLink.NoApp -> R.string.install_on_phone
                        else -> R.string.open_on_phone
                    },
                ),
                onClick = { PhoneSetup.open(context, installed = phone == PhoneLink.Connected) },
                icon = R.drawable.ic_phone,
            )
        }
    }
    item {
        SmallAction(stringResource(R.string.reconnect), onClick = onReconnect, icon = R.drawable.ic_refresh)
    }
}

/** Why a load came back without (new) data; each with a short text for the screen. */
internal enum class Problem(val text: Int) {
    NoPosition(R.string.no_position_fix),
    NoNetwork(R.string.problem_no_network),
    BvgUnreachable(R.string.problem_bvg_unreachable),
    BvgBadAnswer(R.string.problem_bvg_bad_answer),
    NoStops(R.string.problem_no_stops),
    Unexpected(R.string.problem_unexpected),
    NoRoute(R.string.problem_no_route),
    NoRoutePhoneAway(R.string.problem_no_route_phone),
    NoDepartures(R.string.problem_no_departures_loaded),
    NoDeparturesPhoneAway(R.string.problem_no_departures_phone),
}

/**
 * The problem to show for [e]. Where neither the phone nor the watch found anything
 * ([RouteUnavailableException]) it says so, for [departures] in the words of a departure board.
 */
internal fun problemOf(e: Exception, departures: Boolean = false): Problem {
    if (e is RouteUnavailableException) {
        return when {
            departures && e.phoneReached -> Problem.NoDepartures
            departures -> Problem.NoDeparturesPhoneAway
            e.phoneReached -> Problem.NoRoute
            else -> Problem.NoRoutePhoneAway
        }
    }
    return when ((e as? BvgException)?.failure) {
        BvgFailure.NoNetwork -> Problem.NoNetwork
        BvgFailure.Unreachable -> Problem.BvgUnreachable
        BvgFailure.BadAnswer -> Problem.BvgBadAnswer
        null -> Problem.Unexpected
    }
}

/** The user's own position as a place to search from ("address:lat,lon", as the phone names positions). */
internal fun positionPlace(latitude: Double, longitude: Double, name: String): Place =
    Place("address:$latitude,$longitude", name, latitude, longitude, PlaceKind.Address)

/** A loaded value and when it was loaded (epoch milliseconds). */
private class Snapshot<T>(val value: T, val timeMillis: Long)

/**
 * The last good list of stops nearby while the app runs, so the screen opened again (or
 * one whose next request fails) shows it with its age instead of an empty page. Only
 * touched on the main thread. Departures are kept in [DepartureCache].
 */
private object LastGood {
    var stops: Snapshot<List<Place>>? = null
    var stopsFromPhone = false
}

private const val NEARBY_RESULTS = 10
private const val MAX_BADGES = 3

/** The reason of a failed load and a button that tries again. */
internal fun ScalingLazyListScope.problemItems(problem: Problem, onRetry: () -> Unit) {
    item { Note(stringResource(problem.text)) }
    item {
        SmallAction(stringResource(R.string.retry), onClick = onRetry, icon = R.drawable.ic_refresh)
    }
}

/**
 * The stops near the position of the watch (or of the phone), nearest first, each with its
 * distance and lines; the nearest is highlighted. A tap opens its departures ([onStop]).
 * Without a fresh answer the last good list stays with its time.
 */
@Composable
fun NearbyStopsScreen(onStop: (Place) -> Unit) {
    val context = LocalContext.current
    var stops by remember { mutableStateOf(LastGood.stops) }
    var fromPhone by remember { mutableStateOf(LastGood.stopsFromPhone) }
    var loading by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val grants by locationGrants.collectAsState()
    val phone by WatchStore.phone.collectAsState()
    val scope = rememberCoroutineScope()
    val request = rememberLocationRequest()
    // A position the phone sends later (after "Connect again") ends the wait.
    val phoneTime = WatchStore.places.collectAsState().value.phoneLocationTime
    LaunchedEffect(phoneTime) {
        if (problem == Problem.NoPosition && phoneTime > 0) round++
    }
    LaunchedEffect(round, grants) {
        loading = true
        problem = null
        Log.d(LOG_TAG, "nearby: load #$round")
        try {
            val position = currentPosition(context)
            if (position == null) {
                Log.d(LOG_TAG, "nearby: failed at step position")
                problem = Problem.NoPosition
                return@LaunchedEffect
            }
            val found = client.nearbyStops(position.latitude, position.longitude, results = NEARBY_RESULTS)
            Log.d(LOG_TAG, "nearby: ${found.size} stops (position from ${if (position.fromPhone) "phone" else "watch"})")
            val snapshot = Snapshot(found, System.currentTimeMillis())
            stops = snapshot
            fromPhone = position.fromPhone
            LastGood.stops = snapshot
            LastGood.stopsFromPhone = position.fromPhone
            if (found.isEmpty()) problem = Problem.NoStops
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(LOG_TAG, "nearby: failed at step stops: $e")
            problem = problemOf(e)
        } finally {
            loading = false
        }
    }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus()) {
            item { ListHeader { Text(stringResource(R.string.nearby_departures), maxLines = 2, overflow = TextOverflow.Ellipsis) } }
            if (loading) item { LoadingNote(stringResource(R.string.loading)) }
            problem?.let { current ->
                if (current == Problem.NoPosition) {
                    noPositionItems(
                        permitted = hasLocationPermission(context),
                        request = request,
                        phone = phone,
                        onRetry = { round++ },
                        onReconnect = {
                            scope.launch {
                                WatchStore.refresh(context)
                                round++
                            }
                        },
                    )
                } else {
                    problemItems(current, onRetry = { round++ })
                }
            }
            val shown = stops
            if (shown != null && shown.value.isNotEmpty()) {
                if (loading || problem != null) item { Note(lastLoadedText(shown.timeMillis)) }
                if (fromPhone) item { Note(stringResource(R.string.from_phone_position)) }
                if (fromPhone && !hasLocationPermission(context)) {
                    // The phone helped out, but the watch could still find the stops itself with the permission.
                    item {
                        SmallAction(
                            text = stringResource(if (request.refused) R.string.open_settings else R.string.allow_location),
                            onClick = request.ask,
                            icon = R.drawable.ic_place,
                        )
                    }
                }
                itemsIndexed(shown.value) { index, stop -> Box(modifier = Modifier.watchAppear()) { StopButton(stop, nearest = index == 0, onClick = { onStop(stop) }) } }
            }
        }
    }
}

/** One stop nearby: name, then distance and up to [MAX_BADGES] line badges (or its products). */
@Composable
private fun StopButton(stop: Place, nearest: Boolean, onClick: () -> Unit) {
    val press = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(press.modifier),
        interactionSource = press.source,
        colors = if (nearest) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
        // As the start page's header names it: without the town the watch is used in (see stopNameParts).
        label = { Text(listedStopName(stop.name), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                stop.distanceMeters?.let { meters ->
                    Text(stringResource(R.string.distance_m, meters), maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                }
                if (stop.lines.isNotEmpty()) {
                    stop.lines.take(MAX_BADGES).forEach { line ->
                        LineBadge(line.name, line.product)
                        Spacer(Modifier.width(2.dp))
                    }
                    if (stop.lines.size > MAX_BADGES) Text("+${stop.lines.size - MAX_BADGES}", maxLines = 1)
                } else {
                    stop.products.take(MAX_BADGES).forEach { product ->
                        LineBadge(productLabel(product), product)
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }
        },
    )
}

/** A short badge text for a product, when a stop comes without its lines. */
private fun productLabel(product: String): String = when (product) {
    "suburban" -> "S"
    "subway" -> "U"
    "tram" -> "Tram"
    "bus" -> "Bus"
    "ferry" -> "F"
    "regional" -> "RE"
    "express" -> "IC"
    "coach" -> "FB"
    else -> "?"
}

/**
 * Departures of [stop], fresh every BOARD_REFRESH_MILLIS. The stop's name is the header (see
 * StopHeader: without the town the watch is used in, two lines, kept clear of the round top),
 * the departures are the rows of the start page with big minutes that count down on the
 * page's own clock, in two lines each (badge, platform and delay; the direction below; see
 * DepartureRow). When a request fails the last loaded board (also from an earlier run of the
 * app, see [DepartureCache]) stays with its age, under the reason and a retry. The header says
 * "Departures" above the stop's name, so the page says what it is. Two full-size buttons start
 * a route search from this stop ([onRouteFrom], the destination is chosen next) or to it
 * ([onRouteTo], from the user's position): below the departures, or for a [favorite] (usually
 * a destination) right under the header, "to here" first.
 */
@Composable
fun DeparturesScreen(stop: Place?, onRouteFrom: (Place) -> Unit = {}, onRouteTo: (Place) -> Unit = {}, favorite: Boolean = false) {
    val context = LocalContext.current
    val stopId = stop?.id
    // The cached board is read off the main thread, first thing of the loading below.
    var shown by remember(stopId) { mutableStateOf<CachedDepartures?>(null) }
    var loading by remember(stopId) { mutableStateOf(stop != null) }
    var problem by remember(stopId) { mutableStateOf<Problem?>(null) }
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(stopId, round) {
        val asked = stop
        if (stopId == null || asked == null) {
            loading = false
            return@LaunchedEffect
        }
        if (shown == null) shown = DepartureCache.get(context, stopId)
        while (true) {
            loading = true
            try {
                // BVG inside Berlin and Brandenburg, else the phone or Transitous (see TransitRouter).
                val list = TransitRouter.departures(context, asked, results = 12)
                Log.d(LOG_TAG, "departures $stopId: ${list.size}")
                DepartureCache.put(context, stopId, list)
                shown = CachedDepartures(list, System.currentTimeMillis())
                problem = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(LOG_TAG, "departures $stopId: failed: $e")
                problem = problemOf(e, departures = true)
                // Departures that left meanwhile drop out of the cached board.
                shown = DepartureCache.get(context, stopId) ?: shown
            } finally {
                loading = false
            }
            delay(BOARD_REFRESH_MILLIS)
        }
    }
    // One clock for all countdowns of the board: it ticks when one of the minutes changes.
    val resumed by AppForeground.resumed.collectAsState()
    val times = remember(shown) { shown?.departures.orEmpty().mapNotNull { it.time } }
    val now by rememberCountdownClock(times, resumed)
    // The header at the top, the first departure in the middle of the screen (as on the start page).
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus()) {
            item {
                if (stop == null) {
                    ListHeader { Text(stringResource(R.string.nearby_departures), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                } else {
                    StopHeader(
                        name = stop.name,
                        detail = stop.distanceMeters?.let { stringResource(R.string.distance_m, it) },
                        kicker = stringResource(R.string.board_kicker),
                    )
                }
            }
            if (stop == null) {
                item { Note(stringResource(R.string.no_results)) }
                return@ScalingLazyColumn
            }
            if (favorite) {
                item { BoardAction(R.string.route_to_here, R.drawable.ic_place, onClick = { onRouteTo(stop) }) }
                item { BoardAction(R.string.route_from_here, R.drawable.ic_route, onClick = { onRouteFrom(stop) }) }
            }
            val current = shown
            if (loading && current == null) item { LoadingNote(stringResource(R.string.loading)) }
            problem?.let { reason -> problemItems(reason, onRetry = { round++ }) }
            if (current != null) {
                if (problem != null) item { Note(lastLoadedText(current.timeMillis)) }
                if (current.departures.isEmpty()) item { Note(stringResource(R.string.no_departures)) }
                items(current.departures) { departure ->
                    Box(modifier = Modifier.watchAppear()) { DepartureRow(departure, now = now, details = true) }
                }
            }
            if (!favorite) {
                item { BoardAction(R.string.route_from_here, R.drawable.ic_route, onClick = { onRouteFrom(stop) }) }
                item { BoardAction(R.string.route_to_here, R.drawable.ic_place, onClick = { onRouteTo(stop) }) }
            }
        }
    }
}

// How often the board loads again while it is open.
private const val BOARD_REFRESH_MILLIS = 30_000L

/** A full-size tonal button below the departures of the board (a route from or to the stop), as the buttons of the start page. */
@Composable
private fun BoardAction(label: Int, icon: Int, onClick: () -> Unit) {
    val press = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(press.modifier),
        interactionSource = press.source,
        colors = brandTonalButtonColors(),
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(stringResource(label), maxLines = 2, overflow = TextOverflow.Ellipsis) },
    )
}

/**
 * "Gl. 2" for plain numbers; texts such as "Pos. 11" already say what they are. Without the
 * [line] in brackets that some providers add ("1 (U7)"): its badge beside it says it.
 */
@Composable
internal fun platformText(platform: String?, line: String? = null): String? {
    val shown = platformWithoutLine(platform, line) ?: return null
    return if (shown.first().isDigit()) stringResource(R.string.platform, shown) else shown
}

/**
 * Where to go: typed or spoken (the input of Wear OS with keyboard and voice, else speech
 * recognition alone), then a pick among the matches; or one of the recent destinations,
 * favourites, saved places, appointments and contacts of the phone. Appointments and
 * contacts carry an address as text, which is searched like a typed destination. With
 * [origin] (a stop of the map) the search starts there, shown under the title. "Recent" and
 * "Contacts" show three entries each (the contacts used last, see [ContactUsage]) and open
 * their whole list on a page of its own ([onAllRecent], [onAllContacts]).
 */
@Composable
fun RouteScreen(
    origin: Place? = null,
    onDestination: (Place) -> Unit,
    onAllRecent: () -> Unit = {},
    onAllContacts: () -> Unit = {},
) {
    val context = LocalContext.current
    val places by WatchStore.places.collectAsState()
    val phone by WatchStore.phone.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    var matches by remember { mutableStateOf<Load<List<Place>>?>(null) }
    // The sections unfolded with "Show more", by the string id of their title.
    var expanded by rememberSaveable { mutableStateOf(setOf<Int>()) }
    val usedContacts = ContactUsage.used.collectAsState().value
    LaunchedEffect(Unit) { ContactUsage.load(context) }
    fun search(text: String) {
        if (text.isBlank()) return
        matches = Load.Loading
        scope.launch {
            // The matches show at the top, also after a tap far down the list.
            listState.animateScrollToItem(0)
        }
        scope.launch {
            matches = try {
                Load.Done(TransitRouter.searchPlaces(context, text, results = 4))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed
            }
        }
    }
    val typeOrSpeak = rememberTextInput(
        label = stringResource(R.string.where_to),
        onText = { search(it) },
        onUnavailable = { matches = Load.Failed },
    )
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus()) {
            item { ListHeader { Text(stringResource(R.string.destination)) } }
            if (origin != null) item { Note(stringResource(R.string.from_place, origin.name)) }
            // Shown whenever the phone does not answer, even with places cached from earlier.
            phoneNoteItems(
                phone,
                synced = places.synced || !places.isEmpty,
                onReconnect = { scope.launch { WatchStore.refresh(context) } },
            )
            item {
                val press2 = rememberWatchPress()
                Button(
                    onClick = { typeOrSpeak() },
                    modifier = Modifier.fillMaxWidth().then(press2.modifier),
                    interactionSource = press2.source,
                    icon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null) },
                    label = { Text(stringResource(R.string.type_or_speak), maxLines = 1) },
                )
            }
            when (val found = matches) {
                null -> {}
                Load.Loading, Load.NoPosition -> item { LoadingNote(stringResource(R.string.loading)) }
                Load.Failed -> item { Note(stringResource(R.string.no_results)) }
                is Load.Done -> {
                    item { ListHeader { Text(stringResource(R.string.pick_match)) } }
                    if (found.value.isEmpty()) item { Note(stringResource(R.string.no_results)) }
                    items(found.value) { place ->
                        val press3 = rememberWatchPress()
                        Button(
                            onClick = { onDestination(place) },
                            modifier = Modifier.fillMaxWidth().then(press3.modifier),
                            interactionSource = press3.source,
                            label = { Text(if (place.kind == PlaceKind.Stop) listedStopName(place.name) else place.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            fun toggle(section: Int) {
                expanded = if (section in expanded) expanded - section else expanded + section
            }
            previewSection(
                places.recent,
                R.string.recent,
                more = { stringResource(R.string.show_all, places.recent.size) },
                onMore = onAllRecent,
            ) { place ->
                PlaceButton(place, onClick = { onDestination(place.place) })
            }
            foldingSection(places.favorites, R.string.favorites, R.string.favorites in expanded, { toggle(R.string.favorites) }) { place ->
                PlaceButton(place, onClick = { onDestination(place.place) })
            }
            foldingSection(places.saved, R.string.saved, R.string.saved in expanded, { toggle(R.string.saved) }) { place ->
                PlaceButton(place, onClick = { onDestination(place.place) })
            }
            foldingSection(
                upcoming(places.appointments),
                R.string.appointments,
                R.string.appointments in expanded,
                { toggle(R.string.appointments) },
            ) { appointment -> AppointmentButton(appointment, onSearch = { search(it) }) }
            // The contacts used last, filled up alphabetically.
            previewSection(
                ContactUsage.top(places.contacts, usedContacts, SECTION_PREVIEW),
                R.string.contacts,
                total = places.contacts.size,
                more = { stringResource(R.string.all_contacts, places.contacts.size) },
                onMore = onAllContacts,
            ) { contact ->
                ContactButton(
                    contact,
                    onSearch = {
                        ContactUsage.record(context, contact)
                        search(it)
                    },
                )
            }
        }
    }
}

// The key of the typed or spoken text in the result of the input of Wear OS.
private const val INPUT_KEY = "destination"

/**
 * What opens the input of Wear OS (keyboard and voice) for a short text, else speech
 * recognition alone (some emulator images have no input); [onText] gets the text, and
 * [onUnavailable] runs when the watch has neither.
 */
@Composable
internal fun rememberTextInput(label: String, onText: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) onText(spoken)
    }
    val typed = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(INPUT_KEY)?.toString()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) onText(text)
    }
    fun speak() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        }
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(INPUT_KEY).setLabel(label).build()))
        if (intent.resolveActivity(context.packageManager) == null) {
            speak()
        } else {
            try {
                typed.launch(intent)
            } catch (e: ActivityNotFoundException) {
                speak()
            }
        }
    }
}

// Entries a section of the destination page shows before "Show more".
private const val SECTION_PREVIEW = 3

/**
 * A section of the destination page: its title, the first [SECTION_PREVIEW] entries, and
 * when there are more a button that unfolds the rest (animated) and folds it again.
 */
private fun <T> ScalingLazyListScope.foldingSection(
    list: List<T>,
    title: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    if (list.isEmpty()) return
    item { ListHeader { Text(stringResource(title)) } }
    items(list.take(SECTION_PREVIEW)) { entry -> row(entry) }
    val rest = list.drop(SECTION_PREVIEW)
    if (rest.isEmpty()) return
    item {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rest.forEach { entry -> row(entry) }
            }
        }
    }
    item {
        SmallAction(
            text = if (expanded) stringResource(R.string.show_less) else stringResource(R.string.show_more, rest.size),
            onClick = onToggle,
        )
    }
}

/**
 * A section of the destination page that shows its first [SECTION_PREVIEW] entries of [list]
 * and, when there are more than that of [total], a button that opens them all on a page of
 * their own ([onMore]).
 */
private fun <T> ScalingLazyListScope.previewSection(
    list: List<T>,
    title: Int,
    total: Int = list.size,
    more: @Composable () -> String,
    onMore: () -> Unit,
    row: @Composable (T) -> Unit,
) {
    if (list.isEmpty()) return
    item { ListHeader { Text(stringResource(title)) } }
    items(list.take(SECTION_PREVIEW)) { entry -> row(entry) }
    if (total <= SECTION_PREVIEW) return
    item { SmallAction(text = more(), onClick = onMore) }
}

/** One appointment of the phone: title, then day and time with the location. */
@Composable
private fun AppointmentButton(appointment: SyncedAppointment, onSearch: (String) -> Unit) {
    val context = LocalContext.current
    val time = if (appointment.begin > 0) {
        DateUtils.formatDateTime(
            context,
            appointment.begin,
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY,
        ) + " · "
    } else {
        ""
    }
    val press4 = rememberWatchPress()
    Button(
        onClick = { onSearch(appointment.location) },
        modifier = Modifier.fillMaxWidth().then(press4.modifier),
        interactionSource = press4.source,
        label = { Text(appointment.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(time + appointment.location, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** One contact of the phone with an address: name, then the address (or [status] instead, while it is looked up). */
@Composable
internal fun ContactButton(contact: SyncedContact, onSearch: (String) -> Unit, status: String? = null) {
    val press5 = rememberWatchPress()
    Button(
        onClick = { onSearch(contact.address) },
        modifier = Modifier.fillMaxWidth().then(press5.modifier),
        interactionSource = press5.source,
        label = { Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(
                status ?: listOfNotNull(contact.kind, contact.address).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** The coming appointments, soonest first; those without a time at the end. */
private fun upcoming(list: List<SyncedAppointment>): List<SyncedAppointment> =
    list.sortedBy { if (it.begin > 0) it.begin else Long.MAX_VALUE }

/**
 * Two or three journeys from [origin] (null: the position of the watch) to [destination], each
 * with one "Start": the trip runs on the phone when it is reachable and on the watch in any
 * case until the phone takes it over ([LocalFollow.startEverywhere]); [onStarted] opens it.
 */
@Composable
fun ResultsScreen(origin: Place? = null, destination: Place?, onStarted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<List<Journey>>>(Load.Loading) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val myPosition = stringResource(R.string.my_position)
    val grants by locationGrants.collectAsState()
    val phone by WatchStore.phone.collectAsState()
    val comfort by WatchStore.comfort.collectAsState()
    val request = rememberLocationRequest()
    // A position the phone sends later (after "Connect again") ends the wait.
    val phoneTime = WatchStore.places.collectAsState().value.phoneLocationTime
    LaunchedEffect(phoneTime) {
        if (state == Load.NoPosition && phoneTime > 0) {
            state = Load.Loading
            round++
        }
    }
    LaunchedEffect(origin, destination, round, grants, comfort) {
        state = Load.Loading
        if (destination == null) {
            state = Load.Done(emptyList())
            return@LaunchedEffect
        }
        // A chosen start (a stop of the map) needs no position.
        val from = if (origin != null) {
            origin
        } else {
            val position = currentPosition(context)
            if (position == null) {
                state = Load.NoPosition
                return@LaunchedEffect
            }
            positionPlace(position.latitude, position.longitude, myPosition)
        }
        state = try {
            // Through the phone when it is reachable (its sources cover all of Germany), else BVG or Transitous.
            Load.Done(TransitRouter.journeys(context, from, destination, results = 3, comfort = comfort))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(LOG_TAG, "results: journeys failed: $e")
            problem = problemOf(e)
            Load.Failed
        }
    }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus()) {
            item {
                ListHeader {
                    Text(destination?.name ?: stringResource(R.string.results), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (origin != null) item { Note(stringResource(R.string.from_place, origin.name)) }
            item { ComfortChip(comfort, onClick = { WatchStore.chooseComfort(context, comfort.next()) }) }
            when (val current = state) {
                Load.Loading -> item { LoadingNote(stringResource(R.string.loading)) }
                Load.NoPosition -> noPositionItems(
                    permitted = hasLocationPermission(context),
                    request = request,
                    phone = phone,
                    onRetry = { state = Load.Loading; round++ },
                    onReconnect = {
                        scope.launch {
                            WatchStore.refresh(context)
                            state = Load.Loading
                            round++
                        }
                    },
                )
                Load.Failed -> problemItems(problem ?: Problem.Unexpected, onRetry = { round++ })
                is Load.Done -> {
                    if (current.value.isEmpty()) item { Note(stringResource(R.string.no_results)) }
                    items(current.value) { journey ->
                        JourneyCard(
                            journey = journey,
                            onStart = {
                                LocalFollow.startEverywhere(context, journey, destination)
                                // The lines of this connection become the usual way there (the start page shows them).
                                destination?.let { UsualLines.record(context, it, journey) }
                                onStarted()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The change time of the search as a small chip; a tap switches to the next level and searches again. */
@Composable
private fun ComfortChip(comfort: TransferComfort, onClick: () -> Unit) {
    SmallAction(stringResource(R.string.comfort_chip, stringResource(comfortLabel(comfort))), onClick = onClick)
}

private fun comfortLabel(comfort: TransferComfort): Int = when (comfort) {
    TransferComfort.Relaxed -> R.string.comfort_relaxed
    TransferComfort.Normal -> R.string.comfort_normal
    TransferComfort.Risky -> R.string.comfort_risky
    TransferComfort.VeryRisky -> R.string.comfort_very_risky
}

/** One journey, compact: departure in minutes and by the clock, the lines, changes, and one "Start". */
@Composable
private fun JourneyCard(journey: Journey, onStart: () -> Unit) {
    val cardPress = rememberWatchPress()
    Card(onClick = onStart, modifier = Modifier.fillMaxWidth().then(cardPress.modifier), interactionSource = cardPress.source) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(minutesLabel(journey.departure), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                "${clock(journey.departure)}–${clock(journey.arrival)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            journey.rides.take(4).forEach { ride ->
                LineBadge(ride.line, ride.product)
                Spacer(Modifier.width(3.dp))
            }
        }
        Text(
            if (journey.transfers == 0) stringResource(R.string.direct) else stringResource(R.string.changes, journey.transfers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        SmallAction(
            text = stringResource(R.string.start),
            onClick = onStart,
            icon = R.drawable.ic_play,
            primary = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
