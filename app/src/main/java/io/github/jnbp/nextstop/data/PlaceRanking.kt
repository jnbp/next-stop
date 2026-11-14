package io.github.jnbp.nextstop.data

import java.text.Normalizer

// Words of a query that only name the town or the country: a place need not repeat them in its name.
private val TOWN_WORDS = setOf("berlin", "potsdam", "brandenburg", "deutschland", "germany", "de")

// Within this distance a place counts as near and comes before others that match as well.
private const val NEAR_METERS = 3_000
private const val CLOSE_METERS = 15_000

// Places within this distance of the user (or the map) lie in their region and come before all
// places outside it that answer the query; "Hauptbahnhof" finds the main station nearby first.
const val REGION_METERS = 50_000

/**
 * The words of a text in one form for comparing: small letters, umlauts and ß spelled out,
 * accents dropped, and "straße", "strasse" and "str." at the end of a word all as "str", so
 * "Gartenstr" finds "Gartenstraße".
 */
fun searchTokens(text: String): List<String> {
    val folded = text.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    val plain = Normalizer.normalize(folded, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    return plain.split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() }
        .map { word -> word.replace(Regex("(strasse|str)$"), "str") }
}

/**
 * How well [place] answers [query]: the share of the words of the query found at the start of
 * a word of its name, its second line or its areas, plus a little for a name that begins with
 * the query or is the query, and for a place near [near]. Words that only name the town count
 * when the place has them and are left out otherwise. Below zero when not every word is found.
 */
fun placeScore(query: List<String>, place: Stop, near: Pair<Double, Double>?): Double {
    if (query.isEmpty()) return 0.0
    // A stop is compared without the signs of the network and "Bhf" ("S+U Central Bhf" as "Central").
    val nameWords = searchTokens(if (place.kind == PlaceKind.Stop) place.name.briefStopName() else place.name.shortStopName())
    val otherWords = searchTokens(listOfNotNull(place.detail, place.postcode).joinToString(" ") + " " + place.areas.joinToString(" "))
    val words = nameWords + otherWords
    var found = 0
    var counted = 0
    var inName = 0
    for (token in query) {
        val hit = words.any { it.startsWith(token) }
        if (token in TOWN_WORDS && !hit) continue
        counted++
        if (hit) found++
        if (nameWords.any { it.startsWith(token) }) inName++
    }
    if (counted == 0) return 0.0
    var score = found.toDouble() / counted
    if (found < counted) score -= 1.0
    // The name itself, rather than its address, holds the words.
    score += 0.3 * inName / counted
    val joinedName = nameWords.joinToString(" ")
    val joinedQuery = query.filter { it !in TOWN_WORDS }.joinToString(" ")
    if (joinedQuery.isNotEmpty() && joinedName == joinedQuery) {
        score += 0.4
    } else if (joinedQuery.isNotEmpty() && joinedName.startsWith(joinedQuery)) {
        score += 0.25
    }
    // Where two places answer alike, a stop comes first, and a place of a transit provider
    // before the same kind of place from OpenStreetMap.
    if (place.kind == PlaceKind.Stop) score += 0.1
    if (isOsmPlace(place)) score -= 0.05
    val distance = place.distanceMeters?.toDouble()
        ?: near?.let { from -> place.position?.let { groundDistance(from, it) } }
    if (distance != null) {
        score += when {
            distance <= NEAR_METERS -> 0.2
            distance <= CLOSE_METERS -> 0.1
            else -> 0.0
        }
    }
    return score
}

/**
 * Which group of the ranking a place with [score] falls in: 0 inside the region around
 * [region] (see [REGION_METERS]), 1 where its distance is not known, 2 outside, and 3 for a
 * place that does not hold every word of the query, wherever it is.
 */
fun regionTier(score: Double, place: Stop, region: Pair<Double, Double>?): Int {
    if (score < 0) return 3
    val distance = region?.let { from -> place.position?.let { groundDistance(from, it) } }
        ?: place.distanceMeters?.toDouble()
    return when {
        distance == null -> 1
        distance <= REGION_METERS -> 0
        else -> 2
    }
}

/**
 * [places] in the order they answer [query] best: the places of the region around [region]
 * first (see [regionTier]), within each group by [placeScore], so an exact name in the region
 * leads. Places that rank the same keep the order the providers gave them.
 */
fun rankPlaces(
    query: String,
    places: List<Stop>,
    near: Pair<Double, Double>?,
    region: Pair<Double, Double>? = near,
): List<Stop> {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return places
    class Ranked(val place: Stop, val index: Int, val score: Double, val tier: Int)
    return places.mapIndexed { index, place ->
        // Without the position of the user, the middle of the region tells near from far.
        val score = placeScore(tokens, place, near ?: region)
        Ranked(place, index, score, regionTier(score, place, region))
    }
        .sortedWith(compareBy<Ranked> { it.tier }.thenByDescending { it.score }.thenBy { it.index })
        .map { it.place }
}

/**
 * True when two places that are not stops are one: close together, and the name of one is the
 * first part of the other ("Street 12" and "Street 12, District"), or they have the same name.
 */
fun sameSpot(a: Stop, b: Stop, meters: Double = 80.0): Boolean {
    val first = a.position ?: return false
    val second = b.position ?: return false
    if (groundDistance(first, second) > meters) return false
    val one = searchTokens(a.name.substringBefore(',')).joinToString(" ")
    val other = searchTokens(b.name.substringBefore(',')).joinToString(" ")
    return one.isNotEmpty() && (one == other || one.startsWith(other) || other.startsWith(one))
}
