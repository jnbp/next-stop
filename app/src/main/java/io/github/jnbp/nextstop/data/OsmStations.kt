package io.github.jnbp.nextstop.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * Stations, entrances, elevators, the "Pos." stop positions of bus and tram stops and all stop
 * positions of every bus and tram stop area in Berlin, from OpenStreetMap (© OpenStreetMap contributors, ODbL 1.0). The asset osm-berlin.json is
 * built by tools/build_station_asset.py from the extract of tools/fetch_osm_stations.py.
 *
 * Other German states come as packs of the same format ("osm-bayern.json", written by
 * build_station_asset.py --split-regions), in the assets under osm-regions/ or in the app's
 * files under osm-regions/; [OsmStations.ensureRegions] loads those around a place when they
 * are there. Berlin is built in; without packs the app knows Berlin only, as before.
 */

/** A platform of a station as the axis it runs along, from end [a] to end [b] (latitude, longitude). */
data class OsmPlatform(
    val a: Pair<Double, Double>,
    val b: Pair<Double, Double>,
    val ref: String?,
    val level: String?,
    val lines: List<String>,
)

/**
 * An entrance of a station. [marker] is what its sign shows: "U", "S", "SU" ("R" at a station
 * of regional trains only) or null; [wheelchair] as tagged in OpenStreetMap ("yes", "limited",
 * "no") or null. Where [throughBuilding] is true it is no entrance of the station itself but the
 * door of a mall, hotel or other building through which the station is reached (asset "k":"b");
 * [openingHours] then are the building's as tagged ("oh"), where known: it may be closed outside them.
 */
data class OsmEntrance(
    val latitude: Double,
    val longitude: Double,
    val name: String?,
    val ref: String?,
    val wheelchair: String?,
    val level: String?,
    val marker: String?,
    val throughBuilding: Boolean = false,
    val openingHours: String? = null,
) {
    val position: Pair<Double, Double> get() = latitude to longitude
}

/** An elevator, or an escalator where [escalator] is true, of a station. */
data class OsmLift(val latitude: Double, val longitude: Double, val escalator: Boolean, val level: String?)

/**
 * One station complex: the U-Bahn and S-Bahn stations of one name close together. [ids] are
 * its VBB numbers ("900" and six digits) and IFOPT ids where OpenStreetMap has them;
 * [products] is "U", "S" or "SU", with "R" where the extract also has stations of regional
 * trains ("R" alone for a station of regional trains only).
 */
data class OsmStation(
    val name: String,
    val ids: List<String>,
    val products: String,
    val latitude: Double,
    val longitude: Double,
    val platforms: List<OsmPlatform>,
    val entrances: List<OsmEntrance>,
    val lifts: List<OsmLift>,
) {
    val key: String = osmNameKey(name)
    val position: Pair<Double, Double> get() = latitude to longitude
}

/**
 * The stop position of a bus or tram with its number on the sign ([pos], the "Pos." of the
 * BVG, local_ref in OpenStreetMap). [ids] are the VBB numbers of its stop area.
 */
data class OsmStopPosition(
    val name: String,
    val pos: String,
    val latitude: Double,
    val longitude: Double,
    val bus: Boolean,
    val tram: Boolean,
    val ids: List<String>,
    val lines: List<String>,
) {
    // Made on first use: a pack has tens of thousands of stop positions, most never matched by name.
    val key: String by lazy(LazyThreadSafetyMode.PUBLICATION) { osmNameKey(name) }
    val position: Pair<Double, Double> get() = latitude to longitude
}

/** A bus or tram line that stops at a stop position: [name] as on the vehicle ("M48"), [product] "bus" or "tram". */
data class OsmLine(val name: String, val product: String)

/**
 * One stop position of a bus or tram stop area: [pos] is its "Pos." number, null where it has
 * none; [lines] the lines that stop there (trams first, then buses, each in natural order),
 * empty where OpenStreetMap does not say.
 */
data class OsmAreaPosition(
    val latitude: Double,
    val longitude: Double,
    val bus: Boolean,
    val tram: Boolean,
    val pos: String?,
    val lines: List<OsmLine> = emptyList(),
) {
    val position: Pair<Double, Double> get() = latitude to longitude
}

/**
 * A bus or tram stop area with all its stop positions (for a stop position OpenStreetMap
 * does not have, the platform where people wait). [ids] are its VBB numbers where known.
 */
class OsmStopArea(
    val name: String,
    val ids: List<String>,
    val positions: List<OsmAreaPosition>,
    val key: String = osmNameKey(name),
) {
    val latitude: Double = positions.sumOf { it.latitude } / positions.size
    val longitude: Double = positions.sumOf { it.longitude } / positions.size
    val position: Pair<Double, Double> get() = latitude to longitude
}

/** Where a walk leads in the end: a stop position with its number, or an entrance of a station. */
data class WalkTarget(
    val kind: WalkTargetKind,
    /** The number of the stop position ("3"), or the name or letter of the entrance; null for an entrance without one. */
    val label: String?,
    val latitude: Double,
    val longitude: Double,
    /** For a stop position: the bus and tram lines that stop there, where known (see [OsmStations.linesAt]). */
    val lines: List<OsmLine> = emptyList(),
    /** For an entrance: true when it is an access through a building ([OsmEntrance.throughBuilding]). */
    val throughBuilding: Boolean = false,
    /** For an entrance: its level as tagged ("0", "-1"), where known. */
    val level: String? = null,
) {
    val position: Pair<Double, Double> get() = latitude to longitude
}

enum class WalkTargetKind { StopPosition, Entrance }

/** The OpenStreetMap data of the stations, read once on demand and then kept in memory. */
object OsmStations {
    private const val ASSET = "osm-berlin.json"
    private const val WALKS_ASSET = "transfer-walks-berlin.json"
    private const val LOG_TAG = "OsmStations"

    // The packs of the other states: "osm-<state>.json" in this folder of the assets or of the
    // app's files (a downloaded pack there wins), and "index.json" beside them, which says in
    // which cells of REGION_CELL degrees each state has stations or stops.
    private const val REGION_DIR = "osm-regions"
    private const val REGION_INDEX = "index.json"
    private const val BUILT_IN_REGION = "berlin"
    private const val REGION_CELL = 0.1

    // A place this close (in degrees, about 2 km) to a cell of a state counts as in it.
    private const val REGION_MARGIN = 0.02

    // The products whose stations have entrances to lead a walk to or from.
    private val RAIL_PRODUCTS = setOf("subway", "suburban", "regional", "express")

    /**
     * The German states as boxes (south, west, north, east), as in tools/regions.py; only the
     * fallback where the packs come without their index. Generous, so a place may lie in several.
     */
    private val STATE_BOXES: Map<String, DoubleArray> = mapOf(
        "schleswig-holstein" to doubleArrayOf(53.36, 7.86, 55.06, 11.32),
        "hamburg" to doubleArrayOf(53.39, 9.73, 53.74, 10.33),
        "niedersachsen" to doubleArrayOf(51.29, 6.65, 53.89, 11.60),
        "bremen" to doubleArrayOf(53.01, 8.48, 53.61, 8.99),
        "nordrhein-westfalen" to doubleArrayOf(50.32, 5.86, 52.53, 9.46),
        "hessen" to doubleArrayOf(49.39, 7.77, 51.66, 10.24),
        "rheinland-pfalz" to doubleArrayOf(48.97, 6.11, 50.94, 8.51),
        "baden-wuerttemberg" to doubleArrayOf(47.53, 7.51, 49.79, 10.50),
        "bayern" to doubleArrayOf(47.27, 8.97, 50.56, 13.84),
        "saarland" to doubleArrayOf(49.11, 6.36, 49.64, 7.40),
        "berlin" to doubleArrayOf(52.34, 13.09, 52.68, 13.76),
        "brandenburg" to doubleArrayOf(51.36, 11.27, 53.56, 14.77),
        "mecklenburg-vorpommern" to doubleArrayOf(53.11, 10.59, 54.69, 14.41),
        "sachsen" to doubleArrayOf(50.17, 11.87, 51.69, 15.04),
        "sachsen-anhalt" to doubleArrayOf(50.94, 10.56, 53.04, 13.19),
        "thueringen" to doubleArrayOf(50.20, 9.88, 51.65, 12.65),
    )

    @Volatile
    private var appContext: Context? = null

    // The states loaded or tried (a missing pack is not looked for again); under [lock].
    private val triedRegions = HashSet<String>()

    // The tiles of TILE_DEGREES of each state loaded or tried ("bayern" to tile); under [lock].
    // A pack is read in tiles near where the app looks, never as a whole: the pack of
    // Nordrhein-Westfalen (9 MB) read whole as a JSONObject took the app past its heap (192 MB).
    private val triedTiles = HashSet<Pair<String, Long>>()

    // State -> the cells it has stations or stops in, from the index of the packs; read once.
    @Volatile
    private var regionIndex: Map<String, Set<Long>>? = null

    /** Grows whenever a pack of another state was added, so that what is made of the data is made anew. */
    @Volatile
    var generation: Int = 0
        private set

    private val generationFlow = MutableStateFlow(0)

