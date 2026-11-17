package io.github.jnbp.nextstop.data

import java.io.IOException
import java.time.OffsetDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A provider of transit data the app can ask. A local one covers its [area] and is preferred
 * there, because its live data is usually more current; one without an area covers all of
 * Germany. [key] is stored in the settings and must stay as it is. [prefix] stands in front
 * of the ids, trip handles and tokens of the provider; BVG has none, as the app used it alone
 * before. More regional providers become further entries here.
 */
enum class DataSource(val key: String, val prefix: String, val area: ((Double, Double) -> Boolean)?) {
    Bvg("bvg", "", ::inVbbArea),
    Transitous("transitous", "tr:", null),
}

/**
 * Which backends the app asks, in the order the user chose (see [Backend]): the first one in
 * use that covers a request answers it, the next ones stand in when it fails. [enabled] are
 * the ones switched on; a backend that needs an access key is only in use with one.
 * [vbbAccessId] is the key for the official VBB HAFAS API, [pteAuthorization] the HAFAS client
 * authorization that public-transport-enabler sends (JSON, as {"type":"AID","aid":"…"}); both
 * are kept on the device only. [builtInVbbAccessId] is the VBB key built into the app (see
 * BuiltInKeys), used while the user has entered none; it is never stored with the settings.
 */
data class SourceChoice(
    val order: List<Backend> = Backend.entries,
    val enabled: Set<Backend> = DEFAULT_ENABLED,
    val vbbAccessId: String = "",
    val pteAuthorization: String = "",
    val builtInVbbAccessId: String = "",
) {
    /** The VBB key in use: the one entered, else the built-in one. */
    val vbbKey: String
        get() = effectiveKey(vbbAccessId, builtInVbbAccessId)

    /** Every backend once, in the chosen order; ones the order does not know yet at their default place at the end. */
    val ordered: List<Backend>
        get() = (order + Backend.entries).distinct()

    /** True when [backend] has what it needs to be asked. */
    fun configured(backend: Backend): Boolean = when (backend) {
        Backend.Vbb -> vbbKey.isNotBlank()
        Backend.Pte -> PteSource.INCLUDED && pteAuthorization.isNotBlank()
        Backend.Bvg, Backend.Transitous -> true
    }

    /** The backends that are switched on and set up, best first. */
    val inUse: List<Backend>
        get() = ordered.filter { it in enabled && configured(it) }

    /**
     * The id families in use, in the order of their best backend; Transitous when nothing
     * else is, so the app is never left without a source.
     */
    val active: List<DataSource>
        get() = inUse.map { it.family }.distinct().ifEmpty { listOf(DataSource.Transitous) }

    // The keys never go into a log.
    override fun toString(): String =
        "SourceChoice(order=$ordered, enabled=$enabled, vbbKey=${vbbKey.isNotBlank()}, pte=${pteAuthorization.isNotBlank()})"

    companion object {
        /** Every backend on; those without a key are skipped until one is entered. */
        val DEFAULT_ENABLED: Set<Backend> = Backend.entries.toSet()
    }

    /** True when a provider in use has data for a position: a local one in its area, a wide one (Transitous) all over Europe. */
    fun covers(latitude: Double, longitude: Double): Boolean =
        active.any { source -> source.area?.invoke(latitude, longitude) ?: inWideArea(latitude, longitude) }
}

/** True inside a rough box around Germany. */
fun inGermany(latitude: Double, longitude: Double): Boolean =
    latitude in 47.2..55.1 && longitude in 5.8..15.1

/**
 * True inside a rough box around Europe, the area of the wide providers (Transitous has data
 * for much of Europe; how good it is differs by country). The app is made for Germany: the
 * offline station data (lifts, entrances, positions, walks) exists only there.
 */
fun inWideArea(latitude: Double, longitude: Double): Boolean =
    latitude in 34.0..71.5 && longitude in -25.0..45.0

/**
 * Picks the provider for each request from the ones in use (see [SourceChoice]): a local one
 * for places in its area, Transitous for the rest of Germany.
 *
 * Everything carries the [DataSource.prefix] of the provider it came from in front of its ids,
 * trip handles, refresh tokens and page references, so every later request about it goes to
 * that provider again, also after a restart or a change of the setting. Ids of two providers
 * never meet in one request: a place of another provider is passed on by its position.
 *
 * [choice] is read anew for every request, so a changed setting counts at once. [userAgent]
 * names the app towards Transitous (see [TransitousSource.userAgent]). [bvgUrl] is the server
 * asked for Berlin data: the public one, or an own server with the same API (hafas-rest-api
 * v6, as bvg-rest or db-rest), read anew for every request too.
 */
