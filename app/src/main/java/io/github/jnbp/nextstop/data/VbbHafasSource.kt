package io.github.jnbp.nextstop.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The official VBB HAFAS ReST API (the "VBB API" the VBB hands out keys for), asked with the
 * user's own [accessId].
 *
 * UNVERIFIED: written from the documented pattern of HAFAS ReST APIs (location.name,
 * location.nearbystops, departureBoard, trip, journeyDetail, recon; JSON with format=json)
 * before a key was at hand. The base address and version in [DEFAULT_URL], the refresh of a
 * journey ([refreshJourney], "recon") and the shape of polylines are the least certain parts:
 * check them against the documentation that comes with the key. Whatever this source cannot
 * read fails like an unreachable server, so the next source in the list answers instead.
 *
 * Stops are named by their VBB numbers (extId, 900100003), as at BVG and public-transport-
 * enabler. There is no radar: [vehicles] and [arrivals] are not supported.
 */
class VbbHafasSource(
    private val accessId: String,
    baseUrl: String = DEFAULT_URL,
) : TransitSource {
    private val baseUrl = baseUrl.trim().trimEnd('/')

    private fun ensureKey() {
        if (accessId.isBlank()) throw NotConfiguredException(Backend.Vbb)
    }

    override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> =
        withContext(Dispatchers.IO) {
            val root = get("location.nearbystops", "originCoordLat=$latitude&originCoordLong=$longitude&maxNo=$results&r=1000&type=S")
            locations(root).filter { it.kind == PlaceKind.Stop }
        }

    override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? =
        withContext(Dispatchers.IO) {
            val root = get("location.nearbystops", "originCoordLat=$latitude&originCoordLong=$longitude&maxNo=1&r=$NEAREST_PLACE_METERS&type=SP")
            locations(root).firstOrNull()
        }

    override suspend fun searchStops(query: String, results: Int): List<Stop> =
        withContext(Dispatchers.IO) {
            locations(get("location.name", "input=${enc(query)}&maxNo=$results&type=ALL"))
        }

    override suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int,
        products: Set<String>?,
    ): List<Vehicle> = throw NotSupportedException("VBB HAFAS vehicles")

    override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> = tripCourse(tripId).path

    override suspend fun tripStopovers(tripId: String): List<Stopover> = tripCourse(tripId).stops

    override suspend fun tripCourse(tripId: String): TripCourse = withContext(Dispatchers.IO) {
        val root = get("journeyDetail", "id=${enc(tripId)}&poly=1")
        val stops = stopovers(root.optJSONObject("Stops"))
        val product = productList(root).firstOrNull()
        TripCourse(
            tripId = tripId,
            line = product?.let { lineName(it) },
            product = product?.let { productKey(it) } ?: "",
            direction = root.optJSONObject("Directions")?.optJSONArray("Direction")?.optJSONObject(0)?.optString("value")
                ?.takeIf { it.isNotEmpty() },
            stops = stops,
            path = polyline(root),
        )
    }

    override suspend fun departures(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> = withContext(Dispatchers.IO) {
        val at = (time ?: OffsetDateTime.now()).atZoneSameInstant(BERLIN)
        val root = get(
            "departureBoard",
            "id=${enc(stopId)}&date=${at.toLocalDate()}&time=${at.format(HOUR_MINUTE)}" +
                "&duration=60&maxJourneys=$results&products=${productMask(products)}",
        )
        val list = root.optJSONArray("Departure") ?: JSONArray()
        (0 until list.length()).mapNotNull { index -> departure(list.getJSONObject(index)) }
    }

    override suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival> = throw NotSupportedException("VBB HAFAS arrivals")

    override suspend fun journeys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int,
        departure: OffsetDateTime?,
        arrival: OffsetDateTime?,
    ): JourneyPage = withContext(Dispatchers.IO) {
        val at = (arrival ?: departure ?: OffsetDateTime.now()).atZoneSameInstant(BERLIN)
        val time = "&date=${at.toLocalDate()}&time=${at.format(HOUR_MINUTE)}" +
            (if (arrival != null) "&searchForArrival=1" else "")
        journeyPage(get("trip", tripQuery(from, to, options, results, arrival ?: departure) + time)).fitting(options)
    }

    override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? =
        withContext(Dispatchers.IO) {
            // UNVERIFIED: the reconstruction service of HAFAS ReST, given the ctxRecon of a trip.
            val root = get("recon", "ctx=${enc(refreshToken)}&passlist=${flag(stopovers)}&poly=${flag(polylines)}")
            val trips = root.optJSONArray("Trip") ?: return@withContext null
            trips.optJSONObject(0)?.let { journey(it) }
        }

    override suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int,
    ): JourneyPage = withContext(Dispatchers.IO) {
        journeyPage(get("trip", tripQuery(from, to, options, results) + "&context=${enc(earlierRef)}")).fitting(options)
    }

    override suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int,
    ): JourneyPage = withContext(Dispatchers.IO) {
        journeyPage(get("trip", tripQuery(from, to, options, results) + "&context=${enc(laterRef)}")).fitting(options)
    }

    private fun tripQuery(from: Stop, to: Stop, options: SearchOptions, results: Int, time: OffsetDateTime? = null): String {
        val via = options.via?.place?.takeIf { it.kind == PlaceKind.Stop }?.let { "&viaId=${enc(it.id)}" } ?: ""
        // The bike combo leaves out coaches and (but where a bus may carry the bicycle) buses.
        val products = if (options.bikeCombo) {
            BikeRules.comboProducts(options.products, BikeRules.busesWorthAsking(from.position, time))
        } else {
            options.products
        }
        return placeQuery("origin", from) + "&" + placeQuery("dest", to) + via +
            "&numF=$results&passlist=0&poly=1&minChangeTime=${options.comfort.minutes}" +
            "&products=${productMask(products)}"
        // The bicycle option has no parameter here that is known to work: it is left out.
    }

    /** Stops by their number, everything else by its position. */
    private fun placeQuery(prefix: String, place: Stop): String =
        if (place.kind == PlaceKind.Stop) {
            "${prefix}Id=${enc(place.id)}"
        } else {
            "${prefix}CoordLat=${place.latitude}&${prefix}CoordLong=${place.longitude}&${prefix}CoordName=${enc(place.name)}"
        }

    private fun journeyPage(root: JSONObject): JourneyPage {
        val list = root.optJSONArray("Trip") ?: JSONArray()
        val journeys = (0 until list.length()).mapNotNull { index -> journey(list.getJSONObject(index)) }
        return JourneyPage(journeys, root.optString("scrB").ifEmpty { null }, root.optString("scrF").ifEmpty { null })
    }

    private fun journey(trip: JSONObject): Journey? {
        val legs = asArray(trip.optJSONObject("LegList")?.opt("Leg"))
        val parsed = (0 until legs.length()).map { index -> leg(legs.getJSONObject(index)) ?: return null }
        if (parsed.isEmpty()) return null
        return Journey(parsed, refreshToken = trip.optString("ctxRecon").ifEmpty { null })
    }

    private fun leg(item: JSONObject): Leg? {
        val origin = item.optJSONObject("Origin") ?: return null
        val destination = item.optJSONObject("Destination") ?: return null
        val type = item.optString("type")
        val walking = type == "WALK" || type == "TRSF" || type == "KISS"
        val plannedDeparture = time(origin, "date", "time")
        val plannedArrival = time(destination, "date", "time")
        val departure = time(origin, "rtDate", "rtTime") ?: plannedDeparture ?: return null
        val arrival = time(destination, "rtDate", "rtTime") ?: plannedArrival ?: return null
        val product = productList(item).firstOrNull()
        val fromPlace = place(origin)
        val toPlace = place(destination)
        val shape = polyline(item)
        return Leg(
            line = if (walking) null else product?.let { lineName(it) },
            product = if (walking) "" else product?.let { productKey(it) } ?: "",
            walking = walking,
            from = origin.optString("name"),
            to = destination.optString("name"),
            departure = departure,
            plannedDeparture = plannedDeparture,
            arrival = arrival,
            plannedArrival = plannedArrival,
            departurePlatform = origin.optString("rtTrack").ifEmpty { origin.optString("track") }.ifEmpty { null },
            arrivalPlatform = destination.optString("rtTrack").ifEmpty { destination.optString("track") }.ifEmpty { null },
            direction = item.optString("direction").ifEmpty { null },
            cancelled = item.optBoolean("cancelled", false),
            realtime = origin.has("rtTime") || destination.has("rtTime"),
            path = if (shape.isEmpty() && walking) listOfNotNull(fromPlace?.position, toPlace?.position).distinct() else shape,
            fromPlace = fromPlace,
            toPlace = toPlace,
            stopovers = stopovers(item.optJSONObject("Stops")),
            tripId = item.optJSONObject("JourneyDetailRef")?.optString("ref")?.ifEmpty { null },
            walkMeters = if (walking && item.has("dist")) item.optInt("dist") else null,
        )
    }

    private fun departure(item: JSONObject): Departure? {
        val planned = time(item, "date", "time") ?: return null
        val live = time(item, "rtDate", "rtTime")
        val product = item.optJSONObject("ProductAtStop") ?: productList(item).firstOrNull()
        return Departure(
            line = product?.let { lineName(it) } ?: item.optString("name").ifEmpty { "?" },
            product = product?.let { productKey(it) } ?: "",
            direction = item.optString("direction"),
            time = live ?: planned,
            plannedTime = planned,
            delaySeconds = live?.let { ChronoUnit.SECONDS.between(planned, it).toInt() },
            platform = item.optString("rtTrack").ifEmpty { item.optString("track") }.ifEmpty { null },
            cancelled = item.optBoolean("cancelled", false),
            tripId = item.optJSONObject("JourneyDetailRef")?.optString("ref")?.ifEmpty { null },
        )
    }

    private fun stopovers(stops: JSONObject?): List<Stopover> {
        val list = asArray(stops?.opt("Stop"))
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").ifEmpty { return@mapNotNull null }
            val plannedDeparture = time(item, "depDate", "depTime")
            val liveDeparture = time(item, "rtDepDate", "rtDepTime")
            Stopover(
                name = name,
                arrival = time(item, "rtArrDate", "rtArrTime") ?: time(item, "arrDate", "arrTime"),
                departure = liveDeparture ?: plannedDeparture,
                place = place(item),
                delaySeconds = if (liveDeparture != null && plannedDeparture != null) {
                    ChronoUnit.SECONDS.between(plannedDeparture, liveDeparture).toInt()
                } else {
                    null
                },
                plannedArrival = time(item, "arrDate", "arrTime"),
                plannedDeparture = plannedDeparture,
            )
        }
    }

    /** The entries of location.name and location.nearbystops. */
    private fun locations(root: JSONObject): List<Stop> {
        val list = root.optJSONArray("stopLocationOrCoordLocation") ?: return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val entry = list.optJSONObject(index) ?: return@mapNotNull null
            entry.optJSONObject("StopLocation")?.let { place(it) }
                ?: entry.optJSONObject("CoordLocation")?.let { place(it) }
        }
    }

    /** A stop by its VBB number; a point of interest or an address with its position. */
    private fun place(item: JSONObject): Stop? {
        val name = item.optString("name").ifEmpty { return null }
        val latitude = item.optDouble("lat").takeIf { !it.isNaN() }
        val longitude = item.optDouble("lon").takeIf { !it.isNaN() }
        val distance = if (item.has("dist")) item.optInt("dist") else null
        val extId = item.optString("extId").ifEmpty { null }
        val kind = when (item.optString("type")) {
            "POI", "P" -> PlaceKind.Poi
            "ADR", "A" -> PlaceKind.Address
            else -> if (extId != null) PlaceKind.Stop else PlaceKind.Address
        }
        return when (kind) {
            PlaceKind.Stop -> Stop(
                id = extId ?: return null,
                name = name,
                latitude = latitude,
                longitude = longitude,
                distanceMeters = distance,
                products = productsOfMask(item.optString("products").toIntOrNull() ?: 0),
            )
            else -> Stop(
                id = item.optString("id").ifEmpty { "address:$latitude,$longitude" },
                name = name,
                latitude = latitude ?: return null,
                longitude = longitude ?: return null,
                distanceMeters = distance,
                kind = kind,
            )
        }
    }

    /** The products of an entry: "Product" as a list or a single object. */
    private fun productList(item: JSONObject): List<JSONObject> {
        val raw = item.opt("Product") ?: return emptyList()
        val list = asArray(raw)
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    }

    /** The product of an entry by its class; an ICE among the regional trains or a FlixBus among the buses by its name (see [classifyProduct]). */
    private fun productKey(product: JSONObject): String {
        val raw = productOfClass(product.optString("cls").toIntOrNull() ?: 0)
        val name = product.optString("name").trim().ifEmpty { null } ?: product.optString("line").ifEmpty { null }
        return classifyProduct(raw, name, product.optString("operator").trim().ifEmpty { null })
    }

    private fun lineName(product: JSONObject): String? {
        val raw = product.optString("line").ifEmpty { null }
            ?: product.optString("displayNumber").ifEmpty { null }
            ?: product.optString("name").trim().ifEmpty { null }
        return rapidLineName(raw, productKey(product))
    }

    /** The shape of a leg or trip: "Polyline" with "crd" as longitude, latitude, … in degrees. */
    private fun polyline(item: JSONObject): List<Pair<Double, Double>> {
        val polyline = item.optJSONObject("Polyline")
            ?: item.optJSONObject("PolylineGroup")?.optJSONArray("polylineDesc")?.optJSONObject(0)
            ?: return emptyList()
        val crd = polyline.optJSONArray("crd") ?: return emptyList()
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        while (index + 1 < crd.length()) {
            val longitude = crd.optDouble(index)
            val latitude = crd.optDouble(index + 1)
            if (!latitude.isNaN() && !longitude.isNaN() && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                points.add(latitude to longitude)
            }
            index += 2
        }
        return points
    }

    private fun time(item: JSONObject, dateKey: String, timeKey: String): OffsetDateTime? {
        val date = item.optString(dateKey).ifEmpty { return null }
        val clock = item.optString(timeKey).ifEmpty { return null }
        return try {
            ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(clock), BERLIN).toOffsetDateTime()
        } catch (e: java.time.format.DateTimeParseException) {
            null
        }
    }

    private fun get(service: String, query: String): JSONObject {
        ensureKey()
        // The key is part of the address: this address is never logged.
        val url = "$baseUrl/$service?accessId=${enc(accessId)}&format=json&lang=${language()}&$query"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            // A wrong key comes back as 401 or 403: like a server error, the next source answers.
            if (code !in 200..299) throw HttpStatusException(if (code == 404) 502 else code)
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val error = root.optString("errorCode")
            if (error.isNotEmpty()) throw IOException("VBB HAFAS error $error")
            return root
        } finally {
            connection.disconnect()
        }
    }

    private fun language(): String = AppLanguage.code

    companion object {
        /** UNVERIFIED: the address of the VBB API as documented for HAFAS ReST 2.x. */
        const val DEFAULT_URL = "https://fahrinfo.vbb.de/restproxy/2.32"

        private val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private const val NEAREST_PLACE_METERS = 300
    }
}

