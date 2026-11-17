package io.github.jnbp.nextstop.wear.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * A map picture of the phone and the area it shows ([frame], what the phone's camera saw);
 * the bitmap is stretched over that area whatever its pixel size. [quick] for the small
 * first picture of a request, shown until the full one is there.
 */
class MapImage(val frame: MapFrame, val bitmap: ImageBitmap, val quick: Boolean = false) {
    fun covers(view: MapImageRequest): Boolean = !quick && frame.covers(view)
}

/** How the last request for a map picture went, for the status line of a map. */
enum class MapImageStatus {
    /** Asked, the answer not there yet. */
    Waiting,

    /** The picture arrived. */
    Ready,

    /** No phone with Next Stop is connected: the message could not be sent. */
    NoPhone,

    /** Sent, but no answer in time (an older phone app, the phone asleep, the app stopped). */
    NoAnswer,

    /** The phone answered without a picture (no network there, the style or tiles not loaded), or it could not be read. */
    NotDrawn,
}

/** The outcome of [MapImages.fetch]: the picture, or null with the reason in [status]. */
class MapImageResult(val image: MapImage?, val status: MapImageStatus)

/**
 * The map pictures the phone draws for the watch (see [Protocol.MSG_MAP_IMAGE]): the watch
 * stays light, the phone renders with MapLibre and sends a small WebP. The last few are kept
 * decoded in memory, the last [MapImageCache.CAPACITY] on disk, so a map opens with a picture
 * at once (one that covers the view, else a near one meanwhile). A request asks for a small
 * quick picture first ([MapImageRequest.quick]) and the full one after it, in one message.
 * Without a picture the caller gets the reason and draws its sketch meanwhile.
 *
 * Every step is logged with the tag [TAG] ("WatchMap"), as on the phone, with its time.
 */
object MapImages {
    const val TAG = "WatchMap"
    private val memory = ArrayDeque<MapImage>()
    private val keys = AtomicLong(System.currentTimeMillis())
    private val diskLock = Mutex()
    private var disk: MapImageCache? = null

    private fun diskOf(context: Context): MapImageCache =
        disk ?: MapImageCache(File(context.applicationContext.cacheDir, "watchmap")).also { disk = it }

    /** A kept picture that covers [view] (memory, else disk), the newest first; null when none does. */
    suspend fun cached(context: Context, view: MapImageRequest): MapImage? {
        synchronized(memory) { memory.lastOrNull { it.covers(view) } }?.let { return it }
        val started = System.currentTimeMillis()
        val found = fromDisk(context) { it.covering(view) } ?: return null
        Log.i(TAG, "watch: disk cache hit (covers) in ${System.currentTimeMillis() - started} ms")
        return found
    }

    /**
     * A kept picture to show meanwhile for [view]: one that shows a good part of it at a near
     * zoom (see [MapImageCache.nearest]), from memory or disk; null when none is near.
     */
    suspend fun nearest(context: Context, view: MapImageRequest): MapImage? {
        synchronized(memory) {
            memory.filter { !it.quick && it.frame.dark == view.dark && kotlin.math.abs(it.frame.zoom - view.zoom) <= MapImageCache.PLACEHOLDER_ZOOM }
                .maxByOrNull { it.frame.overlap(view) }
                ?.takeIf { it.frame.overlap(view) >= MapImageCache.PLACEHOLDER_OVERLAP }
        }?.let { return it }
        val started = System.currentTimeMillis()
        val found = fromDisk(context) { it.nearest(view) } ?: return null
        Log.i(TAG, "watch: disk cache hit (nearby, meanwhile) in ${System.currentTimeMillis() - started} ms")
        return found
    }

    /**
     * A kept picture of the streets within [THUMBNAIL_METRES] of ([latitude], [longitude]) (any
     * position when null), the most recently used first, from memory or disk; null when none is
     * kept. Never asks the phone.
     */
    suspend fun latestNear(context: Context, latitude: Double?, longitude: Double?): MapImage? {
        val x = longitude?.let { WebMercator.x(it) }
        val y = latitude?.let { WebMercator.y(it) }
        val maxUnits = THUMBNAIL_METRES / WebMercator.metresPerUnit(latitude ?: 0.0)
        synchronized(memory) {
            memory.reversed().firstOrNull { image ->
                !image.quick && image.frame.zoom >= THUMBNAIL_MIN_ZOOM &&
                    (x == null || y == null || kotlin.math.hypot(image.frame.x - x, image.frame.y - y) <= maxUnits)
            }
        }?.let { return it }
        return fromDisk(context) { MapImageCache.pickLatestNear(it.frames(), x, y, maxUnits, THUMBNAIL_MIN_ZOOM) }
    }

    private suspend fun fromDisk(context: Context, pick: (MapImageCache) -> MapImageCache.Entry?): MapImage? = withContext(Dispatchers.IO) {
        val (entry, bytes) = diskLock.withLock {
            val cache = diskOf(context)
            val entry = pick(cache) ?: return@withContext null
            entry to (cache.read(entry) ?: return@withContext null)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        MapImage(entry.frame, bitmap.asImageBitmap()).also { remember(it) }
    }

    private fun remember(image: MapImage) {
        synchronized(memory) {
            memory.remove(image)
            memory.addLast(image)
            while (memory.size > MEMORY_SIZE) memory.removeFirst()
        }
    }

    /**
     * Asks the phone for the picture of [request] (unless one on disk covers it already) and
     * keeps it; for the pictures of a trip before their card is shown. True when one is there.
     */
    suspend fun prefetch(context: Context, request: MapImageRequest): Boolean {
        if (cached(context, request) != null) {
            Log.i(TAG, "watch: prefetch not needed, kept picture covers it")
            return true
        }
        return fetch(context, PREFETCH_SLOT, request).image != null
    }

    /**
     * Asks the phone for the picture of [request] and waits for it at most [ANSWER_TIMEOUT_MILLIS].
     * [slot] names the place it is for ("map", "change"): the answers of one slot replace each other.
     * With [onQuick] the phone first draws a small quick picture of the same area, handed to
     * [onQuick] as soon as it is there (not when the full one came first).
     */
    suspend fun fetch(context: Context, slot: String, request: MapImageRequest, onQuick: ((MapImage) -> Unit)? = null): MapImageResult {
        val app = context.applicationContext
        val key = keys.incrementAndGet().toString()
        val path = "${Protocol.PATH_MAP_IMAGE}/$slot"
        val quickPath = "$path${Protocol.QUICK_SUFFIX}"
        val quick = if (onQuick != null) request.quick() else null
        // The answers: the picture with what the phone drew, or no picture (it could not draw it).
        val answer = CompletableDeferred<Pair<Asset, DataMap>?>()
        val quickAnswer = CompletableDeferred<Pair<Asset, DataMap>>()
        val listener = DataClient.OnDataChangedListener { events ->
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val itemPath = event.dataItem.uri.path ?: continue
                if (!itemPath.startsWith(Protocol.PATH_MAP_IMAGE)) continue
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                val itemKey = map.getString(Protocol.KEY_MAP_KEY)
                if ((itemPath != path && itemPath != quickPath) || itemKey != key) {
                    Log.d(TAG, "watch: other answer ignored path=$itemPath key=$itemKey (waiting for $path key=$key)")
                    continue
                }
                val asset = if (map.containsKey(Protocol.KEY_MAP_ASSET)) map.getAsset(Protocol.KEY_MAP_ASSET) else null
                if (itemPath == quickPath) {
                    Log.i(TAG, "watch: quick answer received key=$key slot=$slot picture=${asset != null}")
                    if (asset != null) quickAnswer.complete(asset to map)
                } else {
                    Log.i(TAG, "watch: answer received key=$key slot=$slot picture=${asset != null}")
                    answer.complete(asset?.let { it to map })
                }
            }
        }
        val client = try {
            Wearable.getDataClient(app).also { it.addListener(listener).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "watch: no data client (Google Play services?): $e")
            return MapImageResult(null, MapImageStatus.NoPhone)
        }
        try {
            val payload = JSONObject()
                .put("key", key)
                .put("slot", slot)
                .put("lat", request.latitude)
                .put("lon", request.longitude)
                .put("zoom", request.zoom)
                .put("width", request.width)
                .put("height", request.height)
                .put("ratio", request.pixelRatio)
                .put("dark", request.dark)
            quick?.let { payload.put("quick", JSONObject().put("zoom", it.zoom).put("width", it.width).put("height", it.height)) }
            val started = System.currentTimeMillis()
            if (!WatchStore.sendToPhone(app, Protocol.MSG_MAP_IMAGE, payload.toString().toByteArray(), single = true)) {
                Log.w(TAG, "watch: request not sent key=$key slot=$slot: no phone reachable")
                return MapImageResult(null, MapImageStatus.NoPhone)
            }
            Log.i(TAG, "watch: request sent key=$key slot=$slot ${request.width}x${request.height} zoom=${"%.2f".format(request.zoom)} dark=${request.dark} quick=${quick?.let { "${it.width}x${it.height}" } ?: "no"}")
            return coroutineScope {
                val quickJob = if (quick != null && onQuick != null) {
                    launch {
                        val (asset, map) = quickAnswer.await()
                        val image = decode(client, asset, map, quick, key, quick = true) ?: return@launch
                        if (!answer.isCompleted) {
                            Log.i(TAG, "watch: quick picture ready key=$key ${System.currentTimeMillis() - started} ms after the request")
                            onQuick(image)
                        }
                    }
                } else {
                    null
                }
                val replied = withTimeoutOrNull(ANSWER_TIMEOUT_MILLIS) { answer.await() }
                quickJob?.cancel()
                when {
                    !answer.isCompleted -> {
                        Log.w(TAG, "watch: no answer key=$key slot=$slot within $ANSWER_TIMEOUT_MILLIS ms")
                        MapImageResult(null, MapImageStatus.NoAnswer)
                    }
                    replied == null -> {
                        Log.w(TAG, "watch: phone answered without a picture key=$key slot=$slot")
                        MapImageResult(null, MapImageStatus.NotDrawn)
                    }
                    else -> {
                        val image = decode(client, replied.first, replied.second, request, key, quick = false, keep = app)
                        if (image == null) {
                            MapImageResult(null, MapImageStatus.NotDrawn)
                        } else {
                            Log.i(TAG, "watch: full picture ready key=$key ${System.currentTimeMillis() - started} ms after the request")
                            remember(image)
                            MapImageResult(image, MapImageStatus.Ready)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "watch: request cancelled key=$key slot=$slot")
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "watch: map image failed key=$key slot=$slot", e)
            return MapImageResult(null, MapImageStatus.NotDrawn)
        } finally {
            client.removeListener(listener)
        }
    }

    /**
     * Reads and decodes the picture [asset]; its area is the one the phone says it drew in
     * [map], else that of [asked] (an older phone). With [keep] it also goes to the disk cache.
     */
    private suspend fun decode(client: DataClient, asset: Asset, map: DataMap, asked: MapImageRequest, key: String, quick: Boolean, keep: Context? = null): MapImage? {
        val bytes = client.getFdForAsset(asset).await().inputStream.use { stream ->
            withContext(Dispatchers.IO) { stream.readBytes() }
        }
        val bitmap = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        if (bitmap == null) {
            Log.w(TAG, "watch: picture not decodable key=$key (${bytes.size} bytes)")
            return null
        }
        val frame = drawnFrame(map, asked.dark) ?: asked.frame
        if (bitmap.width != asked.width || bitmap.height != asked.height) {
            // Drawn over the frame anyway; only worth a note when looking for odd pictures.
            Log.i(TAG, "watch: picture key=$key is ${bitmap.width}x${bitmap.height}, asked ${asked.width}x${asked.height}; stretched over its area")
        }
        Log.i(TAG, "watch: picture decoded key=$key${if (quick) " (quick)" else ""} ${bitmap.width}x${bitmap.height} ${bytes.size} bytes")
        if (keep != null) {
            withContext(Dispatchers.IO) {
                diskLock.withLock { diskOf(keep).put(frame, bytes) }
            }
        }
        return MapImage(frame, bitmap.asImageBitmap(), quick)
    }

    /** The area the phone says it drew (see [Protocol.KEY_MAP_LOGICAL_WIDTH]); null from a phone that does not say. */
    private fun drawnFrame(map: DataMap, dark: Boolean): MapFrame? {
        val names = listOf(Protocol.KEY_MAP_LATITUDE, Protocol.KEY_MAP_LONGITUDE, Protocol.KEY_MAP_ZOOM, Protocol.KEY_MAP_LOGICAL_WIDTH, Protocol.KEY_MAP_LOGICAL_HEIGHT)
        if (names.any { !map.containsKey(it) }) return null
        val values = names.map { map.getDouble(it) }
        if (values[3] <= 0 || values[4] <= 0) return null
        return MapFrame.drawn(values[0], values[1], values[2], values[3], values[4], dark)
    }

    // The phone takes up to 20 s to draw (a cold start of MapLibre, tiles over a slow
    // network) and draws one picture after the other (the quick one first).
    private const val ANSWER_TIMEOUT_MILLIS = 40_000L
    private const val MEMORY_SIZE = 6

    // The preview of the start page: a picture whose centre lies at most this far from the user
    // (metres), drawn at this zoom or closer, so it shows streets, not a region.
    private const val THUMBNAIL_METRES = 1_500.0
    private const val THUMBNAIL_MIN_ZOOM = 13.0
    private const val PREFETCH_SLOT = "pre"
}
