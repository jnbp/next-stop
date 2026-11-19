package io.github.jnbp.nextstop.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import io.github.jnbp.nextstop.ui.AllocProbe
import java.io.File
import java.io.InputStreamReader
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The S- and U-Bahn network for the city view of the map, one way per line, built by the app
 * itself from live data: which trips of each line run (one radar request per part of the
 * city; by day those of right now, at night those of the next midday, see networkSampleTime),
 * then the whole way of one or two of those trips per line. The longest way of a line stands for
 * it, simplified to a few hundred points. The result is kept in the app's files and built again
 * once a week; lines that ran nowhere when it was built (during works) are looked for again
 * after a few hours. A new build is merged into the kept network and never makes it smaller
 * (see mergeNetwork): every line found within the last 30 days stays on the map.
 *
 * Limits: a line is only as complete as the trip that was found for it, so a branch that only
 * some trips serve can be missing, and a line closed for works is missing until it runs again.
 */
data class NetworkLine(val line: String, val points: List<Pair<Double, Double>>)

// Version 2: networks built while the radar request was filled up with buses and trams
// mostly lack the U-Bahn; a new file builds the network once more. Version 3: the ways are
// simplified less (see NETWORK_SIMPLIFY_METRES), so their curves are built once more too.
private const val CACHE_FILE = "rapid-network-3.json"
private val OLD_CACHE_FILES = listOf("rapid-network.json", "rapid-network-2.json")

// The whole network is built again after a week.
private const val REFRESH_MILLIS = 7L * 24 * 60 * 60 * 1000

// Lines that were not found are looked for again after this long.
private const val RETRY_MILLIS = 6L * 60 * 60 * 1000

// Pause between two requests, so building the network stays far below the request limit of the service.
private const val REQUEST_PAUSE_MILLIS = 700L

// Ways are simplified to this many metres of deviation: a curve of 300 m radius, as many of
// the U-Bahn, then bends by less than 20 degrees from point to point (12 m made it angular).
internal const val NETWORK_SIMPLIFY_METRES = 4.0

// The city and its surroundings, split into four parts so each radar answer stays complete.
private const val NORTH = 52.70
private const val SOUTH = 52.33
private const val WEST = 13.05
private const val EAST = 13.80

// The time zone of the network's city, for the hours by which it is sampled (see networkSampleTime).
private val NETWORK_ZONE: ZoneId = ZoneId.of("Europe/Berlin")

private val RAPID_PRODUCTS = setOf("suburban", "subway")

// The tram network (see loadTramNetwork): its own file, built the same way from the trams that
// run; the radar of a part of the city also lists its buses, so the city is cut into more parts.
private const val TRAM_CACHE_FILE = "tram-network-1.json"
private val TRAM_PRODUCTS = setOf("tram")
private const val TRAM_GRID = 3

// Berlin has some 22 tram lines: a network with fewer lacks some (works, or an old build at a
// quiet hour) and is tried again after RETRY_MILLIS.
private const val TRAM_LINES_EXPECTED = 18

/**
 * The network for the lines in [wanted] (names as "S1", "U2"): what is cached is handed to
 * [publish] at once, then built anew when it is old or incomplete, and handed over again.
 * Errors leave the cached network as it is.
 */
