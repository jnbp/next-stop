package io.github.jnbp.nextstop.wear

import io.github.jnbp.nextstop.data.DiagLog
import io.github.jnbp.nextstop.data.distinctStopNames
import io.github.jnbp.nextstop.data.StationIdentity
import io.github.jnbp.nextstop.data.getOffSoon
import io.github.jnbp.nextstop.data.plausible
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.time.Duration
import java.io.IOException
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jnbp.nextstop.MainActivity
import io.github.jnbp.nextstop.R
import io.github.jnbp.nextstop.ui.AppChannel
import io.github.jnbp.nextstop.ui.NotifyFeature
import io.github.jnbp.nextstop.ui.ensureChannel
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.FootPaths
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.JourneyChange
import io.github.jnbp.nextstop.data.Leg
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.data.changeWalkMinutes
import io.github.jnbp.nextstop.data.drawnWalk
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.TransferComfort
import io.github.jnbp.nextstop.data.WalkTargetKind
import io.github.jnbp.nextstop.data.hasCalendarPermission
import io.github.jnbp.nextstop.data.journeyChangesOf
import io.github.jnbp.nextstop.data.loadContactAddresses
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.plainStopName
import io.github.jnbp.nextstop.data.railwayDirection
import io.github.jnbp.nextstop.data.SearchOptions
import io.github.jnbp.nextstop.data.servicesUnreachable
import io.github.jnbp.nextstop.data.shownRideStop
import io.github.jnbp.nextstop.data.townOf
import io.github.jnbp.nextstop.data.townedStopName
import io.github.jnbp.nextstop.data.shortDestinationName
import io.github.jnbp.nextstop.data.changeRatingsByLeg
import io.github.jnbp.nextstop.data.ChangePositions
import io.github.jnbp.nextstop.data.watchJourneys
import io.github.jnbp.nextstop.data.watchSearchCount
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.data.upcomingAppointments
import io.github.jnbp.nextstop.ui.ACTION_OPEN_TRIP
import io.github.jnbp.nextstop.ui.StepKind
import io.github.jnbp.nextstop.ui.TripStep
import io.github.jnbp.nextstop.ui.TripState
import io.github.jnbp.nextstop.ui.ConnectionRescue
import io.github.jnbp.nextstop.ui.LiveFreshness
import io.github.jnbp.nextstop.ui.LiveSource
import io.github.jnbp.nextstop.data.rescueKey
import io.github.jnbp.nextstop.data.spliceJourney
import io.github.jnbp.nextstop.ui.boardingHint
import io.github.jnbp.nextstop.ui.inStation
import io.github.jnbp.nextstop.ui.boardingPositionOf
import io.github.jnbp.nextstop.ui.rideAfter
import io.github.jnbp.nextstop.ui.lineColor
import io.github.jnbp.nextstop.ui.badgeLine
import io.github.jnbp.nextstop.ui.startTripService
import io.github.jnbp.nextstop.ui.stepDelay
import io.github.jnbp.nextstop.ui.tripStep
import io.github.jnbp.nextstop.ui.BatterySaver
import io.github.jnbp.nextstop.ui.SunSideSetting
import io.github.jnbp.nextstop.ui.RailTunnelAsset
import io.github.jnbp.nextstop.ui.legSunShinesKept
import io.github.jnbp.nextstop.ui.legSunSide
import io.github.jnbp.nextstop.data.SunSide
import io.github.jnbp.nextstop.data.shownSunSide
import org.json.JSONArray
import org.json.JSONObject

/**
 * The phone side of the Wear OS app, over the Wearable Data Layer. The watch side
 * (wear/src/main/java/io/github/jnbp/nextstop/wear/data/Protocol.kt) reads the same paths and keys:
 * keep both in step.
 *
 * - [PATH_PLACES]: favourites, saved places, recent destinations, the last position of the phone and,
 *   where the permissions are granted, the next appointments with a location and the contacts with
 *   a postal address (optional keys "appointments" and "contacts"; older watches ignore them).
 *   Written on every start of the app, whenever favourites, saved places or recent destinations
 *   change (debounced), while a trip runs and when the watch asks with [MSG_SYNC].
 * - [PATH_TRIP]: the next step of the followed trip, written while [io.github.jnbp.nextstop.ui.TripService] runs.
 * - Messages from the watch: [MSG_SYNC] writes both again, [MSG_START_TRIP] follows a journey the
 *   watch found (by its refresh token, a handle of the BVG API both use), [MSG_STOP_TRIP] ends the
 *   followed trip (the user ended it on the watch), [MSG_MAP_IMAGE] asks for a map picture of a
 *   view of the watch, answered at [PATH_MAP_IMAGE]`/<slot>` (see [WatchMapImages]),
 *   [MSG_WARM_UP] says the watch app opened: the phone prepares its map pictures (see
 *   [WatchMapImages.warmUp]) and answers nothing,
 *   [MSG_QUERY] asks for journeys, departures or places with the phone's sources, answered with
 *   the message [MSG_ANSWER] to the watch that asked (see [WatchQueries]).
 *
 * One trip, two devices: a trip started on either side runs on the phone when it is reachable
 * (the watch sends [MSG_START_TRIP] and hands over once [PATH_TRIP] says the phone follows it);
 * the watch follows on its own only without the phone. Ending it on one side ends it on both.
 *
 * Without Google Play services or a paired watch every call does nothing.
 */
object PhoneBridge {
    const val PATH_PLACES = "/nextstop/places"
    const val PATH_TRIP = "/nextstop/trip"
    const val MSG_SYNC = "/nextstop/sync"
    const val MSG_START_TRIP = "/nextstop/start-trip"
    const val MSG_STOP_TRIP = "/nextstop/stop-trip"

    /** "Wechseln" on the watch's rescue card: `{"key"}` of the offer it showed (see [rescueJson]). */
    const val MSG_RESCUE_SWITCH = "/nextstop/rescue-switch"
    const val MSG_MAP_IMAGE = "/nextstop/map-image"
    const val PATH_MAP_IMAGE = "/nextstop/mapimg"

    /** The watch app opened: prepare the map pictures. An older phone app ignores it. */
    const val MSG_WARM_UP = "/nextstop/warm-up"
    const val MSG_QUERY = "/nextstop/query"
    const val MSG_ANSWER = "/nextstop/answer"

    /**
     * The settings of the watch app as the phone has them (written here) and as the watch has
     * them (written by the watch); see WatchSettingsSync.kt. Each side reads the other's and
     * merges by time. Older apps do not know these paths and ignore them.
     */
    const val PATH_WATCH_SETTINGS = "/nextstop/watch-settings"
    const val PATH_WATCH_SETTINGS_WATCH = "/nextstop/watch-settings-w"

    /**
     * The wake-up alarm of the phone (see [io.github.jnbp.nextstop.ui.WakeAlarm]):
     * `{"ringing": true, "key", "stop", "arrival" (ISO time), "ring" (a count, new with every
     * ring), "updated"}` while it rings, `{"ringing": false}` once it stops. The watch rings with
     * it and sends [MSG_WAKE_OK] for "Ich bin wach". Older watches ignore both.
     */
    const val PATH_WAKE = "/nextstop/wake"
    const val MSG_WAKE_OK = "/nextstop/wake-ok"

    /** The test of the developer options: the watch opens its companion as for a trip started here. No payload. */
    const val MSG_COMPANION_TEST = "/nextstop/companion-test"
    private const val KEY_JSON = "json"

    // Pushes to the watch run here, apart from any screen, and never take the app down.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var pendingPush: Job? = null

