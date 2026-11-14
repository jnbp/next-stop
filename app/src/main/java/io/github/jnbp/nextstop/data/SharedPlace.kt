package io.github.jnbp.nextstop.data

import java.net.URLDecoder
import java.util.Locale

/**
 * A place another app handed over (a shared text, a geo: link): what to search for, and where
 * it lies when that was in what was shared. [name] is the name the sender gave the place.
 */
data class SharedPlace(val query: String, val position: Pair<Double, Double>?, val name: String?) {
    /** The point itself as a place to route to, when its coordinates were shared. */
    fun asPlace(): Stop? {
        val at = position ?: return null
        val label = name?.takeIf { it.isNotBlank() }
            ?: query.takeIf { it.isNotBlank() }
            ?: "%.5f, %.5f".format(Locale.ROOT, at.first, at.second)
        return Stop(
            id = "pin:${at.first},${at.second}",
            name = label,
            latitude = at.first,
            longitude = at.second,
            distanceMeters = null,
            kind = PlaceKind.Address,
        )
    }
}

// The longest text of a share that is searched for; a longer one is cut.
private const val MAX_QUERY_CHARS = 160

private val URL_PATTERN = Regex("""(?i)\b(?:https?://|geo:)\S+""")
private val NUMBER = """(-?\d{1,3}(?:\.\d+)?)"""
private val LAT_LON = Regex("""^\s*$NUMBER\s*,\s*$NUMBER\s*(?:\((.*)\))?\s*$""", RegexOption.DOT_MATCHES_ALL)

