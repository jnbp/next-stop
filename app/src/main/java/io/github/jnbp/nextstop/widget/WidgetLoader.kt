package io.github.jnbp.nextstop.widget

import io.github.jnbp.nextstop.data.DiagLog
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import io.github.jnbp.nextstop.data.ownServerUrl
import io.github.jnbp.nextstop.data.AppPrefs
import io.github.jnbp.nextstop.data.FavoritePlace
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.RoutingSource
import io.github.jnbp.nextstop.data.SavedPattern
import io.github.jnbp.nextstop.data.SavedPlace
import io.github.jnbp.nextstop.data.nextOfPattern
import io.github.jnbp.nextstop.ui.CURRENT_LOCATION_ID
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.TransitousSource
import io.github.jnbp.nextstop.data.hasCalendarPermission
import io.github.jnbp.nextstop.data.journeyKey
import io.github.jnbp.nextstop.data.placedAt
import io.github.jnbp.nextstop.data.resolvePlace
import io.github.jnbp.nextstop.data.shortStopName
import io.github.jnbp.nextstop.data.upcomingAppointments
import io.github.jnbp.nextstop.ui.TripState

/**
 * The journeys the widgets last showed, by their key, so "Start" on a widget follows the very
 * same journey without a new search while the process lives. Small: one per widget.
 */
object WidgetJourneys {
    private val known = ConcurrentHashMap<String, Journey>()

    fun put(journey: Journey) {
        if (known.size > MAX_KNOWN) known.clear()
        known[journeyKey(journey)] = journey
    }

    fun get(key: String): Journey? = known[key]

    private const val MAX_KNOWN = 24
}

// The automatic widget shows an appointment that starts within this many minutes.
private const val AUTO_APPOINTMENT_MINUTES = 120L

// The automatic widget counts a favourite place as "here" within this many metres.
private const val HERE_METERS = 500f

// The places the locations of appointments resolved to, while the process lives.
private val resolvedPlaces = ConcurrentHashMap<String, Stop>()

/**
 * Loads what the widgets show, with the data layer of the app. One loader serves one round of
 * updates: the nearest stop is looked up once for all widgets of the round.
 */
internal class WidgetLoader(private val context: Context) {
    private val prefs = AppPrefs(context)
    private val source = RoutingSource(choice = { prefs.sourceChoice }, userAgent = TransitousSource.userAgent(context), bvgUrl = { ownServerUrl(prefs.ownServer) }, kindLookups = false)
    private var nearestLoaded = false
    private var nearest: Stop? = null

    private var positionLoaded = false
    private var known: TimedPosition? = null
    // Whether the widget loaded last depends on the position of the user (see [load]).
    private var positionUsed = false

    /**
     * Where the user is, once per round: the newer of the position the app stored and the one
     * Android still knows. Never a request of its own: a widget wakes no location provider
     * (battery rule, see WidgetPosition.kt); an old position is named on the widget instead.
     */
    suspend fun position(): TimedPosition? {
        if (positionLoaded) return known
        val best = newerPosition(storedWidgetPosition(prefs), lastKnownLocation(context)?.timed())
        best?.let { recordWidgetPosition(prefs, it) }
        known = best
        positionLoaded = true
        return best
    }

    /** The stop nearest to the user, or null without a position. */
    suspend fun nearestStop(): Stop? {
        positionUsed = true
        if (nearestLoaded) return nearest
        val position = position()?.pair
        nearest = position?.let { (latitude, longitude) ->
            if (!source.covers(latitude, longitude)) null else source.nearbyStops(latitude, longitude, 1).firstOrNull()
        }
        nearestLoaded = true
        return nearest
    }

    /**
     * What a widget of [kind] with [config] shows now. Throws when the provider cannot be
     * reached. Content that starts at the user's position carries when that position was taken.
     */
    suspend fun load(kind: WidgetKind, config: WidgetConfig): WidgetSnapshot {
        positionUsed = false
        val snapshot = loadContent(kind, config)
        return if (positionUsed) snapshot.copy(positionAt = known?.at?.takeIf { it > 0L }) else snapshot
    }

    private suspend fun loadContent(kind: WidgetKind, config: WidgetConfig): WidgetSnapshot = when (kind) {
        WidgetKind.Departures -> board(config, results = 12)
        WidgetKind.Mini -> board(config, results = if (config.line == null) 4 else 30)
        WidgetKind.Favorite, WidgetKind.Trip -> {
            // A remembered connection while it is still there, else a place.
            val pattern = config.pattern?.let { id -> prefs.loadPatterns().firstOrNull { it.id == id } }
            if (pattern != null) remembered(pattern) else favorite(chosenFavorite(config.favorite, config.saved))
        }
        WidgetKind.Appointment -> appointment(onlyWithinMinutes = null)
        WidgetKind.Auto -> auto(config)
    }

