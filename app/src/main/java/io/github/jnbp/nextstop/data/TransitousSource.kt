package io.github.jnbp.nextstop.data

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data for all of Germany (and beyond) from Transitous, a community service running MOTIS 2
 * on open timetable data: https://transitous.org/api/.
 *
 * Transitous may only be used by free, open-source, non-commercial apps, which must name
 * themselves with a contact in the User-Agent, link its list of data sources and credit
 * OpenStreetMap. Routing (/api/v6/plan) is its most expensive request: answers are kept for a
 * short while, and a journey is loaded again trip by trip through /api/v6/trip instead of a
 * new search. The shape of a trip never changes, so it is fetched once and then kept.
 *
 * What MOTIS has no counterpart for comes back empty; nothing here throws for a missing feature.
 */
class TransitousSource(
    private val userAgent: String,
    baseUrl: String = DEFAULT_URL,
) : TransitSource {
    private val baseUrl: String = baseUrl.trim().trimEnd('/')

    // Short-lived answers by request, so that repeated asks within seconds cost nothing.
    private val cache = object : LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean =
            size > CACHE_ENTRIES
    }

    // The way of a trip on the map by its id; it does not change, so it is fetched only once.
    private val shapes = object : LinkedHashMap<String, List<Pair<Double, Double>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Pair<Double, Double>>>?): Boolean =
            size > SHAPE_ENTRIES
    }

    private val language: String
        get() = AppLanguage.code

    override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> =
        withContext(Dispatchers.IO) {
            val json = JSONArray(
                get("/api/v1/reverse-geocode?place=$latitude,$longitude&type=STOP&numResults=$results", CACHE_PLACES_MILLIS),
            )
            matches(json, latitude to longitude)
                .filter { it.kind == PlaceKind.Stop }
                .sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
                .take(results)
        }

    override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? =
        withContext(Dispatchers.IO) {
            val json = JSONArray(get("/api/v1/reverse-geocode?place=$latitude,$longitude&numResults=3", CACHE_PLACES_MILLIS))
            matches(json, latitude to longitude)
                .filter { (it.distanceMeters ?: Int.MAX_VALUE) <= NEAREST_PLACE_METERS }
                .minByOrNull { it.distanceMeters ?: Int.MAX_VALUE }
        }

    /** Where the search for places looks first; null for no preference. Rounded in the request. */
    @Volatile
    var searchBias: Pair<Double, Double>? = null

    override suspend fun searchStops(query: String, results: Int): List<Stop> = searchStopsNear(query, results, searchBias)

    /** Places that match [query], looking first around [near]; with null without any preference of place. */
    suspend fun searchStopsNear(query: String, results: Int, near: Pair<Double, Double>?): List<Stop> =
        withContext(Dispatchers.IO) {
            val bias = near?.let { "&place=%.2f,%.2f".format(Locale.ROOT, it.first, it.second) } ?: ""
            val json = JSONArray(
                get("/api/v1/geocode?text=${encode(query)}&numResults=$results&language=$language$bias", CACHE_PLACES_MILLIS),
            )
            matches(json, null).take(results)
        }

    /**
     * Vehicles from the trips MOTIS shows on its map: each segment that is under way now is
     * placed along its shape by the time that has passed. Only for a box of city size, as the
     * request grows with the area.
     */
    override suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int,
        products: Set<String>?,
    ): List<Vehicle> = withContext(Dispatchers.IO) {
        // "regional" stands for long-distance trains too, as in the search.
        val wanted = products?.let { if ("regional" in it) it + "express" else it }
        val width = east - west
        if (width <= 0 || width > MAX_VEHICLE_BOX_DEGREES || north - south > MAX_VEHICLE_BOX_DEGREES) {
            return@withContext emptyList<Vehicle>()
        }
        val zoom = (ln(360.0 / width) / ln(2.0)).coerceIn(10.0, 18.0)
        val now = OffsetDateTime.now()
        val query = "/api/v6/map/trips?zoom=${"%.1f".format(Locale.ROOT, zoom)}" +
            "&min=$south,$west&max=$north,$east" +
            "&startTime=${encode(isoTime(now))}&endTime=${encode(isoTime(now.plusSeconds(60)))}" +
            "&precision=5&language=$language"
        val list = JSONArray(get(query, CACHE_VEHICLES_MILLIS))
        val found = LinkedHashMap<String, Vehicle>()
        for (index in 0 until list.length()) {
            if (found.size >= results) break
            val vehicle = try {
                vehicleOf(list.getJSONObject(index), now)
            } catch (e: Exception) {
                // One odd entry must not cost the whole update.
                null
            } ?: continue
            if (wanted != null && vehicle.product !in wanted) continue
            found.putIfAbsent(vehicle.tripId, vehicle)
        }
        found.values.toList()
    }

    private fun vehicleOf(segment: JSONObject, now: OffsetDateTime): Vehicle? {
        val trip = segment.optJSONArray("trips")?.optJSONObject(0) ?: return null
        val tripId = trip.text("tripId") ?: return null
        val departure = segment.time("departure") ?: return null
        val arrival = segment.time("arrival") ?: return null
        if (now.isBefore(departure) || now.isAfter(arrival)) return null
        val shape = decodePolyline(segment.text("polyline") ?: return null, 5)
        if (shape.size < 2) return null
        val total = Duration.between(departure, arrival).toMillis().coerceAtLeast(1)
        fun at(moment: OffsetDateTime): Pair<Double, Double> {
            val passed = Duration.between(departure, moment).toMillis().coerceIn(0, total)
            return pointAlong(shape, passed.toDouble() / total)
        }
        val mode = segment.text("mode") ?: ""
        val line = rapidLineName(
            composeLineName(
                mode = mode,
                route = trip.text("routeShortName"),
                number = null,
                display = trip.text("displayName"),
                category = null,
                longName = null,
                agency = null,
            ),
            productKey(mode),
        )
        val product = classifyProduct(productKey(mode), line)
        val path = if (isStraightRoadHop(product, shape)) {
            // A bus hop without a shape (a rail replacement, often) is a straight line through the
            // fields: the bus stands at the stop it left until halfway, then at the next one.
            val passed = Duration.between(departure, now).toMillis()
            listOf(if (passed < total / 2) shape.first() else shape.last())
        } else {
            listOf(now, now.plusSeconds(15), now.plusSeconds(30)).map { at(it) }.distinct()
        }
        LineColors.noteProvider(
            line, product,
            segment.text("routeColor") ?: trip.text("routeColor"),
            segment.text("routeTextColor") ?: trip.text("routeTextColor"),
            path.firstOrNull(),
        )
        return Vehicle(
            tripId = tripId,
            line = line ?: "?",
            product = product,
            direction = "",
            path = path,
            nextStop = segment.optJSONObject("to")?.text("name"),
            nextStopTime = arrival,
            // MOTIS marks each segment with "realTime": its times come from live data.
            realtime = segment.optBoolean("realTime", false),
            providerColor = LineColors.parseHex(segment.text("routeColor") ?: trip.text("routeColor")),
            providerTextColor = LineColors.parseHex(segment.text("routeTextColor") ?: trip.text("routeTextColor")),
        )
    }

    override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> =
        withContext(Dispatchers.IO) {
            shapeOf(tripId) ?: run {
                tripItinerary(tripId, detailed = true)
                shapeOf(tripId).orEmpty()
            }
        }

    override suspend fun tripStopovers(tripId: String): List<Stopover> =
        withContext(Dispatchers.IO) {
            val trip = tripItinerary(tripId, detailed = false)
            stopoversOf(tripPlaces(trip), trip.realtime)
        }

    override suspend fun tripCourse(tripId: String): TripCourse =
        withContext(Dispatchers.IO) {
            val trip = tripItinerary(tripId, detailed = shapeOf(tripId) == null)
            val leg = trip.legs.firstOrNull { !isStreetMode(it.text("mode")) } ?: throw IOException("no trip")
            noteColor(leg, leg.optJSONObject("from"))
            TripCourse(
                tripId = tripId,
                line = lineName(leg),
                product = productOf(leg),
                direction = leg.text("headsign"),
                stops = stopoversOf(tripPlaces(trip), trip.realtime),
                path = shapeOf(tripId).orEmpty(),
                providerColor = colorOf(leg),
                providerTextColor = textColorOf(leg),
            )
        }

    override suspend fun departures(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> = withContext(Dispatchers.IO) {
        val root = JSONObject(
            get(
                "/api/v6/stoptimes?" + stopQuery(stopId) + "&n=$results&language=$language&withAlerts=true" +
                    modeQuery("mode", products) + (time?.let { "&time=${encode(isoTime(it))}" } ?: ""),
                CACHE_STOPTIMES_MILLIS,
            ),
        )
        val list = root.optJSONArray("stopTimes") ?: return@withContext emptyList()
        (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val place = item.optJSONObject("place") ?: return@mapNotNull null
            val planned = place.time("scheduledDeparture")
            val current = place.time("departure") ?: planned
            val realtime = item.optBoolean("realTime", false)
            noteColor(item, place)
            Departure(
                line = lineName(item) ?: "?",
                product = productOf(item),
                direction = item.text("headsign") ?: item.optJSONObject("tripTo")?.text("name") ?: "",
                time = current,
                plannedTime = planned,
                delaySeconds = if (realtime && current != null && planned != null) {
                    Duration.between(planned, current).seconds.toInt()
                } else {
                    null
                },
                platform = place.text("track"),
                cancelled = item.optBoolean("cancelled", false) || item.optBoolean("tripCancelled", false),
                tripId = item.text("tripId"),
                destination = item.optJSONObject("tripTo")?.let { placeStop(it) },
                // The alerts of the run and of its stop (MOTIS sends them with withAlerts=true).
                warnings = (warningsOf(item.optJSONArray("alerts")) + warningsOf(place.optJSONArray("alerts"))).distinct(),
                providerColor = colorOf(item),
                providerTextColor = textColorOf(item),
                at = placeStop(place).position,
            )
        }
    }

    override suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival> = withContext(Dispatchers.IO) {
        val root = JSONObject(
            get(
                "/api/v6/stoptimes?" + stopQuery(stopId) + "&arriveBy=true&direction=LATER" +
                    "&window=${durationMinutes * 60}&n=1&withAlerts=false&language=$language" +
                    modeQuery("mode", products) + "&time=${encode(isoTime(time))}",
                CACHE_STOPTIMES_MILLIS,
            ),
        )
        val list = root.optJSONArray("stopTimes") ?: return@withContext emptyList()
        (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val place = item.optJSONObject("place")
            Arrival(
                tripId = item.text("tripId"),
                line = lineName(item) ?: return@mapNotNull null,
                product = productOf(item),
                time = place?.time("arrival") ?: place?.time("scheduledArrival"),
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
        val time = arrival ?: departure
        val replay = PageReplay(arriveBy = arrival != null, time = time?.let { isoTime(it) })
        plan(from, to, options, results, replay, cursor = null)
    }

    override suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int,
    ): JourneyPage = withContext(Dispatchers.IO) {
        val (replay, cursor) = PageReplay.read(earlierRef)
        plan(from, to, options, results, replay, cursor)
    }

    override suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int,
    ): JourneyPage = withContext(Dispatchers.IO) {
        val (replay, cursor) = PageReplay.read(laterRef)
        plan(from, to, options, results, replay, cursor)
    }

    /**
     * One search. A further page must repeat the first request as it was, with the cursor of
     * the page before; [replay] carries what the first request had and the cursor alone does not.
     */
    private fun plan(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int,
        replay: PageReplay,
        cursor: String?,
    ): JourneyPage {
        // The bike combo starts and ends by bicycle: MOTIS rides to the first stop only from a
        // position (pre- and post-transit modes do not apply to a stop as the end).
        val combo = options.bikeCombo
        val query = StringBuilder("/api/v6/plan?")
            .append("fromPlace=").append(encode(placeParam(from, byPosition = combo)))
            .append("&toPlace=").append(encode(placeParam(to, byPosition = combo)))
            .append("&numItineraries=").append(results)
            .append("&detailedTransfers=true&language=").append(language)
        if (replay.time != null) query.append("&time=").append(encode(replay.time))
        if (replay.arriveBy) query.append("&arriveBy=true")
        if (options.comfort.minutes > 0) query.append("&minTransferTime=").append(options.comfort.minutes)
        // Very risky also asks for changes with no time at all.
        if (options.comfort == TransferComfort.VeryRisky) query.append("&minTransferTime=0")
        when (options.walkingSpeed) {
            WalkingSpeed.Slow -> query.append("&pedestrianSpeed=0.9")
            WalkingSpeed.Fast -> query.append("&pedestrianSpeed=1.7")
            WalkingSpeed.Normal -> Unit
        }
        if (options.accessibility == Accessibility.Complete) query.append("&pedestrianProfile=WHEELCHAIR")
        if (combo) {
            // Coaches never, buses only where and when one may carry the bicycle (see BikeRules):
            // their stretches are cycled instead, to and from the stops of the trains.
            val time = replay.time?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            val buses = BikeRules.busesWorthAsking(from.position ?: positionOfId(from.id), time)
            query.append(journeyModeQuery(BikeRules.comboProducts(options.products, buses)))
            query.append("&preTransitModes=BIKE&postTransitModes=BIKE")
            query.append("&maxPreTransitTime=").append(COMBO_MAX_BIKE_SECONDS)
            query.append("&maxPostTransitTime=").append(COMBO_MAX_BIKE_SECONDS)
            query.append("&cyclingSpeed=").append(COMBO_CYCLING_SPEED)
        } else {
            query.append(journeyModeQuery(options.products))
        }
        // Only trips that allow bike carriage (MOTIS `requireBikeTransport`), also for the combo.
        if (options.carriesBike) query.append("&requireBikeTransport=true")
        // A stop to pass (MOTIS: `via`, stop ids only, and `viaMinimumStay` in minutes, 0 = pass through).
        val via = options.via
        if (via != null && via.place.kind == PlaceKind.Stop && !isPositionId(via.place.id)) {
            query.append("&via=").append(encode(via.place.id))
            query.append("&viaMinimumStay=").append(via.minutes)
        }
        if (cursor != null) query.append("&pageCursor=").append(encode(cursor))

        logJourneyRequest("transitous", baseUrl + query)
        val root = JSONObject(get(query.toString(), CACHE_PLAN_MILLIS))
        val list = root.optJSONArray("itineraries") ?: JSONArray()
        val journeys = (0 until list.length()).mapNotNull { index ->
            try {
                journeyOf(list.getJSONObject(index), from, to)
            } catch (e: Exception) {
                null
            }
        }
        return JourneyPage(
            journeys = journeys,
            earlierRef = root.text("previousPageCursor")?.let { replay.write(it) },
            laterRef = root.text("nextPageCursor")?.let { replay.write(it) },
        ).fitting(options)
    }

    private fun journeyOf(itinerary: JSONObject, from: Stop, to: Stop): Journey? {
        val list = itinerary.optJSONArray("legs") ?: return null
        val legs = (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            legOf(item, if (index == 0) from else null, if (index == list.length() - 1) to else null) ?: return null
        }
        if (legs.isEmpty()) return null
        val token = JSONArray()
        (0 until list.length()).forEach { index -> token.put(tokenLeg(list.getJSONObject(index), legs[index])) }
        return Journey(legs = legs, refreshToken = JSONObject().put("v", 1).put("l", token).toString())
    }

    /** A leg of a search; [start] and [end] stand in for its ends where these are not stops. */
    private fun legOf(item: JSONObject, start: Stop?, end: Stop?): Leg? {
        val mode = item.text("mode") ?: return null
        val walking = isStreetMode(mode)
        val fromJson = item.optJSONObject("from") ?: return null
        val toJson = item.optJSONObject("to") ?: return null
        val fromPlace = endPlace(fromJson, start)
        val toPlace = endPlace(toJson, end)
        val plannedDeparture = item.time("scheduledStartTime")
        val plannedArrival = item.time("scheduledEndTime")
        val departure = item.time("startTime") ?: plannedDeparture ?: return null
        val arrival = item.time("endTime") ?: plannedArrival ?: return null
        val realtime = item.optBoolean("realTime", false)
        val shape = item.optJSONObject("legGeometry")?.let { geometry ->
            val points = geometry.text("points")
            if (points == null) emptyList() else decodeGeometry(points, geometry, fromJson)
        }.orEmpty()
        val tripId = item.text("tripId")
        val places = if (walking) emptyList() else legPlaces(item)
        if (!walking) noteColor(item, fromJson)
        return Leg(
            line = if (walking) null else lineName(item),
            product = if (walking) "" else productOf(item),
            walking = walking,
            from = fromPlace.name,
            to = toPlace.name,
            departure = departure,
            plannedDeparture = plannedDeparture,
            arrival = arrival,
            plannedArrival = plannedArrival,
            departurePlatform = fromJson.text("track"),
            arrivalPlatform = toJson.text("track"),
            direction = item.text("headsign"),
            cancelled = item.optBoolean("cancelled", false),
            realtime = realtime,
            path = if (walking && shape.isEmpty()) straightWay(fromPlace, toPlace) else shape,
            fromPlace = fromPlace,
            toPlace = toPlace,
            warnings = warningsOf(item.optJSONArray("alerts")),
            stopovers = stopoversOf(places, realtime),
            tripId = if (walking) null else tripId,
            // MOTIS: the vehicle goes on from the ride before (interlining).
            stayOnBoard = !walking && item.optBoolean("interlineWithPreviousLeg", false),
            walkMeters = if (walking) item.number("distance")?.toInt() else null,
            cycling = mode == "BIKE",
            providerColor = if (walking) null else colorOf(item),
            providerTextColor = if (walking) null else textColorOf(item),
        )
    }

    /** What the handle of a journey keeps of one leg, to load it again without a new search. */
    private fun tokenLeg(item: JSONObject, leg: Leg): JSONObject {
        val entry = JSONObject()
        val tripId = leg.tripId
        if (tripId != null) {
            entry.put("trip", tripId)
            entry.put("fi", item.optJSONObject("from")?.text("stopId") ?: "")
            entry.put("ti", item.optJSONObject("to")?.text("stopId") ?: "")
            entry.put("fn", leg.from)
            entry.put("tn", leg.to)
            entry.put("fs", leg.plannedDeparture?.let { isoTime(it) } ?: isoTime(leg.departure))
            entry.put("ts", leg.plannedArrival?.let { isoTime(it) } ?: isoTime(leg.arrival))
        } else {
            entry.put("w", true)
            entry.put("d", Duration.between(leg.departure, leg.arrival).seconds)
            entry.put("s", isoTime(leg.departure))
            entry.put("e", isoTime(leg.arrival))
            entry.put("f", placeJson(leg.fromPlace, leg.from))
            entry.put("t", placeJson(leg.toPlace, leg.to))
            if (leg.path.size > 2) entry.put("g", encodePolyline(leg.path))
            if (leg.cycling) entry.put("b", true)
        }
        return entry
    }

    /**
     * The journey again with fresh live data, from the handle made by [journeyOf]: every ride is
     * read anew from its trip (one request per trip, cached for a short while; the shape only on
     * the first time), and the walks between them keep their length and move with the rides.
     * Null when a trip can no longer be found.
     */
    override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? =
        withContext(Dispatchers.IO) {
            val entries = try {
                JSONObject(refreshToken).getJSONArray("l")
            } catch (e: Exception) {
                return@withContext null
            }
            val rides = arrayOfNulls<Leg>(entries.length())
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val tripId = entry.text("trip") ?: continue
                rides[index] = try {
                    refreshedRide(entry, tripId, polylines) ?: return@withContext null
                } catch (e: HttpStatusException) {
                    if (e.code in 400..499) return@withContext null else throw e
                }
            }
            val legs = mutableListOf<Leg>()
            for (index in 0 until entries.length()) {
                val ride = rides[index]
                if (ride != null) {
                    legs.add(if (stopovers) ride else ride.copy(stopovers = emptyList()))
                    continue
                }
                val entry = entries.getJSONObject(index)
                val seconds = entry.optLong("d", 0)
                val before = legs.lastOrNull()
                val after = (index + 1 until entries.length()).firstNotNullOfOrNull { rides[it] }
                val start = when {
                    before != null -> before.arrival
                    after != null -> after.departure.minusSeconds(seconds)
                    else -> entry.time("s") ?: return@withContext null
                }
                legs.add(walkLeg(entry, start, start.plusSeconds(seconds)))
            }
            if (legs.isEmpty()) null else Journey(legs = legs, refreshToken = refreshToken)
        }

    private fun walkLeg(entry: JSONObject, start: OffsetDateTime, end: OffsetDateTime): Leg {
        val fromPlace = placeFromJson(entry.optJSONObject("f"))
        val toPlace = placeFromJson(entry.optJSONObject("t"))
        val shape = entry.text("g")?.let { decodePolyline(it, 5) }.orEmpty()
        return Leg(
            line = null,
            product = "",
            walking = true,
            from = fromPlace?.name ?: "",
            to = toPlace?.name ?: "",
            departure = start,
            plannedDeparture = entry.time("s"),
            arrival = end,
            plannedArrival = entry.time("e"),
            departurePlatform = null,
            arrivalPlatform = null,
            direction = null,
            cancelled = false,
            path = shape.ifEmpty { straightWay(fromPlace, toPlace) },
            fromPlace = fromPlace,
            toPlace = toPlace,
            cycling = entry.optBoolean("b", false),
        )
    }

    /** One ride of a journey cut out of its whole trip: from the stop of boarding to the one of getting off. */
    private fun refreshedRide(entry: JSONObject, tripId: String, polylines: Boolean): Leg? {
        val trip = tripItinerary(tripId, detailed = polylines && shapeOf(tripId) == null)
        val leg = trip.legs.firstOrNull { !isStreetMode(it.text("mode")) } ?: return null
        val places = tripPlaces(trip)
        val board = findStop(places, entry.text("fi"), entry.text("fn"), entry.time("fs"), departure = true, after = -1)
            ?: return null
        val alight = findStop(places, entry.text("ti"), entry.text("tn"), entry.time("ts"), departure = false, after = board)
            ?: return null
        val boardJson = places[board]
        val alightJson = places[alight]
        val plannedDeparture = boardJson.time("scheduledDeparture") ?: entry.time("fs")
        val plannedArrival = alightJson.time("scheduledArrival") ?: entry.time("ts")
        val fromPlace = placeStop(boardJson)
        val toPlace = placeStop(alightJson)
        val path = if (polylines) {
            cutPath(shapeOf(tripId).orEmpty(), fromPlace.position, toPlace.position)
        } else {
            emptyList()
        }
        return Leg(
            line = lineName(leg),
            product = productOf(leg),
            walking = false,
            from = fromPlace.name,
            to = toPlace.name,
            departure = boardJson.time("departure") ?: plannedDeparture ?: return null,
            plannedDeparture = plannedDeparture,
            arrival = alightJson.time("arrival") ?: plannedArrival ?: return null,
            plannedArrival = plannedArrival,
            departurePlatform = boardJson.text("track"),
            arrivalPlatform = alightJson.text("track"),
            direction = leg.text("headsign"),
            cancelled = leg.optBoolean("cancelled", false) ||
                boardJson.optBoolean("cancelled", false) || alightJson.optBoolean("cancelled", false),
            realtime = trip.realtime,
            path = path,
            fromPlace = fromPlace,
            toPlace = toPlace,
            warnings = trip.legs.flatMap { warningsOf(it.optJSONArray("alerts")) }.distinct(),
            stopovers = stopoversOf(places.subList(board, alight + 1), trip.realtime),
            tripId = tripId,
            providerColor = colorOf(leg),
            providerTextColor = textColorOf(leg),
        )
    }

    /**
     * Where a ride boards or gets off within the stops of its trip: the stop with the same id or
     * name at the planned time, else the first one with the same id, else with the same name.
     */
    private fun findStop(
        places: List<JSONObject>,
        stopId: String?,
        name: String?,
        planned: OffsetDateTime?,
        departure: Boolean,
        after: Int,
    ): Int? {
        val range = (after + 1 until places.size)
        fun same(place: JSONObject): Boolean =
            (!stopId.isNullOrEmpty() && place.text("stopId") == stopId) || (name != null && place.text("name") == name)
        val key = if (departure) "scheduledDeparture" else "scheduledArrival"
        return range.firstOrNull { same(places[it]) && planned != null && places[it].time(key)?.isEqual(planned) == true }
            ?: range.firstOrNull { !stopId.isNullOrEmpty() && places[it].text("stopId") == stopId }
            ?: range.firstOrNull { name != null && places[it].text("name") == name }
    }

    /** A trip as MOTIS gives it: its legs (usually one), and whether live data is part of it. */
    private class TripAnswer(val legs: List<JSONObject>) {
        val realtime: Boolean get() = legs.any { it.optBoolean("realTime", false) }
    }

    private fun tripItinerary(tripId: String, detailed: Boolean): TripAnswer {
        val root = JSONObject(
            get(
                "/api/v6/trip?tripId=${encode(tripId)}&detailedLegs=$detailed&language=$language",
                CACHE_TRIP_MILLIS,
            ),
        )
        val list = root.optJSONArray("legs") ?: throw IOException("no trip")
        val legs = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
        if (detailed) {
            val shape = legs.flatMap { leg ->
                val geometry = leg.optJSONObject("legGeometry")
                val points = geometry?.text("points")
                if (geometry == null || points == null) emptyList() else decodeGeometry(points, geometry, leg.optJSONObject("from"))
            }
            if (shape.isNotEmpty()) synchronized(shapes) { shapes[tripId] = shape }
        }
        return TripAnswer(legs)
    }

    private fun shapeOf(tripId: String): List<Pair<Double, Double>>? = synchronized(shapes) { shapes[tripId] }

    /** All stops of a trip in order, each once: the ends and the stops between of every leg. */
    private fun tripPlaces(trip: TripAnswer): List<JSONObject> {
        val places = mutableListOf<JSONObject>()
        trip.legs.filter { !isStreetMode(it.text("mode")) }.forEach { leg ->
            legPlaces(leg).forEach { place ->
                val last = places.lastOrNull()
                if (last == null || last.text("stopId") != place.text("stopId") || place.text("stopId") == null) {
                    places.add(place)
                }
            }
        }
        return places
    }

    /** The stops of one ride from boarding to getting off. */
    private fun legPlaces(leg: JSONObject): List<JSONObject> {
        val places = mutableListOf<JSONObject>()
        leg.optJSONObject("from")?.let { places.add(it) }
        val between = leg.optJSONArray("intermediateStops")
        if (between != null) (0 until between.length()).forEach { index -> between.optJSONObject(index)?.let { places.add(it) } }
        leg.optJSONObject("to")?.let { places.add(it) }
        return places
    }

    private fun stopoversOf(places: List<JSONObject>, realtime: Boolean): List<Stopover> = places.mapNotNull { place ->
        val name = place.text("name") ?: return@mapNotNull null
        val arrival = place.time("arrival") ?: place.time("scheduledArrival")
        val departure = place.time("departure") ?: place.time("scheduledDeparture")
        val planned = place.time("scheduledDeparture") ?: place.time("scheduledArrival")
        val current = place.time("departure") ?: place.time("arrival")
        Stopover(
            name = name,
            arrival = arrival,
            departure = departure,
            place = placeStop(place),
            delaySeconds = if (realtime && planned != null && current != null) {
                Duration.between(planned, current).seconds.toInt()
            } else {
                null
            },
            plannedArrival = place.time("scheduledArrival"),
            plannedDeparture = place.time("scheduledDeparture"),
        )
    }

    /** A Place of MOTIS as a stop when it has a stop id, otherwise as an address at its position. */
    private fun placeStop(place: JSONObject): Stop {
        val latitude = place.number("lat")
        val longitude = place.number("lon")
        val stopId = place.text("stopId")
        return Stop(
            id = stopId ?: "address:$latitude,$longitude",
            name = place.text("name") ?: "",
            latitude = latitude,
            longitude = longitude,
            distanceMeters = null,
            products = modesOf(place.optJSONArray("modes")),
            kind = if (stopId != null) PlaceKind.Stop else PlaceKind.Address,
        )
    }

    /** An end of a journey: the place the user asked for when MOTIS names no stop there. */
    private fun endPlace(place: JSONObject, asked: Stop?): Stop {
        val found = placeStop(place)
        if (asked == null || found.kind == PlaceKind.Stop) return found
        val position = asked.position ?: found.position
        return Stop(
            id = position?.let { "address:${it.first},${it.second}" } ?: found.id,
            name = asked.name,
            latitude = position?.first,
            longitude = position?.second,
            distanceMeters = null,
            kind = PlaceKind.Address,
        )
    }

    private fun placeJson(place: Stop?, name: String): JSONObject {
        val json = JSONObject().put("n", place?.name ?: name).put("i", place?.id ?: "").put("k", place?.kind?.name ?: "")
        place?.latitude?.let { json.put("la", it) }
        place?.longitude?.let { json.put("lo", it) }
        return json
    }

    private fun placeFromJson(json: JSONObject?): Stop? {
        if (json == null) return null
        return Stop(
            id = json.optString("i", ""),
            name = json.optString("n", ""),
            latitude = json.number("la"),
            longitude = json.number("lo"),
            distanceMeters = null,
            kind = PlaceKind.entries.firstOrNull { it.name == json.optString("k") } ?: PlaceKind.Address,
        )
    }

    /** Matches of the place search as places of the app; [near] gives them a distance. */
    private fun matches(list: JSONArray, near: Pair<Double, Double>?): List<Stop> =
        (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val latitude = item.number("lat") ?: return@mapNotNull null
            val longitude = item.number("lon") ?: return@mapNotNull null
            val name = item.text("name") ?: return@mapNotNull null
            val kind = when (item.text("type")) {
                "STOP" -> PlaceKind.Stop
                "PLACE" -> PlaceKind.Poi
                else -> PlaceKind.Address
            }
            val area = defaultArea(item.optJSONArray("areas"))
            val label = when (kind) {
                PlaceKind.Stop -> name
                PlaceKind.Poi -> if (area == null || name.contains(area)) name else "$name, $area"
                PlaceKind.Address -> {
                    val street = listOfNotNull(item.text("street") ?: name, item.text("houseNumber")).joinToString(" ")
                    if (area == null || street.contains(area)) street else "$street, $area"
                }
            }
            Stop(
                id = if (kind == PlaceKind.Stop) item.text("id") ?: return@mapNotNull null else "address:$latitude,$longitude",
                name = label,
                latitude = latitude,
                longitude = longitude,
                distanceMeters = near?.let { distanceMeters(it, latitude to longitude).toInt() },
                products = modesOf(item.optJSONArray("modes")),
                kind = kind,
                postcode = item.text("zip"),
                areas = areaNames(item.optJSONArray("areas")),
                // A point of interest gets its street and town as a second line.
                detail = if (kind == PlaceKind.Poi) {
                    listOfNotNull(
                        listOfNotNull(item.text("street"), item.text("houseNumber")).joinToString(" ").takeIf { it.isNotEmpty() },
                        area,
                    ).joinToString(", ").takeIf { it.isNotEmpty() }
                } else {
                    null
                },
            )
        }

    /** The names of all areas a place lies in, as MOTIS lists them. */
    private fun areaNames(areas: JSONArray?): List<String> {
        if (areas == null) return emptyList()
        return (0 until areas.length()).mapNotNull { areas.optJSONObject(it)?.text("name") }
    }

    /** The town an address or place lies in, as MOTIS marks it for display. */
    private fun defaultArea(areas: JSONArray?): String? {
        if (areas == null) return null
        val list = (0 until areas.length()).mapNotNull { areas.optJSONObject(it) }
        return (list.firstOrNull { it.optBoolean("default", false) } ?: list.firstOrNull())?.text("name")
    }

    private fun modesOf(modes: JSONArray?): Set<String> {
        if (modes == null) return emptySet()
        return (0 until modes.length()).mapNotNull { modes.optString(it).takeIf { mode -> mode.isNotEmpty() } }
            .map { productKey(it) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** A place for a search: the stop id of a stop of Transitous, otherwise its position. */
    private fun placeParam(place: Stop, byPosition: Boolean = false): String {
        val position = place.position ?: positionOfId(place.id)
        if (byPosition && position != null) return "${position.first},${position.second}"
        if (place.kind == PlaceKind.Stop && !isPositionId(place.id)) return place.id
        return position?.let { "${it.first},${it.second}" } ?: place.id
    }

    /** A stop by its id; a place saved before Transitous (see AppPrefs) is found by its position. */
    private fun stopQuery(stopId: String): String {
        val position = if (isPositionId(stopId)) positionOfId(stopId) else null
        return if (position != null) {
            "center=${position.first},${position.second}&radius=$POSITION_STOP_RADIUS"
        } else {
            "stopId=${encode(stopId)}"
        }
    }

    /** Only the modes of the chosen products; nothing, meaning all, when every product is chosen. */
    private fun modeQuery(name: String, products: Set<String>): String {
        if (PRODUCT_KEYS.all { it in products }) return ""
        val modes = modesOf(products)
        return if (modes.isEmpty()) "" else "&$name=" + modes.joinToString(",")
    }

    /**
     * The transit modes of a journey search, those of [products] (the Deutschlandticket is
     * long-distance trains and coaches off). A feed that files an ICE under regional trains
     * still sends it then: it is dropped afterwards (see [productsFit]).
     */
    private fun journeyModeQuery(products: Set<String>): String = modeQuery("transitModes", products)

    private fun modesOf(products: Set<String>): List<String> = products.flatMap { key ->
        when (key) {
            "suburban" -> listOf("SUBURBAN")
            "subway" -> listOf("SUBWAY")
            "tram" -> listOf("TRAM")
            "bus" -> listOf("BUS")
            "coach" -> listOf("COACH")
            "ferry" -> listOf("FERRY")
            "regional" -> listOf("REGIONAL_RAIL", "REGIONAL_FAST_RAIL")
            "express" -> listOf("HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL")
            else -> emptyList()
        }
    }

    private fun get(path: String, keepMillis: Long): String {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val known = cache[path]
            if (known != null && now - known.first < keepMillis) return known.second
        }
        val started = System.nanoTime()
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            logHttpAnswer("transitous", path, code, started)
            if (code !in 200..299) throw HttpStatusException(code)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            synchronized(cache) { cache[path] = System.currentTimeMillis() to body }
            return body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_URL = "https://api.transitous.org"

        /** The list of the data Transitous is built from, which the app must link. */
        const val SOURCES_URL = "https://transitous.org/sources/"

        /** The name, version and contact Transitous asks every app to send. */
        fun userAgent(context: Context): String {
            // Every client is built with this, also in workers and services: the language is known from here on.
            AppLanguage.init(context)
            val version = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                null
            } ?: "0"
            return "NextStop/$version (+https://github.com/jnbp/next-stop)"
        }
    }
}

