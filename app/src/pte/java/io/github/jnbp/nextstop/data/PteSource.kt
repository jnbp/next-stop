package io.github.jnbp.nextstop.data

import de.schildbach.pte.BvgProvider
import de.schildbach.pte.NetworkProvider
import de.schildbach.pte.dto.Location
import de.schildbach.pte.dto.LocationType
import de.schildbach.pte.dto.Point
import de.schildbach.pte.dto.Product
import de.schildbach.pte.dto.QueryDeparturesResult
import de.schildbach.pte.dto.QueryTripsContext
import de.schildbach.pte.dto.QueryTripsResult
import de.schildbach.pte.dto.Trip
import de.schildbach.pte.dto.TripOptions
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Berlin and Brandenburg through public-transport-enabler (de.schildbach.pte, the library of
 * Öffi, GPL-3.0), with its BvgProvider: the HAFAS server the BVG apps use. That server wants a
 * client authorization ([authorization], JSON such as {"type":"AID","aid":"…"}), which the
 * user enters in the settings; it is never part of the repository.
 *
 * What the library has: places nearby and by name, departures, journeys and further pages.
 * What it lacks, and how this source fills the gap:
 * - Refresh of a journey: the search is run again at its planned start and the journey with
 *   the same legs is picked (the refresh token holds the search, see [refreshToken]).
 * - Trip ids, trip courses, vehicles and arrivals: not supported; departures come without a
 *   trip, so their course cannot be opened while this source answers.
 * - Further pages: the library keeps them as objects in memory; their references are only
 *   valid while the app runs.
 *
 * Only compiled with `nextstop.pte=true` (gradle.properties); otherwise src/nopte holds a
 * stand-in of the same name, so the app builds without the library from JitPack.
 */
class PteSource(private val authorization: String) : TransitSource {

    companion object {
        /** True: this build has public-transport-enabler (see src/nopte for builds without it). */
        const val INCLUDED = true
    }

    private val provider: NetworkProvider by lazy { BvgProvider(authorization) }

