package io.github.jnbp.nextstop.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.jnbp.nextstop.data.Departure
import io.github.jnbp.nextstop.data.Journey
import io.github.jnbp.nextstop.data.PRODUCT_KEYS
import io.github.jnbp.nextstop.data.PlaceIcon
import io.github.jnbp.nextstop.data.Stop
import androidx.compose.ui.graphics.toArgb
import io.github.jnbp.nextstop.ui.lineColor
import org.json.JSONArray
import org.json.JSONObject

/** The kinds of home screen widgets the app offers. */
enum class WidgetKind { Departures, Favorite, Trip, Mini, Appointment, Auto }

/** The colours of all widgets that follow the common setting. The names are stored. */
enum class WidgetColorChoice { NextStop, MaterialYou }

/** The colours of one widget: as all widgets, or its own choice. The names are stored. */
enum class WidgetScheme { Global, NextStop, MaterialYou }

/** Light or dark for one widget: following the system, or always one of them. The names are stored. */
enum class WidgetShade { System, Light, Dark }

/** Keys of the state every widget keeps: its settings and the last data loaded for it. */
internal object WidgetKeys {
    /** The stop as stored by AppPrefs; absent: the stop nearest to the user. */
    val STOP = stringPreferencesKey("stop")
    /**
     * The products to show, joined by commas, as stored before long-distance trains and coaches
     * had keys of their own ("regional" stood for long-distance trains too); read only.
     */
    val PRODUCTS = stringPreferencesKey("products")
    /** The products to show, joined by commas; absent: those of [PRODUCTS], or all. */
    val PRODUCTS_SPLIT = stringPreferencesKey("products2")
    /** One line to show only; absent: all lines. */
    val LINE = stringPreferencesKey("line")
    /** The index of the favourite place in AppPrefs; absent: the first one with a place. */
    val FAVORITE = intPreferencesKey("favorite")
    /** The id of a saved place in AppPrefs, which then counts instead of [FAVORITE]; absent: a favourite place. */
    val SAVED = stringPreferencesKey("savedPlace")
    /** The id of a remembered connection in AppPrefs, which then counts before [SAVED] and [FAVORITE]. */
    val PATTERN = stringPreferencesKey("pattern")
    val SCHEME = stringPreferencesKey("scheme")
    val SHADE = stringPreferencesKey("shade")
    val CONFIGURED = booleanPreferencesKey("configured")
    /** The last loaded data, see [WidgetSnapshot]. */
    val SNAPSHOT = stringPreferencesKey("snapshot")
    val FETCHED_AT = longPreferencesKey("fetchedAt")
    /** Written on every redraw, so the widget composes again even when its data did not change. */
    val TICK = longPreferencesKey("tick")
}

/** The settings of one widget, read from its state. */
data class WidgetConfig(
    val stop: Stop? = null,
    val products: Set<String> = PRODUCT_KEYS.toSet(),
    val line: String? = null,
    val favorite: Int? = null,
    val saved: String? = null,
    val pattern: String? = null,
    val scheme: WidgetScheme = WidgetScheme.Global,
    val shade: WidgetShade = WidgetShade.System,
)

/**
 * The products of a widget from its [current] list, else from its [old] one: there "regional"
 * brought long-distance trains along and coaches came as buses, so they come with them. All
 * products where neither is stored.
 */
internal fun storedWidgetProducts(current: String?, old: String?): Set<String> {
    fun parse(text: String?) = text?.split(",")?.map { it.trim() }?.filter { it in PRODUCT_KEYS }?.toSet()?.takeIf { it.isNotEmpty() }
    parse(current)?.let { return it }
    val before = parse(old) ?: return PRODUCT_KEYS.toSet()
    return before +
        (if ("regional" in before) setOf("express") else emptySet()) +
        (if ("bus" in before) setOf("coach") else emptySet())
}

internal fun readConfig(state: Preferences): WidgetConfig = WidgetConfig(
    stop = state[WidgetKeys.STOP]?.let { placeFromText(it) },
    products = storedWidgetProducts(state[WidgetKeys.PRODUCTS_SPLIT], state[WidgetKeys.PRODUCTS]),
    line = state[WidgetKeys.LINE]?.takeIf { it.isNotBlank() },
    favorite = state[WidgetKeys.FAVORITE],
    saved = state[WidgetKeys.SAVED],
    pattern = state[WidgetKeys.PATTERN],
    scheme = WidgetScheme.entries.firstOrNull { it.name == state[WidgetKeys.SCHEME] } ?: WidgetScheme.Global,
    shade = WidgetShade.entries.firstOrNull { it.name == state[WidgetKeys.SHADE] } ?: WidgetShade.System,
)