suspend fun loadRapidNetwork(
    context: Context,
    wanted: Set<String>,
    source: BvgRestSource = BvgRestSource(),
    publish: (List<NetworkLine>) -> Unit,
) {
    val file = File(context.filesDir, CACHE_FILE)
    val cached = withContext(Dispatchers.IO) {
        // Networks of an earlier version are built anew; their files are no longer needed.
        OLD_CACHE_FILES.forEach { File(context.filesDir, it).delete() }
        AllocProbe.measureAny(AllocProbe.RAPID_NETWORK) { readCache(file) }
    }
    if (cached != null && cached.lines.isNotEmpty()) publish(cached.lines)
    val now = System.currentTimeMillis()
    val known = cached?.lines?.map { it.line }?.toSet().orEmpty()
    val stale = cached == null || now - cached.built > REFRESH_MILLIS
    val incomplete = !known.containsAll(wanted) && (cached == null || cached.legacy || now - cached.attempted > RETRY_MILLIS)
    if (!stale && !incomplete) return
    try {
        val fresh = buildNetwork(source, RAPID_PRODUCTS, cityParts(2), networkSampleTime(now, NETWORK_ZONE)) { it in wanted }
        val record = mergedCache(cached, fresh, stale, now)
        withContext(Dispatchers.IO) { writeCache(file, record) }
        if (fresh.isNotEmpty()) publish(record.lines)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No network now: the cached one stays, and the next start tries again.
    }
}

/**
 * The tram network of Berlin for the overview of the map, one way per line, built and kept as
 * the S- and U-Bahn network is (see [loadRapidNetwork]), in a file of its own: every tram line
 * that runs while it is built. The tiles of the map have tram tracks only close up; these let
 * the trams show from the district view on.
 */
suspend fun loadTramNetwork(
    context: Context,
    source: BvgRestSource = BvgRestSource(),
    publish: (List<NetworkLine>) -> Unit,
) {
    val file = File(context.filesDir, TRAM_CACHE_FILE)
    val cached = withContext(Dispatchers.IO) { AllocProbe.measureAny(AllocProbe.TRAM_NETWORK) { readCache(file) } }
    if (cached != null && cached.lines.isNotEmpty()) publish(cached.lines)
    val now = System.currentTimeMillis()
    val stale = cached == null || now - cached.built > REFRESH_MILLIS
    val incomplete = (cached?.lines?.size ?: 0) < TRAM_LINES_EXPECTED &&
        (cached == null || cached.legacy || now - cached.attempted > RETRY_MILLIS)
    if (!stale && !incomplete) return
    try {
        val fresh = buildNetwork(source, TRAM_PRODUCTS, cityParts(TRAM_GRID), networkSampleTime(now, NETWORK_ZONE)) { true }
        val record = mergedCache(cached, fresh, stale, now)
        withContext(Dispatchers.IO) { writeCache(file, record) }
        if (fresh.isNotEmpty()) publish(record.lines)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No network now: the cached one stays, and the next start tries again.
    }
}

/** [cached] with the [fresh] lines of a build at [now] merged in (see mergeNetwork); never fewer lines. */
internal fun mergedCache(cached: NetworkCache?, fresh: List<NetworkLine>, stale: Boolean, now: Long): NetworkCache {
    val kept = cached?.kept.orEmpty()
    val merged = if (fresh.isEmpty()) kept else mergeNetwork(kept, fresh, now)
    return NetworkCache(
        built = if (stale && fresh.isNotEmpty()) now else cached?.built ?: 0L,
        attempted = now,
        kept = merged,
    )
}

/** The city and its surroundings cut into [grid] by [grid] parts: north, west, south, east of each. */
private fun cityParts(grid: Int): List<DoubleArray> {
    val height = (NORTH - SOUTH) / grid
    val width = (EAST - WEST) / grid
    return (0 until grid).flatMap { row ->
        (0 until grid).map { column ->
            doubleArrayOf(NORTH - row * height, WEST + column * width, NORTH - (row + 1) * height, WEST + (column + 1) * width)
        }
    }
}

/**
 * The lines of [products] that [wanted] takes, each by the longest way of up to two of its trips
 * running now, or at [time] where one is given.
 */
