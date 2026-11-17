package io.github.jnbp.nextstop.ui

import io.github.jnbp.nextstop.data.PlaceKind
import io.github.jnbp.nextstop.data.STREET_STOP_OWN_SIGN_METERS
import io.github.jnbp.nextstop.data.Stop
import io.github.jnbp.nextstop.data.dhidOf
import io.github.jnbp.nextstop.data.normalStationId
import io.github.jnbp.nextstop.data.stationNameKey
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * One sign per station. Providers list the parts of a big station as stops of their own: the
 * VBB "S+U Berlin Hauptbahnhof" and "S+U Berlin Hauptbahnhof [Gleis 1-8]" (the deep
 * platforms, 150 m away), "S+U Alexanderplatz Bhf (Berlin)" and its "[U2]", "[U5]", "[U8]",
 * "München Hbf" and "München Hbf (tief)"; OpenStreetMap draws some complexes as several
 * stations ("Berlin Hauptbahnhof Tunnel (S-Bahn)" beside "Hauptbahnhof"). On the map each
 * would be a sign of its own. The rail stops of one station become one sign at the main
 * stop's place with the roundels of all of them. A bus or tram stop right at the station
 * (the night buses in front of a U-Bahn station, the bus station of a main station) joins
 * that sign; one farther off in the street keeps a sign of its own ("H"), so its vehicles do
 * not seem to leave from the middle of the station. Bus and tram stops of one name close
 * together (a tram and a bus stop of one square) are one "H". Pure logic, no Android.
 */

// The products of a rail station: their stops are merged, the others ("H") never.
private val RAIL_PRODUCTS = setOf("suburban", "subway", "regional", "express")

/** Rail stops of one station lie at most this far apart (Berlin Hauptbahnhof: about 150 m). */
const val STATION_MERGE_METERS = 400.0

/**
 * Stops joined by their name or by OpenStreetMap's complex alone must be closer than this: two
 * stations of one name a few hundred metres apart (S Yorckstr. (Großgörschenstr.) and S+U
 * Yorckstr.) are two places to walk to, and stay two signs. Stops of one stop place (the same
 * provider number) join up to [STATION_MERGE_METERS].
 */
const val STATION_NAME_MERGE_METERS = 250.0

/** True for a stop of trains, S-Bahn or U-Bahn, whose sign shows roundels and no "H". */
fun isRailStop(stop: Stop): Boolean = stop.kind == PlaceKind.Stop && stop.products.any { it in RAIL_PRODUCTS }

/**
 * The products whose vehicles line up beside the sign of a stop of [products] (see GroupAnchor):
 * a station sign those of its roundels (trains, S-Bahn, U-Bahn; not the buses and trams of the
 * street outside, which have their own "H"), an "H" its buses, trams, boats and coaches (buses
 * and trams where the provider names none).
 */
fun anchorProducts(products: Set<String>): Set<String> {
    val rail = products.filterTo(HashSet()) { it in RAIL_PRODUCTS }
    if (rail.isNotEmpty()) return rail
    return products.ifEmpty { setOf("bus", "tram") }
}

/**
 * The stops of one station: [main] stands for it (its id, name and place), [members] are the
 * other stops of it. [sign] is the main stop with the products of all of them.
 */
data class StationGroup(val main: Stop, val members: List<Stop> = emptyList()) {
    val sign: Stop
        get() = if (members.isEmpty()) main else main.copy(products = main.products + members.flatMap { it.products })

    /** True when the stop [id] is one of this station's. */
    fun has(id: String): Boolean = main.id == id || members.any { it.id == id }
}