/**
 * What a further page of a search must repeat of the first request: whether it searched by
 * arrival, and its time. Kept in front of the cursor of MOTIS, in one string.
 */
private data class PageReplay(val arriveBy: Boolean, val time: String?) {
    fun write(cursor: String): String = (if (arriveBy) "a" else "d") + "|" + (time ?: "") + "|" + cursor

    companion object {
        fun read(reference: String): Pair<PageReplay, String> {
            val parts = reference.split("|", limit = 3)
            if (parts.size < 3) return PageReplay(false, null) to reference
            return PageReplay(parts[0] == "a", parts[1].ifEmpty { null }) to parts[2]
        }
    }
}

// The bike combo: the longest ride by bicycle to the first and from the last stop (MOTIS caps it
// at its own limit), and the speed of it in metres per second (about 15 km/h, a relaxed pace).
private const val COMBO_MAX_BIKE_SECONDS = 1800
private const val COMBO_CYCLING_SPEED = "4.2"

// Places saved from the former DB source carry their position in place of an id (see AppPrefs).
private const val POSITION_ID_PREFIX = "pos:"

/** A stop id that stands for the stops around a position (see stopQuery), for a place of another provider. */
internal fun positionStopId(latitude: Double, longitude: Double): String = "$POSITION_ID_PREFIX$latitude,$longitude"

