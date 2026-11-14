package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Where the map of a change is centred (Web Mercator units), its scale, and the user where drawn. */
class ChangeMapFit(val x: Double, val y: Double, val pixelsPerUnit: Double, val user: Pair<Double, Double>?)

/**
 * The view that shows the whole way of [map] (and the user at [own] when near) in a
 * [width] x [height] pixels map with [pad] pixels to spare at the edges; null before the
 * size is known.
 */
fun changeMapFit(map: ChangeMap, own: Pair<Double, Double>?, width: Int, height: Int, pad: Float): ChangeMapFit? {
    if (width <= 0 || height <= 0) return null
    val from = map.fromLatitude to map.fromLongitude
    val to = map.toLatitude to map.toLongitude
    val path = map.path.ifEmpty { listOf(from, to) }
    val metresPerUnit = WebMercator.metresPerUnit((map.fromLatitude + map.toLatitude) / 2)
    val points = path + listOf(from, to)
    var minX = points.minOf { WebMercator.x(it.second) }
    var maxX = points.maxOf { WebMercator.x(it.second) }
    var minY = points.minOf { WebMercator.y(it.first) }
    var maxY = points.maxOf { WebMercator.y(it.first) }
    val reach = maxOf(maxX - minX, maxY - minY) * metresPerUnit
    // The user only when near the way; far away the sketch would shrink to nothing.
    val user = own?.takeIf { point ->
        val dx = (WebMercator.x(point.second) - (minX + maxX) / 2) * metresPerUnit
        val dy = (WebMercator.y(point.first) - (minY + maxY) / 2) * metresPerUnit
        sqrt(dx * dx + dy * dy) <= maxOf(OWN_REACH_METRES, maxOf(reach, MIN_SPAN_METRES) * 2)
    }
    if (user != null) {
        minX = minOf(minX, WebMercator.x(user.second)); maxX = maxOf(maxX, WebMercator.x(user.second))
        minY = minOf(minY, WebMercator.y(user.first)); maxY = maxOf(maxY, WebMercator.y(user.first))
    }
    val minSpan = MIN_SPAN_METRES / metresPerUnit
    val spanX = maxOf(maxX - minX, minSpan)
    val spanY = maxOf(maxY - minY, minSpan)
    val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY).coerceAtLeast(1.0)
    return ChangeMapFit((minX + maxX) / 2, (minY + maxY) / 2, scale, user)
}

/** The map picture to ask the phone for a change map of [map] in a [width] x [height] pixels view (see [changeMapFit]). */
fun changeMapRequest(map: ChangeMap, own: Pair<Double, Double>?, width: Int, height: Int, pad: Float, pixelRatio: Double, dark: Boolean): MapImageRequest? {
    val fit = changeMapFit(map, own, width, height, pad) ?: return null
    return MapImageRequest.ofView(fit.x, fit.y, fit.pixelsPerUnit, width, height, pixelRatio, dark)
}

/**
 * Asks the phone for the map pictures of a trip's changes and walks as soon as the phone
 * sends the trip, before their card is shown: the way of this step and that of the next
 * change, in the size the change card had when last shown (its full screen map too for this
 * step), into the disk cache of [MapImages]. Each view is asked for once per process; one whose
 * picture did not come is asked for again on a later update of the trip, silently, a few times
 * (see [MapImageRetry]).
 */
object TripMapPrefetch {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val asked = HashSet<String>()

    // Views whose picture did not come: how often, and not to be asked again before this time
    // (epoch ms). Tried again silently on a later update of the trip (see MapImageRetry).
    private val failed = HashMap<String, Int>()
    private val notBefore = HashMap<String, Long>()
    private var job: Job? = null

    fun onTrip(context: Context, snapshot: TripSnapshot) {
        if (!snapshot.active) return
        val app = context.applicationContext
        val here = snapshot.map ?: snapshot.walk?.let { ChangeMap(it.fromLatitude, it.fromLongitude, it.toLatitude, it.toLongitude) }
        val wanted = listOfNotNull(here?.let { it to false }, snapshot.next?.map?.let { it to false }, here?.let { it to true })
        val dark = WatchStore.mapStyle.value.dark()
        val ratio = app.resources.displayMetrics.density.toDouble().coerceIn(1.0, 3.0)
        val requests = wanted.mapNotNull { (map, large) ->
            val size = WatchStore.changeMapSize(app, large) ?: return@mapNotNull null
            changeMapRequest(map, null, size[0], size[1], size[2].toFloat(), ratio, dark)?.withMargin()
        }.filter { request ->
            val id = idOf(request)
            synchronized(asked) { (notBefore[id] ?: 0L) <= System.currentTimeMillis() && asked.add(id) }
        }
        if (requests.isEmpty()) return
        val before = job
        job = scope.launch {
            before?.join()
            for (request in requests) {
                val started = System.currentTimeMillis()
                try {
                    val there = MapImages.prefetch(app, request)
                    Log.i(MapImages.TAG, "watch: prefetch ${request.width}x${request.height} zoom=${"%.2f".format(request.zoom)} ${if (there) "kept" else "failed"} in ${System.currentTimeMillis() - started} ms")
                    if (!there) failedOnce(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(MapImages.TAG, "watch: prefetch failed: $e")
                    failedOnce(request)
                }
            }
        }
    }

    private fun idOf(request: MapImageRequest): String =
        "${request.latitude},${request.longitude},${request.zoom},${request.width}x${request.height},${request.dark}"

    /** Lets a view whose picture did not come be asked for again later, a few times (see [MapImageRetry]). */
    private fun failedOnce(request: MapImageRequest) {
        val id = idOf(request)
        synchronized(asked) {
            val failures = (failed[id] ?: 0) + 1
            failed[id] = failures
            if (MapImageRetry.prefetchAgain(failures)) {
                asked.remove(id)
                notBefore[id] = System.currentTimeMillis() + MapImageRetry.PREFETCH_PAUSE_MILLIS
                Log.i(MapImages.TAG, "watch: prefetch to be tried again in ${MapImageRetry.PREFETCH_PAUSE_MILLIS / 1000} s (failure $failures)")
            }
        }
    }
}

// The user's position on the change map: shown while this near.
private const val OWN_REACH_METRES = 600.0

// The least span of the map sketch, so a change of a few metres is not blown up.
private const val MIN_SPAN_METRES = 40.0
