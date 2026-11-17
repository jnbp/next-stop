package io.github.jnbp.nextstop.wear.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What phone and watch say to each other over the Wearable Data Layer. The phone side
 * (app/src/main/java/io/github/jnbp/nextstop/wear/PhoneBridge.kt) writes the same paths and keys:
 * keep both in step.
 *
 * Data items (phone -> watch, kept by the Data Layer and seen by the watch also later):
 * - [PATH_PLACES]: favourites, saved places, recent destinations and the last position of the phone;
 *   optionally "appointments" (next calendar entries with a location) and "contacts" (contacts
 *   with a postal address), when the phone may read them; "transferComfort", the name of the
 *   phone's change time (Relaxed, Normal, Risky, VeryRisky); "alightReminders", the phone's
 *   setting for the reminder before getting off (boolean). Every key is optional.
 * - [PATH_TRIP]: the next step of the trip the phone follows, or `active = false`.
 * - [PATH_MAP_IMAGE]`/<slot>`: the answer to [MSG_MAP_IMAGE]: the key of the request
 *   ([KEY_MAP_KEY]) and, when the phone could draw it, the map as a WebP picture
 *   ([KEY_MAP_ASSET], an Asset), with the area it drew: the camera ([KEY_MAP_LATITUDE],
 *   [KEY_MAP_LONGITUDE], [KEY_MAP_ZOOM]) and its size in logical pixels ([KEY_MAP_LOGICAL_WIDTH],
 *   [KEY_MAP_LOGICAL_HEIGHT]), all doubles. One item per slot, replaced by every answer.
 *   The quick picture of a request comes first at [PATH_MAP_IMAGE]`/<slot>`[QUICK_SUFFIX].
 *
 * Messages (watch -> phone):
 * - [MSG_SYNC]: asks the phone to write [PATH_PLACES] (and the trip) again.
 * - [MSG_START_TRIP]: payload `{"token": "<refresh token>"}`, follows that journey on the phone.
 * - [MSG_STOP_TRIP]: no payload, ends the trip the phone follows.
 * - [MSG_MAP_IMAGE]: payload `{"key", "slot", "lat", "lon", "zoom", "width", "height", "ratio", "dark"}`
 *   (see [MapImageRequest]), optionally `"quick": {"zoom", "width", "height"}` (a small picture of
 *   the same area to draw first, [MapImageRequest.quick]): asks the phone to draw the map there
 *   with MapLibre and to answer at [PATH_MAP_IMAGE]`/<slot>`. A phone without this answers
 *   nothing; the watch then draws its sketch.
 * - [MSG_WARM_UP]: payload `{"dark", "ratio"}` and, when the watch knows where it is, `"lat", "lon"`:
 *   the watch app opened, the phone sets up MapLibre and loads the style and tiles there so
 *   the first map picture comes fast. No answer.
 * - [MSG_QUERY]: payload `{"id", "kind", ...}`, a lookup with the phone's own sources (see
 *   [PhoneQuery]): kind "journeys" with "from" and "to" (places as [placeJson]), "results" and
 *   "comfort" (a name of [TransferComfort]); kind "departures" with "stop" (a place) and
 *   "results"; kind "search" with "query", "results" and optionally "lat"/"lon" to search near.
 *
 * Messages (phone -> watch):
 * - [MSG_ANSWER]: the answer to [MSG_QUERY], sent to the watch that asked: `{"id", "ok"}` with
 *   "journeys" (see [parseJourneyJson]), "departures" (see [parseDeparturesJson]) or "places"
 *   (as [placeJson]); with "ok" false an "error" instead. A phone without this answers nothing;
 *   the watch then looks it up itself.
 *
 * One trip, two devices: the phone is the source of truth when it is reachable. A trip
 * started on the watch is followed there at once ([LocalFollow]) and sent to the phone; as
 * soon as [PATH_TRIP] says the phone follows a trip, the watch stops its own and shows the
 * phone's. Ending it on either side ends it on both.
 *
 * The phone app declares the capability [CAPABILITY_PHONE] (app/src/main/res/values/wear.xml),
 * so the watch can tell a connected phone without Next Stop from one with it.
 */
object Protocol {
    const val PATH_PLACES = "/nextstop/places"
    const val PATH_TRIP = "/nextstop/trip"

    /**
     * The settings of the watch app as the phone last edited them (written by the phone), and as
     * the watch has them (written by the watch); see WatchSettingsSync.kt. Each side reads the
     * other's and merges by time. An older app on either side does not know these paths and
     * ignores them.
     */
    const val PATH_WATCH_SETTINGS = "/nextstop/watch-settings"
    const val PATH_WATCH_SETTINGS_WATCH = "/nextstop/watch-settings-w"
    const val MSG_SYNC = "/nextstop/sync"
    const val MSG_START_TRIP = "/nextstop/start-trip"
    const val MSG_STOP_TRIP = "/nextstop/stop-trip"
    const val MSG_MAP_IMAGE = "/nextstop/map-image"
    const val PATH_MAP_IMAGE = "/nextstop/mapimg"

