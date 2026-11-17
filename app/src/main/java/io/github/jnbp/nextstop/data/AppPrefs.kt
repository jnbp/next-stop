package io.github.jnbp.nextstop.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import io.github.jnbp.nextstop.ui.theme.ACCENT_PRESETS
import io.github.jnbp.nextstop.ui.theme.ColorChoice
import io.github.jnbp.nextstop.ui.theme.DarkChoice
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import io.github.jnbp.nextstop.ui.theme.NotificationLook
import io.github.jnbp.nextstop.ui.theme.MapVehiclesChoice
import io.github.jnbp.nextstop.ui.theme.VehicleDirectionChoice
import io.github.jnbp.nextstop.ui.theme.mapVehiclesChoice
import io.github.jnbp.nextstop.widget.WidgetColorChoice

/**
 * How a time with a delay is shown everywhere in the app.
 * - [RealtimeAndDelay]: the expected time (delay included) with "+2" beside it (default).
 * - [PlannedAndDelay]: the timetable time with "+2" beside it.
 * - [RealtimeOnly]: the expected time alone, red when late and green when on time.
 */
enum class DelayDisplay { RealtimeAndDelay, PlannedAndDelay, RealtimeOnly }

/**
 * Where the connections chart shows the total time of each journey, door to door: nowhere,
 * right above or below its column (moving with it), or on one line pinned to the top or the
 * bottom edge of the chart, where all of them can be compared (moving sideways with their columns).
 */
enum class ChartTotalTime {
    Off, Above, Below, PinnedTop, PinnedBottom;

    companion object {
        /** The setting stored as [name]: "Top" and "Bottom" of before are [Above] and [Below]; [Below] unless one is stored. */
        fun fromStored(name: String?): ChartTotalTime = when (name) {
            "Top" -> Above
            "Bottom" -> Below
            else -> entries.firstOrNull { it.name == name } ?: Below
        }
    }
}

/**
 * How the connections chart draws rides too short for a block of their own, always where their
 * times say: [Separate], each as its own block, smaller step by step (without the minutes of the
 * change, without its name, at last a thin bar); [Combine], a run of them as one block with the
 * colours and names of their lines.
 */
enum class ChartShortLegs { Separate, Combine }

