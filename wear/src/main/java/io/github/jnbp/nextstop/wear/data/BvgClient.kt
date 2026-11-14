package io.github.jnbp.nextstop.wear.data

import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Why a request to the BVG service failed, coarse enough to tell the user in a few words. */
enum class BvgFailure {
    /** No connection at all: the name of the server could not be looked up or no route to it. */
    NoNetwork,

    /** The service is slow or down: a timeout, or an HTTP 5xx / 429. */
    Unreachable,

    /** The service answered, but not with what was expected (HTTP 4xx, or JSON that does not parse). */
    BadAnswer,
}

/** A failed request to the BVG service, with [failure] for the reason shown on the screen. */
class BvgException(val failure: BvgFailure, message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * A small client of the community BVG API (hafas-rest-api v6) for the watch: nearby stops,
 * departures, journeys from a position and a journey loaded again by its handle. It is the
 * same service the phone app asks for Berlin, so a journey handle found here also works on
 * the phone. Only the Berlin/Brandenburg (VBB) area is covered; Transitous is left to the phone.
 */
class BvgClient(baseUrl: String = DEFAULT_URL) {
    private val baseUrl = baseUrl.trimEnd('/')

    private val language: String
        get() = if (Locale.getDefault().language == "de") "de" else "en"

    /** Stops near a position, nearest first, each with its lines when the service sends them. */
    suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int = 3): List<Place> {
        val text = fetch("/locations/nearby?latitude=$latitude&longitude=$longitude&results=$results&linesOfStops=true&language=$language")
        return parse("nearby") {
            val json = JSONArray(text)
            (0 until json.length()).mapNotNull { parsePlace(json.getJSONObject(it)) }
                .distinctBy { it.id }
                .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
        }
    }

    suspend fun searchPlaces(query: String, results: Int = 4): List<Place> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val text = fetch("/locations?query=$encoded&results=$results&poi=true&addresses=true&language=$language")
        return parse("search") {
            val json = JSONArray(text)
            (0 until json.length()).mapNotNull { parsePlace(json.getJSONObject(it)) }
        }
    }

    suspend fun departures(stopId: String, results: Int = 10): List<Departure> {
        // With remarks: they tell an additional run ("Zusatzfahrt"), as a replacement S1 is sent.
        val text = fetch("/stops/${segment(stopId)}/departures?results=$results&duration=60&remarks=true&language=$language")
        return parse("departures") {
            val root = JSONObject(text)
            val list = root.optJSONArray("departures") ?: JSONArray()
            departureBoard((0 until list.length()).map { parseDeparture(list.getJSONObject(it)) })
        }
    }

    /** Journeys from a position (the watch or the phone) to [to], leaving now, with [comfort] between rides. */
    suspend fun journeysFrom(
        latitude: Double,
        longitude: Double,
        fromName: String,
        to: Place,
        results: Int = 3,
        comfort: TransferComfort = TransferComfort.Normal,
    ): List<Journey> {
        val from = "from.latitude=$latitude&from.longitude=$longitude&from.address=" + URLEncoder.encode(fromName, "UTF-8")
        val text = fetch(
            "/journeys?$from&${placeQuery("to", to)}&results=$results&stopovers=true&remarks=false&polylines=false" +
                "&transferTime=${comfort.minutes}&language=$language",
        )
        return parse("journeys") {
            val root = JSONObject(text)
            val list = root.optJSONArray("journeys") ?: JSONArray()
            (0 until list.length()).mapNotNull { parseJourney(list.getJSONObject(it)) }.take(results)
        }
    }

    /**
     * Journeys from [from] to [to], leaving now: a stop by its id, a position (as the user's own,
     * "address:lat,lon") or a place of another provider by its position and name (see [placeQuery]).
     */
    suspend fun journeys(
        from: Place,
        to: Place,
        results: Int = 3,
        comfort: TransferComfort = TransferComfort.Normal,
    ): List<Journey> {
        val text = fetch(
            "/journeys?${placeQuery("from", from)}&${placeQuery("to", to)}&results=$results&stopovers=true&remarks=false" +
                "&polylines=false&transferTime=${comfort.minutes}&language=$language",
        )
        return parse("journeys") {
            val root = JSONObject(text)
            val list = root.optJSONArray("journeys") ?: JSONArray()
            (0 until list.length()).mapNotNull { parseJourney(list.getJSONObject(it)) }.take(results)
        }
    }

    /** The journey with fresh live data, by the handle a search gave; null when it is gone. */
    suspend fun refreshJourney(token: String): Journey? {
        val text = fetch("/journeys/${segment(token)}?stopovers=true&remarks=false&polylines=false&language=$language")
        return parse("refresh") { JSONObject(text).optJSONObject("journey")?.let { parseJourney(it) } }
    }

    /**
     * Stops by id; points of interest by id and position; addresses, and places of another
     * provider (ids with a prefix such as "tr:"), by position and name.
     */
    private fun placeQuery(prefix: String, place: Place): String {
        val name = URLEncoder.encode(place.name, "UTF-8")
        val id = URLEncoder.encode(place.id, "UTF-8")
        val position = "$prefix.latitude=${place.latitude}&$prefix.longitude=${place.longitude}"
        val foreign = place.id.contains(':') && !place.id.first().isDigit()
        return when {
            place.kind == PlaceKind.Stop && !foreign -> "$prefix=$id"
            place.latitude == null || place.longitude == null -> "$prefix=$id"
            place.kind == PlaceKind.Poi && !foreign -> "$prefix.id=$id&$position&$prefix.name=$name"
            else -> "$position&$prefix.address=$name"
        }
    }

    private fun parsePlace(item: JSONObject): Place? {
        val distance = if (item.isNull("distance")) null else item.optInt("distance")
        if (item.str("type") == "location") {
            val latitude = item.dbl("latitude") ?: return null
            val longitude = item.dbl("longitude") ?: return null
            val id = item.str("id")
            val poi = item.optBoolean("poi", false) && id != null
            val name = (if (poi) item.str("name") else item.str("address") ?: item.str("name")) ?: return null
            return Place(
                id = id ?: "address:$latitude,$longitude",
                name = name.substringBefore(", ").ifEmpty { name },
                latitude = latitude,
                longitude = longitude,
                kind = if (poi) PlaceKind.Poi else PlaceKind.Address,
                distanceMeters = distance,
            )
        }
        val id = item.str("id") ?: return null
        val name = item.str("name") ?: return null
        val location = item.optJSONObject("location")
        return Place(
            id = id,
            name = name,
            latitude = location?.dbl("latitude"),
            longitude = location?.dbl("longitude"),
            kind = PlaceKind.Stop,
            distanceMeters = distance,
            lines = parseLines(item.optJSONArray("lines")),
            products = parseProducts(item.optJSONObject("products")),
        )
    }

    /** The lines of a stop (with linesOfStops=true), each once, rail first, in the order of [PRODUCT_ORDER]. */
    private fun parseLines(list: JSONArray?): List<StopLine> {
        if (list == null) return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val line = list.optJSONObject(index) ?: return@mapNotNull null
            val name = lineName(line) ?: return@mapNotNull null
            StopLine(name, line.str("product") ?: "")
        }.distinct().sortedBy { PRODUCT_ORDER.indexOf(it.product).let { rank -> if (rank < 0) PRODUCT_ORDER.size else rank } }
    }

    /** The products served at a stop ("suburban", "subway", ...), in the order of [PRODUCT_ORDER]. */
    private fun parseProducts(products: JSONObject?): List<String> {
        if (products == null) return emptyList()
        return PRODUCT_ORDER.filter { products.optBoolean(it, false) }
    }

    private fun parseDeparture(item: JSONObject): Departure {
        val line = item.optJSONObject("line")
        return Departure(
            line = lineName(line) ?: "?",
            product = line?.str("product") ?: "",
            direction = item.str("direction") ?: "",
            time = item.millis("when") ?: item.millis("plannedWhen"),
            delayMinutes = if (item.isNull("delay")) null else item.optInt("delay") / 60,
            platform = item.str("platform"),
            cancelled = item.optBoolean("cancelled", false),
            plannedTime = item.millis("plannedWhen"),
            additional = isAdditional(item.optJSONArray("remarks")),
        )
    }

    /** True when the remarks of a run announce it as an additional run ("Zusatzfahrt"). */
    private fun isAdditional(remarks: JSONArray?): Boolean {
        if (remarks == null) return false
        return (0 until remarks.length()).any { index -> remarks.optJSONObject(index)?.str("code") == ADDITIONAL_SERVICE_CODE }
    }

    private fun parseJourney(item: JSONObject): Journey? {
        val list = item.optJSONArray("legs") ?: return null
        val legs = (0 until list.length()).map { parseLeg(list.getJSONObject(it)) ?: return null }
        return if (legs.isEmpty()) null else Journey(legs, item.str("refreshToken"))
    }

    private fun parseLeg(item: JSONObject): Leg? {
        val departure = item.millis("departure") ?: item.millis("plannedDeparture") ?: return null
        val arrival = item.millis("arrival") ?: item.millis("plannedArrival") ?: return null
        val line = item.optJSONObject("line")
        val stopovers = item.optJSONArray("stopovers")
        val times = mutableListOf<Long>()
        if (stopovers != null) {
            // The first stopover is where the ride is boarded; the others count towards getting off.
            for (index in 1 until stopovers.length()) {
                val stop = stopovers.optJSONObject(index) ?: continue
                (stop.millis("arrival") ?: stop.millis("plannedArrival") ?: stop.millis("departure"))?.let { times.add(it) }
            }
        }
        return Leg(
            line = lineName(line),
            product = line?.str("product") ?: "",
            walking = item.optBoolean("walking", false),
            from = placeName(item.optJSONObject("origin")),
            to = placeName(item.optJSONObject("destination")),
            departure = departure,
            arrival = arrival,
            departureDelayMinutes = if (item.isNull("departureDelay")) 0 else item.optInt("departureDelay") / 60,
            arrivalDelayMinutes = if (item.isNull("arrivalDelay")) 0 else item.optInt("arrivalDelay") / 60,
            platform = item.str("departurePlatform"),
            direction = item.str("direction"),
            cancelled = item.optBoolean("cancelled", false),
            stopTimes = times,
            arrivalPlatform = item.str("arrivalPlatform"),
            fromPosition = position(item.optJSONObject("origin")),
            toPosition = position(item.optJSONObject("destination")),
        )
    }

    /** Latitude and longitude of a stop (its "location") or of an address or point of interest. */
    private fun position(place: JSONObject?): Pair<Double, Double>? {
        val holder = place?.optJSONObject("location") ?: place ?: return null
        val latitude = holder.dbl("latitude") ?: return null
        val longitude = holder.dbl("longitude") ?: return null
        return latitude to longitude
    }

    /**
     * The name of a line without the word "Bus" in front, as the badges of the phone app show
     * it; an S- or U-Bahn line named by its number alone gets its letter ("1" is "S1", see
     * [rapidLineName]).
     */
    private fun lineName(line: JSONObject?): String? {
        val json = line ?: return null
        val name = json.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return rapidLineName(name.replace(BUS_WORD, "").ifEmpty { name }, json.str("product") ?: "")
    }

    private fun placeName(place: JSONObject?): String = place?.str("name") ?: place?.str("address") ?: ""

    /**
     * GET [path] off the main thread. The community service is often slow or answers 502/503
     * for a moment, so a timeout or a 5xx/429 is tried once more after a short pause; any
     * failure ends as a [BvgException] that tells why.
     */
    private suspend fun fetch(path: String): String {
        val endpoint = path.substringBefore('?')
        var last: BvgException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (attempt > 1) delay(RETRY_PAUSE_MILLIS)
            val started = System.currentTimeMillis()
            try {
                val text = withContext(Dispatchers.IO) { get(path) }
                Log.d(LOG_TAG, "BVG $endpoint ok in ${System.currentTimeMillis() - started} ms (attempt $attempt)")
                return text
            } catch (e: BvgException) {
                Log.d(LOG_TAG, "BVG $endpoint failed after ${System.currentTimeMillis() - started} ms (attempt $attempt): ${e.failure} ${e.message}")
                if (e.failure != BvgFailure.Unreachable) throw e
                last = e
            }
        }
        throw last ?: BvgException(BvgFailure.Unreachable, "no attempt made")
    }

    /**
     * Parses an answer off the main thread (an answer with all stops of a journey is large);
     * JSON that does not fit ends as a [BvgFailure.BadAnswer].
     */
    private suspend inline fun <T> parse(what: String, crossinline block: () -> T): T =
        withContext(Dispatchers.Default) {
            try {
                block()
            } catch (e: JSONException) {
                Log.d(LOG_TAG, "BVG $what: answer does not parse: ${e.message}")
                throw BvgException(BvgFailure.BadAnswer, "unexpected answer for $what", e)
            }
        }

    private fun get(path: String): String {
        val connection = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw classify(e)
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NextStop-Wear-dev/0.1 (private test build)")
            val code = connection.responseCode
            when {
                code in 200..299 -> {}
                code >= 500 || code == 429 -> throw BvgException(BvgFailure.Unreachable, "HTTP $code")
                else -> throw BvgException(BvgFailure.BadAnswer, "HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: BvgException) {
            throw e
        } catch (e: IOException) {
            throw classify(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun classify(e: IOException): BvgException {
        val failure = when (e) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> BvgFailure.NoNetwork
            // Timeouts (SocketTimeoutException is one), TLS and stream errors: the service is slow or down.
            else -> BvgFailure.Unreachable
        }
        return BvgException(failure, "${e.javaClass.simpleName}: ${e.message}", e)
    }

    companion object {
        const val DEFAULT_URL = "https://v6.bvg.transport.rest"
        private const val CONNECT_TIMEOUT_MILLIS = 6_000
        private const val READ_TIMEOUT_MILLIS = 10_000
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_PAUSE_MILLIS = 1_500L
        private val PRODUCT_ORDER = listOf("suburban", "subway", "tram", "bus", "ferry", "express", "regional")
    }
}

private val BUS_WORD = Regex("^bus\\s+(?=\\S)", RegexOption.IGNORE_CASE)

private fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key)

private fun JSONObject.dbl(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.millis(key: String): Long? =
    str(key)?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