    /** The watch app opened: the phone prepares its map pictures (see [MapWarmUp]). An older phone app ignores it. */
    const val MSG_WARM_UP = "/nextstop/warm-up"
    const val MSG_QUERY = "/nextstop/query"
    const val MSG_ANSWER = "/nextstop/answer"
    const val CAPABILITY_PHONE = "nextstop_phone"

    /** The keys of an answer at [PATH_MAP_IMAGE]: the key of its request and the picture. */
    const val KEY_MAP_KEY = "key"
    const val KEY_MAP_ASSET = "image"
    const val KEY_MAP_LATITUDE = "lat"
    const val KEY_MAP_LONGITUDE = "lon"
    const val KEY_MAP_ZOOM = "zoom"
    const val KEY_MAP_LOGICAL_WIDTH = "lw"
    const val KEY_MAP_LOGICAL_HEIGHT = "lh"

    /** Appended to the slot's path for the quick picture of a request. */
    const val QUICK_SUFFIX = "-q"

    /** The key of the JSON text inside each data item. */
    const val KEY_JSON = "json"
}

/** A place as the phone sends it, with the icon the user gave it there (a name of PlaceIcon) or null. */
data class SyncedPlace(val place: Place, val label: String, val icon: String?)

/** A coming appointment of the phone's calendar; [location] is free text, searched on the watch. [begin] in epoch ms. */
data class SyncedAppointment(val title: String, val location: String, val begin: Long)

/** A contact of the phone with one postal address; [kind] is the type of the address (home, work, ...) or null. */
data class SyncedContact(val name: String, val address: String, val kind: String?)

/** All the phone tells the watch about places, and where the phone was last. */
data class SyncedPlaces(
    val favorites: List<SyncedPlace> = emptyList(),
    val saved: List<SyncedPlace> = emptyList(),
    val recent: List<SyncedPlace> = emptyList(),
    val phoneLatitude: Double? = null,
    val phoneLongitude: Double? = null,
    val phoneLocationTime: Long = 0,
    val appointments: List<SyncedAppointment> = emptyList(),
    val contacts: List<SyncedContact> = emptyList(),
    /** The change time the phone uses for its searches ("transferComfort"); null from an older phone app. */
    val phoneComfort: TransferComfort? = null,
    /** The phone's setting "reminder before getting off" ("alightReminders"); null from an older phone app. */
    val phoneAlightReminders: Boolean? = null,
    /** True once the phone wrote anything at all. */
    val synced: Boolean = false,
) {
    /** Every destination once, favourites first. */
    val destinations: List<SyncedPlace>
        get() = (favorites + saved + recent).distinctBy { it.place.id }

    /** True when there is nothing to offer as a destination. */
    val isEmpty: Boolean
        get() = favorites.isEmpty() && saved.isEmpty() && recent.isEmpty() && appointments.isEmpty() && contacts.isEmpty()

    companion object {
        fun parse(text: String?): SyncedPlaces {
            if (text.isNullOrEmpty()) return SyncedPlaces()
            return try {
                val root = JSONObject(text)
                val location = root.optJSONObject("location")
                SyncedPlaces(
                    favorites = places(root.optJSONArray("favorites")),
                    saved = places(root.optJSONArray("saved")),
                    recent = places(root.optJSONArray("recent")),
                    phoneLatitude = location?.optDouble("lat")?.takeIf { !it.isNaN() },
                    phoneLongitude = location?.optDouble("lon")?.takeIf { !it.isNaN() },
                    phoneLocationTime = location?.optLong("time") ?: 0,
                    appointments = appointments(root.optJSONArray("appointments")),
                    contacts = contacts(root.optJSONArray("contacts")),
                    phoneComfort = TransferComfort.parse(root.optString("transferComfort", "")),
                    phoneAlightReminders = if (root.has("alightReminders")) root.optBoolean("alightReminders", true) else null,
                    synced = true,
                )
            } catch (e: Exception) {
                SyncedPlaces()
            }
        }

        private fun appointments(list: JSONArray?): List<SyncedAppointment> {
            if (list == null) return emptyList()
            val now = System.currentTimeMillis()
            return (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val location = item.optString("location", "")
                if (location.isEmpty()) return@mapNotNull null
                val begin = item.optLong("begin", 0)
                // Past appointments of an older sync are no destination any more.
                if (begin in 1 until now) return@mapNotNull null
                SyncedAppointment(item.optString("title", "").ifEmpty { location }, location, begin)
            }
        }

        private fun contacts(list: JSONArray?): List<SyncedContact> {
            if (list == null) return emptyList()
            return (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name", "")
                val address = item.optString("address", "")
                if (name.isEmpty() || address.isEmpty()) return@mapNotNull null
                SyncedContact(name, address, item.optString("kind", "").ifEmpty { null })
            }
        }

        private fun places(list: JSONArray?): List<SyncedPlace> {
            if (list == null) return emptyList()
            return (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id", "")
                val name = item.optString("name", "")
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                val kind = when (item.optString("kind")) {
                    "poi" -> PlaceKind.Poi
                    "address" -> PlaceKind.Address
                    else -> PlaceKind.Stop
                }
                val place = Place(
                    id = id,
                    name = name,
                    latitude = if (item.has("lat")) item.optDouble("lat").takeIf { !it.isNaN() } else null,
                    longitude = if (item.has("lon")) item.optDouble("lon").takeIf { !it.isNaN() } else null,
                    kind = kind,
                )
                SyncedPlace(place, item.optString("label", "").ifEmpty { name }, item.optString("icon", "").ifEmpty { null })
            }
        }
    }
}

