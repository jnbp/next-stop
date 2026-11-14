package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/**
 * The backends of one id family (see [Backend.family]) as one [TransitSource]: every request
 * goes to the backends in the order the user chose, and the next one stands in when one fails
 * (see [SourceChain]).
 *
 * Stops are shared by the family and pass between backends as they are. Trip ids, refresh
 * tokens, page references and points of interest are not: they get the [Backend.tag] of the
 * backend that made them, and requests about them go back to that backend only (no fallback;
 * the screens keep the last known state when such a request fails). Vehicles on the map come
 * from the first backend that has a radar at all (only the BVG REST API has one).
 *
 * [backends] are the backends in use and set up, best first, with their clients; read anew
 * for every request, so a changed setting counts at once. [standIn] tells whether another
 * family could answer when all of these fail (Transitous, say): then for stops nearby and
 * journeys, which another family can answer by position, the last backend is cut off after
 * its few seconds too, so the stand-in is not kept waiting.
 */
class FamilySource(
    private val family: DataSource,
    private val backends: () -> List<Pair<Backend, TransitSource>>,
    private val chain: SourceChain = SourceChain(),
    private val standIn: () -> Boolean = { false },
) : TransitSource {

    private fun list(): List<Pair<Backend, TransitSource>> = backends()

    private fun clientOf(backend: Backend): TransitSource =
        list().firstOrNull { it.first == backend }?.second
            ?: throw SourceUnreachableException(family, NotConfiguredException(backend), emptyList())

    private suspend fun <T> chained(
        timeoutMillis: Long = SourceChain.DEFAULT_TIMEOUT_MILLIS,
        record: Boolean = true,
        cut: Boolean = false,
        block: suspend (Backend, TransitSource) -> T,
    ): T {
        val clients = list()
        return chain.first(family, clients.map { it.first }, timeoutMillis, record, cutLast = cut && standIn()) { backend ->
            block(backend, clients.first { it.first == backend }.second)
        }
    }

    // A request about something one backend made goes to that backend alone.
    private suspend fun <T> at(value: String, block: suspend (TransitSource, String) -> T): T {
        val backend = backendOf(family, value)
        val client = clientOf(backend)
        return chain.first(family, listOf(backend), record = false) { block(client, value.removePrefix(backend.tag)) }
    }

    override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> =
        chained(cut = true) { backend, client -> client.nearbyStops(latitude, longitude, results).map { tagged(backend, it) } }

    override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? =
        chained(record = false) { backend, client -> client.nearestPlace(latitude, longitude)?.let { tagged(backend, it) } }

    override suspend fun searchStops(query: String, results: Int): List<Stop> =
        chained { backend, client -> client.searchStops(query, results).map { tagged(backend, it) } }

    override suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int,
        products: Set<String>?,
    ): List<Vehicle> = try {
        chained(record = false) { backend, client ->
            client.vehicles(north, west, south, east, results, products).map { it.copy(tripId = backend.tag + it.tripId) }
        }
    } catch (e: SourceUnreachableException) {
        // No backend in use has a radar: the map simply shows no vehicles.
        if (e.tried.isEmpty()) emptyList() else throw e
    }

    override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> =
        at(tripId) { client, id -> client.tripPath(id) }

    override suspend fun tripStopovers(tripId: String): List<Stopover> {
        val backend = backendOf(family, tripId)
        return at(tripId) { client, id -> client.tripStopovers(id).map { tagged(backend, it) } }
    }

    override suspend fun tripCourse(tripId: String): TripCourse {
        val backend = backendOf(family, tripId)
        return at(tripId) { client, id ->
            val course = client.tripCourse(id)
            course.copy(tripId = backend.tag + course.tripId, stops = course.stops.map { tagged(backend, it) })
        }
    }

    override suspend fun stopLines(stopId: String): List<StopLine> =
        chained(record = false) { _, client -> client.stopLines(stopId) }

    override suspend fun lineDepartures(stopId: String, line: String, product: String): List<Departure> =
        chained(record = false) { backend, client -> client.lineDepartures(stopId, line, product).map { tagged(backend, it) } }

    override suspend fun departures(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> =
        chained { backend, client -> client.departures(stopId, results, products, time).map { tagged(backend, it) } }

    override suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival> = chained(record = false) { backend, client ->
        client.arrivals(stopId, products, time, durationMinutes).map { arrival ->
            arrival.copy(tripId = arrival.tripId?.let { backend.tag + it })
        }
    }

    override suspend fun journeys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int,
        departure: OffsetDateTime?,
        arrival: OffsetDateTime?,
    ): JourneyPage = chained(SourceChain.JOURNEY_TIMEOUT_MILLIS, cut = true) { backend, client ->
        tagged(
            backend,
            client.journeys(placeFor(backend, from), placeFor(backend, to), optionsFor(backend, options), results, departure, arrival),
        )
    }

    override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? {
        val backend = backendOf(family, refreshToken)
        return at(refreshToken) { client, token -> client.refreshJourney(token, stopovers, polylines)?.let { tagged(backend, it) } }
    }

    override suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int,
    ): JourneyPage {
        val backend = backendOf(family, earlierRef)
        return at(earlierRef) { client, ref ->
            tagged(backend, client.earlierJourneys(placeFor(backend, from), placeFor(backend, to), optionsFor(backend, options), ref, results))
        }
    }

    override suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int,
    ): JourneyPage {
        val backend = backendOf(family, laterRef)
        return at(laterRef) { client, ref ->
            tagged(backend, client.laterJourneys(placeFor(backend, from), placeFor(backend, to), optionsFor(backend, options), ref, results))
        }
    }

    /** A place for [backend]: stops as they are; a point of interest of another backend by its position. */
    private fun placeFor(backend: Backend, place: Stop): Stop {
        if (place.kind != PlaceKind.Poi) return place
        val origin = backendOf(family, place.id)
        if (origin == backend) return place.copy(id = place.id.removePrefix(backend.tag))
        return if (place.position != null) place.copy(kind = PlaceKind.Address) else place
    }

    private fun optionsFor(backend: Backend, options: SearchOptions): SearchOptions {
        val via = options.via ?: return options
        return options.copy(via = via.copy(place = placeFor(backend, via.place)))
    }
}