class RoutingSource(
    private val choice: () -> SourceChoice,
    userAgent: String,
    private val bvgUrl: () -> String = { BvgRestSource.DEFAULT_URL },
    private val health: SourceHealth = SourceHealth.shared,
) : TransitSource {
    // The client of the Berlin server, made anew when its address changes.
    @Volatile
    private var bvgClient: Pair<String, BvgRestSource> = BvgRestSource.DEFAULT_URL to BvgRestSource()
    private val bvg: BvgRestSource
        get() {
            val url = bvgUrl()
            val known = bvgClient
            if (known.first == url) return known.second
            return BvgRestSource(url).also { bvgClient = url to it }
        }
    private val transitous = TransitousSource(userAgent)
    private val photon = PhotonSearch()

    // The clients of the backends with an access key, made anew when the key changes.
    @Volatile
    private var vbbClient: Pair<String, VbbHafasSource>? = null

    @Volatile
    private var pteClient: Pair<String, PteSource>? = null

    private fun vbb(accessId: String): VbbHafasSource =
        vbbClient?.takeIf { it.first == accessId }?.second ?: VbbHafasSource(accessId).also { vbbClient = accessId to it }

    private fun pte(authorization: String): PteSource =
        pteClient?.takeIf { it.first == authorization }?.second
            ?: PteSource(authorization).also { pteClient = authorization to it }

    private fun backendClient(backend: Backend, choice: SourceChoice): TransitSource = when (backend) {
        Backend.Vbb -> vbb(choice.vbbKey)
        Backend.Bvg -> bvg
        Backend.Pte -> pte(choice.pteAuthorization.trim())
        Backend.Transitous -> transitous
    }

    // Berlin and Brandenburg: VBB HAFAS, BVG REST and public-transport-enabler in the chosen
    // order, one standing in for the other (see FamilySource).
    private val vbbFamily = FamilySource(DataSource.Bvg, {
        val current = choice()
        current.inUse.filter { it.family == DataSource.Bvg }.map { it to backendClient(it, current) }
    }, SourceChain(health), standIn = { choice().active.size > 1 })

    /**
     * Where the user is, or the middle of the map: the search for places puts those nearby
     * first. Set by the interface; null while it is not known.
     */
    @Volatile
    var searchNear: Pair<Double, Double>? = null

    /**
     * The middle of the region the search for places prefers: where the user is, else the
     * middle of the map or the last known place. Unlike [searchNear] it is never shown as a
     * distance. Set by the interface; null while it is not known.
     */
    @Volatile
    var searchRegion: Pair<Double, Double>? = null

    // The other stops of a station complex found by a search, by the id of the stop shown for
    // it: the tram and bus stops beside a rail station, or the same station of another provider.
    private val complexes = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    // The answer of a provider other than Transitous to the latest query, by provider and query: the
    // search and the search for far stations, asked at once, share one request (see sharedHits).
    private val providerHits = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<List<Stop>>>()

    /** The stops [source] finds for [query], asked once for the search and the far stations together. */
    private suspend fun sharedHits(source: DataSource, query: String, results: Int): List<Stop> {
        val prefix = "${source.name}|"
        val key = prefix + query
        val fresh = CompletableDeferred<List<Stop>>()
        val known = providerHits.putIfAbsent(key, fresh)
        if (known != null) return known.await()
        // Only the latest query of each provider is kept.
        providerHits.keys.removeIf { it != key && it.startsWith(prefix) }
        return try {
            client(source).searchStops(query, results).also { fresh.complete(it) }
        } catch (e: Throwable) {
            providerHits.remove(key, fresh)
            // One who waits for it is not cancelled with the one who asked: a plain failure for it.
            fresh.completeExceptionally(if (e is CancellationException) IOException("search cancelled") else e)
            throw e
        }
    }

    /**
     * Remembers [members] as further stops of the station shown as [host] (one sign on the map
     * for the parts of a big station, see stationGroups), so its departures come from all of them.
     */
    fun joinComplex(host: String, members: List<String>) {
        val more = members.filter { it != host }
        if (more.isEmpty()) return
        complexes[host] = (complexes[host].orEmpty() + more).distinct()
    }

    /** True when a provider in use has data for a position. */
    fun covers(latitude: Double, longitude: Double): Boolean = choice().covers(latitude, longitude)

    private fun client(source: DataSource): TransitSource = when (source) {
        DataSource.Bvg -> vbbFamily
        DataSource.Transitous -> transitous
    }

    override suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Stop> {
        val source = sourceAt(latitude, longitude)
        return try {
            reached(source) { client(source).nearbyStops(latitude, longitude, results) }.map { signed(source, it) }
        } catch (e: SourceUnreachableException) {
            // Another family in use that covers the place stands in, with its own stops.
            val standIn = choice().active.firstOrNull { it != source && coversPosition(it, latitude, longitude) } ?: throw e
            val stops = reached(standIn) { client(standIn).nearbyStops(latitude, longitude, results) }.map { signed(standIn, it) }
            noteStandIn(standIn)
            stops
        }
    }

    /** Remembers for the hint that the family [standIn] answered in place of the first choice. */
    private fun noteStandIn(standIn: DataSource) {
        val current = choice()
        val first = current.inUse.firstOrNull() ?: return
        val answered = current.inUse.firstOrNull { it.family == standIn } ?: return
        health.record(first, answered)
    }

    private fun coversPosition(source: DataSource, latitude: Double, longitude: Double): Boolean =
        source.area?.invoke(latitude, longitude) ?: inWideArea(latitude, longitude)

    override suspend fun nearestPlace(latitude: Double, longitude: Double): Stop? {
        val source = sourceAt(latitude, longitude)
        return client(source).nearestPlace(latitude, longitude)?.let { marked(source, it) }
    }

    override suspend fun searchStops(query: String, results: Int): List<Stop> = coroutineScope {
        val near = searchNear
        val region = near ?: searchRegion
        // Transitous and Photon are asked for places near the region first.
        transitous.searchBias = region
        // All providers in use are asked at once, and OpenStreetMap (Photon) for the points of
        // interest and addresses they lack; one that fails leaves the results of the others.
        val asked = choice().active.map { source ->
            source to async {
                attempt {
                    val found = if (source == DataSource.Transitous) {
                        client(source).searchStops(query, results)
                    } else {
                        sharedHits(source, query, results)
                    }
                    found.map { signed(source, it) }
                }
            }
        }
        val fromOsm = async { attempt { photon.search(query, near, results, bias = region) } }
        val found = asked.map { (source, answer) -> source to answer.await() }
        val osm = fromOsm.await()
        if (found.all { it.second.isFailure } && osm.isFailure) {
            throw found.firstNotNullOfOrNull { it.second.exceptionOrNull() } ?: IOException("search failed")
        }
        val joined = mutableMapOf<String, List<String>>()
        val providers = mergeStops(found.map { (source, answer) -> source to answer.getOrDefault(emptyList()) }, results, joined)
        // The stops of one station complex are remembered, so its departures come from all of them.
        joined.forEach { (host, members) -> complexes[host] = members }
        // A place of OpenStreetMap that a provider has too is shown once, as the provider has it.
        val extra = osm.getOrDefault(emptyList())
            .filter { place -> choice().covers(place.latitude ?: 0.0, place.longitude ?: 0.0) }
            .filter { place -> providers.none { known -> known.kind != PlaceKind.Stop && sameSpot(known, place) } }
        // One town found by two sources ("Stuttgart", "Stuttgart") shows once.
        withoutTwinTowns(rankPlaces(query, providers + extra, near, region)).take(results)
    }

    override val searchCenter: Pair<Double, Double>? get() = searchNear ?: searchRegion

    override suspend fun searchLongDistance(query: String, results: Int): List<Stop> = coroutineScope {
        // Without the bias for the region: "Hamburg" in Berlin asks Transitous for the stations of
        // Hamburg; the other providers have no bias, so the answer of the search asked at the same
        // time serves (see sharedHits) instead of a second request.
        val asked = choice().active.map { source ->
            source to async {
                attempt {
                    val found = if (source == DataSource.Transitous) {
                        transitous.searchStopsNear(query, results, null)
                    } else {
                        sharedHits(source, query, results)
                    }
                    found.map { signed(source, it) }
                }
            }
        }
        val found = asked.flatMap { (_, answer) -> answer.await().getOrDefault(emptyList()) }
        longDistanceStations(query, found, local = emptyList(), region = searchNear ?: searchRegion)
    }

    /** Vehicles of the provider for the middle of the box; Transitous has them only for small boxes. */
    override suspend fun vehicles(
        north: Double,
        west: Double,
        south: Double,
        east: Double,
        results: Int,
        products: Set<String>?,
    ): List<Vehicle> {
        val source = sourceAt((north + south) / 2, (west + east) / 2)
        return client(source).vehicles(north, west, south, east, results, products).map { vehicle ->
            vehicle.copy(
                tripId = source.prefix + vehicle.tripId,
                direction = localStopTerms(vehicle.direction),
                nextStop = vehicle.nextStop?.let { localStopTerms(it) },
            )
        }
    }

    override suspend fun tripPath(tripId: String): List<Pair<Double, Double>> {
        val source = sourceOf(tripId)
        return client(source).tripPath(plain(source, tripId))
    }

    override suspend fun tripStopovers(tripId: String): List<Stopover> {
        val source = sourceOf(tripId)
        return client(source).tripStopovers(plain(source, tripId)).map { marked(source, it) }
    }

    override suspend fun tripCourse(tripId: String): TripCourse {
        val source = sourceOf(tripId)
        val course = withTrack(source, marked(source, client(source).tripCourse(plain(source, tripId))))
        // The hops still without a way: its vehicle stands at their stations (see TripTrack).
        return course.copy(airHops = airHops(course.path, course.stops.map { it.place?.position }))
    }

    /**
     * [course] with the tracks of its train where its source sends only air lines between
     * stations (see airHops): the regional HAFAS (BVG, VBB) for an ICE, an IC or a regional
     * train from elsewhere. The same run at Transitous, found by its line and planned departure
     * at one of its first stations, lends its way for those hops (see spliceTrack). The course
     * as it is when Transitous is not in use, does not know the run or takes too long.
     */
    private suspend fun withTrack(source: DataSource, course: TripCourse): TripCourse {
        if (!wantsTrack(source, course.product, course.line)) return course
        val stops = course.stops.map { it.place?.position }
        if (airHops(course.path, stops).isEmpty()) return course
        val boards = course.stops.mapNotNull { stop ->
            val position = stop.place?.position ?: return@mapNotNull null
            val planned = stop.plannedDeparture ?: stop.departure ?: return@mapNotNull null
            position to planned
        }
        val track = foregroundTrack(course.tripId, course.line, course.direction, boards) ?: return course
        return course.copy(path = spliceTrack(course.path, stops, track))
    }

    /** True when a trip of [product] from [source] may get its tracks from Transitous (see withTrack). */
    private fun wantsTrack(source: DataSource, product: String, line: String?): Boolean =
        source != DataSource.Transitous && runsOnRails(product, line) && Backend.Transitous in choice().inUse

    /**
     * [boundedTrack] for someone waiting for it (a course, a journey with its ways). A trip that
     * a prefetch is looking up already ([TrackCache.claim]) is not asked for a second time: its
     * answer is waited for up to [TRACK_CLAIM_WAIT_MILLIS], else the course goes without it.
     */
    private suspend fun foregroundTrack(
        tripId: String,
        line: String?,
        direction: String?,
        boards: List<Pair<Pair<Double, Double>, OffsetDateTime>>,
    ): List<Pair<Double, Double>>? {
        TrackCache.get(tripId)?.let { return it.takeIf { track -> track.size >= 2 } }
        if (!TrackCache.claim(tripId)) {
            val known = withTimeoutOrNull(TRACK_CLAIM_WAIT_MILLIS) {
                while (TrackCache.isClaimed(tripId)) delay(TRACK_CLAIM_POLL_MILLIS)
                TrackCache.get(tripId)
            }
            return known?.takeIf { it.size >= 2 }
        }
        return try {
            boundedTrack(tripId, line, direction, boards)
        } finally {
            TrackCache.release(tripId)
        }
    }

    /**
     * [trackOf] within [TRACK_LOOKUP_MILLIS]; null on failure or when it takes longer. A
     * failure (timeout, HTTP, I/O) is kept for a short while (see [TrackCache.putFailure]) so a
     * trip whose lookup fails is not asked for again on every load; a cancellation by the caller
     * is not a failure and is passed on.
     */
    private suspend fun boundedTrack(
        tripId: String,
        line: String?,
        direction: String?,
        boards: List<Pair<Pair<Double, Double>, OffsetDateTime>>,
    ): List<Pair<Double, Double>>? {
        var answered = false
        return try {
            val track = withTimeoutOrNull(TRACK_LOOKUP_MILLIS) { trackOf(tripId, line, direction, boards).also { answered = true } }
            // Timed out (withTimeoutOrNull only returns null by itself when trackOf did not end).
            if (!answered) TrackCache.putFailure(tripId)
            track
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TrackCache.putFailure(tripId)
            null
        }
    }

    /**
     * The way at Transitous of the run [tripId] of [line] towards [direction] that leaves the places of [boards] at
     * their planned times (its first stations are tried, see TRACK_LOOKUP_STOPS); null where it
     * is not found. Kept per trip, a miss too, so a trip is looked up once (see TrackCache).
     */
    private suspend fun trackOf(
        tripId: String,
        line: String?,
        direction: String?,
        boards: List<Pair<Pair<Double, Double>, OffsetDateTime>>,
    ): List<Pair<Double, Double>>? {
        TrackCache.get(tripId)?.let { return it.takeIf { track -> track.size >= 2 } }
        if (line == null || boards.isEmpty()) return null
        for ((position, planned) in boards.take(TRACK_LOOKUP_STOPS)) {
            val found = transitous.departures(
                positionStopId(position.first, position.second),
                TRACK_LOOKUP_RESULTS,
                TRACK_PRODUCTS,
                planned.minusMinutes(TRACK_LOOKUP_LEAD_MINUTES),
            )
            // The best fitting run: by its name, else by kind, minute and direction (see runScore).
            val runId = found.filter { !it.cancelled && it.tripId != null }
                .map { it to runScore(it, line, planned, direction) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }?.first?.tripId ?: continue
            val track = transitous.tripPath(runId).takeIf { it.size >= 2 } ?: continue
            TrackCache.put(tripId, track)
            return track
        }
        // Answered, but no such run: not asked again for a while.
        TrackCache.put(tripId, emptyList())
        return null
    }

    /** The positions of the stops of the ride [leg] in order (null where one has none): where it is boarded, the stops between, where it is left. */
    private fun rideStops(leg: Leg): List<Pair<Double, Double>?> =
        listOf(leg.fromPlace?.position ?: leg.path.firstOrNull()) + leg.stopovers.map { it.place?.position } +
            listOf(leg.toPlace?.position ?: leg.path.lastOrNull())

    /** True when [leg] is a train of [source] whose way has air lines that Transitous may fill (see withTrack). */
    private fun needsTrack(source: DataSource, leg: Leg): Boolean =
        !leg.walking && leg.tripId != null && wantsTrack(source, leg.product, leg.line) && airHops(leg.path, rideStops(leg)).isNotEmpty()

    /** [leg] with the tracks Transitous lent its trip (see [trackOf]); as it is without them. */
    private fun splicedLeg(leg: Leg, track: List<Pair<Double, Double>>?): Leg {
        if (track == null || track.size < 2) return leg
        val path = spliceTrack(leg.path, rideStops(leg), track)
        return if (path === leg.path) leg else leg.copy(path = path)
    }

    private fun boardsOf(leg: Leg): List<Pair<Pair<Double, Double>, OffsetDateTime>> {
        val position = leg.fromPlace?.position ?: leg.path.firstOrNull() ?: return emptyList()
        return listOf(position to (leg.plannedDeparture ?: leg.departure))
    }

    /**
     * [journey] with the tracks of its trains where its source sends air lines (see withTrack).
     * With [wait] the trips not known yet are looked up now (all at once, bounded in time);
     * otherwise only known tracks are used and the others are looked up in the background
     * (see [prefetchTracks]), for the next time the journey is loaded. Legs that have their
     * own way cost no request.
     */
    private suspend fun withTracks(source: DataSource, journey: Journey, wait: Boolean): Journey {
        val wanting = journey.legs.filter { needsTrack(source, it) }
        if (wanting.isEmpty()) return journey
        val tracks = if (wait) {
            coroutineScope {
                wanting.distinctBy { it.tripId }.map { leg ->
                    async { leg.tripId!! to foregroundTrack(leg.tripId!!, leg.line, leg.direction, boardsOf(leg)) }
                }.map { it.await() }.toMap()
            }
        } else {
            prefetchTracks(wanting)
            return withKnownTracks(journey, wanting)
        }
        return journey.copy(legs = journey.legs.map { leg -> if (leg in wanting) splicedLeg(leg, tracks[leg.tripId]) else leg })
    }

    /** [journey] with the tracks already known for its legs of [wanting]; no request. */
    private fun withKnownTracks(journey: Journey, wanting: List<Leg>): Journey =
        journey.copy(legs = journey.legs.map { leg -> if (leg in wanting) splicedLeg(leg, TrackCache.get(leg.tripId!!)) else leg })

    /**
     * [page] with the tracks known for its trains. The trips still wanting one are gathered
     * across all its journeys and the first few looked up in one background prefetch (replacing
     * the prefetch of the page before, see [pagePrefetch]); the page itself uses cache hits only.
     */
    private fun withTracks(source: DataSource, page: JourneyPage): JourneyPage {
        val wanting = page.journeys.associateWith { journey -> journey.legs.filter { needsTrack(source, it) } }
        val all = wanting.values.flatten()
        synchronized(pagePrefetchLock) {
            // The lookups of the page before for trips this page no longer shows are dropped;
            // those it still shows go on (a repeated search would otherwise lose them).
            val wanted = all.mapNotNull { it.tripId }.toSet()
            val (kept, stale) = pagePrefetch.entries.partition { it.key in wanted && it.value.isActive }
            stale.forEach { it.value.cancel() }
            pagePrefetch = kept.associate { it.key to it.value } + prefetchTracks(all)
        }
        if (all.isEmpty()) return page
        return page.copy(
            journeys = page.journeys.map { journey ->
                val legs = wanting[journey].orEmpty()
                if (legs.isEmpty()) journey else withKnownTracks(journey, legs)
            },
        )
    }

    // Lookups of tracks that nobody waits for, off the main thread. The scope lives as long as
    // this source (the process); each lookup is a Job of its own in it.
    private val trackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The prefetch of the search page shown last (a new search, an earlier or a later page), by
    // trip id. It lives until the next page replaces it: that page cancels the lookups of trips
    // it does not show, so lookups still pending for results no longer on screen are dropped (a
    // cancelled lookup is not cached as a failure and releases its claim). The prefetch of a
    // single journey (refreshJourney) is not tracked here and runs to its end.
    private var pagePrefetch: Map<String, Job> = emptyMap()
    private val pagePrefetchLock = Any()

    /**
     * Looks up the tracks of the first few trips of [legs] not known yet, in the background;
     * the Job of each lookup by its trip id.
     */
    private fun prefetchTracks(legs: List<Leg>): Map<String, Job> =
        legs.distinctBy { it.tripId }
            .filter { TrackCache.get(it.tripId!!) == null && !TrackCache.isClaimed(it.tripId!!) }
            .take(TRACK_PREFETCH_TRIPS)
            .filter { TrackCache.claim(it.tripId!!) }
            .associate { leg ->
                val tripId = leg.tripId!!
                val job = trackScope.launch { boundedTrack(tripId, leg.line, leg.direction, boardsOf(leg)) }
                // Released on completion, also when cancelled before it started.
                job.invokeOnCompletion { TrackCache.release(tripId) }
                tripId to job
            }

    override suspend fun departures(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> {
        val members = complexes[stopId].orEmpty()
        // In the order they leave, cancelled runs at their planned time (see departureOrderTime),
        // each ride once (an S1 and an extra "1" at the same minute, see withoutDoubleRides).
        if (members.isEmpty()) return inDepartureOrder(withoutDoubleRides(departuresAt(stopId, results, products, time)))
        // A station complex: the departures of all its stops at once, in one list by time. One
        // stop that fails leaves the others; the same ride from two providers shows once.
        return coroutineScope {
            val asked = (listOf(stopId) + members).map { id ->
                async { attempt { departuresAt(id, results, products, time) } }
            }.map { it.await() }
            if (asked.all { it.isFailure }) throw asked.first().exceptionOrNull() ?: IOException("departures failed")
            mergeDepartures(asked.map { it.getOrDefault(emptyList()) }).take(results)
        }
    }

    override suspend fun stopLines(stopId: String): List<StopLine> {
        val source = sourceOf(stopId)
        return reached(source) { client(source).stopLines(plain(source, stopId)) }
    }

    override suspend fun lineDepartures(stopId: String, line: String, product: String): List<Departure> {
        val source = sourceOf(stopId)
        return reached(source) { client(source).lineDepartures(plain(source, stopId), line, product) }
            .map { marked(source, it) }
    }

    private suspend fun departuresAt(
        stopId: String,
        results: Int,
        products: Set<String>,
        time: OffsetDateTime?,
    ): List<Departure> {
        val source = sourceOf(stopId)
        return reached(source) { client(source).departures(plain(source, stopId), results, products, time) }
            .map { marked(source, it) }
    }

    override suspend fun arrivals(
        stopId: String,
        products: Set<String>,
        time: OffsetDateTime,
        durationMinutes: Int,
    ): List<Arrival> {
        val source = sourceOf(stopId)
        return client(source).arrivals(plain(source, stopId), products, time, durationMinutes).map { arrival ->
            arrival.copy(tripId = arrival.tripId?.let { source.prefix + it })
        }
    }

    override suspend fun journeys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        results: Int,
        departure: OffsetDateTime?,
        arrival: OffsetDateTime?,
    ): JourneyPage {
        val source = journeySource(from, to, options)
        val via = options.via
        // The OpenStreetMap packs of the states of both ends (and the stop on the way), where
        // the app has them, so that the walks are led to stop positions and entrances there.
        OsmStations.ensureRegions(listOfNotNull(from.position, to.position, via?.place?.position))
        if (via != null) {
            // A stop of the same provider passed without a stay is asked for in one search;
            // everything else is put together from a search to the place and one from there.
            if (via.minutes > 0 || via.place.kind != PlaceKind.Stop || sourceOf(via.place.id) != source) {
                return viaJourneys(from, to, via, options.copy(via = null), results, departure, arrival)
            }
            val page = reached(source) {
                client(source).journeys(
                    placeFor(source, from), placeFor(source, to), plainVia(source, options), results, departure, arrival,
                )
            }
            return withTracks(source, withVia(marked(source, page), via))
        }
        val start = placeFor(source, from)
        val end = placeFor(source, to)
        return try {
            withTracks(source, reached(source) { marked(source, client(source).journeys(start, end, options, results, departure, arrival)) })
        } catch (e: SourceUnreachableException) {
            // Another family in use that covers both places stands in for one whose backends all
            // failed (Transitous for Berlin, say); the places pass by their positions.
            val standIn = choice().active.firstOrNull {
                it != source && covers(it, from) && covers(it, to) &&
                    from.position != null && to.position != null
            } ?: throw e
            val page = reached(standIn) {
                client(standIn).journeys(placeFor(standIn, from), placeFor(standIn, to), options, results, departure, arrival)
            }
            noteStandIn(standIn)
            marked(standIn, page)
        }
    }

    override suspend fun refreshJourney(refreshToken: String, stopovers: Boolean, polylines: Boolean): Journey? {
        val source = sourceOf(refreshToken)
        val fresh = client(source).refreshJourney(plain(source, refreshToken), stopovers, polylines)?.let { marked(source, it) }
            ?: return null
        // Loaded with its ways (the detail, a followed trip): the tracks of its trains are waited for.
        return withTracks(source, fresh, wait = polylines)
    }

    // A further page goes to the provider of the page it follows.
    override suspend fun earlierJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        earlierRef: String,
        results: Int,
    ): JourneyPage {
        val source = sourceOf(earlierRef)
        val page = client(source).earlierJourneys(
            placeFor(source, from), placeFor(source, to), plainVia(source, options), plain(source, earlierRef), results,
        )
        return withTracks(source, withVia(marked(source, page), options.via))
    }

    override suspend fun laterJourneys(
        from: Stop,
        to: Stop,
        options: SearchOptions,
        laterRef: String,
        results: Int,
    ): JourneyPage {
        val source = sourceOf(laterRef)
        val page = client(source).laterJourneys(
            placeFor(source, from), placeFor(source, to), plainVia(source, options), plain(source, laterRef), results,
        )
        return withTracks(source, withVia(marked(source, page), options.via))
    }

    /** [options] with the place on the way as [source] knows it. */
    private fun plainVia(source: DataSource, options: SearchOptions): SearchOptions {
        val via = options.via ?: return options
        return options.copy(via = via.copy(place = placeFor(source, via.place)))
    }

    /**
     * Journeys from [from] through [via] to [to], put together from two searches (see
     * [composeVia]). A pass without a stay leaves the usual change time at a stop.
     */
    private suspend fun viaJourneys(
        from: Stop,
        to: Stop,
        via: Via,
        options: SearchOptions,
        results: Int,
        departure: OffsetDateTime?,
        arrival: OffsetDateTime?,
    ): JourneyPage {
        val gap = if (via.minutes > 0 || via.place.kind != PlaceKind.Stop) via.minutes.toLong() else options.comfort.minutes.toLong()
        val found = composeVia(from, to, via, gap, departure, arrival) { start, end, leaves, arrives ->
            journeys(start, end, options, results, leaves, arrives).journeys
        }
        return JourneyPage(found, earlierRef = null, laterRef = null)
    }

    /**
     * Runs a request to [source]; a provider that cannot be reached at all (no network, a name
     * that does not resolve, a server error) is reported as [SourceUnreachableException].
     */
    private suspend fun <T> reached(source: DataSource, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceUnreachableException) {
            throw e
        } catch (e: HttpStatusException) {
            if (e.code >= 500) throw SourceUnreachableException(source, e, triedOf(source)) else throw e
        } catch (e: IOException) {
            throw SourceUnreachableException(source, e, triedOf(source))
        }

    // The backend behind a family that is asked directly, without a chain.
    private fun triedOf(source: DataSource): List<Backend> =
        choice().inUse.filter { it.family == source }.take(1)

    /**
     * For places around a position: the first family in the chosen order that covers it (with
     * the default order the local one in its area, Transitous elsewhere).
     */
    private fun sourceAt(latitude: Double, longitude: Double): DataSource {
        val active = choice().active
        return active.firstOrNull { coversPosition(it, latitude, longitude) }
            ?: active.firstOrNull { it.area == null }
            ?: active.first()
    }

    /**
     * For a search between two places: the first family in the chosen order that covers both;
     * else the one the start came from; else the first in use.
     */
    private fun journeySource(from: Stop, to: Stop, options: SearchOptions? = null): DataSource {
        val active = choice().active
        // The bike combo goes to Transitous where it is in use: only MOTIS rides by bicycle to the
        // first stop and from the last one; HAFAS can only carry the bicycle on the rides.
        if (options?.bikeCombo == true && DataSource.Transitous in active && from.position != null && to.position != null) {
            return DataSource.Transitous
        }
        return active.firstOrNull { covers(it, from) && covers(it, to) }
            ?: sourceOf(from.id).takeIf { it in active }
            ?: active.first()
    }

    /** A place for a request to [source]: its own id when it came from there, otherwise its position. */
    private fun placeFor(source: DataSource, place: Stop): Stop {
        // A place of OpenStreetMap belongs to no provider: it is passed on by its position.
        if (isOsmPlace(place)) {
            if (place.position == null) throw IOException("place without position")
            return place.copy(kind = PlaceKind.Address)
        }
        val origin = sourceOf(place.id)
        val plainPlace = place.copy(id = plain(origin, place.id))
        if (origin == source) return plainPlace
        return when {
            place.kind == PlaceKind.Address -> plainPlace
            place.position != null -> plainPlace.copy(kind = PlaceKind.Address)
            else -> throw IOException("place without position")
        }
    }
}