private suspend fun buildNetwork(
    source: BvgRestSource,
    products: Set<String>,
    parts: List<DoubleArray>,
    time: java.time.OffsetDateTime?,
    wanted: (String) -> Boolean,
): List<NetworkLine> {
    // Which trips of which line run then, with their direction.
    val trips = LinkedHashMap<String, MutableList<Pair<String, String>>>()
    for (part in parts) {
        val vehicles = try {
            source.vehiclesOf(part[0], part[1], part[2], part[3], products, time = time)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        for (vehicle in vehicles) {
            val line = vehicle.line.trim().uppercase()
            if (vehicle.product !in products || line.isEmpty() || !wanted(line)) continue
            val list = trips.getOrPut(line) { mutableListOf() }
            // Up to two trips per line, in different directions where there are two.
            if (list.size < 2 && list.none { it.second == vehicle.direction || it.first == vehicle.tripId }) {
                list.add(vehicle.tripId to vehicle.direction)
            }
        }
        delay(REQUEST_PAUSE_MILLIS)
    }
    val result = mutableListOf<NetworkLine>()
    for ((line, candidates) in trips) {
        var best: List<Pair<Double, Double>> = emptyList()
        var bestLength = 0.0
        for ((tripId, _) in candidates) {
            val path = try {
                source.tripPath(tripId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            delay(REQUEST_PAUSE_MILLIS)
            val length = pathMetres(path)
            if (path.size >= 2 && length > bestLength) {
                best = path
                bestLength = length
            }
        }
        if (best.size >= 2) result.add(NetworkLine(line, simplify(oriented(best), NETWORK_SIMPLIFY_METRES)))
    }
    return result
}

/**
 * [path] in one fixed direction: from west to east where it runs mostly that way, else from
 * north to south. Lines that share a stretch then run the same way there, so their offsets on
 * the map put them side by side instead of on top of each other.
 */
private fun oriented(path: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val first = path.first()
    val last = path.last()
    val east = (last.second - first.second) * cos(Math.toRadians(first.first))
    val north = last.first - first.first
    val reverse = if (kotlin.math.abs(east) >= kotlin.math.abs(north)) east < 0 else north > 0
    return if (reverse) path.reversed() else path
}

private fun pathMetres(path: List<Pair<Double, Double>>): Double {
    var total = 0.0
    for (index in 1 until path.size) total += flatMetres(path[index - 1], path[index])
    return total
}

private const val METRES_PER_DEGREE = 111_320.0

private fun flatMetres(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val dy = (b.first - a.first) * METRES_PER_DEGREE
    val dx = (b.second - a.second) * METRES_PER_DEGREE * cos(Math.toRadians((a.first + b.first) / 2))
    return sqrt(dx * dx + dy * dy)
}

/** [path] simplified by Douglas-Peucker to at most [tolerance] metres of deviation. */
fun simplify(path: List<Pair<Double, Double>>, tolerance: Double): List<Pair<Double, Double>> {
    if (path.size < 3) return path
    val scale = cos(Math.toRadians(path.first().first)) * METRES_PER_DEGREE
    val xs = DoubleArray(path.size) { path[it].second * scale }
    val ys = DoubleArray(path.size) { path[it].first * METRES_PER_DEGREE }
    val keep = BooleanArray(path.size)
    keep[0] = true
    keep[path.size - 1] = true
    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, path.size - 1))
    while (stack.isNotEmpty()) {
        val (start, end) = stack.removeLast().let { it[0] to it[1] }
        if (end - start < 2) continue
        val dx = xs[end] - xs[start]
        val dy = ys[end] - ys[start]
        val length = dx * dx + dy * dy
        var worst = -1.0
        var worstIndex = -1
        for (index in start + 1 until end) {
            val px = xs[index] - xs[start]
            val py = ys[index] - ys[start]
            val share = if (length <= 0.0) 0.0 else ((px * dx + py * dy) / length).coerceIn(0.0, 1.0)
            val ex = px - dx * share
            val ey = py - dy * share
            val distance = ex * ex + ey * ey
            if (distance > worst) {
                worst = distance
                worstIndex = index
            }
        }
        if (worstIndex >= 0 && worst > tolerance * tolerance) {
            keep[worstIndex] = true
            stack.addLast(intArrayOf(start, worstIndex))
            stack.addLast(intArrayOf(worstIndex, end))
        }
    }
    return path.filterIndexed { index, _ -> keep[index] }
}

// [legacy]: read from a file of before the lines kept their own times, which may hold a network
// built at night with half the lines; it is merged with a new build at once.
internal class NetworkCache(
    val built: Long,
    val attempted: Long,
    val kept: List<KeptNetworkLine>,
    val legacy: Boolean = false,
) {
    val lines: List<NetworkLine> get() = kept.map { it.line }
}

// Read as a stream: the file of a network is a few hundred kilobytes of numbers, and reading
// it whole as text and then as a tree of JSON objects made several times that in garbage (each
// number a string, then a boxed number in a list) right in the first seconds of the map.
internal fun readCache(file: File): NetworkCache? = try {
    if (!file.exists()) {
        null
    } else {
        JsonReader(InputStreamReader(file.inputStream(), Charsets.UTF_8).buffered(1 shl 14)).use { reader ->
            var built = 0L
            var attempted = 0L
            val lines = ArrayList<KeptNetworkLine>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "built" -> built = reader.nextLong()
                    "attempted" -> attempted = reader.nextLong()
                    "lines" -> {
                        reader.beginArray()
                        while (reader.hasNext()) readCachedLine(reader)?.let { lines.add(it) }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            // Files written before lines kept their own times: each line as seen when the file was last tried.
            NetworkCache(
                built,
                attempted,
                lines.map { if (it.seen == 0L) KeptNetworkLine(it.line, maxOf(built, attempted), maxOf(built, attempted)) else it },
                legacy = lines.any { it.seen == 0L },
            )
        }
    }
} catch (e: Exception) {
    null
}

// One line of the file: {"line": "S1", "seen": ms, "wayAt": ms, "points": [lat, lon, ...]};
// null where it has no way, the times 0 in files of before they were kept.
private fun readCachedLine(reader: JsonReader): KeptNetworkLine? {
    if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        reader.skipValue()
        return null
    }
    var name = ""
    var points: List<Pair<Double, Double>> = emptyList()
    var seen = 0L
    var wayAt = 0L
    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "line" -> name = if (reader.peek() == JsonToken.STRING) reader.nextString() else {
                reader.skipValue()
                ""
            }
            "seen" -> seen = reader.nextLong()
            "wayAt" -> wayAt = reader.nextLong()
            "points" -> {
                val read = ArrayList<Pair<Double, Double>>()
                reader.beginArray()
                while (reader.hasNext()) {
                    val latitude = reader.nextDouble()
                    if (!reader.hasNext()) break
                    val longitude = reader.nextDouble()
                    if (!latitude.isNaN() && !longitude.isNaN()) read.add(latitude to longitude)
                }
                reader.endArray()
                read.trimToSize()
                points = read
            }
            else -> reader.skipValue()
        }
    }
    reader.endObject()
    return if (name.isNotEmpty() && points.size >= 2) KeptNetworkLine(NetworkLine(name, points), seen, wayAt) else null
}

internal fun writeCache(file: File, cache: NetworkCache) {
    val lines = JSONArray()
    for (kept in cache.kept) {
        val line = kept.line
        val flat = JSONArray()
        for ((latitude, longitude) in line.points) {
            // About a metre is plenty for a city view.
            flat.put(Math.round(latitude * 1e5) / 1e5)
            flat.put(Math.round(longitude * 1e5) / 1e5)
        }
        lines.put(JSONObject().put("line", line.line).put("seen", kept.seen).put("wayAt", kept.wayAt).put("points", flat))
    }
    val root = JSONObject().put("built", cache.built).put("attempted", cache.attempted).put("lines", lines)
    val temporary = File(file.parentFile, file.name + ".tmp")
    temporary.writeText(root.toString())
    if (!temporary.renameTo(file)) file.writeText(root.toString())
}
