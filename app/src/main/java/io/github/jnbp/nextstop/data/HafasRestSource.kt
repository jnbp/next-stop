package io.github.jnbp.nextstop.data

import android.text.Html
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared client for REST APIs in the shape of hafas-rest-api v6, such as the community
 * service at v6.bvg.transport.rest. The providers differ in the
 * names of their products, in some options and in what they support; the subclasses fill
 * in those parts.
 */
abstract class HafasRestSource(baseUrl: String) : TransitSource {
    private val baseUrl: String = baseUrl.trim().trimEnd('/')

    /** The query parameters that switch off every product of the app that is not in [products]. */
    protected abstract fun productQuery(products: Set<String>): String

    /** The product of a line or stop as the app names it (see [PRODUCT_KEYS] and "express"). */
    protected open fun productKey(raw: String): String = raw

    /** The query parameters for walking speed and accessibility, for providers that take them. */
    protected open fun optionQuery(options: SearchOptions): String {
        val accessibility = options.accessibility.apiValue
        return "&walkingSpeed=${options.walkingSpeed.apiValue}" +
            (if (accessibility == null) "" else "&accessibility=$accessibility")
    }

    // Names, directions and notices come in the language of the app where the provider has them.
    private val language: String
        get() = AppLanguage.code

    override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> =
        withContext(Dispatchers.IO) {
            val json = JSONArray(
                get("/locations/nearby?latitude=$latitude&longitude=$longitude&results=$results&language=$language"),
            )
            (0 until json.length()).mapNotNull { index -> parseStop(json.getJSONObject(index)) }
        }

    override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? =
        withContext(Dispatchers.IO) {
            val json = JSONArray(
                get(
                    "/locations/nearby?latitude=$latitude&longitude=$longitude" +
                        "&results=1&poi=true&distance=$NEAREST_PLACE_METERS&language=$language",
                ),
            )
            (0 until json.length()).firstNotNullOfOrNull { index -> parseStop(json.getJSONObject(index)) }
        }