    private suspend fun board(config: WidgetConfig, results: Int): WidgetSnapshot {
        val stop = config.stop ?: nearestStop() ?: return WidgetSnapshot(problem = WidgetProblem.NoLocation)
        val line = config.line
        val departures = source.departures(stop.id, results = results, products = config.products)
            .placedAt(stop.position)
            .filter { line == null || sameLine(it.line, line) }
            .take(MAX_DEPARTURES)
            .map { widgetDeparture(it) }
        return WidgetSnapshot(board = StopBoard(stop, departures))
    }

    /**
     * The saved place with the id [saved] while it is still there, else the favourite place at
     * [index], else the first one with a place.
     */
    private fun chosenFavorite(index: Int?, saved: String? = null): FavoritePlace? {
        if (saved != null) {
            prefs.loadSavedPlaces().firstOrNull { it.id == saved }?.let { return widgetPlace(it) }
        }
        val places = prefs.loadFavoritePlaces()
        return index?.let { places.getOrNull(it) }?.takeIf { it.place != null }
            ?: places.firstOrNull { it.place != null }
            ?: prefs.loadSavedPlaces().firstOrNull()?.let { widgetPlace(it) }
    }

    /** A saved place as the widget names it: by its own name, else by the name of the place. */
    private fun widgetPlace(saved: SavedPlace): FavoritePlace =
        saved.asFavorite().copy(name = saved.name.ifBlank { saved.place.name.shortStopName() })

    private suspend fun favorite(favorite: FavoritePlace?, reason: AutoReason? = null): WidgetSnapshot {
        val target = favorite?.place ?: return WidgetSnapshot(problem = WidgetProblem.NoFavorite, reason = reason)
        val from = nearestStop() ?: return WidgetSnapshot(problem = WidgetProblem.NoLocation, reason = reason)
        val journey = nextJourney(from, target)
            ?: return WidgetSnapshot(label = favorite.name, icon = favorite.icon, problem = WidgetProblem.NoConnection, reason = reason)
        WidgetJourneys.put(journey)
        return WidgetSnapshot(
            connection = widgetConnection(from, target, journey),
            label = favorite.name,
            icon = favorite.icon,
            reason = reason,
        )
    }

    /**
     * A remembered connection: the next journey that rides its lines, else the next journey
     * between its places at all. Its start is the stop nearest to the user where it was saved
     * as "current location".
     */
    private suspend fun remembered(pattern: SavedPattern): WidgetSnapshot {
        val label = pattern.name.ifBlank { pattern.to.name.shortStopName() }
        val from = if (pattern.from.id == CURRENT_LOCATION_ID) {
            nearestStop() ?: return WidgetSnapshot(problem = WidgetProblem.NoLocation)
        } else {
            pattern.from
        }
        val now = OffsetDateTime.now().minusMinutes(1)
        val journeys = source.journeys(from = from, to = pattern.to, options = prefs.searchOptionsInUse, results = 6).journeys
        val journey = nextOfPattern(journeys, pattern.lines, now)
            ?: journeys.firstOrNull { !it.cancelled && !it.departure.isBefore(now) }
            ?: return WidgetSnapshot(label = label, icon = PlaceIcon.Star, problem = WidgetProblem.NoConnection)
        WidgetJourneys.put(journey)
        return WidgetSnapshot(
            connection = widgetConnection(from, pattern.to, journey),
            label = label,
            icon = PlaceIcon.Star,
        )
    }

    /** The next journey that has not left yet, as the search of the app finds it. */
    private suspend fun nextJourney(from: Stop, to: Stop): Journey? {
        val now = OffsetDateTime.now().minusMinutes(1)
        val page = source.journeys(from = from, to = to, options = prefs.searchOptionsInUse, results = 4)
        return page.journeys.firstOrNull { !it.cancelled && !it.departure.isBefore(now) }
    }