/**
 * [stops] grouped by station, in the order of their first stop. Two rail stops (see
 * isRailStop) at most STATION_MERGE_METERS apart are one station when they have the same stop
 * place id (VBB number or DHID: "de:11000:900003201" and "de:11000:900003201::2"), the same
 * name apart from the parts in brackets, "Gleis …", "tief" and the like (see stationBaseKey),
 * or the same station complex [complexOf] (OpenStreetMap's, which knows that 900003201 and
 * 900003200 are both Berlin Hauptbahnhof; null: none known). Two stops of OpenStreetMap only
 * (whose ids come from its tags, not always right) need a word of their names in common for
 * the id. A provider's stop without any products (Transitous may name no modes) counts as a
 * possible part of a station here.
 *
 * Then the stops in the street (see joinStreetStops): a bus or tram stop that belongs to a
 * station (its number or name, at most STREET_STOP_OWN_SIGN_METERS from it; or the station's
 * bus station, "Bahnhof", "ZOB", "Fernbusse", at most STATION_NAME_MERGE_METERS) becomes part
 * of it, and bus and tram stops of one name or number close together (a bus and a tram stop of
 * one square, at most STREET_MERGE_METERS) are one sign. Each keeps its id among the members,
 * so the departures and the stop positions of all of them stay at hand.
 *
 * The main stop of a station is a provider's (its id has departures), then the one with the
 * most rail products, then one without a bracket in its name, then the first.
 * Every other stop is a group of its own.
 */
fun stationGroups(stops: List<Stop>, complexOf: (Stop) -> String? = { null }): List<StationGroup> {
    if (stops.size < 2) return stops.map { StationGroup(it) }
    val parent = IntArray(stops.size) { it }
    fun root(index: Int): Int {
        var at = index
        while (parent[at] != at) {
            parent[at] = parent[parent[at]]
            at = parent[at]
        }
        return at
    }
    // A stop the provider names as the bus or tram part of a station ("S+U Alexanderplatz (Bln)
    // [Bus K.-Liebknecht-Str]") is a stop in the street, whatever products it lists.
    val rail = stops.indices.filter { stationPart(stops[it]) }
    if (rail.size >= 2) {
        val keys = rail.associateWith { stationBaseKey(stops[it].name) }
        val places = rail.associateWith { stopPlaceKey(stops[it].id) }
        val complexes = rail.associateWith { complexOf(stops[it]) }
        for (i in rail.indices) {
            val a = rail[i]
            val at = stops[a].position ?: continue
            for (j in i + 1 until rail.size) {
                val b = rail[j]
                val other = stops[b].position ?: continue
                val apart = metersApart(at, other)
                if (apart > STATION_MERGE_METERS) continue
                val osmOnly = isOsmOnlyStop(stops[a].id) && isOsmOnlyStop(stops[b].id)
                val samePlace = places[a] != null && places[a] == places[b] &&
                    (!osmOnly || nameWords(stops[a].name).any { it in nameWords(stops[b].name) })
                val sameComplex = complexes[a] != null && complexes[a] == complexes[b]
                val near = apart <= STATION_NAME_MERGE_METERS
                if (samePlace || (near && (sameComplex || baseKeysFit(keys.getValue(a), keys.getValue(b))))) {
                    parent[root(a)] = root(b)
                }
            }
        }
    }
    val street = stops.indices.filter { index ->
        val stop = stops[index]
        stop.kind == PlaceKind.Stop && stop.position != null && index !in rail
    }
    joinStreetStops(stops, rail, street) { a, b -> parent[root(a)] = root(b) }
    val byRoot = LinkedHashMap<Int, MutableList<Int>>()
    for (index in stops.indices) byRoot.getOrPut(root(index)) { ArrayList(1) }.add(index)
    return byRoot.values.map { indices ->
        if (indices.size == 1) return@map StationGroup(stops[indices[0]])
        val main = indices.minWithOrNull(
            compareBy<Int> { if (isOsmOnlyStop(stops[it].id)) 1 else 0 }
                .thenByDescending { stops[it].products.count { product -> product in RAIL_PRODUCTS } }
                .thenBy { if (BRACKET.containsMatchIn(stops[it].name)) 1 else 0 }
                .thenBy { it },
        ) ?: indices[0]
        StationGroup(stops[main], indices.filter { it != main }.map { stops[it] })
    }
}

/**
 * Bus and tram stops of one name or one stop place at most this far apart are one sign:
 * the tram and the bus stop of one square (a few dozen to some 150 m apart). Farther, they are
 * stops of their own streets ("H" each).
 */