private fun isPositionId(id: String): Boolean = id.startsWith(POSITION_ID_PREFIX) || id.startsWith("address:")

private fun positionOfId(id: String): Pair<Double, Double>? {
    val parts = id.substringAfter(':').split(",")
    if (parts.size != 2) return null
    val latitude = parts[0].toDoubleOrNull() ?: return null
    val longitude = parts[1].toDoubleOrNull() ?: return null
    return latitude to longitude
}

/**
 * The product of a MOTIS mode as the app names it; empty for modes the app has no product for.
 * "RAIL" is any train (GTFS route type 2): regional here, an ICE among them is told by its
 * name (see [productOf]).
 */
private fun productKey(mode: String): String = when (mode) {
    "HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL" -> "express"
    "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "RAIL" -> "regional"
    "SUBURBAN" -> "suburban"
    "SUBWAY", "METRO" -> "subway"
    "TRAM" -> "tram"
    "COACH" -> "coach"
    "BUS", "ODM" -> "bus"
    "FERRY" -> "ferry"
    else -> ""
}

/**
 * The product of a leg, a stop time or a trip of MOTIS: by its mode, and by the name of its line
 * where the feed puts an ICE under regional or plain rail, or a FlixBus under buses (see
 * [classifyProduct]).
 */
private fun productOf(item: JSONObject): String =
    classifyProduct(productKey(item.text("mode") ?: ""), lineName(item), item.text("agencyName"))

