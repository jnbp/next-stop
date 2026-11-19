package io.github.jnbp.nextstop.data

import java.io.IOException
import java.time.OffsetDateTime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * A disruption notice as the app collects it: the text of the provider's notice and the lines
 * and kinds of transport it was found on. [id] stays the same as long as the text does, so a
 * notice is reported only once.
 *
 * Source: the remarks of type "warning" that the BVG HAFAS REST API (v6.bvg.transport.rest,
 * hafas-rest-api) sends with journeys and departures, and the GTFS-RT alerts Transitous
 * (MOTIS) sends on legs. Neither API has a list of all current notices on its own (no HIM
 * search), so the overview is put together from the departures of the large stations.
 */
data class Disruption(
    val id: String,
    val summary: String,
    val text: String,
    val lines: Set<String>,
    val products: Set<String>,
    /** From when until when it holds, where the provider says so (the widest of its copies). */
    val validFrom: OffsetDateTime? = null,
    val validUntil: OffsetDateTime? = null,
    /** Its links ("more" on bvg.de, a map of a diversion), out of its text (see parseNoticeText). */
    val links: List<NoticeLink> = emptyList(),
    /** The stops of the departures it was found on; empty for the notices of journeys. */
    val stops: List<Stop> = emptyList(),
    /** The provider's own ids of the notice, where it sends them. */
    val providerIds: Set<String> = emptySet(),
    /** The runs it was found on: each line with its own kind of transport. */
    val carriers: List<NoticeLine> = emptyList(),
) {
    /**
     * The lines the notice is about, as its text names them at its start ("Tram 12: …",
     * "Bus M45, 245: …", "U6: …"). Not the same as the runs it was found on: BVG puts a
     * notice on the replacement buses of tram 12 (run as bus "12") and the notice of a lift
     * on every departure of the station.
     */
    val about: List<NoticeLine> by lazy { linesNamedIn(text, carriers).ifEmpty { linesNamedIn(summary, carriers) } }

    /** A notice about a station rather than a line: a lift or escalator, a stop that moved. */
    val station: Boolean by lazy { stationNotice(summary, text) }

    /**
     * The family the overview files it under: "Stations" for a notice about a station, else
     * the first of S, U, tram, bus the lines it is about belong to, else those it was found on.
     */
    val group: DisruptionGroup by lazy {
        if (station) {
            DisruptionGroup.Stations
        } else {
            groupOf(about.map { it.product }) ?: groupOf(carriers.map { it.product }) ?: groupOf(products)
                ?: DisruptionGroup.Other
        }
    }

    /**
     * The line badges of its card: the lines it is about; for a station none but those its
     * text names (the runs that carry a lift notice are any of the station); else the runs
     * it was found on, each in its own colour.
     */
    val badges: List<NoticeLine> by lazy {
        when {
            about.isNotEmpty() -> about
            station -> emptyList()
            carriers.isNotEmpty() -> carriers
            else -> lines.map { NoticeLine(it, products.firstOrNull().orEmpty()) }
        }
    }

    // Its texts as copies are compared (see mergeDisruptions).
    internal val normalSummary: String by lazy { normalNotice(summary) }
    internal val normalText: String by lazy { normalNotice(text) }

    /** True when it is about or was found on one of [myLines]. */
    fun on(myLines: Set<String>): Boolean = lines.any { it in myLines } || about.any { it.line in myLines }
}

/**
 * Where the line badges of a notice take their colours from when the notice has no place of
 * its own: Berlin. A [NoticeLine] carries no place; coloured by where the app happens to look
 * ([LineColors.focus]) a U9 could turn up in the yellow of Stuttgart's or Frankfurt's U9
 * (Berlin's is orange, yellow is its U4). See [Disruption.colourPlace].
 */
val NOTICE_COLOUR_PLACE: Pair<Double, Double> = LineColors.BERLIN_CENTRE

/**
 * The place the line badges of a notice are coloured at: the first of its [stops] with a
 * position (the stops of the departures it was found on; a notice of a journey gets the stop
 * of its ride, see legStopOf), else [NOTICE_COLOUR_PLACE]. So a notice in Stuttgart shows
 * Stuttgart's U9 and one in Berlin always Berlin's network map.
 */