/** A place as JSON, in the form of the places the phone sends: id, name, lat, lon, kind. */
fun placeJson(place: Place): JSONObject = JSONObject().apply {
    put("id", place.id)
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

/** The reverse of [placeJson]; null without id or name. */
fun parsePlaceJson(item: JSONObject): Place? {
    val id = item.optString("id", "")
    val name = item.optString("name", "")
    if (id.isEmpty() || name.isEmpty()) return null
    return Place(
        id = id,
        name = name,
        latitude = if (item.has("lat")) item.optDouble("lat").takeIf { !it.isNaN() } else null,
        longitude = if (item.has("lon")) item.optDouble("lon").takeIf { !it.isNaN() } else null,
        kind = when (item.optString("kind")) {
            "poi" -> PlaceKind.Poi
            "address" -> PlaceKind.Address
            else -> PlaceKind.Stop
        },
    )
}

/**
 * The ride after a change, as the trip page shows it: line, direction, where and when it
 * leaves (epoch ms), its platform or boarding position, and the minutes on foot before it.
 */
data class NextRide(
    val line: String?,
    val product: String,
    val direction: String?,
    val stop: String,
    val platform: String?,
    val time: Long,
    val walkMinutes: Int,
    /** How tight the change to this ride is ("rating", a name of [ChangeRating]); null when unknown. */
    val rating: ChangeRating? = null,
    /** Minutes the change leaves beyond what it needs ("spare"); null when unknown. */
    val spare: Int? = null,
    /** Minutes from the live arrival to the live departure ("gap"), as the phone shows them; null when unknown. */
    val gap: Int? = null,
    /** Where the ride before is left and where this one is boarded, for the change map ("map"). */
    val map: ChangeMap? = null,
    /** True only where the phone knows the change is across the same platform ("samePlatform"). */
    val samePlatform: Boolean = false,
    /** The colour of the line as ARGB, as the phone works it out from the ride itself ("color"); null where it sends none. */
    val color: Long? = null,
    /** The way to this ride is ridden by bike ("bike"), not walked. */
    val cycling: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("line", line ?: JSONObject.NULL)
        put("product", product)
        put("direction", direction ?: JSONObject.NULL)
        put("stop", stop)
        put("platform", platform ?: JSONObject.NULL)
        put("time", time)
        put("walkMinutes", walkMinutes)
        rating?.let { put("rating", it.name) }
        spare?.let { put("spare", it) }
        gap?.let { put("gap", it) }
        map?.let { put("map", it.toJson()) }
        if (samePlatform) put("samePlatform", true)
        color?.let { put("color", it) }
        if (cycling) put("bike", true)
    }

    companion object {
        fun parse(item: JSONObject): NextRide = NextRide(
            // The phone names lines already; a name kept from before may still be "1" for an S1.
            line = rapidLineName(if (item.isNull("line")) null else item.optString("line"), item.optString("product", "")),
            product = item.optString("product", ""),
            direction = if (item.isNull("direction")) null else item.optString("direction"),
            stop = item.optString("stop", ""),
            platform = if (item.isNull("platform")) null else item.optString("platform").ifEmpty { null },
            time = item.optLong("time"),
            walkMinutes = item.optInt("walkMinutes"),
            rating = ChangeRating.parse(item.optString("rating", "")),
            spare = if (item.has("spare") && !item.isNull("spare")) item.optInt("spare") else null,
            gap = if (item.has("gap") && !item.isNull("gap")) item.optInt("gap") else null,
            map = item.optJSONObject("map")?.let { ChangeMap.parse(it) },
            samePlatform = item.optBoolean("samePlatform", false),
            color = lineColorOf(item),
            cycling = item.optBoolean("bike", false),
        )
    }
}

/**
 * How tight a change is, by the names of the phone (Tightness there): relaxed, normal,
 * tight and sprint (less time than the change needs by the live times).
 */
enum class ChangeRating {
    Comfortable,
    Tight,
    Risky,
    VeryRisky,
    ;

    companion object {
        fun parse(name: String?): ChangeRating? = entries.firstOrNull { it.name == name }

        /**
         * A rough rating for a trip the watch follows itself, which knows neither the station
         * nor the stop positions: a change needs 3 minutes, a walk its minutes and one more.
         * From 5 minutes to spare relaxed, from 2 normal; tight where the change still works at
         * a brisk pace (the walk x 0.8, at least 2 minutes), else sprint (the rule of the phone,
         * paceTightness there).
         */
        fun estimate(gapMinutes: Long, walkMinutes: Long): Pair<ChangeRating, Int> {
            val need = if (walkMinutes > 0) walkMinutes + 1 else 3
            val brisk = maxOf(2L, if (walkMinutes > 0) Math.ceil(walkMinutes * 0.8 - 1e-9).toLong() else need)
            val spare = (gapMinutes - need).toInt()
            val rating = when {
                spare >= 5 -> Comfortable
                spare >= 2 -> Tight
                gapMinutes >= brisk -> Risky
                else -> VeryRisky
            }
            return rating to spare
        }
    }
}

