package io.github.jnbp.nextstop.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The colours of lines. The colour of one ride is worked out by [rideStyle], always from the
 * ride itself (its product, its name, where it runs, the colour its provider sent with it),
 * never from where the app looks: so the same ride has the same colour on every screen and
 * at every moment.
 *
 * Besides Berlin's own network map ([BERLIN_RAPID]) the app keeps the colours of lines it
 * does not know by itself: those a provider sends with its data (Transitous: routeColor,
 * routeTextColor of the GTFS feeds) and those of the route relations of OpenStreetMap in the
 * packs of the German states ("lineColors" of osm-<state>.json, see
 * tools/build_station_asset.py). One line name stands for many lines (a tram "1" runs in
 * Mannheim and in Karlsruhe, a "U1" in Berlin, Hamburg and as a train in Bohemia), so every
 * colour is kept with a place, and the colour of a line is the one nearest to where the ride
 * runs within [MAX_FOCUS_METERS]. A provider's colour comes before one of OpenStreetMap.
 *
 * Colours are ARGB ints. Lines are keyed by their kind of vehicle ([kindOf]) and their name in
 * one form ([nameKey]).
 */
object LineColors {
    /** A colour of a line at a place: [color] and, where known, the colour of the text on it. */
    data class Entry(val color: Int, val text: Int?, val latitude: Double, val longitude: Double)

    /** The colour of a ride's line and, where known, the colour of the text on it (ARGB). */
    data class Style(val color: Int, val text: Int?)

    /**
     * The colours of the S- and U-Bahn lines on the network map of Berlin (VBB). They belong
     * to a U-Bahn named U… or an S-Bahn named S… in the area of the VBB only (see [berlinRapid]).
     */
    val BERLIN_RAPID: Map<String, Int> = mapOf(
        // U-Bahn: the colours of the BVG's current line signs.
        "U1" to 0xFF419328.toInt(), "U2" to 0xFFED3C12.toInt(), "U3" to 0xFF016144.toInt(),
        "U4" to 0xFFFFC101.toInt(), "U5" to 0xFF603B2C.toInt(), "U6" to 0xFF504F87.toInt(),
        "U7" to 0xFF0279BD.toInt(), "U8" to 0xFF013F75.toInt(), "U9" to 0xFFF96815.toInt(),
        "S1" to 0xFFDA6BA2.toInt(), "S2" to 0xFF007734.toInt(), "S25" to 0xFF007734.toInt(),
        "S26" to 0xFF007734.toInt(), "S3" to 0xFF0066AD.toInt(), "S41" to 0xFFAD5937.toInt(),
        "S42" to 0xFFCB6418.toInt(), "S45" to 0xFFCD9C53.toInt(), "S46" to 0xFFCD9C53.toInt(),
        "S47" to 0xFFCD9C53.toInt(), "S5" to 0xFFEB7405.toInt(), "S7" to 0xFF816DA6.toInt(),
        "S75" to 0xFF816DA6.toInt(), "S8" to 0xFF66AA22.toInt(), "S85" to 0xFF66AA22.toInt(),
        "S9" to 0xFF992746.toInt(),
    )

    /**
     * The colour of the text on the BVG's sign of a U-Bahn line of [BERLIN_RAPID]: white on every
     * line but the U4, black on its yellow. Fixed, so no rule of lightness decides it (that rule
     * put dark ink on the orange of the U9).
     */
    fun berlinSubwayText(name: String): Int =
        if (name == "U4") 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    /** A point in the middle of Berlin, for what belongs to Berlin's network by itself (its network map). */
    val BERLIN_CENTRE: Pair<Double, Double> = 52.52 to 13.405

    // A colour this far from the focus belongs to a line of the same name elsewhere.
    const val MAX_FOCUS_METERS = 60_000.0

    // A provider's colour of a line this close to a known one replaces it (a new answer).
    private const val SAME_PLACE_METERS = 20_000.0

    // The colours of a line a provider sent, at most this many places per line.
    private const val MAX_PROVIDER_PLACES = 8

    private val provider = ConcurrentHashMap<String, List<Entry>>()
    private val osm = ConcurrentHashMap<String, List<Entry>>()

    /** Where the app looks now (the centre of the map, the start of a journey); null while unknown. */
    @Volatile
    var focus: Pair<Double, Double>? = null

    private val counter = MutableStateFlow(0)

    /**
     * Counts up whenever colours were added or changed, so that what was drawn with them can be
     * drawn anew: observed (collectAsState) only where colours are kept beyond one composition.
     */
    val version: StateFlow<Int> get() = counter