/**
 * Keeps the colour of the line of [item] (a leg, a stop time or a trip of MOTIS: routeColor and
 * routeTextColor of the feed) at [place], for the badges and lines (see [LineColors]).
 */
private fun noteColor(item: JSONObject, place: JSONObject?) {
    val latitude = place?.number("lat")
    val longitude = place?.number("lon")
    LineColors.noteProvider(
        lineName(item), productOf(item), item.text("routeColor"), item.text("routeTextColor"),
        if (latitude != null && longitude != null) latitude to longitude else null,
    )
}

/** The colour of the line of [item] as its feed has it (routeColor), as ARGB; null where it has none. */
private fun colorOf(item: JSONObject): Int? = LineColors.parseHex(item.text("routeColor"))

/** The colour of the text on the line of [item] (routeTextColor), where it comes with a colour of the line. */
private fun textColorOf(item: JSONObject): Int? =
    if (colorOf(item) == null) null else LineColors.parseHex(item.text("routeTextColor"))

/** Modes on the street: walking and the like, not a ride on a line. */
private fun isStreetMode(mode: String?): Boolean =
    mode in setOf("WALK", "BIKE", "RENTAL", "CAR", "CAR_PARKING", "CAR_DROPOFF", "FLEX", "HGV")

private val TRAIN_MODES = setOf("HIGHSPEED_RAIL", "LONG_DISTANCE", "NIGHT_RAIL", "REGIONAL_RAIL", "REGIONAL_FAST_RAIL", "RAIL")

