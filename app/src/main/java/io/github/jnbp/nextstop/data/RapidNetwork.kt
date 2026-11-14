package io.github.jnbp.nextstop.data

import android.content.Context
import java.io.File
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
 * itself from live data: which trips of each line run right now (one radar request per part
 * of the city), then the whole way of one or two of those trips per line. The longest way of
 * a line stands for it, simplified to a few hundred points. The result is kept in the app's
 * files and built again once a week; lines that ran nowhere when it was built (at night, during
 * works) are looked for again after a few hours.
 *
 * Limits: a line is only as complete as the trip that was found for it, so a branch that only
 * some trips serve can be missing, and a line closed for works is missing until it runs again.
 */
data class NetworkLine(val line: String, val points: List<Pair<Double, Double>>)

// Version 2: networks built while the radar request was filled up with buses and trams
// mostly lack the U-Bahn; a new file builds the network once more.
private const val CACHE_FILE = "rapid-network-2.json"

// The whole network is built again after a week.
private const val REFRESH_MILLIS = 7L * 24 * 60 * 60 * 1000

// Lines that were not found are looked for again after this long.
private const val RETRY_MILLIS = 6L * 60 * 60 * 1000

// Pause between two requests, so building the network stays far below the request limit of the service.
private const val REQUEST_PAUSE_MILLIS = 700L

// Ways are simplified to this many metres of deviation.
private const val SIMPLIFY_METRES = 12.0

// The city and its surroundings, split into four parts so each radar answer stays complete.
private const val NORTH = 52.70
private const val SOUTH = 52.33
private const val WEST = 13.05
private const val EAST = 13.80

private val RAPID_PRODUCTS = setOf("suburban", "subway")

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
    val cached = withContext(Dispatchers.IO) { readCache(file) }
    if (cached != null && cached.lines.isNotEmpty()) publish(cached.lines)
    val now = System.currentTimeMillis()
    val known = cached?.lines?.map { it.line }?.toSet().orEmpty()
    val stale = cached == null || now - cached.built > REFRESH_MILLIS
    val incomplete = !known.containsAll(wanted) && (cached == null || now - cached.attempted > RETRY_MILLIS)
    if (!stale && !incomplete) return
    try {
        val fresh = buildNetwork(source, wanted)
        // Lines not found this time keep their last known way.
        val merged = (fresh + cached?.lines.orEmpty().filter { old -> fresh.none { it.line == old.line } })
            .sortedBy { it.line }
        val record = NetworkCache(
            built = if (stale && fresh.isNotEmpty()) now else cached?.built ?: 0L,
            attempted = now,
            lines = merged,
        )
        withContext(Dispatchers.IO) { writeCache(file, record) }
        if (fresh.isNotEmpty()) publish(merged)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No network now: the cached one stays, and the next start tries again.
    }
}

private suspend fun buildNetwork(source: BvgRestSource, wanted: Set<String>): List<NetworkLine> {
    // Which trips of which line run right now, with their direction.
    val trips = LinkedHashMap<String, MutableList<Pair<String, String>>>()
    val midLatitude = (NORTH + SOUTH) / 2
    val midLongitude = (WEST + EAST) / 2
    val parts = listOf(
        doubleArrayOf(NORTH, WEST, midLatitude, midLongitude),
        doubleArrayOf(NORTH, midLongitude, midLatitude, EAST),
        doubleArrayOf(midLatitude, WEST, SOUTH, midLongitude),
        doubleArrayOf(midLatitude, midLongitude, SOUTH, EAST),
    )
    for (part in parts) {
        val vehicles = try {
            source.vehiclesOf(part[0], part[1], part[2], part[3], RAPID_PRODUCTS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        for (vehicle in vehicles) {
            val line = vehicle.line.trim().uppercase()
            if (line !in wanted || vehicle.product !in RAPID_PRODUCTS) continue
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
        if (best.size >= 2) result.add(NetworkLine(line, simplify(oriented(best), SIMPLIFY_METRES)))
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

private class NetworkCache(val built: Long, val attempted: Long, val lines: List<NetworkLine>)

private fun readCache(file: File): NetworkCache? = try {
    if (!file.exists()) {
        null
    } else {
        val root = JSONObject(file.readText())
        val list = root.optJSONArray("lines") ?: JSONArray()
        val lines = (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("line").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val flat = item.optJSONArray("points") ?: return@mapNotNull null
            val points = (0 until flat.length() / 2).map { flat.optDouble(it * 2) to flat.optDouble(it * 2 + 1) }
                .filter { !it.first.isNaN() && !it.second.isNaN() }
            if (points.size >= 2) NetworkLine(name, points) else null
        }
        NetworkCache(root.optLong("built"), root.optLong("attempted"), lines)
    }
} catch (e: Exception) {
    null
}

private fun writeCache(file: File, cache: NetworkCache) {
    val lines = JSONArray()
    for (line in cache.lines) {
        val flat = JSONArray()
        for ((latitude, longitude) in line.points) {
            // About a metre is plenty for a city view.
            flat.put(Math.round(latitude * 1e5) / 1e5)
            flat.put(Math.round(longitude * 1e5) / 1e5)
        }
        lines.put(JSONObject().put("line", line.line).put("points", flat))
    }
    val root = JSONObject().put("built", cache.built).put("attempted", cache.attempted).put("lines", lines)
    val temporary = File(file.parentFile, file.name + ".tmp")
    temporary.writeText(root.toString())
    if (!temporary.renameTo(file)) file.writeText(root.toString())
}