/**
 * The way of a walk or a change for the map sketch of the watch, north up: where it starts
 * (where the ride before is left, or where the walk begins) and where it leads (the stop
 * position or platform of the next ride, or the destination), and the way on foot between
 * them as at most [MAX_PATH] points; empty when only the straight line is known.
 */
data class ChangeMap(
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double,
    val path: List<Pair<Double, Double>> = emptyList(),
    /** The length of the way drawn and the minutes on foot for it, both from that way (the phone); null where not sent. */
    val meters: Int? = null,
    val minutes: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fromLat", fromLatitude)
        put("fromLon", fromLongitude)
        put("toLat", toLatitude)
        put("toLon", toLongitude)
        meters?.let { put("meters", it) }
        minutes?.let { put("minutes", it) }
        if (path.isNotEmpty()) put("path", JSONArray().apply { path.forEach { put(JSONArray().put(it.first).put(it.second)) } })
    }

    companion object {
        const val MAX_PATH = 30

        fun parse(item: JSONObject): ChangeMap? {
            val values = listOf("fromLat", "fromLon", "toLat", "toLon").map { item.optDouble(it) }
            if (values.any { it.isNaN() }) return null
            val list = item.optJSONArray("path")
            val path = if (list == null) {
                emptyList()
            } else {
                (0 until minOf(list.length(), MAX_PATH)).mapNotNull { index ->
                    val point = list.optJSONArray(index) ?: return@mapNotNull null
                    val latitude = point.optDouble(0)
                    val longitude = point.optDouble(1)
                    if (latitude.isNaN() || longitude.isNaN()) null else latitude to longitude
                }
            }
            return ChangeMap(
                values[0], values[1], values[2], values[3], if (path.size >= 2) path else emptyList(),
                meters = if (item.has("meters")) item.optInt("meters") else null,
                minutes = if (item.has("minutes")) item.optInt("minutes") else null,
            )
        }
    }
}

/** One stop of a ride, for the progress line on board; [name] may be empty (a trip of the watch), [time] in epoch ms (0 unknown). */
data class RideStop(val name: String, val time: Long) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("time", time)

    companion object {
        fun parseList(list: JSONArray?): List<RideStop> {
            if (list == null) return emptyList()
            return (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                RideStop(item.optString("name", ""), item.optLong("time"))
            }
        }
    }
}

/**
 * One ride of the whole connection, for the overview on the watch: line, where from and to,
 * when (epoch ms), platforms (or stop positions), the minutes on foot before it, and the
 * rating of the change that leads to it (null for the first ride and for staying on board).
 */
data class PlanRide(
    val line: String?,
    val product: String,
    val direction: String?,
    val from: String,
    val to: String,
    val departure: Long,
    val arrival: Long,
    val departurePlatform: String? = null,
    val arrivalPlatform: String? = null,
    val walkBefore: Int = 0,
    val rating: ChangeRating? = null,
    val spare: Int? = null,
    /** Minutes from the live arrival to the live departure of the change to this ride ("gap"). */
    val gap: Int? = null,
    val cancelled: Boolean = false,
    /** The colour of the line as ARGB, as the phone works it out from the ride itself ("color"); null where it sends none. */
    val color: Long? = null,
    /** The way before this ride ([walkBefore]) is ridden by bike ("bikeBefore"). */
    val bikeBefore: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("line", line ?: JSONObject.NULL)
        put("product", product)
        put("direction", direction ?: JSONObject.NULL)
        put("from", from)
        put("to", to)
        put("dep", departure)
        put("arr", arrival)
        put("depPlatform", departurePlatform ?: JSONObject.NULL)
        put("arrPlatform", arrivalPlatform ?: JSONObject.NULL)
        put("walkBefore", walkBefore)
        rating?.let { put("rating", it.name) }
        spare?.let { put("spare", it) }
        gap?.let { put("gap", it) }
        if (cancelled) put("cancelled", true)
        color?.let { put("color", it) }
        if (bikeBefore) put("bikeBefore", true)
    }

    companion object {
        fun parseList(list: JSONArray?): List<PlanRide> {
            if (list == null) return emptyList()
            return (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                PlanRide(
                    line = rapidLineName(item.textOrNull("line"), item.optString("product", "")),
                    product = item.optString("product", ""),
                    direction = item.textOrNull("direction"),
                    from = item.optString("from", ""),
                    to = item.optString("to", ""),
                    departure = item.optLong("dep"),
                    arrival = item.optLong("arr"),
                    departurePlatform = item.textOrNull("depPlatform"),
                    arrivalPlatform = item.textOrNull("arrPlatform"),
                    walkBefore = item.optInt("walkBefore"),
                    rating = ChangeRating.parse(item.optString("rating", "")),
                    spare = if (item.has("spare") && !item.isNull("spare")) item.optInt("spare") else null,
                    gap = if (item.has("gap") && !item.isNull("gap")) item.optInt("gap") else null,
                    cancelled = item.optBoolean("cancelled", false),
                    color = lineColorOf(item),
                    bikeBefore = item.optBoolean("bikeBefore", false),
                )
            }
        }
    }
}