/** The name of the line of a leg, a stop time or a trip (see [composeLineName]). */
private fun lineName(item: JSONObject): String? = rapidLineName(composeLineName(
    mode = item.text("mode"),
    route = item.text("routeShortName"),
    number = item.text("tripShortName"),
    display = item.text("displayName"),
    category = item.optJSONObject("category")?.text("shortName"),
    longName = item.text("routeLongName"),
    agency = item.text("agencyName"),
), productKey(item.text("mode") ?: ""))

/**
 * The name of a line. Trains and coaches get their kind in front of a bare number:
 * "ICE 29" for a route named "29", "ICE 1234" for a route named only "ICE" with trip
 * number 1234, "RE 1" for a regional route "1". The kind comes from the category, else
 * from the display name, the route names, and last from the mode and the operator.
 * Other lines keep the short name of the route or what MOTIS shows.
 */
internal fun composeLineName(
    mode: String?,
    route: String?,
    number: String?,
    display: String?,
    category: String?,
    longName: String?,
    agency: String?,
): String? {
    // A name that is only the word for the product ("Bus") is no name of a line: the number
    // comes from another field then.
    val routeName = route?.trim()?.takeIf { it.isNotEmpty() && !isProductWord(it) }
    val trip = number?.trim()?.takeIf { it.isNotEmpty() }
    val shown = display?.trim()?.takeIf { it.isNotEmpty() && !isProductWord(it) }
    val shortLong = longName?.trim()?.takeIf { it.length in 1..5 && it.any { char -> char.isDigit() } && !it.contains(' ') }
    val categoryName = category?.trim()?.takeIf { it.isNotEmpty() }
    // A FlixBus by the code of its line ("N1334", "260"): "FlixBus N1334" (see flixLineName).
    if (mode == "COACH" && agency.orEmpty().contains("Flix", ignoreCase = true)) {
        val code = routeName ?: shown ?: trip
        return if (code == null || code.contains("flix", ignoreCase = true) || code.startsWith("FLX", ignoreCase = true)) {
            code ?: "FlixBus"
        } else {
            "FlixBus $code"
        }
    }
    if (mode in TRAIN_MODES || mode == "COACH") {
        val kind = trainKindOf(categoryName) ?: categoryName?.takeIf { it.none { char -> char.isDigit() } }
            ?: kindOf(shown) ?: kindOf(routeName) ?: kindOf(longName)
            ?: trainKindOf(longName) ?: trainKindOf(agency) ?: defaultKind(mode, agency)
        val routeIsNumber = routeName != null && routeName.all { it.isDigit() }
        val tripIsNumber = trip != null && trip.all { it.isDigit() }
        val routeKind = routeName?.takeIf { it.none { char -> char.isDigit() } }?.let { trainKindOf(it) }
        when {
            // The route names only the kind: the trip number goes behind it.
            routeName != null && kind != null && routeName.equals(kind, ignoreCase = true) && tripIsNumber ->
                return "$routeName $trip"
            routeName != null && routeIsNumber && kind != null -> return "$kind $routeName"
            routeName == null && kind != null && shown != null && shown.all { it.isDigit() } -> return "$kind $shown"
            routeName == null && shown == null && kind != null && tripIsNumber -> return "$kind $trip"
            // The route names only the kind in words ("Railjet", "EuroCity"): the kind's code
            // and the number, from the trip or the shown name ("RJ 174"), never the word alone.
            // Trains only: a coach named "Intercity" is no IC.
            mode in TRAIN_MODES && routeKind != null -> {
                val code = routeKind
                if (tripIsNumber) return "$code $trip"
                if (shown != null && shown.all { it.isDigit() }) return "$code $shown"
                if (shown != null && shown.any { it.isDigit() } && kindOf(shown) != null) return shown
            }
        }
    }
    if (routeName == null && shown == null && shortLong == null) {
        // Only the product is named: a bus or tram without its number shows the trip number
        // only where nothing else is known.
        val word = route?.trim()?.takeIf { it.isNotEmpty() } ?: display?.trim()?.takeIf { it.isNotEmpty() }
        return trip?.takeIf { word == null || mode in TRAIN_MODES || it.length <= 4 } ?: word ?: categoryName
    }
    return routeName ?: shown ?: shortLong ?: trip ?: categoryName
}