/** Small settings and saved places, kept on the device only. */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Access keys of data backends: a file of their own, left out of every backup (see
    // res/xml/data_extraction_rules.xml), so they stay on this device.
    private val keys = context.getSharedPreferences(KEYS_PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // The old switch "show delays separately" becomes the delay display once.
        if (!prefs.contains(KEY_DELAY_DISPLAY) && prefs.contains(KEY_DELAY_SEPARATE)) {
            val planned = prefs.getBoolean(KEY_DELAY_SEPARATE, false)
            prefs.edit()
                .putString(KEY_DELAY_DISPLAY, (if (planned) DelayDisplay.PlannedAndDelay else DelayDisplay.RealtimeAndDelay).name)
                .remove(KEY_DELAY_SEPARATE)
                .apply()
        }
        // The address of an own DB server, a setting the app no longer has.
        if (prefs.contains(LEGACY_KEY_DB_SERVER)) prefs.edit().remove(LEGACY_KEY_DB_SERVER).apply()
        // The departures card of the start page is off for new installs. An install from before
        // that, with settings of its own but none for the card, keeps it on as it was.
        if (!prefs.contains(KEY_DEFAULTS_2)) {
            val edit = prefs.edit()
            if (prefs.all.isNotEmpty() && !prefs.contains(KEY_START_DEPARTURES)) edit.putBoolean(KEY_START_DEPARTURES, true)
            edit.putBoolean(KEY_DEFAULTS_2, true).apply()
        }
    }

    /**
     * How a time with a delay is shown (see DelayDisplay). Replaces the old switch "show delays
     * separately": an install that had it on keeps the timetable time with the delay beside it.
     */
    var delayDisplay: DelayDisplay
        get() = DelayDisplay.entries.firstOrNull { it.name == prefs.getString(KEY_DELAY_DISPLAY, null) }
            ?: if (prefs.getBoolean(KEY_DELAY_SEPARATE, false)) DelayDisplay.PlannedAndDelay else DelayDisplay.RealtimeAndDelay
        set(value) = prefs.edit().putString(KEY_DELAY_DISPLAY, value.name).remove(KEY_DELAY_SEPARATE).apply()

    /** True: tapping a vehicle moves the map to show its whole line. */
    var fitLine: Boolean
        get() = prefs.getBoolean(KEY_FIT_LINE, true)
        set(value) = prefs.edit().putBoolean(KEY_FIT_LINE, value).apply()

    /**
     * Which live vehicles the map shows: all, only zoomed in close (the default), or none (no
     * vehicles asked for or drawn, which spares less powerful phones). Taken over from the
     * old switch (on: all, off: none) where it was stored, until a level is chosen.
     */
    var mapVehicles: MapVehiclesChoice
        get() = mapVehiclesChoice(
            prefs.getString(KEY_MAP_VEHICLES_LEVEL, null),
            if (prefs.contains(KEY_MAP_VEHICLES)) prefs.getBoolean(KEY_MAP_VEHICLES, true) else null,
        )
        set(value) = prefs.edit().putString(KEY_MAP_VEHICLES_LEVEL, value.name).remove(KEY_MAP_VEHICLES).apply()

    /** True: the start page shows the vehicles on the road around the user. */
    var startVehicles: Boolean
        get() = prefs.getBoolean(KEY_START_VEHICLES, true)
        set(value) = prefs.edit().putBoolean(KEY_START_VEHICLES, value).apply()

    /** True: close up, bus and tram stops show their name in green on the map instead of the "H". */
    var stopNames: Boolean
        get() = prefs.getBoolean(KEY_STOP_NAMES, false)
        set(value) = prefs.edit().putBoolean(KEY_STOP_NAMES, value).apply()

    /** True: the main maps show a warning sign at the stops known disruptions are about (see MapNoticeLayer). */
    var mapNotices: Boolean
        get() = prefs.getBoolean(KEY_MAP_NOTICES, true)
        set(value) = prefs.edit().putBoolean(KEY_MAP_NOTICES, value).apply()

    /** True: the map shows stop positions, station entrances and lifts from OpenStreetMap close up. */
    var stationDetails: Boolean
        get() = prefs.getBoolean(KEY_STATION_DETAILS, true)
        set(value) = prefs.edit().putBoolean(KEY_STATION_DETAILS, value).apply()

    /**
     * Which station entrances, lifts and escalators the map shows: "auto" (by zoom, the marked
     * station earlier), "always" (all from zoom 16) or "marked" (only those of the marked station).
     */
    var entranceMode: String
        get() = prefs.getString(KEY_ENTRANCE_MODE, null) ?: "auto"
        set(value) = prefs.edit().putString(KEY_ENTRANCE_MODE, value).apply()

    /** True: the start page lies over the map; false: over a plain ground, and the map rests. */
    var startMap: Boolean
        get() = prefs.getBoolean(KEY_START_MAP, true)
        set(value) = prefs.edit().putBoolean(KEY_START_MAP, value).apply()

    /** True: the start page in its classic layout of floating cards; false: in the sheet. */
    var startClassic: Boolean
        get() = prefs.getBoolean(KEY_START_CLASSIC, false)
        set(value) = prefs.edit().putBoolean(KEY_START_CLASSIC, value).apply()

    /** True: the start sheet shows its cards (recent, favourite and calendar tiles); false: only the essentials. */
    var startCards: Boolean
        get() = prefs.getBoolean(KEY_START_CARDS, true)
        set(value) = prefs.edit().putBoolean(KEY_START_CARDS, value).apply()

    /** True: the start page shows the card with the departures around the user, apart from the other cards. */
    var startDepartures: Boolean
        get() = prefs.getBoolean(KEY_START_DEPARTURES, false)
        set(value) = prefs.edit().putBoolean(KEY_START_DEPARTURES, value).apply()

    /**
     * How far the departures of "all stops nearby" reach, in metres: [NEARBY_RADIUS_MIN] to
     * [NEARBY_RADIUS_MAX] in steps of [NEARBY_RADIUS_STEP] (see [nearbyRadiusOf]). A choice of
     * the former 300, 500 or 800 m stays as it was.
     */
    var nearbyRadius: Int
        get() = nearbyRadiusOf(prefs.getInt(KEY_NEARBY_RADIUS, NEARBY_RADIUS_DEFAULT))
        set(value) = prefs.edit().putInt(KEY_NEARBY_RADIUS, nearbyRadiusOf(value)).apply()

    /** True: the departures tab lists the departures of all stops nearby instead of one stop. */
    var nearbyDepartures: Boolean
        get() = prefs.getBoolean(KEY_NEARBY_DEPARTURES, false)
        set(value) = prefs.edit().putBoolean(KEY_NEARBY_DEPARTURES, value).apply()

    /** "Wann los?" on the departures tab: the list by the latest time to set off on foot, not by departure. */
    var leaveOrder: Boolean
        get() = prefs.getBoolean(KEY_LEAVE_ORDER, false)
        set(value) = prefs.edit().putBoolean(KEY_LEAVE_ORDER, value).apply()

    /**
     * The address of an own server for Berlin data (hafas-rest-api v6, as bvg-rest or db-rest)
     * as the user wrote it; empty for the public one (see ownServerUrl).
     */
    var ownServer: String
        get() = prefs.getString(KEY_OWN_SERVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OWN_SERVER, value).apply()

    /**
     * The address of the web pages (web/live and web/route of the repository) for shared live and
     * route links, as the user wrote it; empty for the project website (see siteAddress in
     * ui/JourneyShare.kt).
     */
    var liveBaseUrl: String
        get() = prefs.getString(KEY_LIVE_BASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIVE_BASE_URL, value).apply()

    /**
     * The first name the live page shows ("<name> ist unterwegs nach …"), as typed; empty for
     * none. Kept with the keys, outside every backup: it only leaves the phone inside a link.
     */
    var liveName: String
        get() = keys.getString(KEY_LIVE_NAME, "") ?: ""
        set(value) = keys.edit().putString(KEY_LIVE_NAME, value.take(LIVE_NAME_MAX)).apply()

    /** True once the user was asked for [liveName] (setup wizard or the first live link). */
    var liveNameAsked: Boolean
        get() = prefs.getBoolean(KEY_LIVE_NAME_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_NAME_ASKED, value).apply()

    /** True: the start page shows the card of the next appointment (or asks for the calendar). */
    var startCalendar: Boolean
        get() = prefs.getBoolean(KEY_START_CALENDAR, true)
        set(value) = prefs.edit().putBoolean(KEY_START_CALENDAR, value).apply()

    /** The stage the sheet of the start page was left in, by name ("Away", "Half", "Full"); null at first. */
    var startSheetStage: String?
        get() = prefs.getString(KEY_START_SHEET_STAGE, null)
        set(value) = prefs.edit().putString(KEY_START_SHEET_STAGE, value).apply()

    /** True: changes say where to board the arriving train, in the app and in the notification. */
    var boardingHints: Boolean
        get() = prefs.getBoolean(KEY_BOARDING_HINTS, true)
        set(value) = prefs.edit().putBoolean(KEY_BOARDING_HINTS, value).apply()

    /**
     * True: the slider of transfer levels shows over the connections and the plan (for those who
     * want to pick). False, the default: searches show normal and relaxed journeys, no selector.
     */
    var transferChoice: Boolean
        get() = prefs.getBoolean(KEY_TRANSFER_CHOICE, false)
        set(value) = prefs.edit().putBoolean(KEY_TRANSFER_CHOICE, value).apply()

    /** True: a search first shows its options on the map; false: it opens the connections at once. */
    var planFirst: Boolean
        get() = prefs.getBoolean(KEY_PLAN_FIRST, true)
        set(value) = prefs.edit().putBoolean(KEY_PLAN_FIRST, value).apply()

    /** True: connection detail and companion offer other connecting departures at changes. */
    var connectionChoice: Boolean
        get() = prefs.getBoolean(KEY_CONNECTION_CHOICE, true)
        set(value) = prefs.edit().putBoolean(KEY_CONNECTION_CHOICE, value).apply()

    /** True: the small maps of walks in the companion and the journey detail start unfolded. */
    var walkMapsOpen: Boolean
        get() = prefs.getBoolean(KEY_WALK_MAPS_OPEN, false)
        set(value) = prefs.edit().putBoolean(KEY_WALK_MAPS_OPEN, value).apply()

    /**
     * True: the connections show the chart or the list at full height and a picked journey opens
     * on a page of its own; false: overview and detail share the screen. Full screen is the
     * default; a choice made in the setup or the settings is stored and kept.
     */
    var connectionsFull: Boolean
        get() = prefs.getBoolean(KEY_CONNECTIONS_FULL, true)
        set(value) = prefs.edit().putBoolean(KEY_CONNECTIONS_FULL, value).apply()

    /** True: the chart names the stop of every change between two rides; false: only the minutes. */
    var chartChangeStops: Boolean
        get() = prefs.getBoolean(KEY_CHART_CHANGE_STOPS, false)
        set(value) = prefs.edit().putBoolean(KEY_CHART_CHANGE_STOPS, value).apply()

    /** Where the chart shows the total time of each journey (see ChartTotalTime); below the arrival time unless changed. */
    var chartTotalTime: ChartTotalTime
        get() = ChartTotalTime.fromStored(prefs.getString(KEY_CHART_TOTAL_TIME, null))
        set(value) = prefs.edit().putString(KEY_CHART_TOTAL_TIME, value.name).apply()

    /** How the chart draws short rides (see ChartShortLegs); each by itself unless changed. */
    var chartShortLegs: ChartShortLegs
        get() = ChartShortLegs.entries.firstOrNull { it.name == prefs.getString(KEY_CHART_SHORT_LEGS, null) } ?: ChartShortLegs.Separate
        set(value) = prefs.edit().putString(KEY_CHART_SHORT_LEGS, value.name).apply()

    /** True: the connections are shown as a list; false: as the chart. */
    var connectionsList: Boolean
        get() = prefs.getBoolean(KEY_CONNECTIONS_LIST, false)
        set(value) = prefs.edit().putBoolean(KEY_CONNECTIONS_LIST, value).apply()

    /** True once the setup on first launch was finished or skipped. */
    var setupDone: Boolean
        get() = prefs.getBoolean(KEY_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_DONE, value).apply()

    /** True: a followed trip alerts one stop before the user gets off, and again shortly before arrival. */
    var alightReminders: Boolean
        get() = prefs.getBoolean(KEY_ALIGHT_REMINDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_ALIGHT_REMINDERS, value).apply()

    /** True once the developer options were unlocked by tapping the version. */
    var developerOptions: Boolean
        get() = prefs.getBoolean(KEY_DEVELOPER, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVELOPER, value).apply()

    /** Developer option: "Every minute counts" offers a made-up faster way, marked as a simulation. */
    var simulateFaster: Boolean
        get() = developerOptions && prefs.getBoolean(KEY_SIMULATE_FASTER, false)
        set(value) = prefs.edit().putBoolean(KEY_SIMULATE_FASTER, value).apply()

    /** True once the dialog about very risky changes was shown. */
    var veryRiskyHintSeen: Boolean
        get() = prefs.getBoolean(KEY_VERY_RISKY_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_VERY_RISKY_HINT, value).apply()

    /**
     * "Abkürzungs-Alarm" (shortcut alert): every followed trip searches again in the background
     * at the user's transfer level and notifies when something faster turns up. Off by default.
     */
    var shortcutAlert: Boolean
        get() = prefs.getBoolean(KEY_SHORTCUT_ALERT, false)
        set(value) = prefs.edit().putBoolean(KEY_SHORTCUT_ALERT, value).apply()

    /**
     * "Nur gleich bequeme Alternativen", an option of the shortcut alert: only ways with no
     * more changes ahead than the followed one and within the user's transfer level. Not
     * with "Jede Minute zählt". Off by default.
     */
    var shortcutSameComfort: Boolean
        get() = prefs.getBoolean(KEY_SHORTCUT_SAME_COMFORT, false)
        set(value) = prefs.edit().putBoolean(KEY_SHORTCUT_SAME_COMFORT, value).apply()

    /**
     * "Ruhe nach dem Start", an option of the shortcut alert: in the first 3 minutes of a trip
     * only ways at least 3 minutes faster. Not with "Jede Minute zählt". Off by default.
     */
    var shortcutQuietStart: Boolean
        get() = prefs.getBoolean(KEY_SHORTCUT_QUIET_START, false)
        set(value) = prefs.edit().putBoolean(KEY_SHORTCUT_QUIET_START, value).apply()
    /** True: the map shows the small hint that vehicle positions come from live data, not GPS. */
    var vehicleHint: Boolean
        get() = prefs.getBoolean(KEY_VEHICLE_HINT, true)
        set(value) = prefs.edit().putBoolean(KEY_VEHICLE_HINT, value).apply()

    /** True once the dialog about the mode "Every minute counts" was shown. */
    var everyMinuteHintSeen: Boolean
        get() = prefs.getBoolean(KEY_EVERY_MINUTE_HINT, false)
        set(value) = prefs.edit().putBoolean(KEY_EVERY_MINUTE_HINT, value).apply()

    /**
     * Which backends the app asks, in which order, and their access keys (see [SourceChoice]).
     * Unknown keys are left out. Before the ordered list there was a switch "automatic" and a
     * set of families ("bvg", "transitous"; Deutsche Bahn "db" counted as Transitous): a family
     * switched off there switches off all its backends here.
     */
    var sourceChoice: SourceChoice
        get() {
            val order = prefs.getString(KEY_SOURCE_ORDER, null)?.split(",")
                ?.mapNotNull { key -> Backend.entries.firstOrNull { it.key == key } }
                ?: Backend.entries
            val enabledKeys = prefs.getString(KEY_SOURCES_ENABLED, null)?.split(",")?.toSet()
            val enabled = if (enabledKeys != null) {
                Backend.entries.filter { it.key in enabledKeys }.toSet()
            } else {
                legacyEnabled()
            }
            return SourceChoice(
                order = order,
                enabled = enabled,
                vbbAccessId = keys.getString(KEY_VBB_ACCESS_ID, "") ?: "",
                pteAuthorization = keys.getString(KEY_PTE_AUTHORIZATION, "") ?: "",
                builtInVbbAccessId = BuiltInKeys.vbbAccessId,
            )
        }
        set(value) {
            prefs.edit()
                .putString(KEY_SOURCE_ORDER, value.ordered.joinToString(",") { it.key })
                .putString(KEY_SOURCES_ENABLED, value.enabled.joinToString(",") { it.key })
                .apply()
            keys.edit()
                .putString(KEY_VBB_ACCESS_ID, value.vbbAccessId)
                .putString(KEY_PTE_AUTHORIZATION, value.pteAuthorization)
                .apply()
        }

    /** The backends switched on by the settings of before the ordered list. */
    private fun legacyEnabled(): Set<Backend> {
        if (prefs.getBoolean(KEY_SOURCES_AUTOMATIC, true)) return SourceChoice.DEFAULT_ENABLED
        val families = prefs.getString(KEY_SOURCES, null)?.split(",")
            ?.map { if (it == LEGACY_DB_SOURCE) DataSource.Transitous.key else it }
            ?.toSet()
            ?: return SourceChoice.DEFAULT_ENABLED
        return Backend.entries.filter { it.family.key in families }.toSet().ifEmpty { SourceChoice.DEFAULT_ENABLED }
    }

    /** Where the colours of the app come from. */
    var colorChoice: ColorChoice
        get() = ColorChoice.entries.firstOrNull { it.name == prefs.getString(KEY_COLOR_CHOICE, null) } ?: ColorChoice.NextStop
        set(value) = prefs.edit().putString(KEY_COLOR_CHOICE, value.name).apply()

    /** Light, dark, or as the system is set. */
    var darkChoice: DarkChoice
        get() = DarkChoice.entries.firstOrNull { it.name == prefs.getString(KEY_DARK_CHOICE, null) } ?: DarkChoice.System
        set(value) = prefs.edit().putString(KEY_DARK_CHOICE, value.name).apply()

    /** The look of the map; automatic (light or dark with the app) when nothing was chosen. */
    var mapStyle: MapStyleChoice
        get() = MapStyleChoice.entries.firstOrNull { it.name == prefs.getString(KEY_MAP_STYLE, null) } ?: MapStyleChoice.Auto
        set(value) = prefs.edit().putString(KEY_MAP_STYLE, value.name).apply()

    /** Whether the vehicles on the map show the way they head; off when nothing was chosen. */
    var vehicleDirection: VehicleDirectionChoice
        get() = VehicleDirectionChoice.entries.firstOrNull { it.name == prefs.getString(KEY_VEHICLE_DIRECTION, null) }
            ?: VehicleDirectionChoice.Off
        set(value) = prefs.edit().putString(KEY_VEHICLE_DIRECTION, value.name).apply()

    /** The accent colour of an own colour scheme, as ARGB; unknown values fall back to the first preset. */
    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT, ACCENT_PRESETS.first()).takeIf { it in ACCENT_PRESETS } ?: ACCENT_PRESETS.first()
        set(value) = prefs.edit().putInt(KEY_ACCENT, value).apply()

    /**
     * How a followed trip shows in the notifications. A Live Update needs Android 16; before
     * that the trip always shows in the look of the app.
     */
    var notificationLook: NotificationLook
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return NotificationLook.AppLook
            return NotificationLook.entries.firstOrNull { it.name == prefs.getString(KEY_NOTIFICATION_LOOK, null) }
                ?: NotificationLook.LiveUpdate
        }
        set(value) = prefs.edit().putString(KEY_NOTIFICATION_LOOK, value.name).apply()

    /** The colours of the home screen widgets that follow the common setting. */
    var widgetColors: WidgetColorChoice
        get() = WidgetColorChoice.entries.firstOrNull { it.name == prefs.getString(KEY_WIDGET_COLORS, null) }
            ?: WidgetColorChoice.NextStop
        set(value) = prefs.edit().putString(KEY_WIDGET_COLORS, value.name).apply()

    /**
     * The last position the app knew while it was open, for the widgets: in the background
     * Android often gives no position at all. Null until the app had one.
     */
    var widgetPosition: Pair<Double, Double>?
        get() {
            if (!prefs.contains(KEY_WIDGET_LAT) || !prefs.contains(KEY_WIDGET_LON)) return null
            return prefs.getFloat(KEY_WIDGET_LAT, 0f).toDouble() to prefs.getFloat(KEY_WIDGET_LON, 0f).toDouble()
        }
        set(value) {
            val edit = prefs.edit()
            if (value == null) {
                edit.remove(KEY_WIDGET_LAT).remove(KEY_WIDGET_LON)
            } else {
                edit.putFloat(KEY_WIDGET_LAT, value.first.toFloat()).putFloat(KEY_WIDGET_LON, value.second.toFloat())
            }
            edit.apply()
        }

    /**
     * When [widgetPosition] was taken, in epoch milliseconds; 0 where unknown (a position
     * stored before this was kept). Written together with it by recordWidgetPosition.
     */
    var widgetPositionAt: Long
        get() = prefs.getLong(KEY_WIDGET_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_WIDGET_AT, value).apply()

    /** What every search uses; unknown or missing values fall back to the defaults. */
    /**
     * The options a search outside the open page uses (widgets, tiles): [searchOptions], with
     * normal change times unless the user chose to pick transfer times ([transferChoice]).
     */
    val searchOptionsInUse: SearchOptions
        get() = searchOptions.let { if (transferChoice) it else it.copy(comfort = TransferComfort.Normal) }

    /**
     * "Only Deutschlandticket" for every search: not a setting of its own but the products of
     * [searchOptions] without long-distance trains and coaches (see [withLocalOnly]).
     */
    var localOnlyDefault: Boolean
        get() = searchOptions.localOnly
        set(value) {
            searchOptions = searchOptions.withLocalOnly(value)
        }

    /**
     * The default of "With bicycle" for every search. Kept apart from [searchOptions], whose
     * setter leaves it alone: a search can switch it for itself only.
     */
    var bikeDefault: Boolean
        get() = prefs.getBoolean(KEY_BIKE, false)
        set(value) = prefs.edit().putBoolean(KEY_BIKE, value).apply()

    /** The default of the bike combo for every search; as [bikeDefault]. */
    var bikeComboDefault: Boolean
        get() = prefs.getBoolean(KEY_BIKE_COMBO, false)
        set(value) = prefs.edit().putBoolean(KEY_BIKE_COMBO, value).apply()

    /**
     * The stored products. Stored before long-distance trains and coaches had keys of their own,
     * "regional" stood for long-distance trains too and the Deutschlandticket was a switch of its
     * own ([KEY_LOCAL_ONLY]): such a list gets both, unless the switch was on or regional trains off.
     */
    private fun storedProducts(): Set<String>? {
        val raw = prefs.getString(KEY_PRODUCTS, null)?.split(",")?.map { it.trim() } ?: return null
        val known = raw.filter { it in PRODUCT_KEYS }.toSet()
        if (prefs.getBoolean(KEY_PRODUCTS_SPLIT, false)) return known.takeIf { it.isNotEmpty() }
        val migrated = if ("regional" in known && !prefs.getBoolean(KEY_LOCAL_ONLY, false)) known + LONG_DISTANCE_PRODUCTS else known
        return migrated.takeIf { it.isNotEmpty() }
    }

    /**
     * What every search uses; the bicycle options are the defaults ([bikeDefault],
     * [bikeComboDefault]), "Only Deutschlandticket" is in the products.
     */
    var searchOptions: SearchOptions
        get() {
            // Never stored: all products, with the Deutschlandticket switch of old without the long-distance ones.
            val products = storedProducts()
                ?: if (prefs.getBoolean(KEY_LOCAL_ONLY, false) && !prefs.getBoolean(KEY_PRODUCTS_SPLIT, false)) {
                    PRODUCT_KEYS.toSet() - LONG_DISTANCE_PRODUCTS
                } else {
                    null
                }
            return SearchOptions(
                comfort = TransferComfort.entries.firstOrNull { it.name == prefs.getString(KEY_COMFORT, null) }
                    ?: TransferComfort.Normal,
                products = products ?: PRODUCT_KEYS.toSet(),
                // "Langsamer unterwegs"; before it, a walking speed of slow, normal or fast: slow turns it on.
                slower = if (prefs.contains(KEY_SLOWER)) {
                    prefs.getBoolean(KEY_SLOWER, false)
                } else {
                    prefs.getString(KEY_WALKING, null) == WalkingSpeed.Slow.name
                },
                accessibility = Accessibility.entries
                    .firstOrNull { it.name == prefs.getString(KEY_ACCESSIBILITY, null) }
                    ?: Accessibility.None,
                bike = prefs.getBoolean(KEY_BIKE, false),
                bikeCombo = prefs.getBoolean(KEY_BIKE_COMBO, false),
            )
        }
        set(value) {
            prefs.edit()
                .putString(KEY_COMFORT, value.comfort.name)
                .putString(KEY_PRODUCTS, value.products.joinToString(","))
                .putBoolean(KEY_PRODUCTS_SPLIT, true)
                .putBoolean(KEY_SLOWER, value.slower)
                .remove(KEY_WALKING)
                .putString(KEY_ACCESSIBILITY, value.accessibility.name)
                // The bicycle options as the defaults of the next searches ([bikeDefault], [bikeComboDefault]).
                .putBoolean(KEY_BIKE, value.bike)
                .putBoolean(KEY_BIKE_COMBO, value.bikeCombo)
                .apply()
            applyWalkSettings(value)
        }

    /**
     * The tuning of the transfer rating (buffers for Normal and Relaxed, the change buffer);
     * the defaults of [TransferTuning] until set. Setting it also makes it the
     * one every rating uses (see [applyTransferTuning]).
     */
    var transferTuning: TransferTuning
        get() = TransferTuning(
            normalSpare = prefs.getLong(KEY_NORMAL_SPARE, NORMAL_SPARE),
            relaxedSpare = prefs.getLong(KEY_RELAXED_SPARE, RELAXED_SPARE),
            changeBufferSeconds = prefs.getLong(KEY_CHANGE_BUFFER, EXACT_WALK_EXTRA_SECONDS),
        ).bounded()
        set(value) {
            val bounded = value.bounded()
            prefs.edit()
                .putLong(KEY_NORMAL_SPARE, bounded.normalSpare)
                .putLong(KEY_RELAXED_SPARE, bounded.relaxedSpare)
                .putLong(KEY_CHANGE_BUFFER, bounded.changeBufferSeconds)
                .apply()
            setTransferTuning(bounded)
        }

    /** Makes the stored [transferTuning] the one every rating uses; once when the app starts. */
    fun applyTransferTuning() = setTransferTuning(transferTuning)

    /**
     * Makes everything stored that the rating of changes and the walks depend on the one in use:
     * the [transferTuning], "Langsamer unterwegs" ([walkSpeedFactor]) and step-free walks
     * ([stepFreeWalks]). Called once when the process starts (NextStopApp), so a widget, the
     * trip service or the bridge to the watch rate a change like the app does even when the
     * app itself was never opened; the setters keep them in step afterwards.
     */
    fun applyRatingSettings() {
        applyTransferTuning()
        applyWalkSettings(searchOptions)
    }

    private fun applyWalkSettings(options: SearchOptions) {
        walkSpeedFactor = walkFactor(options.slower)
        stepFreeWalks = options.accessibility != Accessibility.None
    }

    fun loadPlaces(key: String): List<Stop> {
        val text = prefs.getString(key, null) ?: return emptyList()
        return try {
            val list = JSONArray(text)
            (0 until list.length()).mapNotNull { index -> fromJson(list.getJSONObject(index)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlaces(key: String, places: List<Stop>) {
        val list = JSONArray()
        places.forEach { list.put(toJson(it)) }
        prefs.edit().putString(key, list.toString()).apply()
    }

    /**
     * The favourites of several stops at once (see StopGroup), apart from the favourite stops
     * ([KEY_FAVORITES], read as before by everything that lists single stops).
     */
    fun loadStopGroups(): List<StopGroup> {
        val text = prefs.getString(KEY_STOP_GROUPS, null) ?: return emptyList()
        return try {
            val list = JSONArray(text)
            val groups = (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val stops = item.optJSONArray("stops") ?: return@mapNotNull null
                StopGroup(
                    stops = (0 until stops.length()).mapNotNull { at -> fromJson(stops.getJSONObject(at)) },
                    name = item.optString("name", ""),
                )
            }
            storedStopGroups(groups)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveStopGroups(groups: List<StopGroup>) {
        val list = JSONArray()
        groups.forEach { group ->
            val item = JSONObject()
            item.put("name", group.name)
            val stops = JSONArray()
            group.stops.forEach { stops.put(toJson(it)) }
            item.put("stops", stops)
            list.put(item)
        }
        prefs.edit().putString(KEY_STOP_GROUPS, list.toString()).apply()
    }

    fun loadRoutes(): List<SavedRoute> = readRoutes(KEY_ROUTES)

    fun saveRoutes(routes: List<SavedRoute>) = writeRoutes(KEY_ROUTES, routes)

    private fun readRoutes(key: String): List<SavedRoute> {
        val text = prefs.getString(key, null) ?: return emptyList()
        return try {
            val list = JSONArray(text)
            (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val from = fromJson(item.getJSONObject("from"))
                val to = fromJson(item.getJSONObject("to"))
                if (from == null || to == null) null else SavedRoute(from, to)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeRoutes(key: String, routes: List<SavedRoute>) {
        val list = JSONArray()
        routes.forEach { route ->
            val item = JSONObject()
            item.put("from", toJson(route.from))
            item.put("to", toJson(route.to))
            list.put(item)
        }
        prefs.edit().putString(key, list.toString()).apply()
    }

    /** The remembered connections (start, destination and lines), in the order they were saved. */
    fun loadPatterns(): List<SavedPattern> {
        val text = prefs.getString(KEY_PATTERNS, null) ?: return emptyList()
        return try {
            val list = JSONArray(text)
            (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val from = item.optJSONObject("from")?.let { fromJson(it) }
                val to = item.optJSONObject("to")?.let { fromJson(it) }
                val names = item.optJSONArray("lines")
                val lines = if (names == null) emptyList() else (0 until names.length()).map { names.getString(it) }
                if (from == null || to == null || lines.isEmpty()) null else SavedPattern(from, to, lines, item.optString("name", ""))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePatterns(patterns: List<SavedPattern>) {
        val list = JSONArray()
        patterns.forEach { pattern ->
            val item = JSONObject()
            item.put("from", toJson(pattern.from))
            item.put("to", toJson(pattern.to))
            item.put("lines", JSONArray(pattern.lines))
            item.put("name", pattern.name)
            list.put(item)
        }
        prefs.edit().putString(KEY_PATTERNS, list.toString()).apply()
    }

    /** The last searches as pairs of start and destination, the newest first. */
    fun loadRecentRoutes(): List<SavedRoute> = readRoutes(KEY_RECENT_ROUTES)

    fun saveRecentRoutes(routes: List<SavedRoute>) = writeRoutes(KEY_RECENT_ROUTES, routes)

    /**
     * The favourite places of the start page. Before the first change these are home and
     * work, with the places that were saved for them by an earlier version of the app.
     */
    fun loadFavoritePlaces(): List<FavoritePlace> {
        val text = prefs.getString(KEY_FAVORITE_PLACES, null)
        if (text == null) {
            return listOf(
                FavoritePlace(PlaceIcon.Home, place = loadPlaces(KEY_HOME).firstOrNull()),
                FavoritePlace(PlaceIcon.Work, place = loadPlaces(KEY_WORK).firstOrNull()),
            )
        }
        return try {
            val list = JSONArray(text)
            (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val icon = PlaceIcon.entries.firstOrNull { it.name == item.optString("icon") }
                if (icon == null) {
                    null
                } else {
                    FavoritePlace(icon, item.optString("name", ""), item.optJSONObject("place")?.let { fromJson(it) })
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFavoritePlaces(places: List<FavoritePlace>) {
        val list = JSONArray()
        places.forEach { favorite ->
            val item = JSONObject()
            item.put("icon", favorite.icon.name)
            item.put("name", favorite.name)
            if (favorite.place != null) item.put("place", toJson(favorite.place))
            list.put(item)
        }
        prefs.edit().putString(KEY_FAVORITE_PLACES, list.toString()).apply()
    }

    /** The places picked in searches, the newest first. */
    fun loadSearchHistory(): List<Stop> = loadPlaces(KEY_SEARCH_HISTORY)

    fun saveSearchHistory(places: List<Stop>) = savePlaces(KEY_SEARCH_HISTORY, places)

    /** The saved places beyond the favourite places, in the order the user put them. */
    fun loadSavedPlaces(): List<SavedPlace> {
        val text = prefs.getString(KEY_SAVED_PLACES, null) ?: return emptyList()
        return try {
            val list = JSONArray(text)
            (0 until list.length()).mapNotNull { index ->
                val item = list.getJSONObject(index)
                val id = item.optString("id", "")
                val place = item.optJSONObject("place")?.let { fromJson(it) }
                if (id.isEmpty() || place == null) {
                    null
                } else {
                    SavedPlace(
                        id = id,
                        icon = PlaceIcon.entries.firstOrNull { it.name == item.optString("icon") } ?: PlaceIcon.Pin,
                        name = item.optString("name", ""),
                        place = place,
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSavedPlaces(places: List<SavedPlace>) {
        val list = JSONArray()
        places.forEach { saved ->
            val item = JSONObject()
            item.put("id", saved.id)
            item.put("icon", saved.icon.name)
            item.put("name", saved.name)
            item.put("place", toJson(saved.place))
            list.put(item)
        }
        prefs.edit().putString(KEY_SAVED_PLACES, list.toString()).apply()
    }

    private fun toJson(place: Stop): JSONObject {
        val item = JSONObject()
        item.put("id", place.id)
        item.put("name", place.name)
        if (place.latitude != null) item.put("latitude", place.latitude)
        if (place.longitude != null) item.put("longitude", place.longitude)
        item.put("kind", place.kind.name)
        item.put("products", JSONArray(place.products.toList()))
        return item
    }

    private fun fromJson(item: JSONObject): Stop? {
        val id = item.optString("id", "")
        val name = item.optString("name", "")
        if (id.isEmpty() || name.isEmpty()) return null
        val products = mutableSetOf<String>()
        val list = item.optJSONArray("products")
        if (list != null) {
            for (index in 0 until list.length()) products.add(list.getString(index))
        }
        val latitude = if (item.has("latitude")) item.getDouble("latitude") else null
        val longitude = if (item.has("longitude")) item.getDouble("longitude") else null
        // A place of the former DB source: Transitous finds it again by its position.
        val storedId = if (id.startsWith(LEGACY_DB_PREFIX)) {
            if (latitude == null || longitude == null) return null
            DataSource.Transitous.prefix + "pos:$latitude,$longitude"
        } else {
            id
        }
        return Stop(
            id = storedId,
            name = name,
            latitude = latitude,
            longitude = longitude,
            distanceMeters = null,
            products = products,
            kind = PlaceKind.entries.firstOrNull { it.name == item.optString("kind") } ?: PlaceKind.Stop,
        )
    }

    companion object {
        /** The name of the preferences file, for listeners of changes (see wear/PhoneBridge.kt). */
        const val PREFS_NAME = "next-stop"

        /** The keys whose change the watch should learn about: favourites, saved places, recent destinations and the get-off reminder. */
        val WATCH_SYNC_KEYS: Set<String>
            get() = setOf(KEY_HOME, KEY_WORK, KEY_FAVORITE_PLACES, KEY_SAVED_PLACES, KEY_SEARCH_HISTORY, KEY_RECENT_ROUTES, KEY_ALIGHT_REMINDERS)

        // Home and work as an earlier version of the app saved them; read once for the favourite places.
        private const val KEY_HOME = "home"
        private const val KEY_LIVE_BASE_URL = "liveBaseUrl"
        private const val KEY_LIVE_NAME = "liveName"
        private const val KEY_LIVE_NAME_ASKED = "liveNameAsked"
        const val LIVE_NAME_MAX = 40
        private const val KEY_WORK = "work"
        const val KEY_FAVORITES = "favorites"
        private const val KEY_STOP_GROUPS = "favoriteStopGroups"
        private const val KEY_FAVORITE_PLACES = "favoritePlaces"
        private const val KEY_SAVED_PLACES = "savedPlaces"
        private const val KEY_SEARCH_HISTORY = "searchHistory"
        private const val KEY_ROUTES = "routes"
        private const val KEY_RECENT_ROUTES = "recentRoutes"
        private const val KEY_PATTERNS = "savedPatterns"
        private const val KEY_PLAN_FIRST = "planFirst"
        private const val KEY_CONNECTIONS_LIST = "connectionsList"
        private const val KEY_CONNECTION_CHOICE = "connectionChoice"
        private const val KEY_WALK_MAPS_OPEN = "walkMapsOpen"
        private const val KEY_CONNECTIONS_FULL = "connectionsFull"
        private const val KEY_CHART_CHANGE_STOPS = "chartChangeStops"
        private const val KEY_CHART_TOTAL_TIME = "chartTotalTime"
        private const val KEY_CHART_SHORT_LEGS = "chartShortLegs"
        private const val KEY_DELAY_SEPARATE = "delaySeparate"
        private const val KEY_DELAY_DISPLAY = "delayDisplay"
        private const val KEY_FIT_LINE = "fitLine"
        private const val KEY_START_VEHICLES = "startVehicles"
        private const val KEY_MAP_VEHICLES = "mapVehicles"
        private const val KEY_MAP_VEHICLES_LEVEL = "mapVehiclesLevel"
        private const val KEY_START_MAP = "startMap"
        private const val KEY_STATION_DETAILS = "stationDetails"
        private const val KEY_ENTRANCE_MODE = "entranceMode"
        private const val KEY_STOP_NAMES = "stopNames"
        private const val KEY_MAP_NOTICES = "mapNotices"
        private const val KEY_START_CLASSIC = "startClassic"
        private const val KEY_START_CARDS = "startCards"
        private const val KEY_START_DEPARTURES = "startDepartures"

        // Set once the defaults of new installs (the departures card off) were settled for this install.
        private const val KEY_DEFAULTS_2 = "defaults2"
        private const val KEY_BOARDING_HINTS = "boardingHints"
        private const val KEY_TRANSFER_CHOICE = "transferChoice"
        private const val KEY_START_CALENDAR = "startCalendar"
        private const val KEY_OWN_SERVER = "ownServer"
        private const val KEY_NEARBY_RADIUS = "nearbyRadius"
        private const val KEY_NEARBY_DEPARTURES = "nearbyDepartures"
        private const val KEY_LEAVE_ORDER = "leaveOrder"

        /** The reaches of "all stops nearby" the settings offer, in metres. */
        private const val KEY_START_SHEET_STAGE = "startSheetStage"
        private const val KEY_COMFORT = "transferComfort"
        private const val KEY_VERY_RISKY_HINT = "veryRiskyHintSeen"
        private const val KEY_DEVELOPER = "developerOptions"
        private const val KEY_SETUP_DONE = "setupDone"
        private const val KEY_ALIGHT_REMINDERS = "alightReminders"
        private const val KEY_SIMULATE_FASTER = "simulateFaster"
        private const val KEY_EVERY_MINUTE_HINT = "everyMinuteHintSeen"
        private const val KEY_SHORTCUT_ALERT = "shortcutAlert"
        private const val KEY_SHORTCUT_SAME_COMFORT = "shortcutSameComfort"
        private const val KEY_SHORTCUT_QUIET_START = "shortcutQuietStart"
        private const val KEY_VEHICLE_HINT = "vehicleHint"
        private const val KEY_PRODUCTS = "products"
        private const val KEY_WALKING = "walkingSpeed"
        private const val KEY_SLOWER = "slowerWalking"
        private const val KEY_NORMAL_SPARE = "transferNormalSpare"
        private const val KEY_RELAXED_SPARE = "transferRelaxedSpare"
        private const val KEY_CHANGE_BUFFER = "transferChangeBufferSeconds"
        private const val KEY_ACCESSIBILITY = "accessibility"
        private const val KEY_LOCAL_ONLY = "local_only"
        private const val KEY_BIKE = "bike"
        private const val KEY_BIKE_COMBO = "bike_combo"
        // Set once the products are stored with long-distance trains and coaches as keys of their own.
        private const val KEY_PRODUCTS_SPLIT = "products_split"
        private const val KEY_SOURCES_AUTOMATIC = "sourcesAutomatic"
        private const val KEY_SOURCES = "sources"
        private const val LEGACY_DB_SOURCE = "db"
        /** The file of the access keys; its name is also in the backup rules. */
        const val KEYS_PREFS_NAME = "source_keys"
        private const val KEY_SOURCE_ORDER = "sourceOrder"
        private const val KEY_SOURCES_ENABLED = "sourcesEnabled"
        private const val KEY_VBB_ACCESS_ID = "vbbAccessId"
        private const val KEY_PTE_AUTHORIZATION = "pteAuthorization"
        private const val LEGACY_DB_PREFIX = "db:"
        private const val LEGACY_KEY_DB_SERVER = "dbServer"
        private const val KEY_COLOR_CHOICE = "colorChoice"
        private const val KEY_DARK_CHOICE = "darkChoice"
        private const val KEY_MAP_STYLE = "mapStyle"
        private const val KEY_VEHICLE_DIRECTION = "vehicleDirection"
        private const val KEY_ACCENT = "accentColor"
        private const val KEY_NOTIFICATION_LOOK = "notificationLook"
        private const val KEY_WIDGET_COLORS = "widgetColors"
        private const val KEY_WIDGET_LAT = "widgetLatitude"
        private const val KEY_WIDGET_LON = "widgetLongitude"
        private const val KEY_WIDGET_AT = "widgetPositionAt"
    }
}
