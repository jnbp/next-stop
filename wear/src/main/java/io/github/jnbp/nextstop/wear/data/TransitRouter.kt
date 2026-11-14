package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject

/**
 * Neither the phone nor the watch on its own found journeys or departures. [phoneReached]
 * tells whether a phone with Next Stop answered at all ("Handy nicht erreichbar" when not).
 */
class RouteUnavailableException(val phoneReached: Boolean, cause: Throwable?) :
    IOException("no journeys or departures (phone ${if (phoneReached) "answered" else "not reachable"})", cause)

/**
 * Where the watch gets journeys, departures and places from. The phone first when it is
 * reachable ([PhoneQuery]): it asks its whole chain of sources (BVG, Transitous, ...), so the
 * watch finds the same as the phone, also outside Berlin. On its own the watch asks BVG inside
 * Berlin and Brandenburg (the area of the VBB) and Transitous everywhere else, each standing in
 * for the other when it fails.
 *
 * Departures of a stop of the VBB come from BVG directly, as before (they are loaded every 30
 * seconds); the phone only stands in when BVG fails.
 */
object TransitRouter {
    private val bvg = BvgClient()

    /**
     * Journeys from [from] (a stop, or a position as "address:lat,lon") to [to], leaving now.
     * Throws [RouteUnavailableException] when neither the phone nor the watch found any answer.
     */
    suspend fun journeys(context: Context, from: Place, to: Place, results: Int, comfort: TransferComfort): List<Journey> {
        val body = JSONObject()
            .put("from", placeJson(from))
            .put("to", placeJson(to))
            .put("results", results)
            .put("comfort", comfort.name)
        val answer = PhoneQuery.ask(context, PhoneQuery.KIND_JOURNEYS, body, PHONE_JOURNEYS_MILLIS)
        if (answer != null && answer.optBoolean("ok", false)) {
            return parseJourneysJson(answer.optJSONArray("journeys")).sortedWith(searchOrder).take(results)
        }
        if (answer != null) Log.d(LOG_TAG, "router: the phone found no journeys: ${answer.optString("error")}")
        val inVbb = inVbbArea(from) && inVbbArea(to)
        val first = if (inVbb) Backend.Bvg else Backend.Transitous
        val second = if (inVbb) Backend.Transitous else Backend.Bvg
        val tried = attempt(LOCAL_MILLIS) { localJourneys(first, from, to, results, comfort) }
        tried.getOrNull()?.let { return it.sortedWith(searchOrder) }
        Log.d(LOG_TAG, "router: $first failed: ${tried.exceptionOrNull()}")
        val backup = attempt(LOCAL_MILLIS) { localJourneys(second, from, to, results, comfort) }
        backup.getOrNull()?.let { return it.sortedWith(searchOrder) }
        Log.d(LOG_TAG, "router: $second failed: ${backup.exceptionOrNull()}")
        throw RouteUnavailableException(phoneReached = answer != null, cause = tried.exceptionOrNull())
    }

    private suspend fun localJourneys(
        backend: Backend,
        from: Place,
        to: Place,
        results: Int,
        comfort: TransferComfort,
    ): List<Journey> = when (backend) {
        Backend.Bvg -> bvg.journeys(from, to, results, comfort)
        Backend.Transitous -> TransitousClient.journeys(from, to, results, comfort)
    }

    /**
     * The next departures at [stop]. A stop of the VBB: BVG, the phone when that fails (else
     * the failure of BVG, as before). Any other stop: the phone, else Transitous; throws
     * [RouteUnavailableException] when neither answers.
     */
    suspend fun departures(context: Context, stop: Place, results: Int): List<Departure> {
        if (isVbbStop(stop)) {
            val direct = attempt(LOCAL_MILLIS) { bvg.departures(stop.id, results) }
            direct.getOrNull()?.let { return it }
            phoneDepartures(context, stop, results)?.let { return it }
            throw direct.exceptionOrNull() ?: BvgException(BvgFailure.Unreachable, "departures failed")
        }
        val answer = phoneDeparturesAnswer(context, stop, results)
        if (answer != null && answer.optBoolean("ok", false)) return departureBoard(parseDeparturesJson(answer.optJSONArray("departures")))
        val local = attempt(LOCAL_MILLIS) { TransitousClient.departures(stop, results) }
        local.getOrNull()?.let { return it }
        Log.d(LOG_TAG, "router: departures of ${stop.id} failed: ${local.exceptionOrNull()}")
        throw RouteUnavailableException(phoneReached = answer != null, cause = local.exceptionOrNull())
    }

    private suspend fun phoneDepartures(context: Context, stop: Place, results: Int): List<Departure>? {
        val answer = phoneDeparturesAnswer(context, stop, results) ?: return null
        if (!answer.optBoolean("ok", false)) return null
        return departureBoard(parseDeparturesJson(answer.optJSONArray("departures")))
    }