/** The kind at the start of a line name such as "ICE 29" or "RE1": two to four capitals before a number or the end. */
private fun kindOf(name: String?): String? {
    val text = name?.trim() ?: return null
    val kind = text.takeWhile { it.isLetter() && it.isUpperCase() }
    if (kind.length !in 2..4) return null
    val rest = text.substring(kind.length)
    return if (rest.isEmpty() || rest.first() == ' ' || rest.first().isDigit()) kind else null
}

/** The kind of a long-distance train in Germany when MOTIS names none: by the operator and the mode. */
private fun defaultKind(mode: String?, agency: String?): String? {
    val operator = agency.orEmpty()
    if (operator.contains("Flix", ignoreCase = true)) return "FLX"
    val germanRail = agency == null || operator.contains("DB ") || operator.startsWith("DB") ||
        operator.contains("Deutsche Bahn", ignoreCase = true)
    if (!germanRail) return null
    return when (mode) {
        "HIGHSPEED_RAIL" -> "ICE"
        "LONG_DISTANCE" -> "IC"
        else -> null
    }
}

private fun warningsOf(alerts: JSONArray?): List<Warning> {
    if (alerts == null) return emptyList()
    return (0 until alerts.length()).mapNotNull { index ->
        val alert = alerts.optJSONObject(index) ?: return@mapNotNull null
        val summary = withoutPlaceholders(alert.text("headerText")?.trim().orEmpty())
        val text = withoutPlaceholders(alert.text("descriptionText")?.trim().orEmpty())
        // When it holds: the first period of its impact (GTFS-RT active_period), where it has one.
        val period = alert.optJSONArray("impactPeriod")?.optJSONObject(0)
            ?: alert.optJSONArray("communicationPeriod")?.optJSONObject(0)
        val url = alert.text("url")?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (summary.isEmpty() && text.isEmpty()) {
            null
        } else {
            Warning(
                summary = summary,
                text = if (text == summary) "" else text,
                validFrom = period?.time("start"),
                validUntil = period?.time("end"),
                links = if (url == null) emptyList() else listOf(NoticeLink(NoticeLinkKind.More, url)),
            )
        }
    }.distinct()
}

