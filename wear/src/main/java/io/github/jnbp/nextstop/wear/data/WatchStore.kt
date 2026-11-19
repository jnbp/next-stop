package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import io.github.jnbp.nextstop.wear.complication.ComplicationKind
import io.github.jnbp.nextstop.wear.complication.Complications
import org.json.JSONObject

/** Whether the watch reaches a phone with Next Stop on it. */
enum class PhoneLink {
    /** Not checked yet. */
    Unknown,

    /** A phone with Next Stop is connected. */
    Connected,

    /** A phone is connected, but it does not declare Next Stop (not installed, or an older version). */
    NoApp,

    /** No phone is connected: not paired, out of reach, or no Google Play services on the watch. */
    NotConnected,
}

/** The style of the map pictures of the phone on the watch (a setting of the watch alone). */
enum class MapStyleSetting {
    /**
     * As the watch shows itself: Wear OS is dark (its theme has no light variant, and ambient
     * mode is black), so this is the dark map.
     */
    Auto,

    /** The light map, except in ambient mode. */
    Light,

    /** The dark map. */
    Dark;

    /** Whether the dark map is drawn; in [ambient] mode always. */
    fun dark(ambient: Boolean = false): Boolean = ambient || this != Light

    companion object {
        fun parse(name: String?): MapStyleSetting = entries.firstOrNull { it.name == name } ?: Auto
    }
}

/**
 * What the watch knows from the phone, kept in its own preferences so the tile, the
 * complication and the app have it at once, also while the phone is away. Written by
 * [WatchDataService] when the phone changes something, and by [refresh] on start.
 */
object WatchStore {
    /** The trip of the phone, or the one the watch follows itself; [TripSnapshot.NONE] without one. */
    val trip = MutableStateFlow(TripSnapshot.NONE)

    /** Favourites, saved places and recent destinations of the phone. */
    val places = MutableStateFlow(SyncedPlaces())

    /**
     * The change time of the watch's searches: the one of the phone, unless it was switched
     * on the watch since the phone last changed its own.
     */
    val comfort = MutableStateFlow(TransferComfort.Normal)

    /**
     * Whether the watch alerts before getting off: the phone's setting, unless it was switched
     * on the watch since the phone last changed its own. On by default.
     */
    val alightReminders = MutableStateFlow(true)

    /** The destination chosen in the app for the tile "To a favourite"; null when none was chosen. */
    val tileTargetChoice = MutableStateFlow<Place?>(null)

    /** The style of the map pictures: automatic (dark on the watch), light or dark. */
    val mapStyle = MutableStateFlow(MapStyleSetting.Auto)

    /** Whether the start page shows the live departures of the nearest stop (else the button "Departures nearby"). On by default. */
    val liveDepartures = MutableStateFlow(true)

    /** Whether the watch app animates (see rememberWatchAnimationsOn); on by default. */
    val animations = MutableStateFlow(true)

    /** Whether the watch opens the companion by itself when the phone starts a trip (see CompanionAutoOpen); off by default. */
    val openCompanion = MutableStateFlow(false)

    /** Whether the complications show their own icons (the trip's state, home, a warning), not the app's (see complicationIcon); off by default. */
    val stateIcons = MutableStateFlow(false)

    /** Whether the overview of the live trip shows the time travel pill when scrolled (see timeTravelShown); off by default. */
    val crownTimeTravel = MutableStateFlow(false)

    /** The settings edited on the watch and on the phone, with the time of their last change (see WatchSettingsSync.kt). */
    private var settings: Map<String, SyncedSetting> = emptyMap()

    /** Whether a phone with Next Stop was reachable at the last [refresh]. */
    val phone = MutableStateFlow(PhoneLink.Unknown)

    // Messages to the phone sent from places without a coroutine of their own (the data listener).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun prefs(context: Context) = context.getSharedPreferences("wear-store", Context.MODE_PRIVATE)