    private suspend fun phoneDeparturesAnswer(context: Context, stop: Place, results: Int): JSONObject? =
        PhoneQuery.ask(
            context,
            PhoneQuery.KIND_DEPARTURES,
            JSONObject().put("stop", placeJson(stop)).put("results", results),
            PHONE_DEPARTURES_MILLIS,
        )

    /**
     * Places that match [query] (a typed or spoken destination, the address of a contact or an
     * appointment): from the phone, else BVG, else (BVG failed or found nothing) Transitous.
     * Throws the failure of BVG when nothing answers.
     */
    suspend fun searchPlaces(context: Context, query: String, results: Int, near: Position? = null): List<Place> {
        val body = JSONObject().put("query", query).put("results", results)
        if (near != null) body.put("lat", near.latitude).put("lon", near.longitude)
        val answer = PhoneQuery.ask(context, PhoneQuery.KIND_SEARCH, body, PHONE_SEARCH_MILLIS)
        if (answer != null && answer.optBoolean("ok", false)) {
            val found = parsePlacesJson(answer.optJSONArray("places"))
            if (found.isNotEmpty()) return found.take(results)
        }
        val direct = attempt(LOCAL_MILLIS) { bvg.searchPlaces(query, results) }
        direct.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val fallback = attempt(LOCAL_MILLIS) {
            TransitousClient.searchPlaces(query, results, near?.let { it.latitude to it.longitude })
        }
        fallback.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        // Nothing found: an empty list when one of them answered, else the failure.
        if (direct.isSuccess || fallback.isSuccess) return emptyList()
        throw direct.exceptionOrNull() ?: fallback.exceptionOrNull() ?: BvgException(BvgFailure.Unreachable, "search failed")
    }

    /**
     * Runs [block] within [millis]; its failure (a timeout as [BvgFailure.Unreachable]) as the
     * result. A cancellation of the caller goes on.
     */
    private suspend fun <T> attempt(millis: Long, block: suspend () -> T): Result<T> = try {
        Result.success(withHardTimeout(millis, block))
    } catch (e: TimeoutCancellationException) {
        Result.failure(BvgException(BvgFailure.Unreachable, "timeout after $millis ms", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private enum class Backend { Bvg, Transitous }

    // The phone runs its whole chain (several providers, walks from OpenStreetMap): a little longer.
    private const val PHONE_JOURNEYS_MILLIS = 25_000L
    private const val PHONE_DEPARTURES_MILLIS = 15_000L
    private const val PHONE_SEARCH_MILLIS = 12_000L
    private const val LOCAL_MILLIS = 20_000L
}

/** True for a stop of BVG (an id of digits) inside the VBB area, or without a position. */
fun isVbbStop(stop: Place): Boolean {
    if (stop.kind != PlaceKind.Stop || stop.id.isEmpty() || !stop.id.all { it.isDigit() }) return false
    val latitude = stop.latitude ?: return true
    val longitude = stop.longitude ?: return true
    return inVbbArea(latitude, longitude)
}

/**
 * True for a place inside Berlin and Brandenburg: by its position, else (none known) a stop of
 * BVG by its id.
 */
fun inVbbArea(place: Place): Boolean {
    val latitude = place.latitude
    val longitude = place.longitude
    if (latitude == null || longitude == null) return place.id.isNotEmpty() && place.id.all { it.isDigit() }
    return inVbbArea(latitude, longitude)
}

/**
 * The order of the journeys the watch shows for a search, the twin of searchOrder in
 * P data/SearchOrder.kt (change both together): the earliest time to set off first, the start
 * of a leading walk, live where known; then the earlier arrival, fewer rides, less walking.
 * The providers mostly answer in this order, but not always (Transitous, merged answers).
 */
val searchOrder: Comparator<Journey> = compareBy<Journey> { it.departure }
    .thenBy { it.arrival }
    .thenBy { it.rides.size }
    .thenBy { journey -> journey.legs.filter { it.walking }.sumOf { it.arrival - it.departure } }

/** True inside Berlin and Brandenburg, the area of the VBB that BVG's data covers (the outline of the phone app). */
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

// The area of the VBB as a rough outline of latitude and longitude, a little generous at the
// edges; the same as the phone's (RoutingSource.kt there).
private val VBB_OUTLINE: List<Pair<Double, Double>> = listOf(
    53.05 to 11.25, 53.34 to 11.58, 53.38 to 12.30, 53.27 to 12.75, 53.22 to 13.20,
    53.42 to 13.70, 53.40 to 14.25, 53.31 to 14.47, 52.95 to 14.20, 52.60 to 14.67,
    52.30 to 14.64, 51.85 to 14.80, 51.53 to 14.77, 51.41 to 14.20, 51.35 to 13.60,
    51.41 to 13.14, 51.75 to 13.10, 51.95 to 12.78, 52.10 to 12.38, 52.25 to 12.18,
    52.45 to 12.23, 52.65 to 12.18, 52.85 to 12.13, 52.95 to 11.78,
)
