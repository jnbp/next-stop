package io.github.jnbp.nextstop.data

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/*
 * The walks of the changes at each station complex, measured over the walking network of
 * OpenStreetMap (© OpenStreetMap contributors, ODbL 1.0): between the rail platforms of a
 * complex, from each of them to the bus and tram stop positions within 250 m, and between two
 * of those stops at least 60 m apart (through the station where that is shorter). The asset
 * transfer-walks-berlin.json is built by tools/build_transfer_walks.py from the same extract as
 * osm-berlin.json, and osm-regions/transfer-walks-<state>.json from the same extract as the
 * pack osm-regions/osm-<state>.json, so its platforms are those of [OsmStation.platforms] (by
 * their axis) and its stops positions of [OsmStopArea.positions]. The indexes of the loaded
 * packs are joined with [TransferWalkIndex.plus].
 */

/**
 * The walk between two places of a station complex: [seconds] on the shortest way, and
 * [stepFreeSeconds] on the shortest way without steps or escalators (null where none is
 * known). [estimate]: the network does not join the two, the seconds are the straight line
 * x 1.3; such a walk is no better than the rules of thumb and is not used for changes.
 */
data class TransferWalk(val seconds: Int, val stepFreeSeconds: Int?, val estimate: Boolean) {
    /** The seconds to count: the step-free ones where [stepFree] is asked and known. */
    fun seconds(stepFree: Boolean): Int = if (stepFree && stepFreeSeconds != null) stepFreeSeconds else seconds
}

/** One end of a walk of a change: a rail platform (by its axis) or a bus or tram stop position. */
sealed class WalkEnd {
    data class Platform(val a: Pair<Double, Double>, val b: Pair<Double, Double>) : WalkEnd()
    data class Stop(val position: Pair<Double, Double>) : WalkEnd()
}

/**
 * The walks of one station complex: [platforms] as axes (latitude and longitude of both ends),
 * [stops] as positions; [walks] by the pair of their indices (platforms first, then stops),
 * the smaller index first.
 */
class TransferWalkStation(
    val id: String,
    val name: String,
    val platforms: List<DoubleArray>,
    val stops: List<Pair<Double, Double>>,
    private val walks: Map<Long, TransferWalk>,
) {
    /** The index of [end] in this complex; null where it is not one of its platforms or stops. */
    fun indexOf(end: WalkEnd): Int? = when (end) {
        is WalkEnd.Platform -> platforms.indexOfFirst { axis ->
            close(axis[0], end.a.first) && close(axis[1], end.a.second) && close(axis[2], end.b.first) && close(axis[3], end.b.second)
        }.takeIf { it >= 0 }
        is WalkEnd.Stop -> stops.withIndex()
            .map { (index, position) -> index to flatMeters(position, end.position) }
            .filter { it.second <= STOP_MATCH_METERS }
            .minByOrNull { it.second }?.first?.plus(platforms.size)
    }

    /** The walk between the places with indices [i] and [j]; one of no length for one place. */
    fun walk(i: Int, j: Int): TransferWalk? {
        if (i == j) return TransferWalk(0, 0, false)
        return walks[pairKey(minOf(i, j), maxOf(i, j))]
    }

    /** The walk from [from] to [to]; null where either is not known here or no walk is. */
    fun walk(from: WalkEnd, to: WalkEnd): TransferWalk? {
        val i = indexOf(from) ?: return null
        val j = indexOf(to) ?: return null
        return walk(i, j)
    }

    companion object {
        // Two stop positions this close are the same (the asset rounds to 6 decimals).
        const val STOP_MATCH_METERS = 3.0

        fun pairKey(i: Int, j: Int): Long = (i.toLong() shl 32) or j.toLong()

        private fun close(a: Double, b: Double) = abs(a - b) < 2e-6
    }
}

/** The complexes of the asset, by their first id and by the name key (see [osmNameKey]). */
class TransferWalkIndex(val stations: List<TransferWalkStation>) {
    private val byId: Map<String, List<TransferWalkStation>> = stations.filter { it.id.isNotEmpty() }.groupBy { normalStationId(it.id) }
    private val byKey: Map<String, List<TransferWalkStation>> = stations.groupBy { osmNameKey(it.name) }

    /** The complex of [station]: the one with its first id, else of its name with its platforms. */
    fun of(station: OsmStation): TransferWalkStation? {
        val first = station.ids.firstOrNull()
        // Of several with the id (the same complex in two packs, wings of one station), the
        // one of the same name with one of its platforms, else of the same name, else the first.
        first?.let { byId[normalStationId(it)] }?.let { found ->
            found.firstOrNull { sameName(it, station) && sharesPlatform(it, station) }
                ?: found.firstOrNull { sameName(it, station) }
                ?: found.firstOrNull()
        }?.let { return it }
        return byKey[station.key]?.firstOrNull { candidate ->
            station.platforms.isEmpty() || station.platforms.any { p -> candidate.indexOf(WalkEnd.Platform(p.a, p.b)) != null }
        }
    }

