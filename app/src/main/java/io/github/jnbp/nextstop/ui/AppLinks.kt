package io.github.jnbp.nextstop.ui

import android.content.Intent
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.SharedPlace
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.isShortMapLink
import io.github.jnbp.nextstop.data.parseNavLink
import io.github.jnbp.nextstop.data.parseSharedText
import io.github.jnbp.nextstop.widget.WidgetLaunch

/** What a link nextstop://open/... asks of the app (see [AppLinks]). */
sealed interface OpenLink {
    /** The search of a connection to [to], from [from] or, when that is null, from where the user is. */
    data class Route(val from: Stop?, val to: Stop) : OpenLink

    /** The departures of [stop], or of the stops where the user is when that is null. */
    data class Departures(val stop: Stop?) : OpenLink
}

/**
 * The deep links of the app, all under nextstop://open (declared in the manifest):
 *
 * - nextstop://open: just opens the app (the watch uses it).
 * - nextstop://open/route?fromName=…&from=lat,lon&toName=…&to=lat,lon: the search between two
 *   places ("In Next Stop öffnen" of web/route, the launcher shortcuts). Without a start the
 *   search starts where the user is. fromId/toId and fromKind/toKind keep a place exact.
 * - nextstop://open/departures?stopId=…&name=…&lat=…&lon=…: the departures of a stop (a stop
 *   pinned to the home screen); without stopId the departures where the user is.
 *
 * Besides its own links the app takes "navigate here" links of other apps (geo:,
 * google.navigation:, links of Google Maps; see NavLinks.kt): the connection from where the
 * user is to the place they name, or the search for it when they name it only as a text.
 *
 * What a link asks is handed to the page through the flows here, each cleared once taken.
 */
object AppLinks {
    const val SCHEME = "nextstop"
    const val HOST = "open"
    const val PATH_ROUTE = "route"
    const val PATH_DEPARTURES = "departures"

    /** Search a connection. */
    val route = MutableStateFlow<OpenLink.Route?>(null)

    /** Show the departures where the user is. */
    val departuresHere = MutableStateFlow(false)

    /** A place another app shared (a text) or a link named only as a text: the destination of a new search, to confirm. */
    val sharedPlace = MutableStateFlow<SharedPlace?>(null)

    /**
     * Where a "navigate here" link of another app leads: a point is routed to at once from where
     * the user is, a text opens the search for it (see AppRoot).
     */
    val navigateTo = MutableStateFlow<SharedPlace?>(null)

    /** A short link of Google Maps to follow first (see followShortMapLink); then as [navigateTo]. */
    val shortMapLink = MutableStateFlow<String?>(null)

    /** Takes what a link in [intent] asks for; false when it is no link of the app. */
    fun follow(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
                ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
                ?: return false
            sharedPlace.value = parseSharedText(text) ?: return false
            return true
        }
        if (intent.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        if (data.scheme == SCHEME) {
            if (data.host != HOST) return false
        } else {
            return followNavLink(data.scheme, data.toString())
        }
        val link = parseOpenLink(data.path.orEmpty(), queryParams(data.encodedQuery)) ?: return false
        when (link) {
            is OpenLink.Route -> route.value = link
            is OpenLink.Departures -> {
                val stop = link.stop
                if (stop != null) WidgetLaunch.openStop.value = stop else departuresHere.value = true
            }
        }
        return true
    }

    // A "navigate here" link of another app; one of a scheme the manifest takes for it that
    // names no place opens the empty search for a destination rather than nothing.
    private fun followNavLink(scheme: String?, link: String): Boolean {
        val kind = scheme?.lowercase(java.util.Locale.ROOT) ?: return false
        if (kind !in NAV_LINK_SCHEMES) return false
        if (isShortMapLink(link)) {
            shortMapLink.value = link
            return true
        }
        navigateTo.value = parseNavLink(link) ?: SharedPlace("", null, null)
        return true
    }
}

// The schemes of the "navigate here" links the manifest declares (all but the app's own).
private val NAV_LINK_SCHEMES = setOf("geo", "google.navigation", "http", "https")

/**
 * The parameters of an encoded query, decoded as a form (a "+" is a space, as URLSearchParams
 * of the web pages writes it). The first of a repeated key counts.
 */
internal fun queryParams(encoded: String?): Map<String, String> {
    if (encoded.isNullOrEmpty()) return emptyMap()
    val params = LinkedHashMap<String, String>()
    encoded.split('&').forEach { part ->
        if (part.isEmpty()) return@forEach
        val equals = part.indexOf('=')
        val key = decodeQueryPart(if (equals < 0) part else part.substring(0, equals))
        val value = if (equals < 0) "" else decodeQueryPart(part.substring(equals + 1))
        if (key.isNotEmpty() && key !in params) params[key] = value
    }
    return params
}

