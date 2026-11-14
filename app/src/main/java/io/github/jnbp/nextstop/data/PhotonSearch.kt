package io.github.jnbp.nextstop.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Points of interest and addresses from Photon (photon.komoot.io), a free geocoder on
 * OpenStreetMap data. The transit providers know few points of interest, so Photon fills in
 * churches, venues, shops and the like. Its data must be credited to OpenStreetMap wherever
 * it is shown (see [isOsmPlace]).
 *
 * Fair use: only texts of at least [MIN_QUERY] letters are asked for, answers are kept for a
 * while, and the search panel waits for a pause in typing before it asks. The app names itself
 * without any personal contact.
 */
class PhotonSearch(private val baseUrl: String = DEFAULT_URL) {
    // Answers by request, so that typing back and forth costs nothing.
    private val cache = object : LinkedHashMap<String, Pair<Long, List<Stop>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Stop>>>?): Boolean =
            size > CACHE_ENTRIES
    }

    private val german: Boolean get() = AppLanguage.german

    /**
     * Places for [query], those near [bias] first where it is given; [near] gives their
     * distances. Empty for a short text.
     */
    suspend fun search(
        query: String,
        near: Pair<Double, Double>?,
        results: Int,
        bias: Pair<Double, Double>? = near,
    ): List<Stop> {
        val text = query.trim()
        if (text.length < MIN_QUERY) return emptyList()
        // Rounded, so a few steps of the user do not make a new request.
        val biasPart = bias?.let { "&lat=%.2f&lon=%.2f".format(Locale.ROOT, it.first, it.second) } ?: ""
        val path = "/api/?q=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20") +
            "&limit=$results&lang=" + (if (german) "de" else "en") + biasPart
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val known = cache[path]
            if (known != null && now - known.first < CACHE_MILLIS) return withDistance(known.second, near)
        }
        val body = withContext(Dispatchers.IO) { get(path) }
        val places = parsePhoton(body, german)
        synchronized(cache) { cache[path] = System.currentTimeMillis() to places }
        return withDistance(places, near)
    }

    private fun withDistance(places: List<Stop>, near: Pair<Double, Double>?): List<Stop> =
        if (near == null) {
            places
        } else {
            places.map { place -> place.copy(distanceMeters = place.position?.let { groundDistance(near, it).toInt() }) }
        }

    private fun get(path: String): String {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_URL = "https://photon.komoot.io"
        const val USER_AGENT = "NextStop/1.0"

        /** Texts shorter than this are not sent to Photon. */
        const val MIN_QUERY = 3
        private const val CACHE_ENTRIES = 48
        private const val CACHE_MILLIS = 10 * 60_000L
    }
}

/** Marks the ids of places from OpenStreetMap (Photon), so they are credited and routed by position. */
const val OSM_ID_PREFIX = "osm:"

/** True for a place found in OpenStreetMap data, which must be credited where it shows. */
fun isOsmPlace(place: Stop): Boolean = place.id.startsWith(OSM_ID_PREFIX)

// Kinds of OpenStreetMap objects that are no destination of their own here: the providers
// know the stops better, and areas and roads without a house number are too vague.
private val SKIPPED_KEYS = setOf("public_transport", "railway", "boundary", "landuse", "natural", "waterway", "route")
private val AREA_KEYS = setOf("place", "highway")

/**
 * The places of a Photon answer (GeoJSON, positions longitude first). A named object that
 * is not a road or an area is a point of interest, with its kind in front of its address
 * as the second line; the rest are addresses.
 */