/**
 * The departures of the stops of one complex in one list, by time. A ride that an earlier list
 * has already is left out: the same trip, or the same line at the same planned minute towards
 * the same place (as two providers name it).
 */
internal fun mergeDepartures(lists: List<List<Departure>>): List<Departure> {
    val kept = mutableListOf<Departure>()
    lists.forEach { list ->
        val before = kept.toList()
        list.forEach { departure -> if (before.none { sameRide(it, departure) }) kept.add(departure) }
    }
    // A replacement run from one stop may meet the cancelled run it stands in for from another;
    // a ride one stop sends twice (an S1 and an extra "1") shows once.
    return withoutDoubleRides(mergeReplacements(inDepartureOrder(kept)))
}

/**
 * True when two departures of different stops of a complex, or of two pages of one board,
 * are one ride. One trip passes a stop twice on the ring (S41, S42 once round and on): the
 * same trip is one ride only at the same planned minute.
 */
internal fun sameRide(a: Departure, b: Departure): Boolean {
    val first = (a.plannedTime ?: a.time)?.toEpochSecond()?.div(60)
    val second = (b.plannedTime ?: b.time)?.toEpochSecond()?.div(60)
    if (a.tripId != null && a.tripId == b.tripId) return first == null || second == null || first == second
    // A cancelled run and the extra run in its place are two runs (see mergeReplacements).
    if (a.cancelled != b.cancelled && (a.additional || b.additional)) return false
    if (normalLine(a.line) != normalLine(b.line)) return false
    if (first == null || second == null || first != second) return false
    // The same line at the same minute from the same platform: one ride, however two
    // providers name where it goes ("Ring S41 ⟳" and "S Südkreuz").
    val platform = a.platform?.trim().orEmpty()
    if (platform.isNotEmpty() && platform.equals(b.platform?.trim().orEmpty(), ignoreCase = true)) return true
    val one = stationNameKey(a.direction)
    val other = stationNameKey(b.direction)
    return one.isNotEmpty() && other.isNotEmpty() && (one.contains(other) || other.contains(one))
}