    /**
     * [generation] to watch: the map makes its layers anew whenever a pack was added, by
     * whichever caller (a journey search, the map itself), also when that caller was cancelled
     * before it could say so.
     */
    val generations: StateFlow<Int> get() = generationFlow

    // A stop position or station farther than this from where the provider puts the stop is another one.
    private const val MATCH_METERS = 400.0

    // A stop area that carries the provider's VBB number or DHID may lie this far from where
    // the provider puts the stop (ids are exact; the distance only guards against a wrong one).
    private const val ID_MATCH_METERS = 1000.0

    // A walk that starts this close to a station complex starts inside it: no entrance to aim for.
    private const val INSIDE_METERS = 120.0

    // An access through a building is aimed for instead of the nearest station entrance only
    // when it is at least this much nearer to the start of the walk.
    private const val BUILDING_SHORTER_METERS = 80.0

    private val lock = Mutex()

    @Volatile
    private var data: Loaded? = null

    // The stop position at the end of a ride's way is the nearest one within this distance;
    // one with the platform number the provider names may lie this far away.
    private const val SNAP_METERS = 60.0
    private const val NUMBER_SNAP_METERS = 150.0

    private class Loaded(
        val stations: List<OsmStation>,
        val stops: List<OsmStopPosition>,
        val areas: List<OsmStopArea> = emptyList(),
        // The colours of the lines of a pack ("lineColors"); handed to LineColors when it is added.
        val colors: List<OsmLineColor> = emptyList(),
        private val readWalks: () -> TransferWalkIndex = { TransferWalkIndex.EMPTY },
    ) {
        // The walks of the changes (asset transfer-walks-berlin.json and those of the loaded
        // packs, osm-regions/transfer-walks-<state>.json), read on the first change asked.
        val walksLazy: Lazy<TransferWalkIndex> = lazy(readWalks)
        val walks: TransferWalkIndex get() = walksLazy.value

        // The stop areas by VBB number and by name key, for [areasOf].
        val areasById: Map<String, List<OsmStopArea>> = HashMap<String, MutableList<OsmStopArea>>().also { map ->
            for (area in areas) for (id in area.ids) map.getOrPut(id) { ArrayList() }.add(area)
        }
        val areasByKey: Map<String, List<OsmStopArea>> = areas.filter { it.key.isNotEmpty() }.groupBy { it.key }

        // The stop areas, stations and stop positions in cells of 0.01 degrees, for the matches by a
        // name that is only a part of the provider's ("Dortmund Heiliger Weg" and "Heiliger Weg").
        val areaCells: Map<Long, List<OsmStopArea>> by lazy { areas.groupBy { nameCell(it.latitude, it.longitude) } }
        val stationCells: Map<Long, List<OsmStation>> by lazy { stations.groupBy { nameCell(it.latitude, it.longitude) } }
        val stopCells: Map<Long, List<OsmStopPosition>> by lazy { stops.groupBy { nameCell(it.latitude, it.longitude) } }

        // The positions of all stop areas in cells of about 110 by 70 metres, for [linesAt].
        val positionCells: Map<Long, List<OsmAreaPosition>> by lazy {
            HashMap<Long, MutableList<OsmAreaPosition>>().also { map ->
                for (area in areas) for (p in area.positions) map.getOrPut(cellOf(p.latitude, p.longitude)) { ArrayList() }.add(p)
            }
        }
    }

    private fun nameCell(latitude: Double, longitude: Double): Long =
        (Math.floor(latitude * 100).toLong() shl 32) + Math.floor(longitude * 100).toLong()

    // The things of [cells] in the cell of [near] and the eight around it (about 1 to 2 km).
    private fun <T> around(cells: Map<Long, List<T>>, near: Pair<Double, Double>): List<T> {
        val lat = Math.floor(near.first * 100).toLong()
        val lon = Math.floor(near.second * 100).toLong()
        val out = ArrayList<T>()
        for (dy in -1L..1L) for (dx in -1L..1L) cells[((lat + dy) shl 32) + lon + dx]?.let { out.addAll(it) }
        return out
    }

    private fun cellOf(latitude: Double, longitude: Double): Long =
        (Math.floor(latitude * 1000).toLong() shl 32) + Math.floor(longitude * 1000).toLong()

    /**
     * The bus and tram lines that stop at the stop position at [position]: those of the nearest
     * position of a stop area within [within] metres, for trams only where [tram] is true, for
     * buses only where it is false; with [pos], not one with another "Pos." number (the stop
     * position across the road). Empty where none is known.
     */
    fun linesAt(
        position: Pair<Double, Double>,
        tram: Boolean? = null,
        within: Double = 10.0,
        pos: String? = null,
    ): List<OsmLine> {
        val cells = data?.positionCells ?: return emptyList()
        val number = platformNumber(pos)
        var best: OsmAreaPosition? = null
        var bestMeters = within
        val lat = Math.floor(position.first * 1000).toLong()
        val lon = Math.floor(position.second * 1000).toLong()
        for (dy in -1L..1L) for (dx in -1L..1L) {
            for (candidate in cells[((lat + dy) shl 32) + lon + dx].orEmpty()) {
                if (candidate.lines.isEmpty()) continue
                if (tram == true && !candidate.tram || tram == false && !candidate.bus) continue
                if (number != null && candidate.pos != null && platformNumber(candidate.pos) != number) continue
                val d = meters(position, candidate.position)
                if (d <= bestMeters) {
                    best = candidate
                    bestMeters = d
                }
            }
        }
        val lines = best?.lines ?: return emptyList()
        return when (tram) {
            true -> lines.filter { it.product == "tram" }
            false -> lines.filter { it.product == "bus" }
            null -> lines
        }
    }

    /** The lines that stop where the walk [target] leads: its own, else those at its place; none for an entrance. */
    fun linesAt(target: WalkTarget): List<OsmLine> = when {
        target.kind != WalkTargetKind.StopPosition -> emptyList()
        target.lines.isNotEmpty() -> target.lines
        else -> linesAt(target.position, pos = target.label)
    }

    /** True once the data is in memory. */
    val loaded: Boolean get() = data != null

    val stations: List<OsmStation> get() = data?.stations.orEmpty()
    val stops: List<OsmStopPosition> get() = data?.stops.orEmpty()
    val stopAreas: List<OsmStopArea> get() = data?.areas.orEmpty()

    /** The station complexes whose centre lies within [within] metres of [point] (looked up in the cells around it). */
    fun stationsNear(point: Pair<Double, Double>, within: Double = 300.0): List<OsmStation> {
        val loaded = data ?: return emptyList()
        return around(loaded.stationCells, point).filter { meters(point, it.position) <= within }
    }

    /** The bus and tram stop areas with a stop position within [within] metres of [point]. */
    fun areasNear(point: Pair<Double, Double>, within: Double = 150.0): List<OsmStopArea> {
        val loaded = data ?: return emptyList()
        return around(loaded.areaCells, point).filter { area -> area.positions.any { meters(point, it.position) <= within } }
    }

    /**
     * The stations and bus and tram stop areas inside the box [south], [west], [north], [east]
     * as stops for the map's signs, so the map shows every stop on the screen from the offline
     * data and not only the few the provider lists near its centre. Stations carry their rail
     * products ("suburban", "subway", "regional"), stop areas "bus" and "tram"; a stop area of
     * the name of a station beside it is left out (the station's sign stands for both), as are
     * stop areas of one name close together. With [stationsOnly] only the stations. Ids start
     * with [OSM_STOP_PREFIX]: they are no provider's ids (see the map, which resolves a tapped
     * one to the provider's stop). Stations first, at most [limit]. Empty while nothing is
     * loaded and for a box of more than MAX_BOX_CELLS cells of 0.01 degrees (zoomed far out).
     */
    fun mapStops(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        stationsOnly: Boolean,
        limit: Int = MAP_STOP_LIMIT,
        // A wider look than the map's, for the names of the stops a notice names (see MapNotices.kt).
        maxCells: Int = MAX_BOX_CELLS,
    ): List<Stop> {
        val loaded = data ?: return emptyList()
        val rowFrom = Math.floor(south * 100).toLong()
        val rowTo = Math.floor(north * 100).toLong()
        val colFrom = Math.floor(west * 100).toLong()
        val colTo = Math.floor(east * 100).toLong()
        if (rowTo < rowFrom || colTo < colFrom) return emptyList()
        if ((rowTo - rowFrom + 1) * (colTo - colFrom + 1) > maxCells) return emptyList()
        fun inside(latitude: Double, longitude: Double) =
            latitude >= south && latitude <= north && longitude >= west && longitude <= east
        val stations = ArrayList<OsmStation>()
        val areas = ArrayList<OsmStopArea>()
        for (row in rowFrom..rowTo) for (col in colFrom..colTo) {
            val cell = (row shl 32) + col
            loaded.stationCells[cell]?.forEach { if (inside(it.latitude, it.longitude)) stations.add(it) }
            if (!stationsOnly) loaded.areaCells[cell]?.forEach { if (inside(it.latitude, it.longitude)) areas.add(it) }
        }
        val out = ArrayList<Stop>(stations.size + areas.size)
        for (station in stations) {
            val products = LinkedHashSet<String>()
            if ('S' in station.products) products.add("suburban")
            if ('U' in station.products) products.add("subway")
            if ('R' in station.products) products.add("regional")
            out.add(
                Stop(
                    id = OSM_STOP_PREFIX + "s:" + (station.ids.firstOrNull() ?: "${station.latitude},${station.longitude}"),
                    name = station.name,
                    latitude = station.latitude,
                    longitude = station.longitude,
                    distanceMeters = null,
                    products = products,
                ),
            )
        }
        val kept = ArrayList<Pair<OsmStopArea, Pair<Double, Double>>>()
        for (area in areas) {
            if (area.positions.isEmpty()) continue
            // The sign beside the positions, not in the middle of two directions far apart.
            val sign = stopSignPoint(null, area.positions.map { it.position }) ?: area.position
            // A stop area of the name of a station right at its sign is drawn as that sign (see
            // streetStopJoinsStation); one farther off in the street keeps its "H".
            if (stations.any { streetStopJoinsStation(area.key, sign, it.key, it.position) }) continue
            if (kept.any { (other, _) -> other.key == area.key && meters(area.position, other.position) <= SAME_AREA_METERS }) continue
            kept.add(area to sign)
        }
        for ((area, sign) in kept) {
            val products = LinkedHashSet<String>()
            if (area.positions.any { it.tram }) products.add("tram")
            if (area.positions.any { it.bus } || products.isEmpty()) products.add("bus")
            out.add(
                Stop(
                    id = OSM_STOP_PREFIX + "a:" + (area.ids.firstOrNull() ?: "${area.latitude},${area.longitude}") + ":" + area.key,
                    name = area.name,
                    latitude = sign.first,
                    longitude = sign.second,
                    distanceMeters = null,
                    products = products,
                ),
            )
        }
        return if (out.size <= limit) out else out.take(limit)
    }