const val STREET_MERGE_METERS = 200.0

/**
 * True for a stop that may be a part of a station (see stationGroups): a rail stop, or a
 * provider's stop of no known products; never one the provider names as the bus or tram part
 * of a station.
 */
private fun stationPart(stop: Stop): Boolean {
    if (stop.position == null || STREET_PART.containsMatchIn(stop.name)) return false
    if (isRailStop(stop)) return true
    return stop.kind == PlaceKind.Stop && stop.products.isEmpty() && !isOsmOnlyStop(stop.id)
}

/**
 * Joins the bus and tram stops [street] of [stops] (by [join] of two indices) to the station
 * of [rail] they belong to, else to the stops of their name nearby. A street stop joins the
 * nearest station stop that it names or whose number it has, at most
 * STREET_STOP_OWN_SIGN_METERS away (the station's sign stands for it: two signs would
 * cover each other), or whose bus station it is ("Bahnhof", "ZOB", "Fernbusse" by
 * [isForecourtOf]) at most STATION_NAME_MERGE_METERS away. The others are joined to the
 * first stop of their name or stop place within STREET_MERGE_METERS: always to that first
 * one, never on from stop to stop, so a row of stops of one name along a long street stays
 * apart.
 */
private fun joinStreetStops(stops: List<Stop>, rail: List<Int>, street: List<Int>, join: (Int, Int) -> Unit) {
    if (street.isEmpty()) return
    val keys = HashMap<Int, String>()
    fun key(index: Int) = keys.getOrPut(index) { stationBaseKey(stops[index].name) }
    val places = HashMap<Int, String?>()
    fun place(index: Int) = places.getOrPut(index) { stopPlaceKey(stops[index].id) }
    val attached = HashSet<Int>()
    for (index in street) {
        val at = stops[index].position ?: continue
        var best = -1
        var bestMeters = Double.MAX_VALUE
        for (station in rail) {
            val other = stops[station].position ?: continue
            val apart = metersApart(at, other)
            if (apart > STATION_NAME_MERGE_METERS || apart >= bestMeters) continue
            val samePlace = place(index) != null && place(index) == place(station)
            val named = keysContain(key(index), key(station))
            val fits = ((samePlace || named) && apart <= STREET_STOP_OWN_SIGN_METERS) ||
                isForecourtOf(key(index), key(station))
            if (fits) {
                best = station
                bestMeters = apart
            }
        }
        if (best >= 0) {
            join(index, best)
            attached.add(index)
        }
    }
    val seeds = ArrayList<Int>()
    for (index in street) {
        if (index in attached) continue
        val at = stops[index].position ?: continue
        val seed = seeds.firstOrNull { seed ->
            val other = stops[seed].position ?: return@firstOrNull false
            if (metersApart(at, other) > STREET_MERGE_METERS) return@firstOrNull false
            val samePlace = place(index) != null && place(index) == place(seed)
            val sameName = key(index).isNotEmpty() && key(index) == key(seed)
            samePlace || sameName
        }
        if (seed != null) join(index, seed) else seeds.add(index)
    }
}

/** Two keys of [stationBaseKey]: equal, or the shorter one (at least 5 letters) inside the other ("alexanderplatz" in "alexanderplatzdircksenstr"). */
internal fun keysContain(a: String, b: String): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    val (short, long) = if (a.length <= b.length) a to b else b to a
    return short.length >= 5 && long.contains(short)
}

/**
 * True when a bus or tram stop of the key [stopKey] (see stationBaseKey) is the bus station
 * in front of the station [stationKey]: named "Bahnhof", "Hbf", "ZOB", "Busbahnhof",
 * "Fernbusse" and the like, alone or behind the station's name ("Göttingen Fernbusse").
 */
internal fun isForecourtOf(stopKey: String, stationKey: String): Boolean {
    if (stopKey.isEmpty()) return false
    val rest = if (stationKey.length >= 3 && stopKey.startsWith(stationKey)) stopKey.substring(stationKey.length) else stopKey
    return FORECOURT.matches(rest)
}

