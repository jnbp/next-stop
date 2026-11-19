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
 * never taken. A stop is never taken for the address, even one named after its street: the
 * journey has to end at the house, with a walk from the last stop. In turn: an address of the
 * providers with the street and the house number, the geocoder of the device, the best other
 * address of the providers in that street, and at last the town alone, whose name then
 * carries [approximateNote]. The place found is named after the contact's address (see
 * [contactPlaceName]), not after the hit. An address kept only in its written form is split
 * into its fields first (see contactWithFields, #58). A provider that fails counts as one that
 * found nothing. Null when none of these finds the place.
 */
suspend fun resolveContactPlace(
    context: Context,
    source: TransitSource,
    given: ContactAddress,
    approximateNote: String,
): Stop? {
    val contact = contactWithFields(given)
    val postcode = contact.postcode
    val city = contact.city
    val query = contactQuery(contact)
    val covered: (Double, Double) -> Boolean = (source as? RoutingSource)?.let { routing -> routing::covers }
        ?: { _, _ -> true }

    suspend fun search(text: String): List<Stop> = try {
        source.searchStops(text, 8).filter { place -> place.position?.let { covered(it.first, it.second) } == true }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    fun named(found: Pair<Double, Double>) = Stop(
        id = "address:${found.first},${found.second}",
        name = contactPlaceName(contact),
        latitude = found.first,
        longitude = found.second,
        distanceMeters = null,
        kind = PlaceKind.Address,
    )

    // Without a postcode and a town there is nothing to check a hit against: the written
    // address is resolved as any other address, still never as a stop; failing that, the
    // device's geocoder takes the written address as it is.
    if (postcode == null && city == null) {
        val resolved = try {
            source.resolvePlace(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        resolved
            ?.takeIf { it.kind != PlaceKind.Stop }
            ?.position
            ?.let { return named(it) }
        return geocode(context, query, null, null)
            ?.takeIf { (latitude, longitude) -> covered(latitude, longitude) }
            ?.let { named(it) }
    }

    // 1. The providers, with the whole address and with street and town alone: only an
    // address with the street and its house number counts here.
    val streetForm = listOfNotNull(contact.street, listOfNotNull(postcode, city).joinToString(" ").ifEmpty { null })
        .joinToString(", ")
    val forms = listOf(query, streetForm).filter { it.isNotBlank() }.distinct()
    val found = mutableListOf<Stop>()
    for (form in forms) {
        val results = search(form)
        found += results
        pickAddress(results, contact.street, postcode, city, exact = true)?.position?.let { return named(it) }
    }

    // 2. The geocoder of the device, with the whole address.
    geocode(context, query, postcode, city)?.let { (latitude, longitude) ->
        if (covered(latitude, longitude)) return named(latitude to longitude)
    }

    // 2b. The best other address the providers found in that street or town; never a stop.
    pickAddress(found, contact.street, postcode, city, exact = false)?.position?.let { return named(it) }

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

/**
 * The name a place found for a contact address carries: the street with its house number and
 * the town (as "Musterstraße 12, Musterstadt"), or the written address without those fields.
 */
fun contactPlaceName(contact: ContactAddress): String =
    contact.street?.let { street -> listOfNotNull(street, contact.city).joinToString(", ") } ?: contact.address

/**
 * The place among [candidates] that stands for the street address [street] (as
 * "Musterstraße 12") in [postcode] / [city]. A stop never does, even one named after the
 * street; a found place has to agree with the postcode or the town where these are known.
 * With [exact], only an address or a point with the street's name and its house number
 * counts. Otherwise the best match is taken: such an exact one, else an address in that
 * street, else any address. The first of equally good ones wins. Null when none fits.
 */
fun pickAddress(candidates: List<Stop>, street: String?, postcode: String?, city: String?, exact: Boolean): Stop? {
    val number = street?.let { houseNumberOf(it) }
    val streetWords = street?.let { streetWordsOf(it) }.orEmpty()
    fun score(place: Stop): Int {
        if (place.position == null || place.kind == PlaceKind.Stop) return 0
        val texts = listOfNotNull(place.name, place.detail) + place.areas
        if ((postcode != null || city != null) && !addressAgrees(place.postcode, texts, postcode, city)) return 0
        val textWords = texts.map { streetSpelling(words(it)) }
        val inStreet = streetWords.isNotEmpty() && textWords.any { containsRun(it, streetWords) }
        // The street and its number in one text: a number elsewhere (a postcode part, another
        // field) does not make the place that house.
        val sameHouse = number != null && textWords.any { containsRun(it, streetWords) && number in it }
        return when {
            inStreet && sameHouse -> 3
            exact -> 0
            place.kind != PlaceKind.Address -> 0
            inStreet -> 2
            else -> 1
        }
    }
    var best: Stop? = null
    var bestScore = 0
    for (place in candidates) {
        val score = score(place)
        if (score > bestScore) {
            best = place
            bestScore = score
        }
    }
    return best
}

/**
 * The house number at the end of a street address ("12", "12a", "12-14", also right after an
 * abbreviation as in "Musterstr.12"), in lower case; null without one.
 */
fun houseNumberOf(street: String): String? =
    Regex("(?:\\s|(?<=\\.))(\\d+\\s?[a-zA-Z]?(?:\\s?[-/]\\s?\\d+\\s?[a-zA-Z]?)?)\\s*$").find(street.trim())
        ?.groupValues?.get(1)?.let { words(it).firstOrNull() }

/** The words of a street address without its house number. */
private fun streetWordsOf(street: String): List<String> {
    val number = Regex("(?:\\s|(?<=\\.))\\d.*$").replace(street.trim(), "")
    return streetSpelling(words(number))
}

/** Street words with "str" / "str." written out, so "Musterstr. 12" is "Musterstraße 12". */
private fun streetSpelling(list: List<String>): List<String> =
    list.map { word -> if (word.endsWith("str")) word + "asse" else word }

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
 * with [postcode] or [city]; with neither known, the first result. Null without a geocoder,
 * without network or without such a result.
 */
private suspend fun geocode(context: Context, query: String, postcode: String?, city: String?): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            @Suppress("DEPRECATION")
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(query, 5).orEmpty()
            results.firstOrNull { address ->
                address.hasLatitude() && address.hasLongitude() &&
                    (
                        (postcode == null && city == null) || addressAgrees(
                            address.postalCode,
                            listOfNotNull(address.locality, address.subAdminArea, address.subLocality),
                            postcode,
                            city,
                        )
                        )
            }?.let { it.latitude to it.longitude }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
