package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

/**
 * Runs [block] on the IO threads and gives up after [millis]: a blocking request that hangs
 * (HttpURLConnection ignores cancellation) no longer holds the screen, it is left to end on
 * its own. Throws [TimeoutCancellationException] on the timeout.
 */
suspend fun <T> withHardTimeout(millis: Long, block: suspend () -> T): T {
    val work = CoroutineScope(Dispatchers.IO).async { block() }
    try {
        return withTimeout(millis) { work.await() }
    } catch (e: TimeoutCancellationException) {
        work.cancel()
        throw e
    }
}

/**
 * The phone's position, asked for through the Data Layer: the watch asks the phone to sync
 * (its places carry its last known location) and waits at most [millis] for an answer newer
 * than the one it has. Null when the phone sends none.
 */
suspend fun phonePosition(context: Context, millis: Long = PHONE_POSITION_TIMEOUT_MILLIS): Position? {
    val before = WatchStore.places.value.phoneLocationTime
    withTimeoutOrNull(millis) {
        WatchStore.refresh(context)
        WatchStore.places.first { it.phoneLatitude != null && it.phoneLocationTime > before }
    }
    val places = WatchStore.places.value
    val latitude = places.phoneLatitude ?: return null
    val longitude = places.phoneLongitude ?: return null
    Log.d(LOG_TAG, "position: asked the phone, ${(System.currentTimeMillis() - places.phoneLocationTime) / 1000} s old")
    return Position(latitude, longitude, fromPhone = true, timeMillis = places.phoneLocationTime)
}

/**
 * Stops near a position from Transitous (MOTIS reverse geocoding), for when the BVG service
 * fails. A stop of the VBB in the German DELFI ids ("de:11000:900058101::2") gets its BVG id
 * (900058101), so its departures load from BVG as for every other stop; others keep the
 * Transitous id with the prefix "tr:".
 */
object TransitousNearby {
    private const val URL_BASE = "https://api.transitous.org"

    suspend fun nearbyStops(latitude: Double, longitude: Double, results: Int): List<Place> = withHardTimeout(TRANSITOUS_TIMEOUT_MILLIS) {
        val place = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)
        val connection = URL("$URL_BASE/api/v1/reverse-geocode?place=$place&type=STOP&numResults=$results").openConnection() as HttpURLConnection
        val text = try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NextStop-Wear-dev/0.1 (private test build)")
            if (connection.responseCode !in 200..299) throw BvgException(BvgFailure.Unreachable, "Transitous HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val json = JSONArray(text)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") != "STOP") return@mapNotNull null
            val lat = item.optDouble("lat").takeIf { !it.isNaN() } ?: return@mapNotNull null
            val lon = item.optDouble("lon").takeIf { !it.isNaN() } ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Place(
                id = bvgIdOf(id) ?: "tr:$id",
                name = name,
                latitude = lat,
                longitude = lon,
                kind = PlaceKind.Stop,
                distanceMeters = metresBetween(latitude, longitude, lat, lon),
            )
        }.distinctBy { it.id }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
    }
}

/** The BVG (VBB) stop id inside a DELFI id: "900" and six digits after "de:<area>:"; null if none. */
fun bvgIdOf(id: String): String? = VBB_ID.find(id)?.groupValues?.get(1)

private val VBB_ID = Regex("de:1[12]\\d{3}:(900\\d{6})")

/** The distance in metres between two positions, flat-earth (fine for the few kilometres around a stop). */
internal fun metresBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val east = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
    val north = (lat2 - lat1) * 111_320.0
    return sqrt(east * east + north * north).roundToInt()
}

/**
 * Stops near a position: BVG first, within [BVG_NEARBY_TIMEOUT_MILLIS]; where that fails or
 * finds none, Transitous. Throws the failure of BVG when Transitous finds nothing either.
 */
suspend fun nearbyStopsWithFallback(client: BvgClient, latitude: Double, longitude: Double, results: Int): List<Place> {
    val bvgFailure: Exception? = try {
        val found = withHardTimeout(BVG_NEARBY_TIMEOUT_MILLIS) { client.nearbyStops(latitude, longitude, results = results) }
        if (found.isNotEmpty()) {
            // The stops around say the town the watch is used in, for the names it shows.
            WatchTown.noteStops(found.map { it.name })
            return found
        }
        null
    } catch (e: TimeoutCancellationException) {
        Log.d(LOG_TAG, "nearby: BVG timed out after $BVG_NEARBY_TIMEOUT_MILLIS ms")
        BvgException(BvgFailure.Unreachable, "timeout")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(LOG_TAG, "nearby: BVG failed: $e")
        e
    }
    val fallback = try {
        TransitousNearby.nearbyStops(latitude, longitude, results)
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (e is TimeoutCancellationException) emptyList() else throw e
    } catch (e: Exception) {
        Log.d(LOG_TAG, "nearby: Transitous failed: $e")
        emptyList()
    }
    Log.d(LOG_TAG, "nearby: ${fallback.size} stops from Transitous")
    if (fallback.isNotEmpty()) {
        WatchTown.noteStops(fallback.map { it.name })
        return fallback
    }
    throw bvgFailure ?: return emptyList()
}

const val PHONE_POSITION_TIMEOUT_MILLIS = 6_000L
const val BVG_NEARBY_TIMEOUT_MILLIS = 12_000L
const val TRANSITOUS_TIMEOUT_MILLIS = 12_000L