    /**
     * Notes the colour a provider sends for [line] of [product] at [place] ("e3000b" or
     * "#E3000B"; [textHex] the colour of the text). Nothing without a place (where the app
     * looks is no place of the line), and nothing for a colour that is not one, or none.
     */
    fun noteProvider(line: String?, product: String, colorHex: String?, textHex: String?, place: Pair<Double, Double>?) {
        val color = parseHex(colorHex) ?: return
        val key = key(line, product) ?: return
        val at = place ?: return
        val entry = Entry(color, parseHex(textHex), at.first, at.second)
        var changed = false
        provider.compute(key) { _, old ->
            val rest = old.orEmpty().filter { meters(it.latitude to it.longitude, at) > SAME_PLACE_METERS }
            // The same colour sent again for the same line nearby changes nothing that was drawn.
            val replaced = old.orEmpty().filter { meters(it.latitude to it.longitude, at) <= SAME_PLACE_METERS }
            changed = replaced.none { it.color == entry.color && it.text == entry.text }
            (listOf(entry) + rest).take(MAX_PROVIDER_PLACES)
        }
        if (changed) counter.update { it + 1 }
    }

    /**
     * Adds the colours of a pack: entries [mode, line, "rrggbb", lat, lon] as in the packs, the
     * mode "b" bus, "t" tram, "u" U-Bahn or Stadtbahn, "s" S-Bahn.
     */
    fun addOsm(entries: List<OsmLineColor>) {
        if (entries.isEmpty()) return
        val grouped = HashMap<String, MutableList<Entry>>()
        for (e in entries) {
            val color = parseHex(e.color) ?: continue
            val name = nameKey(e.line) ?: continue
            grouped.getOrPut(e.mode + "|" + name) { ArrayList() }.add(Entry(color, null, e.latitude, e.longitude))
        }
        for ((key, list) in grouped) osm.merge(key, list) { old, more -> old + more }
        counter.update { it + 1 }
    }

    /** The colour of [line] of [product] near [focus], or near [at] where given; null where none is known. */
    fun lookup(line: String?, product: String, at: Pair<Double, Double>? = focus): Entry? {
        val key = key(line, product) ?: return null
        nearest(provider[key], at, anywhere = true)?.let { return it }
        // A Stadtbahn may be a tram for the provider and a light rail in OpenStreetMap (and the
        // other way); a line of no known product (a feed's mode the app has no key for) may be
        // any of the lines on rails in the city.
        val mode = key.substringBefore('|')
        val name = key.substringAfter('|')
        val alternatives = when (mode) {
            "t" -> listOf("u")
            "u" -> listOf("t")
            UNKNOWN_KIND -> listOf("u", "t", "s")
            else -> emptyList()
        }
        return nearest(osm[key], at, anywhere = false)
            ?: alternatives.firstNotNullOfOrNull { nearest(osm["$it|$name"], at, anywhere = false) }
    }

    /**
     * The colour of a ride of [line] of [product] that runs at [at] (see [Leg.ridePlace]), in
     * this order: for a U-Bahn of Berlin the colour and text of the BVG's line sign, before any
     * colour a provider sent, so its badge is always the BVG's; the colour its provider sent
     * with it ([provided], [providedText]); the colour of Berlin's network map for an S-Bahn of
     * Berlin ([berlinRapid]); the colour
     * a provider or OpenStreetMap knows for a line of that name and product near [at]. Null
     * where none is known: the colour of its kind or product applies then.
     *
     * Without [at] (a ride whose place is not known) where the app looks ([focus]) stands in.
     */
    fun rideStyle(
        line: String?,
        product: String,
        at: Pair<Double, Double>?,
        provided: Int? = null,
        providedText: Int? = null,
    ): Style? {
        berlinSubwayStyle(line, product, at)?.let { return it }
        if (provided != null) return Style(provided, providedText)
        berlinRapid(line, product, at)?.let { return Style(it, null) }
        val known = lookup(rapidLineName(line, product), product, at ?: focus) ?: return null
        return Style(known.color, known.text)
    }

    /**
     * The colour of Berlin's network map for a ride of [line] of [product] at [at]: only for a
     * U-Bahn ([product] "subway") named U… or an S-Bahn ("suburban") named S… that runs in the
     * area of the VBB (see [inHomeArea]). A regional train named "U1" in another country, or a
     * U-Bahn "U1" in another city, is no line of Berlin. Null for every other ride.
     */
    fun berlinRapid(line: String?, product: String, at: Pair<Double, Double>?): Int? {
        val name = berlinRapidName(line, product) ?: return null
        if (!inHomeArea(at)) return null
        return BERLIN_RAPID[name]
    }

    /**
     * The colour and text of the BVG's sign for a U-Bahn of Berlin (named U…, in the area of the
     * VBB, see [berlinRapid]): [product] "subway", or no known product (""), as a notice may name
     * its line, since a line named U1 to U9 in the VBB area is Berlin's U-Bahn (and the packs of
     * OpenStreetMap may still carry older colours of it). Null for every other ride, an S-Bahn of
     * Berlin included.
     */
    fun berlinSubwayStyle(line: String?, product: String, at: Pair<Double, Double>?): Style? {
        if (product != "subway" && product != "") return null
        // Of no known product only a name with its U ("U9", never a bare "9").
        if (product == "" && nameKey(line)?.firstOrNull() != 'U') return null
        val name = berlinRapidName(line, "subway") ?: return null
        val color = berlinRapid(line, "subway", at) ?: return null
        return Style(color, berlinSubwayText(name))
    }