    /** Reads the asset unless it is in memory already. An unreadable asset counts as empty. */
    suspend fun load(context: Context) {
        appContext = context.applicationContext
        if (data != null) return
        withContext(Dispatchers.Default) {
            lock.withLock {
                if (data == null) {
                    data = read(context.applicationContext)
                    triedRegions.add(BUILT_IN_REGION)
                }
            }
        }
    }

    /**
     * Adds the packs of the German states at [points] (where the map is, the ends of a
     * journey) that the app has and that are not loaded yet, in tiles of TILE_DEGREES around
     * each point; Berlin is built in. True when one was added ([generation] then grew); with
     * the first tiles of a pack its boarding positions of short changes
     * ([ChangePositions.loadRegions]). Does nothing before [load] and where there are no
     * packs. A station, stop area or stop position that an earlier pack has already (the
     * same name close by) is not taken twice.
     */
    suspend fun ensureRegions(points: List<Pair<Double, Double>>): Boolean {
        val context = appContext ?: return false
        if (points.isEmpty()) return false
        // The colours of lines with the same name elsewhere are told apart by where the app looks.
        LineColors.focus = points.first()
        return withContext(Dispatchers.IO) {
            if (data == null) load(context)
            val index = regionIndex ?: readRegionIndex(context).also { regionIndex = it }
            // The tiles of each state around each point.
            val wanted = LinkedHashMap<String, MutableSet<Long>>()
            for (point in points) {
                for (region in regionsAt(point, index)) wanted.getOrPut(region) { LinkedHashSet() }.addAll(tilesAt(point))
            }
            if (wanted.isEmpty()) return@withContext false
            var tilesAdded = 0
            val added = lock.withLock {
                val names = ArrayList<String>()
                for ((region, tiles) in wanted) {
                    if (region == BUILT_IN_REGION) continue
                    val fresh = tiles.filter { triedTiles.add(region to it) }.toSet()
                    if (fresh.isEmpty()) continue
                    // The walks and colours of a state come with its first tiles.
                    val first = triedRegions.add(region)
                    val more = try {
                        readRegion(context, region, fresh, first)
                    } catch (e: OutOfMemoryError) {
                        Log.e(LOG_TAG, "pack $region: out of memory reading tiles $fresh", e)
                        null
                    } catch (e: Exception) {
                        Log.w(LOG_TAG, "pack $region not readable", e)
                        null
                    } ?: continue
                    data = try {
                        merge(data ?: Loaded(emptyList(), emptyList()), more)
                    } catch (e: OutOfMemoryError) {
                        Log.e(LOG_TAG, "pack $region: out of memory joining tiles $fresh", e)
                        continue
                    }
                    // Berlin's lines keep the app's own colours (a pack around it may hold them).
                    LineColors.addOsm(more.colors.filter { !LineColors.inBerlin(it.latitude, it.longitude) })
                    Log.d(
                        LOG_TAG,
                        "added $region tiles $fresh: ${more.stations.size} stations, ${more.stops.size} stop positions, " +
                            "${more.areas.size} stop areas; heap ${heapMegabytes()} MB used",
                    )
                    if (first) names += region
                    tilesAdded++
                }
                names
            }
            if (tilesAdded == 0) return@withContext false
            // The data has grown already: the rest is done even when the caller has gone
            // meanwhile (a newer map position), or nobody would ever learn of the new tiles,
            // which are not read again.
            withContext(NonCancellable) {
                // The boarding positions of short changes in the states just added (beside their
                // packs), before the callers make anew what depends on the data.
                if (added.isNotEmpty()) {
                    try {
                        ChangePositions.loadRegions(context, added)
                    } catch (e: OutOfMemoryError) {
                        Log.e(LOG_TAG, "change positions of $added: out of memory", e)
                    }
                }
                lock.withLock {
                    generation++
                    generationFlow.value = generation
                }
            }
            true
        }
    }

    /** The states whose packs may hold things at [point]: by the index of the packs, else by the boxes of the states. */
    private fun regionsAt(point: Pair<Double, Double>, index: Map<String, Set<Long>>): List<String> {
        if (index.isNotEmpty()) {
            // The cell of the point, and those of the cells within REGION_MARGIN of it, so a
            // station just across a cell edge counts.
            val steps = listOf(-REGION_MARGIN, 0.0, REGION_MARGIN)
            val cells = steps.flatMap { dy ->
                steps.map { dx ->
                    regionCell(
                        Math.floor((point.first + dy) / REGION_CELL).toLong(),
                        Math.floor((point.second + dx) / REGION_CELL).toLong(),
                    )
                }
            }.toSet()
            return index.filter { (_, own) -> cells.any { it in own } }.keys.toList()
        }
        return STATE_BOXES.filter { (_, box) ->
            point.first in box[0]..box[2] && point.second in box[1]..box[3]
        }.keys.toList()
    }

    private fun regionCell(row: Long, column: Long): Long = (row shl 32) + (column and 0xFFFFFFFFL)

    // A pack is read in tiles of this many degrees (about 55 by 35 km), those within
    // TILE_MARGIN of a point.
    private const val TILE_DEGREES = 0.5
    private const val TILE_MARGIN = 0.1

    /** The tiles at [point] and within TILE_MARGIN of it. */
    private fun tilesAt(point: Pair<Double, Double>): Set<Long> {
        val steps = listOf(-TILE_MARGIN, 0.0, TILE_MARGIN)
        return steps.flatMap { dy -> steps.map { dx -> tileOf(point.first + dy, point.second + dx) } }.toSet()
    }

    private fun tileOf(latitude: Double, longitude: Double): Long =
        regionCell(Math.floor(latitude / TILE_DEGREES).toLong(), Math.floor(longitude / TILE_DEGREES).toLong())

    private fun heapMegabytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    /**
     * The things of the pack of [region] in [tiles], read as a stream (the file is never held
     * whole, nor as a tree of JSON objects); with [first] also its change walks and colours.
     * Null where the app has no pack of it.
     */
    private fun readRegion(context: Context, region: String, tiles: Set<Long>, first: Boolean): Loaded? {
        val stream = regionStream(context, "osm-$region.json") ?: return null
        return JsonReader(InputStreamReader(stream, Charsets.UTF_8).buffered(1 shl 16)).use { reader ->
            parse(
                reader,
                keep = { latitude, longitude -> tileOf(latitude, longitude) in tiles },
                colors = first,
                walks = if (first) ({ readRegionWalks(context, region) }) else ({ TransferWalkIndex.EMPTY }),
            )
        }
    }

    // A file of the packs as a stream: downloaded into the app's files, else in the assets; null where neither has it.
    private fun regionStream(context: Context, name: String): InputStream? {
        val downloaded = File(File(context.filesDir, REGION_DIR), name)
        if (downloaded.isFile) return downloaded.inputStream()
        return try {
            context.assets.open("$REGION_DIR/$name")
        } catch (e: java.io.IOException) {
            null
        }
    }