/**
 * A family of data that could not be reached: every backend of it in use failed. [tried] are
 * the backends asked, best first; empty when none could be asked (none set up, or none that
 * has this kind of data). The interface names them instead of showing the cause.
 */
class SourceUnreachableException(
    val source: DataSource,
    cause: Throwable,
    val tried: List<Backend> = emptyList(),
) : IOException("${source.key} unreachable", cause)

/** The provider an id, handle or token came from, by its mark. */
private fun sourceOf(value: String): DataSource =
    DataSource.entries.firstOrNull { it.prefix.isNotEmpty() && value.startsWith(it.prefix) }
        ?: DataSource.entries.first { it.prefix.isEmpty() }

private fun plain(source: DataSource, value: String): String = value.removePrefix(source.prefix)

/**
 * True when a local [source] covers a place: by its position, or, without one, when the place
 * came from there. A provider for all of Germany covers every place.
 */
private fun covers(source: DataSource, place: Stop): Boolean {
    val area = source.area ?: return true
    val position = place.position ?: return sourceOf(place.id) == source
    return area(position.first, position.second)
}

/**
 * [ride] with its way cut where its stops lie beside it (see [PathJoin.clipStartAtStop]): the
 * way of a provider can start before the stop where the ride is boarded and end past the one
 * where it is left, which drew a V with the walk there, on every map that shows the journey.
 */
