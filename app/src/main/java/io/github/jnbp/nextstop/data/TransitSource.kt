package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/**
 * Where the app gets its transit data from. The app only talks to this interface,
 * so the provider behind it can be replaced without touching the screens.
 */
interface TransitSource {
    suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int = 4): List<Stop>

    /** The stop or point of interest closest to a position, or null when none is within a short walk. */
    suspend fun nearestPlace(latitude: Double, longitude: Double): Stop?

    /** Stops, points of interest and addresses that match the text. */
    suspend fun searchStops(query: String, results: Int = 8): List<Stop>

    /**
     * Vehicles inside the given box of coordinates, with their movement over the next half minute.
     * With [products] only vehicles of those products (see [PRODUCT_KEYS]; "regional" includes
     * long-distance trains), so a large box is not filled up with buses before any train.
     */
    suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int = 40,
        products: Set<String>? = null,
    ): List<Vehicle>

    /** The whole way of one trip on the map, as latitude and longitude. */
    suspend fun tripPath(tripId: String): List<Pair<Double, Double>>

    /** All stops of one trip from its first to its last, with live times where the provider has them. */
    suspend fun tripStopovers(tripId: String): List<Stopover>

    /** One trip as a whole: its line, all its stops with live times and its way on the map. */
    suspend fun tripCourse(tripId: String): TripCourse

    /** The next departures of a stop, of the given [products] only: from now on, or from [time] when one is given. */
    /**
     * The lines that serve a stop, also those that do not leave right now. By default from the
     * departures of the next hour; providers that know the lines of a stop say so themselves.
     */
    suspend fun stopLines(stopId: String): List<StopLine> =
        departures(stopId, 80).map { StopLine(it.line, it.product) }.distinct()

    /**
     * The next departure of [line] at a stop in each of its directions, for opening its course
     * from there. By default within the next hour; providers that can look further do so.
     */
    suspend fun lineDepartures(stopId: String, line: String, product: String): List<Departure> =
        departures(stopId, 80, setOf(searchProductOf(product)))
            .filter { it.line == line && !it.cancelled && it.tripId != null }
            .distinctBy { it.direction }

    suspend fun departures(
        stopId: String,
        results: Int = 8,
        products: Set<String> = PRODUCT_KEYS.toSet(),
        time: OffsetDateTime? = null,
    ): List<Departure>

    /** The vehicles of the given [products] that reach a stop within [durationMinutes] from [time] on. */
    suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival>

    /**
     * Journeys that leave now, or at [departure], or that are there by [arrival].
     * The provider takes only one of the two times.
     */
    suspend fun journeys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int = 6,
        departure: OffsetDateTime? = null,
        arrival: OffsetDateTime? = null,
    ): JourneyPage

    /**
     * The same journey with fresh live data, or null when it can no longer be found.
     * The stops on the way and the shapes of the legs are only loaded when asked for.
     */
    suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey?

    /** The journeys before a previous page; [earlierRef] comes from that page. */
    suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int = 4,
    ): JourneyPage

    /** The journeys that follow a previous page; [laterRef] comes from that page. */
    suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int = 4,
    ): JourneyPage
}

/**
 * [journey] with fresh live data, or null when nothing could be loaded. A journey that was
 * put together from two is loaded half by half; a half that cannot be loaded keeps its
 * last known times.
 */
suspend fun TransitSource.refreshed(journey: Journey, stopovers: Boolean, polylines: Boolean): Journey? {
    val split = journey.split
    if (split == null) {
        val token = journey.refreshToken ?: return null
        val fresh = refreshJourney(token, stopovers, polylines) ?: return null
        // The provider does not know which place on the way the search asked for.
        return fresh.copy(refreshToken = fresh.refreshToken ?: token, via = journey.via)
    }
    val head = split.headToken
        ?.let { refreshJourney(it, stopovers, polylines) }
        ?.legs
        ?.takeIf { it.size >= split.headLegs }
        ?.take(split.headLegs)
        ?.let { legs -> cutHead(legs, journey.legs.getOrNull(split.headLegs - 1), split.headCut) }
    val tail = split.tailToken
        ?.let { refreshJourney(it, stopovers, polylines) }
        ?.legs
    if (head == null && tail == null) return null
    return Journey(
        legs = (head ?: journey.legs.take(split.headLegs)) + (tail ?: journey.legs.drop(split.headLegs)),
        split = split,
        via = journey.via,
    )
}

/**
 * The first part of a swapped journey as loaded again: with [cut], its last ride ends at that
 * stop as before ([known] is that leg as it was). Without the stop among the fresh stops on
 * the way the leg stays as it was known.
 */
private fun cutHead(legs: List<Leg>, known: Leg?, cut: String?): List<Leg> {
    if (cut == null || known == null || legs.isEmpty()) return legs
    val last = legs.last()
    val index = last.stopovers.indexOfLast { it.name == cut }
    val shortened = if (index > 0) cutLeg(last, index) else known
    return legs.dropLast(1) + shortened
}

/** A line that serves a stop: its name and product (see [PRODUCT_KEYS] and "express"). */
data class StopLine(val line: String, val product: String)

/** The product key a search filter knows for [product]: every product has its own key. */
fun searchProductOf(product: String): String = product