    // The index of the packs (tools/build_region_index.py): {"cell": 0.1, "regions": {"bayern": [[row, column], ...], ...}}.
    private fun readRegionIndex(context: Context): Map<String, Set<Long>> = try {
        val text = regionFile(context, REGION_INDEX)
        if (text == null) {
            emptyMap()
        } else {
            val regions = JSONObject(text).optJSONObject("regions") ?: JSONObject()
            regions.keys().asSequence().associateWith { name ->
                val cells = regions.optJSONArray(name) ?: JSONArray()
                (0 until cells.length()).mapNotNull { i ->
                    val cell = cells.optJSONArray(i) ?: return@mapNotNull null
                    regionCell(cell.optLong(0), cell.optLong(1))
                }.toSet()
            }
        }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "pack index not readable", e)
        emptyMap()
    }

    // A file of the packs: downloaded into the app's files, else in the assets; null where neither has it.
    private fun regionFile(context: Context, name: String): String? {
        val downloaded = File(File(context.filesDir, REGION_DIR), name)
        if (downloaded.isFile) return downloaded.readText()
        return try {
            context.assets.open("$REGION_DIR/$name").bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            null
        }
    }

    /** [old] with what [more] adds: things it has already (the same name close by) are left out. */
    private fun merge(old: Loaded, more: Loaded): Loaded {
        val stationsByKey = old.stations.groupBy { it.key }
        val stations = old.stations + more.stations.filter { station ->
            stationsByKey[station.key].orEmpty().none { meters(it.position, station.position) <= MATCH_METERS }
        }
        val stopsByKey = old.stops.groupBy { it.key }
        val stops = old.stops + more.stops.filter { stop ->
            stopsByKey[stop.key].orEmpty().none { it.pos == stop.pos && meters(it.position, stop.position) <= SNAP_METERS }
        }
        val areas = old.areas + more.areas.filter { area ->
            old.areasByKey[area.key].orEmpty().none { meters(it.position, area.position) <= NUMBER_SNAP_METERS }
        }
        // The change walks of both, those of [old] first (its stations are the ones kept).
        // The walks of both, read when first asked; only their lazy values are kept, not [old]
        // and [more] themselves (a chain of all data loaded before).
        val oldWalks = old.walksLazy
        val moreWalks = more.walksLazy
        return Loaded(stations, stops, areas) { oldWalks.value + moreWalks.value }
    }

    /**
     * The stop position with the number [platform] of the stop [stopId] (or, without a VBB
     * number in it, named [stopName]) near [near]; null when OpenStreetMap does not know it.
     */
    fun stopPosition(stopId: String?, stopName: String?, platform: String?, near: Pair<Double, Double>?): OsmStopPosition? {
        val loaded = data ?: return null
        val number = platformNumber(platform) ?: return null
        val ids = idKeys(stopId)
        val key = stopName?.let { osmNameKey(it) }?.takeIf { it.isNotEmpty() }
        val all = if (near != null) around(loaded.stopCells, near) else loaded.stops
        fun close(stop: OsmStopPosition) = near == null || meters(near, stop.position) <= MATCH_METERS
        val fitting = all.filter { stop ->
            platformNumber(stop.pos) == number && (ids.any { it in stop.ids } || (key != null && stop.key == key)) && close(stop)
        }.ifEmpty {
            // A name with its town (see areasOf), only near the stop.
            if (near == null || key == null) emptyList()
            else all.filter { platformNumber(it.pos) == number && nameFits(key, it.key) && close(it) }
        }
        return if (near == null) fitting.firstOrNull() else fitting.minByOrNull { meters(near, it.position) }
    }

    /**
     * The bus and tram stop areas of the stop [stopId] (or named [stopName]) near [near]. An
     * area counts by the position of it nearest to [near], not by its middle: the positions of
     * a rural stop often lie on two streets or both ends of a village, with the provider's
     * point between them. One of the stop's VBB number or DHID may lie up to ID_MATCH_METERS
     * away, one found by its name only up to MATCH_METERS.
     */
    fun areasOf(stopId: String?, stopName: String?, near: Pair<Double, Double>): List<OsmStopArea> {
        val loaded = data ?: return emptyList()
        val key = stopName?.let { osmNameKey(it) }?.takeIf { it.isNotEmpty() }
        val found = LinkedHashSet<OsmStopArea>()
        for (id in idKeys(stopId)) {
            loaded.areasById[id]?.filterTo(found) { nearestMeters(it, near) <= ID_MATCH_METERS }
        }
        key?.let { loaded.areasByKey[it] }?.filterTo(found) { nearestMeters(it, near) <= MATCH_METERS }
        // Outside the VBB the providers name a stop with its town ("Dortmund Heiliger Weg",
        // "Heiliger Weg, Dortmund", "Mannheim Tattersall"), OpenStreetMap without it.
        // Only where the id and the full name found nothing (Berlin is matched by its VBB numbers).
        if (found.isNotEmpty() || key == null) return found.toList()
        return around(loaded.areaCells, near).filter { nameFits(key, it.key) && nearestMeters(it, near) <= MATCH_METERS }
    }

    // The distance from [near] to the nearest position of [area].
    private fun nearestMeters(area: OsmStopArea, near: Pair<Double, Double>): Double =
        area.positions.minOf { meters(near, it.position) }

    /**
     * The stop position where the bus or tram ride [ride] is boarded ([boarding]) or left: the
     * one with the platform number the provider names; else the one of the stop area where
     * OpenStreetMap lists the ride's line (the only one, or of several the nearest to the start
     * or end of the ride's way); else the one nearest to the start or end of the ride's way (or,
     * without a way, to its stop) within [SNAP_METERS], among the positions of its stop area for
     * its kind of vehicle. Null for other rides and where OpenStreetMap knows none.
     */
    fun rideSpot(ride: Leg, boarding: Boolean): WalkTarget? {
        if (data == null || ride.walking) return null
        val tram = when (ride.product) {
            "bus" -> false
            "tram" -> true
            else -> return null
        }
        val place = if (boarding) ride.fromPlace else ride.toPlace
        val anchor = (if (boarding) ride.path.firstOrNull() else ride.path.lastOrNull()) ?: place?.position ?: return null
        val name = place?.name ?: if (boarding) ride.from else ride.to
        val candidates = areasOf(place?.id, name, anchor).flatMap { it.positions }
            .filter { if (tram) it.tram else it.bus }
        if (candidates.isEmpty()) return null
        val number = platformNumber(if (boarding) ride.departurePlatform else ride.arrivalPlatform)
        val numbered = number?.let { wanted ->
            candidates.filter { platformNumber(it.pos) == wanted && meters(anchor, it.position) <= NUMBER_SNAP_METERS }
                .minByOrNull { meters(anchor, it.position) }
        }
        val line = ride.line?.let { lineKey(it) }?.takeIf { it.isNotEmpty() }
        val product = if (tram) "tram" else "bus"
        val byLine = line?.let { wanted ->
            val listed = candidates.filter { p -> p.lines.any { it.product == product && lineKey(it.name) == wanted } }
            (listed.singleOrNull() ?: listed.minByOrNull { meters(anchor, it.position) })
                ?.takeIf { meters(anchor, it.position) <= NUMBER_SNAP_METERS }
        }
        val spot = numbered ?: byLine ?: candidates.minByOrNull { meters(anchor, it.position) }
            ?.takeIf { meters(anchor, it.position) <= SNAP_METERS } ?: return null
        return WalkTarget(WalkTargetKind.StopPosition, spot.pos, spot.latitude, spot.longitude, spot.lines)
    }

    /**
     * [ride] with the stop positions where it is boarded and left ([Leg.boardingSpot],
     * [Leg.alightingSpot]) where they are known, and its way led from the one to the other.
     */
    private fun withSpots(ride: Leg): Leg {
        if (ride.walking || (ride.product != "bus" && ride.product != "tram")) return ride
        val board = ride.boardingSpot ?: rideSpot(ride, boarding = true)
        val alight = ride.alightingSpot ?: rideSpot(ride, boarding = false)
        if (board == null && alight == null) return ride
        var path = ride.path
        if (path.isNotEmpty()) {
            // Cut the way where each spot lies beside it, so it does not run past the spot and back.
            if (board != null) path = PathJoin.attachStart(path, board.position)
            if (alight != null) path = PathJoin.attachEnd(path, alight.position)
            path = PathJoin.smoothEnds(path)
        }
        return ride.copy(boardingSpot = board, alightingSpot = alight, path = path)
    }

    /** The station complex of the stop [stopId] (or named [stopName]) near [near]; null when unknown. */
    fun station(stopId: String?, stopName: String?, near: Pair<Double, Double>?): OsmStation? {
        val loaded = data ?: return null
        val ids = idKeys(stopId)
        val key = stopName?.let { osmNameKey(it) }?.takeIf { it.isNotEmpty() }
        val all = if (near != null) around(loaded.stationCells, near) else loaded.stations
        fun close(station: OsmStation) = near == null || meters(near, station.position) <= MATCH_METERS
        val fitting = all.filter { station ->
            (ids.any { it in station.ids } || (key != null && station.key == key)) && close(station)
        }.ifEmpty {
            // A name with its town (see areasOf), only near the stop.
            if (near == null || key == null) emptyList() else all.filter { nameFits(key, it.key) && close(it) }
        }
        return if (near == null) fitting.firstOrNull() else fitting.minByOrNull { meters(near, it.position) }
    }

    /**
     * The platforms of the station where the train, S-Bahn or U-Bahn [ride] is boarded ([boarding]) or
     * left: those OpenStreetMap lists its line at, else those with its platform number. Empty
     * for other rides and where the station or such a platform is not known.
     */
    fun ridePlatforms(ride: Leg, boarding: Boolean): List<OsmPlatform> {
        if (data == null || ride.walking || ride.product !in RAIL_PRODUCTS) return emptyList()
        val place = if (boarding) ride.fromPlace else ride.toPlace
        val near = (if (boarding) ride.path.firstOrNull() else ride.path.lastOrNull()) ?: place?.position
        val station = station(place?.id, place?.name ?: if (boarding) ride.from else ride.to, near) ?: return emptyList()
        val line = ride.line?.let { lineKey(it) }?.takeIf { it.isNotEmpty() }
        val byLine = line?.let { wanted -> station.platforms.filter { platform -> platform.lines.any { lineKey(it) == wanted } } }.orEmpty()
        if (byLine.isNotEmpty()) return byLine
        val number = platformNumber(if (boarding) ride.departurePlatform else ride.arrivalPlatform) ?: return emptyList()
        return station.platforms.filter { platform ->
            platform.ref?.split(';')?.any { platformNumber(it) == number } == true
        }
    }

    /**
     * The seconds on foot of the change from [arriving] to [next], measured over the walking
     * network of OpenStreetMap (see [TransferWalkIndex]): between two rail
     * platforms of one station complex, between a rail platform and the stop position of a
     * bus or tram near it, or between two such stops; step-free (lifts and ramps) where [stepFree] is asked and such a way
     * is known. Null where the platforms, the stop position or the walk are not known; then
     * the rules of thumb decide.
     */
    fun transferWalkSeconds(arriving: Leg, next: Leg, stepFree: Boolean = false): Int? {
        val loaded = data ?: return null
        if (arriving.walking || next.walking) return null
        val from = changeEnds(arriving, boarding = false) ?: return null
        val to = changeEnds(next, boarding = true) ?: return null
        val station = from.station ?: to.station
        val complex = if (station == null) {
            // Two stops in the street: measured where both lie at one complex (one on each side).
            val a = (from.ends.single() as WalkEnd.Stop).position
            val b = (to.ends.single() as WalkEnd.Stop).position
            loaded.walks.ofStops(a, b) ?: return null
        } else {
            // Two platforms only at one complex.
            if (from.station != null && to.station != null && from.station !== to.station) return null
            loaded.walks.of(station) ?: return null
        }
        return changeWalkSeconds(complex, from.ends, to.ends, stepFree)
    }

    private class ChangeEnds(val station: OsmStation?, val ends: List<WalkEnd>)

    /**
     * The station complex where the rail ride [ride] is boarded ([boarding]) or left, with the
     * platforms of it the ride may use (see [changePlatforms]); null for other rides and where
     * they are not known.
     */
    fun platformEnds(ride: Leg, boarding: Boolean): PlatformChange.Ends? {
        if (data == null || ride.walking || ride.product !in RAIL_PRODUCTS) return null
        val place = if (boarding) ride.fromPlace else ride.toPlace
        val near = (if (boarding) ride.path.firstOrNull() else ride.path.lastOrNull()) ?: place?.position
        val station = station(place?.id, place?.name ?: if (boarding) ride.from else ride.to, near) ?: return null
        val platforms = changePlatforms(ride, boarding, station)
        if (platforms.isEmpty()) return null
        return PlatformChange.Ends(station, platforms)
    }

    /**
     * For a hint where to board the rail ride [ride]: the station complex where it is left with
     * the platforms of it the ride may use (see [changePlatforms]). Null for other rides and
     * where they are not known.
     */
    fun alightingPlatforms(ride: Leg): Pair<OsmStation, List<OsmPlatform>>? {
        if (data == null || ride.walking || ride.product !in RAIL_PRODUCTS) return null
        val place = ride.toPlace
        val near = ride.path.lastOrNull() ?: place?.position
        val station = station(place?.id, place?.name ?: ride.to, near) ?: return null
        val platforms = changePlatforms(ride, boarding = false, station)
        if (platforms.isEmpty()) return null
        return station to platforms
    }

    /**
     * The exit of [station] on the shortest way from its [platforms] to [toward] (see
     * [stationEntrance]), with only the entrances of the sign of [product] where it has such;
     * for a hint where to board a train that leads there. Null where no entrance is known.
     */
    fun exitToward(station: OsmStation, platforms: List<OsmPlatform>, toward: Pair<Double, Double>, product: String): WalkTarget? {
        if (data == null || platforms.isEmpty()) return null
        return stationEntrance(station, toward, platforms, null, signOf(product))
    }

    /**
     * True when [arriving] is left and [next] boarded at one platform of one stop: for rail,
     * one platform of one station complex (see [PlatformChange.samePlatform]); for buses and
     * trams, one stop position. False where that is not so or not known.
     */
    fun samePlatform(arriving: Leg, next: Leg): Boolean {
        if (data == null || arriving.walking || next.walking) return false
        val spots = stopSpotMeters(arriving, next)
        if (spots != null) return spots <= SAME_SPOT_METERS
        val from = platformEnds(arriving, boarding = false) ?: return false
        val to = platformEnds(next, boarding = true) ?: return false
        return PlatformChange.samePlatform(from, to)
    }

    /** Where [ride] is boarded ([boarding]) or left: its stop position, or the platforms of its station it may use. */
    private fun changeEnds(ride: Leg, boarding: Boolean): ChangeEnds? {
        when (modeFamily(ride.product)) {
            ModeFamily.Bus, ModeFamily.Tram -> {
                val spot = (if (boarding) ride.boardingSpot else ride.alightingSpot) ?: rideSpot(ride, boarding) ?: return null
                return ChangeEnds(null, listOf(WalkEnd.Stop(spot.position)))
            }
            ModeFamily.Rail, ModeFamily.Subway -> Unit
            else -> return null
        }
        val place = if (boarding) ride.fromPlace else ride.toPlace
        val near = (if (boarding) ride.path.firstOrNull() else ride.path.lastOrNull()) ?: place?.position
        val station = station(place?.id, place?.name ?: if (boarding) ride.from else ride.to, near) ?: return null
        val platforms = changePlatforms(ride, boarding, station)
        if (platforms.isEmpty()) return null
        return ChangeEnds(station, platforms.map { WalkEnd.Platform(it.a, it.b) })
    }

    /**
     * The platforms of [station] the rail ride [ride] may use: those OpenStreetMap lists its
     * line at, narrowed by the platform number the provider names; else those with that
     * number, but not one of another kind of line (an S-Bahn platform "1" for a U-Bahn).
     */
    private fun changePlatforms(ride: Leg, boarding: Boolean, station: OsmStation): List<OsmPlatform> {
        val line = ride.line?.let { lineKey(it) }?.takeIf { it.isNotEmpty() }
        val number = platformNumber(if (boarding) ride.departurePlatform else ride.arrivalPlatform)
        fun numbered(platform: OsmPlatform) = number != null && platform.ref?.split(';')?.any { platformNumber(it) == number } == true
        val byLine = line?.let { wanted -> station.platforms.filter { p -> p.lines.any { lineKey(it) == wanted } } }.orEmpty()
        val both = byLine.filter { numbered(it) }
        if (both.isNotEmpty()) return both
        if (byLine.isNotEmpty()) return byLine
        val letter = line?.firstOrNull()?.takeIf { it == 'S' || it == 'U' }
        return station.platforms.filter { p ->
            numbered(p) && (letter == null || p.lines.isEmpty() || p.lines.any { it.startsWith(letter) })
        }
    }

    /**
     * Where the walk [walk] before the ride [ride] should lead: the stop position of a bus or
     * tram with the platform of the ride, or for the U-Bahn, S-Bahn and trains the entrance of
     * the station on the shortest way from where the walk starts ([from], else the start of
     * its way) to the platform of the ride (see [stationEntrance]). Null when neither is
     * known, and for a walk that starts inside the station itself.
     */
    fun walkTarget(walk: Leg, ride: Leg, from: Pair<Double, Double>? = null): WalkTarget? {
        if (data == null || !walk.walking || ride.walking) return null
        val start = from ?: walk.path.firstOrNull() ?: walk.fromPlace?.position ?: return null
        val rideStart = ride.path.firstOrNull() ?: ride.fromPlace?.position
        val near = rideStart ?: walk.path.lastOrNull() ?: walk.toPlace?.position
        return when (ride.product) {
            "bus", "tram" -> {
                val stop = stopPosition(ride.fromPlace?.id, ride.fromPlace?.name ?: ride.from, ride.departurePlatform, near)
                stop?.let {
                    val lines = linesAt(it.position, tram = ride.product == "tram", within = 15.0, pos = it.pos)
                        .ifEmpty { it.lines.map { name -> OsmLine(name, ride.product) } }
                    WalkTarget(WalkTargetKind.StopPosition, it.pos, it.latitude, it.longitude, lines)
                }
            }
            in RAIL_PRODUCTS -> {
                val station = station(ride.fromPlace?.id, ride.fromPlace?.name ?: ride.from, near) ?: return null
                if (meters(start, station.position) <= INSIDE_METERS) return null
                val platform = ridePlatforms(ride, boarding = true).takeIf { it.isNotEmpty() }
                stationEntrance(station, start, platform, rideStart, signOf(ride.product))
            }
            else -> null
        }
    }

    /**
     * Where the walk [walk] after the ride [ride] of the U-Bahn, S-Bahn or a train leaves its
     * station: the exit on the shortest way from the platform where the ride ends to where the
     * walk ends ([to], else the end of its way). Null for a walk that ends inside the station
     * itself (a change on the spot), and where no exit is known.
     */
    fun walkExit(walk: Leg, ride: Leg, to: Pair<Double, Double>? = null): WalkTarget? {
        if (data == null || !walk.walking || ride.walking || ride.product !in RAIL_PRODUCTS) return null
        val end = to ?: walk.walkTarget?.position ?: walk.path.lastOrNull() ?: walk.toPlace?.position ?: return null
        val rideEnd = ride.path.lastOrNull() ?: ride.toPlace?.position
        val near = rideEnd ?: walk.path.firstOrNull() ?: walk.fromPlace?.position
        val station = station(ride.toPlace?.id, ride.toPlace?.name ?: ride.to, near) ?: return null
        if (meters(end, station.position) <= INSIDE_METERS) return null
        val platform = ridePlatforms(ride, boarding = false).takeIf { it.isNotEmpty() }
        return stationEntrance(station, end, platform, rideEnd, signOf(ride.product))
    }

    // The sign of the entrances of a product: "U" for the U-Bahn, "S" for the S-Bahn, any for trains.
    private fun signOf(product: String): String? = when (product) {
        "subway" -> "U"
        "suburban" -> "S"
        else -> null
    }

    /**
     * The entrance of [station] for a walk between [outside] (where the walk starts or ends
     * outside) and the platforms [platforms] of the ride (else the point [inside], else the
     * centre of the station): of all its entrances, east and west, the one with the shortest
     * way outside - entrance - platform (straight lines), so the walk goes in on the side it
     * comes from and near the right platform. With [sign] ("U", "S") only entrances with that
     * sign or none, where there are such.
     *
     * Station entrances come first. An access through a building (the door of a mall or hotel
     * with a way down to the station) is taken only where no station entrance is known or its
     * way is shorter by at least [BUILDING_SHORTER_METERS], and only where it is not known to
     * close: without opening hours or with "24/7". Other opening hours are not evaluated, so
     * such a door is never aimed for (it may be shut when the walk gets there).
     */
    private fun stationEntrance(
        station: OsmStation,
        outside: Pair<Double, Double>,
        platforms: List<OsmPlatform>?,
        inside: Pair<Double, Double>?,
        sign: String?,
    ): WalkTarget? {
        fun inner(entrance: OsmEntrance): Double = when {
            platforms != null -> platforms.minOf { platformMeters(it, entrance.position) }
            inside != null -> meters(inside, entrance.position)
            else -> meters(station.position, entrance.position)
        }
        fun way(entrance: OsmEntrance): Double = meters(outside, entrance.position) + inner(entrance)
        fun signed(entrance: OsmEntrance) = sign == null || entrance.marker?.contains(sign) ?: true
        val (accesses, entrances) = station.entrances.partition { it.throughBuilding }
        val candidates = entrances.filter { signed(it) }.ifEmpty { entrances }
        val best = candidates.minByOrNull { way(it) }
        val access = accesses
            .filter { signed(it) && (it.openingHours == null || it.openingHours.trim() == "24/7") }
            .minByOrNull { way(it) }
        val entrance = when {
            best == null -> access
            access != null && way(access) + BUILDING_SHORTER_METERS < way(best) -> access
            else -> best
        } ?: return null
        // An entrance farther from the outside end than the station itself lies on the other side.
        if (meters(outside, entrance.position) > meters(outside, station.position) + 60.0 &&
            entrances.size + accesses.size > 1 && platforms == null
        ) {
            return null
        }
        val label = entrance.ref ?: entrance.name?.takeIf { osmNameKey(it) != station.key }
        return WalkTarget(
            WalkTargetKind.Entrance, label, entrance.latitude, entrance.longitude,
            throughBuilding = entrance.throughBuilding,
            level = entrance.level,
        )
    }

    // The distance from [point] to the axis of [platform] (straight, in metres).
    private fun platformMeters(platform: OsmPlatform, point: Pair<Double, Double>): Double {
        val scale = cos(Math.toRadians(point.first))
        val ax = (platform.a.second - point.second) * scale
        val ay = platform.a.first - point.first
        val bx = (platform.b.second - point.second) * scale
        val by = platform.b.first - point.first
        val dx = bx - ax
        val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length <= 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        val x = ax + t * dx
        val y = ay + t * dy
        return sqrt(x * x + y * y) * 111_320.0
    }

    /**
     * [journey] with each walk before a ride led to its [WalkTarget] where one is known: the
     * walk ends there, and a bus or tram ride starts its way there. Bus and tram rides also
     * learn the stop positions where they are boarded and left (see [rideSpot]), so a change
     * between two of them shows where each one stops. Unchanged while the data is not loaded.
     */
    fun refine(journey: Journey): Journey {
        if (data == null) return journey
        val legs = journey.legs
        if (legs.isEmpty()) return journey
        var changed = false
        val out = legs.toMutableList()
        for (index in legs.indices) {
            val spotted = try {
                withSpots(legs[index])
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "stop spots", e)
                legs[index]
            }
            if (spotted !== legs[index]) {
                out[index] = spotted
                changed = true
            }
        }
        // The ways of the rides of rail start and end at their own platforms, not at the
        // coordinate of the whole station (a U-Bahn under an S-Bahn station 150 m away drew a
        // spike out to the station and back).
        for (index in out.indices) {
            val ride = out[index]
            if (ride.walking || ride.product !in RAIL_PRODUCTS) continue
            val clipped = try {
                PlatformChange.clipAtPlatforms(
                    ride,
                    platformEnds(ride, boarding = true)?.platforms.orEmpty(),
                    platformEnds(ride, boarding = false)?.platforms.orEmpty(),
                )
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "platform clip", e)
                ride
            }
            if (clipped !== ride) {
                out[index] = clipped
                changed = true
            }
        }
        for (index in 0 until legs.size - 1) {
            val walk = out[index]
            val ride = out[index + 1]
            if (!walk.walking || ride.walking || walk.walkTarget != null) continue
            // The walk starts where the ride before it was left (its stop position), where there is one.
            val before = out.getOrNull(index - 1)?.takeIf { !it.walking }
            val from = before?.alightingSpot?.position ?: before?.path?.lastOrNull()
            val target = try {
                walkTarget(walk, ride, from)
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "walk target", e)
                null
            } ?: continue
            val path = walk.path
            val newPath = when {
                // A way of its own (not just the straight line) is cut where the target lies beside it.
                path.size > 2 -> PathJoin.attachEnd(path.dropLast(1), target.position, always = true)
                path.size == 2 -> path.dropLast(1) + target.position
                else -> listOfNotNull(walk.fromPlace?.position, target.position).takeIf { it.size == 2 } ?: path
            }
            out[index] = walk.copy(path = newPath, walkTarget = target)
            if (target.kind == WalkTargetKind.StopPosition) {
                // The numbered stop position the walk leads to is where the ride is boarded.
                val ridePath = ride.boardingSpot?.let { spot -> ride.path.drop(if (ride.path.firstOrNull() == spot.position) 1 else 0) }
                    ?: ride.path
                val first = ridePath.firstOrNull()
                if (first != null && first != target.position && meters(first, target.position) <= 150.0) {
                    val joined = PathJoin.smoothEnds(PathJoin.attachStart(ridePath, target.position))
                    out[index + 1] = ride.copy(path = joined, boardingSpot = target)
                } else if (ride.boardingSpot != null && ride.boardingSpot.position != target.position) {
                    out[index + 1] = ride.copy(boardingSpot = target)
                }
            }
            changed = true
        }
        // Each walk after a ride of the U-Bahn, S-Bahn or a train leaves its station by the
        // exit toward where the walk goes: the walk then starts there (the way on foot is
        // asked from the exit).
        for (index in 1 until out.size) {
            val walk = out[index]
            val ride = out[index - 1]
            if (!walk.walking || ride.walking || walk.walkExit != null) continue
            // Toward the stop position or entrance the walk leads to, else the start of the ride after it.
            val after = out.getOrNull(index + 1)?.takeIf { !it.walking }
            val to = walk.walkTarget?.position ?: after?.path?.firstOrNull()
            val exit = try {
                walkExit(walk, ride, to)
            } catch (e: RuntimeException) {
                Log.w(LOG_TAG, "walk exit", e)
                null
            } ?: continue
            val path = walk.path
            val newPath = when {
                path.size > 2 -> PathJoin.attachStart(path.drop(1), exit.position, always = true)
                path.size == 2 -> listOf(exit.position, path.last())
                else -> listOfNotNull(exit.position, walk.walkTarget?.position ?: walk.toPlace?.position)
                    .takeIf { it.size == 2 } ?: path
            }
            out[index] = walk.copy(path = newPath, walkExit = exit)
            changed = true
        }
        // Two rides of rail at one complex on different platforms with no walk between them
        // from the provider: a walk of the app's own, so the map draws it and the change tells it.
        val walked = try {
            PlatformChange.withWalks(
                out,
                RAIL_PRODUCTS,
                { ride, boarding -> platformEnds(ride, boarding) },
                { arriving, next -> io.github.jnbp.nextstop.data.transferWalkSeconds(arriving, next) },
            )
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "platform walks", e)
            out
        }
        if (walked !== out) changed = true
        return if (changed) journey.copy(legs = walked) else journey
    }

    private fun read(context: Context): Loaded = try {
        JsonReader(InputStreamReader(context.assets.open(ASSET), Charsets.UTF_8).buffered(1 shl 16)).use { reader ->
            parse(reader, keep = null, colors = false) { readWalks(context) }
        }
    } catch (e: OutOfMemoryError) {
        Log.e(LOG_TAG, "asset: out of memory", e)
        Loaded(emptyList(), emptyList())
    } catch (e: Exception) {
        Log.w(LOG_TAG, "asset not readable", e)
        Loaded(emptyList(), emptyList())
    }

    // [walks]: the change walks of the data (read lazily, on the first change asked). With
    // [keep], only the stations, stop positions and stop areas at places it keeps (a stop area
    // by its first position); [colors]: also the colours of the lines. Read as a stream, with
    // names, ids and lines shared between the things that have the same ones.
    private fun parse(
        reader: JsonReader,
        keep: ((Double, Double) -> Boolean)?,
        colors: Boolean,
        walks: () -> TransferWalkIndex = { TransferWalkIndex.EMPTY },
    ): Loaded {
        val shared = Shared()
        val stations = ArrayList<OsmStation>()
        val stops = ArrayList<OsmStopPosition>()
        val areas = ArrayList<OsmStopArea>()
        val lineColors = ArrayList<OsmLineColor>()
        fun kept(latitude: Double, longitude: Double) = keep == null || keep(latitude, longitude)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "stations" -> eachOf(reader) {
                    readStation(reader, shared)?.let { if (kept(it.latitude, it.longitude)) stations.add(it) }
                }
                "stops" -> eachOf(reader) {
                    readStop(reader, shared)?.let { if (kept(it.latitude, it.longitude)) stops.add(it) }
                }
                "stopAreas" -> eachOf(reader) {
                    readArea(reader, shared) { latitude, longitude -> kept(latitude, longitude) }?.let { areas.add(it) }
                }
                "lineColors" -> if (colors) eachOf(reader) { readColor(reader)?.let { lineColors.add(it) } } else reader.skipValue()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        stations.trimToSize()
        stops.trimToSize()
        areas.trimToSize()
        Log.d(LOG_TAG, "loaded ${stations.size} stations, ${stops.size} stop positions, ${areas.size} stop areas, ${lineColors.size} line colours")
        return Loaded(stations, stops, areas, lineColors, walks)
    }

    // Strings, ids, name keys and lines read once and shared by all that have the same.
    private class Shared {
        private val strings = HashMap<String, String>()
        private val keys = HashMap<String, String>()
        private val lines = HashMap<String, OsmLine>()
        private val idLists = HashMap<List<String>, List<String>>()

        fun string(value: String): String = strings.getOrPut(value) { value }
        fun key(name: String): String = keys.getOrPut(name) { string(osmNameKey(name)) }
        fun line(name: String, product: String): OsmLine = lines.getOrPut("$product|$name") { OsmLine(string(name), product) }
        fun ids(values: List<String>): List<String> =
            if (values.isEmpty()) emptyList() else idLists.getOrPut(values) { values.map { string(stopPlaceId(it)) } }
    }

    // Calls [item] for every element of the array at the reader (which [item] reads); null skipped.
    private inline fun eachOf(reader: JsonReader, item: () -> Unit) {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return
        }
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.NULL) reader.nextNull() else item()
        }
        reader.endArray()
    }

    // A string or number at the reader as text; null for null and anything else (skipped).
    private fun textOf(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }
        else -> {
            reader.skipValue()
            null
        }
    }

    private fun doubleOf(reader: JsonReader): Double = when (reader.peek()) {
        JsonToken.NUMBER, JsonToken.STRING -> reader.nextDouble()
        else -> {
            reader.skipValue()
            Double.NaN
        }
    }

    private fun stringsOf(reader: JsonReader): List<String> {
        val out = ArrayList<String>(4)
        eachOf(reader) { textOf(reader)?.takeIf { it.isNotBlank() }?.let { out.add(it) } }
        return out
    }

    private fun readWalks(context: Context): TransferWalkIndex = try {
        val text = context.assets.open(WALKS_ASSET).bufferedReader().use { it.readText() }
        TransferWalkIndex.parse(text).also { Log.d(LOG_TAG, "loaded the change walks of ${it.stations.size} complexes") }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "change walks not readable", e)
        TransferWalkIndex.EMPTY
    }

    // The change walks of the pack of [region] ("transfer-walks-<region>.json" beside it); none
    // where it has none or they are not readable.
    private fun readRegionWalks(context: Context, region: String): TransferWalkIndex = try {
        regionFile(context, "transfer-walks-$region.json")?.let { text ->
            TransferWalkIndex.parse(text).also { Log.d(LOG_TAG, "loaded the change walks of $region: ${it.stations.size} complexes") }
        } ?: TransferWalkIndex.EMPTY
    } catch (e: OutOfMemoryError) {
        Log.e(LOG_TAG, "change walks of $region: out of memory", e)
        TransferWalkIndex.EMPTY
    } catch (e: Exception) {
        Log.w(LOG_TAG, "change walks of $region not readable", e)
        TransferWalkIndex.EMPTY
    }

    // A station complex: name, ids, products, lat, lon, platforms {a: [lat, lon, lat, lon], ref,
    // level, lines}, entrances {lat, lon, name, ref, wheelchair, level, m, k, oh}, lifts {t, lat, lon, level}.
    private fun readStation(reader: JsonReader, shared: Shared): OsmStation? {
        var name = ""
        var ids: List<String> = emptyList()
        var products = ""
        var latitude = Double.NaN
        var longitude = Double.NaN
        val platforms = ArrayList<OsmPlatform>()
        val entrances = ArrayList<OsmEntrance>()
        val lifts = ArrayList<OsmLift>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = textOf(reader).orEmpty()
                "ids" -> ids = shared.ids(stringsOf(reader))
                "products" -> products = textOf(reader).orEmpty()
                "lat" -> latitude = doubleOf(reader)
                "lon" -> longitude = doubleOf(reader)
                "platforms" -> eachOf(reader) { readPlatform(reader, shared)?.let { platforms.add(it) } }
                "entrances" -> eachOf(reader) { readEntrance(reader, shared)?.let { entrances.add(it) } }
                "lifts" -> eachOf(reader) { readLift(reader, shared)?.let { lifts.add(it) } }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (latitude.isNaN() || longitude.isNaN()) return null
        return OsmStation(
            name = shared.string(name),
            ids = ids,
            products = shared.string(products),
            latitude = latitude,
            longitude = longitude,
            platforms = platforms,
            entrances = entrances,
            lifts = lifts,
        )
    }

    private fun readPlatform(reader: JsonReader, shared: Shared): OsmPlatform? {
        var axis: List<Double> = emptyList()
        var ref: String? = null
        var level: String? = null
        var lines: List<String> = emptyList()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "a" -> {
                    val values = ArrayList<Double>(4)
                    eachOf(reader) { values.add(doubleOf(reader)) }
                    axis = values
                }
                "ref" -> ref = textOf(reader)?.takeIf { it.isNotBlank() }?.let { shared.string(it) }
                "level" -> level = textOf(reader)?.takeIf { it.isNotBlank() }?.let { shared.string(it) }
                "lines" -> lines = stringsOf(reader).map { shared.string(it) }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (axis.size < 4 || axis.any { it.isNaN() }) return null
        return OsmPlatform(a = axis[0] to axis[1], b = axis[2] to axis[3], ref = ref, level = level, lines = lines)
    }

    private fun readEntrance(reader: JsonReader, shared: Shared): OsmEntrance? {
        var latitude = Double.NaN
        var longitude = Double.NaN
        var name: String? = null
        var ref: String? = null
        var wheelchair: String? = null
        var level: String? = null
        var marker: String? = null
        var kind: String? = null
        var hours: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val field = reader.nextName()
            if (field == "lat") {
                latitude = doubleOf(reader)
                continue
            }
            if (field == "lon") {
                longitude = doubleOf(reader)
                continue
            }
            val value = textOf(reader)?.takeIf { it.isNotBlank() }?.let { shared.string(it) }
            when (field) {
                "name" -> name = value
                "ref" -> ref = value
                "wheelchair" -> wheelchair = value
                "level" -> level = value
                "m" -> marker = value
                "k" -> kind = value
                "oh" -> hours = value
            }
        }
        reader.endObject()
        if (latitude.isNaN() || longitude.isNaN()) return null
        return OsmEntrance(
            latitude = latitude,
            longitude = longitude,
            name = name,
            ref = ref,
            wheelchair = wheelchair,
            level = level,
            marker = marker,
            // "k":"b": an access through a building; absent in older assets (an entrance).
            throughBuilding = kind == "b",
            openingHours = hours,
        )
    }

    private fun readLift(reader: JsonReader, shared: Shared): OsmLift? {
        var latitude = Double.NaN
        var longitude = Double.NaN
        var type: String? = null
        var level: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "lat" -> latitude = doubleOf(reader)
                "lon" -> longitude = doubleOf(reader)
                "t" -> type = textOf(reader)
                "level" -> level = textOf(reader)?.takeIf { it.isNotBlank() }?.let { shared.string(it) }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (latitude.isNaN() || longitude.isNaN()) return null
        return OsmLift(latitude = latitude, longitude = longitude, escalator = type == "escalator", level = level)
    }

    // A stop area: name "n", VBB numbers and DHIDs "ids", positions "p" as [lat, lon, mode,
    // number?, lines?]: the number is "" where a position has lines but none; lines {"t": [...], "b": [...]}.
    // Null for one whose first position [keep] does not keep (its name is then not looked at).
    private fun readArea(reader: JsonReader, shared: Shared, keep: (Double, Double) -> Boolean): OsmStopArea? {
        var name = ""
        var ids: List<String> = emptyList()
        val positions = ArrayList<OsmAreaPosition>(4)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "n" -> name = textOf(reader).orEmpty()
                "ids" -> ids = shared.ids(stringsOf(reader))
                "p" -> eachOf(reader) { readAreaPosition(reader, shared)?.let { positions.add(it) } }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (positions.isEmpty()) return null
        if (!keep(positions[0].latitude, positions[0].longitude)) return null
        positions.trimToSize()
        val shown = shared.string(name)
        return OsmStopArea(name = shown, ids = ids, positions = positions, key = shared.key(shown))
    }

    private fun readAreaPosition(reader: JsonReader, shared: Shared): OsmAreaPosition? {
        reader.beginArray()
        var index = 0
        var latitude = Double.NaN
        var longitude = Double.NaN
        var mode = "b"
        var pos: String? = null
        var lines: List<OsmLine> = emptyList()
        while (reader.hasNext()) {
            when (index) {
                0 -> latitude = doubleOf(reader)
                1 -> longitude = doubleOf(reader)
                2 -> mode = textOf(reader) ?: "b"
                3 -> pos = textOf(reader)?.takeIf { it.isNotBlank() }?.let { shared.string(it) }
                4 -> lines = readAreaLines(reader, shared)
                else -> reader.skipValue()
            }
            index++
        }
        reader.endArray()
        if (latitude.isNaN() || longitude.isNaN()) return null
        return OsmAreaPosition(
            latitude = latitude,
            longitude = longitude,
            bus = 'b' in mode,
            tram = 't' in mode,
            pos = pos,
            lines = lines,
        )
    }

    // The lines of a position: trams first, then buses, as in the asset.
    private fun readAreaLines(reader: JsonReader, shared: Shared): List<OsmLine> {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return emptyList()
        }
        var trams: List<String> = emptyList()
        var buses: List<String> = emptyList()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "t" -> trams = stringsOf(reader)
                "b" -> buses = stringsOf(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (trams.isEmpty() && buses.isEmpty()) return emptyList()
        return trams.map { shared.line(it, "tram") } + buses.map { shared.line(it, "bus") }
    }

    // A colour of a line: [mode, line, "rrggbb", lat, lon] (see tools/build_station_asset.py).
    private fun readColor(reader: JsonReader): OsmLineColor? {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return null
        }
        val values = ArrayList<String?>(5)
        reader.beginArray()
        while (reader.hasNext()) values.add(textOf(reader))
        reader.endArray()
        if (values.size < 5) return null
        val latitude = values[3]?.toDoubleOrNull() ?: return null
        val longitude = values[4]?.toDoubleOrNull() ?: return null
        return OsmLineColor(values[0] ?: return null, values[1] ?: return null, values[2] ?: return null, latitude, longitude)
    }

    // A numbered stop position: name, pos, lat, lon, mode, ids, lines.
    private fun readStop(reader: JsonReader, shared: Shared): OsmStopPosition? {
        var name = ""
        var pos: String? = null
        var latitude = Double.NaN
        var longitude = Double.NaN
        var mode = "b"
        var ids: List<String> = emptyList()
        var lines: List<String> = emptyList()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "name" -> name = textOf(reader).orEmpty()
                "pos" -> pos = textOf(reader)?.takeIf { it.isNotBlank() }
                "lat" -> latitude = doubleOf(reader)
                "lon" -> longitude = doubleOf(reader)
                "mode" -> mode = textOf(reader) ?: "b"
                "ids" -> ids = shared.ids(stringsOf(reader))
                "lines" -> lines = stringsOf(reader).map { shared.string(it) }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (pos == null || latitude.isNaN() || longitude.isNaN()) return null
        return OsmStopPosition(
            name = shared.string(name),
            pos = shared.string(pos),
            latitude = latitude,
            longitude = longitude,
            bus = 'b' in mode,
            tram = 't' in mode,
            ids = ids,
            lines = lines,
        )
    }
}