    // Contexts of further pages by their reference (see [contextRef]); a few hundred at most.
    private val contexts = object : LinkedHashMap<String, QueryTripsContext>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, QueryTripsContext>?): Boolean = size > 200
    }
    private val nextContext = AtomicLong()

    private fun ensureSetUp() {
        if (authorization.isBlank()) throw NotConfiguredException(Backend.Pte)
    }

    override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> =
        withContext(Dispatchers.IO) {
            ensureSetUp()
            val result = provider.queryNearbyLocations(
                EnumSet.of(LocationType.STATION), Location.coord(Point.fromDouble(latitude, longitude)), 1000, results,
            )
            if (result.status != de.schildbach.pte.dto.NearbyLocationsResult.Status.OK) throw IOException("pte nearby ${result.status}")
            result.locations.mapNotNull { stopOf(it) }
        }

    override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? =
        withContext(Dispatchers.IO) {
            ensureSetUp()
            val result = provider.queryNearbyLocations(
                EnumSet.of(LocationType.STATION, LocationType.POI), Location.coord(Point.fromDouble(latitude, longitude)), 300, 1,
            )
            if (result.status != de.schildbach.pte.dto.NearbyLocationsResult.Status.OK) throw IOException("pte nearby ${result.status}")
            result.locations.firstNotNullOfOrNull { stopOf(it) }
        }

    override suspend fun searchStops(query: String, results: Int): List<Stop> =
        withContext(Dispatchers.IO) {
            ensureSetUp()
            val result = provider.suggestLocations(
                query, EnumSet.of(LocationType.STATION, LocationType.POI, LocationType.ADDRESS), results,
            )
            if (result.status != de.schildbach.pte.dto.SuggestLocationsResult.Status.OK) throw IOException("pte search ${result.status}")
            result.locations.mapNotNull { stopOf(it) }
        }

    override suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int,
        products: Set<String>?,
    ): List<Vehicle> = throw NotSupportedException("pte vehicles")

    override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> = throw NotSupportedException("pte trips")

    override suspend fun tripStopovers(tripId: String): List<Stopover> = throw NotSupportedException("pte trips")

    override suspend fun tripCourse(tripId: String): TripCourse = throw NotSupportedException("pte trips")

    override suspend fun departures(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> = withContext(Dispatchers.IO) {
        ensureSetUp()
        val result = provider.queryDepartures(stopId, time?.let { Date.from(it.toInstant()) }, results * 2, false)
        when (result.status) {
            QueryDeparturesResult.Status.OK -> Unit
            QueryDeparturesResult.Status.INVALID_STATION -> throw HttpStatusException(404)
            else -> throw IOException("pte departures ${result.status}")
        }
        result.stationDepartures.flatMap { station ->
            // Where the departures leave: the colour of a line depends on the place.
            val at = station.location.coord?.let { it.latAsDouble to it.lonAsDouble }
            station.departures.map { departure ->
                val planned = departure.plannedTime?.let { offsetOf(it) }
                val live = departure.predictedTime?.let { offsetOf(it) }
                val product = classifyProduct(productOf(departure.line.product), departure.line.label, departure.line.network)
                Departure(
                    line = rapidLineName(withTrainKind(departure.line.label, product, null, departure.line.name), product) ?: departure.line.label ?: "?",
                    product = product,
                    direction = departure.destination?.let { nameOf(it) } ?: "",
                    time = live ?: planned,
                    plannedTime = planned,
                    delaySeconds = if (live != null && planned != null) (live.toEpochSecond() - planned.toEpochSecond()).toInt() else null,
                    platform = departure.position?.name,
                    cancelled = false,
                    destination = departure.destination?.let { stopOf(it) },
                    at = at,
                )
            }
        }
            .filter { searchProductOf(it.product) in products || it.product.isEmpty() }
            .sortedBy { it.time ?: it.plannedTime }
            .take(results)
    }

    override suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival> = throw NotSupportedException("pte arrivals")

    override suspend fun journeys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int,
        departure: OffsetDateTime?,
        arrival: OffsetDateTime?,
    ): JourneyPage = withContext(Dispatchers.IO) {
        ensureSetUp()
        val at = arrival ?: departure ?: OffsetDateTime.now()
        // The bike combo leaves out coaches and (but where a bus may carry the bicycle) buses.
        val products = if (options.bikeCombo) {
            BikeRules.comboProducts(options.products, BikeRules.busesWorthAsking(from.position, at))
        } else {
            options.products
        }
        val search = Search(from, to, options.via?.place, products, options.walkingSpeed, options.accessibility, options.localOnly, options.carriesBike)
        page(search, query(search, Date.from(at.toInstant()), arrival == null)).fitting(options)
    }

    override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? =
        withContext(Dispatchers.IO) {
            ensureSetUp()
            val token = JSONObject(refreshToken)
            val search = Search.fromJson(token.getJSONObject("search"))
            val id = token.getString("id")
            // A minute early, so the journey is surely among the first results.
            val start = Date(token.getLong("start") - 60_000L)
            val result = query(search, start, true)
            val trip = result.trips.firstOrNull { it.id == id } ?: return@withContext null
            journeyOf(search, trip)
        }

    override suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int,
    ): JourneyPage = more(from, to, options, earlierRef, later = false)

    override suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int,
    ): JourneyPage = more(from, to, options, laterRef, later = true)

    private suspend fun more(from: Stop, to: Stop, options: SearchOptions, ref: String, later: Boolean): JourneyPage =
        withContext(Dispatchers.IO) {
            ensureSetUp()
            val context = synchronized(contexts) { contexts[ref] }
                // Pages from before a restart are gone: an empty page, as when there are no more.
                ?: return@withContext JourneyPage(emptyList(), null, null)
            val search = Search(from, to, options.via?.place, options.products, options.walkingSpeed, options.accessibility, options.localOnly, options.carriesBike)
            val result = provider.queryMoreTrips(context, later)
            page(search, checked(result)).fitting(options)
        }

    private fun query(search: Search, date: Date, departing: Boolean): QueryTripsResult {
        // Deutschlandticket: without long-distance trains; bicycle: only trips that take one along.
        val products = productsOf(search.products).let { if (search.localOnly) it - Product.HIGH_SPEED_TRAIN else it }
        val tripOptions = TripOptions(
            products,
            null,
            when (search.walkingSpeed) {
                WalkingSpeed.Slow -> NetworkProvider.WalkSpeed.SLOW
                WalkingSpeed.Normal -> NetworkProvider.WalkSpeed.NORMAL
                WalkingSpeed.Fast -> NetworkProvider.WalkSpeed.FAST
            },
            when (search.accessibility) {
                Accessibility.None -> NetworkProvider.Accessibility.NEUTRAL
                Accessibility.Partial -> NetworkProvider.Accessibility.LIMITED
                Accessibility.Complete -> NetworkProvider.Accessibility.BARRIER_FREE
            },
            if (search.bike) EnumSet.of(NetworkProvider.TripFlag.BIKE) else null,
        )
        return checked(
            provider.queryTrips(
                locationOf(search.from), search.via?.let { locationOf(it) }, locationOf(search.to), date, departing, tripOptions,
            ),
        )
    }

    private fun checked(result: QueryTripsResult): QueryTripsResult = when (result.status) {
        QueryTripsResult.Status.OK -> result
        QueryTripsResult.Status.NO_TRIPS -> throw HttpStatusException(404)
        QueryTripsResult.Status.SERVICE_DOWN -> throw IOException("pte service down")
        // Places it does not know, too close, a bad date: the next source may know better.
        else -> throw IOException("pte trips ${result.status}")
    }

    private fun page(search: Search, result: QueryTripsResult): JourneyPage {
        val context = result.context
        val ref = context?.let { contextRef(it) }
        return JourneyPage(
            journeys = result.trips.mapNotNull { journeyOf(search, it) },
            earlierRef = ref?.takeIf { context.canQueryEarlier() },
            laterRef = ref?.takeIf { context.canQueryLater() },
        )
    }

    private fun contextRef(context: QueryTripsContext): String {
        val ref = "ctx" + nextContext.incrementAndGet()
        synchronized(contexts) { contexts[ref] = context }
        return ref
    }

    private fun journeyOf(search: Search, trip: Trip): Journey? {
        val legs = trip.legs.map { legOf(it) ?: return null }
        if (legs.isEmpty()) return null
        val start = trip.legs.firstNotNullOfOrNull { (it as? Trip.Public)?.departureStop?.plannedDepartureTime }
            ?: trip.firstDepartureTime
        val token = JSONObject()
            .put("search", search.toJson())
            .put("id", trip.id)
            .put("start", start.time)
        return Journey(legs, refreshToken = token.toString())
    }

    private fun legOf(leg: Trip.Leg): Leg? {
        val path = leg.path.orEmpty().map { it.latAsDouble to it.lonAsDouble }
        return when (leg) {
            is Trip.Public -> {
                val product = classifyProduct(productOf(leg.line.product), leg.line.label, leg.line.network)
                val departure = leg.departureStop
                val arrival = leg.arrivalStop
                Leg(
                    line = rapidLineName(withTrainKind(leg.line.label, product, null, leg.line.name), product) ?: leg.line.label,
                    product = product,
                    walking = false,
                    from = nameOf(departure.location),
                    to = nameOf(arrival.location),
                    departure = offsetOf(departure.getDepartureTime(false) ?: return null),
                    plannedDeparture = departure.plannedDepartureTime?.let { offsetOf(it) },
                    arrival = offsetOf(arrival.getArrivalTime(false) ?: return null),
                    plannedArrival = arrival.plannedArrivalTime?.let { offsetOf(it) },
                    departurePlatform = departure.departurePosition?.name,
                    arrivalPlatform = arrival.arrivalPosition?.name,
                    direction = leg.destination?.let { nameOf(it) },
                    cancelled = departure.departureCancelled || arrival.arrivalCancelled,
                    realtime = departure.predictedDepartureTime != null || arrival.predictedArrivalTime != null,
                    path = path,
                    fromPlace = stopOf(departure.location),
                    toPlace = stopOf(arrival.location),
                    stopovers = leg.intermediateStops.orEmpty().map { stop ->
                        Stopover(
                            name = nameOf(stop.location),
                            arrival = stop.getArrivalTime(false)?.let { offsetOf(it) },
                            departure = stop.getDepartureTime(false)?.let { offsetOf(it) },
                            place = stopOf(stop.location),
                            delaySeconds = stop.departureDelay?.let { (it / 1000).toInt() },
                            plannedArrival = stop.plannedArrivalTime?.let { offsetOf(it) },
                            plannedDeparture = stop.plannedDepartureTime?.let { offsetOf(it) },
                        )
                    },
                )
            }
            is Trip.Individual -> {
                val fromPlace = stopOf(leg.departure)
                val toPlace = stopOf(leg.arrival)
                Leg(
                    line = null,
                    product = "",
                    walking = true,
                    from = nameOf(leg.departure),
                    to = nameOf(leg.arrival),
                    departure = offsetOf(leg.departureTime),
                    plannedDeparture = offsetOf(leg.departureTime),
                    arrival = offsetOf(leg.arrivalTime),
                    plannedArrival = offsetOf(leg.arrivalTime),
                    departurePlatform = null,
                    arrivalPlatform = null,
                    direction = null,
                    cancelled = false,
                    path = path.ifEmpty { listOfNotNull(fromPlace?.position, toPlace?.position).distinct() },
                    fromPlace = fromPlace,
                    toPlace = toPlace,
                    walkMeters = leg.distance.takeIf { it > 0 },
                )
            }
            else -> null
        }
    }

    /** The search a journey came from, kept in its refresh token. */
    private data class Search(
        val from: Stop,
        val to: Stop,
        val via: Stop?,
        val products: Set<String>,
        val walkingSpeed: WalkingSpeed,
        val accessibility: Accessibility,
        val localOnly: Boolean = false,
        val bike: Boolean = false,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("from", placeJson(from))
            .put("to", placeJson(to))
            .put("via", via?.let { placeJson(it) })
            .put("products", products.joinToString(","))
            .put("walk", walkingSpeed.name)
            .put("access", accessibility.name)
            .put("local", localOnly)
            .put("bike", bike)

        companion object {
            fun fromJson(json: JSONObject) = Search(
                from = placeOf(json.getJSONObject("from")),
                to = placeOf(json.getJSONObject("to")),
                via = json.optJSONObject("via")?.let { placeOf(it) },
                products = json.optString("products").split(",").filter { it.isNotEmpty() }.toSet(),
                walkingSpeed = WalkingSpeed.entries.firstOrNull { it.name == json.optString("walk") } ?: WalkingSpeed.Normal,
                accessibility = Accessibility.entries.firstOrNull { it.name == json.optString("access") } ?: Accessibility.None,
                localOnly = json.optBoolean("local"),
                bike = json.optBoolean("bike"),
            )

            private fun placeJson(place: Stop): JSONObject = JSONObject()
                .put("id", place.id)
                .put("name", place.name)
                .put("kind", place.kind.name)
                .put("lat", place.latitude)
                .put("lon", place.longitude)

            private fun placeOf(json: JSONObject) = Stop(
                id = json.getString("id"),
                name = json.optString("name"),
                latitude = json.optDouble("lat").takeIf { !it.isNaN() },
                longitude = json.optDouble("lon").takeIf { !it.isNaN() },
                distanceMeters = null,
                kind = PlaceKind.entries.firstOrNull { it.name == json.optString("kind") } ?: PlaceKind.Address,
            )
        }
    }
}

