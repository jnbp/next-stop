package io.github.jnbp.nextstop.data

import java.util.Locale
import kotlin.math.cos

/*
 * Disruptions on the main maps: where a notice lies, and one marker per stop for the notices
 * there. A notice has a place only where it is located:
 * - the notice of a station (a lift, an escalator, a stop that moved) at the stops that carry it;
 * - another notice at the stops its text names (see stopsNamedIn), looked for only near the
 *   stops that carry it (within [NOTICE_AREA_METERS]), so a stop of the same name in another
 *   town does not count.
 * A notice of a whole line that names no stop has no place and no marker.
 */

/** How far from the stops that carry a notice a stop its text names may lie. */
const val NOTICE_AREA_METERS = 15_000.0

/**
 * The stops a notice may name, with the forms of their names prepared once (see
 * stopNameForms): the candidates of [noticePlaces]. Stops without a position are left out.
 */
class NamedStops internal constructor(stops: List<Stop>, joined: List<NamedStops>) {
    constructor(stops: List<Stop>) : this(stops, emptyList())

    internal class Entry(val stop: Stop, val position: Pair<Double, Double>, val forms: List<String>, val heads: List<String>)

    internal val entries: List<Entry> = joined.flatMap { it.entries } + stops.distinctBy { it.id }.mapNotNull { stop ->
        val position = stop.position ?: return@mapNotNull null
        val forms = stopNameForms(stop.name)
        if (forms.isEmpty()) null else Entry(stop, position, forms, forms.map { firstWord(it) })
    }

    val size: Int get() = entries.size

    /** These stops and those of [other] together, without preparing their names again. */
    operator fun plus(other: NamedStops): NamedStops = NamedStops(emptyList(), listOf(this, other))
}

// The first run of letters and digits of a text: a form of a name can only stand in a notice
// whose words include it (a quick test before the search of the whole form).
private fun firstWord(text: String): String = text.takeWhile { it.isLetterOrDigit() }

private val WORD_SPLIT = Regex("""[^\p{L}\p{N}]+""")

/**
 * Where [item] lies on the map: the stops it is at, at most [maxPlaces] (see the top of this
 * file). Stops of [names] and the stops that carry it count where they lie within [areaMeters]
 * of one of those; one stop per name found, the one nearest to them; a name within a longer
 * one found ("Rathaus" in "Rathaus Steglitz") is no stop of its own. Empty for a notice
 * without a stop that carries it with a position.
 */
fun noticePlaces(
    item: Disruption,
    names: NamedStops,
    areaMeters: Double = NOTICE_AREA_METERS,
    maxPlaces: Int = MAX_NOTICE_PLACES,
): List<Stop> {
    val carriers = item.stops.filter { it.position != null }
    if (carriers.isEmpty()) return emptyList()
    if (item.station) return carriers.distinctBy { it.name.briefStopName().lowercase(Locale.ROOT) }.take(maxPlaces)
    val anchors = carriers.mapNotNull { it.position }.distinct().take(MAX_ANCHORS)
    // A quick box around the anchors before the distance of each candidate.
    val latitudeReach = areaMeters / 111_320.0
    val longitudeReach = areaMeters / (111_320.0 * cos(Math.toRadians(anchors.first().first)).coerceAtLeast(0.2))
    val south = anchors.minOf { it.first } - latitudeReach
    val north = anchors.maxOf { it.first } + latitudeReach
    val west = anchors.minOf { it.second } - longitudeReach
    val east = anchors.maxOf { it.second } + longitudeReach
    val words = (item.summary + " " + item.text).lowercase(Locale.ROOT)
    val tokens = words.split(WORD_SPLIT).filter { it.isNotEmpty() }.toHashSet()
    val found = ArrayList<Triple<Stop, String, Double>>()
    val candidates = names.entries + NamedStops(carriers).entries
    for (entry in candidates) {
        val (latitude, longitude) = entry.position
        if (latitude < south || latitude > north || longitude < west || longitude > east) continue
        var best: String? = null
        entry.forms.forEachIndexed { at, form ->
            if (entry.heads[at] in tokens && (best == null || form.length > best!!.length) && nameInText(words, form)) best = form
        }
        val form = best ?: continue
        val meters = anchors.minOf { spotMeters(it, entry.position) }
        if (meters <= areaMeters) found.add(Triple(entry.stop, form, meters))
    }
    // The longest names first; a shorter one inside a longer one found is part of that name.
    val kept = ArrayList<Triple<Stop, String, Double>>()
    for (candidate in found.sortedByDescending { it.second.length }) {
        if (kept.any { it.second != candidate.second && nameInText(it.second, candidate.second) }) continue
        kept.add(candidate)
    }
    return kept.groupBy { it.second }.values
        .map { same -> same.minByOrNull { it.third }!! }
        .sortedByDescending { it.second.length }
        .map { it.first }
        .take(maxPlaces)
}

/**
 * One marker on the map: the notices at one stop (several stops of one station, or close
 * together, are one), [key] telling it apart while the stop stays.
 */
data class NoticeMarker(val key: String, val stop: Stop, val items: List<Disruption>) {
    val position: Pair<Double, Double> get() = stop.position ?: (0.0 to 0.0)

    /** The line its badge shows: the one line its notices are about, where they are about one. */
    val badge: NoticeLine?
        get() = items.flatMap { it.about }.distinctBy { it.line }.singleOrNull()
}