internal fun clippedRide(ride: Leg): Leg {
    if (ride.walking || ride.path.size < 2) return ride
    var path = ride.path
    ride.fromPlace?.position?.let { path = PathJoin.clipStartAtStop(path, it) }
    ride.toPlace?.position?.let { path = PathJoin.clipEndAtStop(path, it) }
    return if (path == ride.path) ride else ride.copy(path = path)
}

// What came from a provider, with its mark on every id and handle in it, and its stop names
// in the app's language where a Europe-wide feed names them in English ("Göttingen central bus
// station" as "Göttingen ZOB", see localStopTerms; with the position of a stop, only in Germany): here, where every provider's answer enters
// the app, so every screen, notification, shared text, widget and the watch get the same names.
// The raw names stay inside the providers' own handles (refresh tokens).

private fun marked(source: DataSource, stop: Stop): Stop =
    stop.copy(id = source.prefix + stop.id, name = if (stop.kind == PlaceKind.Stop) localStopTerms(stop.name, latitude = stop.latitude, longitude = stop.longitude) else stop.name)

// A stop of a list of stops (nearby, found by name) with its sign where its platforms are in
// OpenStreetMap, not where the provider's point lies (see stopSignPoint): the map draws the
// sign, the marked stop and the vehicles lined up beside it there.
private fun signed(source: DataSource, stop: Stop): Stop = OsmStations.signPlace(marked(source, stop))

