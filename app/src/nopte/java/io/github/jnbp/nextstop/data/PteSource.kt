package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime

/**
 * Stand-in for the public-transport-enabler source in builds without that library
 * (`nextstop.pte=false` in gradle.properties, see app/build.gradle.kts). Every request reports
 * the source as not set up, so the chain skips it; the settings say it is not included.
 * The real one is in src/pte/java.
 */
@Suppress("UNUSED_PARAMETER")
class PteSource(authorization: String) : TransitSource {

    private fun absent(): Nothing = throw NotConfiguredException(Backend.Pte)

    override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> = absent()

    override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? = absent()

    override suspend fun searchStops(query: String, results: Int): List<Stop> = absent()

    override suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int,
        products: Set<String>?,
    ): List<Vehicle> = absent()

    override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> = absent()

    override suspend fun tripStopovers(tripId: String): List<Stopover> = absent()

    override suspend fun tripCourse(tripId: String): TripCourse = absent()

    override suspend fun departures(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> = absent()

    override suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival> = absent()

    override suspend fun journeys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int,
        departure: OffsetDateTime?,
        arrival: OffsetDateTime?,
    ): JourneyPage = absent()

    override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? = absent()

    override suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int,
    ): JourneyPage = absent()

    override suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int,
    ): JourneyPage = absent()

    companion object {
        /** False: this build has no public-transport-enabler. */
        const val INCLUDED = false
    }
}