    /** Fills the flows from what was stored before; cheap, for every start of the app, tile or complication. */
    fun load(context: Context) {
        val prefs = prefs(context)
        places.value = SyncedPlaces.parse(prefs.getString(KEY_PLACES, null))
        tileTargetChoice.value = chosenTileTarget(context)
        val stored = TripSnapshot.parse(prefs.getString(KEY_TRIP, null))
        trip.value = stored
        // A trip of the phone that the phone stopped writing (its process died) is not shown frozen.
        if (stored.stale(System.currentTimeMillis())) saveTrip(context, TripSnapshot.NONE)
        updateComfort(context)
        updateAlightReminders(context)
        mapStyle.value = MapStyleSetting.parse(prefs.getString(KEY_MAP_STYLE, null))
        settings = parseSettings(prefs.getString(KEY_SETTINGS, null))
        liveDepartures.value = settingFlag(settings, WatchSettingKeys.LIVE_DEPARTURES, default = true)
        animations.value = settingFlag(settings, WatchSettingKeys.ANIMATIONS, default = true)
        openCompanion.value = settingFlag(settings, WatchSettingKeys.OPEN_COMPANION, default = false)
        stateIcons.value = settingFlag(settings, WatchSettingKeys.STATE_ICONS, default = false)
        crownTimeTravel.value = settingFlag(settings, WatchSettingKeys.CROWN_TIME_TRAVEL, default = false)
        lastPhoneTrip.value = PhoneTripSeen(prefs.getLong(KEY_PHONE_TRIP_AT, 0L), prefs.getBoolean(KEY_PHONE_TRIP_RUNNING, false))
        CompanionAutoOpen.load(context)
        seedSettings(context)
    }

    /**
     * Once after an update from a version without synced settings: the map style and the tile
     * target chosen before go into the synced settings at time 0 (any change on the phone is
     * newer), so the phone shows them.
     */
    private fun seedSettings(context: Context) {
        val prefs = prefs(context)
        var seeded = settings
        prefs.getString(KEY_MAP_STYLE, null)?.let { style ->
            if (WatchSettingKeys.MAP_STYLE !in seeded) seeded = withSetting(seeded, WatchSettingKeys.MAP_STYLE, style, 0L)
        }
        prefs.getString(KEY_TILE_TARGET, null)?.let { target ->
            if (WatchSettingKeys.TILE_TARGET !in seeded) seeded = withSetting(seeded, WatchSettingKeys.TILE_TARGET, target, 0L)
        }
        if (seeded === settings) return
        settings = seeded
        prefs.edit().putString(KEY_SETTINGS, settingsJson(seeded)).apply()
        writeSettings(context.applicationContext, seeded)
    }

    /** Switches the style of the map pictures; the phone gets the change too. */
    fun chooseMapStyle(context: Context, value: MapStyleSetting) {
        prefs(context).edit().putString(KEY_MAP_STYLE, value.name).apply()
        mapStyle.value = value
        changeSetting(context, WatchSettingKeys.MAP_STYLE, value.name)
    }

    /** Switches the live departures of the start page; the phone gets the change too. */
    fun chooseLiveDepartures(context: Context, value: Boolean) {
        liveDepartures.value = value
        changeSetting(context, WatchSettingKeys.LIVE_DEPARTURES, value.toString())
    }

    /** Switches the animations of the watch app; the phone gets the change too. */
    fun chooseAnimations(context: Context, value: Boolean) {
        animations.value = value
        changeSetting(context, WatchSettingKeys.ANIMATIONS, value.toString())
    }

    /** Switches whether the companion opens by itself when the phone starts a trip; the phone gets the change too. */
    fun chooseOpenCompanion(context: Context, value: Boolean) {
        openCompanion.value = value
        changeSetting(context, WatchSettingKeys.OPEN_COMPANION, value.toString())
    }

    /** Switches the own icons of the complications (#253, #282); the phone gets the change too. */
    fun chooseStateIcons(context: Context, value: Boolean) {
        val changed = stateIcons.value != value
        stateIcons.value = value
        changeSetting(context, WatchSettingKeys.STATE_ICONS, value.toString())
        if (changed) onTripIconsChanged(context)
    }

