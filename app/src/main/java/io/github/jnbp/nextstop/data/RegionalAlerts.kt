package io.github.jnbp.nextstop.data

import java.time.OffsetDateTime
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/*
 * Disruptions for all of Germany, from open sources only.
 *
 * The Berlin overview (see currentDisruptions) asks the departures of the large stations of
 * Berlin. Elsewhere the notices come from Transitous (MOTIS v6), which has no list of all
 * current alerts of its own: it sends the GTFS-RT alerts of its feeds only with the answers
 * about rides and stops (`alerts` on the legs and places of /api/v6/plan and /api/v6/trip, on
 * the stop times and places of /api/v6/stoptimes). So the overview outside Berlin is put
 * together from
 * - the departures of the large stations around the user and home (see [regionDisruptions]),
 * - the notices of every journey and departure the app loaded anyway ([AlertHarvest]).
 * It is only as complete as the feeds Transitous has alerts for.
 */

/** A notice the app came across, with when it was last seen (wall clock, ms). */
internal class HarvestEntry(val item: Disruption, val seenMillis: Long)

/**
 * The notices the app came across in the journeys and departures it loaded (see
 * RoutingSource), each once, in memory only and for a few hours: they fill the overview of the
 * region where no list of all notices exists. Nothing is stored and nothing is asked for;
 * the work runs on one background thread, never on the caller's.
 */
object AlertHarvest {
    private val lock = Any()
    private var entries: List<HarvestEntry> = emptyList()

    // One thread, in order: noting never holds up the answer of a request.
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "AlertHarvest").apply { isDaemon = true } }

    /** Notes the notices of [journeys] (their rides, see disruptionsOf). */
    fun noteJourneys(journeys: List<Journey>) {
        if (journeys.none { journey -> journey.legs.any { it.warnings.isNotEmpty() } }) return
        worker.execute { note(disruptionsOf(journeys)) }
    }

    /** Notes the notices of [departures] (see disruptionsOfDepartures). */
    fun noteDepartures(departures: List<Departure>) {
        if (departures.none { it.warnings.isNotEmpty() }) return
        worker.execute { note(disruptionsOfDepartures(departures)) }
    }

    /** Notes [found] now. */
    fun note(found: List<Disruption>, nowMillis: Long = System.currentTimeMillis()) {
        if (found.isEmpty()) return
        synchronized(lock) { entries = harvestMerge(entries, found, nowMillis) }
    }

    /** The notices noted that still hold at [now] and were seen within [HARVEST_KEEP_MILLIS]. */
    fun current(nowMillis: Long = System.currentTimeMillis(), now: OffsetDateTime = OffsetDateTime.now()): List<Disruption> {
        val kept = synchronized(lock) {
            harvestCurrent(entries, nowMillis, now).also { entries = it }
        }
        return kept.map { it.item }
    }
}

/**
 * [kept] with [found] added: a notice seen again (a copy, see mergeDisruptions) is one entry
 * with the lines and stops of both, seen at [nowMillis]; the most recently seen first, at most
 * [max] entries.
 */
internal fun harvestMerge(kept: List<HarvestEntry>, found: List<Disruption>, nowMillis: Long, max: Int = MAX_HARVEST): List<HarvestEntry> {
    val fresh = mergeDisruptions(listOf(found))
    val items = fresh + kept.map { it.item }
    val seen = fresh.map { nowMillis } + kept.map { it.seenMillis }
    return copyGroups(items)
        .map { group -> HarvestEntry(combine(group.map { items[it] }), group.maxOf { seen[it] }) }
        .sortedByDescending { it.seenMillis }
        .take(max)
}

/**
 * The entries of [entries] that still count at [nowMillis] / [now]: seen within [keepMillis],
 * and not over by their end of validity.
 */