// The exact place in a Google Maps link (…!3d52.5!4d13.4…); the "@" of a link is only the
// middle of the map shown.
private val GOOGLE_EXACT = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
private val GOOGLE_AT = Regex("""/@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
private val GOOGLE_PLACE_NAME = Regex("""/maps/place/([^/@?#]+)""")
private val OSM_HASH = Regex("""#map=\d+(?:\.\d+)?/(-?\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)""")

// Words a sender puts in front of a link ("… on Google Maps:"); they are no place.
private val TRAILING_JUNK = Regex("""[\s:–—\-|]+$""")

private fun decode(text: String): String = try {
    URLDecoder.decode(text, "UTF-8")
} catch (e: IllegalArgumentException) {
    text
}

private fun validPosition(latitude: Double?, longitude: Double?): Pair<Double, Double>? {
    if (latitude == null || longitude == null) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    // 0,0 stands for "no position" in geo: links.
    if (latitude == 0.0 && longitude == 0.0) return null
    return latitude to longitude
}

/** "52.5,13.4" or "52.5,13.4(Name)": the position and the name in brackets. */
internal fun latLonText(text: String): Pair<Pair<Double, Double>, String?>? {
    val match = LAT_LON.matchEntire(text) ?: return null
    val position = validPosition(match.groupValues[1].toDoubleOrNull(), match.groupValues[2].toDoubleOrNull()) ?: return null
    return position to match.groupValues[3].trim().takeIf { it.isNotEmpty() }
}

/** The decoded query parameters of a link, the first of each name. */
internal fun linkParams(link: String): Map<String, String> {
    val query = link.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return emptyMap()
    val params = LinkedHashMap<String, String>()
    query.split('&').forEach { part ->
        val equals = part.indexOf('=')
        if (equals <= 0) return@forEach
        val key = decode(part.substring(0, equals)).lowercase(Locale.ROOT)
        if (key !in params) params[key] = decode(part.substring(equals + 1))
    }
    return params
}

/**
 * A geo: link (RFC 5870 and the Android form): "geo:52.5,13.4", "geo:0,0?q=Street 1, Town",
 * "geo:52.5,13.4?q=52.5,13.4(Name)", "geo:52.5,13.4;u=35?z=16". Null when it names nothing.
 */
fun parseGeoUri(text: String): SharedPlace? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("geo:", ignoreCase = true)) return null
    val body = trimmed.substring(4)
    val coordinates = body.substringBefore('?').substringBefore(';').split(',')
    var position = validPosition(coordinates.getOrNull(0)?.trim()?.toDoubleOrNull(), coordinates.getOrNull(1)?.trim()?.toDoubleOrNull())
    var name: String? = null
    var query = ""
    val q = linkParams("?" + body.substringAfter('?', ""))["q"]?.trim().orEmpty()
    if (q.isNotEmpty()) {
        val inQuery = latLonText(q)
        if (inQuery != null) {
            position = inQuery.first
            name = inQuery.second
            query = inQuery.second.orEmpty()
        } else {
            query = q
            name = q
        }
    }
    if (position == null && query.isEmpty()) return null
    return SharedPlace(query.take(MAX_QUERY_CHARS), position, name)
}

/** What a link of a map (Google Maps, OpenStreetMap, Apple Maps) says of a place; null for a link it does not read. */
internal fun parseMapLink(link: String): SharedPlace? {
    if (link.startsWith("geo:", ignoreCase = true)) return parseGeoUri(link)
    val params = linkParams(link)
    var position: Pair<Double, Double>? = null
    var name: String? = null
    var query = ""
    GOOGLE_PLACE_NAME.find(link)?.let { name = decode(it.groupValues[1].replace('+', ' ')).trim().takeIf { n -> n.isNotEmpty() } }
    GOOGLE_EXACT.find(link)?.let { position = validPosition(it.groupValues[1].toDoubleOrNull(), it.groupValues[2].toDoubleOrNull()) }
    if (position == null) {
        position = validPosition(params["mlat"]?.toDoubleOrNull(), params["mlon"]?.toDoubleOrNull())
    }
    for (key in listOf("q", "query", "destination", "daddr", "ll")) {
        val value = params[key]?.trim()?.removePrefix("loc:")?.trim() ?: continue
        if (value.isEmpty()) continue
        val asPosition = latLonText(value)
        if (asPosition != null) {
            if (position == null) position = asPosition.first
            if (name == null) name = asPosition.second
        } else if (query.isEmpty() && key != "ll") {
            query = value
        }
    }
    if (position == null && name == null && query.isEmpty()) {
        // Only the middle of the map shown: better than nothing.
        position = GOOGLE_AT.find(link)?.let { validPosition(it.groupValues[1].toDoubleOrNull(), it.groupValues[2].toDoubleOrNull()) }
            ?: OSM_HASH.find(link)?.let { validPosition(it.groupValues[1].toDoubleOrNull(), it.groupValues[2].toDoubleOrNull()) }
    }
    if (position == null && name == null && query.isEmpty()) return null
    return SharedPlace((query.ifEmpty { name.orEmpty() }).take(MAX_QUERY_CHARS), position, name ?: query.takeIf { it.isNotEmpty() })
}

/**
 * A shared text as a place: a map link in it is read where it says where the place is (short
 * links as maps.app.goo.gl cannot be followed offline); otherwise the text before the link is
 * the search. Of a share as "Name\nStreet 1, 12345 Town\nhttps://…" the address is searched for
 * and the first line is the name. Null for a text with nothing to search for.
 */
fun parseSharedText(text: String): SharedPlace? {
    val links = URL_PATTERN.findAll(text).map { it.value.trimEnd('.', ',', ')', '>', '"') }.toList()
    val firstLink = URL_PATTERN.find(text)
    val before = (if (firstLink == null) text else text.substring(0, firstLink.range.first))
    val lines = before.lines().map { it.trim().replace(TRAILING_JUNK, "").trim() }.filter { it.isNotEmpty() }
    val textName = lines.firstOrNull()
    // The line that looks like an address (it has a number) is the best search; else all of it.
    val textQuery = when {
        lines.isEmpty() -> ""
        lines.size == 1 -> lines[0]
        else -> lines.drop(1).firstOrNull { line -> line.any { it.isDigit() } } ?: lines.joinToString(", ")
    }.take(MAX_QUERY_CHARS)
    val fromLink = links.firstNotNullOfOrNull { parseMapLink(it) }
    if (fromLink != null) {
        return SharedPlace(
            query = textQuery.ifEmpty { fromLink.query },
            position = fromLink.position,
            name = textName ?: fromLink.name,
        )
    }
    if (textQuery.isEmpty()) return null
    return SharedPlace(textQuery, null, textName)
}
