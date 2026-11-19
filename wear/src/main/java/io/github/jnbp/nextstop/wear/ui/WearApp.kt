package io.github.jnbp.nextstop.wear.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.navigation.NavGraphBuilder
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import io.github.jnbp.nextstop.wear.R
import io.github.jnbp.nextstop.wear.complication.Complications
import io.github.jnbp.nextstop.wear.data.CompanionAutoOpen
import io.github.jnbp.nextstop.wear.data.Journey
import io.github.jnbp.nextstop.wear.data.MapStyleSetting
import io.github.jnbp.nextstop.wear.data.PhoneLink
import io.github.jnbp.nextstop.wear.data.Place
import io.github.jnbp.nextstop.wear.data.PlaceKind
import io.github.jnbp.nextstop.wear.data.StepKind
import io.github.jnbp.nextstop.wear.data.SyncedPlace
import io.github.jnbp.nextstop.wear.data.TripSnapshot
import io.github.jnbp.nextstop.wear.data.WatchStore
import io.github.jnbp.nextstop.wear.data.lineArgb
import io.github.jnbp.nextstop.wear.data.placeSubline
import io.github.jnbp.nextstop.wear.data.shownPlaceName
import io.github.jnbp.nextstop.wear.data.rideProgress
import io.github.jnbp.nextstop.wear.data.ridingStopsLeft
import io.github.jnbp.nextstop.wear.tile.FavoriteTileService

/**
 * What one page hands to the next: the stop of a departure board, the start of a route search
 * (null: the user's position), the destination, the found journeys.
 */
object Selection {
    var stop by mutableStateOf<Place?>(null)
    var origin by mutableStateOf<Place?>(null)
    var destination by mutableStateOf<Place?>(null)
    var journeys by mutableStateOf<List<Journey>>(emptyList())

    /** The journey of the results whose overview is open (#175). */
    var journey by mutableStateOf<Journey?>(null)
}

object Routes {
    const val START = "start"
    const val NEARBY = "nearby"
    const val DEPARTURES = "departures"

    /**
     * The departures of a stop opened as a favourite of the start page (its route buttons come
     * first): a route of its own, so whether it was opened that way belongs to the page and can
     * not reach a board opened any other way.
     */
    const val FAVORITE_DEPARTURES = "departures-favorite"
    const val TRIP = "trip"
    const val ROUTE = "route"
    const val RESULTS = "results"
    const val TILE_TARGET = "tile-target"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val ALL_RECENT = "all-recent"
    const val ALL_CONTACTS = "all-contacts"

    /** The overview of one journey of the results before it starts (#175). */
    const val JOURNEY = "journey"
}