/** A text of [key], null when missing, JSON null or empty. */
/**
 * The colour of a line in [item] ("color", ARGB as the phone's Int or as a Long) as an ARGB
 * Long in 32 bits; null where there is none.
 */
internal fun lineColorOf(item: JSONObject): Long? =
    if (!item.has("color") || item.isNull("color")) null else item.optLong("color") and 0xFFFFFFFFL

private fun JSONObject.textOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

/**
 * A walk of a trip for the small sketch on the watch: its two ends with their names and
 * positions, and its minutes. Drawn as a vector sketch on the watch, no map tiles needed.
 */
data class WalkSketch(
    val from: String,
    val to: String,
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double,
    val minutes: Int,
    /** Ridden by bike (the bike combo of the phone, "bike"), not walked. */
    val cycling: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("from", from)
        .put("to", to)
        .put("fromLat", fromLatitude)
        .put("fromLon", fromLongitude)
        .put("toLat", toLatitude)
        .put("toLon", toLongitude)
        .put("minutes", minutes)
        .apply { if (cycling) put("bike", true) }

    companion object {
        fun parse(item: JSONObject): WalkSketch? {
            val values = listOf("fromLat", "fromLon", "toLat", "toLon").map { item.optDouble(it) }
            if (values.any { it.isNaN() }) return null
            return WalkSketch(
                from = item.optString("from", ""),
                to = item.optString("to", ""),
                fromLatitude = values[0],
                fromLongitude = values[1],
                toLatitude = values[2],
                toLongitude = values[3],
                minutes = item.optInt("minutes"),
                cycling = item.optBoolean("bike", false),
            )
        }
    }
}

/** The kinds of the next step of a trip, as the phone names them. */
enum class StepKind { Board, GetOff, WalkToEnd, Arrived }

/**
 * The next step of a followed trip, as the phone (or the watch itself) works it out. Times as
 * epoch milliseconds. [stopsLeft] counts the stops until getting off, -1 when unknown.
 * [alert] is set from one stop before getting off; [alertKey] tells the rides apart, so the
 * watch vibrates once per ride; [change] tells a change from the last getting off.
 */
