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
    synchronized(TOKEN_CACHE) { TOKEN_CACHE[text] }?.let { return it }
    val tokens = tokensOf(text)
    synchronized(TOKEN_CACHE) { TOKEN_CACHE[text] = tokens }
    return tokens
}

private fun tokensOf(text: String): List<String> {
    val folded = text.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    // Plain ASCII has no accents to take off.
    val plain = if (folded.all { it.code < 0x80 }) folded else Normalizer.normalize(folded, Normalizer.Form.NFD).replace(MARKS, "")
    return plain.split(NON_TOKEN)
        .filter { it.isNotEmpty() }
        .map { word -> if (word.endsWith("strasse")) word.dropLast("strasse".length) + "str" else word }
}

// Made once: the search compares every place in its list with what is typed at every key (#109).
private val MARKS = Regex("\\p{M}+")
private val NON_TOKEN = Regex("[^a-z0-9]+")

// The words of the last texts compared (names of places, queries), so a key typed does not
// take every name of the list apart again. Shared by all threads, hence guarded.
private const val TOKEN_CACHE_SIZE = 1024
private val TOKEN_CACHE = object : LinkedHashMap<String, List<String>>(TOKEN_CACHE_SIZE, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > TOKEN_CACHE_SIZE
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

// The products of a station with regional or long-distance trains (see longDistanceStations).
private val FAR_PRODUCTS = setOf("express", "regional")

// Words after the name of the town that make a station its main one ("Hamburg Hbf").
private val MAIN_STATION_WORDS = setOf("hbf", "hauptbahnhof", "central", "centraal")

// Two hits this close are one station (two providers, or a station and its stop for trains).
private const val SAME_FAR_STATION_METERS = 300.0

/**
 * The block "Long distance" below the local hits of a search: the stations among [candidates]
 * whose name is the town [query] names or starts with it as a whole word ("Hamburg Hbf",
 * "Hamburg-Altona" for "Hamburg"; never "Hamburger Str."), served by regional or long-distance
 * trains and outside the region around [region] (those are local hits already). Each station
 * once, none of [local] again. Ranked by importance: long-distance trains first, the main
 * station of the town first, then the order of the providers; at most [limit].
 */
fun longDistanceStations(
    query: String,
    candidates: List<Stop>,
    local: List<Stop>,
    region: Pair<Double, Double>? = null,
    limit: Int = 5,
): List<Stop> {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return emptyList()
    class Far(val stop: Stop, val index: Int, val rank: Int)
    fun same(a: Stop, b: Stop): Boolean = sameFarStation(a, b)
    val ranked = candidates.mapIndexedNotNull { index, stop ->
        if (stop.kind != PlaceKind.Stop || stop.products.none { it in FAR_PRODUCTS }) return@mapIndexedNotNull null
        val words = searchTokens(stop.name.substringBefore('(').substringBefore('['))
        if (words.size < tokens.size || words.subList(0, tokens.size) != tokens) return@mapIndexedNotNull null
        val position = stop.position
        if (region != null && position != null && groundDistance(region, position) <= REGION_METERS) return@mapIndexedNotNull null
        if (local.any { same(it, stop) }) return@mapIndexedNotNull null
        val rest = words.drop(tokens.size)
        val main = rest.isEmpty() || rest.first() in MAIN_STATION_WORDS
        Far(stop, index, (if ("express" in stop.products) 0 else 2) + (if (main) 0 else 1))
    }.sortedWith(compareBy<Far>({ it.rank }, { it.index }))
    val kept = ArrayList<Stop>()
    for (far in ranked) {
        if (kept.size >= limit) break
        if (kept.none { same(it, far.stop) }) kept.add(far.stop)
    }
    return kept
}

// Two hits of one station: the same id or station, or this close (see SAME_FAR_STATION_METERS).
private fun sameFarStation(a: Stop, b: Stop): Boolean {
    if (a.id == b.id || sameStation(a, a.name, b, b.name)) return true
    val first = a.position ?: return false
    val second = b.position ?: return false
    return groundDistance(first, second) <= SAME_FAR_STATION_METERS
}

/** The list of a search in two blocks: the local hits and the block "Long distance" below them. */
data class SearchBlocks(val local: List<Stop>, val far: List<Stop>)

/**
 * The local hits [local] of a search for [query] and the block "Long distance" (see
 * [longDistanceStations]) from [far], the stations the sources found without the bias for the
 * region. A long-distance station of the town that lies outside the region around [region] is
 * in the block, also where it came among the local hits: there it moves into the block ("Stuttgart"
 * in Berlin puts Stuttgart Hbf below, not at the end of the local list). Places of [fixed] (by
 * id: the own position, history, favourites) stay where they are and are not repeated below.
 * Without a known region the local hits stay as they are.
 */
fun longDistanceBlocks(
    query: String,
    local: List<Stop>,
    far: List<Stop>,
    region: Pair<Double, Double>?,
    fixed: Set<String> = emptySet(),
): SearchBlocks {
    val staying = local.filter { it.id in fixed }
    val fromLocal = if (region == null) emptyList() else local.filter { it.id !in fixed }
    // Without a known region the local hits stay local: all of them count as local, not only the fixed ones.
    val block = longDistanceStations(query, far + fromLocal, local = if (region == null) local else staying, region = region)
    if (block.isEmpty()) return SearchBlocks(local, block)
    val rest = local.filter { place ->
        place.id in fixed || place.kind != PlaceKind.Stop || block.none { sameFarStation(it, place) }
    }
    return SearchBlocks(rest, block)
}

// Two places of one town (from two sources) within this distance are one.
private const val SAME_TOWN_METERS = 30_000.0

/**
 * [places] with each town (or district) place once: "Stuttgart" with "Stuttgart" as its
 * second line from two sources shows once, the first. A place counts as a town where its
 * name is its second line or one of its areas; stops, addresses and points of interest with
 * names of their own are never merged.
 */
fun withoutTwinTowns(places: List<Stop>): List<Stop> {
    fun townKey(place: Stop): String? {
        if (place.kind == PlaceKind.Stop) return null
        val name = searchTokens(place.name.substringBefore(','))
        if (name.isEmpty() || name.any { word -> word.any { it.isDigit() } }) return null
        val others = listOfNotNull(place.detail) + place.areas
        return name.joinToString(" ").takeIf { key -> others.any { searchTokens(it).joinToString(" ") == key } }
    }
    val kept = ArrayList<Stop>(places.size)
    val keys = ArrayList<String?>(places.size)
    for (place in places) {
        val key = townKey(place)
        val twin = key != null && kept.indices.any { index ->
            keys[index] == key && run {
                val a = kept[index].position
                val b = place.position
                a == null || b == null || groundDistance(a, b) <= SAME_TOWN_METERS
            }
        }
        if (!twin) {
            kept.add(place)
            keys.add(key)
        }
    }
    return if (kept.size == places.size) places else kept
}
