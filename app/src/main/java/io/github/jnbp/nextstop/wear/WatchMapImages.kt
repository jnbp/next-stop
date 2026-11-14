package io.github.jnbp.nextstop.wear

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import io.github.jnbp.nextstop.data.OsmStations
import io.github.jnbp.nextstop.ui.SnapshotGate
import io.github.jnbp.nextstop.ui.mapStyleUrl
import io.github.jnbp.nextstop.ui.theme.MapStyleChoice
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.storage.FileSource

/**
 * Draws map pictures for the watch ([PhoneBridge.MSG_MAP_IMAGE]): the watch asks for a view
 * (centre, MapLibre zoom, size in pixels, pixel ratio, light or dark), the phone renders it
 * with MapLibre's MapSnapshotter in the style of its own map (OpenFreeMap Positron, or the
 * dark one) and writes it as a WebP Asset at [PhoneBridge.PATH_MAP_IMAGE]`/<slot>` with the
 * key of the request and the area it drew (camera and logical size), so the watch puts it
 * exactly where it belongs whatever the pixel size of the picture. A try that MapLibre reports
 * as failed, or that does not answer in time, is tried once more on a fresh snapshotter (see
 * [render]); when that fails too, the answer has the key and no picture, so the watch draws its
 * sketch at once. MapLibre runs a still picture's loop and all its callbacks on the main
 * thread, so a busy main thread delays it (the log says how long a render waited for it).
 *
 * Fast: one snapshotter is kept warm (its style loaded, its tiles in memory) for
 * [WARM_MILLIS] after the last picture and reused for the next (a new size and camera instead
 * of a new snapshotter, which costs the cold start of MapLibre and the style each time). The
 * pictures are drawn one after the other on it. A request may ask for a small quick picture
 * of the same area first ("quick"), answered at `<slot>-q`, then the full one.
 *
 * Each slot has one request at work at most: a newer request of the same slot cancels the
 * one before (the watch only waits for the newest).
 *
 * Time: a request has one deadline ([WatchMapBudget.REQUEST_MILLIS] after it came, under the
 * 40 s the watch waits), shared by the quick picture and the tries of the full one; a try on a
 * fresh ("cold") snapshotter, which loads the style and tiles first, may take longer than one
 * on the warm snapshotter (see [WatchMapBudget]). Only one still picture is drawn at a time in
 * the app ([SnapshotGate], shared with the small maps of the phone's screens).
 *
 * Warm-up: when the watch app opens it sends [PhoneBridge.MSG_WARM_UP] (see [warmUp]); the
 * phone then sets up MapLibre and draws a small picture of the place the watch names, so the
 * snapshotter is warm and the tiles are in the cache before the watch asks for a map.
 *
 * Everything is logged with the tag [TAG] ("WatchMap"), as on the watch, so one logcat
 * filter shows the whole way of a picture with its times: request received, render start
 * and end (warm or cold), sent.
 */
internal object WatchMapImages {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = mutableMapOf<String, Job>()
    private val main = Handler(Looper.getMainLooper())

    // What the warm snapshotter draws ("<style> <ratio>", null: none), readable off the main thread
    // to choose the time limits of a try before it starts (see [WatchMapBudget]).
    @Volatile
    private var warmSignature: String? = null

    // When the last warm-up started (elapsed realtime, 0: never), for [WatchMapBudget.warmUpDue].
    @Volatile
    private var lastWarmUp = 0L

    // Whether MapLibre was set up in this process already, for the log.
    @Volatile
    private var mapLibreReady = false

    // The warm-up at work, given up for a real request of the watch (which then has the gate at once).
    @Volatile
    private var warmUpJob: Job? = null

    /** A snapshotter with its style loaded, kept for the next picture of that style and ratio. */
    private class Warm(val snapshotter: MapSnapshotter, val style: String, val ratio: Float)

    // The warm snapshotter, held strongly (MapLibre's native side keeps only a weak reference
    // to its Java peer, and a collected snapshotter never calls back). Main thread only.
    private var warm: Warm? = null

    private val release = Runnable {
        warm?.let {
            Log.i(TAG, "phone: warm snapshotter released after ${WARM_MILLIS / 1000} s unused")
            drop(it)
        }
    }

    /** Ends [it] and forgets it when it is the warm one. Main thread. */
    private fun drop(it: Warm) {
        if (warm === it) {
            warm = null
            warmSignature = null
        }
        try {
            it.snapshotter.cancel()
        } catch (e: Exception) {
            // Already done.
        }
    }