@Composable
fun WearApp() {
    MaterialTheme {
        AppScaffold {
            val navController = rememberSwipeDismissableNavController()
            // A trip that starts (on the phone, or here) opens its page, wherever the app is; one that ends closes it.
            val trip by WatchStore.trip.collectAsState()
            var tripWasActive by remember { mutableStateOf(trip.active) }
            LaunchedEffect(trip.active) {
                val onTrip = navController.currentDestination?.route == Routes.TRIP
                if (trip.active && !tripWasActive && !onTrip) {
                    navController.popBackStack(Routes.START, inclusive = false)
                    navController.navigate(Routes.TRIP)
                }
                // Ended on the phone: its page closes.
                if (!trip.active && tripWasActive && onTrip) navController.popBackStack()
                tripWasActive = trip.active
            }
            // A page asked for by the ongoing activity or a tile, on top of the start page.
            val request by OpenRequest.pending.collectAsState()
            LaunchedEffect(request) {
                val open = request ?: return@LaunchedEffect
                OpenRequest.pending.value = null
                navController.popBackStack(Routes.START, inclusive = false)
                when (open) {
                    OpenRequest.Trip -> navController.navigate(Routes.TRIP)
                    OpenRequest.TileTarget -> navController.navigate(Routes.TILE_TARGET)
                    is OpenRequest.Results -> {
                        Selection.origin = null
                        Selection.destination = open.destination
                        navController.navigate(Routes.RESULTS)
                    }
                    is OpenRequest.Departures -> {
                        Selection.stop = open.stop
                        navController.navigate(Routes.DEPARTURES)
                    }
                    OpenRequest.Nearby -> navController.navigate(Routes.NEARBY)
                    OpenRequest.Search -> {
                        Selection.origin = null
                        navController.navigate(Routes.ROUTE)
                    }
                }
            }
            // The departure board of [stop]; [favorite]: opened as a favourite (see Routes.FAVORITE_DEPARTURES).
            fun openDepartures(stop: Place, favorite: Boolean) {
                Selection.stop = stop
                navController.navigate(if (favorite) Routes.FAVORITE_DEPARTURES else Routes.DEPARTURES)
            }
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.START) {
                screen(Routes.START) {
                    StartScreen(
                        onTrip = { navController.navigate(Routes.TRIP) },
                        onNearby = { navController.navigate(Routes.NEARBY) },
                        onRoute = {
                            Selection.origin = null
                            navController.navigate(Routes.ROUTE)
                        },
                        onMap = { navController.navigate(Routes.MAP) },
                        onStop = { stop -> openDepartures(stop, favorite = false) },
                        onSettings = { navController.navigate(Routes.SETTINGS) },
                        onPlace = { place ->
                            if (place.kind == PlaceKind.Stop && !place.id.contains(':')) {
                                openDepartures(place, favorite = true)
                            } else {
                                Selection.origin = null
                                Selection.destination = place
                                navController.navigate(Routes.RESULTS)
                            }
                        },
                    )
                }
                screen(Routes.NEARBY) {
                    NearbyStopsScreen(onStop = { stop -> openDepartures(stop, favorite = false) })
                }
                for ((route, favorite) in listOf(Routes.DEPARTURES to false, Routes.FAVORITE_DEPARTURES to true)) screen(route) {
                    val stop = Selection.stop
                    DeparturesScreen(
                        stop = stop,
                        favorite = favorite,
                        // From this stop: the destination is chosen next, on the destination page.
                        onRouteFrom = { from ->
                            Selection.origin = from
                            navController.navigate(Routes.ROUTE)
                        },
                        // To this stop: from the user's position, straight to the journeys.
                        onRouteTo = { to ->
                            Selection.origin = null
                            Selection.destination = to
                            navController.navigate(Routes.RESULTS)
                        },
                    )
                }
                screen(Routes.MAP) {
                    MapScreen(onStop = { stop -> openDepartures(stop, favorite = false) })
                }
                screen(Routes.SETTINGS) { SettingsScreen(onTileTarget = { navController.navigate(Routes.TILE_TARGET) }) }
                screen(Routes.TRIP) {
                    TripScreen(
                        onEnded = { navController.popBackStack() },
                        onReplan = { destination ->
                            Selection.origin = null
                            Selection.destination = destination
                            navController.navigate(Routes.RESULTS)
                        },
                    )
                }
                screen(Routes.ROUTE) {
                    RouteScreen(
                        origin = Selection.origin,
                        onDestination = { place ->
                            Selection.destination = place
                            navController.navigate(Routes.RESULTS)
                        },
                        onAllRecent = { navController.navigate(Routes.ALL_RECENT) },
                        onAllContacts = { navController.navigate(Routes.ALL_CONTACTS) },
                    )
                }
                // The whole lists of the destination page; the start chosen before (Selection.origin) stays.
                screen(Routes.ALL_RECENT) {
                    AllRecentScreen(onDestination = { place ->
                        Selection.destination = place
                        navController.navigate(Routes.RESULTS)
                    })
                }
                screen(Routes.ALL_CONTACTS) {
                    AllContactsScreen(onDestination = { place ->
                        Selection.destination = place
                        navController.navigate(Routes.RESULTS)
                    })
                }
                screen(Routes.TILE_TARGET) { TileTargetScreen(onChosen = { navController.popBackStack() }) }
                screen(Routes.RESULTS) {
                    ResultsScreen(
                        origin = Selection.origin,
                        destination = Selection.destination,
                        onStarted = {
                            navController.popBackStack(Routes.START, inclusive = false)
                            navController.navigate(Routes.TRIP)
                        },
                        onDetails = { journey ->
                            Selection.journey = journey
                            navController.navigate(Routes.JOURNEY)
                        },
                    )
                }
                screen(Routes.JOURNEY) {
                    val journey = Selection.journey
                    if (journey == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val context = LocalContext.current
                        JourneyDetailScreen(
                            journey = journey,
                            destination = Selection.destination,
                            onStart = {
                                startJourney(context, journey, Selection.destination)
                                navController.popBackStack(Routes.START, inclusive = false)
                                navController.navigate(Routes.TRIP)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A page of the app: the destination [route] of the navigation, which comes in softly when it
 * opens (see watchScreenEnter) on top of the system's swipe between pages.
 */
private fun NavGraphBuilder.screen(route: String, content: @Composable () -> Unit) {
    composable(route) {
        Box(modifier = Modifier.fillMaxSize().watchScreenEnter()) { content() }
    }
}

/** The settings of the watch: the destination of the tile, the reminder before getting off, the style of the map, and a small "About". */
@Composable
fun SettingsScreen(onTileTarget: () -> Unit) {
    val context = LocalContext.current
    val places by WatchStore.places.collectAsState()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus()) {
            item { ListHeader { Text(stringResource(R.string.settings)) } }
            item {
                // Follows the choice, which changes on the page opened from here.
                val chosenTarget by WatchStore.tileTargetChoice.collectAsState()
                val target = WatchStore.tileTarget(chosenTarget, places)
                val press = rememberWatchPress()
                Button(
                    onClick = onTileTarget,
                    modifier = Modifier.fillMaxWidth().then(press.modifier),
                    interactionSource = press.source,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { WordSafeLabel(stringResource(R.string.tile_target)) },
                    secondaryLabel = {
                        Text(target?.let { shownPlaceName(it) } ?: stringResource(R.string.tile_target_none), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
            // How to get there from the watch face: the tile is one swipe away.
            item {
                Text(
                    stringResource(R.string.tile_target_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item {
                val reminders by WatchStore.alightReminders.collectAsState()
                val press2 = rememberWatchPress()
                Button(
                    onClick = { WatchStore.chooseAlightReminders(context, !reminders) },
                    modifier = Modifier.fillMaxWidth().then(press2.modifier),
                    interactionSource = press2.source,
                    colors = if (reminders) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { WordSafeLabel(stringResource(R.string.alight_reminders)) },
                    secondaryLabel = { Text(stringResource(if (reminders) R.string.on else R.string.off), maxLines = 1) },
                )
            }
            item {
                // The live departures of the start page, or the button "Departures nearby" alone.
                val live by WatchStore.liveDepartures.collectAsState()
                val press3 = rememberWatchPress()
                Button(
                    onClick = { WatchStore.chooseLiveDepartures(context, !live) },
                    modifier = Modifier.fillMaxWidth().then(press3.modifier),
                    interactionSource = press3.source,
                    colors = if (live) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { WordSafeLabel(stringResource(R.string.live_departures_setting)) },
                    secondaryLabel = { Text(stringResource(if (live) R.string.on else R.string.off), maxLines = 1) },
                )
            }
            item {
                // All motion of the app, for older or slower watches (the system's setting counts as well).
                val animations by WatchStore.animations.collectAsState()
                val press4 = rememberWatchPress()
                Button(
                    onClick = { WatchStore.chooseAnimations(context, !animations) },
                    modifier = Modifier.fillMaxWidth().then(press4.modifier),
                    interactionSource = press4.source,
                    colors = if (animations) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { WordSafeLabel(stringResource(R.string.animations_setting)) },
                    secondaryLabel = { Text(stringResource(if (animations) R.string.on else R.string.off), maxLines = 1) },
                )
            }
            item {
                // The complications and the trip's chip show the app's icon, or with this the complications their own (#253, #282).
                val stateIcons by WatchStore.stateIcons.collectAsState()
                val press7 = rememberWatchPress()
                Button(
                    onClick = { WatchStore.chooseStateIcons(context, !stateIcons) },
                    modifier = Modifier.fillMaxWidth().then(press7.modifier),
                    interactionSource = press7.source,
                    colors = if (stateIcons) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { WordSafeLabel(stringResource(R.string.r8wear_state_icons_setting)) },
                    secondaryLabel = { Text(stringResource(if (stateIcons) R.string.on else R.string.off), maxLines = 1) },
                )
            }
            item {
                // The crown on the trip overview shows where in time the middle row is (#254; off: it just scrolls).
                val timeTravel by WatchStore.crownTimeTravel.collectAsState()
                val press8 = rememberWatchPress()
                Button(
                    onClick = { WatchStore.chooseCrownTimeTravel(context, !timeTravel) },
                    modifier = Modifier.fillMaxWidth().then(press8.modifier),
                    interactionSource = press8.source,
                    colors = if (timeTravel) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { WordSafeLabel(stringResource(R.string.r8wear_time_travel_setting)) },
                    secondaryLabel = { Text(stringResource(if (timeTravel) R.string.on else R.string.off), maxLines = 1) },
                )
            }
            item {
                // A trip started on the phone opens the companion here by itself (off by default).
                val openCompanion by WatchStore.openCompanion.collectAsState()
                val press6 = rememberWatchPress()
                Button(
                    onClick = { WatchStore.chooseOpenCompanion(context, !openCompanion) },
                    modifier = Modifier.fillMaxWidth().then(press6.modifier),
                    interactionSource = press6.source,
                    colors = if (openCompanion) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { Text(stringResource(R.string.open_companion_setting), maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = { Text(stringResource(if (openCompanion) R.string.on else R.string.off), maxLines = 1) },
                )
            }
            item {
                // What reached the watch from the phone's trip, and whether the companion can open
                // over the watch face: tells "the phone sends nothing" from "the watch may not show it".
                val openCompanion by WatchStore.openCompanion.collectAsState()
                val seen by WatchStore.lastPhoneTrip.collectAsState()
                val phone by WatchStore.phone.collectAsState()
                val last by CompanionAutoOpen.last.collectAsState()
                val lines = buildList {
                    // No phone: nothing of its trip can come.
                    if (phone == PhoneLink.NotConnected) add(stringResource(R.string.companion_status_no_phone))
                    if (phone == PhoneLink.NoApp) add(stringResource(R.string.companion_status_no_phone_app))
                    add(
                        if (seen.at <= 0L) {
                            stringResource(R.string.companion_status_never)
                        } else {
                            stringResource(if (seen.running) R.string.companion_status_running else R.string.companion_status_none, clock(seen.at))
                        },
                    )
                    // What keeps the companion from opening now (read afresh with every composition of the page).
                    if (openCompanion) CompanionAutoOpen.blocker(context)?.let { add(stringResource(outcomeText(it))) }
                    // What came of the last try, with its time.
                    last?.let { add(stringResource(R.string.companion_status_last, clock(it.at), stringResource(outcomeText(it.outcome)))) }
                }
                Text(
                    lines.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item {
                // Automatic, light, dark: each tap the next one.
                val style by WatchStore.mapStyle.collectAsState()
                val press5 = rememberWatchPress()
                Button(
                    onClick = { WatchStore.chooseMapStyle(context, MapStyleSetting.entries[(style.ordinal + 1) % MapStyleSetting.entries.size]) },
                    modifier = Modifier.fillMaxWidth().then(press5.modifier),
                    interactionSource = press5.source,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { WordSafeLabel(stringResource(R.string.map_style)) },
                    secondaryLabel = {
                        val name = when (style) {
                            MapStyleSetting.Auto -> R.string.map_style_auto
                            MapStyleSetting.Light -> R.string.map_style_light
                            MapStyleSetting.Dark -> R.string.map_style_dark
                        }
                        Text(stringResource(name), maxLines = 1)
                    },
                )
            }
            // About, kept small: the version and the license. The watch has no browser, so the
            // licenses and data sources are in the phone app's settings ("About Next Stop").
            item { ListHeader { Text(stringResource(R.string.about_title), maxLines = 2) } }
            item {
                val version = remember(context) { appVersionName(context) }
                Note(stringResource(R.string.about_version, version) + "\n" + stringResource(R.string.about_open_source))
            }
            item { Note(stringResource(R.string.about_on_phone)) }
        }
    }
}

/** The version of the watch app as its store shows it ("0.1.1"), "?" where Android does not say. */
private fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

/**
 * Chooses the destination of the tile "To a favourite" among the favourites and saved
 * places of the phone; the tile is drawn again at once.
 */
@Composable
fun TileTargetScreen(onChosen: () -> Unit) {
    val context = LocalContext.current
    val places by WatchStore.places.collectAsState()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val chosenTarget by WatchStore.tileTargetChoice.collectAsState()
    val chosen = WatchStore.tileTarget(chosenTarget, places)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize().fadeUnderTimeText(contentPadding.calculateTopPadding()).crownFocus()) {
            item { ListHeader { Text(stringResource(R.string.tile_target), maxLines = 2) } }
            val options = (places.favorites + places.saved).distinctBy { it.place.id }
            if (options.isEmpty()) item { Note(stringResource(R.string.no_favorites)) }
            items(options) { option ->
                val press6 = rememberWatchPress()
                Button(
                    onClick = {
                        WatchStore.saveTileTarget(context, option.place)
                        FavoriteTileService.requestUpdate(context)
                        Complications.onHomeChanged(context)
                        onChosen()
                    },
                    modifier = Modifier.fillMaxWidth().then(press6.modifier),
                    interactionSource = press6.source,
                    colors = if (option.place.id == chosen?.id) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = placeSubline(option.label, option.place)?.let { name ->
                        { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                )
            }
        }
    }
}

@Composable
fun PlaceButton(place: SyncedPlace, onClick: () -> Unit) {
    val secondary: (@Composable RowScope.() -> Unit)? = placeSubline(place.label, place.place)?.let { name ->
        { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    val press7 = rememberWatchPress()
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(press7.modifier),
        interactionSource = press7.source,
        label = { Text(place.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = secondary,
    )
}

/** The next step of a trip in a card: what to do, the line, the minutes and the progress. */
@Composable
fun TripCard(trip: TripSnapshot, onClick: () -> Unit) {
    // Minutes count down without new data.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            tick++
        }
    }
    // On board: a small ring of the way to the stop to get off, as on the ride page (see TripRing.kt).
    val now = if (tick >= 0) System.currentTimeMillis() else 0L
    val times = remember(trip.stops) { trip.stops.map { it.time } }
    val ride = if (trip.kind == StepKind.GetOff) rideProgress(times, now) else null
    val lineColor = Color(lineArgb(trip.line, trip.product, trip.color))
    val cardPress = rememberWatchPress()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().then(cardPress.modifier), interactionSource = cardPress.source) {
        Text(stringResource(R.string.your_trip), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trip.line != null && (trip.kind == StepKind.Board || trip.kind == StepKind.GetOff)) {
                LineBadge(trip.line, trip.product, color = trip.color)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = stepText(trip),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (ride != null) {
                Spacer(Modifier.width(4.dp))
                RideRing(ride, trip.stops.size, ridingStopsLeft(trip.stopsLeft, times, now), lineColor)
            }
        }
        Text(
            text = if (tick >= 0) minutesLabel(trip.time) else "",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        ProgressBar(trip.progress)
    }
}

@Composable
fun stepText(trip: TripSnapshot): String = when (trip.kind) {
    StepKind.Board -> when {
        trip.leave -> stringResource(R.string.step_leave, clock(trip.time))
        // The phone's ride state: the ride has probably gone, or the traveller waits at its stop.
        trip.missed && trip.line != null -> stringResource(R.string.step_missed, trip.line)
        trip.waiting -> stringResource(R.string.step_wait, trip.line ?: "", trip.place)
        else -> stringResource(R.string.step_board, trip.line ?: "", trip.place)
    }
    StepKind.GetOff -> stringResource(R.string.step_get_off, trip.place)
    StepKind.WalkToEnd -> stringResource(if (trip.walk?.cycling == true) R.string.step_cycle else R.string.step_walk, trip.place)
    StepKind.Arrived -> stringResource(R.string.arrived)
}

/** The short reason of an [CompanionAutoOpen.Outcome] for the status line of the settings. */
private fun outcomeText(outcome: CompanionAutoOpen.Outcome): Int = when (outcome) {
    CompanionAutoOpen.Outcome.Opened -> R.string.companion_outcome_opened
    CompanionAutoOpen.Outcome.AppInFront -> R.string.companion_outcome_in_front
    CompanionAutoOpen.Outcome.SettingOff -> R.string.companion_outcome_setting_off
    CompanionAutoOpen.Outcome.NotificationsOff -> R.string.companion_outcome_notifications_off
    CompanionAutoOpen.Outcome.ChannelMuted -> R.string.companion_outcome_channel_muted
    CompanionAutoOpen.Outcome.NoFullScreen -> R.string.companion_outcome_no_full_screen
}