private fun isVbbStation(id: String): Boolean = id.length == 9 && id.startsWith("900") && id.all { it.isDigit() }

// A DHID at the level of the stop place ("de:05913:1128"), inside a longer id or one with a prefix.
private val DHID_PLACE = Regex("(?<![0-9A-Za-z])de:[0-9]{5}:[0-9A-Za-z_]+")

/**
 * The DHID of the stop place in [id] ("de-DELFI_de:05913:1128:1:1" of Transitous gives
 * "de:05913:1128"); null where there is none.
 */
fun dhidOf(id: String?): String? = id?.let { DHID_PLACE.find(it)?.value }

/** An id of a stop place in the one form of the packs: its DHID where it has one, else as [normalStationId]. */
fun stopPlaceId(id: String): String {
    // A DHID of Berlin or Brandenburg holds the VBB number the Berlin asset is keyed by.
    val vbb = normalStationId(id)
    if (isVbbStation(vbb)) return vbb
    return dhidOf(id) ?: vbb
}

/** The ids of the provider's stop [stopId] to look the packs up by: its VBB number, its DHID. */
internal fun idKeys(stopId: String?): List<String> {
    stopId ?: return emptyList()
    val vbb = normalStationId(stopId).takeIf { isVbbStation(it) }
    return listOfNotNull(vbb, dhidOf(stopId)).distinct()
}