    /**
     * Takes the request in [data] (JSON, see the watch's Protocol) and answers it in the
     * background; the returned job ends once the answer is written (null: a broken request).
     * The listener service waits for it, so the process stays bound (and is neither frozen
     * nor killed as a cached app) while the picture is drawn with the app in the background.
     */
    fun answer(context: Context, data: ByteArray): Job? {
        val app = context.applicationContext
        val request = Request.parse(String(data))
        if (request == null) {
            Log.w(TAG, "phone: broken map request (${data.size} bytes)")
            return null
        }
        Log.i(TAG, "phone: request received key=${request.key} slot=${request.slot} ${request.full.describe()} quick=${request.quick?.describe() ?: "no"} ${processNote()}")
        warmUpJob?.let { warming ->
            if (warming.isActive) {
                Log.i(TAG, "phone: warm-up given up for request key=${request.key}")
                warming.cancel()
            }
        }
        synchronized(running) {
            running.remove(request.slot)?.cancel()
            val job = scope.launch {
                try {
                    val received = System.currentTimeMillis()
                    val deadline = received + WatchMapBudget.REQUEST_MILLIS
                    // The offline station data for the details on the picture (the listener
                    // service may have started the process without the app's map loading it).
                    if (request.full.zoom >= WatchMapDetails.MIN_ZOOM) {
                        try {
                            OsmStations.load(app)
                            OsmStations.ensureRegions(listOf(request.full.latitude to request.full.longitude))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "phone: station data not loaded: $e")
                        }
                        val loaded = System.currentTimeMillis() - received
                        if (loaded > SLOW_STEP_NOTE_MILLIS) Log.i(TAG, "phone: key=${request.key} station data took $loaded ms")
                    }
                    val full = SnapshotGate.lock.withLock {
                        val waited = System.currentTimeMillis() - received
                        if (waited > SLOW_STEP_NOTE_MILLIS) Log.i(TAG, "phone: key=${request.key} waited $waited ms for the station data and the picture before")
                        request.quick?.let { quick ->
                            val cold = !isWarm(quick)
                            val limit = WatchMapBudget.quickMillis(cold, deadline - System.currentTimeMillis())
                            if (limit <= 0) {
                                Log.i(TAG, "phone: quick picture skipped key=${request.key}: too little time left")
                                return@let
                            }
                            // One try only: a quick picture that fails is given up for the full one.
                            val drawn = render(app, request.key, quick, System.currentTimeMillis() + limit, attempts = 1, firstTryCold = cold)
                            if (drawn != null) {
                                Log.i(TAG, "phone: quick picture key=${request.key} ${System.currentTimeMillis() - received} ms after the request")
                                // Sent while the full one is drawn.
                                scope.launch { write(app, request, quick, drawn, quick = true) }
                            }
                        }
                        render(app, request.key, request.full, deadline, attempts = 2, firstTryCold = !isWarm(request.full))
                    }
                    val took = System.currentTimeMillis() - received
                    if (full == null) {
                        Log.w(TAG, "phone: no picture key=${request.key} after $took ms (see the tries above); answering without one")
                    } else {
                        Log.i(TAG, "phone: full picture key=${request.key} $took ms after the request, ${full.size} bytes WebP")
                    }
                    write(app, request, request.full, full, quick = false)
                } catch (e: CancellationException) {
                    Log.i(TAG, "phone: request cancelled key=${request.key} (a newer one of slot ${request.slot})")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "phone: map image failed key=${request.key}", e)
                }
            }
            running[request.slot] = job
            job.invokeOnCompletion {
                synchronized(running) { if (running[request.slot] === job) running.remove(request.slot) }
            }
            return job
        }
    }

    /**
     * Prepares the pictures for the watch when its app opens ([PhoneBridge.MSG_WARM_UP],
     * payload `{"lat", "lon", "dark", "ratio"}`, the position optional): sets up MapLibre and,
     * with a position, draws a small picture there in the style and pixel ratio of the watch, so
     * the snapshotter is warm and the tiles around are in the cache when the watch asks for its
     * map. Nothing is sent back. Skipped while the snapshotter is warm already and within
     * [WatchMapBudget.WARM_UP_MIN_GAP_MILLIS] of the last warm-up; a request of the watch gives a
     * warm-up at work up. Returns its job (the listener waits for it, as for a picture) or null.
     */
    fun warmUp(context: Context, data: ByteArray): Job? {
        val app = context.applicationContext
        val json = try {
            JSONObject(String(data))
        } catch (e: Exception) {
            JSONObject()
        }
        val dark = json.optBoolean("dark", false)
        val ratio = json.optDouble("ratio", 2.0).toFloat().coerceIn(1f, 3f)
        val latitude = json.optDouble("lat", Double.NaN)
        val longitude = json.optDouble("lon", Double.NaN)
        val view = if (latitude.isFinite() && longitude.isFinite()) {
            View.of(latitude.coerceIn(-85.0, 85.0), longitude.coerceIn(-180.0, 180.0), WARM_UP_ZOOM, WARM_UP_SIDE, WARM_UP_SIDE, ratio, dark)
        } else {
            null
        }
        val now = SystemClock.elapsedRealtime()
        val warmNow = view != null && isWarm(view)
        if (!WatchMapBudget.warmUpDue(now, lastWarmUp, warmNow)) {
            Log.i(TAG, "phone: warm-up skipped (${if (warmNow) "snapshotter warm" else "one ran ${(now - lastWarmUp) / 1000} s ago"})")
            return null
        }
        lastWarmUp = now
        Log.i(TAG, "phone: warm-up start dark=$dark ratio=$ratio position=${if (view != null) "given" else "none"} ${processNote()}")
        val job = scope.launch {
            val started = System.currentTimeMillis()
            try {
                if (view == null) {
                    // No place to draw: MapLibre is set up at least (its first start costs most).
                    main.post {
                        if (!mapLibreReady) {
                            MapLibre.getInstance(app)
                            mapLibreReady = true
                            Log.i(TAG, "phone: warm-up set up MapLibre in ${System.currentTimeMillis() - started} ms")
                        }
                    }
                    return@launch
                }
                SnapshotGate.lock.withLock {
                    // A request of the watch may have warmed it meanwhile.
                    if (isWarm(view)) {
                        Log.i(TAG, "phone: warm-up not needed any more")
                        return@withLock
                    }
                    val drawn = render(app, WARM_UP_KEY, view, System.currentTimeMillis() + WARM_UP_MILLIS, attempts = 1, firstTryCold = true)
                    Log.i(TAG, "phone: warm-up ${if (drawn != null) "done" else "failed"} in ${System.currentTimeMillis() - started} ms")
                }
            } catch (e: CancellationException) {
                Log.i(TAG, "phone: warm-up cancelled after ${System.currentTimeMillis() - started} ms")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "phone: warm-up failed: $e")
            }
        }
        warmUpJob = job
        return job
    }

    /**
     * A picture to draw: the camera ([latitude], [longitude], [zoom]) over [logicalWidth] x
     * [logicalHeight] logical pixels, each [ratio] pixels of the picture.
     */
    private class View(
        val latitude: Double,
        val longitude: Double,
        val zoom: Double,
        val logicalWidth: Int,
        val logicalHeight: Int,
        val ratio: Float,
        val dark: Boolean,
    ) {
        fun describe(): String = "${(logicalWidth * ratio).roundToInt()}x${(logicalHeight * ratio).roundToInt()} ratio=$ratio zoom=${"%.2f".format(zoom)} dark=$dark"

        companion object {
            fun of(latitude: Double, longitude: Double, zoom: Double, width: Int, height: Int, ratio: Float, dark: Boolean) = View(
                latitude,
                longitude,
                zoom.coerceIn(0.0, 20.0),
                (width.coerceIn(MIN_SIDE, MAX_SIDE) / ratio).roundToInt().coerceAtLeast(1),
                (height.coerceIn(MIN_SIDE, MAX_SIDE) / ratio).roundToInt().coerceAtLeast(1),
                ratio,
                dark,
            )
        }
    }

    /** A request of the watch, already checked to be within sane bounds: the full picture and maybe a quick one first. */
    private class Request(val key: String, val slot: String, val full: View, val quick: View?) {
        companion object {
            fun parse(text: String): Request? = try {
                val json = JSONObject(text)
                val slot = json.getString("slot")
                if (!slot.matches(SLOT_PATTERN)) {
                    null
                } else {
                    val latitude = json.getDouble("lat").coerceIn(-85.0, 85.0)
                    val longitude = json.getDouble("lon").coerceIn(-180.0, 180.0)
                    val ratio = json.optDouble("ratio", 2.0).toFloat().coerceIn(1f, 3f)
                    val dark = json.optBoolean("dark", false)
                    val quick = json.optJSONObject("quick")?.let { small ->
                        View.of(latitude, longitude, small.getDouble("zoom"), small.getInt("width"), small.getInt("height"), ratio, dark)
                    }
                    Request(
                        key = json.getString("key"),
                        slot = slot,
                        full = View.of(latitude, longitude, json.getDouble("zoom"), json.getInt("width"), json.getInt("height"), ratio, dark),
                        quick = quick,
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** How one attempt at a picture ended: the picture, or the error MapLibre reported. */
    private sealed interface Attempt {
        class Picture(val bitmap: Bitmap) : Attempt
        class Failed(val reason: String) : Attempt
    }

    /**
     * Draws [view] and returns it as WebP before [until] (wall clock), or null. Up to [attempts]
     * tries: a try that MapLibre reports as failed (it gives up a still picture at the first
     * tile, glyph range or sprite that does not load) or that has no answer within its limit
     * (see [WatchMapBudget.tryMillis]: longer on a fresh snapshotter, [firstTryCold] for the
     * first try, every later try is on a fresh one) is dropped with its snapshotter and tried
     * once more on a fresh one, after [RETRY_PAUSE_MILLIS], in what is left. The station
     * details are drawn and the picture encoded off the main thread.
     */
    private suspend fun render(context: Context, key: String, view: View, until: Long, attempts: Int, firstTryCold: Boolean): ByteArray? {
        for (attempt in 1..attempts) {
            if (attempt > 1) delay(RETRY_PAUSE_MILLIS)
            // A failed try drops its snapshotter: every later try starts a fresh one.
            val cold = firstTryCold || attempt > 1
            val limit = WatchMapBudget.tryMillis(cold, attempt, attempts, until - System.currentTimeMillis())
            if (limit <= 0) {
                Log.w(TAG, "phone: no time left for try $attempt of $attempts key=$key")
                break
            }
            val reason = when (val result = withTimeoutOrNull(limit) { renderOnce(context, key, view) }) {
                is Attempt.Picture -> return encode(key, view, result.bitmap)
                is Attempt.Failed -> result.reason
                null -> "no answer from MapLibre within $limit ms (${if (cold) "cold" else "warm"} limit)"
            }
            Log.w(TAG, "phone: try $attempt of $attempts failed key=$key: $reason")
        }
        return null
    }

    /** True when the warm snapshotter draws in the style and ratio of [view]. */
    private fun isWarm(view: View): Boolean = warmSignature == signature(view)

    private fun signature(view: View): String = "${styleOf(view)} ${view.ratio}"

    // The key of the pictures of a warm-up in the log (requests of the watch have numbers).
    private const val WARM_UP_KEY = "warm-up"

    private fun styleOf(view: View): String = mapStyleUrl(if (view.dark) MapStyleChoice.Dark else MapStyleChoice.Light, view.dark)

    /** How old the process is, for the log: a request right after a cold start of the process is slower. */
    private fun processNote(): String {
        val age = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        return "process ${age / 1000} s old, MapLibre ${if (mapLibreReady) "ready" else "not set up yet"}"
    }

    /** [base] with the station details on it (see [WatchMapDetails]) as WebP; null when encoding failed. */
    private fun encode(key: String, view: View, base: Bitmap): ByteArray? = try {
        // The station details of the area on top, as the phone's map shows them at this zoom.
        val bitmap = try {
            WatchMapDetails.drawOnto(base, view.latitude, view.longitude, view.zoom, view.ratio)
        } catch (e: RuntimeException) {
            Log.w(TAG, "phone: station details not drawn key=$key: $e")
            base
        }
        val expectedWidth = (view.logicalWidth * view.ratio).roundToInt()
        val expectedHeight = (view.logicalHeight * view.ratio).roundToInt()
        if (abs(bitmap.width - expectedWidth) > 1 || abs(bitmap.height - expectedHeight) > 1) {
            // The watch stretches it over the area drawn anyway; worth a note when a picture looks odd.
            Log.w(TAG, "phone: snapshot key=$key is ${bitmap.width}x${bitmap.height}, expected ${expectedWidth}x$expectedHeight")
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
            out.toByteArray()
        }
    } catch (e: Exception) {
        Log.e(TAG, "phone: encoding the snapshot failed key=$key", e)
        null
    }

    /**
     * One try at [view] on the main thread, as MapSnapshotter needs: the picture (a copy of
     * its own) or the error MapLibre reported. On the warm snapshotter when there is one of the
     * style and ratio, else on a new one that stays warm. A failed or cancelled try (a newer
     * request, the time limit) cancels its snapshotter and does not keep it. How long the main
     * thread took to start it is logged: a busy main thread delays every step of MapLibre's
     * still picture, which runs its loop there.
     */
    private suspend fun renderOnce(context: Context, key: String, view: View): Attempt = suspendCancellableCoroutine { continuation ->
        var used: Warm? = null
        val posted = System.currentTimeMillis()
        // The file source held for this try (see below), let go once when the try ends. Main thread.
        var held: FileSource? = null
        fun letGo() {
            held?.deactivate()
            held = null
        }
        main.post {
            if (!continuation.isActive) return@post
            val started = System.currentTimeMillis()
            try {
                // Also when the app was never opened in this process (the listener service
                // started it): MapLibre is otherwise only set up by the map of the app.
                if (!mapLibreReady) {
                    val setUp = System.currentTimeMillis()
                    MapLibre.getInstance(context)
                    mapLibreReady = true
                    Log.i(TAG, "phone: MapLibre set up for key=$key in ${System.currentTimeMillis() - setUp} ms")
                }
                main.removeCallbacks(release)
                // The file source (network and tile cache) is shared with the live map of the app,
                // which pauses it while it is stopped (a screen over it). Held for this try, so the
                // picture never waits on a paused file source; the log says how it was found.
                val files = FileSource.getInstance(context)
                val filesActive = try {
                    files.isActivated
                } catch (e: RuntimeException) {
                    null
                }
                files.activate()
                held = files
                val style = styleOf(view)
                val camera = CameraPosition.Builder().target(LatLng(view.latitude, view.longitude)).zoom(view.zoom).bearing(0.0).tilt(0.0).build()
                val kept = warm?.takeIf { it.style == style && it.ratio == view.ratio }
                val current = if (kept != null) {
                    kept.snapshotter.setSize(view.logicalWidth, view.logicalHeight)
                    kept.snapshotter.setCameraPosition(camera)
                    kept
                } else {
                    warm?.let { drop(it) }
                    val options = MapSnapshotter.Options(view.logicalWidth, view.logicalHeight)
                        .withPixelRatio(view.ratio)
                        .withStyleBuilder(Style.Builder().fromUri(style))
                        .withCameraPosition(camera)
                        .withLogo(false)
                    // Without the logo and attribution MapLibre draws itself: the watch writes a
                    // small "© OSM" where its round screen shows it.
                    val created = object : MapSnapshotter(context, options) {
                        override fun addOverlay(mapSnapshot: MapSnapshot) = Unit
                    }
                    // Says when the style is in, so a try that times out tells whether it waited for the style or the tiles.
                    created.setObserver(object : MapSnapshotter.Observer {
                        override fun onDidFinishLoadingStyle() {
                            Log.i(TAG, "phone: style loaded key=$key after ${System.currentTimeMillis() - started} ms")
                        }

                        override fun onStyleImageMissing(imageName: String) = Unit
                    })
                    Warm(created, style, view.ratio).also {
                        warm = it
                        warmSignature = signature(view)
                    }
                }
                used = current
                val how = if (kept != null) "warm" else "cold"
                val queued = started - posted
                Log.i(TAG, "phone: render start key=$key ${view.describe()} ($how) file source ${if (filesActive == false) "was paused" else "active"}, connected=${MapLibre.isConnected()}${if (queued > MAIN_QUEUE_NOTE_MILLIS) ", main thread busy $queued ms before" else ""}")
                current.snapshotter.start(
                    { snapshot ->
                        // A copy of its own (mutable, for the details drawn on it): encoded off the main thread.
                        val copy: Bitmap? = try {
                            snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        } catch (e: RuntimeException) {
                            null
                        }
                        Log.i(TAG, "phone: render end key=$key in ${System.currentTimeMillis() - started} ms ($how)")
                        letGo()
                        main.removeCallbacks(release)
                        main.postDelayed(release, WARM_MILLIS)
                        if (continuation.isActive) {
                            continuation.resume(if (copy != null) Attempt.Picture(copy) else Attempt.Failed("the snapshot could not be copied"))
                        }
                    },
                    { error ->
                        Log.w(TAG, "phone: snapshot failed key=$key ($how) after ${System.currentTimeMillis() - started} ms: $error")
                        // A style that did not load is not kept: the next picture starts afresh.
                        drop(current)
                        letGo()
                        if (continuation.isActive) continuation.resume(Attempt.Failed("MapLibre: $error"))
                    },
                )
            } catch (e: Exception) {
                // MapLibre not set up in this process, no GL context, a broken style URL, ...
                Log.e(TAG, "phone: snapshotter failed key=$key", e)
                used?.let { drop(it) }
                letGo()
                if (continuation.isActive) continuation.resume(Attempt.Failed(e.toString()))
            }
        }
        // Posted after the start above, so a snapshotter started meanwhile is cancelled too.
        continuation.invokeOnCancellation {
            main.post {
                used?.let {
                    // MapLibre may log "call to OpenGL ES API with no current context" on the
                    // snapshotter's own thread now: it frees its GL objects as it is torn down.
                    Log.i(TAG, "phone: snapshotter of key=$key given up after ${System.currentTimeMillis() - posted} ms")
                    drop(it)
                }
                letGo()
            }
        }
    }

    /** Writes the answer to [request] for [view]: its key, the area drawn, and the picture when there is one. */
    private suspend fun write(context: Context, request: Request, view: View, bytes: ByteArray?, quick: Boolean) {
        val path = "${PhoneBridge.PATH_MAP_IMAGE}/${request.slot}${if (quick) QUICK_SUFFIX else ""}"
        val item = PutDataMapRequest.create(path).apply {
            dataMap.putString(KEY_KEY, request.key)
            dataMap.putLong(KEY_TIME, System.currentTimeMillis())
            dataMap.putDouble(KEY_LATITUDE, view.latitude)
            dataMap.putDouble(KEY_LONGITUDE, view.longitude)
            dataMap.putDouble(KEY_ZOOM, view.zoom)
            dataMap.putDouble(KEY_LOGICAL_WIDTH, view.logicalWidth.toDouble())
            dataMap.putDouble(KEY_LOGICAL_HEIGHT, view.logicalHeight.toDouble())
            if (bytes != null) dataMap.putAsset(KEY_ASSET, Asset.createFromBytes(bytes))
        }.asPutDataRequest().setUrgent()
        try {
            val written = Wearable.getDataClient(context).putDataItem(item).await()
            Log.i(TAG, "phone: answer sent key=${request.key}${if (quick) " (quick)" else ""} uri=${written.uri} picture=${bytes?.size ?: "none"}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No Google Play services or no watch: nothing to answer.
            Log.w(TAG, "phone: sending the answer failed key=${request.key}: $e")
        }
    }

    const val TAG = "WatchMap"
    private const val KEY_KEY = "key"
    private const val KEY_ASSET = "image"
    private const val KEY_TIME = "time"
    private const val KEY_LATITUDE = "lat"
    private const val KEY_LONGITUDE = "lon"
    private const val KEY_ZOOM = "zoom"
    private const val KEY_LOGICAL_WIDTH = "lw"
    private const val KEY_LOGICAL_HEIGHT = "lh"
    private const val QUICK_SUFFIX = "-q"

    // A step before the picture (station data, waiting for another picture) that took longer
    // than this is noted in the log (milliseconds).
    private const val SLOW_STEP_NOTE_MILLIS = 50L

    // The picture of a warm-up: small, at a zoom of streets, only to load the style and tiles.
    private const val WARM_UP_SIDE = 160
    private const val WARM_UP_ZOOM = 15.0

    // A warm-up gives up after this long (milliseconds): it only prepares, nobody waits for it.
    private const val WARM_UP_MILLIS = 25_000L

    // The pause before the second try: a resource that failed often loads a moment later.
    private const val RETRY_PAUSE_MILLIS = 1_000L

    // The main thread was this busy before a render could start: worth a note in the log.
    private const val MAIN_QUEUE_NOTE_MILLIS = 250L

    // The snapshotter stays warm this long after its last picture (a few MB of memory).
    private const val WARM_MILLIS = 3 * 60_000L
    private const val WEBP_QUALITY = 70
    private const val MIN_SIDE = 16
    private const val MAX_SIDE = 800
    private val SLOT_PATTERN = Regex("[a-z0-9-]{1,24}")
}