internal fun parsePhoton(body: String, german: Boolean): List<Stop> {
    val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
    return (0 until features.length()).mapNotNull { index ->
        val feature = features.optJSONObject(index) ?: return@mapNotNull null
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
        if (coordinates.length() < 2) return@mapNotNull null
        val longitude = coordinates.optDouble(0)
        val latitude = coordinates.optDouble(1)
        if (latitude.isNaN() || longitude.isNaN()) return@mapNotNull null
        val p = feature.optJSONObject("properties") ?: return@mapNotNull null
        val key = p.text("osm_key")
        val value = p.text("osm_value")
        if (key in SKIPPED_KEYS) return@mapNotNull null
        val name = p.text("name")
        val street = p.text("street")
        val number = p.text("housenumber")
        val district = p.text("district") ?: p.text("locality")
        val city = p.text("city") ?: p.text("county")
        val streetLine = listOfNotNull(street, number).joinToString(" ").takeIf { it.isNotEmpty() }
        val area = listOfNotNull(district, city).distinct().firstOrNull()
        val poi = name != null && key != null && key !in AREA_KEYS && !(key == "building" && number != null && name == streetLine)
        val osmId = "$OSM_ID_PREFIX${p.text("osm_type") ?: "?"}${p.opt("osm_id") ?: "$latitude,$longitude"}"
        if (poi) {
            val kind = osmKindName(key!!, value, german)
            val where = listOfNotNull(streetLine, area).joinToString(", ").takeIf { it.isNotEmpty() }
            Stop(
                id = osmId,
                name = name!!,
                latitude = latitude,
                longitude = longitude,
                distanceMeters = null,
                kind = PlaceKind.Poi,
                postcode = p.text("postcode"),
                areas = listOfNotNull(district, city),
                detail = listOfNotNull(kind, where).joinToString(" · ").takeIf { it.isNotEmpty() },
            )
        } else {
            // A road or an area has no house number: it is named as it is, with the town.
            val title = (if (key == "highway" || key == "place") name else streetLine ?: name) ?: return@mapNotNull null
            Stop(
                id = osmId,
                name = title,
                latitude = latitude,
                longitude = longitude,
                distanceMeters = null,
                kind = PlaceKind.Address,
                postcode = p.text("postcode"),
                areas = listOfNotNull(district, city),
                detail = listOfNotNull(p.text("postcode")?.let { code -> city?.let { "$code $it" } } ?: city, district)
                    .distinct().joinToString(", ").takeIf { it.isNotEmpty() },
            )
        }
    }
}

// The kinds that come up most, by the value of an OpenStreetMap object, in German and English.
private val KIND_NAMES: Map<String, Pair<String, String>> = mapOf(
    "place_of_worship" to ("Gotteshaus" to "Place of worship"),
    "church" to ("Kirche" to "Church"),
    "cathedral" to ("Kathedrale" to "Cathedral"),
    "mosque" to ("Moschee" to "Mosque"),
    "synagogue" to ("Synagoge" to "Synagogue"),
    "restaurant" to ("Restaurant" to "Restaurant"),
    "cafe" to ("Café" to "Café"),
    "bar" to ("Bar" to "Bar"),
    "pub" to ("Kneipe" to "Pub"),
    "nightclub" to ("Club" to "Club"),
    "fast_food" to ("Imbiss" to "Fast food"),
    "school" to ("Schule" to "School"),
    "university" to ("Universität" to "University"),
    "college" to ("Hochschule" to "College"),
    "kindergarten" to ("Kita" to "Kindergarten"),
    "hospital" to ("Krankenhaus" to "Hospital"),
    "doctors" to ("Arztpraxis" to "Doctor"),
    "pharmacy" to ("Apotheke" to "Pharmacy"),
    "library" to ("Bibliothek" to "Library"),
    "museum" to ("Museum" to "Museum"),
    "theatre" to ("Theater" to "Theatre"),
    "cinema" to ("Kino" to "Cinema"),
    "community_centre" to ("Gemeindezentrum" to "Community centre"),
    "townhall" to ("Rathaus" to "Town hall"),
    "park" to ("Park" to "Park"),
    "sports_centre" to ("Sportzentrum" to "Sports centre"),
    "fitness_centre" to ("Fitnessstudio" to "Gym"),
    "swimming_pool" to ("Schwimmbad" to "Swimming pool"),
    "stadium" to ("Stadion" to "Stadium"),
    "supermarket" to ("Supermarkt" to "Supermarket"),
    "bakery" to ("Bäckerei" to "Bakery"),
    "hotel" to ("Hotel" to "Hotel"),
    "attraction" to ("Sehenswürdigkeit" to "Attraction"),
    "office" to ("Büro" to "Office"),
    "company" to ("Firma" to "Company"),
    "events_venue" to ("Veranstaltungsort" to "Venue"),
    "arts_centre" to ("Kulturzentrum" to "Arts centre"),
)

/** The kind of an OpenStreetMap object as a short word, from its value; null for a plain "yes". */
internal fun osmKindName(key: String, value: String?, german: Boolean): String? {
    val known = value?.let { KIND_NAMES[it] } ?: KIND_NAMES[key]
    if (known != null) return if (german) known.first else known.second
    if (value == null || value == "yes") return null
    return value.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun JSONObject.text(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

/** Distance along the ground between two positions, in metres. */
internal fun groundDistance(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
    val lat1 = Math.toRadians(from.first)
    val lat2 = Math.toRadians(to.first)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.second - from.second)
    val h = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2).let { it * it }
    return 2 * 6_371_000.0 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
}
