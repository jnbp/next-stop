package io.github.jnbp.nextstop.wear.data

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Journeys, departures and places from Transitous (MOTIS) for the watch on its own, where
 * the phone does not answer and BVG does not reach: outside Berlin and Brandenburg. The same
 * service the phone asks there, in the style of [TransitousNearby]. Journeys found here carry
 * no refresh token: the watch follows them by their times, without live updates.
 *
 * Every request ends after [TRANSITOUS_TIMEOUT_MILLIS] at the latest (see [withHardTimeout]);
 * failures end as a [BvgException] with the reason for the screen.
 */
object TransitousClient {
    private const val URL_BASE = "https://api.transitous.org"

    private val language: String
        get() = if (Locale.getDefault().language == "de") "de" else "en"

    /** Journeys from [from] to [to] leaving now, with at least the minutes of [comfort] between rides. */
    suspend fun journeys(from: Place, to: Place, results: Int, comfort: TransferComfort): List<Journey> {
        val fromParam = placeParam(from) ?: throw BvgException(BvgFailure.BadAnswer, "origin without position")
        val toParam = placeParam(to) ?: throw BvgException(BvgFailure.BadAnswer, "destination without position")
        val text = get(
            "/api/v6/plan?fromPlace=${encode(fromParam)}&toPlace=${encode(toParam)}&numItineraries=$results" +
                "&detailedTransfers=true&minTransferTime=${comfort.minutes}&language=$language",
        )
        return parse("plan") {
            val list = JSONObject(text).optJSONArray("itineraries") ?: JSONArray()
            (0 until list.length()).mapNotNull { index -> list.optJSONObject(index)?.let { journeyOf(it, from, to) } }.take(results)
        }
    }

    /**
     * The next departures at [stop]: by its Transitous id ("tr:" in front), else (a stop of
     * another provider) at the stops within a short walk of its position.
     */
    suspend fun departures(stop: Place, results: Int): List<Departure> {
        val where = when {
            stop.id.startsWith(PREFIX) -> "stopId=${encode(stop.id.removePrefix(PREFIX))}"
            stop.latitude != null && stop.longitude != null ->
                "center=${coordinates(stop.latitude, stop.longitude)}&radius=$STOP_RADIUS_METERS"
            else -> throw BvgException(BvgFailure.BadAnswer, "stop without Transitous id or position")
        }
        val text = get("/api/v6/stoptimes?$where&n=$results&language=$language")
        return parse("stoptimes") {
            val list = JSONObject(text).optJSONArray("stopTimes") ?: JSONArray()
            departureBoard(
                (0 until list.length()).mapNotNull { index ->
                    val item = list.optJSONObject(index) ?: return@mapNotNull null
                    val place = item.optJSONObject("place") ?: return@mapNotNull null
                    val planned = place.millis("scheduledDeparture")
                    val current = place.millis("departure") ?: planned
                    val mode = item.text("mode") ?: ""
                    val product = productKey(mode)
                    Departure(
                        line = lineName(item, product) ?: "?",
                        product = product,
                        direction = item.text("headsign") ?: item.optJSONObject("tripTo")?.text("name") ?: "",
                        time = current,
                        delayMinutes = if (item.optBoolean("realTime", false) && current != null && planned != null) {
                            ((current - planned) / 60_000).toInt()
                        } else {
                            null
                        },
                        platform = place.text("track"),
                        cancelled = item.optBoolean("cancelled", false) || item.optBoolean("tripCancelled", false),
                        plannedTime = planned,
                    )
                },
            )
        }
    }

    /** Stops, addresses and places that match [query], near [near] first when given. */
    suspend fun searchPlaces(query: String, results: Int, near: Pair<Double, Double>? = null): List<Place> {
        val bias = near?.let { "&place=${coordinates(it.first, it.second)}" } ?: ""
        val text = get("/api/v1/geocode?text=${encode(query)}&numResults=$results&language=$language$bias")
        return parse("geocode") {
            val list = JSONArray(text)
            (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val latitude = item.number("lat") ?: return@mapNotNull null
                val longitude = item.number("lon") ?: return@mapNotNull null
                val name = item.text("name") ?: return@mapNotNull null
                val id = item.text("id")
                if (item.text("type") == "STOP" && id != null) {
                    Place(bvgIdOf(id) ?: PREFIX + id, name, latitude, longitude, PlaceKind.Stop)
                } else {
                    // Addresses and points of interest go by their position.
                    Place("address:${coordinates(latitude, longitude)}", name, latitude, longitude, PlaceKind.Address)
                }
            }.take(results)
        }
    }

    /** A place for MOTIS: a stop of Transitous by its id, everything else by its position; null without one. */
    private fun placeParam(place: Place): String? {
        if (place.kind == PlaceKind.Stop && place.id.startsWith(PREFIX)) return place.id.removePrefix(PREFIX)
        val latitude = place.latitude ?: return null
        val longitude = place.longitude ?: return null
        return coordinates(latitude, longitude)
    }

    private fun journeyOf(itinerary: JSONObject, from: Place, to: Place): Journey? {
        val list = itinerary.optJSONArray("legs") ?: return null
        val last = list.length() - 1
        val legs = (0 until list.length()).map { index ->
            val item = list.optJSONObject(index) ?: return null
            legOf(item, if (index == 0) from else null, if (index == last) to else null) ?: return null
        }
        return if (legs.isEmpty()) null else Journey(legs, refreshToken = null)
    }