private fun straightWay(from: Stop?, to: Stop?): List<Pair<Double, Double>> {
    val start = from?.position ?: return emptyList()
    val end = to?.position ?: return emptyList()
    return if (start == end) emptyList() else listOf(start, end)
}

/** The part of a trip's shape between two stops: from the point closest to the first to the one closest to the second. */
private fun cutPath(
    shape: List<Pair<Double, Double>>,
    from: Pair<Double, Double>?,
    to: Pair<Double, Double>?,
): List<Pair<Double, Double>> {
    if (shape.isEmpty() || from == null || to == null) return shape
    val start = shape.indices.minByOrNull { distanceMeters(shape[it], from) } ?: 0
    val end = (start until shape.size).minByOrNull { distanceMeters(shape[it], to) } ?: shape.lastIndex
    return if (end <= start) listOf(from, to) else shape.subList(start, end + 1)
}

/** The point at [fraction] (0 to 1) of the way along [shape], by distance. */
private fun pointAlong(shape: List<Pair<Double, Double>>, fraction: Double): Pair<Double, Double> {
    val lengths = shape.zipWithNext { a, b -> distanceMeters(a, b) }
    val goal = lengths.sum() * fraction.coerceIn(0.0, 1.0)
    var walked = 0.0
    for (index in lengths.indices) {
        val length = lengths[index]
        if (walked + length >= goal && length > 0) {
            val part = (goal - walked) / length
            val (lat1, lon1) = shape[index]
            val (lat2, lon2) = shape[index + 1]
            return (lat1 + (lat2 - lat1) * part) to (lon1 + (lon2 - lon1) * part)
        }
        walked += length
    }
    return shape.last()
}