    override suspend fun searchStops(query: String, results: Int): List<Stop> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val json = JSONArray(
                get("/locations?query=$encoded&results=$results&poi=true&addresses=true&language=$language"),
            )
            (0 until json.length()).mapNotNull { index -> parseStop(json.getJSONObject(index)) }
        }

    override suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int,
        products: Set<String>?,
    ): List<Vehicle> = withContext(Dispatchers.IO) {
        // The radar knows no filter by product and fills its limit with whatever it meets
        // first, mostly buses and trams. For some products only it is asked for many more,
        // without their shapes (a small answer), and the rest is dropped here.
        val wanted = products?.let { if ("regional" in it) it + "express" else it }
        val query = if (wanted == null) {
            "&results=$results&duration=30&frames=3&polylines=true"
        } else {
            "&results=$FILTERED_RADAR_RESULTS&duration=30&frames=1&polylines=false"
        }
        val root = JSONObject(get("/radar?north=$north&west=$west&south=$south&east=$east$query&language=$language"))
        val list = root.optJSONArray("movements") ?: return@withContext emptyList<Vehicle>()
        val found = (0 until list.length()).mapNotNull { index ->
            // One odd entry must not cost the whole update.
            try {
                parseVehicle(list.getJSONObject(index))
            } catch (e: Exception) {
                null
            }
        }
        if (wanted == null) found else found.filter { it.product in wanted }.take(results)
    }

    /**
     * Vehicles of the given [products] only inside the given box, without their movement: just
     * enough to know which trips of which lines run there right now (see the network map).
     */
    suspend fun vehiclesOf(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        products: Set<String>,
        results: Int = FILTERED_RADAR_RESULTS,
    ): List<Vehicle> = withContext(Dispatchers.IO) {
        // No product filter at the radar (see vehicles): many results, filtered by the caller.
        val root = JSONObject(
            get(
                "/radar?north=$north&west=$west&south=$south&east=$east" +
                    "&results=$results&duration=30&frames=1&polylines=false&language=$language",
            ),
        )
        val list = root.optJSONArray("movements") ?: return@withContext emptyList<Vehicle>()
        (0 until list.length()).mapNotNull { index ->
            try {
                parseVehicle(list.getJSONObject(index))
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> =
        withContext(Dispatchers.IO) {
            val id = pathSegment(tripId)
            val root = JSONObject(get("/trips/$id?polyline=true&stopovers=false&remarks=false&language=$language"))
            parsePolyline(root.optJSONObject("trip")?.optJSONObject("polyline"))
        }

    override suspend fun tripStopovers(tripId: String): List<Stopover> =
        withContext(Dispatchers.IO) {
            val id = pathSegment(tripId)
            val root = JSONObject(get("/trips/$id?polyline=false&stopovers=true&remarks=false&language=$language"))
            parseStopovers(root.optJSONObject("trip")?.optJSONArray("stopovers"))
        }

    override suspend fun tripCourse(tripId: String): TripCourse =
        withContext(Dispatchers.IO) {
            val id = pathSegment(tripId)
            val root = JSONObject(get("/trips/$id?polyline=true&stopovers=true&remarks=false&language=$language"))
            val trip = root.optJSONObject("trip") ?: throw IOException("no trip")
            val line = trip.optJSONObject("line")
            TripCourse(
                tripId = tripId,
                line = lineNameOf(line),
                product = productOf(line),
                direction = trip.stringOrNull("direction"),
                stops = parseStopovers(trip.optJSONArray("stopovers")),
                path = parsePolyline(trip.optJSONObject("polyline")),
            )
        }

    /** GeoJSON points (longitude first) as pairs of latitude and longitude. */
    private fun parsePolyline(polyline: JSONObject?): List<Pair<Double, Double>> {
        val features = polyline?.optJSONArray("features") ?: return emptyList()
        val points = mutableListOf<Pair<Double, Double>>()
        for (index in 0 until features.length()) {
            val coordinates = features.optJSONObject(index)
                ?.optJSONObject("geometry")
                ?.optJSONArray("coordinates")
            if (coordinates != null && coordinates.length() >= 2) {
                points.add(coordinates.optDouble(1) to coordinates.optDouble(0))
            }
        }
        return points.filter { !it.first.isNaN() && !it.second.isNaN() }
    }

    private fun parseVehicle(item: JSONObject): Vehicle? {
        val tripId = item.stringOrNull("tripId") ?: return null
        val location = item.optJSONObject("location") ?: return null
        val latitude = location.doubleOrNull("latitude") ?: return null
        val longitude = location.doubleOrNull("longitude") ?: return null
        val line = item.optJSONObject("line")

        // The shape of the movement: GeoJSON points, longitude first. It may begin behind the
        // vehicle (at the stop it left), so only the part ahead of where it is now counts: the
        // glide and the arrow must not run back along the way it came.
        val shape = mutableListOf<Pair<Double, Double>>()
        val features = item.optJSONObject("polyline")?.optJSONArray("features")
        if (features != null) {
            for (index in 0 until features.length()) {
                val coordinates = features.optJSONObject(index)
                    ?.optJSONObject("geometry")
                    ?.optJSONArray("coordinates")
                if (coordinates != null && coordinates.length() >= 2) {
                    shape.add(coordinates.getDouble(1) to coordinates.getDouble(0))
                }
            }
        }
        val path = wayAhead(latitude to longitude, shape)

        // The next stop is the first one the vehicle has not reached yet.
        var nextStop: String? = null
        var nextStopTime: OffsetDateTime? = null
        // The radar places a vehicle by its stopovers. A delay that is set (0 included) is a
        // forecast of the provider: live data. Null delays everywhere mean the timetable only.
        var realtime = false
        val stopovers = item.optJSONArray("nextStopovers")
        if (stopovers != null) {
            for (index in 0 until stopovers.length()) {
                val stopover = stopovers.optJSONObject(index) ?: continue
                if (!stopover.isNull("arrivalDelay") || !stopover.isNull("departureDelay")) {
                    realtime = true
                    break
                }
            }
            val now = OffsetDateTime.now()
            for (index in 0 until stopovers.length()) {
                val stopover = stopovers.getJSONObject(index)
                val time = stopover.timeOrNull("arrival") ?: stopover.timeOrNull("plannedArrival") ?: continue
                if (time.isAfter(now)) {
                    nextStop = stopover.optJSONObject("stop")?.stringOrNull("name")
                    nextStopTime = time
                    break
                }
            }
        }
        return Vehicle(
            tripId = tripId,
            line = lineNameOf(line) ?: "?",
            product = productOf(line),
            direction = item.stringOrNull("direction") ?: "",
            path = path,
            nextStop = nextStop,
            nextStopTime = nextStopTime,
            realtime = realtime,
        )
    }

    override suspend fun departures(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> =
        withContext(Dispatchers.IO) {
            val root = JSONObject(
                get(
                    "/stops/${pathSegment(stopId)}/departures?results=$results&duration=60" +
                        productQuery(products) + timeQuery("when", time),
                ),
            )
            val list = root.getJSONArray("departures")
            mergeReplacements((0 until list.length()).map { index -> parseDeparture(list.getJSONObject(index)) })
        }

    override suspend fun stopLines(stopId: String): List<StopLine> {
        val found = linesOfStop(stopId)
        // A stop without its lines in the answer: those of the departures of the next hour.
        return found.ifEmpty { super.stopLines(stopId) }
    }

    private suspend fun linesOfStop(stopId: String): List<StopLine> = withContext(Dispatchers.IO) {
        val root = JSONObject(get("/stops/${pathSegment(stopId)}?linesOfStops=true&language=$language"))
        val lines = root.optJSONArray("lines")
        val found = if (lines == null) {
            emptyList()
        } else {
            (0 until lines.length()).mapNotNull { index ->
                val line = lines.optJSONObject(index) ?: return@mapNotNull null
                val name = lineNameOf(line) ?: return@mapNotNull null
                StopLine(name, productOf(line))
            }.distinct()
        }
        found
    }

    override suspend fun lineDepartures(stopId: String, line: String, product: String): List<Departure> =
        withContext(Dispatchers.IO) {
            // A whole day ahead, so a line that runs rarely or only at night is found too.
            val root = JSONObject(
                get(
                    "/stops/${pathSegment(stopId)}/departures?results=300&duration=1440&remarks=false" +
                        productQuery(setOf(searchProductOf(product))) + "&language=$language",
                ),
            )
            val list = root.getJSONArray("departures")
            (0 until list.length()).map { index -> parseDeparture(list.getJSONObject(index)) }
                .filter { it.line == line && !it.cancelled && it.tripId != null }
                .distinctBy { it.direction }
        }

    override suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival> =
        withContext(Dispatchers.IO) {
            val root = JSONObject(
                get(
                    "/stops/${pathSegment(stopId)}/arrivals?duration=$durationMinutes&remarks=false&language=$language" +
                        productQuery(products) + timeQuery("when", time),
                ),
            )
            val list = root.optJSONArray("arrivals") ?: return@withContext emptyList()
            (0 until list.length()).mapNotNull { index ->
                val item = list.optJSONObject(index) ?: return@mapNotNull null
                val line = item.optJSONObject("line")
                Arrival(
                    tripId = item.stringOrNull("tripId"),
                    line = lineNameOf(line) ?: return@mapNotNull null,
                    product = productOf(line),
                    time = item.timeOrNull("when") ?: item.timeOrNull("plannedWhen"),
                )
            }
        }

    override suspend fun journeys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int,
        departure: OffsetDateTime?,
        arrival: OffsetDateTime?,
    ): JourneyPage = withContext(Dispatchers.IO) {
        val time = timeQuery("departure", departure) + timeQuery("arrival", arrival)
        parseJourneyPage(get("/journeys?" + journeyQuery(from, to, options, results, departure ?: arrival) + time)).fitting(options)
    }

    /** A time as a query parameter in ISO 8601 with its offset; nothing when there is no time. */
    private fun timeQuery(name: String, time: OffsetDateTime?): String =
        if (time == null) {
            ""
        } else {
            "&$name=" + URLEncoder.encode(time.withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), "UTF-8")
        }

    override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? =
        withContext(Dispatchers.IO) {
            val token = pathSegment(refreshToken)
            val root = JSONObject(
                get("/journeys/$token?stopovers=$stopovers&remarks=true&polylines=$polylines&language=$language"),
            )
            parseJourney(root.optJSONObject("journey") ?: root)
        }

    override suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int,
    ): JourneyPage = withContext(Dispatchers.IO) {
        val reference = URLEncoder.encode(earlierRef, "UTF-8")
        parseJourneyPage(get("/journeys?" + journeyQuery(from, to, options, results) + "&earlierThan=$reference")).fitting(options)
    }

    override suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int,
    ): JourneyPage = withContext(Dispatchers.IO) {
        val reference = URLEncoder.encode(laterRef, "UTF-8")
        parseJourneyPage(get("/journeys?" + journeyQuery(from, to, options, results) + "&laterThan=$reference")).fitting(options)
    }

    private fun journeyQuery(from: Stop, to: Stop, options: SearchOptions, results: Int, time: OffsetDateTime? = null): String =
        placeQuery("from", from) + "&" + placeQuery("to", to) +
            "&results=$results&stopovers=false&remarks=true&polylines=true&language=$language" +
            "&transferTime=${options.comfort.minutes}" + optionQuery(options) +
            productQuery(searchProducts(options, from, time)) + viaQuery(options.via) + ticketQuery(options)

    /**
     * The products a journey search asks for: those of [options]; with the bike combo without
     * coaches and, unless a bus may carry the bicycle there and then, without buses (see
     * [BikeRules]). HAFAS has no ways by bicycle to the first stop: the combo is the bicycle
     * carried along on rail only here.
     */
    private fun searchProducts(options: SearchOptions, from: Stop, time: OffsetDateTime?): Set<String> =
        if (!options.bikeCombo) {
            options.products
        } else {
            BikeRules.comboProducts(options.products, BikeRules.busesWorthAsking(from.position, time))
        }

    /**
     * Bicycle (also the bike combo): `bike=true`, only journeys that take a bicycle along. The
     * Deutschlandticket is long-distance trains off, which [productQuery] does.
     */
    private fun ticketQuery(options: SearchOptions): String =
        if (options.carriesBike) "&bike=true" else ""

    /**
     * A station the journeys must pass (parameter `via` of /journeys, a stop id). The API has
     * no time to stay there: a stay is put together from two searches (see RoutingSource).
     */
    private fun viaQuery(via: Via?): String =
        if (via == null || via.place.kind != PlaceKind.Stop) "" else "&via=" + URLEncoder.encode(via.place.id, "UTF-8")

    /** The provider wants stops by id, points of interest by id and position, addresses by position. */
    private fun placeQuery(prefix: String, place: Stop): String {
        val name = URLEncoder.encode(place.name, "UTF-8")
        val id = URLEncoder.encode(place.id, "UTF-8")
        val position = "$prefix.latitude=${place.latitude}&$prefix.longitude=${place.longitude}"
        return when (place.kind) {
            PlaceKind.Stop -> "$prefix=$id"
            PlaceKind.Poi -> "$prefix.id=$id&$position&$prefix.name=$name"
            PlaceKind.Address -> "$position&$prefix.address=$name"
        }
    }

    private fun parseJourneyPage(body: String): JourneyPage {
        val root = JSONObject(body)
        val list = root.getJSONArray("journeys")
        val journeys = (0 until list.length()).mapNotNull { index -> parseJourney(list.getJSONObject(index)) }
        return JourneyPage(journeys, root.stringOrNull("earlierRef"), root.stringOrNull("laterRef"))
    }

    private fun parseStop(item: JSONObject): Stop? {
        val distance = if (item.isNull("distance")) null else item.getInt("distance")
        if (item.stringOrNull("type") == "location") {
            // A point of interest or an address: the position sits directly on the entry.
            val latitude = item.doubleOrNull("latitude") ?: return null
            val longitude = item.doubleOrNull("longitude") ?: return null
            val id = item.stringOrNull("id")
            val isPoi = item.optBoolean("poi", false) && id != null
            val name = (if (isPoi) null else item.stringOrNull("address"))
                ?: item.stringOrNull("name")
                ?: return null
            // A point of interest comes as "Name, Street 1, 12345 Town": the address is its second line.
            val detail = if (isPoi) name.substringAfter(", ", "").takeIf { it.isNotEmpty() } else null
            return Stop(
                id = id ?: "address:$latitude,$longitude",
                name = if (detail != null) name.substringBefore(", ") else name,
                latitude = latitude,
                longitude = longitude,
                distanceMeters = distance,
                kind = if (isPoi) PlaceKind.Poi else PlaceKind.Address,
                detail = detail,
            )
        }
        if (item.isNull("id") || item.isNull("name")) return null
        val location = item.optJSONObject("location")
        val products = mutableSetOf<String>()
        val flags = item.optJSONObject("products")
        if (flags != null) {
            for (key in flags.keys()) {
                if (flags.optBoolean(key, false)) products.add(productKey(key))
            }
        }
        return Stop(
            id = item.getString("id"),
            name = item.getString("name"),
            latitude = location?.doubleOrNull("latitude"),
            longitude = location?.doubleOrNull("longitude"),
            distanceMeters = distance,
            products = products,
        )
    }

    private fun parseDeparture(item: JSONObject): Departure {
        val line = item.optJSONObject("line")
        return Departure(
            line = lineNameOf(line) ?: "?",
            product = productOf(line),
            direction = item.stringOrNull("direction") ?: "",
            time = item.timeOrNull("when"),
            plannedTime = item.timeOrNull("plannedWhen"),
            delaySeconds = if (item.isNull("delay")) null else item.getInt("delay"),
            platform = item.stringOrNull("platform"),
            cancelled = item.optBoolean("cancelled", false),
            tripId = item.stringOrNull("tripId"),
            destination = item.optJSONObject("destination")?.let { parseStop(it) },
            additional = isAdditional(item.optJSONArray("remarks")),
            warnings = parseWarnings(item.optJSONArray("remarks"), item.optJSONObject("stop")?.let { parseStop(it) }),
            // Where it leaves: the colour of its line depends on the place.
            at = item.optJSONObject("stop")?.let { parseStop(it) }?.position,
        )
    }

    /** True when the remarks of a run announce it as an additional run ("Zusatzfahrt"). */
    private fun isAdditional(remarks: JSONArray?): Boolean {
        if (remarks == null) return false
        return (0 until remarks.length()).any { index ->
            remarks.optJSONObject(index)?.stringOrNull("code") == ADDITIONAL_SERVICE_CODE
        }
    }

    private fun parseJourney(item: JSONObject): Journey? {
        val list = item.optJSONArray("legs") ?: return null
        val legs = (0 until list.length()).map { index ->
            parseLeg(list.getJSONObject(index)) ?: return null
        }
        return if (legs.isEmpty()) null else Journey(legs, item.stringOrNull("refreshToken"))
    }

    private fun parseLeg(item: JSONObject): Leg? {
        val plannedDeparture = item.timeOrNull("plannedDeparture")
        val plannedArrival = item.timeOrNull("plannedArrival")
        val departure = item.timeOrNull("departure") ?: plannedDeparture ?: return null
        val arrival = item.timeOrNull("arrival") ?: plannedArrival ?: return null
        val line = item.optJSONObject("line")
        val walking = item.optBoolean("walking", false)
        val fromPlace = item.optJSONObject("origin")?.let { parseStop(it) }
        val toPlace = item.optJSONObject("destination")?.let { parseStop(it) }
        val shape = parsePolyline(item.optJSONObject("polyline"))
        return Leg(
            line = lineNameOf(line),
            product = productOf(line),
            walking = walking,
            from = placeName(item.optJSONObject("origin")),
            to = placeName(item.optJSONObject("destination")),
            departure = departure,
            plannedDeparture = plannedDeparture,
            arrival = arrival,
            plannedArrival = plannedArrival,
            departurePlatform = item.stringOrNull("departurePlatform"),
            arrivalPlatform = item.stringOrNull("arrivalPlatform"),
            direction = item.stringOrNull("direction"),
            cancelled = item.optBoolean("cancelled", false),
            realtime = !item.isNull("departureDelay") || !item.isNull("arrivalDelay"),
            path = if (walking && shape.isEmpty()) straightWay(fromPlace, toPlace) else shape,
            fromPlace = fromPlace,
            toPlace = toPlace,
            warnings = parseWarnings(item.optJSONArray("remarks")),
            stopovers = parseStopovers(item.optJSONArray("stopovers")),
            tripId = item.stringOrNull("tripId"),
            additional = isAdditional(item.optJSONArray("remarks")),
            walkMeters = if (walking) item.intOrNull("distance") else null,
        )
    }

    /** The provider sends no shape for a walk: the straight line between both ends stands in for it. */
    private fun straightWay(from: Stop?, to: Stop?): List<Pair<Double, Double>> {
        val start = from?.position ?: return emptyList()
        val end = to?.position ?: return emptyList()
        return if (start == end) emptyList() else listOf(start, end)
    }

    /**
     * The disruption notices among the remarks of a leg or departure; hints such as bicycle
     * conveyance are left out. [stop] is where the departure that carries them leaves.
     */
    private fun parseWarnings(remarks: JSONArray?, stop: Stop? = null): List<Warning> {
        if (remarks == null) return emptyList()
        return (0 until remarks.length()).mapNotNull { index ->
            val item = remarks.optJSONObject(index)
            if (item == null || item.stringOrNull("type") != "warning") {
                null
            } else {
                // BVG ends its texts with links named "[MEHR/MORE]" and "[KARTE/MAPS]": taken out as links.
                val summary = parseNoticeText(item.stringOrNull("summary")) { plainText(it) }
                val text = parseNoticeText(item.stringOrNull("text")) { plainText(it) }
                if (summary.text.isEmpty() && text.text.isEmpty()) {
                    null
                } else {
                    Warning(
                        summary = summary.text,
                        text = text.text,
                        id = item.stringOrNull("id"),
                        validFrom = item.safeTime("validFrom"),
                        validUntil = item.safeTime("validUntil"),
                        links = (summary.links + text.links).distinct(),
                        stop = stop,
                    )
                }
            }
        }.distinct()
    }

    // A time of a notice that does not parse is left out rather than losing the notice.
    private fun JSONObject.safeTime(key: String): OffsetDateTime? = try {
        timeOrNull(key)
    } catch (e: Exception) {
        null
    }

    /** Notices come with links and line breaks as HTML; the app shows them as plain text. */
    private fun plainText(html: String?): String =
        if (html == null) "" else Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

    private fun parseStopovers(list: JSONArray?): List<Stopover> {
        if (list == null) return emptyList()
        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index)
            val stop = item?.optJSONObject("stop")
            val name = stop?.stringOrNull("name")
            if (item == null || stop == null || name == null) {
                null
            } else {
                Stopover(
                    name = name,
                    arrival = item.timeOrNull("arrival") ?: item.timeOrNull("plannedArrival"),
                    departure = item.timeOrNull("departure") ?: item.timeOrNull("plannedDeparture"),
                    place = parseStop(stop),
                    delaySeconds = item.intOrNull("departureDelay") ?: item.intOrNull("arrivalDelay"),
                    plannedArrival = item.timeOrNull("plannedArrival"),
                    plannedDeparture = item.timeOrNull("plannedDeparture"),
                )
            }
        }
    }

    /**
     * The name of a line. A long-distance train whose name is only a number gets its kind in
     * front, as "ICE 29", from the product name the provider gives with it; an S- or U-Bahn
     * line named only by its number its letter, as "S1" (see [rapidLineName]).
     */
    private fun lineNameOf(line: JSONObject?): String? = rapidLineName(rawLineNameOf(line), productOf(line))

    private fun rawLineNameOf(line: JSONObject?): String? {
        if (line == null) return null
        // A name that is only the word for the product ("Bus") says nothing: the number of the
        // line is then taken from its id ("m85", "5-vbbbvb-187").
        val name = line.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() && !isProductWord(it) }
            ?: lineNumberOfId(line.stringOrNull("id"))
        val kind = line.stringOrNull("productName")?.trim()
            ?.takeIf { it.length in 2..4 && it.all { char -> char.isLetter() } }
        return when {
            name != null && kind != null && name.all { it.isDigit() } -> "$kind $name"
            name == null && kind != null -> line.stringOrNull("fahrtNr")?.trim()
                ?.takeIf { it.isNotEmpty() && it.all { char -> char.isDigit() } }
                ?.let { "$kind $it" } ?: kind
            else -> name
        }
    }

    /** The number of a line from its id, as "M85" from "m85" or "187" from "5-vbbbvb-187"; null when none fits. */
    private fun lineNumberOfId(id: String?): String? =
        id?.trim()?.substringAfterLast('-')
            ?.takeIf { it.length in 1..5 && it.all { char -> char.isLetterOrDigit() } && it.any { char -> char.isDigit() } }
            ?.uppercase()

    /**
     * The product of a line as the app names it; empty when the provider names none. An ICE the
     * provider puts under regional trains and a FlixBus under buses get their own (see
     * [classifyProduct]).
     */
    private fun productOf(line: JSONObject?): String {
        if (line == null) return ""
        val raw = line.stringOrNull("product")?.let { productKey(it) } ?: return ""
        val name = rawLineNameOf(line) ?: line.stringOrNull("productName")
        return classifyProduct(raw, name, line.optJSONObject("operator")?.stringOrNull("name"))
    }

    /** Stops and points of interest have a name, addresses only an address. */
    private fun placeName(place: JSONObject?): String =
        place?.stringOrNull("name") ?: place?.stringOrNull("address") ?: ""

    private fun get(request: String): String {
        // Every request in the app's language: without it the server answers in English
        // (departures carried English notices on a German phone).
        val path = if ("language=" in request) request else request + (if ('?' in request) "&" else "?") + "language=$language"
        if (path.startsWith("/journeys?")) logJourneyRequest("hafas", baseUrl + path)
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NextStop-dev/0.1 (private test build)")
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * A request the server answered with an error [code]. hafas-rest-api answers 404 when the
 * provider found nothing, for example no journey to a place it cannot reach.
 */
class HttpStatusException(val code: Int) : IOException("HTTP $code") {
    val nothingFound: Boolean get() = code == 404
}

/** An id or handle as one part of a path: encoded, and spaces must not turn into plus signs. */
private fun pathSegment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

// A pinned place is named after what lies within this distance of it.
private const val NEAREST_PLACE_METERS = 300

/** org.json returns the text "null" for JSON null; these return a real null instead. */
private fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else getString(key)

private fun JSONObject.intOrNull(key: String): Int? =
    if (isNull(key)) null else optInt(key)

private fun JSONObject.doubleOrNull(key: String): Double? =
    if (isNull(key)) null else getDouble(key)

private fun JSONObject.timeOrNull(key: String): OffsetDateTime? =
    stringOrNull(key)?.let { OffsetDateTime.parse(it) }

// The radar has no filter by product: for some products only, this many vehicles are asked
// for, so the trains are among them in a large box full of buses and trams.
private const val FILTERED_RADAR_RESULTS = 1000
