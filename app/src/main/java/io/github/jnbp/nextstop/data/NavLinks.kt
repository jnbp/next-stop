package io.github.jnbp.nextstop.data

import java.net.URLDecoder
import java.util.Locale

/*
 * "Navigate here" of other apps: a link that names where to go, read as the destination of a
 * connection from where the user is. Other apps hand it over in three forms, all declared in
 * the manifest:
 *
 * - geo: (RFC 5870 and the Android form, read by [parseGeoUri]): "geo:52.5,13.4",
 *   "geo:0,0?q=Street 1, Town", "geo:0,0?q=52.5,13.4(Name)".
 * - google.navigation: (the navigation link of Google Maps that many apps send for "Navigate"):
 *   "google.navigation:q=Street 1, Town", "google.navigation:q=52.5,13.4", "google.navigation:ll=52.5,13.4".
 * - Links of Google Maps on maps.google.com, and on www.google.com under /maps: /maps/dir/ (the
 *   last stop, or destination=/daddr=), /maps/place/<name>/ (its exact point "!3d…!4d…" where
 *   given), /maps/search/<text>, ?q=, and "/@lat,lng" (only the middle of the map shown, so used
 *   only when nothing else names a place).
 *
 * Short links (maps.app.goo.gl/…, goo.gl/maps/…) name nothing until they are followed; see
 * [isShortMapLink] and followShortMapLink. The result is a [SharedPlace]: a point (with the name the
 * link gave it) is routed to at once, a text is searched for and the user picks the place.
 * Nothing here touches Android, so it is tested on the JVM (NavLinksTest).
 */

// The longest destination text taken from a link; a longer one is cut, as a shared text is.
private const val NAV_QUERY_MAX_CHARS = 160

// The scheme of the navigation links of Google Maps, with its colon.
private const val NAVIGATION_SCHEME = "google.navigation:"

// Hosts where every path is a map.
private val MAPS_ONLY_HOSTS = setOf("maps.google.com", "maps.google.de")

// Hosts where only the paths under /maps are maps (the rest is web search and other services).
private val GOOGLE_HOSTS = setOf("www.google.com", "google.com", "www.google.de", "google.de")

// The hosts of short links of Google Maps.
private const val MAPS_SHORT_HOST = "maps.app.goo.gl"
private const val GOO_GL_HOST = "goo.gl"

// The cookie question Google puts in front of a link in the EU; the link itself is its "continue".
private const val CONSENT_HOST = "consent.google.com"

// Scheme, host (without port or user), path, and the rest (query and fragment) of a web link.
private val WEB_LINK = Regex("""(?i)^https?://(?:[^/?#@]*@)?([^/?#:]+)(?::\d+)?([^?#]*)(.*)$""", RegexOption.DOT_MATCHES_ALL)

// Two numbers, as a point is written; one that is no valid point names no place either.
private val NUMBER_PAIR = Regex("""^\s*-?\d+(?:\.\d+)?\s*,\s*-?\d+(?:\.\d+)?\s*$""")

// The stops of a route in daddr are joined by "to:"; the last one is the destination.
private val DADDR_STOPS = Regex("""\s+to:\s*""")

/** Host (in lower case) and path of an http(s) link; null for any other text. */
private fun webHostPath(link: String): Pair<String, String>? {
    val match = WEB_LINK.matchEntire(link.trim()) ?: return null
    return match.groupValues[1].lowercase(Locale.ROOT) to match.groupValues[2]
}

private fun decodePathPart(text: String): String = try {
    URLDecoder.decode(text, "UTF-8")
} catch (e: IllegalArgumentException) {
    text
}

/**
 * True for a short link of Google Maps (maps.app.goo.gl/…, goo.gl/maps/…): it names no place
 * by itself and has to be followed first (see followShortMapLink).
 */
fun isShortMapLink(link: String): Boolean {
    val (host, path) = webHostPath(link) ?: return false
    return when (host) {
        MAPS_SHORT_HOST -> path.trim('/').isNotEmpty()
        GOO_GL_HOST -> path.startsWith("/maps/") && path.length > "/maps/".length
        else -> false
    }
}

/** True for a web link of Google Maps that [parseNavLink] reads (not a short link). */
fun isGoogleMapsLink(link: String): Boolean {
    val (host, path) = webHostPath(link) ?: return false
    return host in MAPS_ONLY_HOSTS || (host in GOOGLE_HOSTS && (path == "/maps" || path.startsWith("/maps/")))
}