// The product classes of the VBB, as bits: S-Bahn 1, U-Bahn 2, tram 4, bus 8, ferry 16,
// long-distance 32, regional 64 (also those of BVG and public-transport-enabler).
private val CLASS_PRODUCTS = listOf(1 to "suburban", 2 to "subway", 4 to "tram", 8 to "bus", 16 to "ferry", 32 to "express", 64 to "regional")

/** The product of a VBB product class; the lowest bit counts when there are several. */
internal fun productOfClass(cls: Int): String = CLASS_PRODUCTS.firstOrNull { (bit, _) -> cls and bit != 0 }?.second ?: ""

/** The products of a VBB bit mask. */
internal fun productsOfMask(mask: Int): Set<String> = CLASS_PRODUCTS.filter { (bit, _) -> mask and bit != 0 }.map { it.second }.toSet()

/**
 * The VBB bit mask of the app's [products]. The VBB has no class for coaches: they run as buses,
 * so buses stay on while coaches are, and the coaches among them are dropped afterwards when
 * off (see [productsFit]).
 */
internal fun productMask(products: Set<String>): Int =
    CLASS_PRODUCTS.filter { (_, key) ->
        if (key == "bus") "bus" in products || "coach" in products else key in products
    }.sumOf { it.first }

/** One object or a list of them as a list, as HAFAS ReST sends either. */
private fun asArray(value: Any?): JSONArray = when (value) {
    is JSONArray -> value
    is JSONObject -> JSONArray().put(value)
    else -> JSONArray()
}

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun flag(value: Boolean): Int = if (value) 1 else 0