fun noticeColourPlace(stops: List<Stop>): Pair<Double, Double> =
    stops.firstNotNullOfOrNull { it.position } ?: NOTICE_COLOUR_PLACE

/** Where the line badges of this notice are coloured (see [noticeColourPlace]). */
val Disruption.colourPlace: Pair<Double, Double> get() = noticeColourPlace(stops)

/** The colour of the badge of [line] in a notice coloured at [place]; null where none is known. */
fun noticeLineStyle(line: NoticeLine, place: Pair<Double, Double> = NOTICE_COLOUR_PLACE): LineColors.Style? =
    LineColors.rideStyle(line.line, line.product, place)

/** A line as a notice names it or was found on, with its kind of transport (see [PRODUCT_KEYS]). */
data class NoticeLine(val line: String, val product: String)

/** The groups of the overview of all disruptions, in this order. */
enum class DisruptionGroup(val products: Set<String>) {
    Suburban(setOf("suburban")),
    Subway(setOf("subway")),
    Tram(setOf("tram")),
    Bus(setOf("bus")),
    Other(setOf("regional", "express", "coach", "ferry")),

    /** Notices about stations (lifts, moved stops), by station rather than by line. */
    Stations(emptySet()),
}

/** The first group of the overview [products] belong to; null where none does. */
private fun groupOf(products: Collection<String>): DisruptionGroup? =
    DisruptionGroup.entries.firstOrNull { group -> products.any { it in group.products } }

// The lines a notice of BVG/VBB names at its start: "Tram 12:", "Tram M10:", "Bus M45, 245:",
// "U6:", "S41/S42:", "RE1:", the same in English ("Tram M10: Replacement service …").
private val NAMED_LINES = Regex(
    """^\s*(?:(Tram|Straßenbahn|Strassenbahn|Bus|Busse|U-Bahn|S-Bahn|Fähre|Ferry|Zug|Train)\s+)?""" +
        """([A-Z]{0,3}\d{1,3}[A-Z]?(?:\s*(?:,|/|&|und|and|u\.)\s*[A-Z]{0,3}\d{1,3}[A-Z]?)*)\s*:(?!\d)""",
)
private val LINE_SEPARATOR = Regex("""\s*(?:,|/|&|und|and|u\.)\s*""")

/**
 * The lines [text] names at its start, each with its kind of transport: by the word before
 * them ("Tram", "Bus"), else by the name (U…, S…, RE…), else as one of [carriers] of the same
 * name. A line whose kind stays unknown is left out.
 */
