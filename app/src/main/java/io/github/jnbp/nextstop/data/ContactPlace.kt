package io.github.jnbp.nextstop.data

import android.content.Context
import android.location.Geocoder
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the postal address of a contact lies. The whole address (street, postcode, town,
 * country) is searched without any bias towards the user's area, and a hit only counts when
 * its postcode or its town agrees with the contact's: a street of the same name elsewhere is
 * never taken. In turn: the place search of the providers, the geocoder of the device, and
 * at last the town alone, whose name then carries [approximateNote]. Null when none of these
 * finds the place.
 */
suspend fun resolveContactPlace(
    context: Context,
    source: TransitSource,
    contact: ContactAddress,
    approximateNote: String,
): Stop? {
    val postcode = contact.postcode
    val city = contact.city
    val query = contactQuery(contact)
    // Without a postcode and a town there is nothing to check a hit against: the written
    // address is resolved as before.
    if (postcode == null && city == null) return source.resolvePlace(query)

    val covered: (Double, Double) -> Boolean = (source as? RoutingSource)?.let { routing -> routing::covers }
        ?: { _, _ -> true }

    suspend fun search(text: String): List<Stop> = try {
        source.searchStops(text, 8).filter { place -> place.position?.let { covered(it.first, it.second) } == true }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    // 1. The providers, with the whole address and with street and town alone.
    val streetForm = listOfNotNull(contact.street, listOfNotNull(postcode, city).joinToString(" ").ifEmpty { null })
        .joinToString(", ")
    for (form in listOf(query, streetForm).filter { it.isNotBlank() }.distinct()) {
        val pick = pickMatching(search(form), postcode, city)
        if (pick != null) return pick
    }

    // 2. The geocoder of the device, with the whole address.
    geocode(context, query, postcode, city)?.let { (latitude, longitude) ->
        if (covered(latitude, longitude)) {
            return Stop(
                id = "address:$latitude,$longitude",
                name = contact.street?.let { street -> listOfNotNull(street, city).joinToString(", ") } ?: contact.address,
                latitude = latitude,
                longitude = longitude,
                distanceMeters = null,
                kind = PlaceKind.Address,
            )
        }
    }

    // 3. The town alone, marked as an approximate place.
    if (city != null || postcode != null) {
        val townQuery = listOfNotNull(postcode, city, contact.country).joinToString(" ")
        val town = geocode(context, townQuery, postcode, city)
            ?: pickMatching(search(listOfNotNull(postcode, city).joinToString(" ")), postcode, city)?.position
        if (town != null && covered(town.first, town.second)) {
            val label = city ?: postcode ?: contact.address
            return Stop(
                id = "address:${town.first},${town.second}",
                name = "$label ($approximateNote)",
                latitude = town.first,
                longitude = town.second,
                distanceMeters = null,
                kind = PlaceKind.Address,
            )
        }
    }
    return null
}

/** The whole address of a contact for a search: street, postcode and town, and the country. */
fun contactQuery(contact: ContactAddress): String {
    val town = listOfNotNull(contact.postcode, contact.city).joinToString(" ").ifEmpty { null }
    val parts = listOfNotNull(contact.street, town, contact.country)
    return if (parts.isEmpty()) contact.address else parts.joinToString(", ")
}

/** The first of [candidates] that agrees with the [postcode] or the [city] of an address. */
fun pickMatching(candidates: List<Stop>, postcode: String?, city: String?): Stop? =
    candidates.firstOrNull { place ->
        place.position != null && addressAgrees(place.postcode, listOf(place.name) + place.areas, postcode, city)
    }

/**
 * True when a found place agrees with the address it was searched for: its postcode
 * ([foundPostcode], or one written in [foundTexts]) is the [postcode], or the [city] stands
 * in [foundTexts] as whole words. Case, accents and umlauts do not count. With neither a
 * postcode nor a town to compare, nothing agrees.
 */
fun addressAgrees(foundPostcode: String?, foundTexts: List<String>, postcode: String?, city: String?): Boolean {
    val wantedCode = postcode?.filter { it.isLetterOrDigit() }?.lowercase()?.takeIf { it.isNotEmpty() }
    val wantedTown = city?.let { words(it) }?.takeIf { it.isNotEmpty() }
    if (wantedCode == null && wantedTown == null) return false
    val textWords = foundTexts.map { words(it) }
    if (wantedCode != null) {
        val code = foundPostcode?.filter { it.isLetterOrDigit() }?.lowercase()
        if (code == wantedCode) return true
        if (textWords.any { wantedCode in it }) return true
    }
    if (wantedTown != null && textWords.any { containsRun(it, wantedTown) }) return true
    return false
}

/** The words of a text, in lower case, with umlauts written out and accents taken off. */
private fun words(text: String): List<String> {
    val written = text.lowercase(Locale.ROOT)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    val plain = Normalizer.normalize(written, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    return plain.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
}

/** True when [run] stands in [list] as consecutive words. */
private fun containsRun(list: List<String>, run: List<String>): Boolean {
    if (run.size > list.size) return false
    for (start in 0..list.size - run.size) {
        if (run.indices.all { list[start + it] == run[it] }) return true
    }
    return false
}

/**
 * The position the device's geocoder gives for [query], taking only a result that agrees
 * with [postcode] or [city]. Null without a geocoder, without network or without such a result.
 */
private suspend fun geocode(context: Context, query: String, postcode: String?, city: String?): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(query, 5).orEmpty()
            results.firstOrNull { address ->
                address.hasLatitude() && address.hasLongitude() &&
                    addressAgrees(
                        address.postalCode,
                        listOfNotNull(address.locality, address.subAdminArea, address.subLocality),
                        postcode,
                        city,
                    )
            }?.let { it.latitude to it.longitude }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