internal fun writeConfig(state: MutablePreferences, config: WidgetConfig) {
    if (config.stop == null) state.remove(WidgetKeys.STOP) else state[WidgetKeys.STOP] = placeToText(config.stop)
    state.remove(WidgetKeys.PRODUCTS)
    if (config.products.containsAll(PRODUCT_KEYS)) {
        state.remove(WidgetKeys.PRODUCTS_SPLIT)
    } else {
        state[WidgetKeys.PRODUCTS_SPLIT] = config.products.joinToString(",")
    }
    if (config.line == null) state.remove(WidgetKeys.LINE) else state[WidgetKeys.LINE] = config.line
    if (config.favorite == null) state.remove(WidgetKeys.FAVORITE) else state[WidgetKeys.FAVORITE] = config.favorite
    if (config.saved == null) state.remove(WidgetKeys.SAVED) else state[WidgetKeys.SAVED] = config.saved
    if (config.pattern == null) state.remove(WidgetKeys.PATTERN) else state[WidgetKeys.PATTERN] = config.pattern
    state[WidgetKeys.SCHEME] = config.scheme.name
    state[WidgetKeys.SHADE] = config.shade.name
    state[WidgetKeys.CONFIGURED] = true
    // New settings, new data: the old one belongs to another stop or place.
    state.remove(WidgetKeys.SNAPSHOT)
    state.remove(WidgetKeys.FETCHED_AT)
}

// Places are stored in the same form as the saved places of the app.
private val placeCodec by lazy { PlaceCodec() }

internal fun placeToText(place: Stop): String = placeCodec.encode(place).toString()

internal fun placeFromText(text: String): Stop? = try {
    placeCodec.decode(JSONObject(text))
} catch (e: Exception) {
    null
}

/** The JSON form of places without a Context: the same fields AppPrefs stores. */
internal class PlaceCodec {
    fun encode(place: Stop): JSONObject = JSONObject().apply {
        put("id", place.id)
        put("name", place.name)
        place.latitude?.let { put("latitude", it) }
        place.longitude?.let { put("longitude", it) }
        put("kind", place.kind.name)
        put("products", JSONArray(place.products.toList()))
    }

    fun decode(item: JSONObject): Stop? {
        val id = item.optString("id", "")
        val name = item.optString("name", "")
        if (id.isEmpty() || name.isEmpty()) return null
        val list = item.optJSONArray("products")
        val products = if (list == null) emptySet() else (0 until list.length()).map { list.getString(it) }.toSet()
        return Stop(
            id = id,
            name = name,
            latitude = if (item.has("latitude")) item.getDouble("latitude") else null,
            longitude = if (item.has("longitude")) item.getDouble("longitude") else null,
            distanceMeters = null,
            products = products,
            kind = io.github.jnbp.nextstop.data.PlaceKind.entries.firstOrNull { it.name == item.optString("kind") }
                ?: io.github.jnbp.nextstop.data.PlaceKind.Stop,
        )
    }
}

/** One departure as a widget keeps it; times in epoch milliseconds. */
data class WidgetDeparture(
    val line: String,
    val product: String,
    val direction: String,
    val time: Long?,
    val plannedTime: Long?,
    val delayMinutes: Int,
    val cancelled: Boolean,
    val platform: String?,
    /**
     * The colour of its line as ARGB, worked out in the app from the departure itself (its
     * provider's colour, where it leaves; see lineColor); null in a snapshot of before.
     */
    val color: Int? = null,
) {
    /** When it leaves for real; the timetable where no live time is known. */
    val leaves: Long? get() = time ?: plannedTime
}

internal fun widgetDeparture(departure: Departure): WidgetDeparture = WidgetDeparture(
    line = departure.line,
    product = departure.product,
    direction = departure.direction,
    time = departure.time?.toInstant()?.toEpochMilli(),
    plannedTime = departure.plannedTime?.toInstant()?.toEpochMilli(),
    delayMinutes = ((departure.delaySeconds ?: 0) / 60),
    cancelled = departure.cancelled,
    platform = departure.platform,
    color = lineColor(departure).toArgb(),
)