private fun marked(source: DataSource, stopover: Stopover): Stopover =
    stopover.copy(name = localStopTerms(stopover.name, latitude = stopover.place?.latitude, longitude = stopover.place?.longitude), place = stopover.place?.let { marked(source, it) })

private fun marked(source: DataSource, departure: Departure): Departure = departure.copy(
    tripId = departure.tripId?.let { source.prefix + it },
    direction = localStopTerms(departure.direction),
    destination = departure.destination?.let { marked(source, it) },
    // The stop a notice was found at can be shown on the map and asked for again.
    warnings = departure.warnings.map { warning -> if (warning.stop == null) warning else warning.copy(stop = marked(source, warning.stop)) },
)

private fun marked(source: DataSource, course: TripCourse): TripCourse = course.copy(
    tripId = source.prefix + course.tripId,
    stops = course.stops.map { marked(source, it) },
)

private fun marked(source: DataSource, leg: Leg): Leg = clippedRide(leg).copy(
    from = if (leg.fromPlace?.kind == PlaceKind.Address) leg.from else localStopTerms(leg.from, latitude = leg.fromPlace?.latitude, longitude = leg.fromPlace?.longitude),
    to = if (leg.toPlace?.kind == PlaceKind.Address) leg.to else localStopTerms(leg.to, latitude = leg.toPlace?.latitude, longitude = leg.toPlace?.longitude),
    direction = leg.direction?.let { localStopTerms(it) },
    fromPlace = leg.fromPlace?.let { marked(source, it) },
    toPlace = leg.toPlace?.let { marked(source, it) },
    stopovers = leg.stopovers.map { marked(source, it) },
    tripId = leg.tripId?.let { source.prefix + it },
)