private val BERLIN_ZONE: ZoneId = ZoneId.of("Europe/Berlin")

private fun offsetOf(date: Date): OffsetDateTime = date.toInstant().atZone(BERLIN_ZONE).toOffsetDateTime()

/** A stop by its VBB number; everything else by its position. */
private fun locationOf(place: Stop): Location {
    if (place.kind == PlaceKind.Stop) return Location(LocationType.STATION, place.id)
    val latitude = place.latitude ?: throw IOException("place without position")
    val longitude = place.longitude ?: throw IOException("place without position")
    return Location(LocationType.ADDRESS, null, Point.fromDouble(latitude, longitude), null, place.name)
}

private fun nameOf(location: Location): String = location.name ?: location.place ?: ""

private fun stopOf(location: Location): Stop? {
    val coord: Point? = location.coord
    val name = nameOf(location).ifEmpty { return null }
    if (location.type == LocationType.STATION) {
        return Stop(
            id = location.id ?: return null,
            name = name,
            latitude = coord?.latAsDouble,
            longitude = coord?.lonAsDouble,
            distanceMeters = null,
            products = location.products.orEmpty().map { productOf(it) }.filter { it.isNotEmpty() }.toSet(),
        )
    }
    // Points of interest and addresses are only of use with their position.
    if (coord == null) return null
    val latitude = coord.latAsDouble
    val longitude = coord.lonAsDouble
    val poi = location.type == LocationType.POI
    return Stop(
        id = if (poi) location.id ?: "poi:$latitude,$longitude" else "address:$latitude,$longitude",
        name = name,
        latitude = latitude,
        longitude = longitude,
        distanceMeters = null,
        kind = if (poi) PlaceKind.Poi else PlaceKind.Address,
    )
}