/** The departures of one stop. */
data class StopBoard(val stop: Stop, val departures: List<WidgetDeparture>)

/**
 * One ride of a connection, for its badge; [color] the colour of its line as ARGB, worked out
 * from the ride itself (see lineColor), null in a snapshot of before.
 */
data class WidgetRide(val line: String?, val product: String, val stops: Int, val color: Int? = null)

/**
 * One connection a widget shows: to a favourite place or to an appointment. [key] finds the
 * journey again (see journeyKey) when the trip is started from the widget.
 */
data class WidgetConnection(
    val from: Stop,
    val to: Stop,
    val departure: Long,
    val plannedDeparture: Long,
    val arrival: Long,
    /** When the first ride leaves, for "in N min"; the departure where the journey is all on foot. */
    val firstRide: Long,
    val rides: List<WidgetRide>,
    val key: String,
)

internal fun widgetConnection(from: Stop, to: Stop, journey: Journey): WidgetConnection {
    val first = journey.legs.first()
    val ride = journey.rides.firstOrNull()
    return WidgetConnection(
        from = from,
        to = to,
        departure = journey.departure.toInstant().toEpochMilli(),
        plannedDeparture = (first.plannedDeparture ?: first.departure).toInstant().toEpochMilli(),
        arrival = journey.arrival.toInstant().toEpochMilli(),
        firstRide = (ride?.departure ?: journey.departure).toInstant().toEpochMilli(),
        rides = journey.rides.map { leg ->
            // The stops ridden through: those after boarding up to getting off.
            WidgetRide(leg.line, leg.product, (leg.stopovers.size - 1).coerceAtLeast(0), lineColor(leg).toArgb())
        },
        key = io.github.jnbp.nextstop.data.journeyKey(journey),
    )
}

/** Why the automatic widget shows what it shows. */
enum class AutoReason { Trip, Appointment, ToWork, ToHome, Nearby }

/**
 * The appointment a widget shows, with the way there once one was found. [place] is what its
 * location resolved to, so a tap opens the search there without looking it up again.
 */
data class WidgetAppointment(
    val title: String,
    val begin: Long,
    val location: String,
    val connection: WidgetConnection?,
    val place: Stop? = null,
)

/**
 * All a widget last loaded. Each kind fills its part: [board] the departures, [connection]
 * the way to a favourite place with its [label] and [icon], [appointment] the next
 * appointment; the automatic widget also says why ([reason]). [problem] names what went
 * wrong when nothing could be loaded. [positionAt] is when the position of the user that the
 * content starts from was taken (epoch milliseconds); null where it does not depend on it.
 */
data class WidgetSnapshot(
    val board: StopBoard? = null,
    val connection: WidgetConnection? = null,
    val label: String? = null,
    val icon: PlaceIcon? = null,
    val appointment: WidgetAppointment? = null,
    val reason: AutoReason? = null,
    val problem: WidgetProblem? = null,
    val positionAt: Long? = null,
) {
    /** The next moment the widget counts down to, for the redraw every minute. */
    fun nextTime(now: Long): Long? {
        val times = buildList {
            board?.departures?.forEach { departure -> departure.leaves?.let { add(it) } }
            connection?.let { add(it.departure) }
            appointment?.connection?.let { add(it.departure) }
            appointment?.let { add(it.begin) }
        }
        return times.filter { it >= now - 60_000 }.minOrNull()
    }
}

/** What kept a widget from showing its content. */
enum class WidgetProblem { NoLocation, NoFavorite, NoConnection, Offline, NoCalendar, NoAppointment }

// The JSON form of a snapshot: short and plain, read on every redraw.

internal fun snapshotToText(snapshot: WidgetSnapshot): String = JSONObject().apply {
    snapshot.board?.let { board ->
        put("board", JSONObject().apply {
            put("stop", placeCodec.encode(board.stop))
            put("departures", JSONArray().apply { board.departures.forEach { put(departureJson(it)) } })
        })
    }
    snapshot.connection?.let { put("connection", connectionJson(it)) }
    snapshot.label?.let { put("label", it) }
    snapshot.icon?.let { put("icon", it.name) }
    snapshot.appointment?.let { appointment ->
        put("appointment", JSONObject().apply {
            put("title", appointment.title)
            put("begin", appointment.begin)
            put("location", appointment.location)
            appointment.connection?.let { put("connection", connectionJson(it)) }
            appointment.place?.let { put("place", placeCodec.encode(it)) }
        })
    }
    snapshot.reason?.let { put("reason", it.name) }
    snapshot.problem?.let { put("problem", it.name) }
    snapshot.positionAt?.let { put("positionAt", it) }
}.toString()