// What came from a backend, with its tag on every trip id, token, reference and point of interest.

internal fun tagged(backend: Backend, stop: Stop): Stop =
    if (stop.kind == PlaceKind.Poi && backend.tag.isNotEmpty()) stop.copy(id = backend.tag + stop.id) else stop

internal fun tagged(backend: Backend, stopover: Stopover): Stopover =
    stopover.copy(place = stopover.place?.let { tagged(backend, it) })

internal fun tagged(backend: Backend, departure: Departure): Departure = departure.copy(
    tripId = departure.tripId?.let { backend.tag + it },
    destination = departure.destination?.let { tagged(backend, it) },
)

internal fun tagged(backend: Backend, leg: Leg): Leg = leg.copy(
    fromPlace = leg.fromPlace?.let { tagged(backend, it) },
    toPlace = leg.toPlace?.let { tagged(backend, it) },
    stopovers = leg.stopovers.map { tagged(backend, it) },
    tripId = leg.tripId?.let { backend.tag + it },
)

internal fun tagged(backend: Backend, journey: Journey): Journey = journey.copy(
    legs = journey.legs.map { tagged(backend, it) },
    refreshToken = journey.refreshToken?.let { backend.tag + it },
)

internal fun tagged(backend: Backend, page: JourneyPage): JourneyPage = JourneyPage(
    journeys = page.journeys.map { tagged(backend, it) },
    earlierRef = page.earlierRef?.let { backend.tag + it },
    laterRef = page.laterRef?.let { backend.tag + it },
)