/** The app's product of a product of the library ("express" for long-distance trains). */
private fun productOf(product: Product?): String = when (product) {
    Product.HIGH_SPEED_TRAIN -> "express"
    Product.REGIONAL_TRAIN -> "regional"
    Product.SUBURBAN_TRAIN -> "suburban"
    Product.SUBWAY -> "subway"
    Product.TRAM -> "tram"
    Product.BUS, Product.ON_DEMAND -> "bus"
    Product.FERRY -> "ferry"
    Product.CABLECAR, null -> ""
}

/** The library's products of the app's [products]; the library has no coaches: they come as buses. */
private fun productsOf(products: Set<String>): Set<Product> {
    val set = EnumSet.noneOf(Product::class.java)
    if ("suburban" in products) set.add(Product.SUBURBAN_TRAIN)
    if ("subway" in products) set.add(Product.SUBWAY)
    if ("tram" in products) set.add(Product.TRAM)
    if ("bus" in products || "coach" in products) set.addAll(listOf(Product.BUS, Product.ON_DEMAND))
    if ("ferry" in products) set.add(Product.FERRY)
    if ("regional" in products) set.add(Product.REGIONAL_TRAIN)
    // Some providers of the library put IC/ICE under regional trains: those are dropped afterwards
    // when off (see classifyProduct and productsFit).
    if ("express" in products) set.add(Product.HIGH_SPEED_TRAIN)
    return set
}