    /**
     * The next appointment with a place and when to leave for it: the same way as the tile of
     * the start page, the last journey from the nearest stop that is there by its start.
     * [onlyWithinMinutes] leaves out appointments that start later than that.
     */
    private suspend fun appointment(onlyWithinMinutes: Long?, reason: AutoReason? = null): WidgetSnapshot {
        if (!hasCalendarPermission(context)) return WidgetSnapshot(problem = WidgetProblem.NoCalendar, reason = reason)
        val appointment = upcomingAppointments(context, days = 7, limit = 1).firstOrNull()
            ?: return WidgetSnapshot(problem = WidgetProblem.NoAppointment, reason = reason)
        if (onlyWithinMinutes != null &&
            appointment.begin.isAfter(OffsetDateTime.now().plusMinutes(onlyWithinMinutes))
        ) {
            return WidgetSnapshot(problem = WidgetProblem.NoAppointment, reason = reason)
        }
        val place = resolvedPlaces[appointment.location]
            ?: source.resolvePlace(appointment.location)?.also { resolvedPlaces[appointment.location] = it }
        val from = nearestStop()
        val journey = if (place == null || from == null) {
            null
        } else {
            source.journeys(from = from, to = place, options = prefs.searchOptionsInUse, results = 3, arrival = appointment.begin)
                .journeys
                .filter { !it.arrival.isAfter(appointment.begin) }
                .maxByOrNull { it.departure }
        }
        journey?.let { WidgetJourneys.put(it) }
        return WidgetSnapshot(
            appointment = WidgetAppointment(
                title = appointment.title,
                begin = appointment.begin.toInstant().toEpochMilli(),
                location = appointment.location,
                connection = if (journey != null && place != null && from != null) {
                    widgetConnection(from, place, journey)
                } else {
                    null
                },
                place = place,
            ),
            reason = reason,
        )
    }

    /**
     * The automatic widget: a followed trip first (drawn from the trip itself, with the
     * departures nearby below it in case it ends), then an appointment within the next two
     * hours, then on weekday mornings and evenings the way between home and work, else the
     * departures nearby.
     */
    private suspend fun auto(config: WidgetConfig): WidgetSnapshot {
        if (TripState.journey.value != null) {
            return attempt { board(config.copy(stop = null), results = 8) }.copy(reason = AutoReason.Trip)
        }
        if (hasCalendarPermission(context)) {
            val soon = attempt { appointment(onlyWithinMinutes = AUTO_APPOINTMENT_MINUTES, reason = AutoReason.Appointment) }
            if (soon.appointment != null) return soon
        }
        commute()?.let { (favorite, reason) ->
            val way = attempt { favorite(favorite, reason) }
            if (way.connection != null) return way
        }
        return board(config.copy(stop = null), results = 8).copy(reason = AutoReason.Nearby)
    }

    /**
     * On weekdays, mornings to work and evenings home, when both are saved; from one of them
     * always to the other one.
     */
    private suspend fun commute(): Pair<FavoritePlace, AutoReason>? {
        val places = prefs.loadFavoritePlaces()
        val home = places.firstOrNull { it.icon == PlaceIcon.Home && it.place != null } ?: return null
        val work = places.firstOrNull { it.icon == PlaceIcon.Work && it.place != null } ?: return null
        val now = Instant.now().atZone(ZoneId.systemDefault())
        if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return null
        val morning = now.hour in MORNING_HOURS
        val evening = now.hour in EVENING_HOURS
        if (!morning && !evening) return null
        val position = position()?.pair
        return when {
            position != null && near(position, home.place) -> work to AutoReason.ToWork
            position != null && near(position, work.place) -> home to AutoReason.ToHome
            morning -> work to AutoReason.ToWork
            else -> home to AutoReason.ToHome
        }
    }

    private fun near(position: Pair<Double, Double>, place: Stop?): Boolean {
        val target = place?.position ?: return false
        val result = FloatArray(1)
        Location.distanceBetween(position.first, position.second, target.first, target.second, result)
        return result[0] <= HERE_METERS
    }

    /** A part of the automatic widget that fails is left out, not the whole widget. */
    private suspend fun attempt(block: suspend () -> WidgetSnapshot): WidgetSnapshot = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DiagLog.w(WIDGET_LOG_TAG) { "widget: a part failed to load: ${e.javaClass.simpleName}" }
        WidgetSnapshot(problem = WidgetProblem.Offline)
    }

    private companion object {
        const val MAX_DEPARTURES = 10
        val MORNING_HOURS = 5..10
        val EVENING_HOURS = 15..20
    }
}

/** True when two names stand for the same line: "Bus 187" and "187" are one. */
internal fun sameLine(line: String, chosen: String): Boolean {
    val a = io.github.jnbp.nextstop.ui.shortLine(line)?.lowercase()?.replace(" ", "")
    val b = io.github.jnbp.nextstop.ui.shortLine(chosen)?.lowercase()?.replace(" ", "")
    return a != null && a == b
}

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** The newest position Android still knows, or null without permission or without one. */
internal fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return manager.getProviders(true).mapNotNull { provider ->
        try {
            manager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        }
    }.maxByOrNull { it.time }
}