    private fun sameName(candidate: TransferWalkStation, station: OsmStation) = osmNameKey(candidate.name) == station.key

    private fun sharesPlatform(candidate: TransferWalkStation, station: OsmStation) =
        station.platforms.any { p -> candidate.indexOf(WalkEnd.Platform(p.a, p.b)) != null }

    /**
     * This index and [other] as one (the walks of two loaded packs): the complexes of this one
     * first, so where both know a complex, this one's is found.
     */
    operator fun plus(other: TransferWalkIndex): TransferWalkIndex = when {
        other.stations.isEmpty() -> this
        stations.isEmpty() -> other
        else -> TransferWalkIndex(stations + other.stations)
    }

    // The complexes by the cells (about 110 by 70 m) of their stops, for [ofStops].
    private val byStopCell: Map<Long, List<TransferWalkStation>> by lazy {
        HashMap<Long, MutableList<TransferWalkStation>>().also { map ->
            for (station in stations) for (stop in station.stops) {
                val list = map.getOrPut(stopCell(stop)) { ArrayList() }
                if (list.lastOrNull() !== station) list.add(station)
            }
        }
    }

    /**
     * The complex that knows the walk between the stop positions [a] and [b] (a bus on one side
     * of a station, one on the other); null where none knows both.
     */
    fun ofStops(a: Pair<Double, Double>, b: Pair<Double, Double>): TransferWalkStation? {
        val lat = Math.floor(a.first * 1000).toLong()
        val lon = Math.floor(a.second * 1000).toLong()
        for (dy in -1L..1L) for (dx in -1L..1L) {
            for (station in byStopCell[((lat + dy) shl 32) + lon + dx].orEmpty()) {
                val i = station.indexOf(WalkEnd.Stop(a)) ?: continue
                val j = station.indexOf(WalkEnd.Stop(b)) ?: continue
                if (station.walk(i, j) != null) return station
            }
        }
        return null
    }

    private fun stopCell(position: Pair<Double, Double>): Long =
        (Math.floor(position.first * 1000).toLong() shl 32) + Math.floor(position.second * 1000).toLong()

    companion object {
        val EMPTY = TransferWalkIndex(emptyList())

        /** The asset's text as an index; complexes that do not parse are left out. */
        fun parse(text: String): TransferWalkIndex {
            val root = JSONObject(text)
            val array = root.optJSONArray("stations") ?: return EMPTY
            return TransferWalkIndex((0 until array.length()).mapNotNull { parseStation(array.optJSONObject(it)) })
        }

        // A complex: "id", "n" name, "p" axes [lat, lon, lat, lon], "s" stops [lat, lon],
        // "w" walks [i, j, seconds, stepFreeSeconds (0: none), estimate (1) or not (0)].
        private fun parseStation(item: JSONObject?): TransferWalkStation? {
            item ?: return null
            val platforms = item.optJSONArray("p").rows().mapNotNull { row ->
                if (row.length() < 4) null else DoubleArray(4) { row.getDouble(it) }
            }
            val stops = item.optJSONArray("s").rows().mapNotNull { row ->
                if (row.length() < 2) null else row.getDouble(0) to row.getDouble(1)
            }
            val walks = HashMap<Long, TransferWalk>()
            for (row in item.optJSONArray("w").rows()) {
                if (row.length() < 3) continue
                val i = row.getInt(0)
                val j = row.getInt(1)
                val free = row.optInt(3, 0)
                walks[TransferWalkStation.pairKey(minOf(i, j), maxOf(i, j))] =
                    TransferWalk(row.getInt(2), free.takeIf { it > 0 }, row.optInt(4, 0) == 1)
            }
            return TransferWalkStation(item.optString("id"), item.optString("n"), platforms, stops, walks)
        }

        private fun JSONArray?.rows(): List<JSONArray> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONArray(it) }
    }
}

/** Metres between two points (latitude, longitude), flat over short distances. */
private fun flatMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val north = (b.first - a.first) * 111_320.0
    val east = (b.second - a.second) * 111_320.0 * cos(Math.toRadians((a.first + b.first) / 2))
    return sqrt(north * north + east * east)
}

/**
 * The seconds of the walk of a change at [station] from one of the places [from] to one of
 * [to] (the platforms a line may use, or one stop position): the longest of all pairs, as it
 * is not known which of them it will be; step-free where [stepFree] is asked and known. Null
 * where a place or a pair is not known, or a walk is only an estimate.
 */
fun changeWalkSeconds(station: TransferWalkStation, from: List<WalkEnd>, to: List<WalkEnd>, stepFree: Boolean): Int? {
    if (from.isEmpty() || to.isEmpty()) return null
    var longest = 0
    for (a in from) for (b in to) {
        val walk = station.walk(a, b) ?: return null
        if (walk.estimate) return null
        longest = maxOf(longest, walk.seconds(stepFree))
    }
    return longest
}
