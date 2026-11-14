package io.github.jnbp.nextstop.data

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Where to stand in the arriving train for a short change, from the open dataset
 * vbb-change-positions (ODbL 1.0), which comes with the app as an asset, and where it has no
 * record from osm-change-positions, worked out from OpenStreetMap data (ODbL 1.0, built by
 * tools/build_station_asset.py) for Berlin and Brandenburg, and from those of the other German
 * states (osm-regions/osm-change-positions-<state>.ndjson) once the state's pack is loaded.
 * [fromPosition] runs from 0 (rear end of the train) to 1 (front, the driver's end).
 * [samePlatform] means both trains stop at the same platform. [estimated] marks a position
 * that comes from the map (see [BoardingGeometry], or a record of the OpenStreetMap dataset,
 * [fromOsm]) rather than from a record of the VBB dataset.
 */
data class ChangePosition(
    val fromPosition: Float,
    val samePlatform: Boolean,
    val estimated: Boolean = false,
    val fromOsm: Boolean = false,
) {
    /** The third of the train to be in: 0 rear, 1 middle, 2 front. */
    val part: Int
        get() = when {
            fromPosition < 0.34f -> 0
            fromPosition > 0.66f -> 2
            else -> 1
        }
}

/**
 * What a change looks like for the dataset: station, lines and the stations before and after.
 * The station before or after is null while it is unknown. When it is only a guess (the stop
 * where the arriving ride begins, or where the departing ride ends, while the stops in
 * between are unknown), [previousCertain] or [nextCertain] is false: a guess that names a
 * station the dataset has next to the change is right, as the dataset only names neighbours,
 * and a guess that names none is dropped. The names are used where an id is not a VBB number.
 * [previousExcluded] is where the arriving vehicle goes after the change, [nextExcluded] where the
 * departing one comes from: neither can be the station before or after, which settles the
 * direction while the stops in between are unknown.
 */
data class ChangeQuery(
    val fromStation: String,
    val fromLine: String,
    val previousStation: String?,
    val toStation: String,
    val toLine: String,
    val nextStation: String?,
    val previousCertain: Boolean = true,
    val nextCertain: Boolean = true,
    val fromName: String? = null,
    val previousName: String? = null,
    val toName: String? = null,
    val nextName: String? = null,
    val previousExcluded: String? = null,
    val previousExcludedName: String? = null,
    val nextExcluded: String? = null,
    val nextExcludedName: String? = null,
)

private class ChangeStation(id: String, name: String) {
    val id = normalStationId(id)
    val key = stationNameKey(name)
}

private class ChangeRecord(
    val fromStation: ChangeStation,
    val fromLines: Set<String>,
    val previousStation: ChangeStation,
    val toStation: ChangeStation,
    val toLines: Set<String>,
    val nextStation: ChangeStation,
    val position: ChangePosition,
)

/**
 * The records of one dataset with the interchange complex of each of its stations, by id: the
 * stations of one record (the platforms of one change) and stations of the same base name form
 * one complex.
 */
private class ChangeDataset(val records: List<ChangeRecord>, val complexes: Map<String, Int>)

/**
 * The datasets, read once on demand and then kept in memory. The VBB dataset wins; the records
 * worked out from OpenStreetMap fill its gaps, first those of Berlin and Brandenburg, then
 * those of the other states in the order their packs were loaded; the estimate from the map
 * ([BoardingGeometry]) is for the callers where none has a record.
 */
object ChangePositions {
    private const val ASSET = "vbb-change-positions.ndjson"
    private const val OSM_ASSET = "osm-change-positions.ndjson"

    // Those of the other states, as tools/build_region_index.py --install puts them: in this
    // folder of the assets, or of the app's files (a download there wins), as OsmStations reads the packs.
    private const val REGION_DIR = "osm-regions"

    // Distinct changes kept answered: far more than one search or trip shows (a few dozen).
    private const val ANSWER_CACHE_SIZE = 512
    private val lock = Mutex()
    private val regionLock = Mutex()

    @Volatile
    private var records: List<ChangeRecord>? = null

    // The records of OSM_ASSET. Set before [records].
    @Volatile
    private var osmRecords: List<ChangeRecord> = emptyList()

    // The interchange complex of every station of the VBB and the built-in OSM dataset, by its
    // id: the stations of one record (the platforms of one change) and stations of the same base
    // name form one complex. Set before [records].
    @Volatile
    private var complexes: Map<String, Int> = emptyMap()

    // The records of the other states whose packs are loaded, each with its own complexes (their
    // ids are local numbers that mean other stations in another state); in the order loaded.
    @Volatile
    private var regions: List<ChangeDataset> = emptyList()

    // The states read or tried (under [regionLock]).
    private val triedRegions = HashSet<String>()

    // The answers of [find] by query, "no record" included: the interface asks the same change
    // many times per frame (every row, every recomposition, every rating of a change), and one
    // lookup filters all records of every dataset. Cleared whenever a dataset is (re)loaded.
    private val answers = LookupCache<ChangeQuery, ChangePosition>(ANSWER_CACHE_SIZE)

    /** True once the dataset is in memory. */
    val loaded: Boolean get() = records != null

    /** Reads the dataset unless it is in memory already. An unreadable asset counts as empty. */
    suspend fun load(context: Context) {
        if (records != null) return
        withContext(Dispatchers.Default) {
            lock.withLock {
                if (records == null) {
                    val vbb = read(context.applicationContext, ASSET, fromOsm = false)
                    val osm = read(context.applicationContext, OSM_ASSET, fromOsm = true)
                    complexes = complexesOf(vbb + osm)
                    osmRecords = osm
                    records = vbb
                    answers.clear()
                }
            }
        }
    }

    /**
     * Reads the records of the states [names] (osm-change-positions-<state>.ndjson beside their
     * packs) unless read or tried already; called by OsmStations.ensureRegions for the packs it
     * has just loaded, so a state's records are read only once the app is there. A state
     * without such a file has none.
     */
    suspend fun loadRegions(context: Context, names: List<String>) {
        if (names.isEmpty()) return
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            regionLock.withLock {
                for (name in names) {
                    if (!triedRegions.add(name)) continue
                    val read = readRegion(app, name)
                    if (read.isEmpty()) continue
                    regions = regions + ChangeDataset(read, complexesOf(read))
                    // A query without a record may have one now.
                    answers.clear()
                    Log.d(LOG_TAG, "loaded the change positions of $name: ${read.size}")
                }
            }
        }
    }

    /**
     * The record for [query], or null when the dataset has none or is not loaded yet. Without
     * the station before or after, the records of both directions are asked, and they count
     * only when they all point to the same part of the train. The VBB dataset is asked first,
     * then the OSM ones (Berlin and Brandenburg, then the loaded states, each on its own).
     * Each distinct query is looked up (and logged) once; the answer is kept until a dataset
     * is loaded (see [LookupCache]), so this is cheap enough to call during composition.
     */
    fun find(query: ChangeQuery): ChangePosition? {
        val all = records ?: return null
        return answers.getOrCompute(query) { lookup(all, it) }
    }

    private fun lookup(all: List<ChangeRecord>, query: ChangeQuery): ChangePosition? {
        val steps = StringBuilder()
        val shared = complexes
        var result = match(all, shared, query, steps)
            ?: osmRecords.takeIf { it.isNotEmpty() }?.let { osm ->
                steps.append("; osm: ")
                match(osm, shared, query, steps)
            }
        if (result == null) {
            for (region in regions) {
                steps.append("; osm state: ")
                result = match(region.records, region.complexes, query, steps)
                if (result != null) break
            }
        }
        Log.d(LOG_TAG, "find $query -> $result ($steps)")
        return result
    }

    private fun match(all: List<ChangeRecord>, complexes: Map<String, Int>, query: ChangeQuery, steps: StringBuilder): ChangePosition? {
        val fromLine = normalLine(query.fromLine)
        val toLine = normalLine(query.toLine)
        // Station and lines first. The station of a change is one complex: some providers give
        // the same id for the arriving and the departing platform, so either id of a record counts.
        var base = all.filter { record ->
            fromLine in record.fromLines &&
                toLine in record.toLines &&
                atChange(record, query.fromStation, query.fromName) &&
                atChange(record, query.toStation, query.toName)
        }
        steps.append("station+lines ").append(base.size)
        if (base.isEmpty()) {
            // Providers do not agree on the ids of one interchange: one gives the platform of a
            // line an id that the dataset uses for the platform of other lines in the same
            // complex. Then the station counts by its complex, by id or by base name, and the
            // lines and the stations before and after pick the record.
            base = all.filter { record ->
                fromLine in record.fromLines &&
                    toLine in record.toLines &&
                    inComplex(complexes, record, query.fromStation, query.fromName) &&
                    inComplex(complexes, record, query.toStation, query.toName)
            }
            val byId = base.count { record ->
                complexById(complexes, record, query.fromStation) && complexById(complexes, record, query.toStation)
            }
            steps.append(", complex ").append(base.size).append(" (by id ").append(byId)
                .append(", by name ").append(base.size - byId).append(')')
            if (base.isEmpty()) return null
        }
        val byPrevious = narrow(complexes, base, query.previousStation, query.previousName, query.previousCertain) { it.previousStation }
            ?.let { found -> found.filterNot { sameStation(it.previousStation, query.previousExcluded, query.previousExcludedName) } }
        steps.append(", previous ").append(byPrevious?.size ?: 0)
        if (byPrevious.isNullOrEmpty()) return null
        val byNext = narrow(complexes, byPrevious, query.nextStation, query.nextName, query.nextCertain) { it.nextStation }
            ?.let { found -> found.filterNot { sameStation(it.nextStation, query.nextExcluded, query.nextExcludedName) } }
        steps.append(", next ").append(byNext?.size ?: 0)
        if (byNext.isNullOrEmpty()) return null
        val first = byNext.first()
        val agree = byNext.all { it.position.part == first.position.part && it.position.samePlatform == first.position.samePlatform }
        if (!agree) steps.append(", records disagree")
        return if (agree) first.position else null
    }

    /**
     * The records whose station [of] is [id]. An unknown station keeps all of them; a guess that
     * fits none keeps all as well, a known station that fits none none (null).
     */
    private fun narrow(
        complexes: Map<String, Int>,
        records: List<ChangeRecord>,
        id: String?,
        name: String?,
        certain: Boolean,
        of: (ChangeRecord) -> ChangeStation,
    ): List<ChangeRecord>? {
        if (id == null && name == null) return records
        val fitting = records.filter { sameStation(of(it), id, name) }
            // A neighbour named by another id of its interchange.
            .ifEmpty { records.filter { sameComplex(complexes, of(it), id, name) } }
        return when {
            fitting.isNotEmpty() -> fitting
            certain -> null
            else -> records
        }
    }

    private fun atChange(record: ChangeRecord, id: String, name: String?): Boolean =
        sameStation(record.fromStation, id, name) || sameStation(record.toStation, id, name)

    private fun inComplex(complexes: Map<String, Int>, record: ChangeRecord, id: String, name: String?): Boolean =
        sameComplex(complexes, record.fromStation, id, name) || sameComplex(complexes, record.toStation, id, name)

    private fun complexById(complexes: Map<String, Int>, record: ChangeRecord, id: String): Boolean {
        val group = complexes[normalStationId(id)] ?: return false
        return complexes[record.fromStation.id] == group || complexes[record.toStation.id] == group
    }

    /**
     * The same interchange: the id names a station of the same complex of the dataset, or the
     * base name (without lines, brackets and the signs of the network) is the same.
     */
    private fun sameComplex(complexes: Map<String, Int>, station: ChangeStation, id: String?, name: String?): Boolean {
        val normal = id?.let { normalStationId(it) }
        if (normal != null) {
            val group = complexes[normal]
            if (group != null && group == complexes[station.id]) return true
        }
        val key = name?.let { stationNameKey(it) }?.takeIf { it.isNotEmpty() } ?: return false
        return station.key == key
    }

    /** Groups the stations of [all] into complexes: the two of one record, and those of one base name. */
    private fun complexesOf(all: List<ChangeRecord>): Map<String, Int> {
        val parent = HashMap<String, String>()
        fun root(id: String): String {
            var at = id
            while (true) {
                val up = parent.getOrPut(at) { at }
                if (up == at) return at
                parent[at] = parent.getOrPut(up) { up }
                at = up
            }
        }
        fun join(a: String, b: String) {
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[ra] = rb
        }
        val byKey = HashMap<String, String>()
        fun add(station: ChangeStation) {
            root(station.id)
            if (station.key.isNotEmpty()) byKey[station.key]?.let { join(it, station.id) } ?: byKey.put(station.key, station.id)
        }
        all.forEach { record ->
            add(record.fromStation)
            add(record.toStation)
            join(record.fromStation.id, record.toStation.id)
        }
        val numbers = HashMap<String, Int>()
        return parent.keys.associateWith { numbers.getOrPut(root(it)) { numbers.size } }
    }

    /** The same station: by its VBB number, or by its name where the id holds none. */
    private fun sameStation(station: ChangeStation, id: String?, name: String?): Boolean {
        val normal = id?.let { normalStationId(it) }
        if (normal != null && isVbbNumber(normal)) return station.id == normal
        val key = name?.let { stationNameKey(it) }?.takeIf { it.isNotEmpty() } ?: return false
        return station.key == key
    }

    private fun read(context: Context, asset: String, fromOsm: Boolean): List<ChangeRecord> = try {
        context.assets.open(asset).bufferedReader().useLines { lines ->
            lines.mapNotNull { line -> if (line.isBlank()) null else parse(line, fromOsm) }.toList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    // The records of the state [name]: downloaded into the app's files, else in the assets; none where neither has them.
    private fun readRegion(context: Context, name: String): List<ChangeRecord> = try {
        val file = "osm-change-positions-$name.ndjson"
        val downloaded = File(File(context.filesDir, REGION_DIR), file)
        val reader = if (downloaded.isFile) downloaded.bufferedReader() else context.assets.open("$REGION_DIR/$file").bufferedReader()
        reader.useLines { lines ->
            lines.mapNotNull { line -> if (line.isBlank()) null else parse(line, fromOsm = true) }.toList()
        }
    } catch (e: Exception) {
        // No file for this state (none was built, or it had no records), or not readable.
        emptyList()
    }

    private fun parse(line: String, fromOsm: Boolean): ChangeRecord? = try {
        val item = JSONObject(line)
        val fromLines = item.getJSONArray("fromLines")
        val toLines = item.getJSONArray("toLines")
        ChangeRecord(
            fromStation = station(item.getJSONObject("fromStation")),
            fromLines = (0 until fromLines.length()).map { normalLine(fromLines.getString(it)) }.toSet(),
            previousStation = station(item.getJSONObject("previousStation")),
            toStation = station(item.getJSONObject("toStation")),
            toLines = (0 until toLines.length()).map { normalLine(toLines.getString(it)) }.toSet(),
            nextStation = station(item.getJSONObject("nextStation")),
            position = ChangePosition(
                fromPosition = item.optDouble("fromPosition", 0.5).toFloat().coerceIn(0f, 1f),
                samePlatform = item.optBoolean("samePlatform", false),
                estimated = fromOsm,
                fromOsm = fromOsm,
            ),
        )
    } catch (e: Exception) {
        null
    }

    private fun station(item: JSONObject): ChangeStation = ChangeStation(item.getString("id"), item.optString("name", ""))
}

private const val LOG_TAG = "ChangePos"

/** A line name in one form: no spaces, upper case, without a word for the product in front ("S 2", "Bus M41"). */
fun normalLine(line: String): String {
    val plain = line.trim().replace(Regex("\\s+"), " ")
    val word = plain.substringBefore(' ', "")
    val rest = if (word.lowercase() in PRODUCT_WORDS && plain.length > word.length) plain.substring(word.length + 1) else plain
    return rest.replace(" ", "").uppercase()
}

private val PRODUCT_WORDS = setOf("bus", "tram", "str", "u-bahn", "s-bahn", "ubahn", "sbahn", "metro", "fähre", "ferry")

private fun isVbbNumber(id: String): Boolean = id.length == 9 && id.startsWith("900") && id.all { it.isDigit() }

/**
 * A station name in one form to compare names of different providers: lower case, without
 * "S+U", "S", "U" in front, the lines and places in brackets or behind, and punctuation.
 */
fun stationNameKey(name: String): String {
    var text = name.lowercase()
        .replace(NAME_BRACKETS, " ")
        .replace("straße", "str").replace("strasse", "str")
        .replace(NAME_SEPARATORS, " ")
        .trim()
    text = text.replace(NAME_RAPID_PREFIX, "")
    // Lines named behind the station, as in the dataset: "... s2 u7".
    text = text.replace(NAME_LINE_SUFFIX, "")
    text = text.removePrefix("berlin ").removeSuffix(" berlin").removeSuffix(" bhf").trim()
    return text.replace(" ", "")
}

// The patterns of [stationNameKey], compiled once: it runs for every stop on the map.
private val NAME_BRACKETS = Regex("\\(.*?\\)")
private val NAME_SEPARATORS = Regex("[^a-z0-9äöüß]+")
private val NAME_RAPID_PREFIX = Regex("^(s u|s|u)\\s+")
private val NAME_LINE_SUFFIX = Regex("(\\s+(s|u|re|rb)\\d+)+$")

/**
 * Station ids in one form: the dataset writes the long VBB form ("900000" and six digits),
 * the providers the short one ("900" and the same six digits), some inside a longer id
 * with a prefix and a platform after it ("de:11000:900" and six digits, then "::2").
 */
fun normalStationId(id: String): String {
    VBB_ID.find(id)?.let { match ->
        val digits = match.value
        return if (digits.length == 12) "900" + digits.substring(6) else digits
    }
    val plain = id.substringAfterLast(':')
    return if (plain.length == 12 && plain.startsWith("900000")) "900" + plain.substring(6) else plain
}

// A VBB station number, long or short, not part of a longer number.
private val VBB_ID = Regex("(?<![0-9])(900000[0-9]{6}|900[0-9]{6})(?![0-9])")