// A name of OpenStreetMap this short is not looked for inside a longer name of a provider.
private const val MIN_PART_KEY = 5

/** The start of the id of a stop the map knows only from OpenStreetMap (see [OsmStations.mapStops]). */
const val OSM_STOP_PREFIX = "osm:"

// The most stops [OsmStations.mapStops] hands out, and the largest box it looks into (in cells
// of 0.01 degrees, about 1.1 by 0.7 km: a box of a phone screen at zoom 12 and a bit).
private const val MAP_STOP_LIMIT = 800
private const val MAX_BOX_CELLS = 600

// Two stop areas of one name this close are one sign.
private const val SAME_AREA_METERS = 250.0

/**
 * A bus or tram stop whose sign stands farther than this from the sign of a rail station keeps
 * a sign ("H") of its own, whatever its name: "S+U Alexanderplatz/Memhardstraße" (the
 * provider's "S+U Alexanderplatz (Bln) [Bus K.-Liebknecht-Str]") is 230 m from the station's
 * sign, a walk of its own. Closer, the station's sign stands for it (the trams of "S+U
 * Alexanderplatz/Dircksenstraße", 20 m): two signs would cover each other.
 */
const val STREET_STOP_OWN_SIGN_METERS = 120.0

/**
 * True when the bus or tram stop of the name key [stopKey] with its sign at [stopSign] is drawn
 * as the sign of the rail station [stationKey] at [stationSign] (see nameFits): of its name and
 * at most [STREET_STOP_OWN_SIGN_METERS] from it.
 */