internal fun harvestCurrent(
    entries: List<HarvestEntry>,
    nowMillis: Long,
    now: OffsetDateTime,
    keepMillis: Long = HARVEST_KEEP_MILLIS,
): List<HarvestEntry> = entries.filter { entry ->
    nowMillis - entry.seenMillis <= keepMillis && entry.item.validUntil?.isBefore(now) != true
}

/**
 * The places of [anchors] (home, where the user is) whose region gets its own large stations
 * asked: those away from Berlin, whose large stations the overview asks anyway (see
 * HUB_STOPS); places near one asked already count once. At most [max].
 */
fun regionAnchorsToAsk(anchors: List<Pair<Double, Double>>, max: Int = MAX_REGION_ANCHORS): List<Pair<Double, Double>> {
    val asked = ArrayList<Pair<Double, Double>>()
    for (anchor in anchors) {
        if (spotMeters(anchor, BERLIN_MIDDLE) <= BERLIN_HUBS_METERS) continue
        if (asked.any { spotMeters(it, anchor) <= SAME_REGION_METERS }) continue
        asked.add(anchor)
        if (asked.size >= max) break
    }
    return asked
}

/** The box (south, west, north, east) of about [meters] around [center], for bigStopsIn. */
internal fun regionBox(center: Pair<Double, Double>, meters: Double = REGION_HUB_METERS): List<Double> {
    val dLat = meters / 111_320.0
    val dLon = meters / (111_320.0 * Math.cos(Math.toRadians(center.first)).coerceAtLeast(0.2))
    return listOf(center.first - dLat, center.second - dLon, center.first + dLat, center.second + dLon)
}

/**
 * The notices around [anchor] outside Berlin: those on the departures of its large stations
 * (rail first, see bigStopsIn; asked through [source], which picks the provider of the
 * place, see RoutingSource.regionHubs), each at the station it was found at. A station that
 * fails is left out.
 */
suspend fun regionDisruptions(source: RoutingSource, anchor: Pair<Double, Double>): List<Disruption> = coroutineScope {
    val hubs = source.regionHubs(anchor.first, anchor.second)
    val lists = hubs.map { hub ->
        async {
            try {
                disruptionsOfDepartures(source.departures(hub.id, results = REGION_DEPARTURES))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }.map { it.await() }
    mergeDisruptions(lists)
}

/**
 * All current notices the app can know of: the Berlin overview (see currentDisruptions, with
 * [extra] stops such as the ends of saved routes), the large stations around each of
 * [anchors] away from Berlin (see regionAnchorsToAsk, regionDisruptions), and those noted
 * from the journeys and departures loaded lately ([AlertHarvest]). One part that fails leaves
 * the others (as one station of the Berlin overview does).
 */
suspend fun nationwideDisruptions(
    source: RoutingSource,
    extra: List<String>,
    anchors: List<Pair<Double, Double>>,
): List<Disruption> = coroutineScope {
    val regions = regionAnchorsToAsk(anchors).map { anchor ->
        async {
            try {
                regionDisruptions(source, anchor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    val berlin = currentDisruptions(source, extra)
    mergeDisruptions(listOf(berlin) + regions.map { it.await() } + listOf(AlertHarvest.current()))
}

// The middle of Berlin, and how far from it the large stations of the overview reach.
private val BERLIN_MIDDLE = 52.5200 to 13.4050
private const val BERLIN_HUBS_METERS = 30_000.0

// Two places this close are one region; the regions asked at most.
private const val SAME_REGION_METERS = 15_000.0
private const val MAX_REGION_ANCHORS = 2

/** How far around a place its large stations are looked for, in metres. */
internal const val REGION_HUB_METERS = 12_000.0

/** The large stations asked per region. */
internal const val REGION_HUBS = 3

// Departures asked per large station: enough to meet most of its lines.
private const val REGION_DEPARTURES = 60

// Notices noted at most, and how long one is kept after it was last seen.
private const val MAX_HARVEST = 150
internal const val HARVEST_KEEP_MILLIS = 3L * 60 * 60 * 1000
