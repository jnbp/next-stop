package io.github.jnbp.nextstop.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A lift that is out of order, near a station. [description] says which one, where the source tells. */
data class LiftOutage(
    val id: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val source: String,
)

/** A source of lifts out of order around a position. */
interface LiftSource {
    suspend fun outagesNear(latitude: Double, longitude: Double, radiusMeters: Int): List<LiftOutage>
}

/**
 * accessibility.cloud by Sozialhelden e.V., the data behind brokenlifts.org: the lifts of BVG,
 * S-Bahn Berlin and (since August 2026) DB InfraGO stations in Berlin and Brandenburg, with
 * `isWorking`. Needs an app token (free for non-commercial use, accessibility.cloud); the
 * user enters it in the settings and it stays on the device.
 * API: GET /equipment-infos.json?appToken&latitude&longitude&accuracy (metres, max 10000).
 */
class AccessibilityCloudLifts(private val appToken: String) : LiftSource {
    override suspend fun outagesNear(latitude: Double, longitude: Double, radiusMeters: Int): List<LiftOutage> =
        withContext(Dispatchers.IO) {
            val body = httpGet(
                "$ACCESSIBILITY_CLOUD_URL/equipment-infos.json?appToken=${encode(appToken)}" +
                    "&latitude=$latitude&longitude=$longitude&accuracy=${radiusMeters.coerceIn(10, 10_000)}&limit=200",
                emptyMap(),
            )
            parseAccessibilityCloud(body)
        }
}

/** The lifts out of order in an answer of accessibility.cloud (a GeoJSON FeatureCollection). */
fun parseAccessibilityCloud(body: String): List<LiftOutage> {
    val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
    return (0 until features.length()).mapNotNull { index ->
        val feature = features.optJSONObject(index) ?: return@mapNotNull null
        val properties = feature.optJSONObject("properties") ?: return@mapNotNull null
        if (properties.optString("category") != "elevator") return@mapNotNull null
        // Only what is known to be broken: a missing state is no outage.
        if (properties.isNull("isWorking") || properties.optBoolean("isWorking", true)) return@mapNotNull null
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
        LiftOutage(
            id = feature.optString("_id").ifEmpty { properties.optString("_id").ifEmpty { properties.optString("originalId") } },
            description = localized(properties.opt("description")),
            latitude = coordinates.optDouble(1),
            longitude = coordinates.optDouble(0),
            source = "accessibility.cloud",
        )
    }.filter { !it.latitude.isNaN() && !it.longitude.isNaN() }
}

/**
 * DB FaSta (station facilities status) of the DB API Marketplace: lifts of DB stations, the
 * S-Bahn stations of Berlin included, not the U-Bahn. Needs a client id and an API key (free
 * plan, developers.deutschebahn.com), entered in the settings and kept on the device.
 * API: GET /facilities?type=ELEVATOR&state=INACTIVE&area=lonW,latS,lonE,latN.
 */
class FaStaLifts(private val clientId: String, private val apiKey: String) : LiftSource {
    override suspend fun outagesNear(latitude: Double, longitude: Double, radiusMeters: Int): List<LiftOutage> =
        withContext(Dispatchers.IO) {
            val dLat = radiusMeters / 111_320.0
            val dLon = radiusMeters / (111_320.0 * cos(Math.toRadians(latitude)))
            val area = listOf(longitude - dLon, latitude - dLat, longitude + dLon, latitude + dLat).joinToString(",")
            val body = httpGet(
                "$FASTA_URL/facilities?type=ELEVATOR&state=INACTIVE&area=${encode(area)}",
                mapOf("DB-Client-Id" to clientId, "DB-Api-Key" to apiKey),
            )
            parseFaSta(body)
        }
}

/** The lifts out of order in an answer of FaSta (a JSON array of facilities). */
fun parseFaSta(body: String): List<LiftOutage> {
    val list = JSONArray(body)
    return (0 until list.length()).mapNotNull { index ->
        val item = list.optJSONObject(index) ?: return@mapNotNull null
        if (item.optString("type") != "ELEVATOR" || item.optString("state") != "INACTIVE") return@mapNotNull null
        if (item.isNull("geocoordX") || item.isNull("geocoordY")) return@mapNotNull null
        LiftOutage(
            id = "fasta:" + item.optLong("equipmentnumber"),
            description = item.optString("description").takeIf { it.isNotBlank() },
            latitude = item.optDouble("geocoordY"),
            longitude = item.optDouble("geocoordX"),
            source = "DB FaSta",
        )
    }
}

/**
 * The keys of the lift sources, entered by the user and kept on the device only. Without one
 * entered, the key built into the app (see [BuiltInKeys]) is used.
 */
class LiftKeys(context: Context) {
    private val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    var accessibilityCloudToken: String
        get() = prefs.getString(KEY_AC, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AC, value.trim()).apply()