fun streetStopJoinsStation(
    stopKey: String,
    stopSign: Pair<Double, Double>,
    stationKey: String,
    stationSign: Pair<Double, Double>,
): Boolean = meters(stopSign, stationSign) <= STREET_STOP_OWN_SIGN_METERS && nameFits(stopKey, stationKey)

/**
 * True when the name key [provider] of a provider's stop and [osm] of OpenStreetMap name the
 * same stop: equal, or one starts or ends with the other where the shorter one has at least
 * MIN_PART_KEY letters ("dortmundheiligerweg" and "heiligerweg", "tattersallmannheim" and
 * "tattersall"). Only for things near the stop.
 */
internal fun nameFits(provider: String, osm: String): Boolean {
    if (provider.isEmpty() || osm.isEmpty()) return false
    if (provider == osm) return true
    val (short, long) = if (provider.length <= osm.length) provider to osm else osm to provider
    return short.length >= MIN_PART_KEY && (long.startsWith(short) || long.endsWith(short))
}

/**
 * The number of a stop position or platform in one form: "Pos. 3", "3" and "Steig 3" are all "3";
 * letters stay ("A"). Null for nothing.
 */
fun platformNumber(platform: String?): String? {
    val text = platform?.trim()?.lowercase() ?: return null
    val plain = text.replace(Regex("^(pos\\.?|position|steig|bstg\\.?|bussteig|gleis|gl\\.?|platform)\\s*"), "").trim()
    return plain.takeIf { it.isNotEmpty() }?.uppercase()
}

/** A name of a station or stop for matching OpenStreetMap to the providers: as [stationNameKey], without "Bhf". */
fun osmNameKey(name: String): String = stationNameKey(name).replace("bhf", "")

private fun meters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
    return sqrt(north * north + east * east)
}