// In this order: each ride's way clipped at its stops (in marked of the leg, see clippedRide);
// walks led to the stop position or station entrance OpenStreetMap knows and out of the exit
// of a station (OsmStations.refine); then a ride the provider splits in two where nobody
// changes is one ride (see mergeThroughRuns), before anything rates, draws or follows it.
private fun marked(source: DataSource, journey: Journey): Journey = mergeThroughRuns(
    OsmStations.refine(
        Journey(
            legs = journey.legs.map { marked(source, it) },
            refreshToken = journey.refreshToken?.let { source.prefix + it },
        ),
    ),
)

private fun marked(source: DataSource, page: JourneyPage): JourneyPage = JourneyPage(
    journeys = page.journeys.map { marked(source, it) },
    earlierRef = page.earlierRef?.let { source.prefix + it },
    laterRef = page.laterRef?.let { source.prefix + it },
)

/**
 * The places of all providers in one list: those of the local ones first, then those of the
 * ones for all of Germany that are not the same place under another name. While one for all of
 * Germany is in use, a local provider only counts inside its area, and the others keep at
 * least half of the rows when they have more to show, so places elsewhere are not pushed out
 * by similar names nearby.
 */
private fun mergeStops(
    found: List<Pair<DataSource, List<Stop>>>,
    results: Int,
    complexes: MutableMap<String, List<String>> = mutableMapOf(),
): List<Stop> {
    val national = found.any { it.first.area == null }
    val local = mutableListOf<Stop>()
    // A stop that is one station with one already kept (see samePlace) joins its complex: its
    // kinds of transport are added to the kept one, and its id is kept for the departures.
    fun join(kept: MutableList<Stop>, stop: Stop): Boolean {
        val at = kept.indexOfFirst { samePlace(it, stop) }
        if (at < 0) return false
        val host = kept[at]
        if (host.kind == PlaceKind.Stop && stop.kind == PlaceKind.Stop && host.id != stop.id) {
            kept[at] = host.copy(products = host.products + stop.products)
            // The same station of another provider only adds what the first one lacks
            // (long-distance trains, say); stops of one provider always belong together.
            if (sourceOf(host.id) == sourceOf(stop.id) || (stop.products - host.products).isNotEmpty()) {
                complexes[host.id] = (complexes[host.id].orEmpty() + stop.id).distinct()
            }
        }
        return true
    }
    found.filter { it.first.area != null }.forEach { (source, stops) ->
        stops.filter { !national || covers(source, it) }.forEach { stop ->
            if (!join(local, stop)) local.add(stop)
        }
    }
    val rest = mutableListOf<Stop>()
    found.filter { it.first.area == null }
        .flatMap { it.second }
        .forEach { candidate -> if (!join(local, candidate) && !join(rest, candidate)) rest.add(candidate) }
    val localRows = if (rest.isEmpty()) results else maxOf(results - rest.size, (results + 1) / 2)
    return (local.take(localRows) + rest).take(results)
}