    var dbClientId: String
        get() = prefs.getString(KEY_DB_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DB_ID, value.trim()).apply()

    var dbApiKey: String
        get() = prefs.getString(KEY_DB_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_DB_KEY, value.trim()).apply()

    /** True when this build has an accessibility.cloud token of its own. */
    val builtInAccessibilityCloud: Boolean get() = BuiltInKeys.accessibilityCloudToken.isNotBlank()

    /** True when this build has a DB FaSta client id and API key of its own. */
    val builtInFasta: Boolean get() = BuiltInKeys.fastaClientId.isNotBlank() && BuiltInKeys.fastaApiKey.isNotBlank()

    /** The sources there are keys for (entered or built in); empty while there is none. */
    fun sources(): List<LiftSource> = buildList {
        val token = effectiveKey(accessibilityCloudToken, BuiltInKeys.accessibilityCloudToken)
        if (token.isNotEmpty()) add(AccessibilityCloudLifts(token))
        effectiveKeyPair(dbClientId, dbApiKey, BuiltInKeys.fastaClientId, BuiltInKeys.fastaApiKey)
            ?.let { (id, key) -> add(FaStaLifts(id, key)) }
    }

    private companion object {
        // Not backed up with the other settings: the keys belong to this device.
        const val STORE = "lift_keys"
        const val KEY_AC = "accessibilityCloud"
        const val KEY_DB_ID = "dbClientId"
        const val KEY_DB_KEY = "dbApiKey"
    }
}

/**
 * Lifts out of order at stations, from every source with a key, kept for [CACHE_MILLIS] per
 * station. A source that fails counts as knowing nothing, so a missing key or network never
 * shows a lift as broken.
 */
object LiftStatus {
    private val cache = mutableMapOf<String, Pair<Long, List<LiftOutage>>>()

    /** The lifts out of order at [place]; empty without a position or source. */
    suspend fun at(sources: List<LiftSource>, place: Stop): List<LiftOutage> {
        val position = place.position ?: return emptyList()
        if (sources.isEmpty()) return emptyList()
        val key = "%.4f,%.4f".format(java.util.Locale.ROOT, position.first, position.second)
        synchronized(cache) {
            cache[key]?.let { (time, found) -> if (System.currentTimeMillis() - time < CACHE_MILLIS) return found }
        }
        val found = coroutineScope {
            sources.map { source ->
                async {
                    try {
                        source.outagesNear(position.first, position.second, STATION_RADIUS_METERS)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.flatMap { it.await() }
        }.distinctBy { "%.5f,%.5f".format(java.util.Locale.ROOT, it.latitude, it.longitude) }
        synchronized(cache) { cache[key] = System.currentTimeMillis() to found }
        return found
    }

    /** The lifts out of order where [journey] starts, changes and ends, by station name. */
    suspend fun onJourney(sources: List<LiftSource>, journey: Journey): List<Pair<String, List<LiftOutage>>> =
        liftPlaces(journey).mapNotNull { place ->
            at(sources, place).takeIf { it.isNotEmpty() }?.let { place.name to it }
        }

    private const val CACHE_MILLIS = 5 * 60 * 1000L
    private const val STATION_RADIUS_METERS = 250
}

/** The stations of [journey] where a lift matters: boarding the first ride, each change, leaving the last ride. */
fun liftPlaces(journey: Journey): List<Stop> {
    val rides = journey.rides
    if (rides.isEmpty()) return emptyList()
    val places = buildList {
        rides.first().fromPlace?.let { add(it) }
        rides.zipWithNext().forEach { (arriving, next) ->
            arriving.toPlace?.let { add(it) }
            next.fromPlace?.let { add(it) }
        }
        rides.last().toPlace?.let { add(it) }
    }
    return places.filter { it.kind == PlaceKind.Stop }.distinctBy { normalStationId(it.id) }
}

private fun localized(value: Any?): String? = when (value) {
    is String -> value.takeIf { it.isNotBlank() }
    is JSONObject -> {
        val language = AppLanguage.code
        value.optString(language).ifEmpty { value.optString("en") }.ifEmpty {
            value.keys().asSequence().map { value.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
        }.takeIf { it.isNotBlank() }
    }
    else -> null
}

private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

private fun httpGet(url: String, headers: Map<String, String>): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "NextStop-dev/0.1 (private test build)")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val code = connection.responseCode
        if (code !in 200..299) throw HttpStatusException(code)
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

// The cached base of accessibility.cloud (data up to 5 minutes old, as the docs ask for apps).
private const val ACCESSIBILITY_CLOUD_URL = "https://accessibility-cloud-v2.freetls.fastly.net"

// FaSta v2 on the DB API Marketplace.
private const val FASTA_URL = "https://apis.deutschebahn.com/db-api-marketplace/apis/fasta/v2"