internal fun linesNamedIn(text: String, carriers: List<NoticeLine>): List<NoticeLine> {
    val match = NAMED_LINES.find(text) ?: return emptyList()
    val word = match.groupValues[1].lowercase()
    return match.groupValues[2].split(LINE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { name ->
        val product = when (word) {
            "tram", "straßenbahn", "strassenbahn" -> "tram"
            "bus", "busse" -> "bus"
            "u-bahn" -> "subway"
            "s-bahn" -> "suburban"
            "fähre", "ferry" -> "ferry"
            "zug", "train" -> "regional"
            else -> when {
                Regex("""^U\d""").containsMatchIn(name) -> "subway"
                Regex("""^S\d""").containsMatchIn(name) -> "suburban"
                Regex("""^(?:RE|RB)\d""").containsMatchIn(name) -> "regional"
                else -> carriers.firstOrNull { it.line == name }?.product
            }
        }
        product?.let { NoticeLine(name, it) }
    }.distinct()
}

// The words of a notice about a station rather than a line, in German and English.
private val STATION_WORDS = Regex(
    """(?i)aufzug|aufzüge|fahrstuhl|fahrtreppe|rolltreppe|\belevator|\blift\b|escalator|""" +
        """haltestelle\w*\s+verlegt|verlegung\s+der\s+haltestelle|haltestellenverlegung|verlegte\s+haltestelle|""" +
        """moved\s+stop|stop\s+(?:has\s+been\s+|was\s+|is\s+)?(?:moved|relocated)|relocat\w*\s+(?:of\s+)?(?:the\s+)?stop""",
)

/**
 * Whether a notice is about a station (a lift or escalator out of service, a stop that
 * moved): by its headline, or by the start of its text where it has none. A line notice that
 * mentions a lift further on stays a line notice.
 */
internal fun stationNotice(summary: String, text: String): Boolean =
    if (summary.isNotBlank()) STATION_WORDS.containsMatchIn(summary) else STATION_WORDS.containsMatchIn(text.take(NOTICE_HEAD))

// The start of a notice that says what it is about.
private const val NOTICE_HEAD = 120

private val PRODUCT_ORDER = listOf("suburban", "subway", "tram", "bus", "regional", "express", "ferry", "coach")

/** The order of the badges of a notice: S, U, tram, bus, the rest, then by name. */
private val CARRIER_ORDER = compareBy<NoticeLine>(
    { line -> PRODUCT_ORDER.indexOf(line.product).let { if (it < 0) PRODUCT_ORDER.size else it } },
    { it.line.length },
    { it.line },
)

/** The state of a saved route: the notices on its next journeys, and whether one of them is cancelled. */
data class RouteStatus(
    val routeId: String,
    val disruptions: List<Disruption>,
    val cancelled: Boolean,
    val checked: OffsetDateTime,
    /** The lines the found journeys ride: "my lines" of the overview. */
    val rideLines: Set<String> = emptySet(),
    /**
     * The notices within [radiusMeters] of its start, its end and the changes of its next
     * journey (see [routeSpots]) that are not among [disruptions] already.
     */
    val around: List<Disruption> = emptyList(),
    /** Where [around] was looked for: the start, the end and the changes, each with a position. */
    val spots: List<Stop> = emptyList(),
    /** The radius [around] was looked for in; 0 when it was not looked for. */
    val radiusMeters: Int = 0,
    /** The next journeys found: when each leaves and the lines it rides (see patternLines). */
    val next: List<Pair<OffsetDateTime, List<String>>> = emptyList(),
) {
    /** The notices on its rides and those around its spots, each once. */
    val all: List<Disruption> get() = disruptions + around

    val clear: Boolean get() = all.isEmpty() && !cancelled
}

/**
 * A stable id for the text of a notice: the same for copies that differ only in case,
 * spaces, punctuation or link placeholders (see [normalNotice]).
 */
fun disruptionId(warning: Warning): String =
    (normalNotice(warning.summary) + "|" + normalNotice(warning.text)).hashCode().toUInt().toString(16)

/** The text of a notice as copies are compared: lower case, without placeholders, punctuation and extra spaces. */
internal fun normalNotice(text: String): String =
    withoutPlaceholders(text).lowercase(Locale.ROOT).replace(NOT_A_WORD, " ").trim()

private val NOT_A_WORD = Regex("""[^\p{L}\p{N}]+""")

/**
 * The notices of [journeys], each once, with the lines and products of the rides that carry
 * them. Walks carry none of their own.
 */
fun disruptionsOf(journeys: List<Journey>): List<Disruption> =
    mergeCopies(journeys.flatMap { journey -> journey.legs.filter { !it.walking } }.flatMap { leg ->
        leg.warnings.map { warning -> Triple(warning.copy(stop = warning.stop ?: legStopOf(warning, leg)), leg.line, leg.product) }
    })

/**
 * Where on [leg] the notice [warning] of a journey sits, as its stop: a notice of a journey
 * comes without one, so the stop of the ride whose name its text mentions (the longest name
 * found, so "U Turmstraße" wins over "Turmstraße"), else the stop the ride starts at. That
 * way the notices of saved routes have a place on the maps like those of departures.
 */
internal fun legStopOf(warning: Warning, leg: Leg): Stop? {
    val stops = listOfNotNull(leg.fromPlace) + leg.stopovers.mapNotNull { it.place } + listOfNotNull(leg.toPlace)
    val placed = stops.filter { it.position != null }
    val words = (warning.summary + " " + warning.text).lowercase()
    val named = placed.filter { it.name.length >= 4 && it.name.lowercase() in words }.maxByOrNull { it.name.length }
    return named ?: leg.fromPlace?.takeIf { it.position != null } ?: placed.firstOrNull()
}

/** The notices of [departures], each once, with the lines and products that carry them. */
fun disruptionsOfDepartures(departures: List<Departure>): List<Disruption> =
    mergeCopies(departures.flatMap { departure -> departure.warnings.map { Triple(it, departure.line, departure.product) } })

private fun mergeCopies(found: List<Triple<Warning, String?, String>>): List<Disruption> =
    mergeDisruptions(listOf(merge(found)))

private fun merge(found: List<Triple<Warning, String?, String>>): List<Disruption> =
    found.groupBy { disruptionId(it.first) }.map { (id, items) ->
        val warnings = items.map { it.first }
        val warning = warnings.first()
        Disruption(
            id = id,
            summary = warning.summary,
            text = warning.text,
            lines = items.mapNotNull { it.second }.toSortedSet(),
            products = items.map { it.third }.filter { it.isNotEmpty() }.toSet(),
            validFrom = warnings.mapNotNull { it.validFrom }.minOrNull(),
            validUntil = warnings.mapNotNull { it.validUntil }.maxOrNull(),
            links = warnings.flatMap { it.links }.distinct(),
            stops = warnings.mapNotNull { it.stop }.distinctBy { it.id },
            providerIds = warnings.mapNotNull { it.id }.toSet(),
            carriers = items.mapNotNull { (_, line, product) -> line?.let { NoticeLine(it, product) } }
                .distinct().sortedWith(CARRIER_ORDER),
        )
    }

/**
 * Several lists of notices as one: a notice seen in two of them, or twice in one, once with
 * all its lines and stops. Copies are the same notice when their texts are the same but for
 * case, spaces, punctuation and placeholders (the same [Disruption.id]), when the provider
 * gives them the same id, or when one text holds the other under the same headline (one
 * source cuts a notice short or sends it without its links).
 */
fun mergeDisruptions(lists: List<List<Disruption>>): List<Disruption> {
    val items = lists.flatten()
    return copyGroups(items).map { group -> combine(group.map { items[it] }) }
}

/** [items] by the notice they are copies of: the places in [items] of each, in the order first seen. */
internal fun copyGroups(items: List<Disruption>): List<List<Int>> {
    val groups = ArrayList<MutableList<Int>>()
    items.forEachIndexed { at, item ->
        val home = groups.firstOrNull { group -> group.any { sameNotice(items[it], item) } }
        if (home == null) groups.add(mutableListOf(at)) else home.add(at)
    }
    return groups
}

private fun sameNotice(a: Disruption, b: Disruption): Boolean {
    if (a.id == b.id) return true
    if (a.providerIds.any { it in b.providerIds }) return true
    if (a.normalSummary != b.normalSummary) return false
    val (short, long) = if (a.normalText.length <= b.normalText.length) a.normalText to b.normalText else b.normalText to a.normalText
    return short.length >= MIN_HELD_TEXT && long.contains(short)
}

// A text this short in another proves nothing: "aufzug außer betrieb" is in many.
private const val MIN_HELD_TEXT = 40

/** Copies of one notice as one: the first's text, everything else of all of them. */
internal fun combine(same: List<Disruption>): Disruption =
    same.first().copy(
        lines = same.flatMap { it.lines }.toSortedSet(),
        products = same.flatMap { it.products }.toSet(),
        validFrom = same.mapNotNull { it.validFrom }.minOrNull(),
        validUntil = same.mapNotNull { it.validUntil }.maxOrNull(),
        links = same.flatMap { it.links }.distinct(),
        stops = same.flatMap { it.stops }.distinctBy { it.id },
        providerIds = same.flatMap { it.providerIds }.toSet(),
        carriers = same.flatMap { it.carriers }.distinct().sortedWith(CARRIER_ORDER),
    )

/**
 * The overview: grouped by S, U, tram, bus, the rest and the stations, and within a group
 * the notices on [myLines] first, then by line (stations by their name).
 */
fun groupDisruptions(all: List<Disruption>, myLines: Set<String>): Map<DisruptionGroup, List<Disruption>> =
    all.groupBy { it.group }
        .mapValues { (group, list) ->
            list.sortedWith(
                compareBy<Disruption>(
                    { item -> if (item.on(myLines)) 0 else 1 },
                    { item -> if (group == DisruptionGroup.Stations) item.stops.firstOrNull()?.name ?: "~" else "" },
                    { item -> item.badges.firstOrNull()?.line?.length ?: 99 },
                    { item -> item.badges.firstOrNull()?.line ?: "~" },
                ),
            )
        }
        .toSortedMap(compareBy<DisruptionGroup> { it.ordinal })

/** The notices of [current] that are not among [seen] yet: those to report. */
fun newDisruptions(current: List<Disruption>, seen: Set<String>): List<Disruption> = current.filter { it.id !in seen }

/**
 * The status of [route]: its next journeys and their notices, and with a [radiusMeters] above
 * 0 the notices within it around the start, the end and the changes of the next journey (see
 * [routeSpots], [placeDisruptions]: one stop asked per spot). A spot that fails is left out.
 */
suspend fun routeStatus(source: TransitSource, route: SavedRoute, options: SearchOptions, radiusMeters: Int = 0): RouteStatus {
    val journeys = source.journeys(route.from, route.to, options.copy(via = null), results = ROUTE_JOURNEYS).journeys
    val ride = disruptionsOf(journeys)
    val spots = if (radiusMeters > 0) routeSpots(route, journeys.firstOrNull(), radiusMeters) else emptyList()
    val lists = coroutineScope {
        spots.map { spot ->
            async {
                try {
                    placeDisruptions(source, spot, radiusMeters, maxStops = 1).disruptions
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }.map { it.await() }
    }
    return RouteStatus(
        routeId = route.id,
        disruptions = ride,
        // Only the next one or two matter for "is my way running".
        cancelled = journeys.take(2).any { it.cancelled },
        checked = OffsetDateTime.now(),
        rideLines = journeys.flatMap { it.rides }.mapNotNull { it.line }.toSet(),
        around = withoutCopiesOf(mergeDisruptions(lists), ride),
        spots = spots,
        radiusMeters = radiusMeters,
        next = journeys.filter { !it.cancelled }.map { it.departure to patternLines(it) },
    )
}

/**
 * Where the notices around a route are looked for: its start and its end, the changes of
 * [journey] (where one ride ends and the next begins), then the stops it boards at first and
 * leaves at last; only those with a position, a spot closer than a quarter of [radiusMeters]
 * (at least [ROUTE_SPOT_APART_METERS]) to one before it left out (the platforms of one station), at
 * most [MAX_ROUTE_SPOTS]. Not the stops it passes: that would be the whole way.
 */
internal fun routeSpots(route: SavedRoute, journey: Journey?, radiusMeters: Int): List<Stop> {
    val rides = journey?.rides.orEmpty()
    val changes = rides.zipWithNext().flatMap { (arriving, next) -> listOfNotNull(arriving.toPlace, next.fromPlace) }
    val candidates = listOf(route.from, route.to) + changes + listOfNotNull(rides.firstOrNull()?.fromPlace, rides.lastOrNull()?.toPlace)
    val apart = maxOf(ROUTE_SPOT_APART_METERS, radiusMeters / 4).toDouble()
    val kept = ArrayList<Stop>()
    for (candidate in candidates) {
        val position = candidate.position ?: continue
        if (kept.any { other -> other.position?.let { spotMeters(it, position) < apart } == true }) continue
        kept.add(candidate)
        if (kept.size >= MAX_ROUTE_SPOTS) break
    }
    return kept
}

/** The notices of [found] that are no copies of one of [known] (see [mergeDisruptions]). */
internal fun withoutCopiesOf(found: List<Disruption>, known: List<Disruption>): List<Disruption> =
    found.filter { item -> known.none { sameNotice(it, item) } }

/**
 * All current notices the departures of [stops] carry (by default the large stations of
 * Berlin), together with those of [extra] stops such as the ends of saved routes. A stop that
 * fails is left out; the overview is only as complete as the stations it asks.
 */
suspend fun currentDisruptions(source: TransitSource, extra: List<String> = emptyList()): List<Disruption> = coroutineScope {
    val ids = (HUB_STOPS + extra).distinct().take(MAX_OVERVIEW_STOPS)
    val lists = ids.map { id ->
        async {
            try {
                disruptionsOfDepartures(source.departures(id, results = OVERVIEW_DEPARTURES))
            } catch (e: Exception) {
                emptyList()
            }
        }
    }.map { it.await() }
    mergeDisruptions(lists)
}

/**
 * The notices around a place: the stops near it whose departures were asked, what lies within
 * [radiusMeters] of it (see [disruptionsWithin]), and that radius.
 */
data class PlaceDisruptions(
    val stops: List<Stop>,
    val disruptions: List<Disruption>,
    val radiusMeters: Int = 0,
)

/**
 * The notices within [radiusMeters] of [place] (see [disruptionsWithin]). They are found on
 * the departures of the stops nearest to it within the radius: the place itself where it is
 * a stop, then the nearest, one per name (the platforms of a station carry the same), at most
 * [maxStops] of them. A notice those departures carry counts only where it lies within the
 * radius: the line of a stop nearby that is disrupted across the city does not. One request
 * for the stops and one per stop. A place without a stop nearby gets no stops and no notices;
 * when no stop could be asked, it fails.
 */
suspend fun placeDisruptions(
    source: TransitSource,
    place: Stop,
    radiusMeters: Int,
    maxStops: Int = placeStopCap(radiusMeters),
): PlaceDisruptions {
    val position = place.position ?: return PlaceDisruptions(emptyList(), emptyList(), radiusMeters)
    fun metersOf(stop: Stop): Double =
        stop.position?.let { spotMeters(position, it) } ?: stop.distanceMeters?.toDouble() ?: Double.MAX_VALUE
    val around = source.nearbyStops(position.first, position.second, placeNearbyResults(radiusMeters))
        .filter { it.kind == PlaceKind.Stop && metersOf(it) <= radiusMeters }
        .sortedBy { metersOf(it) }
    val own = if (place.kind == PlaceKind.Stop) listOf(place) else emptyList()
    val near = (own + around).distinctBy { it.id }
    val stops = near.distinctBy { it.name.briefStopName().lowercase(Locale.ROOT) }.take(maxStops.coerceAtLeast(1))
    if (stops.isEmpty()) return PlaceDisruptions(emptyList(), emptyList(), radiusMeters)
    val lists = coroutineScope {
        stops.map { stop ->
            async {
                try {
                    disruptionsOfDepartures(source.departures(stop.id, results = PLACE_DEPARTURES))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }.map { it.await() }
    }
    if (lists.all { it == null }) throw IOException("No departures around the place")
    return PlaceDisruptions(stops, disruptionsWithin(position, radiusMeters, near, mergeDisruptions(lists.filterNotNull())), radiusMeters)
}

/**
 * The notices of [disruptions] that lie within [radiusMeters] of [spot], each with the stops
 * it lies at as its stops (for the markers of the maps). A notice says where it is only in
 * its text: the stops whose departures carry it are anywhere on its lines. So a notice lies
 * within the radius when
 * - it is a station's (a lift, a moved stop) and a stop that carries it is within the radius,
 *   or
 * - its text names one of [near] (the stops around the spot that are known) or one of the
 *   stops that carry it, and that stop is within the radius (see [stopsNamedIn]).
 * A notice of a line that only passes by is left out.
 */
fun disruptionsWithin(
    spot: Pair<Double, Double>,
    radiusMeters: Int,
    near: List<Stop>,
    disruptions: List<Disruption>,
): List<Disruption> {
    fun inside(stop: Stop): Boolean = stop.position?.let { spotMeters(spot, it) <= radiusMeters } == true
    val known = near.filter { inside(it) }
    return disruptions.mapNotNull { item ->
        val carriers = item.stops.filter { inside(it) }
        val atStation = if (item.station) carriers else emptyList()
        val named = stopsNamedIn(item.summary + " " + item.text, known + carriers)
        val located = (atStation + named).distinctBy { it.id }
        if (located.isEmpty()) null else item.copy(stops = located)
    }
}

/**
 * The stops of [stops] that [text] names, the longest name first: by their whole name, without
 * "(Berlin)", and without the signs of the network in front ("S+U", "U"), "Str." also as
 * "Straße" and back; as whole words, at least [MIN_NAMED_STOP] letters long.
 */
internal fun stopsNamedIn(text: String, stops: List<Stop>): List<Stop> {
    val words = text.lowercase(Locale.ROOT)
    return stops.distinctBy { it.id }.mapNotNull { stop ->
        val longest = stopNameForms(stop.name).filter { nameInText(words, it) }.maxOfOrNull { it.length } ?: return@mapNotNull null
        stop to longest
    }.sortedByDescending { it.second }.map { it.first }
}

internal fun stopNameForms(name: String): List<String> {
    val forms = listOf(name, name.shortStopName(), name.briefStopName()).map { it.lowercase(Locale.ROOT).trim() }
    return (forms + forms.map { it.replace("str.", "straße") } + forms.map { it.replace("straße", "str.") })
        .filter { it.length >= MIN_NAMED_STOP }
        .distinct()
}

/** Whether [name] stands in [words] as whole words: no letter or digit right before or after it. */
internal fun nameInText(words: String, name: String): Boolean {
    var at = words.indexOf(name)
    while (at >= 0) {
        val end = at + name.length
        val before = at == 0 || !words[at - 1].isLetterOrDigit()
        val after = end >= words.length || !words[end].isLetterOrDigit() || name.last() == '.'
        if (before && after) return true
        at = words.indexOf(name, at + 1)
    }
    return false
}

/** How many stops near a place are asked for their departures: more in a wider radius. */
internal fun placeStopCap(radiusMeters: Int): Int = when {
    radiusMeters <= 500 -> MAX_PLACE_STOPS
    radiusMeters <= 1000 -> MAX_PLACE_STOPS + 1
    else -> MAX_PLACE_STOPS + 2
}

// The stops asked for around a place: enough to know the names of those within the radius.
private fun placeNearbyResults(radiusMeters: Int): Int = when {
    radiusMeters <= 250 -> 12
    radiusMeters <= 500 -> 20
    radiusMeters <= 1000 -> 35
    else -> 50
}

/** The radii around places and routes the settings offer, in metres; [DEFAULT_PLACE_RADIUS] the first choice. */
val PLACE_RADII: List<Int> = listOf(250, 500, 1000, 2000)
const val DEFAULT_PLACE_RADIUS = 500

// The stops within the radius asked for their departures, at the smallest radii.
private const val MAX_PLACE_STOPS = 2

// A stop name shorter than this in a text proves nothing ("Markt", "Zoo").
private const val MIN_NAMED_STOP = 5

// The spots around a route at most, and how close two may be before the second is left out.
private const val MAX_ROUTE_SPOTS = 6
private const val ROUTE_SPOT_APART_METERS = 150

// Departures asked for per stop near a place: enough to meet the lines of the stop.
private const val PLACE_DEPARTURES = 40

// Journeys asked for the status of a saved route.
private const val ROUTE_JOURNEYS = 3

// Departures asked for per station of the overview: enough to meet most lines of the station.
private const val OVERVIEW_DEPARTURES = 60

// The BVG REST API allows 100 requests a minute: the overview stays well below.
private const val MAX_OVERVIEW_STOPS = 24

/**
 * Large stations of S, U, tram and bus all over Berlin, by VBB number: together their
 * departures meet most lines of the city.
 */
val HUB_STOPS: List<String> = listOf(
    "900100003", // Alexanderplatz
    "900003201", // Hauptbahnhof
    "900100001", // Friedrichstraße
    "900023201", // Zoologischer Garten
    "900120003", // Ostkreuz
    "900007102", // Gesundbrunnen
    "900058101", // Südkreuz
    "900024101", // Charlottenburg
    "900029101", // Spandau
    "900062202", // Rathaus Steglitz
    "900078201", // Neukölln
    "900160004", // Lichtenberg
    "900110001", // Schönhauser Allee
    "900017101", // Mehringdamm
    "900120004", // Warschauer Straße
    "900192001", // Schöneweide
    "900130002", // Pankow
    "900078101", // Hermannplatz
    "900175001", // Wuhletal
)