/**
 * The way of a leg from its encoded [points]. MOTIS names the precision of the encoding in
 * [geometry] (7 decimal places for the way of a leg by default, 5 or 6 where asked for); a
 * way decoded with a wrong precision lands far off, scaled by a power of ten, so without a
 * named precision, or where the named one puts the way far from where the leg starts
 * ([from], a place with "lat" and "lon"), the precision that starts the way there is taken.
 */
internal fun decodeGeometry(points: String, geometry: JSONObject, from: JSONObject?): List<Pair<Double, Double>> {
    val named = if (geometry.has("precision")) geometry.optInt("precision", 7) else null
    val startLatitude = from?.optDouble("lat", Double.NaN) ?: Double.NaN
    val startLongitude = from?.optDouble("lon", Double.NaN) ?: Double.NaN
    val known = !startLatitude.isNaN() && !startLongitude.isNaN()
    fun near(shape: List<Pair<Double, Double>>): Boolean {
        val first = shape.firstOrNull() ?: return false
        if (!known) return first.first in -90.0..90.0 && first.second in -180.0..180.0
        return abs(first.first - startLatitude) < 0.05 && abs(first.second - startLongitude) < 0.08
    }
    val tried = listOfNotNull(named, 7, 6, 5).distinct()
    for (precision in tried) {
        val shape = decodePolyline(points, precision)
        if (near(shape)) return shape
    }
    return decodePolyline(points, named ?: 7)
}

/** A Google encoded polyline with [precision] decimal places as latitude and longitude. */
internal fun decodePolyline(encoded: String, precision: Int): List<Pair<Double, Double>> {
    val factor = 10.0.pow(precision)
    val points = mutableListOf<Pair<Double, Double>>()
    var index = 0
    var latitude = 0L
    var longitude = 0L
    while (index < encoded.length) {
        var result = 0L
        var shift = 0
        var byte: Int
        do {
            if (index >= encoded.length) return points
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f).toLong() shl shift)
            shift += 5
        } while (byte >= 0x20)
        latitude += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        result = 0L
        shift = 0
        do {
            if (index >= encoded.length) return points
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f).toLong() shl shift)
            shift += 5
        } while (byte >= 0x20)
        longitude += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        points.add(latitude / factor to longitude / factor)
    }
    return points
}

/** The counterpart of [decodePolyline] with precision 5, to keep the way of a walk in a journey handle. */
private fun encodePolyline(points: List<Pair<Double, Double>>): String {
    val out = StringBuilder()
    var lastLatitude = 0L
    var lastLongitude = 0L
    fun put(value: Long) {
        var rest = if (value < 0) (value shl 1).inv() else value shl 1
        while (rest >= 0x20) {
            out.append(((0x20 or (rest and 0x1f).toInt()) + 63).toChar())
            rest = rest shr 5
        }
        out.append((rest.toInt() + 63).toChar())
    }
    points.forEach { (lat, lon) ->
        val latitude = Math.round(lat * 1e5)
        val longitude = Math.round(lon * 1e5)
        put(latitude - lastLatitude)
        put(longitude - lastLongitude)
        lastLatitude = latitude
        lastLongitude = longitude
    }
    return out.toString()
}

private fun distanceMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
    val lat1 = Math.toRadians(from.first)
    val lat2 = Math.toRadians(to.first)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.second - from.second)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** A time for a request: ISO 8601 with its offset, to the second. */
private fun isoTime(time: OffsetDateTime): String = time.withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

// org.json gives the text "null" for JSON null and fails on missing keys; these return a real null.
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.number(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

/** MOTIS sends times in UTC; the app shows them in the time zone of the device. */
private fun JSONObject.time(key: String): OffsetDateTime? = text(key)?.let {
    try {
        OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()
    } catch (e: Exception) {
        null
    }
}

// A pinned place is named after what lies within this distance of it.
private const val NEAREST_PLACE_METERS = 300

// A place saved by position finds its departures among the stops this close to it.
private const val POSITION_STOP_RADIUS = 150

// Vehicles only for a box of city size; MOTIS sends every trip in the box.
private const val MAX_VEHICLE_BOX_DEGREES = 0.6

private const val CACHE_ENTRIES = 32
private const val SHAPE_ENTRIES = 48
private const val CACHE_PLAN_MILLIS = 60_000L
private const val CACHE_TRIP_MILLIS = 20_000L
private const val CACHE_STOPTIMES_MILLIS = 20_000L
private const val CACHE_VEHICLES_MILLIS = 15_000L
private const val CACHE_PLACES_MILLIS = 5 * 60_000L