/**
 * The markers of [items] on the map: each notice not among [hidden] at its places (see
 * [noticePlaces]), the notices of one stop together (see [groupNoticePlaces]).
 */
fun noticeMarkers(items: List<Disruption>, names: NamedStops, hidden: Set<String>): List<NoticeMarker> =
    groupNoticePlaces(items.filter { it.id !in hidden }.flatMap { item -> noticePlaces(item, names).map { item to it } })

/**
 * The notices of [placed] by stop: one marker for stops closer than [NOTICE_SAME_SPOT_METERS],
 * or of one name closer than [NOTICE_SAME_STATION_METERS] (the stops of one station); each
 * notice once in a marker, in the order first found.
 */
fun groupNoticePlaces(placed: List<Pair<Disruption, Stop>>): List<NoticeMarker> {
    class Group(val stop: Stop, val at: Pair<Double, Double>, val name: String, val items: MutableList<Disruption>)
    val groups = ArrayList<Group>()
    for ((item, stop) in placed) {
        val at = stop.position ?: continue
        val name = stop.name.briefStopName().lowercase(Locale.ROOT)
        val home = groups.firstOrNull { group ->
            val meters = spotMeters(group.at, at)
            meters <= NOTICE_SAME_SPOT_METERS || (meters <= NOTICE_SAME_STATION_METERS && group.name == name)
        }
        when {
            home == null -> groups.add(Group(stop, at, name, mutableListOf(item)))
            home.items.none { it.id == item.id } -> home.items.add(item)
        }
    }
    return groups.map { group ->
        NoticeMarker(String.format(Locale.ROOT, "%.5f,%.5f", group.at.first, group.at.second), group.stop, group.items.toList())
    }
}

/**
 * A notice hidden from the maps: its [id], what the list of hidden notices shows of it (its
 * headline, the line it is about) and when it was last seen among the notices the app knows.
 */
data class HiddenNotice(
    val id: String,
    val title: String,
    val line: String? = null,
    val product: String? = null,
    val lastSeen: Long,
)

/**
 * [hidden] after a look at the notices known now: those among [seen] (by id) seen [now], those
 * not seen for longer than [keepMillis] dropped (the notice is over).
 */
fun prunedHidden(hidden: List<HiddenNotice>, seen: Set<String>, now: Long, keepMillis: Long): List<HiddenNotice> =
    hidden.map { if (it.id in seen) it.copy(lastSeen = now) else it }.filter { now - it.lastSeen <= keepMillis }

/**
 * The notices of [departures] of the stop [at] (or of the stop each names): a notice that
 * comes without a stop (Transitous sends none) gets the stop of its departure, so it has a
 * place like those of the BVG.
 */
fun departureNotices(departures: List<Departure>, at: Stop?): List<Disruption> =
    disruptionsOfDepartures(
        departures.map { departure ->
            val stop = (departure.stop ?: at)?.takeIf { it.position != null }
            if (stop == null || departure.warnings.all { it.stop != null }) {
                departure
            } else {
                departure.copy(warnings = departure.warnings.map { if (it.stop == null) it.copy(stop = stop) else it })
            }
        },
    )

/**
 * The stops of [stops] whose departures are asked for the notices of the map in the box
 * [box] (south, west, north, east) around [center]: the provider's stops inside it (no stops
 * of the offline data alone, which have no departures), one per name, the big ones first (S-,
 * U-Bahn and trains, then by how many kinds of transport stop there), then the nearest to
 * [center]; at most [max].
 */
fun bigStopsIn(stops: List<Stop>, box: List<Double>, center: Pair<Double, Double>, max: Int = 3): List<Stop> {
    if (box.size < 4) return emptyList()
    return stops.asSequence()
        .filter { it.kind == PlaceKind.Stop && !it.id.startsWith(OSM_STOP_PREFIX) }
        .filter { stop ->
            val at = stop.position
            at != null && at.first >= box[0] && at.first <= box[2] && at.second >= box[1] && at.second <= box[3]
        }
        .sortedWith(
            compareBy<Stop>(
                { stop -> if (stop.products.any { it in BIG_STOP_PRODUCTS }) 0 else 1 },
                { stop -> -stop.products.size },
                { stop -> spotMeters(center, stop.position!!) },
            ),
        )
        .distinctBy { it.name.briefStopName().lowercase(Locale.ROOT) }
        .take(max)
        .toList()
}

/** The area of the map [center] lies in, for asking its stops at most once in a while: cells of about 2 km. */
fun noticeAreaKey(center: Pair<Double, Double>): String =
    Math.floor(center.first / NOTICE_AREA_CELL).toLong().toString() + ":" + Math.floor(center.second / (NOTICE_AREA_CELL * 1.5)).toLong()

// The kinds of transport of a big stop, and the size of an area of the map in degrees of latitude.
private val BIG_STOP_PRODUCTS = setOf("suburban", "subway", "regional", "express")
private const val NOTICE_AREA_CELL = 0.02

/** How long a hidden notice is kept after it was last seen. */
const val HIDDEN_NOTICE_KEEP_MILLIS = 5L * 24 * 60 * 60 * 1000

// The places of one notice at most: a diversion that names many stops shows at the first few.
private const val MAX_NOTICE_PLACES = 3

// The stops that carry a notice looked around at most.
private const val MAX_ANCHORS = 12

// Stops this close are one marker; stops of one name this close too (the parts of a station).
private const val NOTICE_SAME_SPOT_METERS = 60.0
private const val NOTICE_SAME_STATION_METERS = 400.0