data class TripSnapshot(
    val active: Boolean,
    val kind: StepKind = StepKind.Arrived,
    val line: String? = null,
    val product: String = "",
    val direction: String? = null,
    val place: String = "",
    val platform: String? = null,
    val time: Long = 0,
    val delayMinutes: Int = 0,
    val hint: String? = null,
    val progress: Float = 0f,
    val arrival: Long = 0,
    val destination: String = "",
    val stopsLeft: Int = -1,
    val alert: Boolean = false,
    val alertKey: String = "",
    /** True when the phone follows the trip, false when the watch follows it on its own. */
    val fromPhone: Boolean = true,
    /** With [StepKind.GetOff]: another ride follows, so getting off is a change (its own vibration). */
    val change: Boolean = false,
    /** The destination as a place a new search can lead to ("Missed it"); null when unknown. */
    val target: Place? = null,
    /** With [StepKind.Board]: where to get off this ride, and when. */
    val rideTo: String? = null,
    val rideArrival: Long = 0,
    /** The ride after the one of this step (a change), or null after the last ride. */
    val next: NextRide? = null,
    /** The walk of this step (to the boarding stop, of a change, or to the end), for its sketch. */
    val walk: WalkSketch? = null,
    /**
     * A trip of the phone: what tells it apart from others (its refresh token, else its id on
     * the phone), when the phone began to follow it and when it last wrote this state (epoch
     * milliseconds; 0 when an older phone app sends none).
     */
    val tripKey: String? = null,
    val started: Long = 0,
    val updated: Long = 0,
    /**
     * With [StepKind.Board] and a boarding hint: the third of the train to board in, 0 rear,
     * 1 middle, 2 front ("boardPart"), -1 when unknown; [boardEstimated] for an estimate from the map.
     */
    val boardPart: Int = -1,
    val boardEstimated: Boolean = false,
    /** All stops of the ride of this step, from boarding to getting off ("stops"); empty when unknown. */
    val stops: List<RideStop> = emptyList(),
    /** Every ride of the connection for the overview ("plan"), the walk after the last one ("planWalkEnd", minutes). */
    val plan: List<PlanRide> = emptyList(),
    val planWalkEnd: Int = 0,
    /** The way after the last ride ([planWalkEnd]) is ridden by bike ("planBikeEnd"). */
    val planBikeEnd: Boolean = false,
    /** The ride of this step in [plan] ("planIndex"); [plan].size after the last ride; -1 when unknown. */
    val planIndex: Int = -1,
    /** With [StepKind.Board] after another ride: how tight that change is and the minutes it leaves. */
    val rating: ChangeRating? = null,
    val spare: Int? = null,
    /** With [StepKind.Board] after another ride: minutes from the live arrival to the live departure ("gap"). */
    val gap: Int? = null,
    /** The way of this step on foot (to the boarding stop or the destination) for the map sketch ("map"). */
    val map: ChangeMap? = null,
    /** The colour of the line as ARGB, as the phone works it out from the ride itself ("color"); null where it sends none. */
    val color: Long? = null,
    /**
     * With [StepKind.Board] before a journey that starts on foot and whose walk has not begun:
     * the step is setting off, [time] is when to leave and [departure] when the ride leaves.
     * On the wire "time" stays the ride's departure and "leaveAt" carries [time], so a watch
     * that knows nothing of leaving still shows boarding at the right time. [delayMinutes]
     * stays the ride's delay.
     */
    val leave: Boolean = false,
    /** With [leave]: the departure of the ride the walk leads to. */
    val departure: Long = 0,
) {
    /**
     * True for a trip of the phone that no longer runs there although the phone never said so
     * (its process died, or it is out of reach): past its arrival, or no new state for too
     * long while the phone writes one at least every minute.
     */
    fun stale(now: Long): Boolean {
        if (!active || !fromPhone) return false
        if (arrival > 0 && now > arrival + PHONE_AFTER_ARRIVAL_MILLIS) return true
        return updated > 0 && now - updated > PHONE_SILENCE_MILLIS
    }

    fun toJson(): String = JSONObject().apply {
        put("active", active)
        put("kind", kind.name)
        put("line", line ?: JSONObject.NULL)
        put("product", product)
        put("direction", direction ?: JSONObject.NULL)
        put("place", place)
        put("platform", platform ?: JSONObject.NULL)
        // As the phone sends it: "time" the ride's departure, "leaveAt" when to set off.
        put("time", if (leave) departure else time)
        if (leave) put("leaveAt", time)
        put("delay", delayMinutes)
        put("hint", hint ?: JSONObject.NULL)
        put("progress", progress.toDouble())
        put("arrival", arrival)
        put("destination", destination)
        put("stopsLeft", stopsLeft)
        put("alert", alert)
        put("alertKey", alertKey)
        put("fromPhone", fromPhone)
        put("change", change)
        put("target", target?.let { placeJson(it) } ?: JSONObject.NULL)
        put("rideTo", rideTo ?: JSONObject.NULL)
        put("rideArrival", rideArrival)
        put("next", next?.toJson() ?: JSONObject.NULL)
        put("walk", walk?.toJson() ?: JSONObject.NULL)
        put("tripKey", tripKey ?: JSONObject.NULL)
        put("started", started)
        put("updated", updated)
        if (boardPart >= 0) {
            put("boardPart", boardPart)
            put("boardEstimated", boardEstimated)
        }
        if (stops.isNotEmpty()) put("stops", JSONArray().apply { stops.forEach { put(it.toJson()) } })
        if (plan.isNotEmpty()) {
            put("plan", JSONArray().apply { plan.forEach { put(it.toJson()) } })
            put("planWalkEnd", planWalkEnd)
            if (planBikeEnd) put("planBikeEnd", true)
        }
        put("planIndex", planIndex)
        rating?.let { put("rating", it.name) }
        spare?.let { put("spare", it) }
        gap?.let { put("gap", it) }
        map?.let { put("map", it.toJson()) }
        color?.let { put("color", it) }
    }.toString()

    companion object {
        /** No trip. Belongs to neither side, so a trip the watch starts and the phone's states both replace it. */
        val NONE = TripSnapshot(active = false, fromPhone = false)

        // A trip of the phone counts as over this long after its arrival (the phone ends it after 5 minutes).
        private const val PHONE_AFTER_ARRIVAL_MILLIS = 5 * 60_000L

        // The phone writes the state of a running trip at least every minute; after this long without one it is gone.
        private const val PHONE_SILENCE_MILLIS = 3 * 60_000L

        fun parse(text: String?): TripSnapshot {
            if (text.isNullOrEmpty()) return NONE
            return try {
                val root = JSONObject(text)
                TripSnapshot(
                    active = root.optBoolean("active", false),
                    kind = StepKind.entries.firstOrNull { it.name == root.optString("kind") } ?: StepKind.Arrived,
                    line = rapidLineName(root.text("line"), root.optString("product", "")),
                    product = root.optString("product", ""),
                    direction = root.text("direction"),
                    place = root.optString("place", ""),
                    platform = root.text("platform"),
                    time = root.optLong("leaveAt").takeIf { it > 0 } ?: root.optLong("time"),
                    delayMinutes = root.optInt("delay"),
                    hint = root.text("hint"),
                    progress = root.optDouble("progress", 0.0).toFloat(),
                    arrival = root.optLong("arrival"),
                    destination = root.optString("destination", ""),
                    stopsLeft = root.optInt("stopsLeft", -1),
                    alert = root.optBoolean("alert", false),
                    alertKey = root.optString("alertKey", ""),
                    fromPhone = root.optBoolean("fromPhone", true),
                    change = root.optBoolean("change", false),
                    target = root.optJSONObject("target")?.let { parsePlaceJson(it) },
                    rideTo = root.text("rideTo"),
                    rideArrival = root.optLong("rideArrival"),
                    next = root.optJSONObject("next")?.let { NextRide.parse(it) },
                    walk = root.optJSONObject("walk")?.let { WalkSketch.parse(it) },
                    tripKey = root.text("tripKey"),
                    started = root.optLong("started"),
                    updated = root.optLong("updated"),
                    boardPart = root.optInt("boardPart", -1).takeIf { it in 0..2 } ?: -1,
                    boardEstimated = root.optBoolean("boardEstimated", false),
                    stops = RideStop.parseList(root.optJSONArray("stops")),
                    plan = PlanRide.parseList(root.optJSONArray("plan")),
                    planWalkEnd = root.optInt("planWalkEnd"),
                    planBikeEnd = root.optBoolean("planBikeEnd", false),
                    planIndex = root.optInt("planIndex", -1),
                    rating = ChangeRating.parse(root.optString("rating", "")),
                    spare = if (root.has("spare") && !root.isNull("spare")) root.optInt("spare") else null,
                    gap = if (root.has("gap") && !root.isNull("gap")) root.optInt("gap") else null,
                    map = root.optJSONObject("map")?.let { ChangeMap.parse(it) },
                    color = lineColorOf(root),
                    leave = root.optLong("leaveAt") > 0,
                    departure = if (root.optLong("leaveAt") > 0) root.optLong("time") else 0,
                )
            } catch (e: Exception) {
                NONE
            }
        }

        private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }
    }
}