    /** Switches the time travel pill of the trip overview (#254); the phone gets the change too. */
    fun chooseCrownTimeTravel(context: Context, value: Boolean) {
        crownTimeTravel.value = value
        changeSetting(context, WatchSettingKeys.CROWN_TIME_TRAVEL, value.toString())
    }

    /** The complications of the trip and the ongoing activity draw their icons anew: one request each, no polling. */
    private fun onTripIconsChanged(context: Context) {
        val app = context.applicationContext
        // Every complication shows the app's icon or its own (#282), not only those of the trip.
        Complications.requestUpdate(app, *ComplicationKind.values())
        TripOngoing.update(app, trip.value)
    }

    /**
     * Keeps a setting changed on the watch with the time now, and writes all of them for the
     * phone. The Data Layer keeps the item and hands it over once the phone is connected, so
     * a change made offline arrives later by itself.
     */
    private fun changeSetting(context: Context, key: String, value: String) {
        val changed = withSetting(settings, key, value, System.currentTimeMillis())
        if (changed === settings) return
        settings = changed
        prefs(context).edit().putString(KEY_SETTINGS, settingsJson(changed)).apply()
        writeSettings(context.applicationContext, changed)
    }

    private fun writeSettings(context: Context, values: Map<String, SyncedSetting>) {
        scope.launch {
            try {
                val request = PutDataMapRequest.create(Protocol.PATH_WATCH_SETTINGS_WATCH).apply {
                    dataMap.putString(Protocol.KEY_JSON, settingsJson(values))
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(request).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No Google Play services on this watch: the setting holds here alone.
            }
        }
    }

    /**
     * Takes the settings the phone wrote ([json] of [Protocol.PATH_WATCH_SETTINGS]): each newer
     * than the watch's own replaces it (see mergeSettings) and applies at once; what the watch
     * changed later stays. True when anything changed.
     */
    fun takePhoneSettings(context: Context, json: String): Boolean {
        val merged = mergeSettings(settings, parseSettings(json))
        if (merged == settings) return false
        settings = merged
        val prefs = prefs(context)
        val edit = prefs.edit().putString(KEY_SETTINGS, settingsJson(merged))
        liveDepartures.value = settingFlag(merged, WatchSettingKeys.LIVE_DEPARTURES, default = true)
        animations.value = settingFlag(merged, WatchSettingKeys.ANIMATIONS, default = true)
        openCompanion.value = settingFlag(merged, WatchSettingKeys.OPEN_COMPANION, default = false)
        crownTimeTravel.value = settingFlag(merged, WatchSettingKeys.CROWN_TIME_TRAVEL, default = false)
        val icons = settingFlag(merged, WatchSettingKeys.STATE_ICONS, default = false)
        if (icons != stateIcons.value) {
            stateIcons.value = icons
            onTripIconsChanged(context)
        }
        merged[WatchSettingKeys.MAP_STYLE]?.let { style ->
            val value = MapStyleSetting.parse(style.value)
            edit.putString(KEY_MAP_STYLE, value.name)
            mapStyle.value = value
        }
        merged[WatchSettingKeys.TILE_TARGET]?.let { target ->
            val place = target.value.takeIf { it.isNotEmpty() }?.let { runCatching { parsePlaceJson(JSONObject(it)) }.getOrNull() }
            if (place != null) edit.putString(KEY_TILE_TARGET, target.value) else edit.remove(KEY_TILE_TARGET)
            tileTargetChoice.value = place
        }
        edit.apply()
        return true
    }

    /** The size in pixels and the edge to spare of the change map last shown ([large]: the full screen one), for prefetching its pictures. */
    fun changeMapSize(context: Context, large: Boolean): IntArray? =
        prefs(context).getString(if (large) KEY_CHANGE_MAP_FULL else KEY_CHANGE_MAP, null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { (it.size == 3 || it.size == 4) && it[0] > 0 && it[1] > 0 }?.toIntArray()

    /** Width, height, pad and (from #149) the bottom band in pixels of the change map last shown; an older entry has no band. */
    fun saveChangeMapSize(context: Context, large: Boolean, width: Int, height: Int, pad: Int, bottomBand: Int = 0) {
        val text = "$width,$height,$pad,$bottomBand"
        val key = if (large) KEY_CHANGE_MAP_FULL else KEY_CHANGE_MAP
        val prefs = prefs(context)
        if (prefs.getString(key, null) != text) prefs.edit().putString(key, text).apply()
    }

    fun savePlaces(context: Context, json: String) {
        prefs(context).edit().putString(KEY_PLACES, json).apply()
        places.value = SyncedPlaces.parse(json)
        updateComfort(context)
        updateAlightReminders(context)
    }

    /** Switches the change time on the watch; it holds until the phone sends another one. */
    fun chooseComfort(context: Context, value: TransferComfort) {
        prefs(context).edit()
            .putString(KEY_COMFORT, value.name)
            .putString(KEY_COMFORT_BASE, places.value.phoneComfort?.name ?: "")
            .apply()
        comfort.value = value
    }

    /** Switches the reminder on the watch; it holds until the phone sends another setting. */
    fun chooseAlightReminders(context: Context, value: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_REMINDERS, value)
            .putString(KEY_REMINDERS_BASE, phoneRemindersText())
            .apply()
        alightReminders.value = value
    }

    /** The current setting, read from the store itself: for the alert, which may run before the app was opened. */
    fun alightRemindersOn(context: Context): Boolean {
        updateAlightReminders(context)
        return alightReminders.value
    }

    private fun phoneRemindersText(): String = places.value.phoneAlightReminders?.toString() ?: ""

    private fun updateAlightReminders(context: Context) {
        val prefs = prefs(context)
        // Places may not be parsed yet when an alert comes in before the app was opened.
        if (!places.value.synced) places.value = SyncedPlaces.parse(prefs.getString(KEY_PLACES, null))
        val phone = places.value.phoneAlightReminders
        val chosen = if (prefs.contains(KEY_REMINDERS)) prefs.getBoolean(KEY_REMINDERS, true) else null
        val base = prefs.getString(KEY_REMINDERS_BASE, null)
        alightReminders.value = if (chosen != null && base == phoneRemindersText()) chosen else phone ?: true
    }

    private fun updateComfort(context: Context) {
        val prefs = prefs(context)
        val phone = places.value.phoneComfort
        val chosen = TransferComfort.parse(prefs.getString(KEY_COMFORT, null))
        val base = prefs.getString(KEY_COMFORT_BASE, null)
        comfort.value = if (chosen != null && base == (phone?.name ?: "")) chosen else phone ?: TransferComfort.Normal
    }

    fun saveTrip(context: Context, snapshot: TripSnapshot) {
        prefs(context).edit().putString(KEY_TRIP, snapshot.toJson()).apply()
        trip.value = snapshot
        // The ongoing activity follows every state of the trip, of the phone or of the watch.
        TripOngoing.update(context.applicationContext, snapshot)
    }

    /**
     * Takes a trip state the phone wrote (its data item). A running trip of the phone wins over
     * one the watch follows itself, unless it is stale ([TripSnapshot.stale]) or the user
     * ended that very trip on the watch while the phone did not hear of it; then the phone is
     * asked again to end it. A trip the watch follows itself stays while the phone has none.
     * Returns true when [trip] changed.
     */
    fun takePhoneTrip(context: Context, snapshot: TripSnapshot): Boolean {
        val now = System.currentTimeMillis()
        val running = snapshot.active && !snapshot.stale(now)
        noteReceived(context, snapshot, running, now)
        if (running && endedOnWatch(context, snapshot)) {
            val app = context.applicationContext
            scope.launch { sendToPhone(app, Protocol.MSG_STOP_TRIP, ByteArray(0)) }
        } else if (running) {
            // The phone follows a trip: the watch stops following its own.
            LocalFollow.handOver(context)
            saveTrip(context, snapshot)
            return true
        }
        // The phone has no (live) trip: a trip of the phone shown here ends, one of the watch stays,
        // and so does one of another phone (see endsShownTrip).
        val shown = trip.value
        if (endsShownTrip(shown, snapshot, now)) {
            saveTrip(context, TripSnapshot.NONE)
            return true
        }
        return false
    }

    /**
     * When a state of the phone's trip last came in (epoch ms, 0: never) and whether it was a
     * running trip: shown in the settings of the watch, to tell "the phone sends nothing" from
     * "the watch drops it". Only the time and a flag, nothing of the trip itself.
     */
    val lastPhoneTrip = MutableStateFlow(PhoneTripSeen(0L, false))

    data class PhoneTripSeen(val at: Long, val running: Boolean)

    private fun noteReceived(context: Context, snapshot: TripSnapshot, running: Boolean, now: Long) {
        lastPhoneTrip.value = PhoneTripSeen(now, running)
        prefs(context).edit().putLong(KEY_PHONE_TRIP_AT, now).putBoolean(KEY_PHONE_TRIP_RUNNING, running).apply()
        android.util.Log.d(LOG_TAG, "phone trip state: active=${snapshot.active} running=$running kind=${snapshot.kind}")
    }

    /** Remembers the trip the user ended on the watch, so a late state of the phone does not bring it back. */
    fun rememberEnded(context: Context, key: String?) {
        prefs(context).edit()
            .putString(KEY_ENDED_KEY, key)
            .putLong(KEY_ENDED_AT, System.currentTimeMillis())
            .apply()
    }

    // The same trip, begun on the phone before it was ended here; a new start of it there counts again.
    private fun endedOnWatch(context: Context, snapshot: TripSnapshot): Boolean {
        val key = snapshot.tripKey ?: return false
        val prefs = prefs(context)
        if (prefs.getString(KEY_ENDED_KEY, null) != key) return false
        return snapshot.started <= prefs.getLong(KEY_ENDED_AT, 0L)
    }

    /** The ride the watch last vibrated for, so every ride alerts once only. */
    fun takeAlert(context: Context, key: String): Boolean {
        val prefs = prefs(context)
        if (key.isEmpty() || prefs.getString(KEY_ALERTED, null) == key) return false
        prefs.edit().putString(KEY_ALERTED, key).apply()
        return true
    }

    /** A favourite stop chosen for the tile and the complication: the first favourite that is a stop. */
    fun tileStop(): Place? =
        places.value.favorites.firstOrNull { it.place.kind == PlaceKind.Stop && !it.place.id.contains(':') }?.place
            ?: places.value.saved.firstOrNull { it.place.kind == PlaceKind.Stop && !it.place.id.contains(':') }?.place

    /**
     * The destination of the tile "To a favourite": the one chosen in the app, else the
     * favourite marked as home on the phone, else null (the tile then asks to choose one).
     */
    fun tileTarget(context: Context): Place? = tileTarget(chosenTileTarget(context), places.value)

    /** [tileTarget] from what is known already, for the screens: [tileTargetChoice] and [places]. */
    fun tileTarget(chosen: Place?, places: SyncedPlaces): Place? =
        chosen ?: (places.favorites + places.saved).firstOrNull { it.icon == "Home" }?.place

    private fun chosenTileTarget(context: Context): Place? =
        prefs(context).getString(KEY_TILE_TARGET, null)
            ?.let { runCatching { parsePlaceJson(JSONObject(it)) }.getOrNull() }

    fun saveTileTarget(context: Context, place: Place) {
        val json = placeJson(place).toString()
        prefs(context).edit().putString(KEY_TILE_TARGET, json).apply()
        tileTargetChoice.value = place
        changeSetting(context, WatchSettingKeys.TILE_TARGET, json)
    }

    /**
     * Reads the data items the phone wrote, checks whether a phone with Next Stop is
     * connected ([phone]) and asks it to write them again. Called on every start and resume
     * of the app and by the retry buttons. Nothing happens without a paired phone; what is
     * stored stays.
     */
    suspend fun refresh(context: Context) {
        try {
            val items = Wearable.getDataClient(context).dataItems.await()
            // Every phone that ever wrote the trip keeps its own item: the one to take is chosen
            // from all of them (see choosePhoneTrip), not the last one read.
            val trips = mutableListOf<TripSnapshot>()
            try {
                for (item in items) {
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(Protocol.KEY_JSON) ?: continue
                    when (item.uri.path) {
                        Protocol.PATH_PLACES -> savePlaces(context, json)
                        Protocol.PATH_TRIP -> trips += TripSnapshot.parse(json).copy(node = item.uri.host)
                        Protocol.PATH_WATCH_SETTINGS -> takePhoneSettings(context, json)
                    }
                }
            } finally {
                items.release()
            }
            choosePhoneTrip(trips, System.currentTimeMillis())?.let { takePhoneTrip(context, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Google Play services on this watch: keep what is stored.
        }
        sendToPhone(context, Protocol.MSG_SYNC, ByteArray(0))
    }

    /**
     * The phones to talk to: those that declare Next Stop, else (an older phone app without
     * the capability) every connected one. Updates [phone].
     */
    private suspend fun phoneNodes(context: Context): List<Node> {
        val connected = try {
            Wearable.getNodeClient(context).connectedNodes.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val capable = try {
            Wearable.getCapabilityClient(context)
                .getCapability(Protocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        phone.value = when {
            capable.isNotEmpty() -> PhoneLink.Connected
            connected.isNotEmpty() -> PhoneLink.NoApp
            else -> PhoneLink.NotConnected
        }
        return capable.ifEmpty { connected }
    }

    /**
     * Sends a message to the phone (see [phoneNodes]); false when none was reached. With
     * [single] only to one phone, a nearby one first (a request that must be answered once,
     * such as a map picture, which would else be drawn by every phone).
     */
    suspend fun sendToPhone(context: Context, path: String, payload: ByteArray, single: Boolean = false): Boolean {
        var sent = false
        val nodes = phoneNodes(context).sortedByDescending { it.isNearby }
        for (node in nodes) {
            if (single && sent) break
            try {
                Wearable.getMessageClient(context).sendMessage(node.id, path, payload).await()
                sent = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // This node is gone; try the next one.
            }
        }
        return sent
    }

    /** Asks the phone to follow the journey with this handle (see [Protocol.MSG_START_TRIP]). */
    suspend fun startOnPhone(context: Context, token: String): Boolean =
        sendToPhone(context, Protocol.MSG_START_TRIP, JSONObject().put("token", token).toString().toByteArray())

    /** The uri of a data item of the phone, for listeners that filter by path (scheme "wear", as PutDataRequest.WEAR_URI_SCHEME). */
    fun uri(path: String): Uri = Uri.Builder().scheme("wear").path(path).build()

    private const val KEY_PLACES = "places"
    private const val KEY_TRIP = "trip"
    private const val KEY_PHONE_TRIP_AT = "phone-trip-at"
    private const val KEY_PHONE_TRIP_RUNNING = "phone-trip-running"
    private const val KEY_ENDED_KEY = "ended-trip"
    private const val KEY_ENDED_AT = "ended-at"
    private const val KEY_ALERTED = "alerted"
    private const val KEY_TILE_TARGET = "tile-target"
    private const val KEY_COMFORT = "comfort"
    private const val KEY_COMFORT_BASE = "comfort-base"
    private const val KEY_REMINDERS = "alight-reminders"
    private const val KEY_REMINDERS_BASE = "alight-reminders-base"
    private const val KEY_MAP_STYLE = "map-style"
    private const val KEY_SETTINGS = "synced-settings"
    private const val KEY_CHANGE_MAP = "change-map-size"
    private const val KEY_CHANGE_MAP_FULL = "change-map-full-size"
}