/**
 * The link behind the cookie question of Google (consent.google.com/…?continue=<link>), which a
 * short link can lead to in the EU; any other link as it is.
 */
fun unwrapConsentLink(link: String): String {
    val (host, _) = webHostPath(link) ?: return link
    if (host != CONSENT_HOST) return link
    return linkParams(link)["continue"]?.trim()?.takeIf { it.isNotEmpty() } ?: link
}

/** A destination written as text: a point ("52.5,13.4", "52.5,13.4(Name)") or a place to search for. */
private fun navTarget(text: String): SharedPlace? {
    val value = text.trim().removePrefix("loc:").trim()
    if (value.isEmpty()) return null
    val point = latLonText(value)
    if (point != null) return SharedPlace(point.second.orEmpty().take(NAV_QUERY_MAX_CHARS), point.first, point.second)
    // "0,0" and other numbers off the globe stand for no place, not for a search.
    if (NUMBER_PAIR.matches(value)) return null
    return SharedPlace(value.take(NAV_QUERY_MAX_CHARS), null, value)
}

/** google.navigation:q=…, google.navigation:ll=lat,lng (also with "//" or "?" after the colon). */
private fun parseNavigationUri(text: String): SharedPlace? {
    val body = text.substring(NAVIGATION_SCHEME.length).removePrefix("//").removePrefix("?")
    val params = linkParams("?$body")
    val byQuery = params["q"]?.let { navTarget(it) }
    val point = params["ll"]?.let { latLonText(it) }?.first
    return when {
        byQuery?.position != null -> byQuery
        // A text and a point: the point, named by the text.
        byQuery != null && point != null -> SharedPlace(byQuery.query, point, byQuery.name)
        byQuery != null -> byQuery
        point != null -> SharedPlace("", point, null)
        else -> null
    }
}

/** The last stop of a route link /maps/dir/<start>/<stop>/…/<destination>/@…/data=…, decoded. */
private fun dirDestination(path: String): String? = path.substringAfter("/maps/dir/", "")
    .split('/')
    .filter { it.isNotBlank() && !it.startsWith("@") && !it.startsWith("data=") && !it.startsWith("am=") }
    .lastOrNull()
    ?.let { decodePathPart(it).trim() }
    ?.takeIf { it.isNotEmpty() }

/** The text of a search link /maps/search/<text>/…, decoded. */
private fun searchSegment(path: String): String? = path.substringAfter("/maps/search/", "")
    .split('/')
    .firstOrNull()
    ?.takeIf { it.isNotBlank() && !it.startsWith("@") }
    ?.let { decodePathPart(it).trim() }
    ?.takeIf { it.isNotEmpty() }

private fun parseGoogleMapsUrl(link: String, path: String): SharedPlace? {
    val params = linkParams(link)
    // The destination of a route is said outright: it wins over any other place in the link.
    val destination = params["destination"] ?: params["daddr"]
    if (!destination.isNullOrBlank()) return navTarget(destination.split(DADDR_STOPS).last())
    if (path.startsWith("/maps/dir")) {
        // The middle of the map of a route is not where it leads: without a destination, nothing.
        return dirDestination(path)?.let { navTarget(it) }
    }
    if (params["q"] == null && params["query"] == null) {
        searchSegment(path)?.let { segment -> return navTarget(segment) }
    }
    return parseMapLink(link)?.let { it.copy(query = it.query.take(NAV_QUERY_MAX_CHARS)) }
}

/**
 * Where a "navigate here" link of another app leads: a geo: link, a google.navigation: link or
 * a web link of Google Maps (see the comment at the top of this file). Null for a link that
 * names no place or is of another kind, also for a short link, which has to be followed first.
 */
fun parseNavLink(link: String): SharedPlace? {
    val text = unwrapConsentLink(link.trim())
    return when {
        text.startsWith("geo:", ignoreCase = true) -> parseGeoUri(text)
        text.startsWith(NAVIGATION_SCHEME, ignoreCase = true) -> parseNavigationUri(text)
        isGoogleMapsLink(text) -> webHostPath(text)?.let { parseGoogleMapsUrl(text, it.second) }
        else -> null
    }
}