/**
 * The next step of [journey] at [now], worked out on the watch for a trip it follows itself
 * ("Hier verfolgen"). The same rules as on the phone: on a ride the next step is getting
 * off, before a ride boarding it, after the last ride the walk to the end.
 */
fun snapshotOf(journey: Journey, now: Long): TripSnapshot {
    val rides = journey.rides
    val riding = rides.firstOrNull { now >= it.departure && now < it.arrival }
    val upcoming = rides.firstOrNull { it.departure > now }
    val total = (journey.arrival - journey.departure).coerceAtLeast(1)
    val progress = ((now - journey.departure).toFloat() / total).coerceIn(0f, 1f)
    val destination = journey.legs.last().to
    val base = TripSnapshot(active = true, progress = progress, arrival = journey.arrival, destination = destination, fromPhone = false)
    val legs = journey.legs
    val plan = planOf(legs)
    val planBase = base.copy(plan = plan, planWalkEnd = walkMinutesOf(walkingBefore(legs, legs.size)))
    return when {
        riding != null -> {
            val index = legs.indexOf(riding)
            val after = walkingAfter(legs, index)
            val left = if (riding.stopTimes.isEmpty()) -1 else riding.stopTimes.count { it > now }.coerceAtLeast(1)
            val soon = riding.arrival - now <= ALERT_FALLBACK_MILLIS
            planBase.copy(
                planIndex = rides.indexOf(riding),
                stops = stopsOf(riding),
                kind = StepKind.GetOff,
                line = riding.line,
                product = riding.product,
                direction = riding.direction,
                place = riding.to,
                time = riding.arrival,
                delayMinutes = riding.arrivalDelayMinutes,
                stopsLeft = left,
                // On a long last hop the buzz waits for the last minutes, not hours ahead.
                alert = if (left > 0) left <= 1 && getOffSoon(riding.arrival, now) else soon,
                alertKey = "${riding.line}|${riding.to}|${riding.departure}",
                change = rides.indexOf(riding) < rides.lastIndex,
                platform = riding.arrivalPlatform,
                next = nextRideAfter(legs, index),
                // The walk of the change (or to the end) that follows getting off.
                walk = walkOf(after),
            )
        }
        upcoming != null -> {
            val index = legs.indexOf(upcoming)
            val planIndex = rides.indexOf(upcoming)
            val before = walkingBefore(legs, index)
            val previous = legs.subList(0, index).lastOrNull { !it.walking }
            // Before the walk to the first ride: when to leave, not when the ride departs.
            val leave = if (planIndex == 0) leaveTimeOf(legs, index) else null
            val leaving = leave != null && now < leave
            planBase.copy(
                leave = leaving,
                departure = if (leaving) upcoming.departure else 0,
                planIndex = planIndex,
                stops = stopsOf(upcoming),
                rating = plan.getOrNull(planIndex)?.rating,
                spare = plan.getOrNull(planIndex)?.spare,
                gap = plan.getOrNull(planIndex)?.gap,
                map = changeMapOf(previous, before, upcoming),
                kind = StepKind.Board,
                line = upcoming.line,
                product = upcoming.product,
                direction = upcoming.direction,
                place = upcoming.from,
                platform = upcoming.platform,
                time = if (leaving) leave!! else upcoming.departure,
                delayMinutes = upcoming.departureDelayMinutes,
                rideTo = upcoming.to,
                rideArrival = upcoming.arrival,
                next = nextRideAfter(legs, index),
                walk = walkOf(walkingBefore(legs, index)),
            )
        }
        now < journey.arrival -> planBase.copy(
            kind = StepKind.WalkToEnd,
            place = destination,
            time = journey.arrival,
            walk = walkOf(walkingBefore(legs, legs.size)),
            planIndex = rides.size,
            map = walkingBefore(legs, legs.size).let { walks -> changeMapOf(legs.subList(0, legs.size - walks.size).lastOrNull { !it.walking }, walks, null) },
        )
        else -> planBase.copy(kind = StepKind.Arrived, place = destination, time = journey.arrival, progress = 1f, planIndex = rides.size)
    }
}

