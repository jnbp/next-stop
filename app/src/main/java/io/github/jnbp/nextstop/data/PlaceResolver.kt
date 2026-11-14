package io.github.jnbp.nextstop.data

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// A place found by name counts as the address written beside it only this close to it.
private const val ADDRESS_MATCH_METERS = 1_000.0

// A postcode of five digits at the start of a part, as in "12345 Town".
private val POSTCODE = Regex("^\\d{5}\\b")

/**
 * The place a free text stands for, as calendars, contacts and maps write it: for example
 * "Venue, Street 1, 12345 Town, Country". The provider's search often finds nothing for the
 * whole text, or a point of interest it cannot plan a journey to, so several forms are tried
 * in turn: the text without the country, the part before the first comma (the venue), and
 * "street, postcode town". The first result whose position is plausible is taken: it has a
 * position, a provider in use covers it, and where the text names a street it lies near
 * that street address. Without such a result the street address itself is taken. Null when
 * nothing was found; a failed request counts as nothing found for that form.
 */
suspend fun TransitSource.resolvePlace(text: String): Stop? {
    val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    // A last part without digits after at least two others is the country.
    val withoutCountry = if (parts.size >= 3 && parts.last().none { it.isDigit() }) parts.dropLast(1) else parts
    val full = withoutCountry.joinToString(", ")
    val venue = parts.first().takeIf { withoutCountry.size >= 2 && it.none { char -> char.isDigit() } }
    val postcodeAt = withoutCountry.indexOfFirst { POSTCODE.containsMatchIn(it) }
    val street = if (postcodeAt >= 1) "${withoutCountry[postcodeAt - 1]}, ${withoutCountry[postcodeAt]}" else null

    val covered: (Double, Double) -> Boolean = (this as? RoutingSource)?.let { routing -> routing::covers }
        ?: { _, _ -> true }
    val asked = mutableMapOf<String, List<Stop>>()
    suspend fun search(query: String): List<Stop> = asked.getOrPut(query) {
        try {
            searchStops(query, 6).filter { place -> place.position?.let { covered(it.first, it.second) } == true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // The street address anchors the others, when the text names one apart from a venue.
    val anchor = street?.takeIf { it != full || venue != null }?.let { form ->
        val found = search(form)
        found.firstOrNull { it.kind == PlaceKind.Address } ?: found.firstOrNull()
    }
    val forms = listOfNotNull(full, venue, street).distinct()
    for (form in forms) {
        val found = search(form)
        val pick = found.firstOrNull { place ->
            val position = place.position ?: return@firstOrNull false
            val near = anchor?.position ?: return@firstOrNull true
            groundMeters(position, near) <= ADDRESS_MATCH_METERS
        }
        if (pick != null) return pick
    }
    return anchor
}

/** True when a text reads like a whole postal address: parts split by commas and a postcode. */
fun looksLikeAddress(text: String): Boolean =
    text.contains(',') && text.split(',').any { POSTCODE.containsMatchIn(it.trim()) }

/** Distance along the ground between two positions, in metres. */
private fun groundMeters(from: Pair<Double, Double>, to: Pair<Double, Double>): Double {
    val lat1 = Math.toRadians(from.first)
    val lat2 = Math.toRadians(to.first)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(to.second - from.second)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}