    // Held strongly: SharedPreferences keeps its listeners only weakly.
    private var placesListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /**
     * Writes the places for the watch soon, and from now on whenever favourites, saved places
     * or recent destinations change. Called on every start of the app; the listener is
     * registered once per process.
     */
    fun watchPlaces(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            if (placesListener == null) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    // A null key means the whole file was cleared.
                    if (key == null || key in AppPrefs.WATCH_SYNC_KEYS) schedulePlaces(app)
                }
                try {
                    app.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)
                        .registerOnSharedPreferenceChangeListener(listener)
                    placesListener = listener
                } catch (e: Exception) {
                    // Without the listener the watch still gets the places on every start.
                }
            }
        }
        schedulePlaces(app)
    }

    /** Writes the places for the watch after [delayMillis]; a newer call replaces a waiting one. */
    fun schedulePlaces(context: Context, delayMillis: Long = PUSH_DEBOUNCE_MILLIS) {
        val app = context.applicationContext
        synchronized(lock) {
            pendingPush?.cancel()
            pendingPush = scope.launch {
                delay(delayMillis)
                try {
                    syncPlaces(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No Google Play services, no watch or an unreadable store: the app works as before.
                }
            }
        }
    }

    // The last trip state written (without its time) and when, so the same state is not
    // written again every 15 seconds, but at least every [HEARTBEAT_MILLIS] while a trip runs:
    // the watch takes a trip that is not written for minutes as gone (the process died).
    private var lastTrip: String? = null
    private var lastTripAt = 0L

    // The trip followed now as the watch tells it apart: its key and when it began here.
    private data class Followed(val id: String, val token: String?, val key: String, val started: Long)
    private var followed: Followed? = null

    /**
     * Mirrors the followed trip to the watch for as long as it runs: at once, then every 15
     * seconds where the step or the progress changed, and as soon as a real way on foot came
     * in (see [FootPaths.version]). Called by [io.github.jnbp.nextstop.ui.TripService];
     * when it ends, the watch learns that the trip is over. Also called by the app while the
     * service does not run (notifications of the phone off): there with [endWithNone] false, so
     * a hand-over to the service (or a recreated activity) does not end the trip on the watch
     * for a moment; a trip ended in the app still goes to the watch as "no trip".
     */
    suspend fun mirrorTrip(context: Context, endWithNone: Boolean = true) {
        val app = context.applicationContext
        try {
            // The places must never keep the trip from the watch: they go alongside, the trip
            // state is written at once (it is what opens the companion on the watch).
            scope.launch {
                try {
                    syncPlaces(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DiagLog.w(LOG_TAG) { "watch: places not written: ${e.javaClass.simpleName}" }
                }
            }
            TripState.journey.collectLatest { journey ->
                // The real ways on foot of its walks for the watch's sketches, also while no
                // page of the phone shows the trip, before the ways of other journeys: each
                // goes to the watch as soon as it is there (not with the next update), so the
                // change card replaces its straight line at once.
                journey?.let { FootPaths.want(listOf(it), urgent = true) }
                while (true) {
                    publishTrip(app, journey)
                    if (journey == null) return@collectLatest
                    // A new way on foot or a new rescue offer (#98) goes to the watch at once.
                    val seen = FootPaths.version.value to ConnectionRescue.suggestion.value
                    withTimeoutOrNull(MIRROR_MILLIS) {
                        combine(FootPaths.version, ConnectionRescue.suggestion) { ways, rescue -> ways to rescue }.first { it != seen }
                    }
                }
            }
        } finally {
            if (endWithNone) withContext(NonCancellable) { publishTrip(app, null) }
        }
    }

    /**
     * The rescue offer of the moment for the followed [journey] (see ConnectionRescue), as the
     * watch shows it: the first ride of the offered connection with its line, departure,
     * platform and colour, the change's stop, and its [rescueKey]. Null while the chosen
     * connection works or the offer belongs to another trip.
     */
    private fun rescueJson(journey: Journey): JSONObject? {
        val found = ConnectionRescue.suggestion.value?.takeIf { it.journeyId == journey.id } ?: return null
        val ride = found.option.rides.firstOrNull() ?: return null
        return JSONObject().apply {
            put("key", rescueKey(found.journeyId, found.keepLegs, found.option.id))
            put("line", ride.line ?: JSONObject.NULL)
            put("product", ride.product)
            put("dep", ride.departure.toInstant().toEpochMilli())
            put("platform", ride.departurePlatform ?: JSONObject.NULL)
            put("stop", found.stop)
            put("color", lineColor(ride).toArgb())
        }
    }

    /**
     * "Wechseln" on the watch: the followed trip goes on with the offered connection from the
     * change on, as the button of the phone's card does. Only while the phone's offer of the
     * moment is the one the watch showed ([key]) and still belongs to the followed trip.
     */
    fun switchToRescue(context: Context, key: String) {
        val followed = TripState.journey.value ?: return
        val found = ConnectionRescue.suggestion.value ?: return
        if (found.journeyId != followed.id || rescueKey(found.journeyId, found.keepLegs, found.option.id) != key) {
            DiagLog.i(LOG_TAG) { "watch: rescue switch ignored, the offer changed meanwhile" }
            return
        }
        TripState.journey.value = spliceJourney(followed, found.keepLegs, found.option)
        ConnectionRescue.update(context, null)
    }

    /** What came of [testCompanionOnWatch]. */
    enum class CompanionTest { Sent, NoWatch, NoAppOnWatch, Failed }

    /**
     * The test of the developer options ("Test: Begleiter auf der Uhr öffnen"): asks the watch
     * with Next Stop to open its companion now, the same way as for a trip started here. What
     * the watch made of it shows under its option "Begleiter öffnen" in its settings.
     */
    suspend fun testCompanionOnWatch(context: Context): CompanionTest {
        val app = context.applicationContext
        val status = WatchLink.check(app)
        val node = when (status.state) {
            WatchState.None -> return CompanionTest.NoWatch
            WatchState.NoApp -> return CompanionTest.NoAppOnWatch
            WatchState.Connected -> status.nodeId ?: return CompanionTest.Failed
        }
        return try {
            Wearable.getMessageClient(app).sendMessage(node, MSG_COMPANION_TEST, ByteArray(0)).await()
            CompanionTest.Sent
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.w(LOG_TAG) { "watch: companion test not sent: ${e.javaClass.simpleName}" }
            CompanionTest.Failed
        }
    }

    /**
     * Writes the next step of [journey] (or "no trip") for the watch, when it differs from the
     * last one or the last one is older than [HEARTBEAT_MILLIS], or always with [force] (the
     * watch asked: a fresh write is what tells it, as a change of the item). Called from the
     * trip service and the listener of the watch's messages at once, hence [lock].
     */
    suspend fun publishTrip(context: Context, journey: Journey?, force: Boolean = false) {
        val now = System.currentTimeMillis()
        // Never waits for the forecast: a kept one or none (asked in the background for the next state).
        val sun = try {
            boardingSunSide(context, journey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val (content, written) = synchronized(lock) {
            val followed = follow(journey, now)
            val stamp = OffsetDateTime.now()
            // A state the full form cannot be made of still reaches the watch, in its plain form.
            val text = try {
                tripJson(context, journey, followed, stamp, sun)
            } catch (e: Exception) {
                DiagLog.w(LOG_TAG) { "watch: full trip state failed: ${e.javaClass.simpleName}" }
                plainTripJson(journey, followed, stamp)
            }.toString()
            if (!force && text == lastTrip && (journey == null || now - lastTripAt < HEARTBEAT_MILLIS)) return
            text to lastTripAt
        }
        if (!put(context, PATH_TRIP, JSONObject(content).put("updated", now).toString())) return
        synchronized(lock) {
            // A newer state written meanwhile stays the last one.
            if (lastTripAt == written) {
                lastTrip = content
                lastTripAt = now
            }
        }
    }

    /**
     * The key and start of the trip [journey] belongs to: a new one when there was none or
     * another journey (by its id and its handle) is followed now. The key is the journey's
     * refresh token (for a trip the watch started: the very token it sent), else its id.
     */
    private fun follow(journey: Journey?, now: Long): Followed? {
        if (journey == null) {
            followed = null
            return null
        }
        val known = followed
        if (known != null && (known.id == journey.id || (known.token != null && known.token == journey.refreshToken))) {
            if (known.id != journey.id || known.token != journey.refreshToken) {
                followed = known.copy(id = journey.id, token = journey.refreshToken ?: known.token)
            }
            return followed
        }
        return Followed(journey.id, journey.refreshToken, journey.refreshToken ?: journey.id, now).also { followed = it }
    }

    /** Writes favourites, saved places, recent destinations and the last position of the phone. */
    suspend fun syncPlaces(context: Context) {
        val prefs = AppPrefs(context)
        val root = JSONObject()
        val favorites = JSONArray()
        for (favorite in prefs.loadFavoritePlaces()) {
            val place = favorite.place ?: continue
            favorites.put(placeJson(place, favorite.name.ifEmpty { iconLabel(context, favorite.icon) ?: place.name.shortStopName() }, favorite.icon))
        }
        val saved = JSONArray()
        for (entry in prefs.loadSavedPlaces()) saved.put(placeJson(entry.place, entry.name.ifEmpty { entry.place.name.shortStopName() }, entry.icon))
        val recent = JSONArray()
        val recentPlaces = (prefs.loadRecentRoutes().map { it.to } + prefs.loadSearchHistory())
            .filter { it.latitude != null || it.kind == PlaceKind.Stop }
            .distinctBy { it.id }
            .take(RECENT_COUNT)
        for (place in recentPlaces) recent.put(placeJson(place, place.name.shortStopName(), null))
        root.put("favorites", favorites)
        root.put("saved", saved)
        root.put("recent", recent)
        // The change time the phone's own searches outside the open page use, for the watch's searches.
        root.put("transferComfort", prefs.searchOptionsInUse.comfort.name)
        // The walking pace ("Langsamer unterwegs"), step-free ways and products of those searches:
        // a search of the watch itself (no phone in reach) asks the same (PhoneSearchOptions there).
        root.put("searchOptions", watchSearchOptionsJson(prefs.searchOptionsInUse))
        // The get-off reminder setting; the watch follows it until it is switched there.
        root.put("alightReminders", prefs.alightReminders)
        // How delays are shown ("Verspätungen", #227b): the watch shows its times the same way.
        root.put("delayDisplay", prefs.delayDisplay.name)
        lastLocation(context)?.let { root.put("location", it) }
        appointmentsJson(context)?.let { root.put("appointments", it) }
        contactsJson(context)?.let { root.put("contacts", it) }
        put(context, PATH_PLACES, root.toString())
    }

    /** The next appointments with a location, only when the calendar may be read already; null otherwise. */
    /** [options] as the watch reads them (PhoneSearchOptions there). */
    private fun watchSearchOptionsJson(options: SearchOptions): JSONObject = JSONObject().apply {
        put("walkingSpeed", options.walkingSpeed.apiValue)
        // "Eigener Faktor" of "Langsamer unterwegs" (#198): the watch asks Transitous at that pace.
        if (options.ownWalkFactor != null) put("walkFactor", options.walkFactorInUse)
        options.accessibility.apiValue?.let { put("accessibility", it) }
        put("products", JSONArray(options.products.toList()))
    }

    private suspend fun appointmentsJson(context: Context): JSONArray? {
        if (!hasCalendarPermission(context)) return null
        return try {
            val list = JSONArray()
            for (appointment in upcomingAppointments(context, days = APPOINTMENT_DAYS, limit = APPOINTMENT_COUNT)) {
                list.put(
                    JSONObject()
                        .put("title", appointment.title)
                        .put("location", appointment.location)
                        .put("begin", appointment.begin.toInstant().toEpochMilli()),
                )
            }
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** The contacts with a postal address, only when the contacts may be read already; null otherwise. */
    private suspend fun contactsJson(context: Context): JSONArray? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return try {
            val list = JSONArray()
            for (contact in loadContactAddresses(context).take(CONTACT_COUNT)) {
                list.put(
                    JSONObject().apply {
                        put("name", contact.name)
                        put("address", contact.address)
                        contact.kind?.let { put("kind", it) }
                    },
                )
            }
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Writes the phone's copy of the watch's settings ([json], see WatchSettings); false without Wear OS. */
    suspend fun putWatchSettings(context: Context, json: String): Boolean = put(context, PATH_WATCH_SETTINGS, json)

    /** Writes the state of the wake-up alarm for the watch: [target] rings (the [ring]th time), or null: it stopped. */
    suspend fun publishWake(context: Context, target: io.github.jnbp.nextstop.data.WakeTarget?, journey: Journey?, ring: Int) {
        val json = JSONObject().put("ringing", target != null).put("updated", System.currentTimeMillis())
        if (target != null) {
            json.put("key", target.key)
                .put("stop", target.stop.plainStopName())
                .put("arrival", target.arrival.toString())
                .put("ring", ring)
            journey?.legs?.getOrNull(target.legIndex)?.line?.let { json.put("line", it) }
        }
        put(context.applicationContext, PATH_WAKE, json.toString())
    }

    private suspend fun put(context: Context, path: String, json: String): Boolean = try {
        val request = PutDataMapRequest.create(path).apply { dataMap.putString(KEY_JSON, json) }
            .asPutDataRequest()
            .setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No Google Play services or no Wear OS app: the phone app works as before.
        false
    }

    private fun placeJson(place: Stop, label: String, icon: PlaceIcon?): JSONObject = JSONObject().apply {
        put("id", place.id)
        StationIdentity.stationKey(place)?.let { put("key", it) }
        put("name", place.name)
        put("label", label)
        place.latitude?.let { put("lat", it) }
        place.longitude?.let { put("lon", it) }
        put(
            "kind",
            when (place.kind) {
                PlaceKind.Stop -> "stop"
                PlaceKind.Poi -> "poi"
                PlaceKind.Address -> "address"
            },
        )
        icon?.let { put("icon", it.name) }
    }

    private fun iconLabel(context: Context, icon: PlaceIcon): String? = when (icon) {
        PlaceIcon.Home -> context.getString(R.string.home)
        PlaceIcon.Work -> context.getString(R.string.work)
        else -> null
    }

    private fun lastLocation(context: Context): JSONObject? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val location = try {
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        } ?: return null
        return JSONObject().put("lat", location.latitude).put("lon", location.longitude).put("time", location.time)
    }

    /**
     * The trip state with the keys every watch app reads and nothing that could fail: the step,
     * its place and time, the arrival and the trip's key. Only when [tripJson] fails.
     */
    private fun plainTripJson(journey: Journey?, followed: Followed?, now: OffsetDateTime): JSONObject {
        if (journey == null || followed == null) return JSONObject().put("active", false)
        val step = try {
            tripStep(journey, now, null)
        } catch (e: Exception) {
            null
        }
        val kind = step?.kind ?: if (now.isBefore(journey.departure)) StepKind.Board else StepKind.WalkToEnd
        return JSONObject()
            .put("active", true)
            .put("kind", kind.name)
            .put("line", step?.leg?.line ?: JSONObject.NULL)
            .put("product", step?.leg?.product ?: "")
            .put("place", step?.let { shownRideStop(it.leg, it.place) } ?: journey.legs.last().to.plainStopName())
            .put("time", (step?.time ?: journey.arrival).toInstant().toEpochMilli())
            .put("arrival", journey.arrival.toInstant().toEpochMilli())
            .put("destination", shortDestinationName(journey.legs.last()))
            .put("fromPhone", true)
            .put("tripKey", followed.key)
            .put("started", followed.started)
    }

    /**
     * The sunny side of the ride to board now, as the phone's hint shows it (#176): the setting,
     * a clear side of a ride long enough, and the sun really shining; null else. Never waits for
     * the network (the state for the watch must not, it may hang up to ~18 s without one): only a
     * kept forecast counts, and where none is kept it is asked in the background while the trip
     * is followed (rounded area, at most every 30 min, none at the battery saver's "Extrem"), so
     * the next state carries it. The watch asks for no weather itself. The rail tunnels are read
     * off the main thread the first time (see RailTunnelAsset.ensure).
     */
    private suspend fun boardingSunSide(context: Context, journey: Journey?): SunSide? {
        if (journey == null) return null
        val step = tripStep(journey, OffsetDateTime.now())
        val leg = step.leg?.takeIf { step.kind == StepKind.Board && !it.walking && !it.cancelled } ?: return null
        SunSideSetting.load(context)
        if (!SunSideSetting.on) return null
        // publishTrip may run on the main thread (the trip service, the app): the tunnels are read
        // on an IO thread, and waited for here, so the watch never gets a hint through a tunnel.
        RailTunnelAsset.ensure(context)
        val side = legSunSide(leg)
        val shines = !SunSideSetting.test && side != null && legSunShinesKept(leg, mayAsk = !BatterySaver.extreme, scope)
        return shownSunSide(SunSideSetting.on, SunSideSetting.test, side, shines)
    }

    /** The trip state as the watch reads it (see TripSnapshot there), without the time it is written. */
    private fun tripJson(context: Context, journey: Journey?, followed: Followed?, now: OffsetDateTime, sun: SunSide? = null): JSONObject {
        if (journey == null || followed == null) return JSONObject().put("active", false)
        val step = tripStep(journey, now)
        val leg = step.leg
        val total = Duration.between(journey.departure, journey.arrival).toMillis().coerceAtLeast(1)
        val passed = Duration.between(journey.departure, now).toMillis()
        // In steps of 5 %, so the watch is not woken for every second of the trip.
        val progress = (passed.toDouble() / total).coerceIn(0.0, 1.0).let { Math.round(it * 20) / 20.0 }
        var stopsLeft = -1
        var alert = false
        if (step.kind == StepKind.GetOff && leg != null) {
            // The stops ahead by position where the companion has one on this ride, else by the times.
            val fix = io.github.jnbp.nextstop.data.CompanionPosition.fixFor(journey, now.toInstant().toEpochMilli())
                ?.takeIf { it.nextStop >= 0 && journey.legs.getOrNull(it.legIndex) === leg }
            val ahead = if (fix != null) {
                leg.stopovers.size - fix.nextStop
            } else {
                leg.stopovers.drop(1).count { (it.arrival ?: it.departure)?.isAfter(now) == true }
            }
            if (leg.stopovers.size >= 2) {
                stopsLeft = ahead.coerceAtLeast(1)
                // On a long last hop the watch buzzes only within the last minutes, not hours ahead.
                // By the time of the step: on board by position the blended arrival (see RideState).
                alert = stopsLeft <= 1 && getOffSoon(step.time, now)
            } else {
                alert = Duration.between(now, step.time).toMinutes() < ALERT_FALLBACK_MINUTES
            }
        }
        val hint = try {
            boardingHint(context, step, journey)
        } catch (e: Exception) {
            null
        }
        return JSONObject().apply {
            put("active", true)
            put(
                "kind",
                when (step.kind) {
                    StepKind.Board -> "Board"
                    StepKind.GetOff -> "GetOff"
                    StepKind.WalkToEnd -> "WalkToEnd"
                    StepKind.Arrived -> "Arrived"
                },
            )
            put("line", badgeLine(leg?.line) ?: JSONObject.NULL)
            put("product", leg?.product ?: "")
            // The colour of the very ride (its provider's, where it runs): the watch knows no places.
            leg?.takeIf { !it.walking }?.let { put("color", lineColor(it).toArgb()) }
            put("direction", leg?.direction?.let { railwayDirection(leg, it) } ?: JSONObject.NULL)
            // Railway style on a long-distance ride (#93): the watch shows the names as sent.
            put("place", shownRideStop(leg, step.place))
            // The ride state (see RideState): at the stop waiting for the ride, or on board in a standing vehicle.
            put("waiting", step.waiting)
            put("standing", step.standing)
            // #277: the time is the app's estimate from the position ("ca.", blue); not for the
            // ride's departure sent in place of the time to set off. Older watches ignore it.
            put("estimated", step.estimated && !step.leave)
            // The ride has probably gone without the traveller ("Verpasst?"); older watches ignore it.
            put("missed", step.missed)
            // When the vehicle waited for is due here (#184): the watch says "kommt jeden Moment" and
            // "überfällig" by the same rule (waitPhaseOf there); older watches ignore it.
            if (step.waiting) step.dueAt?.let { put("dueAt", it.toInstant().toEpochMilli()) }
            put("platform", (if (step.kind == StepKind.Board) leg?.departurePlatform else leg?.arrivalPlatform) ?: JSONObject.NULL)
            // Before the walk to the first ride: "time" stays the ride's departure (as older watches
            // read it), "leaveAt" is when to set off; the delay is the ride's.
            val rideStep = if (step.leave && leg != null) {
                val ride = journey.plausible().legs.getOrNull(journey.legs.indexOfFirst { it === leg }) ?: leg
                step.copy(time = ride.departure, leave = false)
            } else {
                step
            }
            put("time", rideStep.time.toInstant().toEpochMilli())
            if (rideStep !== step) put("leaveAt", step.time.toInstant().toEpochMilli())
            put("delay", stepDelay(rideStep))
            put("hint", hint ?: JSONObject.NULL)
            put("progress", progress)
            put("arrival", journey.arrival.toInstant().toEpochMilli())
            put("destination", shortDestinationName(journey.legs.last()))
            put("stopsLeft", stopsLeft)
            put("alert", alert)
            put("alertKey", if (leg == null) "" else "${leg.tripId ?: leg.line}|${leg.to}")
            put("fromPhone", true)
            put("tripKey", followed.key)
            put("started", followed.started)
            // Another ride after this one: getting off is a change (the watch vibrates differently).
            val index = if (leg == null) -1 else journey.legs.indexOf(leg)
            put("change", step.kind == StepKind.GetOff && index >= 0 && journey.legs.drop(index + 1).any { !it.walking })
            // The destination as a place, so "Missed it" on the watch can search again from there.
            journey.legs.last().toPlace?.let { put("target", placeJson(it, it.name, null)) }
            // The chosen change ahead broke by a delay (#98): the phone's offer, for the watch's card.
            rescueJson(journey)?.let { put("rescue", it) }
            // How fresh the times are (#70 on the watch): the ride of this step live or not, and
            // when live times of the trip last came in. Changes with each refresh (about every
            // 30 s), so the watch's "Live · vor 25 s" is never more than one mirror round off.
            if (leg?.realtime == true) put("realtime", true)
            LiveFreshness.at(LiveSource.Trip).takeIf { it > 0 }?.let { put("liveAt", it) }
            // What the trip page of the watch shows besides the step: where this ride ends, the
            // next ride of a change, and the walk of this step for its sketch.
            val legs = journey.legs
            // The changes of the journey with their ratings, for the change card and the overview.
            val changes = try {
                journeyChangesOf(journey)
            } catch (e: Exception) {
                emptyList()
            }
            if (step.kind == StepKind.Board && leg != null) {
                // The sunny side of this ride in its direction of travel (#176); older watches ignore it.
                sun?.let { put("sunSide", it.name) }
                put("rideTo", shownRideStop(leg, leg.to))
                put("rideArrival", leg.arrival.toInstant().toEpochMilli())
            }
            if (index >= 0) nextRideJson(legs, index, changes)?.let { put("next", it) }
            val walk = when (step.kind) {
                StepKind.Board -> if (index >= 0) walkingBefore(legs, index) else emptyList()
                StepKind.GetOff -> if (index >= 0) walkingAfter(legs, index) else emptyList()
                StepKind.WalkToEnd -> walkingBefore(legs, legs.size)
                StepKind.Arrived -> emptyList()
            }
            walkJson(walk)?.let { put("walk", it) }
            // The trip companion of the watch (every key optional there): the third of the train
            // to board in, the stops of this ride, every ride of the journey for the overview,
            // and the rating and map of the change (or walk) of this step.
            if (step.kind == StepKind.Board && leg != null && hint != null) {
                val position = try {
                    rideAfter(journey, leg)?.let { boardingPositionOf(leg, it) }
                } catch (e: Exception) {
                    null
                }
                if (position != null && !position.samePlatform) {
                    put("boardPart", position.part)
                    put("boardEstimated", position.estimated)
                }
            }
            if (leg != null && (step.kind == StepKind.Board || step.kind == StepKind.GetOff)) {
                stopsJson(leg)?.let { put("stops", it) }
            }
            val rides = legs.indices.filter { !legs[it].walking }
            put("plan", planJson(legs, changes))
            put("planWalkEnd", walkMinutes(walkingBefore(legs, legs.size)))
            if (walkingBefore(legs, legs.size).any { it.cycling }) put("planBikeEnd", true)
            put("planIndex", if (index >= 0 && !legs[index].walking) rides.indexOf(index) else rides.size)
            if (step.kind == StepKind.Board && index >= 0) {
                changes.firstOrNull { it.nextIndex == index }?.let {
                    put("rating", it.tightness.name)
                    put("spare", it.spare)
                    put("gap", it.time.minutes)
                }
                val walks = walkingBefore(legs, index)
                val arriving = legs.subList(0, index - walks.size).lastOrNull()?.takeIf { !it.walking }
                wayMapJson(arriving, walks, legs[index])?.let { put("map", it) }
            }
            if (step.kind == StepKind.WalkToEnd) {
                val walks = walkingBefore(legs, legs.size)
                val arriving = legs.subList(0, legs.size - walks.size).lastOrNull()?.takeIf { !it.walking }
                wayMapJson(arriving, walks, null)?.let { put("map", it) }
            }
        }
    }

    /** The minutes of [walks] together, by the times of the provider. */
    private fun walkMinutes(walks: List<Leg>): Long =
        walks.sumOf { Duration.between(it.departure, it.arrival).toMinutes().coerceAtLeast(0) }

    /** The stops of [ride] from boarding to getting off with their times, for the progress line of the watch; null without them. */
    private fun stopsJson(ride: Leg): JSONArray? {
        if (ride.stopovers.size < 2 || ride.stopovers.size > WATCH_MAX_STOPS) return null
        // Never two different stops alike in the list (#278).
        val names = distinctStopNames(ride.stopovers.map { it.name }, ride.stopovers.map { shownRideStop(ride, it.name) })
        return JSONArray().apply {
            ride.stopovers.forEachIndexed { index, stop ->
                put(JSONObject().put("name", names[index]).put("time", stop.time?.toInstant()?.toEpochMilli() ?: 0L))
            }
        }
    }

    /**
     * Every ride of [legs] for the overview of the watch (PlanRide there): line, from and to,
     * times, platforms, the minutes on foot before it and the rating of the change to it.
     */
    private fun planJson(legs: List<Leg>, changes: List<JourneyChange>): JSONArray {
        val plan = JSONArray()
        var walk = 0L
        var bike = false
        legs.forEachIndexed { index, leg ->
            if (leg.walking) {
                walk += Duration.between(leg.departure, leg.arrival).toMinutes().coerceAtLeast(0)
                bike = bike || leg.cycling
                return@forEachIndexed
            }
            val change = changes.firstOrNull { it.nextIndex == index }
            plan.put(
                JSONObject()
                    .put("line", badgeLine(leg.line) ?: JSONObject.NULL)
                    .put("product", leg.product)
                    .put("color", lineColor(leg).toArgb())
                    .put("direction", leg.direction?.let { railwayDirection(leg, it) } ?: JSONObject.NULL)
                    .put("from", shownRideStop(leg, leg.from))
                    .put("to", shownRideStop(leg, leg.to))
                    .put("dep", leg.departure.toInstant().toEpochMilli())
                    .put("arr", leg.arrival.toInstant().toEpochMilli())
                    .put("depPlatform", leg.departurePlatform ?: JSONObject.NULL)
                    .put("arrPlatform", leg.arrivalPlatform ?: JSONObject.NULL)
                    .put("walkBefore", walk)
                    .put("live", leg.realtime)
                    .apply {
                        change?.let {
                            put("rating", it.tightness.name)
                            put("spare", it.spare)
                            put("gap", it.time.minutes)
                        }
                        if (leg.cancelled) put("cancelled", true)
                        if (bike) put("bikeBefore", true)
                    },
            )
            walk = 0
            bike = false
        }
        return plan
    }

    /**
     * The way of a change or walk for the map sketch of the watch (ChangeMap there): from
     * where [arriving] is left (else where [walks] begin) to where [next] is boarded (its stop
     * position, else the stop position the walk leads to, else the start of its way, else its
     * stop), else to where the walks end; with the way on foot where a real one is known (only
     * ways already loaded for the maps of the phone), thinned to [WATCH_PATH_POINTS] points.
     * Null without both ends.
     */
    private fun wayMapJson(arriving: Leg?, walks: List<Leg>, next: Leg?): JSONObject? {
        val walkPath = walks.flatMap { FootPaths.pathFor(it) }
        val start = arriving?.let { it.alightingSpot?.position ?: it.path.lastOrNull() ?: it.toPlace?.position }
            ?: walkPath.firstOrNull()
            ?: walks.firstOrNull()?.fromPlace?.position
            ?: return null
        val end = next?.let { ride ->
            ride.boardingSpot?.position
                ?: walks.lastOrNull()?.walkTarget?.takeIf { it.kind == WalkTargetKind.StopPosition }?.position
                ?: ride.path.firstOrNull()
                ?: ride.fromPlace?.position
        } ?: walkPath.lastOrNull() ?: walks.lastOrNull()?.toPlace?.position ?: return null
        if (start == end) return null
        val way = when {
            walkPath.size > 2 -> walkPath
            walks.isEmpty() && arriving?.alightingSpot != null && next?.boardingSpot != null -> FootPaths.wayBetween(start, end)
            else -> emptyList()
        }
        // The metres and minutes of the card, both of the way drawn (the watch showed the
        // straight line between the ends beside the minutes of the provider: "67 m · 8 min").
        val drawn = drawnWalk(if (way.size > 2) way else listOf(start, end))
        return JSONObject().apply {
            put("fromLat", start.first)
            put("fromLon", start.second)
            put("toLat", end.first)
            put("toLon", end.second)
            drawn?.let { (meters, minutes) ->
                put("meters", meters)
                put("minutes", minutes)
            }
            if (way.size > 2) {
                put("path", JSONArray().apply { thinOut(way, WATCH_PATH_POINTS).forEach { put(JSONArray().put(round5(it.first)).put(round5(it.second))) } })
            }
        }
    }

    /** At most [max] of [points], evenly picked, the first and the last kept. */
    private fun thinOut(points: List<Pair<Double, Double>>, max: Int): List<Pair<Double, Double>> {
        if (points.size <= max) return points
        return (0 until max).map { points[(it.toLong() * (points.size - 1) / (max - 1)).toInt()] }
    }

    /** A coordinate to 5 decimals (about a metre): enough for the sketch, and a smaller data item. */
    private fun round5(value: Double): Double = Math.round(value * 100_000) / 100_000.0

    /** The walking legs right before the leg at [index] (legs.size for the end of the journey). */
    private fun walkingBefore(legs: List<Leg>, index: Int): List<Leg> {
        var start = index
        while (start > 0 && legs[start - 1].walking) start--
        return legs.subList(start, index)
    }

    /** The walking legs right after the leg at [index]. */
    private fun walkingAfter(legs: List<Leg>, index: Int): List<Leg> {
        var end = index + 1
        while (end < legs.size && legs[end].walking) end++
        return legs.subList((index + 1).coerceAtMost(end), end)
    }

    /** The ride after the leg at [index] with the minutes on foot before it, as the watch reads it (NextRide). */
    private fun nextRideJson(legs: List<Leg>, index: Int, changes: List<JourneyChange>): JSONObject? {
        val nextIndex = (index + 1 until legs.size).firstOrNull { !legs[it].walking } ?: return null
        val next = legs[nextIndex]
        // The minutes the walk takes, as the change rating of the phone counts them: not a walk
        // the provider stretched over the wait for the next ride (see changeWalkMinutes).
        val walk = legs.subList(index + 1, nextIndex)
            .sumOf { changeWalkMinutes(it, legs[index].takeIf { ride -> !ride.walking }, next) }
        return JSONObject()
            .put("line", badgeLine(next.line) ?: JSONObject.NULL)
            .put("product", next.product)
            .put("color", lineColor(next).toArgb())
            .put("direction", next.direction?.let { railwayDirection(next, it) } ?: JSONObject.NULL)
            .put("stop", shownRideStop(next, next.from))
            .put("platform", next.departurePlatform ?: JSONObject.NULL)
            .put("time", next.departure.toInstant().toEpochMilli())
            .put("walkMinutes", walk)
            .apply { if (legs.subList(index + 1, nextIndex).any { it.cycling }) put("bike", true) }
            // Both rides at one platform of one stop (a change across, no walk at all): only then true.
            .put("samePlatform", nextIndex == index + 1 && !legs[index].walking && OsmStations.samePlatform(legs[index], next))
            .apply {
                // How tight the change is, and the map of its way (for the change card of the watch).
                changes.firstOrNull { it.nextIndex == nextIndex }?.let {
                    put("rating", it.tightness.name)
                    put("spare", it.spare)
                    put("gap", it.time.minutes)
                }
                wayMapJson(legs[index].takeIf { !it.walking }, legs.subList(index + 1, nextIndex), next)?.let { put("map", it) }
            }
    }

    /** One walk from the first to the last of [legs], with positions, as the watch reads it (WalkSketch). */
    private fun walkJson(legs: List<Leg>): JSONObject? {
        if (legs.isEmpty()) return null
        val from = legs.first().path.firstOrNull() ?: legs.first().fromPlace?.position ?: return null
        val to = legs.last().path.lastOrNull() ?: legs.last().toPlace?.position ?: return null
        if (from == to) return null
        val minutes = legs.sumOf { Duration.between(it.departure, it.arrival).toMinutes().coerceAtLeast(0) }
        return JSONObject()
            .put("from", legs.first().from.plainStopName())
            .put("to", legs.last().to.plainStopName())
            .put("fromLat", from.first)
            .put("fromLon", from.second)
            .put("toLat", to.first)
            .put("toLon", to.second)
            .put("minutes", minutes)
            .apply { if (legs.any { it.cycling }) put("bike", true) }
    }

    /**
     * The handle of a trip of the watch the phone could not take over in the background, set
     * by a tap on its notification ([followTap]); the app takes it, follows the journey and
     * sets it back to null.
     */
    val watchTripRequest = MutableStateFlow<String?>(null)

    // The journey of that trip as loaded by the listener, so the app need not load it again.
    @Volatile
    private var pendingJourney: Journey? = null

    /** Takes a tap on the notification of a trip of the watch; false for any other intent. */
    fun followTap(intent: Intent?): Boolean {
        if (intent?.action != ACTION_FOLLOW_WATCH_TRIP) return false
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return false
        watchTripRequest.value = token
        return true
    }

    /**
     * The journey of the trip of the watch with [token], for the app to follow: the one the
     * listener loaded, else loaded again by its handle. Null when it cannot be loaded.
     */
    suspend fun watchJourney(source: RoutingSource, token: String): Journey? {
        pendingJourney?.takeIf { it.refreshToken == token }?.let {
            pendingJourney = null
            return it
        }
        return source.refreshJourney(token, stopovers = true, polylines = true)?.copy(refreshToken = token)
    }

    /**
     * Follows the journey the watch chose: loads it by its handle and makes it the followed
     * trip, kept fresh and mirrored to the watch by the trip service.
     *
     * The handle is the provider's own: one of the watch (BVG, no prefix) goes to BVG whatever
     * provider the phone prefers (see RoutingSource.refreshJourney). The journey keeps the very
     * handle of the watch, which it uses as the key of the trip.
     *
     * From the background Android 12+ may refuse the foreground service. The trip is then not
     * taken over here (nothing would keep it fresh, and the watch would stop following it for a
     * state that never changes): the trip state stays as it was, the watch goes on following
     * the trip itself, and a notification lets the user follow it in the app, which starts the
     * service from the front.
     */
    internal suspend fun startFromWatch(context: Context, token: String) {
        val prefs = AppPrefs(context)
        val source = RoutingSource(
            choice = { prefs.sourceChoice },
            userAgent = TransitousSource.userAgent(context),
            bvgUrl = { ownServerUrl(prefs.ownServer) },
        )
        // Not loaded (no network, an unknown handle): the watch goes on following it alone.
        val journey = source.refreshJourney(token, stopovers = true, polylines = true)?.copy(refreshToken = token) ?: return
        if (TripState.serviceRunning.value) {
            // The service runs (a trip of the phone): it follows the new journey at once.
            TripState.journey.value = journey
            showStartedNotification(context, null)
            return
        }
        val previous = TripState.journey.value
        // A new trip from the watch: the shortcut alert by the setting ("Abkürzungs-Alarm").
        if (previous == null) TripState.shortcutAlert.value = prefs.shortcutAlert
        // Set before the start: the service reads it in its first onStartCommand.
        TripState.journey.value = journey
        if (startTripService(context)) {
            showStartedNotification(context, null)
            return
        }
        TripState.journey.compareAndSet(journey, previous)
        pendingJourney = journey
        showStartedNotification(context, token)
    }

    /** The user ended the trip on the watch: a trip of the watch waiting for a tap goes too. */
    internal fun dropWatchTrip(context: Context) {
        pendingJourney = null
        watchTripRequest.value = null
        context.getSystemService(NotificationManager::class.java)?.cancel(WATCH_NOTIFICATION)
    }

    /**
     * "Trip started on the watch, tap to follow it here": opens the trip page, or with
     * [pendingToken] (the phone did not take the trip over) follows that trip in the app first.
     */
    private fun showStartedNotification(context: Context, pendingToken: String?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(WATCH_CHANNEL) == null) ensureChannel(context, AppChannel.of(NotifyFeature.WatchStarted))
        val open = PendingIntent.getActivity(
            context,
            WATCH_NOTIFICATION,
            Intent(context, MainActivity::class.java)
                .apply {
                    if (pendingToken == null) {
                        setAction(ACTION_OPEN_TRIP)
                    } else {
                        setAction(ACTION_FOLLOW_WATCH_TRIP)
                        putExtra(EXTRA_TOKEN, pendingToken)
                    }
                }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, WATCH_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trip)
            .setContentTitle(context.getString(R.string.wear_trip_started))
            .setContentText(context.getString(R.string.wear_trip_started_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(WATCH_NOTIFICATION, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed: the trip is set and shows when the app is opened.
        }
    }

    private const val MIRROR_MILLIS = 15_000L

    // The trip companion of the watch: the stops of a ride are sent up to this many (a longer
    // ride sends none, the watch then counts by "stopsLeft"), the way of a change as at most
    // this many points (the watch reads no more, see ChangeMap.MAX_PATH there).
    private const val WATCH_MAX_STOPS = 80
    private const val WATCH_PATH_POINTS = 30
    private const val HEARTBEAT_MILLIS = 60_000L

    // The logcat tag of the bridge to the watch (no trip contents are logged).
    private const val LOG_TAG = "PhoneBridge"
    private const val RECENT_COUNT = 8
    private const val APPOINTMENT_DAYS = 7
    private const val APPOINTMENT_COUNT = 4

    // Keeps the data item small (the Data Layer allows 100 KB).
    private const val CONTACT_COUNT = 100
    private const val PUSH_DEBOUNCE_MILLIS = 1_500L
    private const val ALERT_FALLBACK_MINUTES = 2L
    private val WATCH_CHANNEL = AppChannel.of(NotifyFeature.WatchStarted).id
    private const val WATCH_NOTIFICATION = 4711
    private const val ACTION_FOLLOW_WATCH_TRIP = "io.github.jnbp.nextstop.FOLLOW_WATCH_TRIP"
    private const val EXTRA_TOKEN = "token"
}

/** Receives the messages of the watch, also while the app is closed. */
class PhoneBridgeService : WearableListenerService() {
    override fun onCreate() {
        super.onCreate()
        // Says in the log whether a request of the watch found the process running or started it.
        val age = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
        DiagLog.i(WatchMapImages.TAG) { "phone: listener service created, process ${age / 1000} s old" }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        // The watch changed one of its settings (see WatchSettingsSync.kt): the phone's copy follows.
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != PhoneBridge.PATH_WATCH_SETTINGS_WATCH) continue
            val json = DataMapItem.fromDataItem(item).dataMap.getString("json") ?: continue
            WatchSettings.takeFromWatch(applicationContext, json)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        // The warm-up is not waited for: the listener delivers one message after the other, and
        // a request for a picture must not wait behind it (the request gives it up instead).
        if (event.path == PhoneBridge.MSG_WARM_UP) {
            WatchMapImages.warmUp(applicationContext, event.data)
            return
        }
        // A map picture is drawn on the main thread and may take seconds. This callback waits
        // for it (it runs on a thread of the listener, not the main one): while it returns
        // not, the service stays bound, so with the app in the background the process is
        // neither frozen nor killed as a cached app halfway through the picture. Messages that
        // come meanwhile wait for it.
        if (event.path == PhoneBridge.MSG_MAP_IMAGE) {
            val job = WatchMapImages.answer(applicationContext, event.data) ?: return
            runBlocking {
                if (withTimeoutOrNull(MAP_IMAGE_WAIT_MILLIS) { job.join() } == null) {
                    DiagLog.w(WatchMapImages.TAG) { "phone: listener stopped waiting for the map picture" }
                }
            }
            return
        }
        // A lookup for the watch: answered by a message of its own, within its own time limit.
        if (event.path == PhoneBridge.MSG_QUERY) {
            runBlocking { WatchQueries.answer(applicationContext, event.sourceNodeId, event.data) }
            return
        }
        // Called on a background thread of the listener; the work is short and bounded.
        runBlocking {
            withTimeoutOrNull(MESSAGE_TIMEOUT_MILLIS) {
                try {
                    when (event.path) {
                        PhoneBridge.MSG_SYNC -> {
                            PhoneBridge.syncPlaces(applicationContext)
                            // Written even when unchanged: the watch asked, maybe after it dropped the trip.
                            PhoneBridge.publishTrip(applicationContext, TripState.journey.value, force = true)
                        }
                        PhoneBridge.MSG_START_TRIP -> {
                            val token = JSONObject(String(event.data)).optString("token", "")
                            if (token.isNotEmpty()) PhoneBridge.startFromWatch(applicationContext, token)
                        }
                        PhoneBridge.MSG_RESCUE_SWITCH -> {
                            val key = JSONObject(String(event.data)).optString("key", "")
                            if (key.isNotEmpty()) PhoneBridge.switchToRescue(applicationContext, key)
                        }
                        // "Ich bin wach" on the watch: the phone stops ringing as well.
                        PhoneBridge.MSG_WAKE_OK -> io.github.jnbp.nextstop.ui.WakeAlarm.confirm(applicationContext)
                        PhoneBridge.MSG_STOP_TRIP -> {
                            // The trip service and the companion of the app end with the journey;
                            // the watch learns it from the trip state written right away. As the app ends it.
                            io.github.jnbp.nextstop.ui.endFollowedTrip(applicationContext)
                            PhoneBridge.dropWatchTrip(applicationContext)
                            PhoneBridge.publishTrip(applicationContext, null)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A broken message or no network: the watch shows its own message.
                }
            }
        }
    }

    private companion object {
        const val MESSAGE_TIMEOUT_MILLIS = 25_000L

        // A little over the deadline of a request (WatchMapBudget.REQUEST_MILLIS), with the time to send the answer.
        const val MAP_IMAGE_WAIT_MILLIS = 45_000L
    }
}

/**
 * Answers the lookups of the watch ([PhoneBridge.MSG_QUERY]) with the phone's own sources, so
 * the watch finds journeys and departures wherever the phone does (Transitous outside Berlin,
 * the backends the user chose). The answer goes to the watch that asked as the message
 * [PhoneBridge.MSG_ANSWER] with the "id" of the question: `{"id", "ok": true}` with "journeys",
 * "departures" or "places", or `{"id", "ok": false, "error", "down"}` ("down": no source reached). The watch side reads it in
 * wear/src/main/java/io/github/jnbp/nextstop/wear/data/PhoneQuery.kt: keep both in step.
 */
internal object WatchQueries {
    // A little under the time the watch waits (25 s for journeys), so it gets the failure.
    private const val QUERY_TIMEOUT_MILLIS = 22_000L

    // A message may carry 100 KB; an answer above this is sent again without the stop times.
    private const val MAX_ANSWER_BYTES = 90_000
    private const val MAX_RESULTS = 6
    private const val MAX_STOPS = 80

    suspend fun answer(context: Context, node: String, data: ByteArray) {
        val request = try {
            JSONObject(String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            return
        }
        val id = request.optString("id", "")
        if (id.isEmpty()) return
        val answer = try {
            withTimeout(QUERY_TIMEOUT_MILLIS) { lookUp(context, request) }.put("ok", true)
        } catch (e: TimeoutCancellationException) {
            JSONObject().put("ok", false).put("error", "timeout").put("down", true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // "down": none of the sources could be reached, so the watch says so, not "nothing found" (#196).
            JSONObject().put("ok", false).put("error", e.javaClass.simpleName + ": " + (e.message ?: ""))
                .put("down", servicesUnreachable(e))
        }
        answer.put("id", id)
        var bytes = answer.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_ANSWER_BYTES) {
            answer.optJSONArray("journeys")?.let { list ->
                for (index in 0 until list.length()) {
                    val legs = list.optJSONObject(index)?.optJSONArray("legs") ?: continue
                    for (leg in 0 until legs.length()) legs.optJSONObject(leg)?.remove("stops")
                }
            }
            bytes = answer.toString().toByteArray(Charsets.UTF_8)
        }
        if (bytes.size > MAX_ANSWER_BYTES) {
            bytes = JSONObject().put("id", id).put("ok", false).put("error", "too large").toString().toByteArray(Charsets.UTF_8)
        }
        try {
            Wearable.getMessageClient(context).sendMessage(node, PhoneBridge.MSG_ANSWER, bytes).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The watch is gone: it looks the answer up itself.
        }
    }

    private suspend fun lookUp(context: Context, request: JSONObject): JSONObject {
        val prefs = AppPrefs(context)
        val source = RoutingSource(
            choice = { prefs.sourceChoice },
            userAgent = TransitousSource.userAgent(context),
            bvgUrl = { ownServerUrl(prefs.ownServer) },
        )
        val results = request.optInt("results", 3).coerceIn(1, MAX_RESULTS)
        return when (request.optString("kind")) {
            "journeys" -> {
                val from = placeOf(request.optJSONObject("from")) ?: throw IOException("no origin")
                val to = placeOf(request.optJSONObject("to")) ?: throw IOException("no destination")
                val options = prefs.searchOptionsInUse.let { options ->
                    val comfort = TransferComfort.entries.firstOrNull { it.name == request.optString("comfort") }
                    if (comfort == null) options else options.copy(comfort = comfort)
                }
                val page = source.journeys(from, to, options, watchSearchCount(results, options.comfort, MAX_RESULTS), null, null)
                // Where to board each ride (see boardingJson) asks the dataset of change positions.
                if (!ChangePositions.loaded) ChangePositions.load(context)
                // As the phone's search list offers them at the level: none with a tighter change,
                // the first [results] by the time to set off.
                JSONObject().put("journeys", JSONArray().apply { watchJourneys(page.journeys, options.comfort, results).forEach { put(journeyJson(context, it)) } })
            }
            "departures" -> {
                val stop = placeOf(request.optJSONObject("stop")) ?: throw IOException("no stop")
                val count = request.optInt("results", 12).coerceIn(1, 20)
                val list = source.departures(stop.id, count)
                // The directions as the phone's board names them: with their town where it is
                // another than the stop's (townedStopName, "Kirchstr. · Zagelsdorf").
                val town = townOf(stop.name)
                JSONObject().put("departures", JSONArray().apply { list.take(count).forEach { put(departureJson(it, town)) } })
            }
            "search" -> {
                val query = request.optString("query", "").trim()
                if (query.isEmpty()) throw IOException("no query")
                val latitude = request.optDouble("lat")
                val longitude = request.optDouble("lon")
                if (!latitude.isNaN() && !longitude.isNaN()) source.searchRegion = latitude to longitude
                val found = source.searchStops(query, results)
                JSONObject().put("places", JSONArray().apply { found.forEach { put(placeJson(it)) } })
            }
            else -> throw IOException("unknown query")
        }
    }

    /** A place of the watch (id, name, lat, lon, kind), with the ids the phone gave it or BVG's. */
    private fun placeOf(json: JSONObject?): Stop? {
        if (json == null) return null
        val id = json.optString("id", "")
        val name = json.optString("name", "")
        if (id.isEmpty() || name.isEmpty()) return null
        StationIdentity.noteStored(id, json.optString("key", ""))
        return Stop(
            id = id,
            name = name,
            latitude = if (json.has("lat")) json.optDouble("lat").takeIf { !it.isNaN() } else null,
            longitude = if (json.has("lon")) json.optDouble("lon").takeIf { !it.isNaN() } else null,
            distanceMeters = null,
            kind = when (json.optString("kind")) {
                "poi" -> PlaceKind.Poi
                "address" -> PlaceKind.Address
                else -> PlaceKind.Stop
            },
        )
    }

    private fun placeJson(place: Stop): JSONObject = JSONObject().apply {
        put("id", place.id)
        StationIdentity.stationKey(place)?.let { put("key", it) }
        put("name", place.name)
        place.latitude?.let { put("lat", it) }
        place.longitude?.let { put("lon", it) }
        put(
            "kind",
            when (place.kind) {
                PlaceKind.Stop -> "stop"
                PlaceKind.Poi -> "poi"
                PlaceKind.Address -> "address"
            },
        )
    }

    /** A journey in the compact form of the watch (parseJourneyJson there). */
    private fun journeyJson(context: Context, journey: Journey): JSONObject = JSONObject().apply {
        journey.refreshToken?.let { put("token", it) }
        // The rating of each change by the phone's rule: the watch rates and filters by it.
        val ratings = try {
            changeRatingsByLeg(journey)
        } catch (e: Exception) {
            emptyMap()
        }
        put(
            "legs",
            JSONArray().apply {
                journey.legs.forEachIndexed { index, leg ->
                    put(
                        legJson(leg).apply {
                            shownNamesJson(leg, last = index == journey.legs.lastIndex, json = this)
                            changeWalkJson(journey.legs, index, this)
                            ratings[index]?.let { put("rating", it.name) }
                            boardingJson(context, journey, leg, this)
                        },
                    )
                }
            },
        )
    }

    /**
     * Where to board [leg] of [journey], into [json]: the hint text ("boardHint") and the third
     * of the train ("boardPart", "boardEstimated"), worked out as for the trip state of a trip the
     * phone follows (see tripJson). So a trip the watch follows itself (started there, before or
     * without the phone taking it over) shows the same hint on its boarding page. Nothing for a
     * walk, nor where the setting is off or no hint is known.
     */
    private fun boardingJson(context: Context, journey: Journey, leg: Leg, json: JSONObject) {
        if (leg.walking) return
        val hint = try {
            boardingHint(context, TripStep(StepKind.Board, leg.departure, leg, leg.from), journey)
        } catch (e: Exception) {
            null
        } ?: return
        json.put("boardHint", hint)
        val position = try {
            rideAfter(journey, leg)?.let { boardingPositionOf(leg, it) }
        } catch (e: Exception) {
            null
        }
        if (position != null && !position.samePlatform) {
            json.put("boardPart", position.part)
            json.put("boardEstimated", position.estimated)
        }
    }

    /**
     * The names of [leg] as the trip state of the phone shows them, where they differ from the
     * provider's: its stops and direction ("fromShown", "toShown", "dirShown"; railway style on a
     * long-distance ride, the railway's short words on a regional train, see shownRideStop), and
     * for the [last] leg the destination as short as the trip state names it (shortDestinationName).
     * So the watch names a journey it shows, or a trip it follows itself, as after the phone took
     * the trip over; "from" and "to" stay the provider's names for matching.
     */
    private fun shownNamesJson(leg: Leg, last: Boolean, json: JSONObject) {
        if (!leg.walking) {
            shownRideStop(leg, leg.from).takeIf { it != leg.from }?.let { json.put("fromShown", it) }
            if (!last) shownRideStop(leg, leg.to).takeIf { it != leg.to }?.let { json.put("toShown", it) }
            leg.direction?.let { railwayDirection(leg, it) }?.takeIf { it != leg.direction }?.let { json.put("dirShown", it) }
        }
        if (last) shortDestinationName(leg).takeIf { it != leg.to }?.let { json.put("toShown", it) }
    }

    /**
     * For a walk of a change (between two rides) the minutes it takes as the phone counts them
     * ("changeWalk", see changeWalkMinutes), where they are fewer than the provider's: a walk the
     * provider stretched over the wait for the next ride. The trip state's next ride sends the same
     * minutes, so the change card of a trip the watch follows itself says the same walk.
     */
    private fun changeWalkJson(legs: List<Leg>, index: Int, json: JSONObject) {
        val walk = legs[index]
        if (!walk.walking) return
        var before = index - 1
        while (before >= 0 && legs[before].walking) before--
        var after = index + 1
        while (after < legs.size && legs[after].walking) after++
        val arriving = legs.getOrNull(before) ?: return
        val next = legs.getOrNull(after) ?: return
        val minutes = try {
            changeWalkMinutes(walk, arriving, next)
        } catch (e: Exception) {
            return
        }
        if (minutes < Duration.between(walk.departure, walk.arrival).toMinutes().coerceAtLeast(0)) json.put("changeWalk", minutes)
    }

    private fun legJson(leg: Leg): JSONObject = JSONObject().apply {
        put("line", badgeLine(leg.line) ?: JSONObject.NULL)
        put("product", leg.product)
        put("walk", leg.walking)
        if (leg.walking && leg.cycling) put("bike", true)
        // A walk of 0 m within a station: the watch does not count down to leaving for it.
        if (leg.inStation) put("inStation", true)
        put("from", leg.from)
        put("to", leg.to)
        put("dep", leg.departure.toInstant().toEpochMilli())
        put("arr", leg.arrival.toInstant().toEpochMilli())
        put("depDelay", leg.plannedDeparture?.let { Duration.between(it, leg.departure).toMinutes() } ?: 0L)
        put("arrDelay", leg.plannedArrival?.let { Duration.between(it, leg.arrival).toMinutes() } ?: 0L)
        // Live times or the timetable: the watch colours its times by it (#227b).
        if (leg.realtime) put("realtime", true)
        leg.departurePlatform?.let { put("platform", it) }
        leg.arrivalPlatform?.let { put("arrPlatform", it) }
        leg.direction?.let { put("dir", it) }
        put("cancelled", leg.cancelled)
        // The colour of the very ride, as the phone shows it (its provider's, where it runs): the
        // watch knows only Berlin's network map, so a tram of another town or a Hamburg U1 would
        // differ from the phone (and from the trip state, which sends it too).
        if (!leg.walking) put("color", lineColor(leg).toArgb())
        // The stops after boarding, the last one being where the ride is left.
        val times = leg.stopovers.drop(1).mapNotNull { it.time?.toInstant()?.toEpochMilli() }
        if (!leg.walking && times.isNotEmpty() && times.size <= MAX_STOPS) {
            put("stops", JSONArray().apply { times.forEach { put(it) } })
        }
        leg.fromPlace?.position?.let {
            put("fromLat", it.first)
            put("fromLon", it.second)
        }
        leg.toPlace?.position?.let {
            put("toLat", it.first)
            put("toLon", it.second)
        }
    }

    private fun departureJson(departure: Departure, boardTown: String?): JSONObject = JSONObject().apply {
        put("line", badgeLine(departure.line) ?: departure.line)
        put("product", departure.product)
        put("dir", departure.direction)
        townedStopName(departure.direction, boardTown).takeIf { it.isNotBlank() && it != departure.direction }?.let { put("dirShown", it) }
        departure.time?.let { put("time", it.toInstant().toEpochMilli()) }
        departure.plannedTime?.let { put("planned", it.toInstant().toEpochMilli()) }
        departure.delaySeconds?.let { put("delay", it / 60) }
        departure.platform?.let { put("platform", it) }
        put("cancelled", departure.cancelled)
        if (departure.additional) put("additional", true)
        departure.replaces?.let { put("replaces", it) }
        // The colour of the run's line as the phone's board shows it (see legJson).
        put("color", lineColor(departure).toArgb())
    }
}