private fun decodeQueryPart(text: String): String = try {
    URLDecoder.decode(text, "UTF-8")
} catch (e: IllegalArgumentException) {
    text
}

private fun encodeQueryPart(text: String): String = URLEncoder.encode(text, "UTF-8")

/** "52.52,13.40" as latitude and longitude; null for anything else or a point off the globe. */
internal fun parseLatLon(text: String?): Pair<Double, Double>? {
    val parts = text?.split(',') ?: return null
    if (parts.size != 2) return null
    return checkedLatLon(parts[0].trim().toDoubleOrNull(), parts[1].trim().toDoubleOrNull())
}

private fun checkedLatLon(latitude: Double?, longitude: Double?): Pair<Double, Double>? {
    if (latitude == null || longitude == null) return null
    // NaN is in no range, so it is refused here too.
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return latitude to longitude
}

/**
 * A place of a link: by its [id] where the link names one, else a point at [position] that the
 * search routes to by its coordinates. Null when the link gives neither.
 */
internal fun linkPlace(name: String?, position: Pair<Double, Double>?, id: String?, kind: String?): Stop? {
    val knownId = id?.trim()?.takeIf { it.isNotEmpty() }
    if (position == null && knownId == null) return null
    val label = name?.trim()?.takeIf { it.isNotEmpty() }
        ?: position?.let { "%.5f, %.5f".format(java.util.Locale.ROOT, it.first, it.second) }
        ?: knownId
        ?: return null
    val placeKind = PlaceKind.entries.firstOrNull { it.name == kind }
        ?: if (knownId == null) PlaceKind.Address else PlaceKind.Stop
    return Stop(
        id = knownId ?: "pin:${position?.first},${position?.second}",
        name = label,
        latitude = position?.first,
        longitude = position?.second,
        distanceMeters = null,
        kind = placeKind,
    )
}

/** What the link with [path] (as "/route") and the decoded [params] asks for; null for an unknown one. */
internal fun parseOpenLink(path: String, params: Map<String, String>): OpenLink? = when (path.trim('/')) {
    AppLinks.PATH_ROUTE -> {
        val to = linkPlace(params["toName"], parseLatLon(params["to"]), params["toId"], params["toKind"])
        if (to == null) {
            null
        } else {
            val from = linkPlace(params["fromName"], parseLatLon(params["from"]), params["fromId"], params["fromKind"])
            OpenLink.Route(from, to)
        }
    }
    AppLinks.PATH_DEPARTURES -> {
        val id = params["stopId"]?.trim()?.takeIf { it.isNotEmpty() }
        if (id == null) {
            OpenLink.Departures(null)
        } else {
            val position = checkedLatLon(params["lat"]?.toDoubleOrNull(), params["lon"]?.toDoubleOrNull())
            OpenLink.Departures(
                Stop(
                    id = id,
                    name = params["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: id,
                    latitude = position?.first,
                    longitude = position?.second,
                    distanceMeters = null,
                    kind = PlaceKind.Stop,
                ),
            )
        }
    }
    else -> null
}

/** A link of the app with [path] and the parameters that have a value. */
private fun openLinkText(path: String, params: List<Pair<String, String?>>): String {
    val query = params.mapNotNull { (key, value) -> value?.let { encodeQueryPart(key) + "=" + encodeQueryPart(it) } }
    val base = "${AppLinks.SCHEME}://${AppLinks.HOST}/$path"
    return if (query.isEmpty()) base else base + "?" + query.joinToString("&")
}

// The parameters of one end of a route link. A point of the map or the own position has no id
// worth keeping: only its coordinates go into the link.
private fun routeEnd(prefix: String, place: Stop): List<Pair<String, String?>> {
    val keepId = !place.id.startsWith("pin:") && place.id != CURRENT_LOCATION_ID
    return listOf(
        "${prefix}Name" to place.name,
        prefix to place.position?.let { "${it.first},${it.second}" },
        "${prefix}Id" to place.id.takeIf { keepId },
        "${prefix}Kind" to place.kind.name.takeIf { keepId },
    )
}

/** nextstop://open/route to [to], from [from] or, when that is null or the own position, from where the user is. */
internal fun routeLinkText(from: Stop?, to: Stop): String {
    val start = from?.takeIf { it.id != CURRENT_LOCATION_ID }
    return openLinkText(AppLinks.PATH_ROUTE, (start?.let { routeEnd("from", it) } ?: emptyList()) + routeEnd("to", to))
}

/** nextstop://open/departures of [stop], or of where the user is when that is null. */
internal fun departuresLinkText(stop: Stop?): String = openLinkText(
    AppLinks.PATH_DEPARTURES,
    if (stop == null) {
        emptyList()
    } else {
        listOf(
            "stopId" to stop.id,
            "name" to stop.name,
            "lat" to stop.latitude?.toString(),
            "lon" to stop.longitude?.toString(),
        )
    },
)