    /**
     * The name of [line] of [product] as a key of [BERLIN_RAPID] ("U1", "S41"), where the name
     * and the product agree (U for a U-Bahn, S for an S-Bahn); null else. Says nothing about
     * the place.
     */
    fun berlinRapidName(line: String?, product: String): String? {
        val name = rapidLineName(line, product)?.uppercase()?.replace(" ", "") ?: return null
        val letter = when (product) {
            "subway" -> 'U'
            "suburban" -> 'S'
            else -> return null
        }
        return name.takeIf { it.firstOrNull() == letter && it in BERLIN_RAPID }
    }

    /**
     * True where a ride at [at] runs in the area of the VBB (Berlin and Brandenburg), whose
     * rules and colours the app knows by itself. Without [at], where the app looks ([focus])
     * stands in; with neither, the app's home counts.
     */
    fun inHomeArea(at: Pair<Double, Double>?): Boolean {
        val place = at ?: focus ?: return true
        return inVbbArea(place.first, place.second)
    }

    /** Forgets everything; for tests. */
    fun clear() {
        provider.clear()
        osm.clear()
        focus = null
        counter.update { it + 1 }
    }

    // The entry nearest to [at] within MAX_FOCUS_METERS; without [at] the only one of a line
    // (or with [anywhere] the newest one). Berlin's lines and those around it are kept apart
    // (see sameCity): a night bus N2 in Berlin never takes the light blue of the N2 of
    // Brandenburg an der Havel, which lies within MAX_FOCUS_METERS of western Berlin.
    private fun nearest(list: List<Entry>?, at: Pair<Double, Double>?, anywhere: Boolean): Entry? {
        if (list.isNullOrEmpty()) return null
        if (at == null) return if (anywhere || list.map { it.color }.distinct().size == 1) list.first() else null
        val best = list.filter { sameCity(it, at) }.minByOrNull { meters(it.latitude to it.longitude, at) } ?: return null
        return best.takeIf { meters(it.latitude to it.longitude, at) <= MAX_FOCUS_METERS }
    }

    // A colour noted in Berlin belongs to a ride in Berlin only, one noted outside to a ride
    // outside only: a line name of Berlin (N2, M41, 100) names other lines in the towns around.
    private fun sameCity(entry: Entry, at: Pair<Double, Double>): Boolean =
        inBerlin(entry.latitude, entry.longitude) == inBerlin(at.first, at.second)

    private fun key(line: String?, product: String): String? {
        val mode = kindOf(product) ?: return null
        val name = nameKey(line) ?: return null
        return "$mode|$name"
    }

    /**
     * The kind of a product in the colour table: "b", "t", "u", "s", "f" (ferry), "o" for a line
     * whose product the provider names none or one the app has no key for (a Stadtbahn in some
     * feeds); null for trains and coaches.
     */
    fun kindOf(product: String): String? = when (product) {
        "bus" -> "b"
        "tram" -> "t"
        "subway" -> "u"
        "suburban" -> "s"
        "ferry" -> "f"
        "" -> UNKNOWN_KIND
        else -> null
    }

    // The kind of a line of no known product (see kindOf).
    private const val UNKNOWN_KIND = "o"

    /** A line name in one form: "Tram 4A" and "4a" are "4A", "S 1" is "S1". Null for no name. */
    fun nameKey(line: String?): String? {
        val text = line?.trim()?.uppercase() ?: return null
        val plain = text.replace(KIND_WORD, "").replace(" ", "")
        return plain.takeIf { it.isNotEmpty() && it != "?" }
    }

    /** "e3000b", "#E3000B" or "#e00" as an opaque ARGB colour; null for anything else. */
    fun parseHex(value: String?): Int? {
        var text = value?.trim()?.removePrefix("#") ?: return null
        if (text.length == 3) text = text.map { "$it$it" }.joinToString("")
        if (text.length != 6 || !text.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF000000.toInt()) or text.toInt(16)
    }

    /** True inside the box of Berlin, whose lines have the app's own colours. */
    fun inBerlin(latitude: Double, longitude: Double): Boolean =
        latitude in 52.33..52.68 && longitude in 13.08..13.77

    private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val north = (b.first - a.first) * 111_320.0
        val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
        return sqrt(north * north + east * east)
    }

    private val KIND_WORD = Regex("^(TRAM|STR|BUS)\\s+(?=\\S)")
}

/** A colour of a line from a pack of OpenStreetMap (see [LineColors.addOsm]). */
data class OsmLineColor(val mode: String, val line: String, val color: String, val latitude: Double, val longitude: Double)