// Two stops this close are one station, whatever each provider calls it.
private const val SAME_STATION_METERS = 300

/** Stops close together are one; other places only with the same name as well. */
private fun samePlace(a: Stop, b: Stop): Boolean {
    val first = a.position
    val second = b.position
    if (first == null || second == null) return plainName(a) == plainName(b)
    val near = distanceMeters(first, second) <= SAME_STATION_METERS
    if (a.kind != PlaceKind.Stop && b.kind != PlaceKind.Stop && sameSpot(a, b)) return true
    return near && (a.kind == PlaceKind.Stop && b.kind == PlaceKind.Stop || plainName(a) == plainName(b))
}

private fun plainName(place: Stop): String = place.name.plainStopName().lowercase().filter { it.isLetterOrDigit() }

/** Distance along the ground between two positions, in metres. */
private fun distanceMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
    val lat1 = Math.toRadians(from.first)
    val lat2 = Math.toRadians(to.first)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.second - from.second)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Runs [block] and keeps a failure as a value; cancellation still goes through. */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

// The area of the VBB (Berlin and Brandenburg) as a rough outline of latitude and longitude,
// a little generous at the edges. Good enough to tell Brandenburg from the states around it.
private val VBB_OUTLINE: List<Pair<Double, Double>> = listOf(
    53.05 to 11.25, 53.34 to 11.58, 53.38 to 12.30, 53.27 to 12.75, 53.22 to 13.20,
    53.42 to 13.70, 53.40 to 14.25, 53.31 to 14.47, 52.95 to 14.20, 52.60 to 14.67,
    52.30 to 14.64, 51.85 to 14.80, 51.53 to 14.77, 51.41 to 14.20, 51.35 to 13.60,
    51.41 to 13.14, 51.75 to 13.10, 51.95 to 12.78, 52.10 to 12.38, 52.25 to 12.18,
    52.45 to 12.23, 52.65 to 12.18, 52.85 to 12.13, 52.95 to 11.78,
)

/** True inside Berlin and Brandenburg, the area of the VBB that BVG's data covers. */
fun inVbbArea(latitude: Double, longitude: Double): Boolean {
    // Ray casting: a point is inside when a line from it crosses the outline an odd number of times.
    var inside = false
    var previous = VBB_OUTLINE.last()
    for (point in VBB_OUTLINE) {
        val (lat1, lon1) = point
        val (lat2, lon2) = previous
        if ((lat1 > latitude) != (lat2 > latitude) &&
            longitude < (lon2 - lon1) * (latitude - lat1) / (lat2 - lat1) + lon1
        ) {
            inside = !inside
        }
        previous = point
    }
    return inside
}

/** The journeys of [page] marked with the place on the way they were searched with. */
private fun withVia(page: JourneyPage, via: Via?): JourneyPage {
    if (via == null) return page
    return page.copy(journeys = page.journeys.map { it.copy(via = JourneyVia(via.place, via.minutes, null)) })
}

/**
 * The address of the server for Berlin data as the user wrote it ("own server" in the
 * settings), or the public one where that is empty or no web address.
 */
fun ownServerUrl(text: String): String {
    val url = text.trim().trimEnd('/')
    val web = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
    return if (web && url.length > "https://".length) url else BvgRestSource.DEFAULT_URL
}

// Looking up the tracks of a train at Transitous (see RoutingSource.withTrack): at most this many
// of its first stations, this many departures from a few minutes before it leaves, of these
// products, and no longer than this in all, so the course is not kept waiting.
private const val TRACK_LOOKUP_STOPS = 2
// At most this many trips of one page of search results are looked up in the background.
private const val TRACK_PREFETCH_TRIPS = 4
private const val TRACK_LOOKUP_RESULTS = 20
private const val TRACK_LOOKUP_LEAD_MINUTES = 5L
private const val TRACK_LOOKUP_MILLIS = 6000L
// A course waits this long for a lookup of its trip that a prefetch has already started, checking this often.
private const val TRACK_CLAIM_WAIT_MILLIS = 2500L
private const val TRACK_CLAIM_POLL_MILLIS = 100L
private val TRACK_PRODUCTS = setOf("express", "regional", "suburban")