/**
 * When to set off for a journey whose first ride, at [firstRide] in [legs], follows a walk: the
 * start of that walk. Null without a walk of its own before it, or with walks of 0 m only (as on the phone).
 */
internal fun leaveTimeOf(legs: List<Leg>, firstRide: Int): Long? {
    if (firstRide <= 0) return null
    val walks = legs.subList(0, firstRide)
    if (walks.any { !it.walking } || walks.all { it.inStation }) return null
    return legs.first().departure.takeIf { it < legs[firstRide].departure }
}

/** The walking legs right before the leg at [index] ([legs].size for the end). */
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

/** The ride after the leg at [index] with the walk before it; null after the last ride. */
private fun nextRideAfter(legs: List<Leg>, index: Int): NextRide? {
    if (index < 0) return null
    val nextIndex = (index + 1 until legs.size).firstOrNull { !legs[it].walking } ?: return null
    val next = legs[nextIndex]
    val walks = legs.subList(index + 1, nextIndex)
    val walk = walkMinutesOf(walks)
    val arriving = legs[index].takeIf { !it.walking }
    val gap = arriving?.let { Math.floorDiv(next.departure - it.arrival, 60_000L) }
    val rated = gap?.let { ChangeRating.estimate(it, walk.toLong()) }
    return NextRide(
        next.line, next.product, next.direction, next.from, next.platform, next.departure, walk,
        rating = rated?.first,
        spare = rated?.second,
        gap = gap?.toInt(),
        map = changeMapOf(arriving, walks, next),
    )
}

/** The minutes of [walks] together. */
private fun walkMinutesOf(walks: List<Leg>): Int = walks.sumOf { ((it.arrival - it.departure) / 60_000).coerceAtLeast(0) }.toInt()

/** The stops of [ride] for the progress line: boarding (no name known on the watch) and the times of the others. */
private fun stopsOf(ride: Leg): List<RideStop> =
    if (ride.stopTimes.isEmpty()) emptyList() else listOf(RideStop("", ride.departure)) + ride.stopTimes.map { RideStop("", it) }

/** Every ride of [legs] for the overview, with the walk before it and a rough rating of its change (see [ChangeRating.estimate]). */
private fun planOf(legs: List<Leg>): List<PlanRide> {
    var previous: Leg? = null
    var walk = 0
    val plan = mutableListOf<PlanRide>()
    for (leg in legs) {
        if (leg.walking) {
            walk += ((leg.arrival - leg.departure) / 60_000).coerceAtLeast(0).toInt()
            continue
        }
        val gap = previous?.let { Math.floorDiv(leg.departure - it.arrival, 60_000L) }
        val rated = gap?.let { ChangeRating.estimate(it, walk.toLong()) }
        plan += PlanRide(
            line = leg.line,
            product = leg.product,
            direction = leg.direction,
            from = leg.from,
            to = leg.to,
            departure = leg.departure,
            arrival = leg.arrival,
            departurePlatform = leg.platform,
            arrivalPlatform = leg.arrivalPlatform,
            walkBefore = walk,
            rating = rated?.first,
            spare = rated?.second,
            gap = gap?.toInt(),
            cancelled = leg.cancelled,
        )
        previous = leg
        walk = 0
    }
    return plan
}

/**
 * The map of a change or walk from the positions the provider names: from where [arriving]
 * is left (else where [walks] begin) to where [next] is boarded (else where the walks end);
 * a straight line, the watch knows no ways on foot. Null without both ends.
 */
private fun changeMapOf(arriving: Leg?, walks: List<Leg>, next: Leg?): ChangeMap? {
    val from = arriving?.toPosition ?: walks.firstOrNull()?.fromPosition ?: return null
    val to = next?.fromPosition ?: walks.lastOrNull()?.toPosition ?: return null
    if (from == to) return null
    return ChangeMap(from.first, from.second, to.first, to.second)
}

/** One walk from the first to the last of [legs]; null without legs, positions or distance. */
private fun walkOf(legs: List<Leg>): WalkSketch? {
    if (legs.isEmpty()) return null
    val from = legs.first().fromPosition ?: return null
    val to = legs.last().toPosition ?: return null
    if (from == to) return null
    val minutes = legs.sumOf { ((it.arrival - it.departure) / 60_000).coerceAtLeast(0) }.toInt()
    return WalkSketch(legs.first().from, legs.last().to, from.first, from.second, to.first, to.second, minutes)
}

// Without the stops of a ride, the alert to get off comes this long before its arrival.
private const val ALERT_FALLBACK_MILLIS = 2 * 60_000L