// The names of the bus station in front of a railway station.
private val FORECOURT = Regex("(bahnhof|bf|hbf|hauptbahnhof|bahnhofsvorplatz|bahnhofsplatz|zob|busbahnhof|bahnhofzob|zobbahnhof|fernbus|fernbusse|fernbusbahnhof|bahnhoffernbus|bahnhoffernbusse)")

/**
 * The signs of the map for [stops], one per station (see stationGroups), without the station of
 * the [marked] stop: that one is drawn in a layer of its own with its ring, and no second
 * sign of the same station may stand beside it.
 */
fun stationSigns(stops: List<Stop>, marked: Stop?, complexOf: (Stop) -> String? = { null }): List<Stop> {
    val all = if (marked != null && stops.none { it.id == marked.id }) stops + marked else stops
    return stationGroups(all, complexOf)
        .filter { group -> marked == null || !group.has(marked.id) }
        .map { it.sign }
}

/**
 * A station's name in one form for [stationGroups]: as stationNameKey, without the parts in
 * square or round brackets ("[Gleis 1-8]", "[U2]", "(tief)", "(Berlin)"), what follows a comma
 * or "Gleis" ("München Hbf Gleis 27-36, Starnberger Bahnhof"), a trailing "tief", and with
 * "Hauptbahnhof" as "hbf". Empty where nothing is left.
 */
fun stationBaseKey(name: String): String {
    val plain = name.replace(BRACKET, " ").replace(PARENTHESES, " ").substringBefore(',')
        .replace(PLATFORM_PART, "")
        .replace(LEVEL_PART, "")
    return stationNameKey(plain).replace("hauptbahnhof", "hbf")
}

/** Two keys of [stationBaseKey] of one station: the same, or one the other plus a word of a station part ("hbf" and "hbftunnel"). */
internal fun baseKeysFit(a: String, b: String): Boolean {
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    val (short, long) = if (a.length <= b.length) a to b else b to a
    return short.length >= 3 && long.startsWith(short) && long.substring(short.length) in STATION_PARTS
}

// What may follow the name of a station in the name of one of its parts. Not "ost", "süd",
// "mitte" and the like: "Herne Mitte" is another station than "Herne".
private val STATION_PARTS = setOf("tunnel", "hp", "bf", "bft", "bahnhof", "s", "u", "sbahn", "ubahn", "tief")

private val BRACKET = Regex("\\[[^\\]]*\\]")
// The part in square brackets of a bus or tram stop beside a station: "[Bus K.-Liebknecht-Str]", "[Tram]".
private val STREET_PART = Regex("\\[\\s*(bus|tram|straßenbahn|strab|sev)\\b[^\\]]*\\]", RegexOption.IGNORE_CASE)
private val PARENTHESES = Regex("\\([^)]*\\)")
private val PLATFORM_PART = Regex("\\s+(gleis\\b|bahnsteig\\b|gl\\.)\\s*[0-9].*$", RegexOption.IGNORE_CASE)
private val LEVEL_PART = Regex("\\s+(tief|oben|unten)\\s*$", RegexOption.IGNORE_CASE)
private val WORD = Regex("[a-z0-9äöüß]{3,}")
private val COMMON_WORDS = setOf("bahnhof", "bhf", "station", "gleis", "tief", "berlin")

/** The stop place of [id]: its VBB number or DHID; null for an id of neither kind. */
internal fun stopPlaceKey(id: String): String? {
    val vbb = normalStationId(id)
    if (vbb.length == 9 && vbb.startsWith("900") && vbb.all { it.isDigit() }) return vbb
    return dhidOf(id)
}

private fun nameWords(name: String): Set<String> =
    WORD.findAll(name.lowercase().replace(BRACKET, " ").replace(PARENTHESES, " ").replace("hbf", "hauptbahnhof"))
        .map { it.value }
        .filterTo(HashSet()) { it !in COMMON_WORDS }

private fun metersApart(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos((a.first + b.first) / 2 * PI / 180)
    return sqrt(north * north + east * east)
}