internal fun snapshotFromText(text: String?): WidgetSnapshot? {
    if (text.isNullOrEmpty()) return null
    return try {
        val item = JSONObject(text)
        WidgetSnapshot(
            board = item.optJSONObject("board")?.let { board ->
                val stop = placeCodec.decode(board.getJSONObject("stop")) ?: return@let null
                val list = board.getJSONArray("departures")
                StopBoard(stop, (0 until list.length()).map { departureFrom(list.getJSONObject(it)) })
            },
            connection = item.optJSONObject("connection")?.let { connectionFrom(it) },
            label = item.optString("label").takeIf { item.has("label") },
            icon = PlaceIcon.entries.firstOrNull { it.name == item.optString("icon") },
            appointment = item.optJSONObject("appointment")?.let { appointment ->
                WidgetAppointment(
                    title = appointment.optString("title"),
                    begin = appointment.getLong("begin"),
                    location = appointment.optString("location"),
                    connection = appointment.optJSONObject("connection")?.let { connectionFrom(it) },
                    place = appointment.optJSONObject("place")?.let { placeCodec.decode(it) },
                )
            },
            reason = AutoReason.entries.firstOrNull { it.name == item.optString("reason") },
            problem = WidgetProblem.entries.firstOrNull { it.name == item.optString("problem") },
            positionAt = if (item.has("positionAt")) item.getLong("positionAt") else null,
        )
    } catch (e: Exception) {
        null
    }
}

private fun departureJson(departure: WidgetDeparture) = JSONObject().apply {
    put("line", departure.line)
    put("product", departure.product)
    put("direction", departure.direction)
    departure.time?.let { put("time", it) }
    departure.plannedTime?.let { put("planned", it) }
    put("delay", departure.delayMinutes)
    put("cancelled", departure.cancelled)
    departure.platform?.let { put("platform", it) }
    departure.color?.let { put("color", it) }
}

private fun departureFrom(item: JSONObject) = WidgetDeparture(
    line = item.optString("line"),
    product = item.optString("product"),
    direction = item.optString("direction"),
    time = if (item.has("time")) item.getLong("time") else null,
    plannedTime = if (item.has("planned")) item.getLong("planned") else null,
    delayMinutes = item.optInt("delay"),
    cancelled = item.optBoolean("cancelled"),
    platform = if (item.has("platform")) item.getString("platform") else null,
    color = if (item.has("color")) item.getInt("color") else null,
)

private fun connectionJson(connection: WidgetConnection) = JSONObject().apply {
    put("from", placeCodec.encode(connection.from))
    put("to", placeCodec.encode(connection.to))
    put("departure", connection.departure)
    put("plannedDeparture", connection.plannedDeparture)
    put("arrival", connection.arrival)
    put("firstRide", connection.firstRide)
    put("key", connection.key)
    put("rides", JSONArray().apply {
        connection.rides.forEach { ride ->
            put(JSONObject().apply {
                ride.line?.let { put("line", it) }
                put("product", ride.product)
                put("stops", ride.stops)
                ride.color?.let { put("color", it) }
            })
        }
    })
}

private fun connectionFrom(item: JSONObject): WidgetConnection? {
    val from = placeCodec.decode(item.getJSONObject("from")) ?: return null
    val to = placeCodec.decode(item.getJSONObject("to")) ?: return null
    val rides = item.getJSONArray("rides")
    return WidgetConnection(
        from = from,
        to = to,
        departure = item.getLong("departure"),
        plannedDeparture = item.optLong("plannedDeparture", item.getLong("departure")),
        arrival = item.getLong("arrival"),
        firstRide = item.optLong("firstRide", item.getLong("departure")),
        rides = (0 until rides.length()).map { index ->
            val ride = rides.getJSONObject(index)
            WidgetRide(
                if (ride.has("line")) ride.getString("line") else null,
                ride.optString("product"),
                ride.optInt("stops"),
                if (ride.has("color")) ride.getInt("color") else null,
            )
        },
        key = item.optString("key"),
    )
}