    /** One leg; [start] and [end] name its ends where these are the searched places, not stops. */
    private fun legOf(item: JSONObject, start: Place?, end: Place?): Leg? {
        val mode = item.text("mode") ?: return null
        val walking = mode in STREET_MODES
        val fromJson = item.optJSONObject("from") ?: return null
        val toJson = item.optJSONObject("to") ?: return null
        val plannedDeparture = item.millis("scheduledStartTime")
        val plannedArrival = item.millis("scheduledEndTime")
        val departure = item.millis("startTime") ?: plannedDeparture ?: return null
        val arrival = item.millis("endTime") ?: plannedArrival ?: return null
        val realtime = item.optBoolean("realTime", false)
        val product = if (walking) "" else productKey(mode)
        val times = mutableListOf<Long>()
        if (!walking) {
            val between = item.optJSONArray("intermediateStops")
            if (between != null) {
                for (index in 0 until between.length()) {
                    val stop = between.optJSONObject(index) ?: continue
                    (stop.millis("arrival") ?: stop.millis("scheduledArrival") ?: stop.millis("departure"))?.let { times.add(it) }
                }
            }
            times.add(arrival)
        }
        return Leg(
            line = if (walking) null else lineName(item, product),
            product = product,
            walking = walking,
            from = endName(fromJson, start),
            to = endName(toJson, end),
            departure = departure,
            arrival = arrival,
            departureDelayMinutes = if (realtime && plannedDeparture != null) ((departure - plannedDeparture) / 60_000).toInt() else 0,
            arrivalDelayMinutes = if (realtime && plannedArrival != null) ((arrival - plannedArrival) / 60_000).toInt() else 0,
            platform = fromJson.text("track"),
            direction = item.text("headsign"),
            cancelled = item.optBoolean("cancelled", false),
            stopTimes = times,
            arrivalPlatform = toJson.text("track"),
            fromPosition = positionOf(fromJson),
            toPosition = positionOf(toJson),
        )
    }

    /** The name of the end of a leg; the searched place's own name where the end is no stop ("START", "END"). */
    private fun endName(place: JSONObject, asked: Place?): String {
        val name = place.text("name")
        if (asked != null && (name == null || place.text("vertexType") != "TRANSIT")) return asked.name
        return name ?: asked?.name ?: ""
    }

    private fun positionOf(place: JSONObject): Pair<Double, Double>? {
        val latitude = place.number("lat") ?: return null
        val longitude = place.number("lon") ?: return null
        return latitude to longitude
    }

    /**
     * The name of the line of a leg or a stop time: the short name of the route, with the kind
     * of train in front of a bare number ("RE 1", "ICE 29"); else what MOTIS shows.
     */
    private fun lineName(item: JSONObject, product: String): String? {
        val route = item.text("routeShortName")?.trim()
        val category = item.optJSONObject("category")?.text("shortName")?.trim()
        val name = when {
            route != null && category != null && route.all { it.isDigit() } -> "$category $route"
            route != null -> route
            else -> item.text("displayName") ?: item.text("tripShortName")
        }
        return rapidLineName(name, product)
    }

    /** GET [path] on the IO threads, within [TRANSITOUS_TIMEOUT_MILLIS]. */
    private suspend fun get(path: String): String = withHardTimeout(TRANSITOUS_TIMEOUT_MILLIS) {
        val endpoint = path.substringBefore('?')
        val started = System.currentTimeMillis()
        val connection = try {
            URL(URL_BASE + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw BvgException(BvgFailure.NoNetwork, "Transitous: ${e.message}", e)
        }
        try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NextStop-Wear-dev/0.1 (private test build)")
            val code = connection.responseCode
            when {
                code in 200..299 -> {}
                code >= 500 || code == 429 -> throw BvgException(BvgFailure.Unreachable, "Transitous HTTP $code")
                else -> throw BvgException(BvgFailure.BadAnswer, "Transitous HTTP $code")
            }
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(LOG_TAG, "Transitous $endpoint ok in ${System.currentTimeMillis() - started} ms")
            text
        } catch (e: BvgException) {
            throw e
        } catch (e: IOException) {
            Log.d(LOG_TAG, "Transitous $endpoint failed: $e")
            throw BvgException(BvgFailure.Unreachable, "Transitous: ${e.javaClass.simpleName}", e)
        } finally {
            connection.disconnect()
        }
    }

    private inline fun <T> parse(what: String, block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        Log.d(LOG_TAG, "Transitous $what: answer does not parse: ${e.message}")
        throw BvgException(BvgFailure.BadAnswer, "unexpected Transitous answer for $what", e)
    }

    private const val PREFIX = "tr:"

    // A stop of another provider finds its departures among the stops this close to it.
    private const val STOP_RADIUS_METERS = 150

    private val STREET_MODES = setOf("WALK", "BIKE", "RENTAL", "CAR", "CAR_PARKING", "CAR_DROPOFF", "FLEX", "HGV")
}

/** The product of a MOTIS mode as the app names it (as on the phone); empty for modes without one. */
private fun productKey(mode: String): String = when (mode) {
    "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL" -> "express"
    "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "RAIL" -> "regional"
    "SUBURBAN" -> "suburban"
    "SUBWAY", "METRO" -> "subway"
    "TRAM" -> "tram"
    "BUS", "COACH", "ODM" -> "bus"
    "FERRY" -> "ferry"
    else -> ""
}

private fun coordinates(latitude: Double, longitude: Double): String = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun JSONObject.text(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

private fun JSONObject.number(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.millis(key: String): Long? =
    text(key)?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
